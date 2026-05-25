# 03. Реестр персональных данных

## Общая модель владения данными

В `Second Shelf` данные распределены по доменам:

- `auth-service` отвечает за сессии и токены;
- `user-service` является авторитетным владельцем профилей пользователей;
- `book-service` хранит привязку книги к владельцу через `ownerId`;
- `exchange-service` хранит взаимодействия пользователей в рамках обменов;
- `notification-service` хранит производные уведомления для конкретного пользователя.

## Реестр данных

| Категория данных | Поля | Субъект данных | Сервис-владелец | Хранение | Назначение | Примечание |
| --- | --- | --- | --- | --- | --- | --- |
| Учетная запись и профиль | `username`, `email`, `firstName`, `lastName`, `city`, `about`, `userId` | Пользователь платформы | `user-service` | `users_db.users` | Регистрация, профиль, отображение в API | Основной источник персональных данных |
| Аутентификационные данные | пароль в открытом виде только в момент ввода; bcrypt-хэш пароля | Пользователь платформы | `user-service` | `users_db.users.password` | Логин и проверка учетных данных | В коде пароль хэшируется через `BCryptPasswordEncoder` |
| Регистрационный транзит | `username`, `email`, `firstName`, `lastName`, `city`, `about`, пароль | Новый пользователь | `auth-service` транзитно, `user-service` постоянно | HTTP запросы, затем `users_db` | Создание учетной записи | `auth-service` не хранит профильные поля как собственный источник данных |
| Сессии и токены | `userId`, refresh token hash, даты создания/истечения/отзыва | Пользователь платформы | `auth-service` | `auth_db.refresh_tokens` | Управление сессиями, logout, logout-all | Сырые refresh-токены в БД не сохраняются |
| Профильные ответы API | `username`, `email`, `firstName`, `lastName`, `city`, `about`, `userId` | Пользователь платформы | `user-service` | HTTP ответы `/api/v1/users/**` | Отображение профиля другим авторизованным пользователям | Чтение доступно авторизованным пользователям |
| Данные владения книгой | `ownerId` | Пользователь-владелец книги | `book-service` | `books_db.books` | Привязка книги к владельцу и owner-based authorization | Само описание книги не относится к ПДн, но `ownerId` относится |
| Сообщение обмена | `message`, `requesterId`, `ownerId`, `requestedBookId`, `offeredBookId` | Участники обмена | `exchange-service` | `exchange_db.exchange_requests` | Коммуникация в процессе обмена книгами | Сообщение может содержать персональные сведения, введенные пользователем |
| Снимки данных обмена | `initiatorUsername`, `requestedBookTitle`, `requestedBookAuthor`, `offeredBookTitle`, `offeredBookAuthor`, `requestMessage`, `requesterId`, `ownerId` | Участники обмена | `exchange-service` | `exchange_db.outbox_events.payload` | Формирование доменных событий и уведомлений | Содержимое outbox хранится до операционного обслуживания |
| Уведомления | `userId`, `title`, `message`, `relatedEntityId` | Пользователь-получатель | `notification-service` | `notification_db.notifications` | Доставка in-app уведомлений | Текст уведомления может включать `username` инициатора и текст сообщения обмена |
| Идентификаторы обработки событий | `eventId`, `eventType` | Участники обмена косвенно | `notification-service` | `notification_db.processed_events` | Идемпотентность обработки событий | Сами поля не являются ПДн, но относятся к событиям с ПДн |

## Источники получения данных

- пользовательские HTTP-запросы в `auth-service`, `user-service`, `book-service`, `exchange-service`;
- внутренние вызовы `auth-service -> user-service`;
- внутренние вызовы `exchange-service -> book-service`;
- RabbitMQ-события `exchange-service -> notification-service`.

## Особенности текущей реализации

- `user-service` хранит полные профильные данные и пароль в виде bcrypt-хэша;
- `auth-service` хранит только метаданные refresh-сессий и `userId`;
- `book-service` не хранит `username` или `email`, но хранит `ownerId`;
- `exchange-service` хранит сообщения обмена и идентификаторы участников;
- `notification-service` хранит производный текст уведомлений, который может содержать фрагменты сообщения обмена.

## Замечания по срокам хранения

На уровне runtime-кода автоматическая очистка профилей, обменов, outbox-событий и уведомлений не реализована.
Фактические сроки хранения должны быть утверждены регламентом эксплуатации и, при необходимости,
дополнительно обеспечены внешними задачами архивирования или отдельной доработкой приложения.
