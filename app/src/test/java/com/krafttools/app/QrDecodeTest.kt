package com.krafttools.app

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage

/** Validates the exact decode chain Qr.kt uses (YUV -> zxing -> text). */
class QrDecodeTest {
    @Test
    fun yuvRoundTrip() {
        val payload = "https://github.com/kedharsairam/krafttools"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 200, 200)
        // RGB -> grayscale Y plane (same layout decodeYuv feeds zxing).
        val w = matrix.width
        val h = matrix.height
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                img.setRGB(x, y, if (matrix.get(x, y)) -0x1000000 else -0x1)
            }
        }
        val yuv = ByteArray(w * h)
        val rgb = IntArray(w * h)
        img.getRGB(0, 0, w, h, rgb, 0, w)
        for (i in rgb.indices) {
            val p = rgb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            yuv[i] = ((66 * r + 129 * g + 25 * b + 128) shr 8).toByte()
        }
        val source = PlanarYUVLuminanceSource(yuv, w, h, 0, 0, w, h, false)
        val out = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(payload, out)
    }
}
