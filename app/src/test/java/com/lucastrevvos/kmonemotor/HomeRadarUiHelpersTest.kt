package com.lucastrevvos.kmonemotor

import com.lucastrevvos.kmonemotor.radar.seenoffers.RidePlatform
import com.lucastrevvos.kmonemotor.radar.seenoffers.SavedRide
import com.lucastrevvos.kmonemotor.radar.seenoffers.SavedRideSource
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffer
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferStatus
import com.lucastrevvos.kmonemotor.radar.tracking.TrackingGpsStatus
import com.lucastrevvos.kmonemotor.radar.tracking.trackingGpsStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRadarUiHelpersTest {
    @Test
    fun radarHidden_usesAbrirRadarLabel() {
        assertEquals("Abrir Radar", homeRadarActionLabel(isRadarVisible = false))
    }

    @Test
    fun radarVisible_usesOcultarRadarLabel() {
        assertEquals("Ocultar Radar", homeRadarActionLabel(isRadarVisible = true))
    }

    @Test
    fun trackingIdle_usesIniciarTrackingLabel() {
        assertEquals("Iniciar tracking", trackingPrimaryButtonLabel(TrackingUiStatus.IDLE))
    }

    @Test
    fun trackingRunning_exposesFinishAndCancelLabels() {
        assertEquals(listOf("Encerrar e salvar", "Cancelar"), trackingRunningActionLabels())
    }

    @Test
    fun displacementType_usesDeslocamentoLabel() {
        assertEquals("Deslocamento", trackingTypeLabel(TrackingSaveType.DISPLACEMENT))
    }

    @Test
    fun privateRideType_usesCorridaParticularLabel() {
        assertEquals("Corrida particular", trackingTypeLabel(TrackingSaveType.PRIVATE_RIDE))
    }

    @Test
    fun missingPermissionGpsStatus_usesWaitingPermissionLabel() {
        assertEquals("GPS: aguardando permissao", trackingGpsStatusLabel(TrackingGpsStatus.WAITING_PERMISSION))
    }

    @Test
    fun recordsDisplayRides_hidesPrivateRideAuxiliarySavedRide() {
        val visible = recordsDisplayRides(
            listOf(
                savedRide(id = "normal", source = SavedRideSource.MANUAL_ENTRY),
                savedRide(id = "private", source = SavedRideSource.PRIVATE_RIDE)
            )
        )

        assertEquals(listOf("normal"), visible.map { it.id })
    }

    @Test
    fun visibleHomeSeenOffers_excludesIgnoredOffers() {
        val visible = visibleHomeSeenOffers(
            listOf(
                seenOffer(id = "seen", status = SeenOfferStatus.SEEN, createdAtMs = 2_000L),
                seenOffer(id = "ignored", status = SeenOfferStatus.IGNORED, createdAtMs = 3_000L)
            )
        )

        assertEquals(listOf("seen"), visible.map { it.id })
    }

    @Test
    fun visibleHomeSeenOffers_returnsEmptyWhenOnlyIgnoredOffersRemain() {
        val visible = visibleHomeSeenOffers(
            listOf(seenOffer(id = "ignored", status = SeenOfferStatus.IGNORED, createdAtMs = 3_000L))
        )

        assertEquals(emptyList<String>(), visible.map { it.id })
    }

    private fun seenOffer(
        id: String,
        status: SeenOfferStatus,
        createdAtMs: Long
    ) = SeenOffer(
        id = id,
        observationId = "obs-$id",
        platform = RidePlatform.UBER,
        sourceTrigger = "test",
        status = status,
        price = 31.76,
        valuePerKm = 2.4,
        pickupDistanceKm = 1.0,
        pickupTimeMin = 4.0,
        tripDistanceKm = 6.0,
        tripTimeMin = 12.0,
        totalDistanceKm = 7.0,
        estimatedTotalTimeMin = 16.0,
        productName = "UberX",
        originPreview = "Origem",
        destinationPreview = "Destino",
        rawTextPreview = "UberX",
        score = 1,
        rawTextHash = null,
        routeTextHash = null,
        createdAtMs = createdAtMs,
        updatedAtMs = createdAtMs
    )

    private fun savedRide(
        id: String,
        source: SavedRideSource
    ) = SavedRide(
        id = id,
        sourceSeenOfferId = null,
        platform = RidePlatform.UNKNOWN,
        price = 10.0,
        valuePerKm = null,
        pickupDistanceKm = null,
        pickupTimeMin = null,
        tripDistanceKm = null,
        tripTimeMin = null,
        totalDistanceKm = null,
        estimatedTotalTimeMin = null,
        productName = null,
        originPreview = null,
        destinationPreview = null,
        acceptedAtMs = 1_000L,
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L,
        source = source
    )
}
