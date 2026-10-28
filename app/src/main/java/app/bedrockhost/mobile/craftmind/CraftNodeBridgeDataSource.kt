package app.bedrockhost.mobile.craftmind

import android.content.Context
import java.io.File
import java.util.Properties

/** Reads existing app-owned profiles and bounded console logs; never executes commands. */
class CraftNodeBridgeDataSource(context: Context) : CraftMindDataSource {
    private val appContext = context.applicationContext
    private val worldsRoot = File(appContext.filesDir, "worlds")
    private val consoleLog = File(appContext.filesDir, "logs/server-console.log")

    override fun serverStatus(requestId: String) = CraftMindToolResult(
        requestId = requestId, tool = "server.status", status = CraftMindToolStatus.UNSUPPORTED,
        message = "Live server status is not exposed by a verified runtime adapter yet.",
        verified = false, errorCode = "RUNTIME_STATUS_UNAVAILABLE"
    )

    override fun listWorlds(requestId: String): CraftMindToolResult {
        val worlds = worldsRoot.listFiles()?.filter { it.isDirectory && PROFILE_ID.matches(it.name) }?.sortedBy { it.name }.orEmpty()
        val encoded = worlds.joinToString("\n") { dir ->
            val props = loadProperties(File(dir, "server.properties"))
            listOf(dir.name, props.getProperty("level-name", dir.name)).joinToString("\t")
        }.take(CRAFTMIND_MAX_OUTPUT_CHARS)
        return CraftMindToolResult(
            requestId = requestId, tool = "world.list", status = CraftMindToolStatus.SUCCESS,
            message = if (worlds.isEmpty()) "No saved world profiles were found." else "Read ${worlds.size} saved world profile(s).",
            verified = true, data = mapOf("worlds" to encoded, "count" to worlds.size.toString())
        )
    }

    override fun getWorld(requestId: String, worldId: String): CraftMindToolResult {
        val root = worldsRoot.canonicalFile
        val world = File(worldsRoot, worldId).canonicalFile
        if (world.parentFile != root || !world.isDirectory) return CraftMindToolResult(
            requestId = requestId, tool = "world.get", status = CraftMindToolStatus.REJECTED,
            message = "That world profile does not exist.", errorCode = "WORLD_NOT_FOUND"
        )
        val propertiesFile = File(world, "server.properties")
        val properties = loadProperties(propertiesFile)
        val data = linkedMapOf("worldId" to worldId, "profileExists" to "true", "serverPropertiesPresent" to propertiesFile.isFile.toString())
        listOf("level-name", "difficulty", "gamemode", "max-players", "allow-cheats", "level-seed")
            .forEach { key -> properties.getProperty(key)?.let { data[key] = it.take(256) } }
        return CraftMindToolResult(
            requestId = requestId, tool = "world.get", status = CraftMindToolStatus.SUCCESS,
            message = "Read saved settings from server.properties. Some settings may apply only after the next server start.",
            verified = true, data = data
        )
    }

    override fun recentLogs(requestId: String, maxLines: Int): CraftMindToolResult {
        if (!consoleLog.isFile) return CraftMindToolResult(
            requestId = requestId, tool = "server.logs.explain", status = CraftMindToolStatus.UNSUPPORTED,
            message = "No saved server console log is available yet.", errorCode = "LOG_NOT_FOUND"
        )
        val tail = ArrayDeque<String>()
        consoleLog.forEachLine { line ->
            tail.addLast(line)
            if (tail.size > maxLines) tail.removeFirst()
        }
        val lines = tail.toList()
        val safe = lines.joinToString("\n") { redact(it).take(1_000) }.take(CRAFTMIND_MAX_OUTPUT_CHARS)
        return CraftMindToolResult(
            requestId = requestId, tool = "server.logs.explain", status = CraftMindToolStatus.SUCCESS,
            message = "Returned a bounded, redacted console-log excerpt. AI log explanation is not connected yet.",
            verified = true, data = mapOf("logExcerpt" to safe, "lineCount" to lines.size.toString())
        )
    }

    override fun listPlayers(requestId: String) = CraftMindToolResult(
        requestId = requestId, tool = "players.list", status = CraftMindToolStatus.UNSUPPORTED,
        message = "Online players are not exposed through a verified engine-specific parser yet.",
        verified = false, errorCode = "PLAYER_LIST_UNAVAILABLE"
    )

    private fun loadProperties(file: File) = Properties().apply {
        if (file.isFile) runCatching { file.inputStream().buffered().use { load(it) } }
    }

    private fun redact(line: String): String {
        val bearerSafe = Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)\\S+").replace(line) {
            it.groupValues[1] + "[REDACTED]"
        }
        return Regex("(?i)(token|secret|api[_-]?key|password)(\\s*[:=]\\s*)[^\\s,;]+").replace(bearerSafe) {
            it.groupValues[1] + it.groupValues[2] + "[REDACTED]"
        }
    }

    companion object { private val PROFILE_ID = Regex("[A-Za-z0-9_-]{1,64}") }
}
