package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.io.File

data class DriverSettings(
    val dailyGoalBrl: Double? = null
)

interface DriverSettingsRepository {
    fun getSettings(): DriverSettings
    fun updateDailyGoal(value: Double): DriverSettings
}

class FileDriverSettingsRepository(
    private val storageFile: File
) : DriverSettingsRepository {
    private val lock = Any()

    override fun getSettings(): DriverSettings = synchronized(lock) {
        loadSettings()
    }

    override fun updateDailyGoal(value: Double): DriverSettings = synchronized(lock) {
        val settings = DriverSettings(dailyGoalBrl = value)
        persistSettings(settings)
        settings
    }

    private fun loadSettings(): DriverSettings {
        if (!storageFile.exists()) return DriverSettings()
        val raw = storageFile.readText().trim()
        if (raw.isBlank()) return DriverSettings()
        return DriverSettings(dailyGoalBrl = raw.toDoubleOrNull())
    }

    private fun persistSettings(settings: DriverSettings) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(settings.dailyGoalBrl?.toString().orEmpty())
    }
}
