package app.vitranslator.phone

import app.vitranslator.phone.core.StubCore
import app.vitranslator.phone.core.TvLink
import app.vitranslator.phone.core.Word
import app.vitranslator.phone.ps.Psn
import app.vitranslator.phone.read.YuvSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LogicTest {
    @Test
    fun wordsBecomeTheAreasText() {
        val words = listOf(
            Word("car", 50, 40, 80, 60, 1),
            Word("Get", 0, 0, 30, 20, 0),
            Word("in", 40, 0, 60, 20, 0),
            Word("the", 0, 40, 40, 60, 1),
        )
        assertEquals("Get in the car", StubCore.textOf(words))
        assertEquals("", StubCore.textOf(emptyList()))
    }

    @Test
    fun theStandInTranslatesKnownPhrases() {
        assertEquals("اركب السيارة", StubCore.translate("Get in the car.", "ar"))
        assertEquals("[es] Get in the car", StubCore.translate("Get in the car", "es"))
    }

    @Test
    fun tvAddresses() {
        assertEquals("192.168.1.30:8792", TvLink.normalize(" 192.168.1.30 "))
        assertEquals("192.168.1.30:9000", TvLink.normalize("192.168.1.30:9000"))
        assertNull(TvLink.normalize("192.168.1.300"))
        assertNull(TvLink.normalize("tv.local"))
        assertNull(TvLink.normalize("192.168.1.30:70000"))
    }

    @Test
    fun psnAccountIdIsEightBytesLittleEndian() {
        // 0x0102030405060708
        val id = Psn.accountIdOf("72623859790382856")
        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), Base64.getDecoder().decode(id).map { it.toInt() })
        // (numbers above 2^63 too)
        assertEquals(8, Base64.getDecoder().decode(Psn.accountIdOf("18446744073709551615")).size)
    }

    @Test
    fun areaRectanglesStayInTheFrame() {
        val r = StubCore.boundsOf(StubCore.Area("s", 0.1f, 0.8f, 0.9f, 1.2f), 1920, 1080)
        assertEquals(listOf(192, 864, 1728, 1080), r.toList())
        val (l, t, rr, b) = StubCore.boundsOf(StubCore.Area("t", 1f, 1f, 1f, 1f), 100, 100).toList()
        assertTrue(rr - l >= 1 && b - t >= 1 && rr <= 100 && b <= 100)
    }

    @Test
    fun updateVersions() {
        assertTrue(Updater.newer("1.2.10", "1.2.9"))
        assertTrue(!Updater.newer("1.2.9", "1.2.9"))
        assertNull(Updater.parse("1.2"))
    }

    /** The PS5 stream's grey (U = V = 128) as RGB: its own black and white come out as the HDMI picture's. */
    private fun grey(y: Int): Int {
        val out = ByteArray(4)
        YuvSnapshot.pixel(out, 0, y, 128, 128)
        assertEquals("grey stays grey", out[0], out[1])
        assertEquals("grey stays grey", out[1], out[2])
        return out[0].toInt() and 255
    }

    @Test
    fun ps5LevelsAreStretchedToTheHdmiPictures() {
        // the stream's blacks (~32 after the video range) and whites (~228) were measured at Y ~ 44 and ~ 212
        assertTrue("black", grey(16) == 0 && grey(44) <= 20)
        assertTrue("white >= 226 (GTA VI's pause-menu checks)", grey(212) >= 240)
        assertEquals(255, grey(235))
        // (and in between it rises steadily)
        assertTrue((16..235).zipWithNext().all { (a, b) -> grey(a) <= grey(b) })
    }
}
