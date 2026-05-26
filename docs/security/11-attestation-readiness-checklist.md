# 11. Чек-лист готовности к аттестации

## Назначение

Чек-лист используется для подготовки `Second Shelf` к развертыванию в attestation-ready environment.
Он не является заявлением о формальной аттестации или сертификации системы. Итоговая оценка должна выполняться
для конкретной среды размещения, сетевой схемы, средств защиты, эксплуатационных процедур и собранных доказательств.

## 1. Реализовано в application code

| Контроль | Текущее состояние | Доказательство в репозитории |
| --- | --- | --- |
| Personal data minimization | Данные разделены по доменам; `auth-service` хранит refresh metadata и `userId`, `book-service` хранит `ownerId`, а не профиль; `notification-service` не обращается к профилям и книгам для построения текста | `docs/security/03-personal-data-register.md`, ADR 007 |
| Authentication / authorization | JWT для пользовательских API; owner-based, participant-based и `ROLE_ADMIN` checks | `docs/security/04-access-control-matrix.md`, сервисные security tests |
| Password hashing | Пароли хранятся как bcrypt-хэши в `user-service` | `user-service`, `InternalAuthServiceTest` |
| Password policy | Регистрация и internal user creation требуют длину 10-100, lowercase, uppercase, digit, special char, отсутствие whitespace, username и email local-part | `auth-service` и `user-service` password validation tests |
| Refresh token rotation | Refresh-токен отзывается и заменяется новым токеном той же family | `auth-service`, `RefreshTokenServiceTest` |
| Refresh token reuse detection | Повторное использование отозванного refresh-токена помечает family и отзывает активные токены family | `auth_db.refresh_tokens.reuse_detected_at`, `RefreshTokenServiceTest` |
| Rate limiting | `auth-service` имеет in-memory token-bucket limiter для login, register и refresh | `AuthControllerTest`, `AuthRateLimitServiceTest` |
| Audit logging | Security audit events пишутся в application logs в structured key-value стиле; чувствительные поля маскируются | `observability-common`, `AuditLoggerTest` |
| Correlation id | `X-Correlation-Id` создается или сохраняется в gateway и HTTP-сервисах, пишется в MDC и передается в RabbitMQ events | gateway/filter tests, service controller/client tests |
| Internal APIs | `/internal/**` в `user-service` и `book-service` защищены `X-Internal-Token` и скрыты из Swagger | internal controller/security tests |
| Database per service | Пять service databases без cross-service foreign keys | `docker/postgres/init/01-create-extra-dbs.sql`, ADR 002 |
| RabbitMQ outbox | `exchange-service` пишет `outbox_events` в той же транзакции, публикует с confirms и переводит исчерпанные события в `TERMINAL_FAILED` | outbox tests, ADR 005 |
| Retry/backoff and DLQ | Notification consumer использует retry/redelivery handling, DLQ и processed event idempotency | notification messaging tests |
| Repair flow | Partial distributed failures переводят exchange в `REPAIR_REQUIRED`; admin repair endpoint повторяет нужные book transitions | exchange service/admin tests, ADR 008 |
| Gateway entry point | `api-gateway` маршрутизирует frontend paths, CORS и correlation id; production-like compose публикует только gateway | `api-gateway`, `docker-compose.prod.example.yaml`, ADR 006 |
| Non-root containers | Java runtime images создают и используют `appuser` | сервисные `Dockerfile` |
| Production secrets validation | Non-local profiles fail fast на отсутствующих, коротких или demo secrets; seed admin password проверяется вне local profile | `SecurityConfigurationValidator*Test`, `AdminSeederTest` |
| Vulnerability scanning hook | Maven profile запускает OWASP Dependency-Check отдельно от обычных тестов | `./mvnw -Psecurity-scan verify` |

## 2. Требуется в deployment infrastructure

