package com.nourtime.app.data.learning

import android.content.Context
import android.util.Log
import com.nourtime.app.core.learning.ClockLevel
import com.nourtime.app.core.learning.ColoringPack
import com.nourtime.app.core.learning.DotShape
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LettersLanguagePack
import com.nourtime.app.core.learning.LettersLevel
import com.nourtime.app.core.learning.ListenLevel
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.MemoryLevel
import com.nourtime.app.core.learning.PatternLevel
import com.nourtime.app.core.learning.TraceLetter
import com.nourtime.app.core.learning.WordLevel
import com.nourtime.app.core.learning.content.ContentLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Learning Hub's content packs from `assets/learning/`. Each pack is read the first time a game
 * needs it, off the main thread, and then kept in memory.
 *
 * Later sources (packs downloaded from the server, Play Asset Delivery, a prebuilt database) plug in
 * behind [ContentLoader]'s file interface without changing the games.
 */
@Singleton
class LearningContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loader = ContentLoader(
        files = { path -> runCatching { context.assets.open("$ROOT/$path").bufferedReader().use { it.readText() } }.getOrNull() },
        report = { Log.w(TAG, "content: $it") },
    )

    suspend fun math(): GamePack<MathLevel> = io { loader.math() }
    suspend fun letterLevels(): GamePack<LettersLevel> = io { loader.letterLevels() }
    suspend fun letters(language: LearnLanguage): LettersLanguagePack = io { loader.letters(language) }
    suspend fun listen(): GamePack<ListenLevel> = io { loader.listen() }
    suspend fun patterns(): GamePack<PatternLevel> = io { loader.patterns() }
    suspend fun clock(): GamePack<ClockLevel> = io { loader.clock() }
    suspend fun memory(): GamePack<MemoryLevel> = io { loader.memory() }
    suspend fun words(): GamePack<WordLevel> = io { loader.words() }
    suspend fun tracing(language: LearnLanguage): GamePack<TraceLetter> = io { loader.tracing(language) }
    suspend fun connect(): GamePack<DotShape> = io { loader.connect() }
    suspend fun coloring(): ColoringPack = io { loader.coloring() }

    /** Illustration bytes for [image] (`assets/learning/images/<image>.webp` or `.png`), or null. */
    suspend fun image(image: String): ByteArray? = io {
        IMAGE_TYPES.firstNotNullOfOrNull { type ->
            runCatching { context.assets.open("$ROOT/images/$image.$type").use { it.readBytes() } }.getOrNull()
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val TAG = "LearningContent"
        const val ROOT = "learning"
        val IMAGE_TYPES = listOf("webp", "png")
    }
}
