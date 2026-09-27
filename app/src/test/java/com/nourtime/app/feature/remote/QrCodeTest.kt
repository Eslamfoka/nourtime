package com.nourtime.app.feature.remote

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    @Test
    fun `the drawn code scans back to the pairing link`() {
        val matrix = QrCode.matrix("nourtime://pair?c=123456", sizePx = 300)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        assertEquals("nourtime://pair?c=123456", QRCodeReader().decode(bitmap).text)
    }
}
