package com.lucastrevvos.kmonemotor.radar.core

data class RadarConfig(
    val monitoredPackages: Set<String> = setOf(
        UBER_DRIVER_PACKAGE,
        NINETY_NINE_DRIVER_PACKAGE,
        NINETY_NINE_DRIVER_LEGACY_PACKAGE
    ),
    val dominantCoverageThreshold: Double = 0.50,
    val floatingCoverageThreshold: Double = 0.25,
    val uberFloatingMinCoverage: Double = 0.005,
    val uberFloatingMaxCoverage: Double = 0.08
) {
    companion object {
        const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        const val NINETY_NINE_DRIVER_PACKAGE = "com.app99.driver"
        const val NINETY_NINE_DRIVER_LEGACY_PACKAGE = "com.taxis99"

        val Default = RadarConfig()
    }
}
