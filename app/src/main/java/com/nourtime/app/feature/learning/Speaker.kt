package com.nourtime.app.feature.learning

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.SpeechCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Reads words and numbers aloud in the games. Silent (never crashing) where no voice exists. */
interface Speaker {
    /** Languages the child can hear; empty until known. */
    val voices: StateFlow<Set<LearnLanguage>>

    fun say(text: String, language: LearnLanguage)

    object Silent : Speaker {
        override val voices: StateFlow<Set<LearnLanguage>> = MutableStateFlow(emptySet())
        override fun say(text: String, language: LearnLanguage) = Unit
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker> { Speaker.Silent }

private val SPEECH = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** A [Speaker] on the recorded voices, alive while this composable is. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { RecordedSpeaker(context.applicationContext) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

/**
 * Plays the recorded clips from `assets/audio` (see [SpeechCatalog]). The phone's text-to-speech is
 * only a fallback for text with no recording; it isn't even started until that happens.
 */
private class RecordedSpeaker(private val context: Context) : Speaker {
    /** The clip files each language has, read once. */
    private val recorded: Map<LearnLanguage, Set<String>> = LearnLanguage.entries.associateWith { lang ->
        runCatching { context.assets.list("audio/${lang.tag}")?.toSet() }.getOrNull().orEmpty()
    }
    private val recordedLanguages = recorded.filterValues { it.isNotEmpty() }.keys
    private val _voices = MutableStateFlow(recordedLanguages)
    override val voices: StateFlow<Set<LearnLanguage>> = _voices.asStateFlow()

    private var fallback: TtsSpeaker? = null
    private val playing = mutableListOf<MediaPlayer>()

    override fun say(text: String, language: LearnLanguage) {
        val clips = SpeechCatalog.clipsFor(text, language)
        val files = clips?.map { SpeechCatalog.assetPath(it, language) }
        if (files != null && files.all { it.substringAfterLast('/') in recorded[language].orEmpty() }) {
            if (play(files)) return
        }
        Log.i(TAG, "no recording for \"$text\" ($language), using the phone's voice")
        stop()
        val tts = fallback ?: TtsSpeaker(context) { ttsVoices -> _voices.value = recordedLanguages + ttsVoices }.also { fallback = it }
        tts.say(text, language)
    }

    /** Plays [files] one after the other, without a gap. False when they can't be played. */
    private fun play(files: List<String>): Boolean {
        stop()
        fallback?.stop()
        val players = mutableListOf<MediaPlayer>()
        val ok = runCatching {
            files.forEach { path ->
                players += MediaPlayer().apply {
                    setAudioAttributes(SPEECH)
                    context.assets.openFd(path).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    prepare()
                }
            }
        }.onFailure { Log.w(TAG, "couldn't play $files", it) }.isSuccess
        if (!ok) {
            players.forEach { runCatching { it.release() } }
            return false
        }
        playing += players
        players.zipWithNext { a, b -> a.setNextMediaPlayer(b) }
        players.forEach { p ->
            p.setOnCompletionListener {
                it.release()
                playing.remove(it)
            }
        }
        players.first().start()
        return true
    }

    private fun stop() {
        playing.forEach { runCatching { it.stop(); it.release() } }
        playing.clear()
    }

    fun shutdown() {
        stop()
        fallback?.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
    }
}

/** The phone's own text-to-speech engine: the fallback for text that has no recording. */
private class TtsSpeaker(context: Context, private val onVoices: (Set<LearnLanguage>) -> Unit) {
    private var ready = false
    private var voices: Set<LearnLanguage> = emptySet()
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
            voices = LearnLanguage.entries.filter(::supports).toSet()
            Log.i(TAG, "fallback voices: $voices")
            onVoices(voices)
            pending?.let { (text, language) -> say(text, language) }
            pending = null
        }
    }.onFailure { Log.w(TAG, "Couldn't start text-to-speech", it) }.getOrNull()?.apply {
        setAudioAttributes(SPEECH)
        setSpeechRate(0.9f)
    }

    private fun supports(language: LearnLanguage): Boolean = runCatching {
        (tts?.isLanguageAvailable(Locale.forLanguageTag(language.tag)) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
    }.getOrDefault(false)

    fun say(text: String, language: LearnLanguage) {
        val engine = tts ?: return
        if (!ready) {
            pending = text to language
            return
        }
        if (language !in voices) return
        runCatching {
            if (current != language) {
                engine.language = Locale.forLanguageTag(language.tag)
                current = language
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nour-${System.nanoTime()}")
        }.onFailure { Log.w(TAG, "speak failed", it) }
    }

    fun stop() {
        runCatching { tts?.stop() }
        pending = null
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
