package com.soma.plugin.runtime

/**
 * Интерфейс для прослушивания событий EventBus
 */
interface EventBusListener {
    
    /**
     * Обработка события
     * @param event событие для обработки
     */
    fun onEvent(event: PluginEvent)
    
    /**
     * Фильтр событий - определяет какие события должен получать этот listener
     * По умолчанию принимает все события
     * @param event событие для проверки
     * @return true если событие должно быть обработано
     */
    fun shouldHandle(event: PluginEvent): Boolean = true
    
    /**
     * Приоритет обработки события (больше = выше приоритет)
     * Listeners с высшим приоритетом получают события первыми
     */
    val priority: Int get() = 0
    
    /**
     * Уникальный идентификатор listener'а
     */
    val listenerId: String get() = this.javaClass.simpleName
}

/**
 * Базовая реализация EventBusListener с фильтрацией по типам событий
 */
abstract class FilteredEventBusListener(
    private val eventTypes: Set<String>
) : EventBusListener {
    
    constructor(vararg eventTypes: String) : this(eventTypes.toSet())
    
    override fun shouldHandle(event: PluginEvent): Boolean {
        return eventTypes.isEmpty() || event.type in eventTypes
    }
}

/**
 * EventBusListener для конкретного типа события
 */
abstract class TypedEventBusListener(
    private val eventType: String
) : EventBusListener {
    
    override fun shouldHandle(event: PluginEvent): Boolean {
        return event.type == eventType
    }
}
