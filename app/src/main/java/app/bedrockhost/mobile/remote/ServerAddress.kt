package app.bedrockhost.mobile.remote

import android.content.Intent
import app.bedrockhost.mobile.reliability.AddressClassifier
import app.bedrockhost.mobile.reliability.AddressKind

/** A Bedrock server address: IPv4 or host name plus UDP port. */
data class ServerAddress(val host: String, val port: Int = DEFAULT_PORT) {
    init {
        require(isValidHost(host)) { "Invalid host." }
        require(port in 1..65535) { "Invalid port." }
    }

    val display: String get() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 19132
        private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

        fun isValidHost(host: String): Boolean = HOST.matches(host)

        /** Parses "host" or "host:port". Returns null for invalid input (IPv6 is not supported). */
        fun parse(input: String): ServerAddress? {
            val text = input.trim()
            if (text.isEmpty() || text.count { it == ':' } > 1) return null
            val host = text.substringBefore(':')
            val port = if (':' in text) text.substringAfter(':').toIntOrNull() ?: return null else DEFAULT_PORT
            if (!isValidHost(host) || port !in 1..65535) return null
            return ServerAddress(host, port)
        }
    }
}

object ServerAddressShare {
    fun shareText(serverName: String, address: ServerAddress): String {
        val name = serverName.filter { !it.isISOControl() && it != '"' }.trim().take(40).ifEmpty { "CraftNode" }
        return buildString {
            appendLine("Join my Minecraft Bedrock server \"$name\"")
            appendLine("Address: ${address.host}")
            appendLine("Port: ${address.port}")
            if (AddressClassifier.classify(address.host) == AddressKind.TAILSCALE_OR_CGNAT) {
                appendLine("You need to be on the same Tailscale network to connect.")
            }
            append("In Minecraft: Play > Servers > Add Server.")
        }
    }

    fun chooserIntent(text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, "Share server address").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
