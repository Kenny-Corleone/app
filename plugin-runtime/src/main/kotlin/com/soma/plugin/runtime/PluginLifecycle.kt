package com.soma.plugin.runtime

/**
 * Контекст плагина, предоставляемый при загрузке
 */
interface PluginContext {
    /**
     * Имя плагина
     */
    val pluginName: String
    
    /**
     * Версия плагина
     */
    val version: String
    
    /**
     * Сессия плагина
     */
    val session: PluginSession
    
    /**
     * Общий контекст для обмена данными между плагинами
     */
    val sharedContext: SharedContext
    
    /**
     * EventBus для публикации и подписки на события
     */
    val eventBus: EventBus
    
    /**
     * Конфигурация плагина
     */
    val config: Map<String, Any>
    
    /**
     * Логгер плагина
     */
    val logger: org.slf4j.Logger
}

/**
 * Реализация PluginContext по умолчанию
 */
class DefaultPluginContext(
    override val pluginName: String,
    override val version: String = "1.0.0",
    override val session: PluginSession = PluginSessionManager.createSession(),
    override val sharedContext: SharedContext = SharedContext,
    override val eventBus: EventBus = EventBus,
    override val config: Map<String, Any> = emptyMap()
) : PluginContext {
    
    override val logger: org.slf4j.Logger = 
        org.slf4j.LoggerFactory.getLogger("plugin.$pluginName")
}

/**
 * Интерфейс жизненного цикла плагина
 */
interface PluginLifecycle {
    
    /**
     * Вызывается при загрузке плагина
     * @param context контекст плагина
     */
    fun onLoad(context: PluginContext)
    
    /**
     * Вызывается при выгрузке плагина
     */
    fun onUnload()
    
    /**
     * Вызывается при приостановке плагина
     */
    fun onSuspend() {}
    
    /**
     * Вызывается при возобновлении работы плагина
     */
    fun onResume() {}
    
    /**
     * Вызывается при изменении конфигурации плагина
     * @param newConfig новая конфигурация
     */
    fun onConfigChanged(newConfig: Map<String, Any>) {}
    
    /**
     * Проверка готовности плагина к работе
     */
    fun isReady(): Boolean = true
    
    /**
     * Получение информации о состоянии плагина
     */
    fun getStatus(): PluginStatus = PluginStatus.ACTIVE
}

/**
 * Состояние плагина
 */
enum class PluginStatus {
    LOADING,
    ACTIVE,
    SUSPENDED,
    UNLOADING,
    ERROR
}
