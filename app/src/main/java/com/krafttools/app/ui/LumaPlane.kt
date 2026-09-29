package com.krafttools.app.ui

import android.graphics.ImageFormat
import java.nio.ByteBuffer

/**
 * Reading a camera frame's luminance plane correctly.
 *
 * The bug this exists to prevent is the single most common CameraX +
 * ZXing integration mistake, and it fails *quietly*: the scanner just
 * often cannot decode, with no error to point at.
 *
 * `PlanarYUVLuminanceSource` assumes the byte array is tightly packed,
 * with row stride equal to the image width. A camera HAL's Y plane is
 * not: rows are padded to 16/32/64-byte boundaries, and the whole
 * allocation is often padded to a power of two. So row *r* really
 * starts at `r * rowStride`, but ZXing reads it at `r * width`. Each
 * row is offset by a growing error and the image is sheared
 * diagonally — which destroys the three finder patterns and the timing
 * patterns a QR code is built from.
 *
 * The unit test for this builds a deliberately padded buffer, because
 * the obvious test (a tightly packed array) passes either way and is a
 * false green.
 */
object LumaPlane {

    /**
     * Copy a YUV image's Y plane into a tightly packed array of exactly
     * [width] * [height] bytes, honouring [rowStride].
     *
     * Returns null for a frame with no Y plane, which a caller should
     * treat as "nothing to do" rather than an error.
     */
    fun pack(image: androidx.camera.core.ImageProxy): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val out = ByteArray(width * height)
        // limit(), not capacity(): direct buffers from a camera HAL are
        // page-padded, so capacity routinely exceeds the data size and
        // an index between them throws.
        val limit = minOf(buffer.capacity(), buffer.limit())
        val rowStart = buffer.position()

        for (y in 0 until height) {
            var src = rowStart + y * rowStride
            val dst = y * width
            for (x in 0 until width) {
                if (src >= limit) {
                    // Short row: the frame is smaller than the image
                    // claims. Leave the rest at zero rather than
                    // throwing on someone else's buffer.
                    break
                }
                out[dst + x] = buffer.get(src)
                src += pixelStride
            }
        }
        return out
    }
}

/** The pixel format this app asks the camera for. Kept in one place so
 *  the two camera tools cannot drift apart. */
const val ANALYSIS_FORMAT = ImageFormat.YUV_420_888
