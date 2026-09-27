package com.nourtime.app.feature.remote

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Draws the pairing QR code (Phase 2). */
object QrCode {

    fun matrix(text: String, sizePx: Int): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )

    /** Dark modules on white, whatever the app theme, so every scanner reads it. */
    fun image(text: String, sizePx: Int): ImageBitmap {
        val m = matrix(text, sizePx)
        val pixels = IntArray(m.width * m.height) { i ->
            if (m.get(i % m.width, i / m.width)) DARK else LIGHT
        }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val DARK = 0xFF1B2440.toInt()
    private const val LIGHT = 0xFFFFFFFF.toInt()
}
