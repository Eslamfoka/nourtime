package com.nourtime.app.service.blocking

/**
 * One value kept while its key stays the same. The lock screen's allowed apps (launch intents, names,
 * icons) are resolved again only when the allowed set, the language or the installed apps change, not
 * on every tick (2026-10-28 audit, battery). Used from one coroutine at a time.
 */
internal class KeyedCache<K, V> {
    @Volatile private var cached: Pair<K, V>? = null

    suspend fun get(key: K, resolve: suspend (K) -> V): V {
        cached?.let { (k, v) -> if (k == key) return v }
        return resolve(key).also { cached = key to it }
    }
}
