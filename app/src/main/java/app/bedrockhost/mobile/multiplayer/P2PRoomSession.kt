package app.bedrockhost.mobile.multiplayer

import java.security.SecureRandom

/**
 * Local lifecycle model for CraftNode-to-CraftNode sessions.
 *
 * This deliberately does not claim to establish a network connection. A signaling
 * transport and a tested Bedrock-compatible join path must be supplied before a
 * room code can be shared between devices or used to join a world.
 */
data class P2PRoomSession(
    val code: String,
    val role: Role,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val state: State,
    val detail: String
) {
    enum class Role { HOST, JOINER }
    enum class State {
        WAITING_FOR_FRIEND,
        CONNECTING,
        CONNECTED,
        FAILED,
        CLOSED
    }

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis >= expiresAtMillis

    fun close(): P2PRoomSession = copy(
        state = State.CLOSED,
        detail = "Session closed"
    )

    companion object {
        private const val CODE_LENGTH = 6
        private const val CODE_TTL_MILLIS = 15 * 60 * 1000L
        private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private val random = SecureRandom()

        /** Creates a local session descriptor. Does not publish it to another device. */
        fun createHostSession(nowMillis: Long = System.currentTimeMillis()): P2PRoomSession {
            val code = buildString(CODE_LENGTH) {
                repeat(CODE_LENGTH) {
                    append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
                }
            }
            return P2PRoomSession(
                code = code,
                role = Role.HOST,
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + CODE_TTL_MILLIS,
                state = State.WAITING_FOR_FRIEND,
                detail = "Waiting for signaling and a compatible connection transport"
            )
        }

        fun createJoinRequest(codeInput: String, nowMillis: Long = System.currentTimeMillis()): P2PRoomSession? {
            val normalized = codeInput.trim().uppercase()
            if (normalized.length != CODE_LENGTH || normalized.any { it !in CODE_ALPHABET }) {
                return null
            }
            return P2PRoomSession(
                code = normalized,
                role = Role.JOINER,
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + CODE_TTL_MILLIS,
                state = State.CONNECTING,
                detail = "Waiting for signaling and host validation"
            )
        }
    }
}
