package app.bedrockhost.mobile.reliability

enum class ThermalLevel { NORMAL, WARM, HOT, CRITICAL, UNKNOWN }

data class DeviceHealth(
    val thermal: ThermalLevel,
    val availableMemoryMb: Long,
    val lowMemory: Boolean,
    val ignoringBatteryOptimizations: Boolean
)

/** Pure logic that turns device readings into user-facing guidance. */
object HealthAdvisor {
    /** Maps android.os.PowerManager THERMAL_STATUS_* values (0-6); null when the API is unavailable. */
    fun thermalLevel(status: Int?): ThermalLevel = when (status) {
        null -> ThermalLevel.UNKNOWN
        0, 1 -> ThermalLevel.NORMAL
        2 -> ThermalLevel.WARM
        3 -> ThermalLevel.HOT
        in 4..6 -> ThermalLevel.CRITICAL
        else -> ThermalLevel.UNKNOWN
    }

    fun warnings(health: DeviceHealth): List<String> = buildList {
        when (health.thermal) {
            ThermalLevel.HOT -> add("The phone is running hot. Consider reducing players or pausing the server.")
            ThermalLevel.CRITICAL -> add("The phone is critically hot. Stop the server to avoid forced shutdown.")
            else -> Unit
        }
        if (health.lowMemory) add("Memory is low (${health.availableMemoryMb} MB free). Android may stop the server.")
        if (!health.ignoringBatteryOptimizations) add("Battery optimization is on. Android may stop the server in the background.")
    }
}
