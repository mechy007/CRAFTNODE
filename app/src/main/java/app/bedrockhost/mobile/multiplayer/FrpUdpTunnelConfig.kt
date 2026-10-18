package app.bedrockhost.mobile.multiplayer

/**
 * Validated FRP UDP proxy configuration.
 *
 * This model only creates client configuration. It does not launch a tunnel,
 * provision a relay, or prove that Minecraft can connect.
 */
data class FrpUdpTunnelConfig(
    val relayHost: String,
    val relayControlPort: Int,
    val authToken: String,
    val publicUdpPort: Int,
    val localUdpPort: Int = DEFAULT_BEDROCK_PORT,
    val proxyName: String = "craftnode-bedrock"
) {
    init {
        require(isValidHost(relayHost)) { "Enter a hostname or IPv4 address, not a URL or path." }
        require(relayControlPort in VALID_PORTS) { "Control port must be 1–65535." }
        require(publicUdpPort in VALID_PORTS) { "Public UDP port must be 1–65535." }
        require(localUdpPort in VALID_PORTS) { "Local UDP port must be 1–65535." }
        require(authToken.matches(Regex("[A-Za-z0-9_-]{16,256}"))) {
            "Relay token must contain 16–256 letters, digits, underscores or hyphens."
        }
        require(proxyName.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Invalid proxy name." }
    }

    /** Contains the relay token: never log, export, or commit this string. */
    fun toToml(): String = buildString {
        appendLine("serverAddr = " + quoted(relayHost))
        appendLine("serverPort = " + relayControlPort)
        appendLine()
        appendLine("auth.method = " + quoted("token"))
        appendLine("auth.token = " + quoted(authToken))
        appendLine("transport.tls.enable = true")
        appendLine()
        appendLine("[[proxies]]")
        appendLine("name = " + quoted(proxyName))
        appendLine("type = " + quoted("udp"))
        appendLine("localIP = " + quoted("127.0.0.1"))
        appendLine("localPort = " + localUdpPort)
        appendLine("remotePort = " + publicUdpPort)
    }

    companion object {
        const val DEFAULT_BEDROCK_PORT = 19132
        private val VALID_PORTS = 1..65535
        private val HOST_LABEL = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        private fun isValidHost(host: String): Boolean {
            if (host.isBlank() || host.length > 253 || host.contains("://")) return false
            if (host.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) return false
            val labels = host.split('.')
            if (labels.any { it.isEmpty() || !HOST_LABEL.matches(it) }) return false
            val looksLikeIpv4 = labels.size == 4 && labels.all { label -> label.all { it.isDigit() } }
            return !looksLikeIpv4 || labels.all { it.toIntOrNull() in 0..255 }
        }

        private fun quoted(value: String): String = 34.toChar().toString() + value + 34.toChar()
    }
}

/** Future tunnel-controller states. READY must require actual Bedrock reachability. */
enum class RemoteTunnelState {
    NOT_CONFIGURED,
    CONFIGURED,
    STARTING,
    CONNECTED,
    VERIFYING_BEDROCK,
    READY,
    FAILED,
    STOPPING,
    STOPPED
}
