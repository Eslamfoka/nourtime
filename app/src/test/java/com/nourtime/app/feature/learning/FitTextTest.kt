package com.nourtime.app.feature.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class FitTextTest {
    @Test
    fun `a word that fits keeps the biggest size`() {
        assertEquals(96f, fitSize(96f, 14f) { true })
    }

    @Test
    fun `a long word shrinks until it fits`() {
        // Pretend the width grows with the size: 7 letters at 0.6 em each in 300 px.
        assertEquals(70f, fitSize(96f, 14f) { size -> 7 * size * 0.6f <= 300f })
    }

    @Test
    fun `a word that never fits stops at the minimum`() {
        assertEquals(14f, fitSize(96f, 14f) { false })
    }
}
