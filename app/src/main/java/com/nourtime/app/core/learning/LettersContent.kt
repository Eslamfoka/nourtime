package com.nourtime.app.core.learning

/** A letter with its spoken name and a word (and picture) that starts with it. */
data class LetterEntry(val letter: String, val name: String, val word: String, val emoji: String)

data class WordEntry(val word: String, val emoji: String)

data class ColorEntry(val name: String, val argb: Long)

/**
 * Content for Letters & Words. Pictures are emoji from Emoji 5.0 or older, so they draw on every
 * Android 8+ phone without downloads; real artwork can replace them here later.
 */
object LettersContent {

    fun letters(language: LearnLanguage): List<LetterEntry> = when (language) {
        LearnLanguage.ARABIC -> ARABIC_LETTERS
        LearnLanguage.ENGLISH -> ENGLISH_LETTERS
    }

    fun words(language: LearnLanguage): List<WordEntry> = when (language) {
        LearnLanguage.ARABIC -> VOCABULARY.map { WordEntry(it.ar, it.emoji) }
        LearnLanguage.ENGLISH -> VOCABULARY.map { WordEntry(it.en, it.emoji) }
    }

    fun colors(language: LearnLanguage): List<ColorEntry> = COLORS.map {
        ColorEntry(if (language == LearnLanguage.ARABIC) it.ar else it.en, it.argb)
    }

    /** What's read aloud for a letter: "A, Apple" / "ألف، أرنب". */
    fun speech(entry: LetterEntry, language: LearnLanguage): String =
        if (language == LearnLanguage.ARABIC) "${entry.name}، ${entry.word}" else "${entry.name}, ${entry.word}"

    private val ENGLISH_LETTERS = listOf(
        LetterEntry("A", "A", "Apple", "🍎"),
        LetterEntry("B", "B", "Bus", "🚌"),
        LetterEntry("C", "C", "Cat", "🐱"),
        LetterEntry("D", "D", "Dog", "🐶"),
        LetterEntry("E", "E", "Egg", "🥚"),
        LetterEntry("F", "F", "Fish", "🐟"),
        LetterEntry("G", "G", "Grapes", "🍇"),
        LetterEntry("H", "H", "House", "🏠"),
        LetterEntry("I", "I", "Ice cream", "🍦"),
        LetterEntry("J", "J", "Juice", "🥤"),
        LetterEntry("K", "K", "Key", "🔑"),
        LetterEntry("L", "L", "Lion", "🦁"),
        LetterEntry("M", "M", "Moon", "🌙"),
        LetterEntry("N", "N", "Nose", "👃"),
        LetterEntry("O", "O", "Orange", "🍊"),
        LetterEntry("P", "P", "Pizza", "🍕"),
        LetterEntry("Q", "Q", "Queen", "👸"),
        LetterEntry("R", "R", "Rabbit", "🐰"),
        LetterEntry("S", "S", "Sun", "☀️"),
        LetterEntry("T", "T", "Tree", "🌳"),
        LetterEntry("U", "U", "Umbrella", "☂️"),
        LetterEntry("V", "V", "Violin", "🎻"),
        LetterEntry("W", "W", "Watch", "⌚"),
        LetterEntry("Y", "Y", "Yacht", "⛵"),
        LetterEntry("Z", "Z", "Zebra", "🦓"),
    )

    private val ARABIC_LETTERS = listOf(
        LetterEntry("أ", "ألف", "أرنب", "🐰"),
        LetterEntry("ب", "باء", "بطة", "🦆"),
        LetterEntry("ت", "تاء", "تفاحة", "🍎"),
        LetterEntry("ث", "ثاء", "ثعلب", "🦊"),
        LetterEntry("ج", "جيم", "جمل", "🐫"),
        LetterEntry("ح", "حاء", "حصان", "🐴"),
        LetterEntry("خ", "خاء", "خروف", "🐑"),
        LetterEntry("د", "دال", "دب", "🐻"),
        LetterEntry("ذ", "ذال", "ذرة", "🌽"),
        LetterEntry("ر", "راء", "رز", "🍚"),
        LetterEntry("ز", "زاي", "زرافة", "🦒"),
        LetterEntry("س", "سين", "سمكة", "🐟"),
        LetterEntry("ش", "شين", "شمس", "☀️"),
        LetterEntry("ص", "صاد", "صقر", "🦅"),
        LetterEntry("ض", "ضاد", "ضفدع", "🐸"),
        LetterEntry("ط", "طاء", "طائرة", "✈️"),
        LetterEntry("ظ", "ظاء", "ظرف", "✉️"),
        LetterEntry("ع", "عين", "عنب", "🍇"),
        LetterEntry("غ", "غين", "غيمة", "☁️"),
        LetterEntry("ف", "فاء", "فيل", "🐘"),
        LetterEntry("ق", "قاف", "قمر", "🌙"),
        LetterEntry("ك", "كاف", "كرة", "⚽"),
        LetterEntry("ل", "لام", "ليمون", "🍋"),
        LetterEntry("م", "ميم", "موز", "🍌"),
        LetterEntry("ن", "نون", "نحلة", "🐝"),
        LetterEntry("ه", "هاء", "هدية", "🎁"),
        LetterEntry("و", "واو", "وردة", "🌹"),
        LetterEntry("ي", "ياء", "يد", "✋"),
    )

    private class Vocab(val en: String, val ar: String, val emoji: String)

    private val VOCABULARY = listOf(
        Vocab("Mouth", "فم", "👄"),
        Vocab("Eye", "عين", "👁️"),
        Vocab("Ear", "أذن", "👂"),
        Vocab("Nose", "أنف", "👃"),
        Vocab("Hand", "يد", "✋"),
        Vocab("Car", "سيارة", "🚗"),
        Vocab("Bus", "حافلة", "🚌"),
        Vocab("House", "بيت", "🏠"),
        Vocab("Book", "كتاب", "📖"),
        Vocab("Star", "نجمة", "⭐"),
        Vocab("Tree", "شجرة", "🌳"),
        Vocab("Flower", "زهرة", "🌸"),
        Vocab("Cat", "قطة", "🐱"),
        Vocab("Dog", "كلب", "🐶"),
        Vocab("Bird", "عصفور", "🐦"),
        Vocab("Banana", "موز", "🍌"),
        Vocab("Apple", "تفاحة", "🍎"),
        Vocab("Milk", "حليب", "🥛"),
        Vocab("Bread", "خبز", "🍞"),
        Vocab("Moon", "قمر", "🌙"),
        Vocab("Sun", "شمس", "☀️"),
        Vocab("Ball", "كرة", "⚽"),
        Vocab("Clock", "ساعة", "⏰"),
        Vocab("Fish", "سمكة", "🐟"),
        Vocab("Horse", "حصان", "🐴"),
    )

    private class ColorName(val en: String, val ar: String, val argb: Long)

    private val COLORS = listOf(
        ColorName("Red", "أحمر", 0xFFE53935),
        ColorName("Blue", "أزرق", 0xFF1E88E5),
        ColorName("Green", "أخضر", 0xFF43A047),
        ColorName("Yellow", "أصفر", 0xFFFDD835),
        ColorName("Orange", "برتقالي", 0xFFFB8C00),
        ColorName("Purple", "بنفسجي", 0xFF8E24AA),
        ColorName("Pink", "وردي", 0xFFF06292),
        ColorName("Brown", "بني", 0xFF795548),
        ColorName("Black", "أسود", 0xFF212121),
        ColorName("White", "أبيض", 0xFFFFFFFF),
        ColorName("Gray", "رمادي", 0xFF9E9E9E),
    )
}
