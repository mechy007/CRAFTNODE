package app.bedrockhost.mobile.reliability

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Reads thermal, memory and battery-optimization state. Cheap enough to call every few seconds. */
class DeviceHealthMonitor(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): DeviceHealth {
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else null
        return DeviceHealth(
            thermal = HealthAdvisor.thermalLevel(thermalStatus),
            availableMemoryMb = memory.availMem / (1024 * 1024),
            lowMemory = memory.lowMemory,
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        )
    }

    /** Opens the system battery-optimization list; needs no extra permission. */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
