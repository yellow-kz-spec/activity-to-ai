# Roadmap

## Current baseline — Garmin on Android

- прием Garmin Share Link через Android Share Sheet;
- автоматическое определение типа тренировки;
- извлечение доступных публичных данных;
- исключение карты и GPS-координат из Markdown;
- генерация Markdown и сохранение через Storage Access Framework;
- постоянное разрешение выбранной папки и безопасные имена файлов.

## Near term — Strava source

- завершить обработку Strava share intent и определение активности;
- использовать Strava API, а не изображение или анонимный HTML share page;
- получать подробные данные и высокоточные activity streams;
- преобразовать Strava-данные в общую модель с расширениями источника;
- экспортировать через общий Markdown pipeline;
- вынести token exchange/refresh в HTTPS backend/proxy до публичного релиза.

## Architecture evolution

- выделить Source Detector и адаптеры Garmin/Strava;
- ввести `NormalizedActivity` с source-specific enrichment;
- отделить Markdown exporter от получения данных конкретной платформы.

## Future enhancements

- дополнительные адаптеры платформ и файлов (например, COROS, Suunto, Polar, Fitbit, Apple-экспорты, FIT/TCX/GPX);
- настройки плотности временных рядов;
- JSON-экспорт;
- публикация приложения.

Эти пункты показывают направление расширения и не означают текущую поддержку.

