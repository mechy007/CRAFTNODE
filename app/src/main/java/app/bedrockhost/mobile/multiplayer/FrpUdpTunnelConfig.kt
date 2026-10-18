package app.bedrockhost.mobile.multiplayer

/**
 * Validated configuration for a CraftNode-managed FRP UDP proxy.
 *
 * This builds client configuration only. It does not launch a tunnel or prove connectivity.
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
        require(isValidHost(relayHost)) { "Relay host must be a hostname or IPv4 address, without a scheme or path." }
        require(relayControlPort in VALID_PORTS) { "Relay control port must be between 1 and 65535." }
        require(publicUdpPort in VALID_PORTS) { "Public UDP port must be between 1 and 65535." }
        require(localUdpPort in VALID_PORTS) { "Local UDP port must be between 1 and 65535." }
        require(authToken.isNotBlank()) { "Relay authentication token is required." }
        require(proxyName.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Proxy name contains unsupported characters." }
    }

    /** The result contains a secret token. Never log or commit it. */
    fun toToml(): String = buildString {
        appendLine("serverAddr = \${tomlString(relayHost)}")
        appendLine("serverPort = $relayControlPort")
        appendLine()
        appendLine("auth.method = \\"token\\"")
        appendLine("auth.token = \${tomlString(authToken)}")
        appendLine("transport.tls.enable = true")
        appendLine()
        appendLine("[[proxies]]")
        appendLine("name = \${tomlString(proxyName)}")
        appendLine("type = \\"udp\\"")
        appendLine("localIP = \\"127.0.0.1\\"")
        appendLine("localPort = $localUdpPort")
        appendLine("remotePort = $publicUdpPort")
    }

    companion object {
        const val DEFAULT_BEDROCK_PORT = 19132
        private val VALID_PORTS = 1..65535
        private val HOST_LABEL = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        private fun isValidHost(host: String): Boolean {
            if (host.isBlank() || host.length > 253 || host.contains("://") ||
                host.any { it == '/' || it == '\\\\' || it.isWhitespace() }
            ) return false
            if (host.matches(Regex("[0-9]{1,3}(\\\\.[0-9]{1,3}){3}"))) {
                return host.split('.').all { it.toIntOrNull() in 0..255 }
            }
            return host.split('.').all { HOST_LABEL.matches(it) }
        }

        private fun tomlString(value: String): String = "\\"" + value
            .replace("\\\\\\\\", "\\\\\\\\\\\\\\\\")
            .replace("\\"", "\\\\\\\\"")
            .replace("\\n", "\\\\n")
            .replace("\\r", "\\\\r")
            .replace("\\t", "\\\\t") + "\\""
    }
}

/** UI states for a future tunnel controller; READY requires actual health checks. */
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
