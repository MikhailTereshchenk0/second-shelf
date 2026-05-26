# 10. Безопасная конфигурация

## Цель

Документ фиксирует требования к безопасной конфигурации `Second Shelf` без изменения runtime-кода.
Он особенно важен, поскольку в репозитории присутствуют удобные для локальной разработки default values,
которые не должны попадать в production.

## Критичные параметры

| Параметр | Где используется | Требование |
| --- | --- | --- |
| `JWT_SECRET` | `auth-service`, `user-service`, `book-service`, `exchange-service`, `notification-service` | хранить только в secret manager, задавать длинное случайное значение, обеспечивать ротацию |
| `INTERNAL_TOKEN` | `auth-service`, `user-service`, `book-service`, `exchange-service` | хранить только в secret manager, не передавать через открытые каналы, регулярно ротировать |
| `DB_USERNAME` / `DB_PASSWORD` | все сервисы | использовать отдельные учетные данные по средам, по возможности раздельно по сервисам |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `exchange-service`, `notification-service` | не использовать guest/default в production |
| `SEED_ADMIN_USERNAME`, `SEED_ADMIN_PASSWORD`, `SEED_ADMIN_EMAIL` | `user-service` | задать безопасные значения или отключить seed после инициализации среды |
| `AUTH_DB_NAME`, `USER_DB_NAME`, `BOOK_DB_NAME`, `EXCHANGE_DB_NAME`, `NOTIFICATION_DB_NAME` | соответствующие сервисы | не смешивать базы между окружениями |
| `USER_SERVICE_BASE_URL`, `BOOK_SERVICE_BASE_URL` | межсервисовые клиенты | публиковать только во внутреннем сегменте |

## Небезопасные значения, которые нельзя оставлять в production

В текущих `application.yaml` и `docker-compose.yaml` есть значения, годные только для локальной разработки:

- `internal-secret-123`
- `change_this_secret_to_something_long_and_random_1234567890_change_me`
- `admin` / `admin12345` / `admin@secondshelf.local`
- `guest` для RabbitMQ

Эти значения должны быть переопределены до ввода в эксплуатацию.

## Требования по сервисам

### `auth-service`

- публиковать только через TLS-терминирующий слой;
- ограничить доступ к Swagger и Actuator;
- защищать `JWT_SECRET` как ключ доверия для всего JWT-контура;
- контролировать срок жизни access и refresh-токенов;
- исключить логирование refresh-токенов.

### `user-service`

- хранить пароль только как bcrypt-хэш;
- защитить admin endpoints дополнительными инфраструктурными ограничениями при возможности;
- контролировать конфигурацию seed admin;
- защищать `/internal/**` не только токеном, но и сетевой изоляцией.

### `book-service`

- не публиковать `/internal/books/**` наружу;
- проверять, что сервис доступен изнутри сегмента только для `exchange-service`;
- учитывать, что `ownerId` является критичным атрибутом разграничения доступа.

### `exchange-service`

- защищать `BOOK_SERVICE_BASE_URL` внутренним маршрутом;
- контролировать доступ к RabbitMQ и состояние outbox publisher;
- ограничить доступ к `/actuator/metrics`, так как сейчас endpoint открыт без JWT;
- отслеживать рост `TERMINAL_FAILED` и backlog pending events.

### `notification-service`

- ограничить доступ к RabbitMQ listener-инфраструктуре;
- ограничить доступ к `/actuator/metrics`, так как endpoint открыт без JWT;
- контролировать DLQ и объём уведомлений с ПДн.

## Инфраструктурные обязательства

Следующие меры не решаются прикладной конфигурацией и должны задаваться вне репозитория:

- TLS;
- firewall;
- централизованное логирование;
- backup storage;
- IDS/IPS;
- DNS-логирование;
- EDR/антивирус;
- синхронизация времени;
- secret manager.

## Проверка зависимостей на уязвимости

В root `pom.xml` добавлен отдельный Maven profile `security-scan` для проверки
third-party dependencies. Обычные команды разработки, например
`./mvnw test` и `./mvnw -DskipTests package`, не запускают этот scan и не
зависят от доступности internet/NVD.

Команда для CI или явной ручной проверки:

```bash
./mvnw -Psecurity-scan verify
```

Профиль запускает OWASP Dependency-Check Maven plugin в aggregate-режиме для
multi-module проекта и сохраняет отчеты в `target/security-reports`.
Настроены HTML и JSON форматы. Build с активным `security-scan` считается
неуспешным при найденной уязвимости с CVSS `7.0` и выше.

Эксплуатационные правила:

- запускать scan в CI/CD или отдельном security pipeline, где есть стабильный
  доступ к NVD или корпоративному кэшу Dependency-Check;
- не требовать успешного security scan от обычной локальной сборки без сети;
- хранить отчеты как CI artifacts с ограниченным доступом;
- разбирать high/critical findings до release;
- добавлять suppression file только для подтвержденных false positives, с
  комментарием к каждому suppression и сроком пересмотра.

## Рекомендуемые эксплуатационные правила

- не хранить секреты в `.env`, если файл доступен широкому кругу лиц;
- разделять production, staging и test секреты;
- использовать разные учетные записи БД для сервисов, даже если СУБД общая;
- закрыть Swagger и Actuator внешним ingress или IP allowlist;
- фиксировать изменения конфигурации в change management;
- проверять, что health endpoints не раскрывают лишние детали.
