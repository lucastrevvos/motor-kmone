package com.lucastrevvos.kmonemotor

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.OperationalAppState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
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
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Abrir acessibilidade")
            }
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
                title = "Offer Cycle",
                lines = listOf(
                    "kind=${debugState.lastOfferCycleKind}",
                    "cycleId=${debugState.lastOfferCycleId}",
                    "reason=${debugState.lastOfferCycleReason}",
                    "shouldPreferForOcr=${debugState.lastOfferCycleShouldPreferForOcr}",
                    "timeSincePreviousMs=${debugState.lastOfferCycleTimeSincePreviousMs}"
                )
            )
        }
    }
}

@Composable
private fun DebugCard(title: String, lines: List<String>) {
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
                lastOfferCycleKind = "NEW_OFFER_CYCLE",
                lastOfferCycleId = "cycle-123",
                lastOfferCycleReason = "no_previous_cycle",
                lastOfferCycleShouldPreferForOcr = true,
                lastOfferCycleTimeSincePreviousMs = null
            )
        )
    }
}
