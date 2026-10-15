package app.bedrockhost.mobile.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRuntimeContractTest {
    @Test
    fun unavailableRuntimeNeverReportsSuccessfulStart() = runBlocking {
        val runtime = UnavailableServerRuntime()

        val result = runtime.start("survival")

        assertEquals(ServerState.NOT_INSTALLED, result.state)
        assertFalse(result.canStop)
        assertNull(result.address)
        assertNull(result.playersOnline)
        assertTrue(result.message.contains("not been verified"))
    }

    @Test
    fun unavailableRuntimeExplainsStopWhenNoProcessExists() = runBlocking {
        val result = UnavailableServerRuntime().stop()

        assertEquals(ServerState.NOT_INSTALLED, result.state)
        assertTrue(result.message.contains("No server process is running"))
    }
}
