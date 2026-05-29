package com.lucastrevvos.kmonemotor.radar.seenoffers

data class TrackingRecordUiModel(
    val title: String,
    val amountLabel: String?,
    val durationLabel: String,
    val distanceLabel: String,
    val revenueLabel: String
)

object TrackingRecordUiMapper {
    fun map(record: TrackingRecord): TrackingRecordUiModel {
        return TrackingRecordUiModel(
            title = trackingRecordTypeLabel(record.type),
            amountLabel = record.amount?.takeIf { it > 0.0 }?.let { "R$ " + "%.2f".format(java.util.Locale.US, it).replace(".", ",") },
            durationLabel = formatDuration(record.durationSeconds),
            distanceLabel = record.distanceKm?.takeIf { it > 0.0 }?.let {
                "%.1f km".format(java.util.Locale.US, it).replace(".", ",")
            } ?: "—",
            revenueLabel = if (record.type == TrackingRecordType.PRIVATE_RIDE && (record.amount ?: 0.0) > 0.0) {
                "Receita particular"
            } else {
                "Sem receita"
            }
        )
    }

    private fun formatDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60L
        val seconds = durationSeconds % 60L
        return when {
            minutes > 0L -> "${minutes} min"
            else -> "${seconds}s"
        }
    }
}
