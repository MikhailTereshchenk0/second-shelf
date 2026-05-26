# 10. Безопасная конфигурация

## Цель

Документ фиксирует требования к безопасной конфигурации `Second Shelf` без изменения runtime-кода.
В репозитории есть удобные local defaults и production-like шаблоны. Local defaults нельзя переносить в shared,
staging или production среду.

Система спроектирована и документирована для развертывания в attestation-ready environment; формальная готовность
зависит от конкретной инфраструктуры и эксплуатационных доказательств.

## Production entry point

Для production-like deployment публичным entry point должен быть `api-gateway` или внешний reverse proxy перед ним.
`docker-compose.prod.example.yaml` моделирует такую схему: публикуется только gateway, а `auth-service`,
`user-service`, `book-service`, `exchange-service`, `notification-service`, PostgreSQL и RabbitMQ остаются на
internal Docker network.

Требования:

- frontend вызывает только gateway routes;
- CORS задается через `FRONTEND_ALLOWED_ORIGINS`;
- TLS завершается на ingress/reverse proxy или gateway-facing инфраструктуре;
- direct service ports, PostgreSQL, RabbitMQ AMQP и RabbitMQ Management UI не публикуются наружу;
- `/internal/**`, Swagger, OpenAPI, Actuator и metrics закрываются network policy, firewall, VPN или IP allowlist.

## Критичные параметры

| Параметр | Где используется | Требование |
| --- | --- | --- |
| `JWT_SECRET` | `auth-service`, `user-service`, `book-service`, `exchange-service`, `notification-service` | хранить только в secret manager, задавать длинное случайное значение, обеспечивать ротацию и аудит доступа |
| `AUTH_REFRESH_TOKEN_PEPPER` | `auth-service` | хранить отдельно от БД, задавать вне `local` profile, ротировать по процедуре incident response |
| `INTERNAL_TOKEN` | `auth-service`, `user-service`, `book-service`, `exchange-service` | хранить в secret manager, регулярно ротировать, не использовать в logs или Swagger examples |
| `DB_USERNAME` / `DB_PASSWORD` | все domain services | использовать отдельные значения по средам; в production желательно отдельные DB users per service |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `exchange-service`, `notification-service` | не использовать `guest/guest`; ограничить доступ к vhost, exchange, queue и DLQ |
| `SEED_ADMIN_USERNAME`, `SEED_ADMIN_PASSWORD`, `SEED_ADMIN_EMAIL` | `user-service` | задать безопасные значения, задокументировать bootstrap и отключить/ограничить seed после инициализации среды |
| `FRONTEND_ALLOWED_ORIGINS` | `api-gateway` | указывать только доверенные frontend origins; не использовать wildcard с credentials |
| `USER_SERVICE_BASE_URL`, `BOOK_SERVICE_BASE_URL` | service-to-service clients | использовать internal DNS / internal network only |
| `AUTH_RATE_LIMIT_*` | `auth-service` | включить как defense-in-depth; основной distributed limiter настроить на gateway/ingress/WAF/shared backend |

## Небезопасные значения, которые нельзя оставлять вне local profile

В local-конфигурации и `.env.example` встречаются значения, пригодные только для разработки:

- `internal-secret-123`
- `change_this_secret_to_something_long_and_random_1234567890_change_me`
- `admin` / `admin12345` / `admin@secondshelf.local`
- `guest` для RabbitMQ
- `secret` как demo DB password

Non-local profiles содержат startup validation, которая отбрасывает отсутствующие, слишком короткие и demo secrets.
Тем не менее real deployment должен получать значения из secret manager или защищенного CI/CD secret store, а не из git.

## Требования по сервисам

### `api-gateway`

- публиковать как основной frontend entry point;
- включать только доверенные `FRONTEND_ALLOWED_ORIGINS`;
- сохранять и создавать `X-Correlation-Id`;
- не считать gateway единственным authorization boundary, потому что JWT validation остается в downstream services;
- закрывать management endpoints infrastructure controls.

### `auth-service`

- публиковать через gateway/reverse proxy и TLS;
- защищать `JWT_SECRET` и `AUTH_REFRESH_TOKEN_PEPPER` как high-value secrets;
- контролировать сроки жизни access и refresh tokens;
- включать rate limiting для login, register и refresh;
- исключить логирование passwords, raw refresh tokens и JWT целиком;
- ограничить Swagger и Actuator в production.

### `user-service`

- хранить пароль только как bcrypt hash;
- использовать password policy для user creation и seed admin вне local profile;
- защищать admin endpoints и `/internal/**` network controls;
- контролировать роль `ROLE_ADMIN`, блокировку/разблокировку и изменение ролей через audit logs.

### `book-service`

- не публиковать `/internal/books/**` наружу;
- разрешать internal transitions только доверенному `exchange-service`;
- учитывать, что `ownerId` является ПДн и ключевым атрибутом authorization;
- сохранять публичный каталог ограниченным книгами, разрешенными бизнес-правилами.

### `exchange-service`

- использовать `BOOK_SERVICE_BASE_URL` только во внутреннем сегменте;
- мониторить pending outbox, `TERMINAL_FAILED`, repair attempts и admin outbox retry;
- ограничить admin repair/outbox endpoints ролью и инфраструктурными controls;
- закрыть `/actuator/metrics` от публичного доступа.

### `notification-service`

- ограничить доступ к RabbitMQ и DLQ;
- мониторить listener health, processing failures, DLQ growth и redrive attempts;
- закрыть admin DLQ redrive endpoints ролью и infrastructure controls;
- учитывать, что notification messages могут содержать ПДн из exchange snapshots.

## Инфраструктурные обязательства

Следующие меры не решаются только application configuration:

- TLS и gateway/reverse proxy;
- firewall, network segmentation и private service network;
- centralized logs, retention и restricted access;
- backup storage, restore tests и журнал восстановления;
- DB/RabbitMQ credential isolation;
- secret manager и secret rotation;
- container platform hardening поверх non-root images;
- vulnerability scanning для dependencies, container images и base images;
- IDS/IPS, DNS logging, EDR/antivirus и time synchronization, если они входят в защищаемый контур.

## Проверка зависимостей на уязвимости

В root `pom.xml` есть отдельный Maven profile `security-scan`. Обычные команды разработки, например
`./mvnw test` и `./mvnw -DskipTests package`, не запускают scan и не зависят от доступности internet/NVD.

Команда для CI или явной ручной проверки:

```bash
./mvnw -Psecurity-scan verify
```

Профиль запускает OWASP Dependency-Check Maven plugin в aggregate-режиме для multi-module проекта и сохраняет отчеты
в `target/security-reports`. Build с активным `security-scan` считается неуспешным при найденной уязвимости с CVSS
`7.0` и выше.

Эксплуатационные правила:

- запускать scan в CI/CD или отдельном security pipeline со стабильным доступом к NVD или корпоративному кэшу;
- хранить отчеты как CI artifacts с ограниченным доступом;
- разбирать high/critical findings до release;
- добавлять suppressions только для подтвержденных false positives, с комментарием и сроком пересмотра.

## Рекомендуемые эксплуатационные правила

- разделять production, staging и test secrets;
- не хранить реальные secrets в `.env`, git, images или публичных artifacts;
- использовать разные учетные записи DB/RabbitMQ по средам и, по возможности, по сервисам;
- закрыть Swagger, Actuator, metrics и RabbitMQ UI внешним ingress, VPN или IP allowlist;
- фиксировать изменения конфигурации в change management;
- регулярно проверять restore, incident response и secret rotation procedures;
- проверять, что health endpoints не раскрывают лишние детали.
