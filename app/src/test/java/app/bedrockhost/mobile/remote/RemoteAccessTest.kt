package app.bedrockhost.mobile.remote

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAccessTest {
    private fun pong(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(1 + 8 + 8 + 16 + 2 + body.size)
            .put(0x1C.toByte()).putLong(1L).putLong(2L).put(RakNetPing.MAGIC)
            .putShort(body.size.toShort()).put(body).array()
    }

    @Test
    fun buildsPing() {
        val ping = RakNetPing.buildUnconnectedPing(1L, 2L)
        assertEquals(33, ping.size)
        assertEquals(1, ping[0].toInt())
        assertEquals(0x00, ping[9].toInt())
        assertEquals(0xff.toByte(), ping[10])
        assertEquals(0x78, ping[24].toInt())
    }

    @Test
    fun parsesPong() {
        val info = RakNetPing.parsePong(pong("MCPE;My Server;860;1.21.0;3;20;123456;World;Survival;1;19132;19133;"))
        assertEquals(PongInfo("My Server", 860, "1.21.0", 3, 20), info)
    }

    @Test
    fun rejectsGarbageAndTruncatedPongs() {
        assertNull(RakNetPing.parsePong(ByteArray(10)))
        assertNull(RakNetPing.parsePong(ByteArray(64)))
        val valid = pong("MCPE;My Server;860;1.21.0;3;20")
        assertNull(RakNetPing.parsePong(valid, valid.size - 5))
    }

    @Test
    fun parsesAddresses() {
        assertEquals(ServerAddress("1.2.3.4", 19132), ServerAddress.parse("1.2.3.4"))
        assertEquals(ServerAddress("host.example.com", 25565), ServerAddress.parse("host.example.com:25565"))
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("a:b:c"))
        assertNull(ServerAddress.parse("1.2.3.4:99999"))
        assertNull(ServerAddress.parse("bad host"))
    }

    @Test
    fun shareTextMentionsTailscaleOnlyForTailscaleAddresses() {
        val tailscale = ServerAddressShare.shareText("Friends", ServerAddress("100.100.50.2"))
        assertTrue(tailscale.contains("Port: 19132"))
        assertTrue(tailscale.contains("Tailscale"))
        assertTrue(!ServerAddressShare.shareText("Friends", ServerAddress("192.168.1.5")).contains("Tailscale"))
    }
}
