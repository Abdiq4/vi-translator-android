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

    /**
     * BT.709 limited range (the PS5's HD stream) to RGBA, then studio levels (16–235) stretched to
     * full (0–255): the PS5's Remote Play picture carries them (its blacks come out at ~32 and whites
     * at ~228, where the console's HDMI picture has ~15 and ~248), and the game pages' colour checks
     * are tuned on the HDMI picture (GTA VI's pause menu isn't recognised without this). Measured on
     * a real PS5 stream (the Android TV app's test).
     */
    override fun rgba(): ByteArray {
        val w = width
        val h = height
        val out = ByteArray(w * h * 4)
        fun rows(from: Int, to: Int) {
            for (r in from until to) {
                val row = pic.top + r
                val yBase = row * yRow
                val cBase = (row shr 1) * cRow
                var i = r * w * 4
                for (col in pic.left until pic.left + w) {
                    val ci = cBase + (col shr 1) * cPix
                    pixel(out, i, y[yBase + col * yPix].toInt() and 255, u[ci].toInt() and 255, v[ci].toInt() and 255)
                    i += 4
                }
            }
        }
        // (fast mode: the rows are converted on several threads at once)
        val n = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        if (n == 1 || h < 64) {
            rows(0, h)
        } else {
            val per = (h + n - 1) / n
            val ts = (0 until n).map { k -> Thread { rows(k * per, minOf(h, (k + 1) * per)) }.also { it.start() } }
            ts.forEach { it.join() }
        }
        return out
    }

    companion object {
        /** One pixel's Y, U, V (0..255) as RGBA at out[i]. */
        @Suppress("NOTHING_TO_INLINE")
        inline fun pixel(out: ByteArray, i: Int, y: Int, u: Int, v: Int) {
            val yy = (y - 16).coerceAtLeast(0)
            val uu = u - 128
            val vv = v - 128
            // (fixed point, x1024: BT.709's factors times 255/219, less 16 x 255/219 for the levels)
            val c = 1388 * yy - 19076
            out[i] = ((c + 2138 * vv) shr 10).coerceIn(0, 255).toByte()
            out[i + 1] = ((c - 254 * uu - 636 * vv) shr 10).coerceIn(0, 255).toByte()
            out[i + 2] = ((c + 2519 * uu) shr 10).coerceIn(0, 255).toByte()
            out[i + 3] = -1
        }
    }
}
