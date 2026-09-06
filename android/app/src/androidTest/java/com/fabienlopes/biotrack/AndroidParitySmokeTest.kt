package com.fabienlopes.biotrack

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.LocalStore
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.SnapshotMigration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidParitySmokeTest {
    @Test
    fun mainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(!activity.isFinishing)
            }
        }
    }

    @Test
    fun firstInstallAndUpgradedSnapshotPersistOnAndroidStorage() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val isolatedFiles = File(application.cacheDir, "parity-store-${System.nanoTime()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(application) {
            override fun getFilesDir(): File = isolatedFiles
        }
        try {
            val store = LocalStore(isolatedContext)
            val firstInstall = store.load()
            assertTrue(firstInstall.protocols.isEmpty())

            val legacy = AppSnapshot(
                schemaVersion = 3,
                protocols = listOf(
                    ProtocolItem(id = "as-needed", name = "Libre", frequency = Frequency.weekly(emptyList()))
                )
            )
            store.save(legacy)

            val upgraded = store.load()
            assertEquals(SnapshotMigration.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion)
            assertTrue(upgraded.protocols.single().frequency.isAsNeeded)
            assertEquals(10, upgraded.metrics.size)
        } finally {
            isolatedFiles.deleteRecursively()
        }
    }
}
