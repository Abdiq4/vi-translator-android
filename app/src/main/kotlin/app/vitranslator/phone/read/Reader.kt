package app.vitranslator.phone.read

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import app.vitranslator.phone.core.Area
import app.vitranslator.phone.core.AreaRead
import app.vitranslator.phone.core.Core
import app.vitranslator.phone.core.FrameRead
import app.vitranslator.phone.core.Word
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the game: of the frames offered (as fast as they come), takes about [fps] a second, copies
 * the core's areas out of each, has ML Kit read them (on-device, Latin script) and gives the words
 * to the core. A frame offered while the last one is still being read is skipped at once, so the
 * stream never waits for text recognition.
 */
class Reader(private val core: Core, private val fps: Int = 4) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "reader").apply { isDaemon = true } }
    private val busy = AtomicBoolean(false)

    @Volatile
    private var areas: List<Area> = core.areas()
    private var last = 0L

    /** For the screen: frames seen, frames read, and how long the last reading took. */
    @Volatile
    var offered = 0L
        private set

    @Volatile
    var read = 0L
        private set

    @Volatile
    var lastMs = 0L
        private set

    @Volatile
    var frameSize = ""
        private set

    /** The game or language changed: the core may read other areas. */
    fun refreshAreas() {
        areas = core.areas()
    }

    /**
     * A new frame (on the producer's thread). True when it was taken: the areas were copied before
     * this returns, so the producer can let the frame go either way.
     */
    fun offer(frame: Frame): Boolean {
        offered++
        val now = SystemClock.elapsedRealtime()
        if (now - last < 1000L / fps) return false
        if (!busy.compareAndSet(false, true)) return false
        last = now
        val crops = try {
            frameSize = "${frame.width}x${frame.height}"
            areas.map { a -> a to frame.crop(rectOf(a, frame.width, frame.height)) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "crop failed", e)
            busy.set(false)
            return false
        }
        val w = frame.width
        val h = frame.height
        worker.execute {
            try {
                val start = SystemClock.elapsedRealtime()
                val reads = crops.map { (a, bmp) -> readArea(a, bmp) }
                core.submit(FrameRead(w, h, now, reads))
                lastMs = SystemClock.elapsedRealtime() - start
                read++
            } catch (e: Exception) {
                Log.w(TAG, "reading failed", e)
            } finally {
                busy.set(false)
            }
        }
        return true
    }

    private fun readArea(a: Area, bmp: Bitmap): AreaRead {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 5, TimeUnit.SECONDS)
        val words = wordsOf(text)
        val rgba = ByteBuffer.allocate(bmp.byteCount).also { bmp.copyPixelsToBuffer(it) }.array()
        val r = AreaRead(a.id, bmp.width, bmp.height, rgba, words)
        bmp.recycle()
        return r
    }

    fun close() {
        worker.shutdown()
        recognizer.close()
    }

    companion object {
        private const val TAG = "Reader"

        /** The area's rectangle in pixels, inside the frame and at least 1x1. */
        fun rectOf(a: Area, w: Int, h: Int): Rect = boundsOf(a, w, h).let { Rect(it[0], it[1], it[2], it[3]) }

        /** left, top, right, bottom */
        fun boundsOf(a: Area, w: Int, h: Int): IntArray {
            val l = (a.left * w).toInt().coerceIn(0, w - 1)
            val t = (a.top * h).toInt().coerceIn(0, h - 1)
            val r = (a.right * w).toInt().coerceIn(l + 1, w)
            val b = (a.bottom * h).toInt().coerceIn(t + 1, h)
            return intArrayOf(l, t, r, b)
        }

        /** ML Kit's answer as words, each with the line it is in (top to bottom over the blocks). */
        fun wordsOf(text: Text): List<Word> {
            val out = ArrayList<Word>()
            var line = 0
            for (block in text.textBlocks) {
                for (l in block.lines) {
                    for (e in l.elements) {
                        val b = e.boundingBox ?: continue
                        out.add(Word(e.text, b.left, b.top, b.right, b.bottom, line))
                    }
                    line++
                }
            }
            return out
        }
    }
}
