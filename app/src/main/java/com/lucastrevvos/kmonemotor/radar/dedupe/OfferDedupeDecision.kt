package com.lucastrevvos.kmonemotor.radar.dedupe

enum class OfferDedupeDecision {
    NEW_OFFER_CANDIDATE,
    SAME_OFFER_UPDATED,
    SAME_OFFER_IGNORED_WEAKER,
    NON_OFFER_IGNORED,
    UNKNOWN_IGNORED
}
