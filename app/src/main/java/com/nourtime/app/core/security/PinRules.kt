package com.nourtime.app.core.security

object PinRules {
    const val LENGTH = 4

    fun isValidFormat(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** Rejects PINs a child could guess: repeated digits (1111) and straight runs (1234, 9876). */
    fun isTooSimple(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}
