package app.bedrockhost.mobile.server

/**
 * User-owned server profile metadata. World files remain separate from this lightweight record.
 * IDs are stable so profiles can later be connected to an actual engine runtime.
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val worldName: String,
    val engineId: String = "unverified",
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Profile ID must not be blank." }
        require(name.isNotBlank()) { "Profile name must not be blank." }
        require(worldName.isNotBlank()) { "World name must not be blank." }
    }
}

/** In-memory starter catalog; persistence can be added without changing the profile contract. */
class ServerProfileCatalog(initialProfiles: List<ServerProfile> = defaults()) {
    private val profiles = initialProfiles.toMutableList()

    fun all(): List<ServerProfile> = profiles.toList()

    fun find(id: String): ServerProfile? = profiles.firstOrNull { it.id == id }

    fun add(profile: ServerProfile): Boolean {
        if (profiles.any { it.id == profile.id }) return false
        profiles += profile
        return true
    }

    fun remove(id: String): Boolean {
        val index = profiles.indexOfFirst { it.id == id }
        if (index < 0) return false
        profiles.removeAt(index)
        return true
    }

    companion object {
        fun defaults(): List<ServerProfile> = listOf(
            ServerProfile("survival", "Survival World", "Survival World"),
            ServerProfile("friends-smp", "Friends SMP", "Friends SMP"),
            ServerProfile("creative-test", "Creative Test", "Creative Test")
        )
    }
}
