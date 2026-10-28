package com.nourtime.app.service.blocking

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// The lock screen's allowed apps used to be looked up (launch intents, names, icons) every second.
class KeyedCacheTest {

    private data class Key(val packages: Set<String>, val language: String, val installed: Int)

    private val cache = KeyedCache<Key, List<String>>()
    private var resolved = 0
    private val resolve: suspend (Key) -> List<String> = { k -> resolved++; k.packages.sorted().map { "${k.language}:$it" } }

    @Test
    fun `the same key resolves once and returns the same list`() = runTest {
        val key = Key(setOf("b", "a"), "en", 0)
        val first = cache.get(key, resolve)
        val second = cache.get(Key(setOf("a", "b"), "en", 0), resolve)
        assertSame(first, second)
        assertEquals(1, resolved)
    }

    @Test
    fun `a change of the set, the language or the installed apps resolves again`() = runTest {
        cache.get(Key(setOf("a"), "en", 0), resolve)
        assertEquals(listOf("en:a", "en:b"), cache.get(Key(setOf("a", "b"), "en", 0), resolve))
        assertEquals(listOf("ar:a", "ar:b"), cache.get(Key(setOf("a", "b"), "ar", 0), resolve))
        cache.get(Key(setOf("a", "b"), "ar", 1), resolve)
        assertEquals(4, resolved)
    }

    @Test
    fun `a failed lookup isn't kept`() = runTest {
        val key = Key(setOf("a"), "en", 0)
        runCatching { cache.get(key) { throw IllegalStateException("package manager died") } }
        assertEquals(listOf("en:a"), cache.get(key, resolve))
        assertEquals(1, resolved)
    }
}
