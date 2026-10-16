package app.bedrockhost.mobile.remote

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import kotlin.random.Random

data class PongInfo(
    val motd: String,
    val protocol: Int?,
    val version: String?,
    val playersOnline: Int?,
    val playersMax: Int?
)

/** Minimal RakNet "unconnected ping" client, the same probe Minecraft uses to list servers. */
object RakNetPing {
    internal val MAGIC: ByteArray = intArrayOf(
        0x00, 0xff, 0xff, 0x00, 0xfe, 0xfe, 0xfe, 0xfe, 0xfd, 0xfd, 0xfd, 0xfd, 0x12, 0x34, 0x56, 0x78
    ).map { it.toByte() }.toByteArray()
    private const val ID_PING = 0x01
    private const val ID_PONG = 0x1C
    private const val PONG_HEADER_SIZE = 1 + 8 + 8 + 16 + 2

    fun buildUnconnectedPing(timeMillis: Long, clientGuid: Long): ByteArray =
        ByteBuffer.allocate(1 + 8 + 16 + 8)
            .put(ID_PING.toByte())
            .putLong(timeMillis)
            .put(MAGIC)
            .putLong(clientGuid)
            .array()

    /** Returns null for anything that is not a well-formed Bedrock pong. */
    fun parsePong(data: ByteArray, length: Int = data.size): PongInfo? {
        if (length < PONG_HEADER_SIZE || length > data.size) return null
        if ((data[0].toInt() and 0xFF) != ID_PONG) return null
        for (i in MAGIC.indices) if (data[17 + i] != MAGIC[i]) return null
        val textLength = ((data[33].toInt() and 0xFF) shl 8) or (data[34].toInt() and 0xFF)
        if (35 + textLength > length) return null
        val parts = String(data, 35, textLength, Charsets.UTF_8).split(';')
        if (parts.size < 2 || parts[0] !in setOf("MCPE", "MCEE")) return null
        return PongInfo(
            motd = parts[1],
            protocol = parts.getOrNull(2)?.toIntOrNull(),
            version = parts.getOrNull(3),
            playersOnline = parts.getOrNull(4)?.toIntOrNull(),
            playersMax = parts.getOrNull(5)?.toIntOrNull()
        )
    }
}

sealed interface ReachabilityResult {
    data class Reachable(val info: PongInfo, val latencyMillis: Long) : ReachabilityResult
    data class NoResponse(val hint: String) : ReachabilityResult
    data class Failed(val message: String) : ReachabilityResult
}

/**
 * Checks whether a Bedrock server answers on a host and UDP port. Blocking: call from a background thread.
 * For a meaningful remote test, run it from a different device or network than the host; testing a phone's
 * own address only proves the server is running locally.
 */
class BedrockReachabilityChecker(
    private val timeoutMillis: Int = 2_000,
    private val attempts: Int = 2
) {
    fun check(host: String, port: Int = 19132): ReachabilityResult {
        val target = host.trim()
        if (target.isEmpty() || target.length > 253 || target.any { it.isWhitespace() }) {
            return ReachabilityResult.Failed("Enter a valid host name or IP address.")
        }
        if (port !in 1..65535) return ReachabilityResult.Failed("Port must be between 1 and 65535.")
        return try {
            val address = InetAddress.getByName(target)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis
                repeat(attempts) {
                    val started = System.nanoTime()
                    val ping = RakNetPing.buildUnconnectedPing(System.currentTimeMillis(), Random.nextLong())
                    socket.send(DatagramPacket(ping, ping.size, address, port))
                    val buffer = ByteArray(1500)
                    val reply = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(reply)
                        val info = RakNetPing.parsePong(buffer, reply.length)
                        if (info != null) {
                            return ReachabilityResult.Reachable(info, (System.nanoTime() - started) / 1_000_000)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Try again, then report no response.
                    }
                }
            }
            ReachabilityResult.NoResponse(
                "No Bedrock response. Check that the server is running, the port matches, and that both phones are " +
                    "connected to the same Tailscale network. UDP can be filtered, so this does not prove the server is down."
            )
        } catch (_: UnknownHostException) {
            ReachabilityResult.Failed("Could not resolve that host name.")
        } catch (e: Exception) {
            ReachabilityResult.Failed("Check failed: ${e.javaClass.simpleName}")
        }
    }
}
