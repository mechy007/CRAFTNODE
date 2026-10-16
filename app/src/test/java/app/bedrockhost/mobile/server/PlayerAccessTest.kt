package app.bedrockhost.mobile.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerAccessTest {
    @Test
    fun buildsCommands() {
        assertEquals("whitelist add Steve", PlayerCommands.whitelistAdd("Steve"))
        assertEquals("whitelist add \"Steve Smith\"", PlayerCommands.whitelistAdd("Steve Smith"))
        assertEquals("op Alex", PlayerCommands.op("Alex"))
        assertEquals("list", PlayerCommands.listPlayers())
    }

    @Test
    fun rejectsNamesThatCouldInjectCommands() {
        assertThrows(IllegalArgumentException::class.java) { PlayerCommands.ban("Steve; stop") }
        assertThrows(IllegalArgumentException::class.java) { PlayerCommands.kick("Steve\nstop") }
        assertThrows(IllegalArgumentException::class.java) { PlayerCommands.op("") }
    }

    @Test
    fun cleansReasons() {
        assertEquals("kick Steve Spam x", PlayerCommands.kick("Steve", "Spam \"x\"\n"))
        assertEquals("ban Steve", PlayerCommands.ban("Steve", "   "))
    }

    @Test
    fun parsesPlayerList() {
        val lines = listOf(
            "1699999999999 [12:00:00] [Server thread/INFO]: There are 2/20 players online:",
            "1699999999999 [12:00:00] [Server thread/INFO]: Steve, Alex"
        )
        assertEquals(OnlinePlayers(2, 20, listOf("Steve", "Alex")), PlayerListParser.parse(lines))
    }

    @Test
    fun parsesEmptyListAndMissingHeader() {
        assertEquals(OnlinePlayers(0, 20, emptyList()), PlayerListParser.parse(listOf("There are 0/20 players online:")))
        assertNull(PlayerListParser.parse(listOf("Server started")))
    }
}
