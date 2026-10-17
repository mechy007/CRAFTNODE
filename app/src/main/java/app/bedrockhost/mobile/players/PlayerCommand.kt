package app.bedrockhost.mobile.players

/**
 * Builds safe, single-line PocketMine-style moderation commands.
 *
 * This only validates and builds commands. The caller must send them to a running,
 * compatible server and inspect the server console for the actual result.
 * Falcon command compatibility is not yet verified.
 */
object PlayerCommand {
    private val playerName = Regex("[A-Za-z0-9_]{1,16}")
    private val reason = Regex("[A-Za-z0-9 _.,!?'():-]{1,120}")

    fun list(): String = "list"

    fun whitelistAdd(name: String): String? =
        name.takeIf(playerName::matches)?.let { "whitelist add $it" }

    fun whitelistRemove(name: String): String? =
        name.takeIf(playerName::matches)?.let { "whitelist remove $it" }

    fun operatorAdd(name: String): String? =
        name.takeIf(playerName::matches)?.let { "op $it" }

    fun operatorRemove(name: String): String? =
        name.takeIf(playerName::matches)?.let { "deop $it" }

    fun kick(name: String, message: String = "Removed by server operator"): String? {
        if (!playerName.matches(name)) return null
        val safeReason = message.trim()
        if (!reason.matches(safeReason)) return null
        return "kick $name $safeReason"
    }

    fun ban(name: String): String? =
        name.takeIf(playerName::matches)?.let { "ban $it" }
}
