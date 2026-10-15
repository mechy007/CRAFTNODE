package app.bedrockhost.mobile.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileCatalogTest {
    @Test
    fun startsWithThreeStableLocalProfiles() {
        val catalog = ServerProfileCatalog()

        assertEquals(3, catalog.all().size)
        assertNotNull(catalog.find("survival"))
        assertNotNull(catalog.find("friends-smp"))
        assertNotNull(catalog.find("creative-test"))
    }

    @Test
    fun addingDuplicateIdIsRejected() {
        val catalog = ServerProfileCatalog()
        val added = catalog.add(ServerProfile("survival", "Another name", "Another world"))

        assertFalse(added)
        assertEquals(3, catalog.all().size)
    }

    @Test
    fun addAndRemoveProfileUpdatesCatalog() {
        val catalog = ServerProfileCatalog()
        val profile = ServerProfile("test-new", "Test New", "Test New World")

        assertTrue(catalog.add(profile))
        assertEquals(profile, catalog.find("test-new"))
        assertTrue(catalog.remove("test-new"))
        assertNull(catalog.find("test-new"))
    }
}
