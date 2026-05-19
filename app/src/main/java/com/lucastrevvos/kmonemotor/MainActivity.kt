package com.lucastrevvos.kmonemotor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestBus
import com.lucastrevvos.kmonemotor.radar.core.OperationalAppState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.ui.theme.KMONEMotorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KMONEMotorTheme {
                val debugState by RadarDebugStore.state.collectAsStateWithLifecycle()
                RadarDebugScreen(debugState = debugState)
            }
        }
    }
}

@Composable
private fun RadarDebugScreen(debugState: RadarDebugState) {
    val context = LocalContext.current
    val piuController = PiuOverlayRuntime.get(context)

    LaunchedEffect(Unit) {
        val permissionGranted = Settings.canDrawOverlays(context)
        if (permissionGranted) {
            RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_OVERLAY_PERMISSION_GRANTED")
        }
        RadarDebugStore.updatePiuOverlayState(
            permissionGranted = permissionGranted,
            showing = piuController.isShowing(),
            x = debugState.piuLastX
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "KM Radar Core V2 Debug",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Abrir acessibilidade")
            }
            DebugCard(
                title = "PIU Overlay",
                lines = listOf(
                    "ganhoAtual=R$ 0,00",
                    "overlayPermissionGranted=${debugState.piuOverlayPermissionGranted}",
                    "showing=${debugState.piuOverlayShowing}",
                    "x=${debugState.piuLastX}",
                    "lastAnalyzeClickedAtMs=${debugState.piuLastAnalyzeClickedAtMs}",
                    "lastError=${debugState.piuLastError ?: "nenhum"}"
                ),
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_OVERLAY_PERMISSION_REQUESTED")
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        ) {
                            Text("Permissao overlay")
                        }
                        Button(
                            onClick = {
                                val shown = piuController.show()
                                RadarDebugStore.updatePiuOverlayState(
                                    permissionGranted = Settings.canDrawOverlays(context),
                                    showing = shown,
                                    x = debugState.piuLastX
                                )
                            }
                        ) {
                            Text("Mostrar PIU")
                        }
                        Button(
                            onClick = {
                                piuController.hide()
                                RadarDebugStore.updatePiuOverlayState(
                                    permissionGranted = Settings.canDrawOverlays(context),
                                    showing = false,
                                    x = debugState.piuLastX
                                )
                            }
                        ) {
                            Text("Ocultar PIU")
                        }
                    }
                }
            )
            DebugCard(
                title = "Manual Debug Interno",
                lines = listOf(
                    "Analisar dentro do app e apenas teste interno.",
                    "Para rua, use o PIU overlay."
                ),
                action = {
                    Button(
                        onClick = {
                            val clickedAtMs = System.currentTimeMillis()
                            val requested = ManualAnalysisRequestBus.requestAnalysis(
                                source = "in_app_debug",
                                clickedAtMs = clickedAtMs
                            )
                            if (!requested) {
                                RadarLogger.w(
                                    "KM_V2_OVERLAY",
                                    "KM_V2_PIU_ERROR",
                                    "message" to "manual_analysis_listener_not_registered"
                                )
                            }
                        }
                    ) {
                        Text("Analisar no app")
                    }
                }
            )
            DebugCard(
                title = "Servico",
                lines = listOf(
                    "ativo=${debugState.serviceActive}"
                )
            )
            DebugCard(
                title = "Window Stack",
                lines = listOf(
                    "topDominantWindowPackage=${debugState.topDominantWindowPackage}",
                    "topFloatingWindowPackage=${debugState.topFloatingWindowPackage}",
                    "floatingCoverage=${debugState.floatingCoverage}",
                    "floatingKind=${debugState.floatingKind}"
                )
            )
            DebugCard(
                title = "Operational State",
                lines = listOf(
                    "uberState=${debugState.uberOperationalState}",
                    "ninetyNineState=${debugState.ninetyNineOperationalState}",
                    "lastUpdate=${debugState.lastOperationalStateUpdate ?: "nenhum"}"
                )
            )
            DebugCard(
                title = "Node Tree",
                lines = listOf(
                    "package=${debugState.nodeTreePackage}",
                    "nodeCount=${debugState.nodeCount}",
                    "visibleTextNodeCount=${debugState.visibleTextNodeCount}",
                    "knownStateTexts=${debugState.knownStateTexts.joinToString(",")}"
                )
            )
            DebugCard(
                title = "Ultimo Signal",
                lines = listOf(debugState.lastSignalSummary ?: "nenhum")
            )
            DebugCard(
                title = "Ultimo CaptureRequest",
                lines = listOf(debugState.lastCaptureRequestSummary ?: "nenhum")
            )
            DebugCard(
                title = "Ultima Observation",
                lines = listOf(debugState.lastObservationSummary ?: "nenhum")
            )
            DebugCard(
                title = "Latency",
                lines = listOf(
                    "eventToObservationMs=${debugState.lastEventToObservationMs}",
                    "screenshotDurationMs=${debugState.lastScreenshotDurationMs}",
                    "captureStatus=${debugState.lastCaptureStatus}",
                    "bottleneck=${debugState.lastLatencyBottleneck}"
                )
            )
            DebugCard(
                title = "Vision",
                lines = listOf(
                    "visionDurationMs=${debugState.lastVisionDurationMs}",
                    "bestCropKind=${debugState.lastBestCropKind}",
                    "visualOfferLikeScore=${debugState.lastVisualOfferLikeScore}",
                    "acceptedForOcrFuture=${debugState.lastAcceptedForOcrFuture}",
                    "visualProbeReason=${debugState.lastVisualProbeReason}"
                )
            )
            DebugCard(
                title = "OCR",
                lines = listOf(
                    "ocrDurationMs=${debugState.lastOcrDurationMs}",
                    "ocrSuccess=${debugState.lastOcrSuccess}",
                    "ocrCropKind=${debugState.lastOcrCropKind}",
                    "ocrPolicyReason=${debugState.lastOcrPolicyReason}",
                    "ocrRawTextPreview=${debugState.lastOcrRawTextPreview}"
                )
            )
            DebugCard(
                title = "Fingerprint",
                lines = listOf(
                    "fingerprintKind=${debugState.lastFingerprintKind}",
                    "platformHint=${debugState.lastFingerprintPlatformHint}",
                    "offerLikeScore=${debugState.lastFingerprintOfferLikeScore}",
                    "nonOfferScore=${debugState.lastFingerprintNonOfferScore}",
                    "pricePreview=${debugState.lastFingerprintPricePreview}",
                    "reason=${debugState.lastFingerprintReason}"
                )
            )
            DebugCard(
                title = "Dedupe",
                lines = listOf(
                    "result=${debugState.lastDedupeResult}",
                    "clusterId=${debugState.lastDedupeClusterId}",
                    "quality=${debugState.lastDedupeQuality}",
                    "reason=${debugState.lastDedupeReason}",
                    "isBest=${debugState.lastDedupeIsBest}",
                    "activeClusters=${debugState.activeOfferClusterCount}",
                    "bestPreview=${debugState.lastBestOfferPreview}",
                    "bestMainPrice=${debugState.lastBestOfferMainPrice}",
                    "bestPlatform=${debugState.lastBestOfferPlatform}"
                )
            )
            DebugCard(
                title = "Parser",
                lines = listOf(
                    "status=${debugState.lastParserResultStatus}",
                    "clusterId=${debugState.lastParsedClusterId}",
                    "platform=${debugState.lastParsedPlatform}",
                    "product=${debugState.lastParsedProduct}",
                    "price=${debugState.lastParsedPrice}",
                    "valuePerKm=${debugState.lastParsedValuePerKm}",
                    "pickup=${debugState.lastParsedPickupTime} / ${debugState.lastParsedPickupDistance}",
                    "trip=${debugState.lastParsedTripTime} / ${debugState.lastParsedTripDistance}",
                    "confidence=${debugState.lastParsedConfidence}",
                    "warnings=${debugState.lastParserWarnings}",
                    "sanity=${debugState.lastParsedSanityStatus}",
                    "issues=${debugState.lastParsedSanityIssues}",
                    "blockEconomic=${debugState.lastParsedShouldBlockEconomicDecision}"
                )
            )
            DebugCard(
                title = "Economic Decision",
                lines = listOf(
                    "decision=${debugState.lastEconomicDecision}",
                    "clusterId=${debugState.lastEconomicDecisionClusterId}",
                    "score=${debugState.lastEconomicDecisionScore}",
                    "confidence=${debugState.lastEconomicDecisionConfidence}",
                    "grossPerTripKm=${debugState.lastEconomicGrossPerTripKm}",
                    "grossPerTotalKm=${debugState.lastEconomicGrossPerTotalKm}",
                    "totalDistanceKm=${debugState.lastEconomicTotalDistanceKm}",
                    "totalTimeMin=${debugState.lastEconomicTotalTimeMin}",
                    "reasons=${debugState.lastEconomicDecisionReasons}"
                )
            )
            DebugCard(
                title = "Offer Cycle",
                lines = listOf(
                    "kind=${debugState.lastOfferCycleKind}",
                    "cycleId=${debugState.lastOfferCycleId}",
                    "reason=${debugState.lastOfferCycleReason}",
                    "shouldPreferForOcr=${debugState.lastOfferCycleShouldPreferForOcr}",
                    "timeSincePreviousMs=${debugState.lastOfferCycleTimeSincePreviousMs}"
                )
            )
            DebugCard(
                title = "Manual Analysis",
                lines = listOf(
                    "epoch=${debugState.lastManualAnalysisEpoch}",
                    "status=${debugState.lastManualAnalysisStatus}",
                    "durationMs=${debugState.lastManualAnalysisDurationMs}",
                    "fingerprintKind=${debugState.lastManualFingerprintKind}",
                    "fingerprintPreview=${debugState.lastManualFingerprintPreview}",
                    "acceptedAtMs=${debugState.lastManualClickAcceptedAtMs}",
                    "rejectedReason=${debugState.lastManualClickRejectedReason}",
                    "running=${debugState.manualAnalysisRunning}",
                    "cooldownRemainingMs=${debugState.manualCooldownRemainingMs}",
                    "secondaryOcrStatus=${debugState.lastManualSecondaryOcrStatus}",
                    "bitmapWarning=${debugState.lastManualBitmapWarning}"
                )
            )
        }
    }
}

