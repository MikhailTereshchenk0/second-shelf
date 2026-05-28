# 05. Модель угроз

## Защищаемые активы

К ключевым активам `Second Shelf` относятся:

- профильные данные пользователей в `user-service`, включая контактный телефон;
- bcrypt-хэши паролей;
- refresh-сессии в `auth-service`;
- JWT и `INTERNAL_TOKEN` как средства доступа;
- данные о владении книгами;
- сообщения, условия и история обменов в `exchange-service`;
- уведомления в `notification-service`;
- outbox-события, RabbitMQ queue и DLQ;
- журналы событий и метрики.

## Основные нарушители

- внешний анонимный атакующий из сети Интернет;
- пользователь платформы, пытающийся получить доступ к чужим данным;
- злоумышленник, получивший JWT, refresh-токен или `INTERNAL_TOKEN`;
- администратор или оператор с избыточными правами в инфраструктуре;
- вредоносное ПО на хосте размещения;
- сбой брокера сообщений или БД, приводящий к потере событий.

## Ключевые угрозы и меры

| Угроза | Затрагиваемые компоненты | Уже реализованные меры | Что должно закрываться дополнительно |
| --- | --- | --- | --- |
| Перехват JWT или refresh-токена в открытом канале | `auth-service`, все публичные API | Подпись JWT, ротация refresh-токенов, отзыв сессий, хранение только хэша refresh-токена | TLS, безопасная публикация API, защита клиентского хранения токенов |
| Подбор паролей и credential stuffing | `auth-service`, `user-service`, `api-gateway` | bcrypt-хэширование, password policy, запрет логина отключенным пользователям, in-memory rate limiting для auth endpoint'ов | distributed rate limiting на gateway/ingress/WAF, антибот-защита, мониторинг аномалий |
| Несанкционированный доступ к чужому профилю, книге, обмену или уведомлению | `user-service`, `book-service`, `exchange-service`, `notification-service` | owner-based authorization, participant-based authorization, `ROLE_ADMIN` | регулярный аудит прав, негативные тесты на авторизацию, review production-ролей |
| Компрометация внутреннего токена сервис-сервис | `user-service`, `book-service`, `exchange-service` как потребитель внутренних API | Отдельные `/internal/**`, проверка `X-Internal-Token`, скрытие из Swagger | secret manager, регулярная ротация, mTLS или сегментация между сервисами |
| Боковое перемещение после компрометации одной БД | PostgreSQL контур | Изолированные БД по сервисам | отдельные пользователи БД, network ACL, шифрование дисков и backup storage |
| Потеря или дублирование событий обмена | `exchange-service`, RabbitMQ, `notification-service` | outbox, broker confirms, retry, DLQ, idempotency через `processed_events` | алерты по DLQ и `TERMINAL_FAILED`, процедуры re-drive из DLQ |
| Раскрытие служебной информации через Swagger, Actuator или метрики | все сервисы, особенно `exchange-service` и `notification-service` | `show-details: never` для публичного health | ограничение публикации management endpoints через ingress/firewall |
| Утечка ПДн через логи | все сервисы | audit logger маскирует чувствительные поля; `X-Correlation-Id` поддерживается gateway и HTTP-сервисами и передается в RabbitMQ | централизованная маскировка, правила запрета логирования секретов, контроль доступа к логам |
| Отказ в обслуживании или backlog в async-контуре | RabbitMQ, `exchange-service`, `notification-service` | health checks, async metrics, outbox health indicator, listener health indicator | capacity planning, autoscaling, оповещения, лимиты на ingress |
| Подмена или удаление резервных копий | backup storage, БД | на уровне приложения не реализуется | защищенное хранилище резервных копий, контроль доступа, шифрование, тесты восстановления |

## Особые риски текущей реализации

- Один и тот же `JWT_SECRET` разделяется между `auth-service` и сервисами, которые валидируют JWT локально. Компрометация секрета влияет на весь контур доверия.
- В примерах конфигурации присутствуют небезопасные default values для `JWT_SECRET`, `INTERNAL_TOKEN` и seed admin credentials; для production они неприемлемы.
- Swagger, OpenAPI, Actuator и metrics endpoints не являются самостоятельным security boundary, поэтому доступ к ним должен быть закрыт внешним периметром и admin network.
- Автоматическая очистка уведомлений, outbox-событий и истории обменов не реализована, что повышает риск накопления чувствительных данных без утвержденной политики retention.

## Вывод

Наиболее критичными для `Second Shelf` являются угрозы компрометации токенов, ошибки разграничения доступа,
раскрытия служебных интерфейсов и нарушения целостности async-цепочки `exchange-service -> RabbitMQ -> notification-service`.
Часть этих рисков уже смягчается приложением, но достижение требуемого уровня защиты `3-ИН`
невозможно без мер на стороне инфраструктуры и эксплуатации.
