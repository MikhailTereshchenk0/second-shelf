# 08. Резервное копирование и хранение

## Объекты резервного копирования

Для `Second Shelf` резервному копированию подлежат:

- `auth_db`
- `users_db`
- `books_db`
- `exchange_db`
- `notification_db`
- конфигурация RabbitMQ, включая exchange, queue и DLQ
- deployment-конфигурация без раскрытия секретов в открытом виде
- журналы, необходимые для расследования инцидентов, если это предусмотрено политикой эксплуатации

## Что особенно важно для проекта

| Компонент | Почему важен | Особенность восстановления |
| --- | --- | --- |
| `users_db` | содержит основные ПДн и password hash | критичен для восстановления учетных записей |
| `auth_db` | содержит refresh-сессии | при потере возможно массовое завершение сессий |
| `books_db` | содержит каталог и привязку `ownerId` | влияет на доступность операций обмена |
| `exchange_db` | содержит историю обменов и outbox | потеря нарушит бизнес-историю и целостность async-потока |
| `notification_db` | содержит уведомления и idempotency registry | потеря может вызвать повторную обработку или утрату уведомлений |
| RabbitMQ / DLQ | содержит еще не обработанные или ошибочные события | нужен либо backup брокера, либо регламент повторной публикации и re-drive |

## Базовая политика backup

Для production-контура рекомендуется минимум:

- ежедневный backup всех PostgreSQL БД;
- дополнительная возможность point-in-time recovery, если это поддерживает выбранная платформа;
- резервирование RabbitMQ persisted state или регулярный экспорт конфигурации и DLQ-содержимого;
- отдельное защищенное backup storage вне основного вычислительного узла;
- периодическая проверка восстановления на тестовом контуре.

В репозитории есть operational scripts для ручного запуска backup / restore:

- `scripts/backup-postgres.sh` создает timestamped `pg_dump --format=custom`
  для всех service databases;
- `scripts/restore-postgres.sh` восстанавливает один явно указанный target DB
  из одного явно указанного backup file;
- `scripts/rotate-local-backups.sh` удаляет локальные `.dump` файлы старше
  заданного срока retention.

Скрипты не подключены к application startup и должны запускаться только
оператором, scheduler-ом или отдельной backup automation.

### Переменные окружения для PostgreSQL backup

Скрипты читают параметры подключения из environment и не содержат hard-coded
секретов. Минимальный набор:

| Variable | Purpose |
| --- | --- |
| `DB_HOST` | PostgreSQL host. |
| `POSTGRES_PORT` | PostgreSQL port. |
| `DB_USERNAME` | Пользователь PostgreSQL с правом чтения всех service databases; для restore также нужны права на пересоздание объектов. |
| `DB_PASSWORD` | Пароль PostgreSQL. Скрипты передают его через `PGPASSWORD` и не выводят в stdout/stderr. |
| `USER_DB_NAME` | Имя БД `user-service`, обычно `users_db`. |
| `AUTH_DB_NAME` | Имя БД `auth-service`, обычно `auth_db`. |
| `BOOK_DB_NAME` | Имя БД `book-service`, обычно `books_db`. |
| `EXCHANGE_DB_NAME` | Имя БД `exchange-service`, обычно `exchange_db`. |
| `NOTIFICATION_DB_NAME` | Имя БД `notification-service`, обычно `notification_db`. |
| `BACKUP_DIR` | Каталог для backup-файлов. Для backup по умолчанию `backups/postgres`; для rotation задается явно. |

Пример локального backup после загрузки `.env`:

```bash
set -a
. ./.env
set +a
BACKUP_DIR=backups/postgres ./scripts/backup-postgres.sh
```

Файлы создаются с UTC timestamp в имени:

```text
users_db_20260526T171500Z.dump
auth_db_20260526T171500Z.dump
books_db_20260526T171500Z.dump
exchange_db_20260526T171500Z.dump
notification_db_20260526T171500Z.dump
```

Пример локальной ротации:

```bash
BACKUP_DIR=backups/postgres BACKUP_RETENTION_DAYS=30 ./scripts/rotate-local-backups.sh
```

### Restore procedure

Restore является потенциально destructive operation. Скрипт отказывается
запускаться без явного подтверждения:

```bash
set -a
. ./.env
set +a
CONFIRM_RESTORE=true \
TARGET_DB=users_db \
BACKUP_FILE=backups/postgres/users_db_20260526T171500Z.dump \
./scripts/restore-postgres.sh
```

Safety checks:

- `TARGET_DB` обязателен и должен совпадать с одной из пяти service databases;
- `BACKUP_FILE` обязателен и должен существовать;
- `CONFIRM_RESTORE=true` обязателен;
- отсутствующие connection variables приводят к немедленному завершению;
- пароль БД не выводится в лог.

## Политика retention

Текущий runtime-код не реализует автоматическую очистку:

- уведомлений;
- истории обменов;
- outbox-событий;
- refresh-токенов после завершения их жизненного цикла;
- технических данных для повторной обработки событий.

Поэтому необходимо утвердить эксплуатационную политику retention. Базовый ориентир:

- operational backups: не менее 30 дней;
- еженедельные долгосрочные копии: по внутренней политике организации;
- журналы ИБ и аудита: по внутренней политике организации и регуляторным требованиям;
- DLQ-сообщения: до разбора причины и принятия решения о re-drive или списании.

Для локальных filesystem backups можно использовать
`scripts/rotate-local-backups.sh` с `BACKUP_RETENTION_DAYS`. В production
retention должен применяться на уровне backup platform/object storage policy,
а не только локальным `find -delete`.

## Требования к хранилищу резервных копий

Backup storage должно обеспечивать:

- разграничение доступа;
- защиту от несанкционированного удаления;
- шифрование на уровне платформы или хранилища;
- журналирование операций с резервными копиями;
- хранение вне того же отказного домена, где работают сервисы.

Production expectations:

- backup storage должен быть encrypted at rest; при передаче в удаленное
  хранилище должен использоваться защищенный транспорт;
- доступ к backup storage должен быть ограничен минимальным числом
  эксплуатационных ролей;
- операции чтения, восстановления, удаления и изменения retention policy должны
  попадать в audit log;
- restore tests должны проводиться регулярно на отдельном тестовом контуре;
- retention period должен быть формально утвержден и технически enforced;
- backup-файлы не должны попадать в git, application images или публичные
  artifact repositories.

## Процедуры восстановления

При восстановлении `Second Shelf` необходимо учитывать зависимость сервисов:

1. восстановить БД;
2. восстановить конфигурацию RabbitMQ;
3. проверить согласованность состояний в `exchange_db` и `books_db`;
4. оценить необходимость re-drive сообщений из DLQ;
5. при необходимости принудительно завершить старые refresh-сессии и потребовать повторный вход пользователей.

## Замечание по аттестации

Для системы класса `3-ИН` наличие backup-политики должно подтверждаться не только данным документом,
но и фактической настройкой backup storage, регламентом тестирования восстановления и журналом проведенных проверок.
