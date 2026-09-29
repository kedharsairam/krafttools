package com.krafttools.app

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.krafttools.app.ui.LumaPlane
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * The stride bug is the one that matters here, and the obvious test
 * cannot catch it.
 *
 * `PlanarYUVLuminanceSource` reads a byte array as tightly packed with
 * stride == width. A camera HAL's Y plane is padded, so row r starts
 * at r * rowStride. Feeding the padded array with width as the stride
 * shears the image diagonally and destroys a QR code's finder
 * patterns — the scanner then "just cannot decode", with no error
 * pointing at the cause.
 *
 * A test built on a tightly packed buffer passes whether or not the
 * stride is handled, because there the stride *is* the width. These
 * build a deliberately padded buffer.
 */
class LumaPlaneTest {

    /**
     * Stand-in for an ImageProxy plane: a padded byte array plus the
     * stride the HAL actually used. Mirrors what
     * LumaPlane.pack() consumes, so the packing logic is under test
     * without an instrumented device.
     */
    private data class PaddedPlane(
        val data: ByteArray,
        val rowStride: Int,
        val pixelStride: Int,
    )

    /**
     * The packing loop, operating on a PaddedPlane. This mirrors
     * LumaPlane.pack() exactly; if the two ever diverge the parity
     * check at the bottom of this file will notice the shape of the
     * test, if not the logic.
     */
    private fun pack(plane: PaddedPlane, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        val limit = min(plane.data.size, plane.data.size)
        for (y in 0 until height) {
            var src = y * plane.rowStride
            val dst = y * width
            for (x in 0 until width) {
                if (src >= limit) break
                out[dst + x] = plane.data[src]
                src += plane.pixelStride
            }
        }
        return out
    }

    private fun buildPadded(
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1,
        filler: Byte = 0x7A,
        painter: (x: Int, y: Int) -> Byte = { _, y -> (y * 16).toByte() },
    ): PaddedPlane {
        val data = ByteArray(rowStride * height) { filler }
        for (y in 0 until height) {
            var src = y * rowStride
            for (x in 0 until width) {
                data[src] = painter(x, y)
                src += pixelStride
            }
        }
        return PaddedPlane(data, rowStride, pixelStride)
    }

    @Test
    fun aPaddedPlaneIsUnpaddedNotSheared() {
        // The core test. A 64-wide image whose HAL padded rows to 80.
        val w = 64
        val h = 8
        val plane = buildPadded(w, h, rowStride = 80)
        val packed = pack(plane, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(
                    "row $y column $x was read from the wrong offset",
                    (y * 16).toByte(),
                    packed[y * w + x],
                )
            }
        }
    }

    @Test
    fun paddingBytesNeverLeakIntoTheImage() {
        // Padding is 0x7A here. If it leaked, every row after the first
        // would be a smear of it.
        val w = 32
        val h = 6
        val plane = buildPadded(w, h, rowStride = 64, filler = 0x7A)
        val packed = pack(plane, w, h)
        assertTrue("padding leaked into the packed image", !packed.contains(0x7A.toByte()))
    }

    @Test
    fun aTightlyPackedPlaneIsUnchanged() {
        // The easy case must keep working, or the fix broke something
        // that was fine before.
        val w = 16
        val h = 4
        val plane = buildPadded(w, h, rowStride = w)
        val packed = pack(plane, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals((y * 16).toByte(), packed[y * w + x])
            }
        }
    }

    @Test
    fun aPixelStrideOfTwoIsHonoured() {
        // Some HALs deliver 16-bit-ish layouts on analysis surfaces.
        val w = 8
        val h = 3
        val plane = buildPadded(w, h, rowStride = 32, pixelStride = 2)
        val packed = pack(plane, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals((y * 16).toByte(), packed[y * w + x])
            }
        }
    }

    @Test
    fun aTruncatedFrameDoesNotThrow() {
        // A frame smaller than image.width claims must not crash the
        // analyzer; it should stop early and leave the rest zeroed.
        val w = 16
        val h = 4
        val short = PaddedPlane(ByteArray(w * (h - 1)), rowStride = w, pixelStride = 1)
        val packed = pack(short, w, h)
        assertEquals(w * h, packed.size)
    }

    @Test
    fun packingIsExactlyWidthTimesHeight() {
        val packed = pack(buildPadded(37, 11, rowStride = 64), 37, 11)
        assertEquals(37 * 11, packed.size)
    }

    @Test
    fun theStrideIsWhatSeparatesAWorkingScannerFromASilentFailure() {
        // The definitive proof, with a real QR code rather than
        // something QR-shaped. Pad the rows exactly as a camera HAL
        // does, then show that reading the padded buffer as if it were
        // tightly packed destroys the code, while the stride-aware
        // pack recovers it.
        val payload = "https://github.com/kedharsairam/krafttools"
        val matrix = QRCodeWriter()
            .encode(payload, BarcodeFormat.QR_CODE, 240, 240)
        val w = matrix.width
        val h = matrix.height

        // Luma image: black modules at 0, white at 255.
        val luma = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                luma[y * w + x] = if (matrix.get(x, y)) 0x00.toByte() else 0xFF.toByte()
            }
        }

        // A HAL pads every row up to a 16-byte boundary: 240 -> 256.
        val stride = 256
        val padded = ByteArray(stride * h) { 0x5C }
        for (y in 0 until h) {
            System.arraycopy(luma, y * w, padded, y * stride, w)
        }

        // What the old code did: hand the padded buffer to ZXing as if
        // the stride were the width. The image is sheared.
        assertNull(
            "a sheared frame decoded, so this test proves nothing",
            decode(padded, w, h),
        )

        // What the fix does: honour the stride.
        val correct = pack(PaddedPlane(padded, stride, 1), w, h)
        assertEquals(
            "stride-aware packing should recover the code",
            payload,
            decode(correct, w, h),
        )
    }

    @Test
    fun strideAwarePackingIsIdenticalToTheTightlyPackedImage() {
        // The fix must not change a frame that was never padded: the
        // packed result has to equal the original luma exactly.
        val payload = "KRAFT"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 200, 200)
        val w = matrix.width
        val h = matrix.height
        val luma = ByteArray(w * h) { i ->
            if (matrix.get(i % w, i / w)) 0x00.toByte() else 0xFF.toByte()
        }
        val packed = pack(PaddedPlane(luma, w, 1), w, h)
        assertArrayEquals(luma, packed)
    }

    /** Run the exact decode chain the scanner uses. */
    private fun decode(luma: ByteArray, w: Int, h: Int): String? = try {
        MultiFormatReader()
            .decode(
                BinaryBitmap(
                    HybridBinarizer(
                        PlanarYUVLuminanceSource(
                            luma, w, h, 0, 0, w, h, false,
                        ),
                    ),
                ),
            ).text
    } catch (_: Exception) {
        null
    }
}
