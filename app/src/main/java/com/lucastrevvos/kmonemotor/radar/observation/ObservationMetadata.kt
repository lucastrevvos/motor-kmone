package com.lucastrevvos.kmonemotor.radar.observation

import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification

data class ObservationMetadata(
    val notes: Map<String, String> = emptyMap(),
    val offerCycleClassification: OfferCycleClassification? = null
)
