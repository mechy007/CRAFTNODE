package app.bedrockhost.mobile.server

/** Typed view of the server.properties values CraftNode lets the user edit. */
data class ServerSettings(
    val motd: String = "CraftNode Server",
    val gamemode: String = "survival",
    val difficulty: String = "normal",
    val maxPlayers: Int = 20,
    val port: Int = 19132,
    val whitelistEnabled: Boolean = false
) {
    /** Human-readable problems; empty when the settings are safe to write. */
    fun validationErrors(): List<String> = buildList {
        if (motd.isBlank()) add("MOTD must not be blank.")
        if (motd.length > ServerPropertiesEditor.MAX_MOTD_LENGTH) add("MOTD must be at most ${ServerPropertiesEditor.MAX_MOTD_LENGTH} characters.")
        if (motd.any { it.isISOControl() }) add("MOTD must not contain line breaks or control characters.")
        if (';' in motd) add("MOTD must not contain ';' because Bedrock uses it as a field separator.")
        if (gamemode !in ServerPropertiesEditor.GAMEMODES) add("Unknown game mode: $gamemode.")
        if (difficulty !in ServerPropertiesEditor.DIFFICULTIES) add("Unknown difficulty: $difficulty.")
        if (maxPlayers !in 1..ServerPropertiesEditor.MAX_PLAYERS_LIMIT) add("Max players must be 1-${ServerPropertiesEditor.MAX_PLAYERS_LIMIT}.")
        if (port !in ServerPropertiesEditor.MIN_PORT..ServerPropertiesEditor.MAX_PORT) add("Port must be ${ServerPropertiesEditor.MIN_PORT}-${ServerPropertiesEditor.MAX_PORT}; Android apps cannot bind lower ports.")
    }
}

/**
 * Reads and updates a PocketMine-style server.properties file as text.
 * Comments, unknown keys (for example xbox-auth) and their order are preserved.
 */
object ServerPropertiesEditor {
    val GAMEMODES = listOf("survival", "creative", "adventure", "spectator")
    val DIFFICULTIES = listOf("peaceful", "easy", "normal", "hard")
    const val MAX_MOTD_LENGTH = 64
    const val MAX_PLAYERS_LIMIT = 100
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    fun read(text: String): ServerSettings {
        val values = parse(text)
        val defaults = ServerSettings()
        return ServerSettings(
            motd = values["motd"]?.takeIf { it.isNotBlank() } ?: defaults.motd,
            gamemode = values["gamemode"]?.let(::normalizeGamemode) ?: defaults.gamemode,
            difficulty = values["difficulty"]?.let(::normalizeDifficulty) ?: defaults.difficulty,
            maxPlayers = values["max-players"]?.toIntOrNull()?.takeIf { it in 1..MAX_PLAYERS_LIMIT } ?: defaults.maxPlayers,
            port = values["server-port"]?.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT } ?: defaults.port,
            whitelistEnabled = values["white-list"]?.lowercase() in setOf("on", "true", "1", "yes")
        )
    }

    /** Returns [text] with the editable keys set from [settings]. Throws if the settings are invalid. */
    fun apply(text: String, settings: ServerSettings): String {
        val errors = settings.validationErrors()
        require(errors.isEmpty()) { errors.joinToString(" ") }
        val updates = linkedMapOf(
            "motd" to settings.motd,
            "gamemode" to settings.gamemode,
            "difficulty" to settings.difficulty,
            "max-players" to settings.maxPlayers.toString(),
            "server-port" to settings.port.toString(),
            "white-list" to if (settings.whitelistEnabled) "on" else "off"
        )
        val lines = if (text.isEmpty()) mutableListOf() else text.removeSuffix("\n").split("\n").toMutableList()
        val handled = mutableSetOf<String>()
        for (i in lines.indices) {
            val key = keyOf(lines[i]) ?: continue
            val value = updates[key] ?: continue
            if (handled.add(key)) lines[i] = "$key=$value"
        }
        for ((key, value) in updates) if (key !in handled) lines.add("$key=$value")
        return lines.joinToString("\n") + "\n"
    }

    private fun parse(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (line in text.lines()) {
            val key = keyOf(line) ?: continue
            if (key !in result) result[key] = line.trim().substringAfter('=').trim()
        }
        return result
    }

    private fun keyOf(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val index = trimmed.indexOf('=')
        if (index <= 0) return null
        return trimmed.substring(0, index).trim()
    }

    private fun normalizeGamemode(raw: String): String? {
        val v = raw.trim().lowercase()
        return when (v) {
            "0" -> "survival"
            "1" -> "creative"
            "2" -> "adventure"
            "3" -> "spectator"
            else -> v.takeIf { it in GAMEMODES }
        }
    }

    private fun normalizeDifficulty(raw: String): String? {
        val v = raw.trim().lowercase()
        return when (v) {
            "0" -> "peaceful"
            "1" -> "easy"
            "2" -> "normal"
            "3" -> "hard"
            else -> v.takeIf { it in DIFFICULTIES }
        }
    }
}
