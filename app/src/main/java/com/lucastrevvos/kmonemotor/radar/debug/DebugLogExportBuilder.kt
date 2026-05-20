package com.lucastrevvos.kmonemotor.radar.debug

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DebugExportSource(
    val file: File,
    val relativePrefix: String = ""
)

data class DebugExportFileEntry(
    val relativePath: String,
    val sizeBytes: Long
)

data class DebugLogBuildResult(
    val zipFile: File,
    val files: List<DebugExportFileEntry>
)

class DebugLogExportBuilder(
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun build(
        exportDir: File,
        zipName: String,
        sources: List<DebugExportSource>,
        manifestContent: String,
        maxExports: Int = 5
    ): DebugLogBuildResult {
        exportDir.mkdirs()
        cleanupOldExports(exportDir = exportDir, maxExports = maxExports - 1)
        val zipFile = File(exportDir, zipName)
        val collectedEntries = mutableListOf<DebugExportFileEntry>()
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            val manifestBytes = manifestContent.toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("export_manifest.txt"))
            zip.write(manifestBytes)
            zip.closeEntry()
            collectedEntries += DebugExportFileEntry("export_manifest.txt", manifestBytes.size.toLong())

            sources.forEach { source ->
                if (!source.file.exists()) return@forEach
                if (source.file.isFile) {
                    addFile(
                        zip = zip,
                        root = source.file.parentFile ?: source.file,
                        file = source.file,
                        relativePrefix = source.relativePrefix,
                        collectedEntries = collectedEntries
                    )
                } else {
                    source.file.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            addFile(
                                zip = zip,
                                root = source.file,
                                file = file,
                                relativePrefix = source.relativePrefix,
                                collectedEntries = collectedEntries
                            )
                        }
                }
            }
        }
        return DebugLogBuildResult(zipFile = zipFile, files = collectedEntries)
    }

    fun cleanupOldExports(exportDir: File, maxExports: Int): List<File> {
        val zipFiles = exportDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        return zipFiles.drop(maxExports.coerceAtLeast(0)).onEach { it.delete() }
    }

    private fun addFile(
        zip: ZipOutputStream,
        root: File,
        file: File,
        relativePrefix: String,
        collectedEntries: MutableList<DebugExportFileEntry>
    ) {
        val relativePath = buildRelativePath(root = root, file = file, relativePrefix = relativePrefix)
        zip.putNextEntry(ZipEntry(relativePath))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
        collectedEntries += DebugExportFileEntry(relativePath = relativePath, sizeBytes = file.length())
    }

    private fun buildRelativePath(root: File, file: File, relativePrefix: String): String {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        return listOf(relativePrefix.trim('/'), relative)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}
