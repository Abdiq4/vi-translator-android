package app.vitranslator.phone.core

/**
 * What the app needs from VI Translator's shared core (the Rust crate vi-translator-core, called
 * through UniFFI): the areas of the picture to read, the translation of what was read there, and
 * the TVs that show it. The rest of the app only talks to this, so the stand-in ([StubCore]) and the
 * real core ([RustCore]) swap without touching it.
 *
 * Kept to the shapes UniFFI makes (records, lists, strings, numbers), so the real one maps 1:1.
 * Every call may block (network, files): not on the main thread unless noted.
 */
interface Core {
    /** Which core this is, for the about line ("stand-in" or the real core's version). Quick. */
    val name: String

    /** The games it knows (GTA VI first). Quick. */
    fun games(): List<Game>

    /** The languages it translates into. Quick. */
    fun languages(): List<Language>

    /**
     * Get ready for this game and language: loads (or downloads, then keeps) the translation memory.
     * Throws [CoreException] when it can't (offline on first use, say).
     */
    fun prepare(game: String, language: String)

    /** The areas of the picture to read for the prepared game, as fractions of the frame. Quick. */
    fun areas(): List<Area>

    /**
     * One frame's reading: what text recognition found in each area. The core decides what is new,
     * translates it, and puts it on the TVs. [timeMs]: when the frame was taken (monotonic).
     */
    fun submit(frame: FrameRead)

    /** The latest lines read and translated, newest last (at most [max]). Quick. */
    fun lines(max: Int): List<Line>

    /** Told when [lines] changes (on the core's thread). Null to stop. */
    fun setListener(listener: CoreListener?)

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

    /** The TVs connected so far (remembered across runs). Quick. */
    fun pairedTvs(): List<Tv>

    /** Forget a TV (its key stops working; the TV shows a new code). */
    fun forgetTv(id: String)
}

data class Game(val id: String, val name: String)

/** [code]: a BCP 47 tag ("ar", "pt-BR"); [name]: in the language itself; [rtl]: written right to left. */
data class Language(val code: String, val name: String, val rtl: Boolean)

/** A part of the picture to read: [kind] "subtitle", "objective"...; the rectangle in fractions of the frame (0..1). */
data class Area(val id: String, val kind: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A word text recognition found, in pixels of its area's crop, and the line of the crop it is in (0, 1...). */
data class Word(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val line: Int)

/** One area of one frame: its pixels (RGBA, 4 bytes each, row after row, [width] x [height]) and the words read in it. */
class AreaRead(val areaId: String, val width: Int, val height: Int, val rgba: ByteArray, val words: List<Word>)

/** A frame read: its full size in pixels (the areas are fractions of it) and each area's reading. */
class FrameRead(val width: Int, val height: Int, val timeMs: Long, val areas: List<AreaRead>)

/** A line read on the game and its translation ([translated] empty when there is none yet). */
data class Line(val id: Long, val areaId: String, val original: String, val translated: String, val timeMs: Long)

/** [id]: the TV app's own id; [address]: "ip:port". */
data class Tv(val id: String, val name: String, val address: String)

enum class PairResult {
    OK,

    /** the code isn't the one the TV shows */
    WRONG_CODE,

    /** too many wrong codes: the TV shows a new one */
    NEW_CODE,

    /** no TV app answered at that address */
    UNREACHABLE,
}

interface CoreListener {
    fun linesChanged()
}

class CoreException(message: String) : Exception(message)
