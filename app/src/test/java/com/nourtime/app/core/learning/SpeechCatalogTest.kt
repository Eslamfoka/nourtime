package com.nourtime.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeechCatalogTest {

    @Test
    fun `small numbers and words are one clip`() {
        assertEquals(listOf("7"), SpeechCatalog.clipsFor("7", LearnLanguage.ARABIC))
        assertEquals(listOf("100"), SpeechCatalog.clipsFor("100", LearnLanguage.ENGLISH))
        assertEquals(listOf("300"), SpeechCatalog.clipsFor("300", LearnLanguage.ARABIC))
        assertEquals(listOf("1000"), SpeechCatalog.clipsFor("1000", LearnLanguage.ENGLISH))
        assertEquals(listOf("قطة"), SpeechCatalog.clipsFor("قطة", LearnLanguage.ARABIC))
        assertEquals(listOf("حمار وحشي"), SpeechCatalog.clipsFor("حمار وحشي", LearnLanguage.ARABIC))
    }

    @Test
    fun `a letter's phrase is its name's clip then its word's`() {
        assertEquals(listOf("ألف", "أرنب"), SpeechCatalog.clipsFor("ألف، أرنب", LearnLanguage.ARABIC))
        assertEquals(listOf("A", "Apple"), SpeechCatalog.clipsFor("A, Apple", LearnLanguage.ENGLISH))
    }

    @Test
    fun `big numbers are hundreds then the rest, with its و in Arabic`() {
        assertEquals(listOf("300", "45"), SpeechCatalog.clipsFor("345", LearnLanguage.ENGLISH))
        assertEquals(listOf("300", "+45"), SpeechCatalog.clipsFor("345", LearnLanguage.ARABIC))
        assertEquals(listOf("900", "+1"), SpeechCatalog.clipsFor("901", LearnLanguage.ARABIC))
    }

    @Test
    fun `numbers nothing was recorded for are left to the fallback`() {
        assertNull(SpeechCatalog.clipsFor("1001", LearnLanguage.ARABIC))
        assertNull(SpeechCatalog.clipsFor("-1", LearnLanguage.ENGLISH))
    }

    @Test
    fun `the file name is stable`() {
        // The generator (tools/audio/generate_audio.py) uses the same SHA-1 prefix.
        assertEquals("a94a8fe5ccb19ba6", SpeechCatalog.key("test"))
        assertEquals("audio/en/a94a8fe5ccb19ba6.mp3", SpeechCatalog.assetPath("test", LearnLanguage.ENGLISH))
    }

    @Test
    fun `the catalog has what the games say`() {
        val ar = SpeechCatalog.clips(TestContent.loader, LearnLanguage.ARABIC)
        val en = SpeechCatalog.clips(TestContent.loader, LearnLanguage.ENGLISH)
        assertTrue("ألف" in ar && "أرنب" in ar && "عنكبوت" in ar && "أحمر" in ar && "حيوانات" in ar && "ة" in ar && "+99" in ar)
        assertTrue("A" in en && "Apple" in en && "Peacock" in en && "Red" in en && "Animals" in en && "p" in en && "900" in en)
        // Every number the games use can be said.
        (0..SpeechCatalog.MAX_NUMBER).forEach { n ->
            LearnLanguage.entries.forEach { lang ->
                val clips = SpeechCatalog.clipsFor(n.toString(), lang)!!
                assertTrue("$n in $lang", clips.all { it in if (lang == LearnLanguage.ARABIC) ar else en })
            }
        }
    }

    /**
     * Every clip is recorded: a missing file means the phone's robotic voice. Run
     * `python tools/audio/generate_audio.py` after adding content (it reads the lists written here).
     */
    @Test
    fun `every clip has its recording in the assets`() {
        val lists = File("build/speech").apply { mkdirs() }
        val missing = LearnLanguage.entries.flatMap { lang ->
            val clips = SpeechCatalog.clips(TestContent.loader, lang)
            File(lists, "${lang.tag}.txt").writeText(clips.joinToString("\n", postfix = "\n"))
            clips.filterNot { File("src/main/assets", SpeechCatalog.assetPath(it, lang)).isFile }.map { "${lang.tag}: $it" }
        }
        assertEquals("clips without a recording (run tools/audio/generate_audio.py)", emptyList<String>(), missing)
    }
}
