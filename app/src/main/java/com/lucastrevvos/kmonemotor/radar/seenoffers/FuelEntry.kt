package com.lucastrevvos.kmonemotor.radar.seenoffers

data class FuelEntry(
    val id: String,
    val amountBrl: Double,
    val liters: Double?,
    val fuelType: String?,
    val createdAtMs: Long,
    val note: String? = null
)

interface FuelEntryRepository {
    fun saveFuelEntry(entry: FuelEntry): FuelEntry
    fun listFuelEntries(limit: Int = 500): List<FuelEntry>
    fun deleteFuelEntry(id: String): Boolean
}
