package app.bedrockhost.mobile.server

/**
 * Builds console commands for player management. Every command is validated so it can be passed to
 * ServerHostService.ACTION_COMMAND (single line, at most 512 characters) without injection of extra commands.
 */
object PlayerCommands {
    const val MAX_NAME_LENGTH = 32
    const val MAX_REASON_LENGTH = 120
    private val NAME = Regex("^[A-Za-z0-9_#-]+( [A-Za-z0-9_#-]+)*$")

    fun isValidName(name: String): Boolean = name.length in 1..MAX_NAME_LENGTH && NAME.matches(name)

    fun whitelistAdd(name: String) = "whitelist add ${arg(name)}"
    fun whitelistRemove(name: String) = "whitelist remove ${arg(name)}"
    fun whitelistOn() = "whitelist on"
    fun whitelistOff() = "whitelist off"
    fun op(name: String) = "op ${arg(name)}"
    fun deop(name: String) = "deop ${arg(name)}"
    fun pardon(name: String) = "pardon ${arg(name)}"
    fun kick(name: String, reason: String? = null) = withReason("kick", name, reason)
    fun ban(name: String, reason: String? = null) = withReason("ban", name, reason)
    fun listPlayers() = "list"

    private fun arg(name: String): String {
        require(isValidName(name)) { "Invalid player name." }
        return if (' ' in name) "\"$name\"" else name
    }

    private fun withReason(command: String, name: String, reason: String?): String {
        val base = "$command ${arg(name)}"
        val clean = reason
            ?.filter { !it.isISOControl() && it != '"' }
            ?.trim()
            ?.take(MAX_REASON_LENGTH)
            ?.takeIf { it.isNotEmpty() }
        return if (clean == null) base else "$base $clean"
    }
}

data class OnlinePlayers(val online: Int, val max: Int, val names: List<String>)

/** Parses the output of the server's `list` command from console log lines. */
object PlayerListParser {
    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val PREFIX = Regex("^\\s*(\\d{10,}\\s+)?(\\[[^\\]]*\\]\\s*)*:?\\s*")
    private val HEADER = Regex("There are (\\d+)/(\\d+) players online", RegexOption.IGNORE_CASE)

    fun parse(lines: List<String>): OnlinePlayers? {
        val cleaned = lines.map(::clean)
        val index = cleaned.indexOfLast { HEADER.containsMatchIn(it) }
        if (index < 0) return null
        val match = HEADER.find(cleaned[index]) ?: return null
        val online = match.groupValues[1].toIntOrNull() ?: return null
        val max = match.groupValues[2].toIntOrNull() ?: return null
        val names = if (online == 0) {
            emptyList()
        } else {
            val candidates = cleaned.getOrNull(index + 1).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (candidates.size == online && candidates.all(PlayerCommands::isValidName)) candidates else emptyList()
        }
        return OnlinePlayers(online, max, names)
    }

    private fun clean(line: String): String = ANSI.replace(line, "").replace(PREFIX, "").trim()
}
