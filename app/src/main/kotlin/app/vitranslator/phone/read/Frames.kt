package app.vitranslator.phone.read

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image

/** A picture of the game, whatever it came from: its size, and a copy of a part of it. */
interface Frame {
    val width: Int
    val height: Int

    /** A copy of [r] (inside the frame), as an ARGB_8888 bitmap. On the producer's thread, before the frame goes. */
    fun crop(r: Rect): Bitmap
}

/** A frame the Xbox player was copied into. */
class BitmapFrame(private val bitmap: Bitmap) : Frame {
    override val width get() = bitmap.width
    override val height get() = bitmap.height
    override fun crop(r: Rect): Bitmap = Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
}

/**
 * A frame from the PS5's video decoder (YUV 4:2:0, as the decoder wrote it into the ImageReader).
 * Only the areas are converted to RGB: the rest of the picture is never touched.
 */
class YuvFrame(private val image: Image) : Frame {
    // (the decoder's buffers may be a little bigger than the picture, 1088 rows for 1080: its crop is the picture)
    private val pic = image.cropRect.takeIf { !it.isEmpty } ?: Rect(0, 0, image.width, image.height)
    override val width get() = pic.width()
    override val height get() = pic.height()

    override fun crop(r: Rect): Bitmap {
        val px = IntArray(r.width() * r.height())
        val inImage = Rect(r).apply { offset(pic.left, pic.top) }
        yuvToArgb(image, inImage, px)
        return Bitmap.createBitmap(px, r.width(), r.height(), Bitmap.Config.ARGB_8888)
    }

    companion object {
        /** BT.709 limited range (the PS5's HD stream) to ARGB, for the part [r] of [image]. */
        fun yuvToArgb(image: Image, r: Rect, out: IntArray) {
            val y = image.planes[0]
            val u = image.planes[1]
            val v = image.planes[2]
            val yb = y.buffer
            val ub = u.buffer
            val vb = v.buffer
            val yRow = y.rowStride
            val yPix = y.pixelStride
            val cRow = u.rowStride
            val cPix = u.pixelStride
            var i = 0
            for (row in r.top until r.bottom) {
                val yBase = row * yRow
                val cBase = (row shr 1) * cRow
                for (col in r.left until r.right) {
                    val yy = (yb.get(yBase + col * yPix).toInt() and 255) - 16
                    val ci = cBase + (col shr 1) * cPix
                    val uu = (ub.get(ci).toInt() and 255) - 128
                    val vv = (vb.get(ci).toInt() and 255) - 128
                    // (fixed point, x1024)
                    val c = 1192 * yy.coerceAtLeast(0)
                    val red = (c + 1836 * vv) shr 10
                    val green = (c - 218 * uu - 546 * vv) shr 10
                    val blue = (c + 2163 * uu) shr 10
                    out[i++] = (0xff shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
                }
            }
        }
    }
}
