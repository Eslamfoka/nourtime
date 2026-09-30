package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import java.io.File

/** The app's real content packs (`src/main/assets/learning`), as the games get them. */
object TestContent {
    private val root = File("src/main/assets/learning")

    val loader = ContentLoader({ path -> File(root, path).takeIf { it.exists() }?.readText() }) {
        throw AssertionError("content problem: $it")
    }

    val math get() = loader.math()
    val letterLevels get() = loader.letterLevels()
    val listen get() = loader.listen()
    val patterns get() = loader.patterns()
    val clock get() = loader.clock()
    val memory get() = loader.memory()
    val words get() = loader.words()
    fun tracing(language: LearnLanguage) = loader.tracing(language)
    fun letters(language: LearnLanguage) = loader.letters(language)
    val connect get() = loader.connect()
    val coloring get() = loader.coloring()
}
