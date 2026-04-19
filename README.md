# Fomenki Bot

Telegram-бот для отслеживания доступности билетов в [Мастерской Петра Фоменко](https://fomenki.ru). Бот является лёгким клиентом: вся логика проверки билетов и хранение данных вынесены на бэкенд. Бот принимает уведомления от бэкенда через вебхук и пересылает их подписчикам в Telegram.

## Стек

- **Kotlin 2.1.20** (JDK 21)
- **kotlin-telegram-bot** — фреймворк для Telegram Bot API (polling)
- **Ktor** — HTTP-сервер для приёма вебхуков и HTTP-клиент для обращения к бэкенду
- **kotlinx-serialization** — JSON-сериализация DTO
- **Docker** — контейнеризация и деплой

## Архитектура

```
Бэкенд
  │  POST /webhook/notifications  (X-Webhook-Secret)
  ▼
Fomenki Bot (Ktor, порт 8084)
  │  sendMessage
  ▼
Telegram
```

Пользователи управляют подписками через команды бота, который проксирует запросы к бэкенду. Бэкенд самостоятельно проверяет наличие билетов и присылает уведомления боту через вебхук.

## Команды бота

| Команда | Описание |
|---|---|
| `/perfs` | Список спектаклей с кнопками подписки/отписки |
| `/mysubs` | Мои подписки со ссылками на спектакли |

## Переменные окружения

| Переменная | Описание | По умолчанию |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота (обязательно) | — |
| `BACKEND_URL` | URL бэкенда | `http://localhost:8080` |
| `FOMENKI_API_KEY` | API-ключ для авторизации запросов к бэкенду | `fomenki-secret` |
| `WEBHOOK_SECRET` | Секрет для проверки входящих вебхуков | `webhook-secret` |
| `WEBHOOK_PORT` | Порт для приёма вебхуков | `8084` |
| `DEV_MODE` | `1` — режим разработки (вебхук-сервер не запускается) | `0` |

## Запуск

### Локальный запуск

```bash
export TELEGRAM_BOT_TOKEN=<токен>
export BACKEND_URL=http://localhost:8080
export FOMENKI_API_KEY=<ключ>
export DEV_MODE=1
./gradlew shadowJar && java -jar build/libs/fomenki-bot-all.jar
```

### Docker

Создайте `.env` файл:

```env
TAG=0.0.1
TELEGRAM_BOT_TOKEN=<токен>
BACKEND_URL=https://your-backend.example.com
FOMENKI_API_KEY=<ключ>
WEBHOOK_SECRET=<секрет>
WEBHOOK_PORT=8084
```

Запуск:

```bash
docker compose up -d
```

## Сборка

```bash
./gradlew build        # сборка проекта
./gradlew shadowJar    # fat JAR со всеми зависимостями
./gradlew test         # запуск тестов
```

## Деплой

CI/CD настроен через GitHub Actions. При пуше в `master`:

1. Собирается Docker-образ и публикуется в DockerHub
2. На удалённый сервер копируются `.env` и `docker-compose.yml`
3. Сервер подтягивает новый образ и перезапускает контейнер
