package app.bedrockhost.mobile.craftmind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ServerRuntimeStatusStoreTest {
    @Test fun writesAndReadsVerifiedRuntimeSnapshotFields() {
        val directory = Files.createTempDirectory("craftnode-runtime-status").toFile()
        try {
            val store = ServerRuntimeStatusStore(File(directory, "status.properties"))
            store.write("running", "survival", "novamine", 4321L)
            val snapshot = store.read()
            assertNotNull(snapshot)
            assertEquals("running", snapshot?.state)
            assertEquals("survival", snapshot?.profileId)
            assertEquals("novamine", snapshot?.engineId)
            assertEquals(4321L, snapshot?.pid)
            assertEquals(true, (snapshot?.updatedAt ?: 0L) > 0L)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun missingOrMalformedSnapshotIsNotTreatedAsRunning() {
        val directory = Files.createTempDirectory("craftnode-runtime-status").toFile()
        try {
            val file = File(directory, "status.properties")
            val store = ServerRuntimeStatusStore(file)
            assertNull(store.read())
            file.writeText("state=pretend-running\npid=abc\n")
            assertNull(store.read())
        } finally {
            directory.deleteRecursively()
        }
    }
}
