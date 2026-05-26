# 02. Требования по безопасности

## Цель документа

Документ фиксирует требования ИБ для `Second Shelf` и разделяет их на четыре группы:

- реализовано в application code;
- требуется в deployment infrastructure;
- требуется как organizational process;
- неприменимо к текущему проекту с обоснованием.

Система спроектирована и документирована для развертывания в attestation-ready environment. Документ не утверждает,
что система формально аттестована или сертифицирована.

## 1. Реализовано в application code

| Область | Требование | Текущее состояние |
| --- | --- | --- |
| Personal data minimization | Сервисы должны хранить только данные, необходимые их домену | Реализовано через domain ownership и database-per-service; подробности в `03-personal-data-register.md` |
| Идентификация пользователей | Все пользовательские операции, кроме публичных auth endpoint'ов и публичного каталога, должны выполняться от имени аутентифицированного субъекта | Реализовано через JWT |
| Authorization | Доступ к профилям, книгам, обменам, уведомлениям и admin flows должен быть ограничен владельцем, участником или ролью | Реализовано: owner-based, participant-based, `ROLE_ADMIN` |
| Password hashing | Пароли не должны храниться в открытом виде | Реализовано: bcrypt-хэш в `user-service` |
| Password policy | Пароль должен быть достаточно сложным и не включать username/email local-part | Реализовано в `auth-service` и `user-service`: 10-100 символов, lower/upper/digit/special, без whitespace, username и email local-part |
| Refresh token model | Должны использоваться access token и refresh token с отзывом и ротацией | Реализовано в `auth-service` |
| Refresh token storage | Сырой refresh token не должен храниться в БД | Реализовано: HMAC-SHA-256 hash с server-side pepper |
| Refresh token reuse detection | Повторное использование отозванного refresh token должно приводить к revocation family | Реализовано в `auth-service` |
| Rate limiting | Публичные auth endpoint'ы должны иметь защиту от brute force и credential stuffing | Реализован in-memory limiter для login, register и refresh; основной distributed limiter требуется в инфраструктуре |
| Internal APIs | Внутренние сервисные endpoint'ы должны быть отделены от публичных API | Реализовано: `/internal/**`, `X-Internal-Token`, скрытие из Swagger |
| Audit logging | Security-relevant события должны логироваться без секретов | Реализовано через `observability-common` audit logger и маскирование чувствительных полей |
| Correlation id | Запросы и async-события должны иметь идентификатор корреляции | Реализовано: `X-Correlation-Id` создается/сохраняется gateway и сервисами, передается в RabbitMQ |
| Изоляция данных | Данные разных доменов должны храниться отдельно | Реализовано: пять service databases |
| Async reliability | События обмена не должны теряться между DB commit и broker publish | Реализовано: outbox pattern, publisher confirms, retry, `TERMINAL_FAILED` |
| DLQ / redrive | Невалидные или исчерпавшие retry сообщения должны попадать в DLQ с возможностью операционного redrive | Реализовано в `notification-service` |
| Distributed consistency repair | Partial failures обмена должны быть явно видимы и ремонтируемы | Реализовано: `REPAIR_REQUIRED` и admin repair flow |
| Gateway entry point | Frontend должен иметь единый entry point и CORS boundary | Реализовано: `api-gateway`; production-like compose публикует только gateway |
| Non-root containers | Runtime контейнеры не должны запускать Java-приложение под root | Реализовано: `appuser` во всех Java service Dockerfile |
| Production secret validation | Non-local startup должен отвергать отсутствующие и demo secrets | Реализовано через `SecurityConfigurationValidator` и seed admin password checks |

## 2. Требуется в deployment infrastructure

| Область | Требование |
| --- | --- |
| TLS | HTTPS для внешнего трафика; защищенный транспорт или доверенный закрытый сегмент для internal HTTP, PostgreSQL и RabbitMQ |
| Gateway / reverse proxy | Публиковать только gateway/reverse proxy; direct service ports, PostgreSQL, RabbitMQ и management UI не должны быть доступны из Internet |
| Firewall / segmentation | Ограничить service-to-service маршруты, `/internal/**`, DB и RabbitMQ через firewall, security groups или network policies |
| Centralized logs and retention | Централизованный сбор логов, ограничение доступа, retention policy, поиск по `correlationId`, защита от несанкционированного изменения |
| Backups and restore tests | Backup всех service DB и RabbitMQ operational state; регулярные restore tests с журналом результатов |
| DB/RabbitMQ isolation | Раздельные credentials по средам, желательно per service; запрет demo credentials; сетевое ограничение доступа |
| Secret manager | Хранение и ротация `JWT_SECRET`, `AUTH_REFRESH_TOKEN_PEPPER`, `INTERNAL_TOKEN`, DB/RabbitMQ credentials вне git |
| Distributed rate limiting | Для нескольких инстансов нужен limiter на gateway/ingress/WAF или shared backend вроде Redis |
| Vulnerability scanning | Dependency scan, image scan и base-image monitoring в CI/CD или security pipeline |
| Management endpoints | Swagger/OpenAPI, metrics, extended actuator groups и RabbitMQ UI должны быть доступны только admin контуру; public health/info endpoints допускаются без деталей |
| Time synchronization | Единый источник времени для токенов, audit logs, correlation и incident response |

## 3. Требуется как organizational process

| Область | Требование |
| --- | --- |
| `3-ИН` classification | Подтвердить применимость класса `3-ИН` для конкретной среды и регуляторного scope |
| Personal data governance | Утвердить реестр ПДн, цели обработки, retention, порядок удаления и права доступа |
| Access review | Периодически пересматривать admin accounts, production roles, доступ к logs, backups, secret manager, DB и RabbitMQ |
| Incident response | Назначить ответственных, эскалацию, playbooks, evidence handling и post-incident review |
| Backup governance | Утвердить RPO/RTO, частоту backup, частоту restore tests и владельцев процедуры |
| Change management | Фиксировать изменения gateway routes, CORS, firewall, secrets, retention, scan suppressions и production profiles |
| Attestation readiness | Собирать доказательства: deployment diagrams, scan reports, test reports, backup/restore logs, security configuration и регламенты |

## 4. Неприменимо к текущему проекту

| Требование | Обоснование |
| --- | --- |
| ЭЦП пользовательских документов | В системе нет юридически значимого документооборота и пользовательского подписания |
| Пользовательская PKI / личные ключи | Приложение не выпускает и не хранит ключи пользователей или УЦ |
| Wi-Fi controls на уровне приложения | Backend не управляет Wi-Fi инфраструктурой |
| Removable media controls на уровне приложения | Runtime-код не работает со съемными носителями; это относится к эксплуатации backup/export процессов |

## Примечание по аттестации

Документ фиксирует baseline-требования для системы, предварительно рассматриваемой как `3-ИН`. Формальная аттестация
должна проводиться для конкретного deployment environment с учетом инфраструктуры, организационных процедур и
подтверждающих материалов.
