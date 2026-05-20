package com.lucastrevvos.kmonemotor.radar.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DebugLogExportBuilderTest {
    @Test
    fun build_createsZipAndManifestEvenWhenOptionalSourcesMissing() {
        val tempRoot = createTempDir(prefix = "kmone_export_test_")
        val exportDir = File(tempRoot, "exports")
        val existingDir = File(tempRoot, "existing").apply { mkdirs() }
        File(existingDir, "a.txt").writeText("hello")

        val result = DebugLogExportBuilder().build(
            exportDir = exportDir,
            zipName = "sample.zip",
            sources = listOf(
                DebugExportSource(existingDir, "debug"),
                DebugExportSource(File(tempRoot, "missing"), "missing")
            ),
            manifestContent = "exportedAt=1\n",
            maxExports = 5
        )

        assertTrue(result.zipFile.exists())
        ZipFile(result.zipFile).use { zip ->
            assertTrue(zip.getEntry("export_manifest.txt") != null)
            assertTrue(zip.getEntry("debug/a.txt") != null)
        }
    }

    @Test
    fun cleanupOldExports_keepsOnlyNewest() {
        val tempRoot = createTempDir(prefix = "kmone_export_cleanup_")
        val exportDir = File(tempRoot, "exports").apply { mkdirs() }
        val oldest = File(exportDir, "oldest.zip").apply { writeText("1"); setLastModified(1L) }
        File(exportDir, "middle.zip").apply { writeText("2"); setLastModified(2L) }
        File(exportDir, "newest.zip").apply { writeText("3"); setLastModified(3L) }

        val deleted = DebugLogExportBuilder().cleanupOldExports(exportDir, maxExports = 2)

        assertEquals(listOf(oldest.name), deleted.map { it.name })
        assertTrue(!oldest.exists())
    }
}
