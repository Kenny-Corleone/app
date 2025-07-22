package com.soma.plugin.runtime

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Общий контекст для обмена данными между плагинами
 */
object SharedContext {
    
    private val logger = LoggerFactory.getLogger(SharedContext::class.java)
    
    // Основное хранилище данных
    private val storage = ConcurrentHashMap<String, Any>()
    
    // Временные данные с TTL
    private val temporaryStorage = ConcurrentHashMap<String, TimedValue>()
    
    // Подписчики на изменения данных
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<DataChangeListener>>()
    
    /**
     * Получение значения по ключу
     */
    fun <T> get(key: String): T? {
        cleanupExpiredValues()
        
        // Сначала проверяем временное хранилище
        temporaryStorage[key]?.let { timedValue ->
            if (!timedValue.isExpired()) {
                @Suppress("UNCHECKED_CAST")
                return timedValue.value as? T
            } else {
                temporaryStorage.remove(key)
            }
        }
        
        // Затем основное хранилище
        @Suppress("UNCHECKED_CAST")
        return storage[key] as? T
    }
    
    /**
     * Установка значения
     */
    fun <T> set(key: String, value: T) {
        val oldValue = storage.put(key, value as Any)
        logger.debug("Set value for key: $key")
        
        // Уведомление подписчиков
        notifyListeners(key, oldValue, value)
    }
    
    /**
     * Установка временного значения с TTL
     */
    fun <T> setTemporary(key: String, value: T, ttlMs: Long) {
        val expiresAt = System.currentTimeMillis() + ttlMs
        val oldValue = temporaryStorage.put(key, TimedValue(value as Any, expiresAt))?.value
        logger.debug("Set temporary value for key: $key (TTL: ${ttlMs}ms)")
        
        // Уведомление подписчиков
        notifyListeners(key, oldValue, value)
    }
    
    /**
     * Удаление значения
     */
    fun remove(key: String): Any? {
        val removedFromStorage = storage.remove(key)
        val removedFromTemp = temporaryStorage.remove(key)?.value
        val removed = removedFromTemp ?: removedFromStorage
        
        if (removed != null) {
            logger.debug("Removed value for key: $key")
            notifyListeners(key, removed, null)
        }
        
        return removed
    }
    
    /**
     * Проверка существования ключа
     */
    fun contains(key: String): Boolean {
        cleanupExpiredValues()
        return storage.containsKey(key) || 
               (temporaryStorage[key]?.let { !it.isExpired() } ?: false)
    }
    
    /**
     * Получение всех ключей
     */
    fun keys(): Set<String> {
        cleanupExpiredValues()
        return storage.keys + temporaryStorage.keys
    }
    
    /**
     * Очистка всех данных
     */
    fun clear() {
        val keys = keys()
        storage.clear()
        temporaryStorage.clear()
        listeners.clear()
        
        logger.info("Cleared SharedContext (${keys.size} keys)")
    }
    
    /**
     * Подписка на изменения данных
     */
    fun subscribe(key: String, listener: DataChangeListener) {
        listeners.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(listener)
        logger.debug("Added listener for key: $key")
    }
    
    /**
     * Отписка от изменений данных
     */
    fun unsubscribe(key: String, listener: DataChangeListener) {
        listeners[key]?.remove(listener)
        logger.debug("Removed listener for key: $key")
    }
    
    /**
     * Получение статистики
     */
    fun getStats(): SharedContextStats {
        cleanupExpiredValues()
        return SharedContextStats(
            totalKeys = storage.size + temporaryStorage.size,
            persistentKeys = storage.size,
            temporaryKeys = temporaryStorage.size,
            totalListeners = listeners.values.sumOf { it.size }
        )
    }
    
    // Приватные методы
    
    private fun cleanupExpiredValues() {
        val now = System.currentTimeMillis()
        val expired = temporaryStorage.filter { it.value.isExpired(now) }
        
        expired.forEach { (key, timedValue) ->
            temporaryStorage.remove(key)
            notifyListeners(key, timedValue.value, null)
        }
        
        if (expired.isNotEmpty()) {
            logger.debug("Cleaned up ${expired.size} expired values")
        }
    }
    
    private fun notifyListeners(key: String, oldValue: Any?, newValue: Any?) {
        listeners[key]?.forEach { listener ->
            try {
                listener.onDataChanged(key, oldValue, newValue)
            } catch (e: Exception) {
                logger.error("Error notifying listener for key: $key", e)
            }
        }
    }
    
    /**
     * Значение с временем жизни
     */
    private data class TimedValue(
        val value: Any,
        val expiresAt: Long
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
            return now > expiresAt
        }
    }
}

/**
 * Интерфейс для прослушивания изменений данных в SharedContext
 */
interface DataChangeListener {
    fun onDataChanged(key: String, oldValue: Any?, newValue: Any?)
}

/**
 * Статистика SharedContext
 */
data class SharedContextStats(
    val totalKeys: Int,
    val persistentKeys: Int,
    val temporaryKeys: Int,
    val totalListeners: Int
)

/**
 * Удобные extension функции для работы с SharedContext
 */

/**
 * Получение или установка значения по умолчанию
 */
inline fun <T> SharedContext.getOrPut(key: String, defaultValue: () -> T): T {
    return get<T>(key) ?: defaultValue().also { set(key, it) }
}

/**
 * Обновление значения с функцией трансформации
 */
inline fun <T> SharedContext.update(key: String, transform: (T?) -> T) {
    val currentValue = get<T>(key)
    val newValue = transform(currentValue)
    set(key, newValue)
}

/**
 * Increment числового значения
 */
fun SharedContext.increment(key: String, delta: Int = 1): Int {
    val current = get<Int>(key) ?: 0
    val new = current + delta
    set(key, new)
    return new
}
