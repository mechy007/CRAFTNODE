package app.bedrockhost.mobile.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrpUdpTunnelConfigTest {
    private fun config(
        host: String = "relay.example.net",
        controlPort: Int = 7000,
        token: String = "abcdefghijklmnop",
        publicPort: Int = 19132,
        localPort: Int = 19132
    ) = FrpUdpTunnelConfig(host, controlPort, token, publicPort, localPort)

    @Test
    fun emitsUdpProxyForBedrockPort() {
        val toml = config().toToml()
        val quote = 34.toChar()
        assertTrue(toml.contains("serverAddr = " + quote + "relay.example.net" + quote))
        assertTrue(toml.contains("auth.token = " + quote + "abcdefghijklmnop" + quote))
        assertTrue(toml.contains("type = " + quote + "udp" + quote))
        assertTrue(toml.contains("localPort = 19132"))
        assertTrue(toml.contains("remotePort = 19132"))
        assertTrue(toml.contains("transport.tls.enable = true"))
    }

    @Test
    fun rejectsSchemesPathsAndInvalidHosts() {
        assertThrows(IllegalArgumentException::class.java) { config(host = "https://relay.example.net") }
        assertThrows(IllegalArgumentException::class.java) { config(host = "relay.example.net/path") }
        assertThrows(IllegalArgumentException::class.java) { config(host = "999.1.1.1") }
    }

    @Test
    fun rejectsInvalidPortsAndBlankTokens() {
        assertThrows(IllegalArgumentException::class.java) { config(controlPort = 0) }
        assertThrows(IllegalArgumentException::class.java) { config(publicPort = 65536) }
        assertThrows(IllegalArgumentException::class.java) { config(localPort = -1) }
        assertThrows(IllegalArgumentException::class.java) { config(token = "  ") }
    }

    @Test
    fun readyIsAnExplicitStateNotTheDefault() {
        assertEquals(RemoteTunnelState.NOT_CONFIGURED, RemoteTunnelState.values().first())
        assertTrue(RemoteTunnelState.READY != RemoteTunnelState.CONFIGURED)
    }
}
