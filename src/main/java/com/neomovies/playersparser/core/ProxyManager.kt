package com.neomovies.playersparser.core

/**
 * Менеджер для управления прокси и fallback стратегией
 */
class ProxyManager(private val proxies: List<String> = emptyList()) {
    private var currentIndex = 0

    /**
     * Получить текущий прокси
     */
    fun get(): String? {
        return if (proxies.isEmpty()) null else proxies[currentIndex % proxies.size]
    }

    /**
     * Переключиться на следующий прокси (fallback)
     */
    fun refresh() {
        if (proxies.isNotEmpty()) {
            currentIndex++
        }
    }

    /**
     * Получить список всех прокси
     */
    fun getAll(): List<String> = proxies

    /**
     * Проверить наличие прокси
     */
    fun hasProxies(): Boolean = proxies.isNotEmpty()
}
