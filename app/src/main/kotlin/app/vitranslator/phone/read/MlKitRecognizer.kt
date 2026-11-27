package app.vitranslator.phone.read

import android.graphics.Bitmap
import android.util.Log
import app.vitranslator.phone.core.Recognizer
import app.vitranslator.phone.core.Word
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** Google ML Kit's text recognition (Latin script, on the phone), called synchronously by the core. */
class MlKitRecognizer : Recognizer {
    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        // (fast mode: the model is loaded now, in the background, so the first subtitle isn't late)
        Thread {
            try {
                val blank = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                Tasks.await(client.process(InputImage.fromBitmap(blank, 0)), 20, TimeUnit.SECONDS)
                blank.recycle()
            } catch (e: Exception) {
                Log.w("MlKitRecognizer", "warm-up failed: $e")
            }
        }.apply { isDaemon = true }.start()
    }

    override fun recognize(bgra: ByteArray, width: Int, height: Int): List<Word> {
        if (width <= 0 || height <= 0 || bgra.size < width * height * 4) return emptyList()
        // (grey: the channel order doesn't matter)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bgra, 0, width * height * 4))
        return try {
            // (the first call loads the model: it may take a few seconds)
            wordsOf(Tasks.await(client.process(InputImage.fromBitmap(bmp, 0)), 20, TimeUnit.SECONDS))
        } catch (e: Exception) {
            Log.w("MlKitRecognizer", "text recognition failed: $e")
            emptyList()
        } finally {
            bmp.recycle()
        }
    }

    fun close() {
        client.close()
    }

    companion object {
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
