package app.vitranslator.phone.ps

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import app.vitranslator.phone.ps.chiaki.Codec
import app.vitranslator.phone.ps.chiaki.ConnectInfo
import app.vitranslator.phone.ps.chiaki.ConnectVideoProfile
import app.vitranslator.phone.ps.chiaki.ConnectedEvent
import app.vitranslator.phone.ps.chiaki.CreateError
import app.vitranslator.phone.ps.chiaki.LoginPinRequestEvent
import app.vitranslator.phone.ps.chiaki.QuitEvent
import app.vitranslator.phone.ps.chiaki.RumbleEvent
import app.vitranslator.phone.ps.chiaki.Session
import app.vitranslator.phone.ps.chiaki.VideoFPSPreset
import app.vitranslator.phone.ps.chiaki.VideoResolutionPreset
import app.vitranslator.phone.read.Reader
import app.vitranslator.phone.read.YuvFrame

/**
 * PS Remote Play from the PS5, for reading only: chiaki-ng's session decodes the video with
 * MediaCodec straight into an ImageReader (no screen), no sound. Every frame is taken from the
 * reader at once and given back (so the decoder never waits); about 4 a second are read.
 */
class PsStream(
    private val console: PsConsole,
    private val reader: Reader,
    private val listener: Listener,
    private val logFile: String?,
) {
    interface Listener {
        fun onState(state: State, detail: String?)

        /** The PS5 asks for the user's login passcode (the PSN account has one). */
        fun onLoginPin(incorrect: Boolean)
    }

    enum class State { CONNECTING, STREAMING, ENDED, FAILED }

    private val thread = HandlerThread("ps-frames").apply { start() }
    private val handler = Handler(thread.looper)
    private var images: ImageReader? = null
    private var session: Session? = null

    /** frames the decoder gave (all of them, read or not) */
    @Volatile
    var frames = 0L
        private set

    fun start(resolution: VideoResolutionPreset = VideoResolutionPreset.RES_1080P) {
        val profile = ConnectVideoProfile.preset(resolution, VideoFPSPreset.FPS_30, Codec.CODEC_H264)
        val ir = ImageReader.newInstance(profile.width, profile.height, ImageFormat.YUV_420_888, 3)
        ir.setOnImageAvailableListener({ r ->
            val img = try {
                r.acquireLatestImage()
            } catch (e: IllegalStateException) {
                null
            } ?: return@setOnImageAvailableListener
            try {
                frames++
                reader.offer(YuvFrame(img))
            } catch (e: RuntimeException) {
                Log.w(TAG, "frame", e)
            } finally {
                img.close()
            }
        }, handler)
        images = ir

        val s = try {
            Session(ConnectInfo(true, console.host, console.registKey, console.rpKey, profile), logFile, false)
        } catch (e: CreateError) {
            listener.onState(State.FAILED, e.errorCode.toString())
            return
        }
        s.eventCallback = { ev ->
            when (ev) {
                ConnectedEvent -> listener.onState(State.STREAMING, null)
                is LoginPinRequestEvent -> listener.onLoginPin(ev.pinIncorrect)
                is QuitEvent -> listener.onState(if (ev.reason.isError) State.FAILED else State.ENDED, ev.reasonString ?: ev.reason.toString())
                is RumbleEvent -> Unit
            }
        }
        s.setSurface(ir.surface)
        session = s
        listener.onState(State.CONNECTING, null)
        val err = s.start()
        if (!err.isSuccess) listener.onState(State.FAILED, err.toString())
    }

    fun setLoginPin(pin: String) {
        session?.setLoginPin(pin)
    }

    fun stop() {
        val s = session
        session = null
        if (s != null) {
            s.stop()
            s.dispose()
        }
        images?.close()
        images = null
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "PsStream"
    }
}
