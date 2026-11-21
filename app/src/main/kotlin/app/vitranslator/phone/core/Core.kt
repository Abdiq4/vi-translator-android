package app.vitranslator.phone.core

/**
 * What the app needs from VI Translator's shared core (the Rust crate vi-translator-core, through
 * its UniFFI bindings: [RustCore]): reading the game's frames, translating what is read, and the
 * TVs that show it. The rest of the app only talks to this, so the stand-in ([StubCore], for builds
 * without the core's library) and the real core swap without touching it.
 *
 * Every call may block (network, files, text recognition): not on the main thread unless noted.
 */
interface Core {
    /** Which core this is, for the about line ("stand-in" or the real core's version). Quick. */
    val name: String

    /** The games it knows (GTA VI first). Quick. */
    fun games(): List<Game>

    /** The languages it translates into. Quick. */
    fun languages(): List<Language>

    /**
     * Get ready for this game and language: loads the translation memory and starts downloading
     * the reviewed translations. Throws [CoreException] when it can't.
     */
    fun prepare(game: String, language: String)

    /**
     * One frame of the game (RGBA, 4 bytes a pixel, rows of [width] pixels). The core crops its
     * areas, has the app's [Recognizer] read them, and translates; the TVs show the result. Returns
     * how many milliseconds to wait before the next frame (the core sets the pace).
     */
    fun processFrame(rgba: ByteArray, width: Int, height: Int): Long

    /** No picture now (the stream stopped): what is shown goes once that lasts. */
    fun noPicture()

    /** The latest lines read and translated, newest last (at most [max]). */
    fun lines(max: Int): List<Line>

    /** Told when [lines] changes, and of status messages (on the core's threads). Null to stop. */
    fun setListener(listener: CoreListener?)

    /** The translation service: [Service.MEMORY] (the translation memory only, free) or a service with its key. */
    fun service(): Pair<Service, String>

    fun setService(service: Service, key: String)

    /** Serve the TV page on the home network (the TVs load it from here). The port. */
    fun startServer(): Int

    fun stopServer()

    /** Look for TVs running a VI Translator TV app on the home network (a few seconds). */
    fun findTvs(): List<Tv>

    /**
     * Connect a TV with the 6-digit code it shows: [address] from [findTvs] ("192.168.1.30:8792"),
     * or one the user typed (an IP address alone takes the TV app's port).
     */
    fun pairTv(address: String, code: String): PairResult

    /** The TVs connected so far (remembered across runs). */
    fun pairedTvs(): List<Tv>

    /** Forget a TV (its key stops working; the TV shows a new code). */
    fun forgetTv(id: String)
}

/**
 * The app's text recognition, which the core calls (synchronously, from its reading thread) on the
 * crops it prepared: grey pictures, dark text on light, 4 bytes a pixel.
 */
interface Recognizer {
    fun recognize(bgra: ByteArray, width: Int, height: Int): List<Word>
}

/** The translation services the core can use (the core's "engine" setting and the key's name). */
enum class Service(val engine: String, val keyName: String?) {
    /** no service: only lines the translation memory and the reviewed translations know */
    MEMORY("local", null),
    DEEPL("deepl", "deepl_key"),
    GOOGLE("google", "google_key"),
    CLAUDE("claude", "claude_key"),
    OPENAI("openai", "openai_key"),
}

data class Game(val id: String, val name: String)

/** [code]: a BCP 47 tag ("ar", "pt-BR"); [name]: in the language itself; [rtl]: written right to left. */
data class Language(val code: String, val name: String, val rtl: Boolean)

/** A word text recognition found, in pixels of the picture it read, and the line it is in (0, 1...). */
data class Word(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val line: Int)

/** A line read on the game and its translation ([translated] empty when there is none). */
data class Line(val id: Long, val areaId: String, val original: String, val translated: String, val timeMs: Long)

/** [id]: the TV app's own id; [address]: "ip:port" (empty when not known). */
data class Tv(val id: String, val name: String, val address: String)

enum class PairResult {
    OK,

    /** the code isn't the one the TV shows */
    WRONG_CODE,

    /** too many wrong codes: the TV shows a new one */
    NEW_CODE,

    /** no TV app answered at that address */
    UNREACHABLE,

    /** this phone isn't serving the TV page (no Wi-Fi?) */
    NOT_SHARING,
}

interface CoreListener {
    fun linesChanged()

    /** A status message from the core (a download, a translation service's problem). */
    fun status(message: String) {}
}

class CoreException(message: String) : Exception(message)
