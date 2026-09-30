package com.nourtime.app.feature.learning

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.nourtime.app.core.learning.LearnLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Reads words and numbers aloud in the games. Silent (never crashing) where no voice exists. */
interface Speaker {
    /** Languages the phone has a voice for; empty until the engine is ready. */
    val voices: StateFlow<Set<LearnLanguage>>

    fun say(text: String, language: LearnLanguage)

    object Silent : Speaker {
        override val voices: StateFlow<Set<LearnLanguage>> = MutableStateFlow(emptySet())
        override fun say(text: String, language: LearnLanguage) = Unit
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker> { Speaker.Silent }

/** A [Speaker] on the phone's own text-to-speech engine, alive while this composable is. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { AndroidSpeaker(context.applicationContext) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

private class AndroidSpeaker(context: Context) : Speaker {
    private val _voices = MutableStateFlow<Set<LearnLanguage>>(emptySet())
    override val voices: StateFlow<Set<LearnLanguage>> = _voices.asStateFlow()

    private var ready = false
    private var current: LearnLanguage? = null
    /** Asked before the engine was ready: said once it is. */
    private var pending: Pair<String, LearnLanguage>? = null

    private val tts: TextToSpeech? = runCatching {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "No text-to-speech engine ($status)")
                return@TextToSpeech
            }
            ready = true
            _voices.value = LearnLanguage.entries.filter(::supports).toSet()
            Log.i(TAG, "voices: ${_voices.value}")
            pending?.let { (text, language) -> say(text, language) }
            pending = null
        }
    }.onFailure { Log.w(TAG, "Couldn't start text-to-speech", it) }.getOrNull()?.apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        setSpeechRate(0.9f)
    }

    private fun supports(language: LearnLanguage): Boolean = runCatching {
        (tts?.isLanguageAvailable(Locale.forLanguageTag(language.tag)) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
    }.getOrDefault(false)

    override fun say(text: String, language: LearnLanguage) {
        val engine = tts ?: return
        if (!ready) {
            pending = text to language
            return
        }
        if (language !in _voices.value) return
        runCatching {
            if (current != language) {
                engine.language = Locale.forLanguageTag(language.tag)
                current = language
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nour-${System.nanoTime()}")
        }.onFailure { Log.w(TAG, "speak failed", it) }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
