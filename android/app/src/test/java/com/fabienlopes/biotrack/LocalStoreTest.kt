package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.LocalStore
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStoreTest {
    @Test
    fun legacySnapshotMigratesAtomicallyAndSurvivesReload() {
        withTemporaryStore { file, store ->
            file.writeText(
                """
                {
                  "schemaVersion": 3,
                  "supplements": [{
                    "id": "s", "name": "Suivi",
                    "frequency": {"kind": "DAILY", "days": [], "timesPerDay": 1},
                    "timesPerDay": 3
                  }]
                }
                """.trimIndent()
            )

            val first = store.load()
            val second = store.load()

            assertEquals(4, first.schemaVersion)
            assertEquals(FrequencyKind.TIMES_PER_DAY, first.supplements.single().frequency.kind)
            assertEquals(first, second)
            assertTrue(file.readText().contains("\"schemaVersion\": 4"))
            assertTrue(requireNotNull(file.parentFile).listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        }
    }

    @Test
    fun corruptedSnapshotIsPreservedAndNeverOverwrittenAsValidData() {
        withTemporaryStore { file, store ->
            file.writeText("{not-json")

            val loaded = store.load()

            assertEquals(AppSnapshot(), loaded)
            assertEquals("{not-json", file.readText())
            assertTrue(requireNotNull(file.parentFile).listFiles().orEmpty().any { it.name.startsWith("${file.name}.corrupt-") })
        }
    }

    @Test
    fun currentSnapshotSaveLeavesNoTemporaryFile() {
        withTemporaryStore { file, store ->
            store.save(AppSnapshot())

            assertTrue(file.exists())
            assertFalse(requireNotNull(file.parentFile).listFiles().orEmpty().any { it.name.endsWith(".tmp") })
            assertEquals(4, store.load().schemaVersion)
        }
    }

    private fun withTemporaryStore(block: (java.io.File, LocalStore) -> Unit) {
        val directory = Files.createTempDirectory("ajuste-local-store-test").toFile()
        val file = directory.resolve("snapshot.json")
        try {
            block(file, LocalStore.forFile(file))
        } finally {
            directory.deleteRecursively()
        }
    }
}
