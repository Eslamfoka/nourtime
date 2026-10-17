package com.nourtime.app.remote.child

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncTasksTest {

    private class Gone : Exception()

    private val noLog: (String, Throwable) -> Unit = { _, _ -> }

    @Test
    fun `a failing task runs again by itself while its neighbour keeps going`() = runTest {
        var runs = 0
        val neighbourDone = CompletableDeferred<Unit>()
        coroutineScope {
            launch {
                runAlone("apps", fatal = { it is Gone }, log = noLog) {
                    runs++
                    if (runs < 3) error("upload failed")
                }
            }
            launch {
                runAlone("settings", fatal = { it is Gone }, log = noLog) { neighbourDone.complete(Unit) }
            }
        }
        assertEquals(3, runs)
        assertTrue(neighbourDone.isCompleted)
    }

    @Test
    fun `a fatal error ends the sync instead of retrying`() = runTest {
        var runs = 0
        try {
            runAlone("commands", fatal = { it is Gone }, log = noLog) {
                runs++
                throw Gone()
            }
            fail("expected the fatal error")
        } catch (_: Gone) {
        }
        assertEquals(1, runs)
    }

    @Test
    fun `pauses grow from five seconds to a minute`() {
        assertEquals(5_000L, syncRetryPause(1))
        assertEquals(10_000L, syncRetryPause(2))
        assertEquals(40_000L, syncRetryPause(4))
        assertEquals(60_000L, syncRetryPause(5))
        assertEquals(60_000L, syncRetryPause(500))
    }
}
