package com.lucastrevvos.kmonemotor.radar.debug

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DebugExportResult {
    data class Success(
        val file: File,
        val uri: Uri,
        val filesCount: Int
    ) : DebugExportResult()

    data class Failure(
        val reason: String,
        val throwable: Throwable? = null
    ) : DebugExportResult()
}

class DebugLogExporter(
    private val context: Context,
    private val builder: DebugLogExportBuilder = DebugLogExportBuilder()
) {
    fun export(): DebugExportResult {
        RadarLogger.i("KM_V2_DEBUG", "KM_V2_DEBUG_EXPORT_STARTED")
        return runCatching {
            DebugEventLogStore.flushBlocking()
            val exportDir = File(context.cacheDir, "kmone_exports").apply { mkdirs() }
            builder.cleanupOldExports(exportDir, maxExports = 4).forEach { deleted ->
                RadarLogger.i(
                    "KM_V2_DEBUG",
                    "KM_V2_DEBUG_EXPORT_OLD_FILE_DELETED",
                    "path" to deleted.absolutePath
                )
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipName = "kmone_debug_export_${timestamp}.zip"
            val sources = buildSources()
            val sourceFiles = collectExistingFiles(sources)
            val buildResult = builder.build(
                exportDir = exportDir,
                zipName = zipName,
                sources = sources,
                manifestContent = buildManifest(sourceFiles),
                maxExports = 5
            )
            buildResult.files.forEach { entry ->
                RadarLogger.i(
                    "KM_V2_DEBUG",
                    "KM_V2_DEBUG_EXPORT_FILE_ADDED",
                    "relativePath" to entry.relativePath,
                    "sizeBytes" to entry.sizeBytes
                )
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                buildResult.zipFile
            )
            RadarLogger.i(
                "KM_V2_DEBUG",
                "KM_V2_DEBUG_EXPORT_DONE",
                "zipPath" to buildResult.zipFile.absolutePath,
                "filesCount" to buildResult.files.size,
                "sizeBytes" to buildResult.zipFile.length()
            )
            DebugExportResult.Success(
                file = buildResult.zipFile,
                uri = uri,
                filesCount = buildResult.files.size
            )
        }.getOrElse { throwable ->
            RadarLogger.w(
                "KM_V2_DEBUG",
                "KM_V2_DEBUG_EXPORT_FAILED",
                "reason" to (throwable.message ?: "export_failed"),
                "stacktrace" to throwable.stackTraceToString()
            )
            DebugExportResult.Failure(
                reason = throwable.message ?: "Falha ao exportar logs",
                throwable = throwable
            )
        }
    }

    private fun buildSources(): List<DebugExportSource> {
        val filesDir = context.filesDir
        val sources = mutableListOf<DebugExportSource>()
        DebugEventLogStore.currentLogFile()?.let {
            sources += DebugExportSource(it, "internal_logs")
        }
        sources += DebugExportSource(File(filesDir, "radar"), "state")
        sources += DebugExportSource(File(filesDir, "radar_debug_screenshots"), "screenshots")
        listOf(
            "debug_ocr",
            "debug_fingerprint",
            "debug_parser",
            "debug_decision",
            "debug_dedupe",
            "debug_presentation",
            "debug_visual_probe"
        ).forEach { dirName ->
            context.getExternalFilesDir(dirName)?.let { dir ->
                sources += DebugExportSource(dir, "external/$dirName")
            }
        }
        return sources
    }

    private fun buildManifest(sourceFiles: List<File>): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val timeRange = if (sourceFiles.isEmpty()) {
            "n/a"
        } else {
            "${sourceFiles.minOf { it.lastModified() }}..${sourceFiles.maxOf { it.lastModified() }}"
        }
        val lines = listOf(
            "exportedAt=${System.currentTimeMillis()}",
            "appVersion=${packageInfo.versionName}",
            "packageName=${context.packageName}",
            "deviceModel=${Build.MODEL}",
            "androidVersion=${Build.VERSION.RELEASE}",
            "totalFiles=${sourceFiles.size + 1}",
            "timeRange=$timeRange",
            "buildType=unknown",
            "newArchEnabled=n/a"
        )
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun collectExistingFiles(sources: List<DebugExportSource>): List<File> {
        return sources.flatMap { source ->
            when {
                !source.file.exists() -> emptyList()
                source.file.isFile -> listOf(source.file)
                else -> source.file.walkTopDown().filter { it.isFile }.toList()
            }
        }
    }
}
