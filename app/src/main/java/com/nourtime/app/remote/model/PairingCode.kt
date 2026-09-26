package com.nourtime.app.remote.model

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * The 6-digit code the child's phone shows while pairing (Phase 2). The QR code holds the same
 * code as a `nourtime://pair?c=` link, so scanning and typing lead to the same claim.
 */
object PairingCode {

    const val LENGTH = 6
    private const val URI_PREFIX = "nourtime://pair?c="

    fun generate(random: Random = SecureRandom().asKotlinRandom()): String =
        random.nextInt(0, 1_000_000).toString().padStart(LENGTH, '0')

    fun uri(code: String): String = URI_PREFIX + code

    /** The code from a scanned link or typed text (spaces, dashes and Arabic-Indic digits allowed), or null. */
    fun parse(input: String): String? {
        val text = input.trim()
        val raw = when {
            text.startsWith(URI_PREFIX) -> text.removePrefix(URI_PREFIX)
            text.contains("://") -> return null
            else -> text
        }
        val digits = raw.filterNot { it == ' ' || it == '-' }.map(::westernDigit)
        if (digits.size != LENGTH || digits.any { it == null }) return null
        return digits.joinToString("")
    }

    private fun westernDigit(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        else -> null
    }
}
