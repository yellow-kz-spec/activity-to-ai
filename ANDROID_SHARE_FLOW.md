# Android Share Flow

## Intent

Текущий рабочий Garmin-путь зарегистрирован как получатель:

- `ACTION_SEND`
- MIME type: `text/plain`

## Вход

Приложение принимает текст из `Intent.EXTRA_TEXT`.

Из текста необходимо извлечь первую валидную ссылку Garmin Connect.

Поддерживаемый пример:

`https://connect.garmin.com/modern/activity/{activity_id}?share_unique_id={value}`

Также поддерживается фактический формат Android/Web Garmin Connect:

`https://connect.garmin.com/app/activity/{activity_id}?share_unique_id={value}`

Разрешается только HTTPS, точный хост `connect.garmin.com`, числовой идентификатор активности и непустой `share_unique_id`.

## Strava input (under development)

Strava на Android отправляет `ACTION_SEND` с MIME type `image/*`, URL в `Intent.EXTRA_TEXT` и image stream. Изображение не планируется использовать как источник данных. Share URL должен использоваться для идентификации активности, после чего подробности и high-resolution streams будут получены через Strava API с OAuth.

Публичная Strava share page ведет в login/auth flow и не является аналогом публичной Garmin activity page. Получение и Markdown-экспорт Strava-активности пока не реализованы.

## Поведение

- Не открывать полноценный рабочий экран после Share; допускается отдельная однократная настройка OAuth для источников, которым она нужна.
- Запустить обработку сразу после получения ссылки.
- При необходимости показать компактное системное состояние обработки.
- После завершения закрыть временную Activity.
- Сообщить пользователю итог через Toast, Snackbar временной Activity или системное уведомление.

## Повторная отправка

Каждая новая share-ссылка создает отдельный файл.
Существующие файлы не перезаписываются без явной безопасной стратегии именования.
