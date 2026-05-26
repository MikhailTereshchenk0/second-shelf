# 04. Матрица разграничения доступа

## Принципы

`Second Shelf` использует несколько моделей доступа одновременно:

- публичный доступ только к ограниченным endpoint'ам;
- JWT-аутентификацию для пользовательских API;
- owner-based authorization для профилей и книг;
- participant-based authorization для обменов;
- `ROLE_ADMIN` для административных операций;
- `X-Internal-Token` для закрытых внутренних API.

## Матрица доступа

| Сервис / ресурс | Основные endpoint'ы | Кто имеет доступ | Основание доступа | Комментарий |
| --- | --- | --- | --- | --- |
| `api-gateway` frontend entry point | `/api/auth/**`, `/api/v1/users/**`, `/api/v1/books/**`, `/api/v1/exchanges/**`, `/api/v1/notifications/**`, admin API route families | Browser/frontend client | Gateway routes, CORS, `X-Correlation-Id` propagation | JWT validation остается в downstream services; production должен публиковать gateway вместо direct service ports |
| `auth-service` публичная аутентификация | `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/logout-all`, `/api/auth/ping` | Анонимный клиент | `permitAll` | Без bearer token; валидность refresh-токена проверяется на уровне бизнес-логики |
| `auth-service` текущий пользователь | `/api/auth/me` | Аутентифицированный пользователь | Bearer JWT | Возвращает имя и роли из текущей аутентификации |
| `user-service` чтение профиля | `/api/v1/users/{id}`, `/api/v1/users/by-username` | Любой аутентифицированный пользователь | Bearer JWT | Email и профиль видимы после аутентификации |
| `user-service` изменение профиля | `/api/v1/users/{id}` | Только владелец профиля | `@PreAuthorize("#id == principal.userId")` | Прямой owner-check на уровне метода |
| `user-service` администрирование | `/api/v1/admin/users/{id}/roles`, `/block`, `/unblock` | Только пользователь с `ROLE_ADMIN` | `hasRole("ADMIN")` | Управление ролями и доступностью учетной записи |
| `user-service` внутренние API | `/internal/users`, `/internal/users/{id}/claims`, `/internal/auth/authenticate` | `auth-service` и доверенные сервисы с `X-Internal-Token` | `INTERNAL_SERVICE` authority | Эндпоинты скрыты из Swagger через `@Hidden` |
| `book-service` публичный каталог | `/api/v1/books/public` | Анонимный клиент | `permitAll` | Возвращаются только книги `PUBLIC` со статусом `AVAILABLE` |
| `book-service` мои книги | `/api/v1/books/my`, `POST /api/v1/books` | Аутентифицированный владелец | Bearer JWT | `ownerId` берется из JWT |
| `book-service` операции над книгой | `PATCH /api/v1/books/{id}`, `DELETE`, `PUT /publish`, `PUT /hide` | Только владелец конкретной книги | Owner-based authorization в `BookServiceImpl` | Невладелец получает отказ, а часть сценариев блокируется по состоянию книги; перевод в `EXCHANGED` выполняется только через internal exchange workflow |
| `book-service` чтение книги по `id` | `GET /api/v1/books/{id}` | Владелец всегда; другой пользователь только для `PUBLIC` и `AVAILABLE` книги | Bearer JWT + проверка видимости | Приватные, reserved и exchanged чужие книги скрываются как `404` |
| `book-service` внутренние API | `/internal/books/{id}`, `/reserve`, `/available`, `/exchanged` | `exchange-service` и доверенные сервисы с `X-Internal-Token` | `INTERNAL_SERVICE` authority | Используются для согласования состояний книг в обменах |
| `exchange-service` создание обмена | `POST /api/v1/exchanges` | Аутентифицированный пользователь | Bearer JWT + бизнес-проверки | Можно предложить только свою книгу и только по публичной книге другого владельца |
| `exchange-service` просмотр своих обменов | `/api/v1/exchanges/my/outgoing`, `/my/incoming` | Участник обмена по роли `requester` или `owner` | Bearer JWT | Доступ ограничен собственными списками |
| `exchange-service` принятие / отклонение | `POST /api/v1/exchanges/{id}/accept`, `/decline` | Только владелец запрошенной книги | Проверка `ownerId` | Невладелец получает `403` |
| `exchange-service` отмена | `POST /api/v1/exchanges/{id}/cancel` | Только инициатор запроса | Проверка `requesterId` | После начала подтверждения завершения отмена блокируется |
| `exchange-service` завершение | `POST /api/v1/exchanges/{id}/complete` | Только участники обмена | Проверка `isParticipant` | Требуются подтверждения обеих сторон |
| `notification-service` уведомления | `/api/v1/notifications`, `/unread-count`, `/{id}/read`, `/read-all` | Только пользователь-владелец уведомлений | Bearer JWT + фильтрация по `userId` | Чтение и изменение доступны только по своему `userId` |
| Технические endpoints | `/actuator/health`, `/actuator/info`, Swagger, OpenAPI | Анонимный клиент | `permitAll` | Во всех сервисах открыты без JWT |
| Технические endpoints с метриками | `/actuator/metrics`, где endpoint включен в exposure | Анонимный клиент на уровне приложения | `permitAll` | В production должны дополнительно ограничиваться на уровне ingress/firewall/admin network |

## Отдельные замечания

- Внутренние API есть только у `user-service` и `book-service`; `exchange-service` и `notification-service` выступают как потребители, а не как провайдеры внутренних endpoint'ов.
- `auth-service` и `book-service` используют различную модель доступа: auth-потоки публичны, а управление книгами всегда привязано к владельцу.
- Для production-контура рекомендуется считать Swagger и Actuator техническими интерфейсами и не публиковать их в открытый интернет без отдельной необходимости.
