package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.io.File
import java.util.Base64

class FileFuelEntryRepository(
    private val storageFile: File
) : FuelEntryRepository {
    private val lock = Any()

    override fun saveFuelEntry(entry: FuelEntry): FuelEntry = synchronized(lock) {
        val entries = loadEntries().toMutableList()
        entries += entry
        persistEntries(entries)
        entry
    }

    override fun listFuelEntries(limit: Int): List<FuelEntry> = synchronized(lock) {
        loadEntries().sortedByDescending { it.createdAtMs }.take(limit)
    }

    override fun deleteFuelEntry(id: String): Boolean = synchronized(lock) {
        val entries = loadEntries()
        val filtered = entries.filterNot { it.id == id }
        if (entries.size == filtered.size) return false
        persistEntries(filtered)
        true
    }

    private fun loadEntries(): List<FuelEntry> {
        if (!storageFile.exists()) return emptyList()
        return storageFile.readLines()
            .filter { it.isNotBlank() }
            .map { it.toFuelEntry() }
    }

    private fun persistEntries(entries: List<FuelEntry>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(entries.joinToString(separator = "\n") { it.serialize() })
    }

    private fun FuelEntry.serialize(): String = listOf(
        encode(id),
        amountBrl.toString(),
        encodeNullable(liters),
        encodeNullable(fuelType),
        createdAtMs.toString(),
        encodeNullable(note)
    ).joinToString(separator = "\t")

    private fun String.toFuelEntry(): FuelEntry {
        val parts = split('\t')
        return FuelEntry(
            id = decode(parts[0]),
            amountBrl = parts[1].toDouble(),
            liters = decodeNullable(parts[2])?.toDoubleOrNull(),
            fuelType = decodeNullable(parts[3]),
            createdAtMs = parts[4].toLong(),
            note = decodeNullable(parts[5])
        )
    }

    private fun encode(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray())
    }

    private fun encodeNullable(value: Any?): String {
        return value?.toString()?.let(::encode) ?: "-"
    }

    private fun decode(value: String): String {
        return String(Base64.getDecoder().decode(value))
    }

    private fun decodeNullable(value: String): String? {
        return if (value == "-") null else decode(value)
    }
}