| Контроль | Требование для production / attestation-ready environment |
| --- | --- |
| TLS requirement | Внешний трафик должен идти через HTTPS; внутренний сервисный, PostgreSQL и RabbitMQ трафик должен быть защищен средствами платформы или закрытым доверенным сегментом |
| Gateway / reverse proxy | Публично должен быть опубликован только `api-gateway` или внешний reverse proxy перед ним; прямые порты domain services не должны быть доступны из Internet |
| Firewall | Доступ к service ports, `/internal/**`, PostgreSQL, RabbitMQ AMQP и RabbitMQ Management UI должен ограничиваться network ACL / security groups / firewall |
| Centralized logs and retention | Логи всех сервисов должны собираться централизованно, храниться с утвержденным retention, иметь restricted access и возможность поиска по `correlationId` |
| Monitoring and alerting | Нужны алерты на auth failures, rate-limit spikes, DLQ growth, `TERMINAL_FAILED`, outbox backlog, failed repair/redrive attempts и недоступность health endpoints |
| Backups and restore tests | Все пять PostgreSQL DB, RabbitMQ state/configuration, DLQ и deployment config должны иметь backup policy; restore tests должны выполняться на отдельном контуре и журналироваться |
| DB / RabbitMQ isolation | Для production нужны отдельные credentials по средам, желательно отдельные DB users per service, network isolation и запрет demo `guest/guest` |
| Secret manager | `JWT_SECRET`, `AUTH_REFRESH_TOKEN_PEPPER`, `INTERNAL_TOKEN`, DB и RabbitMQ credentials должны храниться вне git и compose files, с ротацией и аудитом доступа |
| Management endpoint exposure | Swagger, OpenAPI, Actuator, metrics и RabbitMQ UI должны быть закрыты ingress rules, VPN, IP allowlist или отдельной admin network |
| Container platform hardening | Runtime запускается non-root в образах, но platform должна enforce read-only/least-privilege policies, resource limits, image provenance и restricted capabilities |
| Vulnerability scanning | Dependency scan, container image scan и base-image monitoring должны запускаться в CI/CD или security pipeline с разбором high/critical findings |
| Time synchronization | Все узлы должны использовать единый источник времени для токенов, audit logs, incident response и восстановления событий |

## 3. Требуется как organizational process

| Контроль | Требование |
| --- | --- |
| `3-ИН` classification | Предварительное обоснование класса `3-ИН` должно быть подтверждено владельцем системы и специалистами ИБ для конкретного deployment scope |
| Personal data governance | Реестр ПДн, цели обработки, retention, права доступа и порядок удаления должны быть утверждены организацией |
| Access review | Production admin accounts, роли, доступ к логам, backup storage, secret manager, БД и RabbitMQ должны регулярно пересматриваться |
| Incident response | Должны быть назначены ответственные, каналы эскалации, playbooks, правила сохранения evidence и порядок уведомления заинтересованных сторон |
| Backup governance | Нужно вести журнал backup/restore tests, фиксировать RPO/RTO, ответственных и результаты проверок |
| Change management | Изменения gateway routes, CORS, secrets, firewall, retention, scan suppressions и production profiles должны проходить review и фиксироваться |
| Attestation readiness evidence | Для аттестации должны собираться deployment diagrams, перечень средств защиты, scan reports, test reports, backup restore evidence, incident response docs и настройки доступа |

## 4. Неприменимо к проекту с обоснованием

| Требование | Обоснование |
| --- | --- |
| Электронная цифровая подпись пользовательских документов | В Second Shelf нет юридически значимого документооборота и операций подписания документов пользователями |
| Пользовательская PKI / личные ключи пользователей | Приложение не выпускает, не хранит и не использует ключи пользователей или УЦ |
| Application-level Wi-Fi controls | Backend не управляет беспроводной сетью; если Wi-Fi есть в защищаемом контуре, он должен регулироваться инфраструктурными процедурами |
| Application-level removable media controls | Runtime-код не взаимодействует со съемными носителями; защита носителей для backup/export относится к эксплуатации |

## Итог

`Second Shelf` имеет прикладной baseline для deployment в attestation-ready environment: JWT, refresh rotation/reuse detection,
password policy, internal token, rate limiting, audit logging, correlation id, database-per-service, outbox, DLQ, repair flow,
non-root containers и production secret validation. Формальная готовность к аттестации зависит от инфраструктуры и
организационных доказательств, а не только от исходного кода.
