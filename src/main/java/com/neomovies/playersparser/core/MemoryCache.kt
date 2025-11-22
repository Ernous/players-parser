package com.neomovies.playersparser.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory кэш с TTL поддержкой
 */
object MemoryCache {
    private val data = mutableMapOf<String, CacheEntry<*>>()
    private val mutex = Mutex()

    private data class CacheEntry<T>(
        val value: T,
        val timestamp: Long,
        val ttlMillis: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMillis
    }

    /**
     * Получить значение из кэша или загрузить через loader
     * @param key ключ кэша
     * @param ttlSeconds время жизни в секундах
     * @param loader функция для загрузки значения
     */
    suspend fun <T> get(
        key: String,
        ttlSeconds: Int = 600,
        loader: suspend () -> T
    ): T {
        mutex.withLock {
            val cached = data[key]
            if (cached != null && !cached.isExpired()) {
                @Suppress("UNCHECKED_CAST")
                return cached.value as T
            }

            val value = loader()
            data[key] = CacheEntry(value, System.currentTimeMillis(), ttlSeconds * 1000L)
            return value
        }
    }

    /**
     * Очистить весь кэш
     */
    suspend fun clear() {
        mutex.withLock {
            data.clear()
        }
    }

    /**
     * Удалить конкретный ключ
     */
    suspend fun remove(key: String) {
        mutex.withLock {
            data.remove(key)
        }
    }

    /**
     * Получить размер кэша
     */
    suspend fun size(): Int {
        mutex.withLock {
            return data.size
        }
    }
}
