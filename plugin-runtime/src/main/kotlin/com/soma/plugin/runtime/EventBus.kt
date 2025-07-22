package com.soma.plugin.runtime

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Система событий для плагинов
 */
object EventBus {
    
    private val logger = LoggerFactory.getLogger(EventBus::class.java)
    
    // Все зарегистрированные listeners
    private val listeners = CopyOnWriteArrayList<EventBusListener>()
    
    // Listeners по типам событий для быстрого поиска
    private val listenersByType = ConcurrentHashMap<String, CopyOnWriteArrayList<EventBusListener>>()
    
    // Scope для async обработки событий
    private val eventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Статистика
    @Volatile
    private var totalEventsPosted = 0L
    @Volatile
    private var totalEventsProcessed = 0L
    
    /**
     * Подписка на события
     */
    fun subscribe(listener: EventBusListener) {
        listeners.add(listener)
        logger.debug("Subscribed listener: ${listener.listenerId}")
        
        // Уведомление о подписке
        post(PluginEvent("eventbus.listener.subscribed", listener.listenerId, "core"))
    }
    
    /**
     * Отписка от событий
     */
    fun unsubscribe(listener: EventBusListener) {
        listeners.remove(listener)
        
        // Удаление из кеша по типам
        listenersByType.values.forEach { it.remove(listener) }
        
        logger.debug("Unsubscribed listener: ${listener.listenerId}")
        
        // Уведомление об отписке
        post(PluginEvent("eventbus.listener.unsubscribed", listener.listenerId, "core"))
    }
    
    /**
     * Публикация события (синхронно)
     */
    fun post(event: PluginEvent) {
        totalEventsPosted++
        logger.debug("Posted event: ${event.type} from ${event.source}")
        
        // Получаем подходящих listeners
        val relevantListeners = getRelevantListeners(event)
        
        // Обрабатываем событие синхронно
        processEvent(event, relevantListeners)
    }
    
    /**
     * Публикация события (асинхронно)
     */
    fun postAsync(event: PluginEvent): Job {
        totalEventsPosted++
        logger.debug("Posted async event: ${event.type} from ${event.source}")
        
        return eventScope.launch {
            try {
                val relevantListeners = getRelevantListeners(event)
                processEventAsync(event, relevantListeners)
            } catch (e: Exception) {
                logger.error("Error processing async event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Публикация события с задержкой
     */
    fun postDelayed(event: PluginEvent, delayMs: Long): Job {
        return eventScope.launch {
            delay(delayMs)
            postAsync(event)
        }
    }
    
    /**
     * Получение статистики EventBus
     */
    fun getStats(): EventBusStats {
        return EventBusStats(
            totalListeners = listeners.size,
            totalEventsPosted = totalEventsPosted,
            totalEventsProcessed = totalEventsProcessed,
            activeCoroutines = eventScope.coroutineContext[Job]?.children?.count() ?: 0
        )
    }
    
    /**
     * Очистка всех listeners
     */
    fun clear() {
        val listenerCount = listeners.size
        listeners.clear()
        listenersByType.clear()
        
        logger.info("Cleared EventBus ($listenerCount listeners)")
    }
    
    /**
     * Остановка EventBus
     */
    fun shutdown() {
        clear()
        eventScope.cancel()
        logger.info("EventBus shutdown")
    }
    
    // Приватные методы
    
    private fun getRelevantListeners(event: PluginEvent): List<EventBusListener> {
        return listeners
            .filter { it.shouldHandle(event) }
            .sortedByDescending { it.priority }
    }
    
    private fun processEvent(event: PluginEvent, listeners: List<EventBusListener>) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
                totalEventsProcessed++
            } catch (e: Exception) {
                logger.error("Error in listener ${listener.listenerId} for event ${event.type}", e)
                
                // Публикуем событие об ошибке
                val errorEvent = PluginEvent.error(e, listener.listenerId)
                // Избегаем бесконечной рекурсии - не отправляем error events через EventBus
                if (event.type != PluginEvent.ERROR_OCCURRED) {
                    eventScope.launch { 
                        post(errorEvent)
                    }
                }
            }
        }
    }
    
    private suspend fun processEventAsync(event: PluginEvent, listeners: List<EventBusListener>) {
        listeners.forEach { listener ->
            launch {
                try {
                    listener.onEvent(event)
                    totalEventsProcessed++
                } catch (e: Exception) {
                    logger.error("Error in async listener ${listener.listenerId} for event ${event.type}", e)
                    
                    if (event.type != PluginEvent.ERROR_OCCURRED) {
                        val errorEvent = PluginEvent.error(e, listener.listenerId)
                        post(errorEvent)
                    }
                }
            }
        }
    }
}

/**
 * Статистика EventBus
 */
data class EventBusStats(
    val totalListeners: Int,
    val totalEventsPosted: Long,
    val totalEventsProcessed: Long,
    val activeCoroutines: Int
)

/**
 * Builder для удобного создания событий
 */
class EventBuilder(private val type: String) {
    private var payload: Any? = null
    private var source: String = "unknown"
    private var metadata: Map<String, Any> = emptyMap()
    
    fun payload(payload: Any?): EventBuilder {
        this.payload = payload
        return this
    }
    
    fun source(source: String): EventBuilder {
        this.source = source
        return this
    }
    
    fun metadata(metadata: Map<String, Any>): EventBuilder {
        this.metadata = metadata
        return this
    }
    
    fun metadata(vararg pairs: Pair<String, Any>): EventBuilder {
        this.metadata = pairs.toMap()
        return this
    }
    
    fun build(): PluginEvent {
        return PluginEvent(type, payload, source, System.currentTimeMillis(), metadata)
    }
    
    fun post() {
        EventBus.post(build())
    }
    
    fun postAsync(): Job {
        return EventBus.postAsync(build())
    }
}

/**
 * Extension функции для удобной работы с EventBus
 */

/**
 * Создание события через builder
 */
fun EventBus.event(type: String): EventBuilder {
    return EventBuilder(type)
}

/**
 * Быстрая публикация простого события
 */
fun EventBus.post(type: String, payload: Any? = null, source: String = "unknown") {
    post(PluginEvent(type, payload, source))
}
