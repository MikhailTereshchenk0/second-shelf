# Практическая часть: финальная реализация Second Shelf

## Общая характеристика

`Second Shelf` реализован как multi-module Spring Boot backend для обмена книгами между пользователями.
Финальная архитектура состоит из gateway и пяти domain services. Система спроектирована и документирована для
развертывания в attestation-ready environment, но репозиторий не утверждает, что система формально аттестована или
сертифицирована.

## Финальный список сервисов

| Сервис | Назначение |
| --- | --- |
| `api-gateway` | Frontend и production entry point, маршрутизация публичных API, CORS, создание и прокидывание `X-Correlation-Id` |
| `auth-service` | Регистрация, login, выпуск JWT access tokens, refresh token rotation, reuse detection, logout и logout-all |
| `user-service` | Профили пользователей, роли, блокировка/разблокировка, password hash, internal auth/claims APIs |
| `book-service` | Публичный каталог книг, управление книгами владельца, internal state transitions для обменов |
| `exchange-service` | Жизненный цикл заявок на обмен, резервирование/освобождение/завершение книг, outbox events, repair flow |
| `notification-service` | Получение RabbitMQ событий, идемпотентная обработка и сохранение in-app уведомлений |

## Основные пользовательские сценарии

1. Registration / login / refresh / logout:
   пользователь регистрируется через `auth-service`; профиль создается во внутреннем вызове в `user-service`.
   Login возвращает access token и refresh token. Refresh выполняет ротацию refresh token и выдает новую пару токенов.
   Logout отзывает текущий refresh token, а logout-all отзывает все активные refresh tokens пользователя.

2. Profile management:
   `user-service` хранит профиль, email, роли, password hash и флаг `enabled`. Пользователь может читать и изменять
   свой профиль по owner-based правилам; администратор управляет ролями и блокировкой.

3. Book catalog and owner book management:
   `book-service` публикует публичный каталог только для книг `PUBLIC` и `AVAILABLE`. Владелец управляет своими книгами:
   создает, обновляет, публикует, скрывает и удаляет их, пока бизнес-состояние книги допускает изменение.

4. Exchange request lifecycle:
   `exchange-service` создает заявку по выбранной чужой книге без встречной книги заявителя. В статусе `PENDING`
   владелец видит телефон заявителя и список его доступных публичных книг, затем отклоняет заявку или выбирает одну
   книгу как встречное предложение. После статуса `OWNER_OFFERED` requester принимает или отклоняет предложение; только
   финальное принятие переводит заявку в `ACCEPTED`, резервирует обе книги и раскрывает requester'у телефон владельца.

5. Two-sided completion:
   завершение обмена требует подтверждения обеих сторон. Первое подтверждение переводит заявку в `COMPLETION_PENDING`
   и создает уведомление для второй стороны. После второго подтверждения обе книги переводятся в `EXCHANGED` и
   `PRIVATE`, а заявка становится `COMPLETED`.

6. Notifications:
   `exchange-service` создает outbox events для действий с обменами. `notification-service` получает события из RabbitMQ,
   проверяет идемпотентность через `processed_events` и сохраняет in-app уведомления для получателей.

## Security

- Personal data minimization: данные распределены по доменам. `auth-service` хранит refresh metadata и `userId`, но не
  полный профиль; `book-service` хранит `ownerId`, но не email; `notification-service` работает с готовым event payload
  и не запрашивает профили или книги для построения текста.
- Обоснование класса `3-ИН`: система подключена к открытым каналам передачи данных, обрабатывает обычные персональные
  данные пользователей, имеет публичные API и требует защиты конфиденциальности, целостности и доступности.
- JWT / refresh model: access tokens являются HMAC-signed JWT, downstream services валидируют их локально. Refresh
  tokens являются opaque tokens, хранятся только как HMAC-SHA-256 hash с server-side pepper, ротируются при refresh и
  поддерживают reuse detection с revocation token family.
- Password hashing and password policy: пароли хранятся как bcrypt hash. Политика пароля требует 10-100 символов,
  lowercase, uppercase, digit, special char, отсутствие whitespace, username и email local-part.
- Rate limiting: `auth-service` содержит in-memory token-bucket limiter для login, register и refresh. Для production
  дополнительно требуется distributed limiter на gateway/ingress/WAF или shared backend.
- Internal APIs: `/internal/**` в `user-service` и `book-service` защищены `X-Internal-Token`, скрыты из Swagger и должны
  быть недоступны извне через network segmentation.
- Audit logging: security audit events пишутся в structured key-value формате с маскированием чувствительных полей.
  `X-Correlation-Id` позволяет связывать HTTP-запросы и async-события.
- Production secrets validation: non-local profiles отклоняют отсутствующие, короткие или demo secrets; seed admin
  password вне local profile должен соответствовать password policy.

## Reliability

- Database per service: используются отдельные PostgreSQL databases `auth_db`, `users_db`, `books_db`, `exchange_db` и
  `notification_db`, без cross-service foreign keys.
- RabbitMQ: exchange events публикуются в topic exchange `exchange.events`, доставляются в queue
  `notification.exchange-events`, а ошибочные сообщения попадают в DLQ.
- Outbox: `exchange-service` записывает `outbox_events` в той же транзакции, что и изменение exchange state, поэтому
  намерение отправить уведомление не теряется при сбое между БД и broker publish.
- Retry/backoff: outbox publisher повторяет публикацию с учетом attempts, broker confirms и returned messages; после
  исчерпания попыток событие получает статус `TERMINAL_FAILED`.
- DLQ/redrive: consumer отправляет invalid или exhausted messages в DLQ. Admin endpoint позволяет redrive после
  устранения причины сбоя.
- Repair flow: при partial distributed failure exchange переводится в `REPAIR_REQUIRED`; admin repair endpoint
  повторяет необходимые book transitions и возвращает обмен в согласованное состояние.

## Deployment

- Local compose: `docker-compose.yaml` публикует gateway, domain services, PostgreSQL, RabbitMQ AMQP и RabbitMQ
  Management UI для локальной разработки и диагностики.
- Production-like compose: `docker-compose.prod.example.yaml` публикует только `api-gateway`; domain services, PostgreSQL
  и RabbitMQ находятся на internal network.
- Gateway-only exposure: frontend должен обращаться к gateway; direct service ports допустимы только для local/debug.
- TLS и infrastructure requirements: TLS, firewall, network segmentation, centralized logs, backups, secret manager,
  vulnerability scanning, restore tests, incident response и attestation evidence обеспечиваются deployment
  infrastructure и организационными процедурами.
- Containers: Java service images запускают приложение под non-root `appuser`.

## Testing

- Critical security tests: покрывают password policy, refresh token reuse detection, rate limiting, internal token
  protection, production secret validation, correlation id и audit log masking.
- Business workflow tests: покрывают управление профилем, каталог книг, owner-based правила, двухшаговый lifecycle
  exchange request, условное раскрытие контактов, two-sided completion, admin repair и authorization restrictions.
- Async reliability tests: покрывают outbox event creation, publisher confirms/failures, terminal failed retry, RabbitMQ
  topology, idempotent notification processing, DLQ и redrive.

## Итог

Практическая реализация доведена до состояния production-readiness baseline: архитектурные решения зафиксированы в ADR,
security docs разделяют прикладные, инфраструктурные и организационные меры, а README описывает локальный и
production-like запуск. Для реального production deployment остаются обязательными инфраструктурные средства защиты и
формальное подтверждение готовности в конкретной среде размещения.
