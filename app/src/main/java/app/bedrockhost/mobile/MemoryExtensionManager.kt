package app.bedrockhost.mobile

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.File

/** Read-only no-root memory diagnostics. Never attempts to activate system swap. */
object MemoryExtensionManager {
    data class Snapshot(val totalRamMb: Long, val availableRamMb: Long, val appPssMb: Long, val activeSwapMb: Long?, val swapStatus: String, val recommendation: String) {
        fun asText(): String = buildString {
            append("Physical RAM: $totalRamMb MB total; $availableRamMb MB available. ")
            append("CraftNode app PSS: $appPssMb MB. Active system swap: ")
            append(activeSwapMb?.let { "$it MB listed in /proc/swaps" } ?: swapStatus)
            append(". "); append(recommendation)
        }
    }

    fun inspect(context: Context): Snapshot {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val totalMb = memory.totalMem / (1024L * 1024L)
        val availableMb = memory.availMem / (1024L * 1024L)
        val appPssMb = Debug.getPss().toLong() / 1024L
        val swapFile = File("/proc/swaps")
        var swapTotalKb = 0L
        var swapReadable = false
        if (swapFile.canRead()) {
            swapReadable = true
            runCatching {
                swapFile.useLines { lines -> lines.drop(1).forEach { line ->
                    val columns = line.trim().split(Regex("\\s+"))
                    if (columns.size >= 4) swapTotalKb += columns[2].toLongOrNull() ?: 0L
                } }
            }.onFailure { swapReadable = false }
        }
        val activeSwapMb = if (swapReadable) swapTotalKb / 1024L else null
        val swapStatus = when {
            !swapReadable -> "status unreadable (Android permission/device limitation)"
            activeSwapMb == 0L -> "none listed by the kernel"
            else -> "active swap detected"
        }
        val reserveMb = maxOf(768L, totalMb / 8L)
        val suggestedHeapMb = (availableMb - reserveMb).coerceAtLeast(512L).coerceAtMost(2048L)
        val recommendation = if (availableMb <= reserveMb) {
            "Low available RAM: close other apps before hosting. PowerNukkitX is not yet runnable in this build."
        } else {
            "Future PowerNukkitX starting heap ceiling to evaluate: ${suggestedHeapMb} MB. Recommendation only; it is not applied to a server yet."
        }
        return Snapshot(totalMb, availableMb, appPssMb, activeSwapMb, swapStatus, recommendation)
    }
}
