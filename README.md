# Players Parser

Мощная Kotlin библиотека для парсинга видео плееров и потоковых источников. Полная реализация логики из Lampac проекта с поддержкой Rezka, Collaps (DASH/HLS) и VideoHUB.

[![](https://jitpack.io/v/Ernous/players-parser.svg)](https://jitpack.io/#Ernous/players-parser)

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
    implementation("com.github.Ernous:players-parser:1.0.0")
}
```

## Использование

### Инициализация

```kotlin
val manager = PlayersParserManager()
```

### Поиск контента

```kotlin
// Поиск в конкретном источнике
val results = manager.search("rezka", "Inception")
results.results.forEach { result ->
    println("${result.name} (${result.year}) - ${result.type}")
}

// Поиск во всех источниках
val allResults = manager.searchInAllSources("Breaking Bad")
allResults.forEach { (source, response) ->
    println("$source: ${response.results.size} results")
}
```

### Получение плеера

```kotlin
// Получить плеер из конкретного источника
val player = manager.getPlayer(
    source = "rezka",
    id = "inception-2010",
    type = "movie"
)

if (player.success) {
    println("URL: ${player.url}")
}

// Получить плеер из первого доступного источника (fallback)
val player = manager.getPlayerFromMultipleSources(
    sources = listOf("rezka", "collaps", "videohub"),
    id = "tt0903747",
    type = "series",
    season = 1,
    episode = 1
)
```

### Информация о сериалах

```kotlin
val seriesInfo = manager.getSeriesInfo("rezka", "breaking-bad")
seriesInfo?.forEach { (season, episodes) ->
    println("Season $season: ${episodes.size} episodes")
}
```

### Кастомные настройки

```kotlin
// Rezka с кастомными параметрами
val rezkaSettings = RezkaSettings(
    host = "https://hdrezka.ag",
    login = "your_login",
    passwd = "your_password",
    ajax = true,  // Использовать AJAX режим
    proxies = listOf("http://proxy1:8080", "http://proxy2:8080")
)
manager.registerRezka(rezkaSettings)

// Collaps с токеном (токен уже встроен из Lampac)
val collapsSettings = CollapsSettings(
    apiHost = "https://api.bhcesh.me",
    token = "eedefb541aeba871dcfc756e6b31c02e",
    useDash = false,
    two = true,  // Режим two
    reserve = false,
    vast = false
)
manager.registerCollaps(collapsSettings)

// VideoHUB с кастомным pubId (pubId = 12 из Lampac)
val videoHubSettings = VideoHubSettings(
    apiHost = "https://videohub.ru",
    pubId = "12"
)
manager.registerVideoHub(videoHubSettings)
```

## Модели данных

### PlayerResponse

```kotlin
data class PlayerResponse(
    val url: String? = null,
    val urls: List<String>? = null,
    val playlist: List<PlaylistItem>? = null,
    val error: String? = null,
    val success: Boolean = true
)
```

### SearchResult

```kotlin
data class SearchResult(
    val id: String,
    val name: String,
    val type: String, // "movie" или "series"
    val year: Int? = null,
    val poster: String? = null
)
```

### PlaylistItem

```kotlin
data class PlaylistItem(
    val url: String,
    val quality: String? = null,
    val type: String? = null // "video", "audio", "subtitle", "hls", "dash"
)
```

## Примеры

Смотрите файл `Example.kt` для полных примеров использования.

## Архитектура

### Структура проекта
```
src/main/java/com/neomovies/playersparser/
├── core/
│   ├── ProxyManager.kt          # Управление прокси с fallback
│   └── MemoryCache.kt           # In-memory кэш с TTL
├── models/
│   ├── PlayerResponse.kt        # Основные модели ответов
│   ├── RezkaModels.kt           # Модели для Rezka парсера
│   ├── CollapsModels.kt         # Модели для Collaps парсера
│   └── VideoHubModels.kt        # Модели для VideoHUB парсера
├── parsers/
│   ├── BaseParser.kt            # Базовый класс с HTTP логикой
│   ├── RezkaParser.kt           # Парсер для HDRezka
│   ├── CollapsParser.kt         # Парсер для Collaps
│   └── VideoHubParser.kt        # Парсер для VideoHUB
├── PlayersParserManager.kt      # Главный менеджер парсеров
└── Example.kt                   # Примеры использования
```

### Ключевые особенности

- **Полная совместимость с Lampac**: Все токены и параметры взяты прямо из Lampac проекта
- **Асинхронность**: Все операции используют Kotlin coroutines (suspend функции)
- **Кэширование**: In-memory кэш с TTL для оптимизации производительности
- **Proxy поддержка**: Встроенная поддержка HTTP прокси с fallback стратегией
- **Расширенные headers**: Поддержка X-Real-IP, X-Forwarded-For, X-App-Hdrezka-App
- **Потокобезопасность**: Использование Mutex для синхронизации доступа к кэшу

## Зависимости

- **Kotlin**: 2.1.0
- **OkHttp3**: 4.12.0 (HTTP клиент)
- **org.json**: 20240303 (JSON парсинг)
- **Coroutines**: 1.10.1 (асинхронные операции)
- **Gson**: 2.11.0 (JSON сериализация)

## Лицензия

[Apache 2.0](LICENSE)

## Автор

Erno

## Благодарности

Спасибо проекту [Lampac](https://github.com/immisterio/Lampac).