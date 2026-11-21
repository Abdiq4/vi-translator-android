package app.vitranslator.phone.core

import android.content.Context
import android.util.Log

object Cores {
    /**
     * The real core when this build has its library (libvi_translator_ffi.so, built by
     * native/build.sh); otherwise, or if it fails to start, the stand-in.
     */
    fun create(ctx: Context, recognizer: Recognizer): Core = try {
        RustCore(ctx, recognizer)
    } catch (e: Throwable) {
        // (UnsatisfiedLinkError: a build without the core's library)
        Log.w("Cores", "the core didn't load, using the stand-in: $e")
        StubCore(ctx, recognizer)
    }
}
