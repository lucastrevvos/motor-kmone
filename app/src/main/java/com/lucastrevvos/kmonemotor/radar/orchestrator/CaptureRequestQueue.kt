package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

class CaptureRequestQueue {
    private val requestsBySource = linkedMapOf<TriggerSource, CaptureRequest>()

    fun enqueue(request: CaptureRequest): CaptureRequest? {
        return requestsBySource.put(request.triggerSource, request)
    }

    fun latest(): List<CaptureRequest> = requestsBySource.values.toList()
}
