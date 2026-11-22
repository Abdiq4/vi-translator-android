package app.vitranslator.phone.card

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import app.vitranslator.phone.BuildConfig
import app.vitranslator.phone.ps.GlFrames
import app.vitranslator.phone.read.Reader

/**
 * A USB capture card as the picture source: the console's HDMI goes into the card (most cards pass
 * the picture on to the TV), the card's USB into the phone. Phones that support USB cameras (UVC:
 * many on Android 14 and later) list the card as an external camera, opened here with Android's
 * camera API; the others don't see it at all (they would need their own USB driver for it).
 *
 * The card's picture goes through the GPU (GlFrames, as the PS5's): the camera draws into a
 * texture whatever format the card sends (MJPEG or YUY2, decoded by the phone), and a frame is read
 * as plain RGBA, so no frame layout is assumed.
 */
class CardStream(private val ctx: Context, private val reader: Reader, private val listener: (State, String?) -> Unit) {
    enum class State { STREAMING, ENDED, FAILED }

    private val thread = HandlerThread("card").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var gl: GlFrames? = null

    @Volatile
    private var stopped = false

    /** frames the card gave (all of them, read or not) */
    @Volatile
    var frames = 0L
        private set

    /** the size the card sends, once open ("1920x1080") */
    @Volatile
    var size: String? = null
        private set

    /** Opens the card, at 1080p (or with [big], its largest picture up to 4K). */
    @SuppressLint("MissingPermission") // (asked for before translating starts: MainActivity)
    fun start(big: Boolean) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = find(ctx)
        if (id == null) {
            listener(State.FAILED, if (supported(ctx)) "no capture card found: is it plugged in?" else "this phone doesn't support USB capture cards")
            return
        }
        val map = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        val pick = best(sizes, big) { s ->
            val d = map?.getOutputMinFrameDuration(SurfaceTexture::class.java, s) ?: 0L
            if (d > 0) 1e9 / d else 30.0
        }
        if (pick == null) {
            listener(State.FAILED, "the capture card offers no picture size")
            return
        }
        size = "${pick.width}x${pick.height}"
        Log.i(TAG, "card $id: $size of ${sizes.joinToString { "${it.width}x${it.height}" }}")
        val frames = GlFrames(pick.width, pick.height) { frame ->
            this.frames++
            try {
                reader.offer(frame)
            } catch (e: RuntimeException) {
                Log.w(TAG, "frame", e)
            }
        }
        val surface = if (frames.start()) frames.surface else null
        if (surface == null) {
            frames.stop()
            listener(State.FAILED, "video: the phone's GPU could not be used")
            return
        }
        gl = frames
        try {
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    if (stopped) {
                        c.close()
                        return
                    }
                    camera = c
                    @Suppress("DEPRECATION")
                    c.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (stopped) return
                            session = s
                            try {
                                val req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
                                s.setRepeatingRequest(req.build(), null, handler)
                                listener(State.STREAMING, null)
                            } catch (e: Exception) {
                                Log.w(TAG, "request", e)
                                listener(State.FAILED, "the capture card could not start: ${e.message}")
                            }
                        }

                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            listener(State.FAILED, "the capture card could not start (its picture size isn't accepted)")
                        }
                    }, handler)
                }

                override fun onDisconnected(c: CameraDevice) {
                    c.close()
                    camera = null
                    if (!stopped) listener(State.ENDED, "the capture card was unplugged")
                }

                override fun onError(c: CameraDevice, error: Int) {
                    c.close()
                    camera = null
                    if (!stopped) listener(State.FAILED, "the capture card stopped (error $error; is another app using it?)")
                }
            }, handler)
        } catch (e: Exception) {
            Log.w(TAG, "open", e)
            listener(State.FAILED, "the capture card could not be opened: ${e.message}")
        }
    }

    fun stop() {
        stopped = true
        handler.post {
            try {
                session?.close()
            } catch (_: Exception) {
            }
            session = null
            camera?.close()
            camera = null
            gl?.stop()
            gl = null
            thread.quitSafely()
        }
    }

    companion object {
        private const val TAG = "CardStream"

        /** The phone can use USB cameras (and so capture cards). */
        fun supported(ctx: Context) = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL)

        /**
         * The card plugged in now: the first external camera. (In a debug build with none, the first
         * camera, to try the rest of the way on the emulator's camera.)
         */
        fun find(ctx: Context): String? {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val ids = try {
                cm.cameraIdList.toList()
            } catch (e: Exception) {
                Log.w(TAG, "cameras", e)
                return null
            }
            return ids.firstOrNull {
                try {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
                } catch (_: Exception) {
                    false
                }
            } ?: if (BuildConfig.DEBUG) ids.firstOrNull() else null
        }

        /**
         * The size to stream, as the desktop picks the card's format (device.rs format_score): the
         * largest up to 1920x1080 (or with [big] up to 3840x2160; larger ones count less), then the
         * highest frame rate up to 60.
         */
        fun best(sizes: List<Size>, big: Boolean, fps: (Size) -> Double): Size? {
            val most = if (big) 3840L * 2160 else 1920L * 1080
            return sizes.maxByOrNull { s ->
                val area = s.width.toLong() * s.height
                val sizeScore = if (area <= most) area else most - (area - most) / 4
                sizeScore * 1000 + (fps(s).coerceAtMost(60.0) * 10).toLong()
            }
        }
    }
}
