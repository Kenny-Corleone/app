package com.soma.plugin.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Интерфейс для управления сессией плагина
 */
interface PluginSession {
    /**
     * Уникальный идентификатор сессии
     */
    val sessionId: String
    
    /**
     * Время создания сессии
     */
    val createdAt: Long
    
    /**
     * Изменяемые метаданные сессии
     */
    val metadata: MutableMap<String, Any>
    
    /**
     * Проверка активности сессии
     */
    val isActive: Boolean
    
    /**
     * Время последнего доступа к сессии
     */
    val lastAccessTime: Long
    
    /**
     * Обновление времени последнего доступа
     */
    fun touch()
    
    /**
     * Закрытие сессии
     */
    fun close()
    
    /**
     * Получение значения из метаданных с типизацией
     */
    fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return metadata[key] as? T
    }
    
    /**
     * Установка значения в метаданных
     */
    fun <T> set(key: String, value: T) {
        metadata[key] = value as Any
    }
    
    /**
     * Удаление значения из метаданных
     */
    fun remove(key: String): Any? {
        return metadata.remove(key)
    }
}

/**
 * Реализация PluginSession по умолчанию
 */
class DefaultPluginSession(
    override val sessionId: String = UUID.randomUUID().toString(),
    override val createdAt: Long = System.currentTimeMillis()
) : PluginSession {
    
    override val metadata: MutableMap<String, Any> = ConcurrentHashMap()
    
    @Volatile
    private var _isActive: Boolean = true
    
    @Volatile
    private var _lastAccessTime: Long = createdAt
    
    override val isActive: Boolean
        get() = _isActive
    
    override val lastAccessTime: Long
        get() = _lastAccessTime
    
    override fun touch() {
        _lastAccessTime = System.currentTimeMillis()
    }
    
    override fun close() {
        _isActive = false
        metadata.clear()
    }
    
    override fun toString(): String {
        return "PluginSession(id=$sessionId, active=$isActive, created=$createdAt, lastAccess=$lastAccessTime)"
    }
}

/**
 * Менеджер сессий плагинов
 */
object PluginSessionManager {
    
    private val sessions = ConcurrentHashMap<String, PluginSession>()
    
    /**
     * Создание новой сессии
     */
    fun createSession(): PluginSession {
        val session = DefaultPluginSession()
        sessions[session.sessionId] = session
        return session
    }
    
    /**
     * Получение сессии по ID
     */
    fun getSession(sessionId: String): PluginSession? {
        return sessions[sessionId]?.also { it.touch() }
    }
    
    /**
     * Удаление сессии
     */
    fun removeSession(sessionId: String): PluginSession? {
        return sessions.remove(sessionId)?.also { it.close() }
    }
    
    /**
     * Получение всех активных сессий
     */
    fun getActiveSessions(): List<PluginSession> {
        return sessions.values.filter { it.isActive }
    }
    
    /**
     * Очистка неактивных сессий
     */
    fun cleanupInactiveSessions() {
        val inactive = sessions.filter { !it.value.isActive }
        inactive.forEach { (sessionId, _) ->
            sessions.remove(sessionId)
        }
    }
    
    /**
     * Очистка старых сессий (старше указанного времени в миллисекундах)
     */
    fun cleanupOldSessions(maxAge: Long) {
        val now = System.currentTimeMillis()
        val oldSessions = sessions.filter { (_, session) ->
            now - session.lastAccessTime > maxAge
        }
        oldSessions.forEach { (sessionId, session) ->
            session.close()
            sessions.remove(sessionId)
        }
    }
}
