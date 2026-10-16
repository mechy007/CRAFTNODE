package app.bedrockhost.mobile.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPropertiesEditorTest {
    @Test
    fun emptyFileReadsAsDefaults() {
        assertEquals(ServerSettings(), ServerPropertiesEditor.read(""))
    }

    @Test
    fun readsNumericAndNamedValues() {
        val settings = ServerPropertiesEditor.read("gamemode=1\ndifficulty=3\nmax-players=8\nwhite-list=on\nserver-port=19140\n")
        assertEquals("creative", settings.gamemode)
        assertEquals("hard", settings.difficulty)
        assertEquals(8, settings.maxPlayers)
        assertEquals(19140, settings.port)
        assertTrue(settings.whitelistEnabled)
    }

    @Test
    fun outOfRangeValuesFallBackToDefaults() {
        val settings = ServerPropertiesEditor.read("max-players=0\nserver-port=80\n")
        assertEquals(20, settings.maxPlayers)
        assertEquals(19132, settings.port)
    }

    @Test
    fun applyKeepsCommentsAndUnknownKeys() {
        val original = "# comment\nmotd=Old\nserver-port=19133\nxbox-auth=off\n"
        val result = ServerPropertiesEditor.apply(original, ServerSettings(motd = "New", port = 19140))
        assertEquals(
            "# comment\nmotd=New\nserver-port=19140\nxbox-auth=off\n" +
                "gamemode=survival\ndifficulty=normal\nmax-players=20\nwhite-list=off\n",
            result
        )
    }

    @Test
    fun applyRejectsInvalidSettings() {
        assertThrows(IllegalArgumentException::class.java) { ServerPropertiesEditor.apply("", ServerSettings(port = 80)) }
        assertThrows(IllegalArgumentException::class.java) { ServerPropertiesEditor.apply("", ServerSettings(motd = "a;b")) }
        assertThrows(IllegalArgumentException::class.java) { ServerPropertiesEditor.apply("", ServerSettings(motd = "a\nb")) }
    }
}
