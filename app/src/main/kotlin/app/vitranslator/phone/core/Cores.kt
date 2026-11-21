package app.vitranslator.phone.core

import android.content.Context
import android.util.Log

object Cores {
    /**
     * The real core (libvi_translator_core.so and its UniFFI bindings) once it is in the build;
     * until then, or if it fails to load, the stand-in.
     */
    fun create(ctx: Context): Core {
        return try {
            val cls = Class.forName("app.vitranslator.phone.core.RustCore")
            cls.getConstructor(Context::class.java).newInstance(ctx) as Core
        } catch (e: ClassNotFoundException) {
            StubCore(ctx)
        } catch (e: Throwable) {
            Log.w("Cores", "the core didn't load, using the stand-in", e)
            StubCore(ctx)
        }
    }
}
