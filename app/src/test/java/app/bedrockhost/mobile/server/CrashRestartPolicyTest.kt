package app.bedrockhost.mobile.server

import app.bedrockhost.mobile.server.CrashRestartPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRestartPolicyTest {
    @Test
    fun backsOffThenGivesUp() {
        val policy = CrashRestartPolicy()
        assertEquals(Decision.Restart(1, 5_000), policy.onExit(1, false, 0))
        assertEquals(Decision.Restart(2, 10_000), policy.onExit(1, false, 1_000))
        assertEquals(Decision.Restart(3, 20_000), policy.onExit(null, false, 2_000))
        assertTrue(policy.onExit(1, false, 3_000) is Decision.GiveUp)
    }

    @Test
    fun allowsRestartsAgainAfterWindow() {
        val policy = CrashRestartPolicy()
        repeat(3) { policy.onExit(1, false, it * 1_000L) }
        assertEquals(Decision.Restart(1, 5_000), policy.onExit(1, false, 660_000))
    }

    @Test
    fun cleanExitAndUserStopNeverRestart() {
        val policy = CrashRestartPolicy()
        assertEquals(Decision.NoAction, policy.onExit(0, false, 0))
        assertEquals(Decision.NoAction, policy.onExit(1, true, 0))
    }

    @Test
    fun userStopClearsCrashHistory() {
        val policy = CrashRestartPolicy()
        repeat(3) { policy.onExit(1, false, it * 1_000L) }
        policy.onExit(0, true, 4_000)
        assertEquals(Decision.Restart(1, 5_000), policy.onExit(1, false, 5_000))
    }
}
