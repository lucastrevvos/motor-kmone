package com.lucastrevvos.kmonemotor.radar.tracking

interface TrackingLocationClient {
    fun start(
        onPoint: (LocationSnapshot) -> Unit,
        onStatus: (TrackingGpsStatus) -> Unit,
        onError: (Throwable) -> Unit
    ): Boolean

    fun stop()
}
