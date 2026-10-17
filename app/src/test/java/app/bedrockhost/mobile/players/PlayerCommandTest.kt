package app.bedrockhost.mobile.players

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerCommandTest {
    @Test fun buildsExpectedCommands() {
        assertEquals("list", PlayerCommand.list())
        assertEquals("whitelist add Steve_1", PlayerCommand.whitelistAdd("Steve_1"))
        assertEquals("whitelist remove Steve", PlayerCommand.whitelistRemove("Steve"))
        assertEquals("op Steve", PlayerCommand.operatorAdd("Steve"))
        assertEquals("deop Steve", PlayerCommand.operatorRemove("Steve"))
        assertEquals("kick Steve Be nice", PlayerCommand.kick("Steve", "Be nice"))
        assertEquals("ban Steve", PlayerCommand.ban("Steve"))
    }

    @Test fun rejectsCommandInjectionAndInvalidNames() {
        assertNull(PlayerCommand.operatorAdd("Steve;stop"))
        assertNull(PlayerCommand.whitelistAdd("Steve\nstop"))
        assertNull(PlayerCommand.kick("Steve", "nope\nstop"))
        assertNull(PlayerCommand.kick("Steve", "bad @ reason"))
        assertNull(PlayerCommand.ban(""))
    }
}
