package app.bedrockhost.mobile.craftmind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftMindReadOnlyBridgeTest {
    private class FakeSource : CraftMindDataSource {
        var calls = 0
        override fun serverStatus(requestId: String) = unavailable("server.status").copy(requestId = requestId)
        override fun listWorlds(requestId: String): CraftMindToolResult {
            calls++
            return CraftMindToolResult(requestId = requestId, tool = "world.list", status = CraftMindToolStatus.SUCCESS, message = "ok", verified = true, data = mapOf("count" to "2"))
        }
        override fun getWorld(requestId: String, worldId: String) = CraftMindToolResult(requestId = requestId, tool = "world.get", status = CraftMindToolStatus.SUCCESS, message = "ok", verified = true, data = mapOf("worldId" to worldId))
        override fun recentLogs(requestId: String, maxLines: Int) = CraftMindToolResult(requestId = requestId, tool = "server.logs.explain", status = CraftMindToolStatus.SUCCESS, message = "ok", verified = true, data = mapOf("lineCount" to maxLines.toString()))
        override fun listPlayers(requestId: String) = unavailable("players.list")
        private fun unavailable(tool: String) = CraftMindToolResult(requestId = "internal", tool = tool, status = CraftMindToolStatus.UNSUPPORTED, message = "not available", verified = false)
    }

    private val source = FakeSource()
    private val bridge = CraftMindReadOnlyBridge(source)

    @Test fun rejectsUnknownProtocol() {
        val result = bridge.execute(CraftMindToolRequest(protocol = "craftmind.bridge.v99", requestId = "r1", tool = "world.list"))
        assertEquals(CraftMindToolStatus.REJECTED, result.status)
        assertEquals("UNSUPPORTED_PROTOCOL", result.errorCode)
        assertEquals(0, source.calls)
    }

    @Test fun rejectsUnlistedWriteTool() {
        val result = bridge.execute(CraftMindToolRequest(requestId = "r1", tool = "server.stop"))
        assertEquals(CraftMindToolStatus.REJECTED, result.status)
        assertEquals("TOOL_NOT_ALLOWED", result.errorCode)
        assertEquals(0, source.calls)
    }

    @Test fun validatesWorldIdBeforeDataAccess() {
        val result = bridge.execute(CraftMindToolRequest(requestId = "r1", tool = "world.get", arguments = mapOf("worldId" to "../secrets")))
        assertEquals(CraftMindToolStatus.REJECTED, result.status)
        assertEquals(0, source.calls)
    }

    @Test fun routesAllowedReadOnlyTool() {
        val result = bridge.execute(CraftMindToolRequest(requestId = "r2", tool = "world.list"))
        assertEquals(CraftMindToolStatus.SUCCESS, result.status)
        assertTrue(result.verified)
        assertEquals("2", result.data["count"])
        assertEquals(1, source.calls)
    }

    @Test fun boundsLogRequest() {
        val result = bridge.execute(CraftMindToolRequest(requestId = "r3", tool = "server.logs.explain", arguments = mapOf("maxLines" to "101")))
        assertEquals(CraftMindToolStatus.REJECTED, result.status)
        assertEquals("INVALID_ARGUMENTS", result.errorCode)
    }

    @Test fun unsupportedDataIsNotReportedAsVerifiedSuccess() {
        val result = bridge.execute(CraftMindToolRequest(requestId = "r4", tool = "players.list"))
        assertEquals(CraftMindToolStatus.UNSUPPORTED, result.status)
        assertFalse(result.verified)
    }
}
