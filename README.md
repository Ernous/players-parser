# Players Parser

Kotlin библиотека для парсинга видео плееров и потоковых источников. Полная реализация логики из проекта **Lampac** и **go-hdrezka**, адаптированная под JVM/Android.

Поддерживает обход защиты (Cloudflare/DDoS-Guard) через эмуляцию браузерных сессий, умное декодирование ссылок ("trash" decoder) и извлечение HLS/DASH потоков.

[![](https://jitpack.io/v/Ernous/players-parser.svg)](https://jitpack.io/#Ernous/players-parser)

## Поддерживаемые источники

| Источник | Тип | Возможности                                                                             |
|----------|-----|-----------------------------------------------------------------------------------------|
| **HDRezka** | HTML/AJAX | Поиск, Сериалы/Фильмы, 720p, Выбор озвучки, **Smart Trash Decoder**, **Cookie Session** |
| **Collaps** | API/Iframe | DASH/HLS, Выбор качества, Авто-выбор лучшего потока                                     |
| **VideoHUB** | API | Прямые ссылки, JSON API, CDN Video Hub                                                  |

## Установка

### Шаг 1: Добавьте JitPack репозиторий

В файл `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Шаг 2: Добавьте зависимость

В файл `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Ernous:players-parser:1.0.1")
}
```

## Использование

### 1. Инициализация

Вы можете использовать отдельные парсеры или общий менеджер.

```kotlin
// Для Rezka рекомендуется указывать рабочее зеркало
val rezkaSettings = RezkaSettings(baseUrl = "https://hdrezka.me")
val rezkaParser = RezkaParser(rezkaSettings)

val collapsParser = CollapsParser()
```

### 2. Поиск контента

```kotlin
val query = "Во все тяжкие"
val searchResponse = rezkaParser.search(query)

if (searchResponse.error == null) {
    searchResponse.results.forEach { item ->
        // Приведение типа к конкретной модели парсера
        if (item is RezkaSearchItem) {
            println("Found: ${item.title} (${item.year}) - ${item.url}")
        }
    }
}
```

### 3. Получение потока (Плеер)

Библиотека автоматически обрабатывает шифрование ссылок и сессии.

```kotlin
val response = rezkaParser.getPlayer(
    id = "https://hdrezka.me/series/thriller/646-vo-vse-tyazhkie-2008-latest.html", // URL или ID
    type = "series", // "movie" или "series"
    season = 1,
    episode = 1
)

if (response.success) {
    println("Main Stream: ${response.url}")
    
    // Список доступных качеств
    response.playlist?.forEach { stream ->
        println("- [${stream.quality}] ${stream.type}: ${stream.url}")
    }
} else {
    println("Error: ${response.error}")
}
```

### 4. Информация о сериалах

Получение списка сезонов и эпизодов (для Collaps и VideoHub).

```kotlin
val seriesInfo = collapsParser.getSeriesInfo("464963") // Kinopoisk ID
seriesInfo?.forEach { (season, episodes) ->
    println("Season $season: ${episodes.size} episodes")
}
```

## Особенности реализации

*   **Rezka Smart Decoder**: Реализован алгоритм декодирования ссылок `#h...` с поддержкой удаления "мусорных" строк и Base64 (порт логики из `go-hdrezka`).
*   **Session Management**: Используется `CookieJar` для сохранения сессионных кук (`hdmbbs`, `dle_user_taken`, `PHPSESSID`) между запросами, что позволяет обходить защиту "Время сессии истекло".
*   **Inline Extraction**: Парсер умеет извлекать ссылки прямо из HTML страницы (через `initCDN...` скрипты), минимизируя количество сетевых запросов.
*   **Headers Spoofing**: Эмуляция заголовков официальных приложений и браузеров.

## Структура проекта

```
src/main/java/com/neomovies/playersparser/
├── models/
│   ├── CommonModels.kt          # Общие модели (PlayerResponse, PlaylistItem)
│   ├── RezkaModels.kt           # Модели и Decoder для Rezka
│   └── CollapsModels.kt         # Модели для Collaps
├── parsers/
│   ├── BaseParser.kt            # Базовый HTTP клиент с OkHttp
│   ├── RezkaParser.kt           # Парсер HDRezka (Cookies + Regex + AJAX)
│   ├── CollapsParser.kt         # Парсер Collaps (Iframe extraction)
│   └── VideoHubParser.kt        # Парсер VideoHUB
└── PlayersParserManager.kt      # Фасад (опционально)
```

## Зависимости

- **Kotlin**: 2.1.0
- **OkHttp3**: 4.12.0 (HTTP/2, CookieJar, Interceptors)
- **org.json**: 20240303 (Легковесный JSON парсинг)
- **Coroutines**: 1.10.1 (Асинхронность)

## Благодарности
*   **[immisterio/Lampac](https://github.com/immisterio/Lampac)**
*   **[n0madic/go-hdrezka](https://github.com/n0madic/go-hdrezka)**

## Лицензия
[Apache 2.0](LICENSE)