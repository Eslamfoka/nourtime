package com.nourtime.app.service.detection

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshLoopTest {

    @Test
    fun `a failed window refresh doesn't end the loop`() = runTest {
        val requests = Channel<Unit>(Channel.UNLIMITED)
        repeat(3) { requests.trySend(Unit) }
        requests.close()
        var published = 0
        val errors = mutableListOf<Exception>()
        refreshLoop(requests, publish = {
            published++
            if (published == 1) throw IllegalStateException("window list failed")
        }) { errors += it }
        assertEquals(3, published)
        assertEquals(1, errors.size)
    }
}
