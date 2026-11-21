package app.vitranslator.phone

import android.app.Application
import app.vitranslator.phone.core.Core
import app.vitranslator.phone.core.Cores
import app.vitranslator.phone.read.Reader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        core = Cores.create(this)
    }

    companion object {
        /** The translation core (the real one when this build has it, else the stand-in). */
        lateinit var core: Core
            private set

        /** Reading the game, while translating (TranslateService sets it). */
        @Volatile
        var reader: Reader? = null
    }
}
