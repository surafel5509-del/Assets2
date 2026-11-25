package com.sengine.engine.script

/** Result of subscribing to an event. */
data class EventSubscription(
    val id: Int, val name: String, val once: Boolean, val handler: (Any?) -> Unit
)

/**
 * Decoupled publish/subscribe bus used by scripts and by engine systems.
 *
 * Deterministic: subscribers run in registration order, "*" listeners run
 * after named listeners, one-shot listeners are removed before the next emit,
 * and an exception in one handler never prevents the others from running.
 */
class EventBus {
    private val handlers = LinkedHashMap<String, ArrayList<EventSubscription>>()
    private val lastArgs = HashMap<String, Any?>()
    private var nextId = 1

    val totalSubscriptions: Int get() = handlers.values.sumOf { it.size }

    fun on(name: String, handler: (Any?) -> Unit): Int {
        val id = nextId++
        handlers.getOrPut(name) { ArrayList() }.add(EventSubscription(id, name, false, handler))
        return id
    }

    fun once(name: String, handler: (Any?) -> Unit): Int {
        val id = nextId++
        handlers.getOrPut(name) { ArrayList() }.add(EventSubscription(id, name, true, handler))
        return id
    }

    fun off(id: Int): Boolean {
        for (list in handlers.values) {
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) { list.removeAt(i); return true }
        }
        return false
    }

    fun off(name: String, id: Int): Boolean {
        val list = handlers[name] ?: return false
        return list.removeAll { it.id == id }
    }

    /** Emits to named listeners first, then wildcard "*" listeners. Returns invoked count. */
    fun emit(name: String, arg: Any?): Int {
        lastArgs[name] = arg
        var count = 0
        val named = handlers[name]?.toList() ?: emptyList()
        val wildcard = handlers["*"]?.toList() ?: emptyList()
        for (s in named + wildcard) {
            try { s.handler(arg); count++ } catch (_: Throwable) { }
        }
        val namedOnce = named.filter { it.once }.map { it.id }.toSet()
        val wildOnce = wildcard.filter { it.once }.map { it.id }.toSet()
        if (namedOnce.isNotEmpty()) handlers[name]?.removeAll { it.id in namedOnce }
        if (wildOnce.isNotEmpty()) handlers["*"]?.removeAll { it.id in wildOnce }
        return count
    }

    fun last(name: String): Any? = lastArgs[name]

    fun listenerCount(name: String): Int = handlers[name]?.size ?: 0

    fun names(): List<String> = handlers.keys.toList()

    fun clear(name: String? = null) {
        if (name == null) { handlers.clear(); lastArgs.clear() } else { handlers.remove(name); lastArgs.remove(name) }
    }
}
