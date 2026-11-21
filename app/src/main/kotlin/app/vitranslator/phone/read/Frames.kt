package app.vitranslator.phone.read

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import java.nio.ByteBuffer

/** A picture of the game, whatever it came from. */
interface Frame {
    /**
     * A copy, taken on the producer's thread while the frame is still there (quick: no
     * conversion); [Snapshot.rgba] then makes the RGBA picture on the reading thread.
     */
    fun snapshot(): Snapshot
}

interface Snapshot {
    val width: Int
    val height: Int

    /** The picture as RGBA, 4 bytes a pixel, row after row. */
    fun rgba(): ByteArray
}

/** A frame the Xbox player was copied into (ARGB_8888: RGBA in memory). */
class BitmapFrame(private val bitmap: Bitmap) : Frame {
    override fun snapshot(): Snapshot {
        val b = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return object : Snapshot {
            override val width = b.width
            override val height = b.height
            override fun rgba(): ByteArray {
                val out = ByteBuffer.allocate(b.byteCount)
                b.copyPixelsToBuffer(out)
                b.recycle()
                return out.array()
            }
        }
    }
}

/**
 * A frame from the PS5's video decoder (YUV 4:2:0, as the decoder wrote it into the ImageReader).
 * The planes are copied as they are; the colours are converted later, off the decoder's way.
 */
class YuvFrame(private val image: Image) : Frame {
    override fun snapshot(): Snapshot {
        // (the decoder's buffers may be a little bigger than the picture, 1088 rows for 1080: its crop is the picture)
        val pic = image.cropRect.takeIf { !it.isEmpty } ?: Rect(0, 0, image.width, image.height)
        val p = image.planes
        return YuvSnapshot(
            pic,
            bytes(p[0].buffer), p[0].rowStride, p[0].pixelStride,
            bytes(p[1].buffer), bytes(p[2].buffer), p[1].rowStride, p[1].pixelStride,
        )
    }

    private fun bytes(b: ByteBuffer): ByteArray {
        val d = b.duplicate()
        d.rewind()
        return ByteArray(d.remaining()).also { d.get(it) }
    }
}

class YuvSnapshot(
    private val pic: Rect,
    private val y: ByteArray,
    private val yRow: Int,
    private val yPix: Int,
    private val u: ByteArray,
    private val v: ByteArray,
    private val cRow: Int,
    private val cPix: Int,
) : Snapshot {
    override val width get() = pic.width()
    override val height get() = pic.height()

    /** BT.709 limited range (the PS5's HD stream) to RGBA. */
    override fun rgba(): ByteArray {
        val w = width
        val h = height
        val out = ByteArray(w * h * 4)
        var i = 0
        for (row in pic.top until pic.top + h) {
            val yBase = row * yRow
            val cBase = (row shr 1) * cRow
            for (col in pic.left until pic.left + w) {
                val yy = ((y[yBase + col * yPix].toInt() and 255) - 16).coerceAtLeast(0)
                val ci = cBase + (col shr 1) * cPix
                val uu = (u[ci].toInt() and 255) - 128
                val vv = (v[ci].toInt() and 255) - 128
                // (fixed point, x1024)
                val c = 1192 * yy
                out[i] = ((c + 1836 * vv) shr 10).coerceIn(0, 255).toByte()
                out[i + 1] = ((c - 218 * uu - 546 * vv) shr 10).coerceIn(0, 255).toByte()
                out[i + 2] = ((c + 2163 * uu) shr 10).coerceIn(0, 255).toByte()
                out[i + 3] = -1
                i += 4
            }
        }
        return out
    }
}
