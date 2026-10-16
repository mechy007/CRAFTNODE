package app.bedrockhost.mobile.server

/**
 * Decides whether a server that exited should be restarted. Uses exponential backoff and gives up
 * after [maxRestarts] crashes inside [windowMillis] so a broken server cannot loop forever and drain the battery.
 */
class CrashRestartPolicy(
    private val maxRestarts: Int = 3,
    private val windowMillis: Long = 10 * 60_000L,
    private val baseDelayMillis: Long = 5_000L,
    private val maxDelayMillis: Long = 60_000L
) {
    init {
        require(maxRestarts >= 0) { "maxRestarts must not be negative." }
        require(windowMillis > 0 && baseDelayMillis > 0 && maxDelayMillis >= baseDelayMillis) { "Invalid timing values." }
    }

    sealed interface Decision {
        data class Restart(val attempt: Int, val delayMillis: Long) : Decision
        data class GiveUp(val reason: String) : Decision
        data object NoAction : Decision
    }

    private val crashTimes = ArrayDeque<Long>()

    /** [exitCode] is null when unknown. A user-requested stop or a clean exit (0) never restarts. */
    @Synchronized
    fun onExit(exitCode: Int?, userRequestedStop: Boolean, nowMillis: Long): Decision {
        if (userRequestedStop) {
            crashTimes.clear()
            return Decision.NoAction
        }
        if (exitCode == 0) return Decision.NoAction
        while (crashTimes.isNotEmpty() && nowMillis - crashTimes.first() > windowMillis) crashTimes.removeFirst()
        if (crashTimes.size >= maxRestarts) {
            return Decision.GiveUp("Server crashed ${crashTimes.size + 1} times within ${windowMillis / 60_000} minutes; auto-restart is paused.")
        }
        crashTimes.addLast(nowMillis)
        val attempt = crashTimes.size
        val delay = minOf(maxDelayMillis, baseDelayMillis shl minOf(attempt - 1, 20))
        return Decision.Restart(attempt, delay)
    }

    @Synchronized
    fun reset() = crashTimes.clear()
}
