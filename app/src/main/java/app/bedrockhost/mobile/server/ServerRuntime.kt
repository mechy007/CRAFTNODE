package app.bedrockhost.mobile.server

/**
 * Runtime boundary for a real Bedrock-compatible server engine.
 *
 * No engine implementation is shipped yet. UI code must not report a server as running
 * unless an implementation emits [ServerState.RUNNING] after detecting real engine readiness.
 */
enum class ServerState {
    NOT_INSTALLED,
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}

data class ServerSnapshot(
    val state: ServerState = ServerState.NOT_INSTALLED,
    val message: String = "No compatible server runtime is installed or verified.",
    val address: String? = null,
    val port: Int? = null,
    val playersOnline: Int? = null,
    val maxPlayers: Int? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    val canStart: Boolean
        get() = state == ServerState.STOPPED || state == ServerState.FAILED

    val canStop: Boolean
        get() = state == ServerState.STARTING || state == ServerState.RUNNING
}

/**
 * Contract implemented by the selected engine adapter once Android compatibility is proven.
 * Implementations must stream actual process logs and use real readiness/exit signals.
 */
interface ServerRuntime {
    fun snapshot(): ServerSnapshot

    suspend fun start(profileId: String): ServerSnapshot

    suspend fun stop(): ServerSnapshot

    fun addLogListener(listener: (String) -> Unit): AutoCloseable
}

/**
 * Safe default until a verified engine adapter is integrated. It never simulates success.
 */
class UnavailableServerRuntime : ServerRuntime {
    private val current = ServerSnapshot()

    override fun snapshot(): ServerSnapshot = current

    override suspend fun start(profileId: String): ServerSnapshot = current.copy(
        state = ServerState.NOT_INSTALLED,
        message = "Cannot start: a compatible Android server runtime has not been verified.",
        updatedAtEpochMillis = System.currentTimeMillis()
    )

    override suspend fun stop(): ServerSnapshot = current.copy(
        state = ServerState.NOT_INSTALLED,
        message = "No server process is running.",
        updatedAtEpochMillis = System.currentTimeMillis()
    )

    override fun addLogListener(listener: (String) -> Unit): AutoCloseable = AutoCloseable { }
}