@Composable
private fun DebugCard(title: String, lines: List<String>, action: (@Composable () -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            action?.invoke()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RadarDebugScreenPreview() {
    KMONEMotorTheme {
        RadarDebugScreen(
            debugState = RadarDebugState(
                serviceActive = true,
                topDominantWindowPackage = "com.app99.driver",
                topFloatingWindowPackage = "com.ubercab.driver",
                floatingCoverage = 0.012,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                nodeTreePackage = "com.app99.driver",
                nodeCount = 87,
                visibleTextNodeCount = 12,
                knownStateTexts = listOf("buscando corridas"),
                uberOperationalState = OperationalAppState.UBER_BACKGROUND_ONLINE_HINT,
                ninetyNineOperationalState = OperationalAppState.NINETY_NINE_FOREGROUND_ACTIVE_HINT,
                lastOperationalStateUpdate = "app=99 state=NINETY_NINE_FOREGROUND_ACTIVE_HINT",
                lastSignalSummary = "type=UBER_FLOATING_OVER_OTHER_APP dominant=com.app99.driver floating=com.ubercab.driver coverage=0.012 kind=FLOATING_BUBBLE nodePackage=com.app99.driver",
                lastCaptureRequestSummary = null,
                lastObservationSummary = "id=abc12345 request=req12345 trigger=UBER_FLOATING_OVER_99_DIAGNOSTIC size=1080x2400 eventToObservationMs=210 screenshotDurationMs=88 floatingKind=FLOATING_BUBBLE",
                lastEventToObservationMs = 210,
                lastScreenshotDurationMs = 88,
                lastCaptureStatus = "success",
                lastLatencyBottleneck = "screenshot",
                lastVisionDurationMs = 24,
                lastBestCropKind = "FLOATING_BOUNDS_EXPANDED",
                lastVisualOfferLikeScore = 8,
                lastAcceptedForOcrFuture = true,
                lastVisualProbeReason = "best_candidate_score",
                lastOcrDurationMs = 45,
                lastOcrSuccess = true,
                lastOcrCropKind = "LOWER_HALF",
                lastOcrPolicyReason = "ocr_allowed_new_offer_cycle",
                lastOcrRawTextPreview = "R$ 18,40 7 min 3,2 km",
                lastFingerprintKind = "OFFER_LIKE",
                lastFingerprintPlatformHint = "UBER",
                lastFingerprintOfferLikeScore = 14,
                lastFingerprintNonOfferScore = 0,
                lastFingerprintPricePreview = "11.55",
                lastFingerprintReason = "offer_like_positive_signals",
                lastDedupeResult = "SAME_OFFER_UPDATED",
                lastDedupeClusterId = "cluster_2",
                lastDedupeQuality = 28,
                lastDedupeReason = "same_price_distance_time",
                lastDedupeIsBest = true,
                activeOfferClusterCount = 2,
                lastBestOfferPreview = "R$ 18,40 7 min 3,2 km",
                lastBestOfferMainPrice = "18.4",
                lastBestOfferPlatform = "UBER",
                lastParserResultStatus = "parsed",
                lastParsedClusterId = "cluster_2",
                lastParsedPlatform = "UBER",
                lastParsedProduct = "UberX",
                lastParsedPrice = "18.4",
                lastParsedValuePerKm = "2.3",
                lastParsedPickupTime = "4.0 min",
                lastParsedPickupDistance = "1.8 km",
                lastParsedTripTime = "7.0 min",
                lastParsedTripDistance = "3.2 km",
                lastParsedConfidence = "0.86",
                lastParserWarnings = "nenhum",
                lastParsedSanityStatus = "VALID",
                lastParsedSanityIssues = "",
                lastParsedShouldBlockEconomicDecision = false,
                lastEconomicDecision = "WARNING",
                lastEconomicDecisionClusterId = "cluster_2",
                lastEconomicDecisionScore = 1,
                lastEconomicDecisionConfidence = "0.71",
                lastEconomicDecisionReasons = "ABOVE_MIN_TOTAL_KM,SHORT_TRIP",
                lastEconomicGrossPerTripKm = "5.75",
                lastEconomicGrossPerTotalKm = "1.84",
                lastEconomicTotalDistanceKm = "10.0",
                lastEconomicTotalTimeMin = "11.0",
                lastOfferCycleKind = "NEW_OFFER_CYCLE",
                lastOfferCycleId = "cycle-123",
                lastOfferCycleReason = "no_previous_cycle",
                lastOfferCycleShouldPreferForOcr = true,
                lastOfferCycleTimeSincePreviousMs = null,
                lastManualClickAcceptedAtMs = 123456789L,
                lastManualClickRejectedReason = "throttled",
                manualAnalysisRunning = false,
                manualCooldownRemainingMs = 0L,
                lastManualSecondaryOcrStatus = "secondary_completed",
                lastManualBitmapWarning = null,
                piuOverlayPermissionGranted = true,
                piuOverlayShowing = true,
                piuLastX = 120,
                piuLastAnalyzeClickedAtMs = 123456789L,
                piuLastError = null
            )
        )
    }
}
