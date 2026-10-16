package app.bedrockhost.mobile.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2PRoomSessionTest {
    @Test
    fun hostSessionCreatesShortLivedRoomCode() {
        val session = P2PRoomSession.createHostSession(nowMillis = 1_000L)
        assertEquals(P2PRoomSession.Role.HOST, session.role)
        assertEquals(6, session.code.length)
        assertEquals(P2PRoomSession.State.WAITING_FOR_FRIEND, session.state)
        assertFalse(session.isExpired(1_001L))
        assertTrue(session.isExpired(session.expiresAtMillis))
    }

    @Test
    fun joinRequestNormalizesValidCodeAndRejectsInvalidInput() {
        val host = P2PRoomSession.createHostSession(nowMillis = 1_000L)
        assertNotNull(P2PRoomSession.createJoinRequest(" " + host.code.lowercase() + " ", 2_000L))
        assertNull(P2PRoomSession.createJoinRequest("bad!", 2_000L))
        assertNull(P2PRoomSession.createJoinRequest("123", 2_000L))
    }

    @Test
    fun closingSessionInvalidatesItsLifecycleState() {
        val closed = P2PRoomSession.createHostSession(nowMillis = 1_000L).close()
        assertEquals(P2PRoomSession.State.CLOSED, closed.state)
        assertEquals("Session closed", closed.detail)
    }
}
