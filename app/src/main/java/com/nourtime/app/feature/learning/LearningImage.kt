package com.nourtime.app.feature.learning

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads an illustration's bytes by id (from the content packs); null when there is none. */
val LocalImageSource = staticCompositionLocalOf<suspend (String) -> ByteArray?> { { null } }

/** Decoded illustrations, shared by all screens (about 40 pictures of ~250 px). */
private object ImageCache {
    private val hits = LruCache<String, ImageBitmap>(40)
    private val missing = mutableSetOf<String>()

    fun get(id: String): ImageBitmap? = hits.get(id)
    fun isMissing(id: String) = synchronized(missing) { id in missing }
    fun put(id: String, bitmap: ImageBitmap?) {
        if (bitmap != null) hits.put(id, bitmap) else synchronized(missing) { missing += id }
    }
}

/**
 * A picture card's content: the illustration [image] when the content pack has one, otherwise the
 * [emoji]. Illustrations are decoded off the main thread at about [size], then cached.
 */
@Composable
fun LearningPicture(image: String?, emoji: String, size: Dp, emojiSize: TextUnit, description: String? = null) {
    val source = LocalImageSource.current
    var bitmap by remember(image) { mutableStateOf(image?.let(ImageCache::get)) }
    if (image != null && bitmap == null && !ImageCache.isMissing(image)) {
        LaunchedEffect(image) {
            val decoded = withContext(Dispatchers.Default) {
                source(image)?.let { bytes -> decode(bytes) }
            }
            ImageCache.put(image, decoded)
            bitmap = decoded
        }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = description, modifier = Modifier.size(size))
    } else {
        Text(emoji, fontSize = emojiSize)
    }
}

/** Decodes at most ~512 px wide, so a huge source image can't cost much memory. */
private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 512) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
