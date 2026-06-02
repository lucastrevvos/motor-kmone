package com.lucastrevvos.kmonemotor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.OperationalAppState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.DebugExportResult
import com.lucastrevvos.kmonemotor.radar.debug.DebugLogExporter
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.seenoffers.DriverSettings
import com.lucastrevvos.kmonemotor.radar.seenoffers.FuelEntry
import com.lucastrevvos.kmonemotor.radar.seenoffers.HomeDailySummary
import com.lucastrevvos.kmonemotor.radar.seenoffers.HomeDailySummaryProvider
import com.lucastrevvos.kmonemotor.radar.seenoffers.HomeGoalSource
import com.lucastrevvos.kmonemotor.radar.seenoffers.RecordsPeriodFilter
import com.lucastrevvos.kmonemotor.radar.seenoffers.RecordsSummary
import com.lucastrevvos.kmonemotor.radar.seenoffers.RecordsSummaryProvider
import com.lucastrevvos.kmonemotor.radar.seenoffers.ResolvedRideEconomics
import com.lucastrevvos.kmonemotor.radar.seenoffers.RideEconomicsCalculator
import com.lucastrevvos.kmonemotor.radar.seenoffers.RidePlatform
import com.lucastrevvos.kmonemotor.radar.seenoffers.SavedRide
import com.lucastrevvos.kmonemotor.radar.seenoffers.SavedRideSource
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffer
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferRuntime
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferSanitizationRules
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferStatus
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferUiMapper
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferUiModel
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffersUiState
import com.lucastrevvos.kmonemotor.radar.seenoffers.TrackingRecord
import com.lucastrevvos.kmonemotor.radar.seenoffers.TrackingRecordFactory
import com.lucastrevvos.kmonemotor.radar.seenoffers.TrackingRecordSyncProcessor
import com.lucastrevvos.kmonemotor.radar.seenoffers.TrackingRecordType
import com.lucastrevvos.kmonemotor.radar.seenoffers.TrackingRecordUiMapper
import com.lucastrevvos.kmonemotor.radar.tracking.AndroidTrackingLocationClient
import com.lucastrevvos.kmonemotor.radar.tracking.TrackingDistanceCalculator
import com.lucastrevvos.kmonemotor.radar.tracking.TrackingDistanceState
import com.lucastrevvos.kmonemotor.radar.tracking.TrackingGpsStatus
import com.lucastrevvos.kmonemotor.radar.tracking.trackingGpsStatusLabel
import com.lucastrevvos.kmonemotor.ui.theme.KMONEMotorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KMONEMotorTheme(dynamicColor = false) {
                KmOneApp()
            }
        }
    }
}

private const val ENABLE_HOME_PERF_LOGS = false

private enum class AppTab(
    val label: String,
    val iconResId: Int
) {
    HOME("Inicio", R.drawable.ic_home),
    SEEN("Vistas", R.drawable.ic_eye),
    RIDES("Registros", R.drawable.ic_clipboard),
    CONFIG("Config.", R.drawable.ic_settings)
}

private enum class SeenFilter(
    val label: String
) {
    ALL("Todas")
}

private object KmOnePalette {
    val Background = Color(0xFF07111C)
    val BackgroundDeep = Color(0xFF02070D)
    val Card = Color(0xE60B1624)
    val CardAlt = Color(0xCC102033)
    val Neon = Color(0xFF00E676)
    val NeonSoft = Color(0xFF00B359)
    val ElectricBlue = Color(0xFF5AA8FF)
    val Line = Color(0x4700E676)
    val TextPrimary = Color(0xFFF4F8FF)
    val TextSecondary = Color(0xFFAAB7C4)
    val Positive = Color(0xFF00E676)
    val Attention = Color(0xFFF7C948)
    val Negative = Color(0xFFEF4444)
    val Neutral = Color(0xFF7D93A8)
}

private data class HomeUiState(
    val summary: HomeDailySummary = emptyHomeSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

internal enum class TrackingUiStatus {
    IDLE,
    RUNNING
}

internal enum class TrackingSaveType {
    DISPLACEMENT,
    PRIVATE_RIDE
}

private data class TrackingUiSession(
    val status: TrackingUiStatus = TrackingUiStatus.IDLE,
    val startedAtMs: Long? = null,
    val distanceKm: Double = 0.0,
    val pointCount: Int = 0,
    val gpsStatus: TrackingGpsStatus = TrackingGpsStatus.IDLE
)

private sealed class RecordsListItem {
    abstract val stableId: String
    abstract val timestampMs: Long

    data class RideItem(val ride: SavedRide) : RecordsListItem() {
        override val stableId: String = "ride:${ride.id}"
        override val timestampMs: Long = ride.acceptedAtMs
    }

    data class FuelItem(val fuelEntry: FuelEntry) : RecordsListItem() {
        override val stableId: String = "fuel:${fuelEntry.id}"
        override val timestampMs: Long = fuelEntry.createdAtMs
    }

    data class TrackingItem(val trackingRecord: TrackingRecord) : RecordsListItem() {
        override val stableId: String = "tracking:${trackingRecord.id}"
        override val timestampMs: Long = trackingRecord.endedAtMs
    }
}

@Composable
private fun KmOneApp(debugStateOverride: RadarDebugState? = null) {
    val context = LocalContext.current
    val module = remember { SeenOfferRuntime.get(context) }
    val trackingSyncProcessor = remember {
        TrackingRecordSyncProcessor(
            trackingRecordRepository = module.trackingRecordRepository,
            savedRideRepository = module.savedRideRepository
        )
    }
    val homeSummaryProvider = remember {
        HomeDailySummaryProvider(
            seenOfferRepository = module.seenOfferRepository,
            savedRideRepository = module.savedRideRepository,
            configuredDailyGoalProvider = { module.driverSettingsRepository.getSettings().dailyGoalBrl }
        )
    }
    val recordsSummaryProvider = remember {
        RecordsSummaryProvider(
            savedRideRepository = module.savedRideRepository,
            fuelEntriesProvider = { module.fuelEntryRepository.listFuelEntries(limit = 500) }
        )
    }
    val overlayController = remember { PiuOverlayRuntime.get(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var seenState by remember { mutableStateOf(SeenOffersUiState(isLoading = true)) }
    var savedRides by remember { mutableStateOf<List<SavedRide>>(emptyList()) }
    var fuelEntries by remember { mutableStateOf<List<FuelEntry>>(emptyList()) }
    var trackingRecords by remember { mutableStateOf<List<TrackingRecord>>(emptyList()) }
    var ridesLoading by remember { mutableStateOf(true) }
    var ridesError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf(HomeUiState(isLoading = true)) }
    var driverSettings by remember { mutableStateOf(DriverSettings()) }
    var debugExporting by remember { mutableStateOf(false) }
    var debugExportMessage by remember { mutableStateOf<String?>(null) }
    var showLaunchBrand by remember { mutableStateOf(true) }
    var didInitialRefresh by remember { mutableStateOf(false) }
    val liveHomeSummary = homeState.summary

    suspend fun reloadSeenOffers(): List<SeenOffer> {
        seenState = seenState.copy(isLoading = true, errorMessage = null)
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            withContext(Dispatchers.IO) {
                module.seenOfferRepository.listSeenOffers(limit = 500)
            }
        }.onSuccess { offers ->
            seenState = SeenOffersUiState(offers = offers, isLoading = false, errorMessage = null)
        }.onFailure { error ->
            seenState = SeenOffersUiState(isLoading = false, errorMessage = error.message ?: "Falha ao carregar ofertas")
        }.also {
            RadarLogger.i(
                "KM_V2_PERF",
                "KM_V2_PERF_RELOAD_SEEN_OFFERS_DURATION",
                "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
                "count" to seenState.offers.size,
                "success" to it.isSuccess
            )
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun reloadSavedRides(): List<SavedRide> {
        ridesLoading = true
        ridesError = null
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            withContext(Dispatchers.IO) {
                module.savedRideRepository.listSavedRides(limit = 500)
            }
        }.onSuccess { rides ->
            savedRides = rides
            ridesLoading = false
        }.onFailure { error ->
            savedRides = emptyList()
            ridesLoading = false
            ridesError = error.message ?: "Falha ao carregar registros"
        }.also {
            RadarLogger.i(
                "KM_V2_PERF",
                "KM_V2_PERF_RELOAD_SAVED_RIDES_DURATION",
                "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
                "count" to savedRides.size,
                "success" to it.isSuccess
            )
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun reloadFuelEntries(): List<FuelEntry> {
        return runCatching {
            withContext(Dispatchers.IO) {
                module.fuelEntryRepository.listFuelEntries(limit = 500)
            }
        }.onSuccess { entries ->
            fuelEntries = entries
        }.onFailure {
            fuelEntries = emptyList()
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun reloadTrackingRecords(): List<TrackingRecord> {
        return runCatching {
            module.trackingRecordRepository.list(limit = 500)
        }.onSuccess { records ->
            trackingRecords = records
        }.onFailure {
            trackingRecords = emptyList()
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun reloadDriverSettings(): DriverSettings {
        return withContext(Dispatchers.IO) {
            module.driverSettingsRepository.getSettings()
        }.also {
            driverSettings = it
        }
    }

    fun refreshAll(onComplete: ((seenOfferCount: Int, savedRideCount: Int) -> Unit)? = null) {
        coroutineScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            homeState = homeState.copy(isLoading = true, errorMessage = null)
            reloadDriverSettings()
            val offers = reloadSeenOffers()
            val rides = reloadSavedRides()
            reloadFuelEntries()
            val records = reloadTrackingRecords()
            val summary = withContext(Dispatchers.Default) {
                homeSummaryProvider.summarize(
                    seenOffers = offers,
                    savedRides = rides,
                    trackingRecords = records
                )
            }
            homeState = HomeUiState(
                summary = summary,
                isLoading = false,
                errorMessage = seenState.errorMessage ?: ridesError
            )
            RadarLogger.i(
                "KM_V2_PERF",
                "KM_V2_PERF_REFRESH_ALL_DURATION",
                "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
                "seenOfferCount" to offers.size,
                "savedRideCount" to rides.size
            )
            onComplete?.invoke(offers.size, rides.size)
        }
    }

    LaunchedEffect(Unit) {
        val permissionGranted = Settings.canDrawOverlays(context)
        RadarDebugStore.updatePiuOverlayState(
            permissionGranted = permissionGranted,
            showing = overlayController.isShowing(),
            x = RadarDebugStore.state.value.piuLastX
        )
        refreshAll()
        didInitialRefresh = true
    }

    LaunchedEffect(selectedTab) {
        if (didInitialRefresh && selectedTab == AppTab.HOME) {
            refreshAll()
        }
    }

    LaunchedEffect(Unit) {
        delay(1000L)
        showLaunchBrand = false
    }

    val appBackgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                KmOnePalette.BackgroundDeep,
                KmOnePalette.Background,
                Color(0xFF06251A),
                KmOnePalette.BackgroundDeep
            )
        )
    }

    if (showLaunchBrand) {
        KmOneLaunchBrandScreen()
        return
    }

    LaunchedEffect(liveHomeSummary.earnedToday) {
        val earningsText = formatMoney(liveHomeSummary.earnedToday)
        overlayController.updateEarningsText(earningsText)
        RadarLogger.i(
            "KM_V2_OVERLAY",
            "KM_V2_PIU_EARNINGS_TEXT_UPDATED",
            "text" to earningsText,
            "earnedToday" to liveHomeSummary.earnedToday,
            "source" to "home_daily_summary"
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            KmOneBottomBar(
                selectedTab = selectedTab,
                onSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackgroundBrush)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.HOME -> {
                    val debugState by if (debugStateOverride == null) {
                        RadarDebugStore.state.collectAsStateWithLifecycle()
                    } else {
                        rememberUpdatedState(debugStateOverride)
                    }
                    HomeDashboardFinalTab(
                        debugState = debugState,
                        homeState = homeState,
                        resolvedSummary = liveHomeSummary,
                        seenOffers = seenState.offers,
                        savedRides = savedRides,
                        fuelEntries = fuelEntries,
                        onSaveTrackingRecord = { record ->
                            coroutineScope.launch {
                                val savedRecord = trackingSyncProcessor.saveNew(record)
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_RECORD_SAVED",
                                    "id" to savedRecord.id,
                                    "type" to savedRecord.type,
                                    "durationSeconds" to savedRecord.durationSeconds,
                                    "distanceKm" to savedRecord.distanceKm,
                                    "amount" to savedRecord.amount
                                )
                                refreshAll()
                            }
                        },
                        onToggleRadar = {
                            val currentVisible = debugState.piuOverlayShowing
                            val targetVisible = !currentVisible
                            RadarLogger.i(
                                "KM_V2_HOME",
                                "KM_V2_HOME_RADAR_TOGGLE_CLICKED",
                                "currentVisible" to currentVisible,
                                "targetVisible" to targetVisible
                            )
                            when {
                                !debugState.serviceActive -> {
                                    RadarLogger.w(
                                        "KM_V2_HOME",
                                        "KM_V2_HOME_RADAR_TOGGLE_BLOCKED",
                                        "reason" to "missing_accessibility"
                                    )
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                !Settings.canDrawOverlays(context) -> {
                                    RadarLogger.w(
                                        "KM_V2_HOME",
                                        "KM_V2_HOME_RADAR_TOGGLE_BLOCKED",
                                        "reason" to "missing_overlay_permission"
                                    )
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                currentVisible -> {
                                    overlayController.hide()
                                    RadarDebugStore.updatePiuOverlayState(
                                        permissionGranted = true,
                                        showing = false,
                                        x = debugState.piuLastX
                                    )
                                    RadarLogger.i(
                                        "KM_V2_HOME",
                                        "KM_V2_HOME_RADAR_VISIBILITY_CHANGED",
                                        "visible" to false,
                                        "source" to "home_analyze_offer_button"
                                    )
                                }
                                else -> {
                                    val shown = overlayController.show()
                                    RadarDebugStore.updatePiuOverlayState(
                                        permissionGranted = true,
                                        showing = shown,
                                        x = debugState.piuLastX
                                    )
                                    if (shown) {
                                        RadarLogger.i(
                                            "KM_V2_HOME",
                                            "KM_V2_HOME_RADAR_VISIBILITY_CHANGED",
                                            "visible" to true,
                                            "source" to "home_analyze_offer_button"
                                        )
                                    } else {
                                        RadarLogger.w(
                                            "KM_V2_HOME",
                                            "KM_V2_HOME_RADAR_TOGGLE_BLOCKED",
                                            "reason" to "service_not_ready"
                                        )
                                    }
                                }
                            }
                        },
                        onConfigureGoal = { selectedTab = AppTab.CONFIG },
                        onOpenSeen = { selectedTab = AppTab.SEEN },
                        onOpenRecords = { selectedTab = AppTab.RIDES },
                        onOpenConfig = { selectedTab = AppTab.CONFIG }
                    )
                }

                AppTab.SEEN -> SeenOffersTab(
                    state = seenState,
                    onRefresh = { onComplete -> refreshAll(onComplete) },
                    onSave = { offerId ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val savedRide = module.manualActions.acceptSeenOfferManually(offerId)
                                if (savedRide != null) {
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SEEN_OFFER_SAVED_MANUALLY_FROM_VIEWS",
                                        "seenOfferId" to offerId,
                                        "savedRideId" to savedRide.id
                                    )
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SAVED_RIDE_CREATED",
                                        "source" to "seen_offer_manual_accept",
                                        "price" to savedRide.price,
                                        "createdAt" to savedRide.acceptedAtMs,
                                        "savedRideId" to savedRide.id
                                    )
                                }
                            }
                            refreshAll()
                        }
                    },
                    onIgnore = { offerId ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.manualActions.ignoreSeenOffer(offerId)
                                RadarLogger.i(
                                    "KM_V2_SEEN",
                                    "KM_V2_SEEN_OFFER_IGNORED_FROM_VIEWS",
                                        "seenOfferId" to offerId
                                )
                            }
                            refreshAll()
                        }
                    },
                    onClearPending = { pendingOfferIds ->
                        coroutineScope.launch {
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_SEEN_OFFERS_CLEAR_PENDING_REQUESTED",
                                "count" to pendingOfferIds.size
                            )
                            withContext(Dispatchers.IO) {
                                pendingOfferIds.forEach { offerId ->
                                    module.manualActions.ignoreSeenOffer(offerId)
                                }
                            }
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_SEEN_OFFERS_CLEAR_PENDING_DONE",
                                "count" to pendingOfferIds.size
                            )
                            refreshAll()
                        }
                    }
                )

                AppTab.RIDES -> RecordsTab(
                    rides = savedRides,
                    fuelEntries = fuelEntries,
                    trackingRecords = trackingRecords,
                    isLoading = ridesLoading,
                    error = ridesError,
                    recordsSummaryProvider = recordsSummaryProvider,
                    onRefresh = { refreshAll() },
                    onCreateFuelEntry = { fuelEntry ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.fuelEntryRepository.saveFuelEntry(fuelEntry)
                            }
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_FUEL_ENTRY_CREATED",
                                "fuelEntryId" to fuelEntry.id,
                                "amountBrl" to fuelEntry.amountBrl,
                                "liters" to fuelEntry.liters,
                                "fuelType" to fuelEntry.fuelType
                            )
                            refreshAll()
                        }
                    },
                    onSaveEditedFuelEntry = { updatedEntry ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.fuelEntryRepository.updateFuelEntry(updatedEntry)
                            }
                            refreshAll()
                        }
                    },
                    onSaveEditedRide = { updatedRide ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                if (savedRides.any { it.id == updatedRide.id }) {
                                    module.savedRideRepository.updateRide(updatedRide)
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SAVED_RIDE_EDIT_SAVED",
                                        "savedRideId" to updatedRide.id,
                                        "price" to updatedRide.price,
                                        "totalDistanceKm" to updatedRide.totalDistanceKm,
                                        "valuePerKm" to updatedRide.valuePerKm
                                    )
                                } else {
                                    module.savedRideRepository.saveRide(updatedRide)
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_MANUAL_RIDE_CREATED",
                                        "savedRideId" to updatedRide.id,
                                        "price" to updatedRide.price,
                                        "valuePerKm" to updatedRide.valuePerKm
                                    )
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SAVED_RIDE_CREATED",
                                        "source" to "manual_entry",
                                        "price" to updatedRide.price,
                                        "createdAt" to updatedRide.acceptedAtMs,
                                        "savedRideId" to updatedRide.id
                                    )
                                }
                            }
                            refreshAll()
                        }
                    },
                    onDeleteRide = { savedRideId ->
                        coroutineScope.launch {
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_SAVED_RIDE_DELETE_REQUESTED",
                                "savedRideId" to savedRideId
                            )
                            val deleted = withContext(Dispatchers.IO) {
                                module.savedRideRepository.deleteRide(savedRideId)
                            }
                            if (deleted) {
                                RadarLogger.i(
                                    "KM_V2_SEEN",
                                    "KM_V2_SAVED_RIDE_DELETED",
                                    "savedRideId" to savedRideId
                                )
                            }
                            refreshAll()
                        }
                    },
                    onDeleteFuelEntry = { fuelEntryId ->
                        coroutineScope.launch {
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_FUEL_ENTRY_DELETE_REQUESTED",
                                "fuelEntryId" to fuelEntryId
                            )
                            val deleted = withContext(Dispatchers.IO) {
                                module.fuelEntryRepository.deleteFuelEntry(fuelEntryId)
                            }
                            if (deleted) {
                                RadarLogger.i(
                                    "KM_V2_SEEN",
                                    "KM_V2_FUEL_ENTRY_DELETED",
                                    "fuelEntryId" to fuelEntryId
                                )
                            }
                            refreshAll()
                        }
                    },
                    onSaveEditedTrackingRecord = { updatedRecord ->
                        coroutineScope.launch {
                            RadarLogger.i(
                                "KM_V2_TRACKING",
                                "KM_V2_TRACKING_RECORD_UPDATED",
                                "id" to updatedRecord.id,
                                "type" to updatedRecord.type,
                                "distanceKm" to updatedRecord.distanceKm,
                                "amount" to updatedRecord.amount
                            )
                            trackingSyncProcessor.update(updatedRecord)
                            refreshAll()
                        }
                    },
                    onDeleteTrackingRecord = { trackingRecord ->
                        coroutineScope.launch {
                            trackingSyncProcessor.delete(trackingRecord)
                            refreshAll()
                        }
                    }
                )

                AppTab.CONFIG -> {
                    val debugState by if (debugStateOverride == null) {
                        RadarDebugStore.state.collectAsStateWithLifecycle()
                    } else {
                        rememberUpdatedState(debugStateOverride)
                    }
                    ConfigTab(
                        debugState = debugState,
                        settings = driverSettings,
                        isExportingLogs = debugExporting,
                        exportMessage = debugExportMessage,
                        onSaveDailyGoal = { dailyGoal ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    module.driverSettingsRepository.updateDailyGoal(dailyGoal)
                                }
                                RadarLogger.i(
                                    "KM_V2_SEEN",
                                    "KM_V2_DRIVER_SETTINGS_DAILY_GOAL_SAVED",
                                    "dailyGoalBrl" to dailyGoal
                                )
                                refreshAll()
                            }
                        },
                        onExportDebugLogs = {
                        coroutineScope.launch {
                            debugExporting = true
                            debugExportMessage = null
                            val result = withContext(Dispatchers.IO) {
                                DebugLogExporter(context).export()
                            }
                            debugExporting = false
                            when (result) {
                                is DebugExportResult.Success -> {
                                    debugExportMessage = "Pacote gerado com ${result.filesCount} arquivos."
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, result.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Exportar logs de debug").apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }.onFailure { error ->
                                        debugExportMessage = "Pacote salvo em ${result.file.absolutePath}"
                                        RadarLogger.w(
                                            "KM_V2_DEBUG",
                                            "KM_V2_DEBUG_EXPORT_FAILED",
                                            "reason" to "share_sheet_failed",
                                            "error" to error.message
                                        )
                                    }
                                }
                                is DebugExportResult.Failure -> {
                                    debugExportMessage = result.reason
                                }
                            }
                        }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    debugState: RadarDebugState,
    homeState: HomeUiState,
    onOpenAccessibility: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onShowPiu: () -> Unit,
    onHidePiu: () -> Unit,
    onManualAnalyze: () -> Unit,
    onConfigureGoal: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DailyGoalCard(
                summary = homeState.summary,
                isLoading = homeState.isLoading,
                errorMessage = homeState.errorMessage,
                onConfigureGoal = onConfigureGoal
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Corridas",
                    value = homeState.summary.acceptedRidesCount.toString(),
                    accent = KmOnePalette.Positive
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Km rodados",
                    value = homeState.summary.totalKmToday?.let(::formatKmCompact) ?: "—",
                    accent = KmOnePalette.Neon
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Km rodados",
                    value = homeState.summary.totalKmToday?.let(::formatKmCompact) ?: "—",
                    accent = KmOnePalette.Neon
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Media",
                    value = homeState.summary.averageValuePerKm?.let(::formatPerKm) ?: "—",
                    accent = KmOnePalette.Attention
                )
            }
        }
        if (homeState.summary.bestRideValuePerKm != null || homeState.summary.bestRidePrice != null) {
            item {
                BestRideCard(summary = homeState.summary)
            }
        }
        item {
            CockpitCard(title = "Status operacional", accent = KmOnePalette.Line) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("KM One", if (debugState.serviceActive) "Ativo" else "Inativo")
                    DetailLine("Overlay", if (debugState.piuOverlayShowing) "Visivel" else "Oculto")
                    DetailLine("Permissao", if (debugState.piuOverlayPermissionGranted) "OK" else "Pendente")
                }
            }
        }
        item {
            CockpitCard(title = "Atalhos", accent = Color(0x4438FF97)) {
                ActionGrid(
                    actions = listOf(
                        QuickAction("Acessibilidade", onOpenAccessibility),
                        QuickAction("Permissao overlay", onRequestOverlayPermission),
                        QuickAction("Mostrar PIU", onShowPiu),
                        QuickAction("Ocultar PIU", onHidePiu),
                        QuickAction("Analisar agora", onManualAnalyze)
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeDashboardTab(
    debugState: RadarDebugState,
    homeState: HomeUiState,
    onOpenAccessibility: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onShowPiu: () -> Unit,
    onHidePiu: () -> Unit,
    onManualAnalyze: () -> Unit,
    onConfigureGoal: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DailyGoalCard(
                summary = homeState.summary,
                isLoading = homeState.isLoading,
                errorMessage = homeState.errorMessage,
                onConfigureGoal = onConfigureGoal
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Corridas",
                    value = homeState.summary.acceptedRidesCount.toString(),
                    accent = KmOnePalette.Positive
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Km rodados",
                    value = homeState.summary.totalKmToday?.let(::formatKmCompact) ?: "—",
                    accent = KmOnePalette.Neon
                )
            }
        }
        item {
            MetricPanel(
                label = "Media",
                value = homeState.summary.averageValuePerKm?.let(::formatPerKm) ?: "—",
                accent = KmOnePalette.Attention
            )
        }
        item {
            CockpitCard(title = "Atalhos", accent = Color(0x4438FF97)) {
                ActionGrid(
                    actions = listOf(
                        QuickAction("Acessibilidade", onOpenAccessibility),
                        QuickAction("Permissao overlay", onRequestOverlayPermission),
                        QuickAction("Mostrar painel", onShowPiu),
                        QuickAction("Ocultar painel", onHidePiu)
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeDashboardFinalTab(
    debugState: RadarDebugState,
    homeState: HomeUiState,
    resolvedSummary: HomeDailySummary,
    seenOffers: List<SeenOffer>,
    savedRides: List<SavedRide>,
    fuelEntries: List<FuelEntry>,
    onSaveTrackingRecord: (TrackingRecord) -> Unit,
    onToggleRadar: () -> Unit,
    onConfigureGoal: () -> Unit,
    onOpenSeen: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val summary = resolvedSummary
    val todayFuelSpend = remember(fuelEntries) { fuelEntries.sumFuelSpentToday() }
    val todayFuelCount = remember(fuelEntries) { fuelEntries.countTodayEntries() }
    val yesterdayDelta = remember(savedRides, summary.earnedToday) {
        savedRides.dayOverDayEarnedDelta(summary.earnedToday)
    }
    val visibleSeenOffers = remember(seenOffers) { visibleHomeSeenOffers(seenOffers) }
    val recentActivity = remember(visibleSeenOffers, savedRides) {
        buildHomeRecentActivity(visibleSeenOffers, savedRides)
    }

    LaunchedEffect(Unit) {
        if (ENABLE_HOME_PERF_LOGS) {
            RadarLogger.i(
                "KM_V2_HOME",
                "KM_V2_HOME_PERFORMANCE_CHECK",
                "reason" to "home_composed"
            )
        }
    }
    LaunchedEffect(summary.earnedToday, summary.dailyGoal, summary.progressFraction, savedRides) {
        val zoneId = ZoneId.systemDefault()
        val resolvedAtMs = System.currentTimeMillis()
        val day = Instant.ofEpochMilli(resolvedAtMs).atZone(zoneId).toLocalDate()
        val startOfDay = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        val todayRideCount = savedRides.count {
            Instant.ofEpochMilli(it.acceptedAtMs).atZone(zoneId).toLocalDate() == day
        }
        RadarLogger.i(
            "KM_V2_HOME",
            "KM_V2_HOME_DAILY_TOTAL_RESOLVED",
            "source" to "saved_rides_today",
            "savedRideCount" to savedRides.size,
            "todayRideCount" to todayRideCount,
            "grossToday" to summary.earnedToday,
            "dailyGoal" to summary.dailyGoal,
            "progress" to summary.progressFraction,
            "timezone" to zoneId.id,
            "startOfDay" to startOfDay,
            "endOfDay" to endOfDay
        )
    }
    LaunchedEffect(visibleSeenOffers, recentActivity) {
        RadarLogger.i(
            "KM_V2_HOME",
            "KM_V2_HOME_LAST_SEEN_OFFER_RESOLVED",
            "source" to "seen_offers_visible_queue",
            "seenOfferCount" to visibleSeenOffers.size,
            "lastSeenOfferId" to visibleSeenOffers.maxByOrNull { it.createdAtMs }?.id,
            "empty" to visibleSeenOffers.isEmpty()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeDashboardBackgroundDecor()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeBrandHeader(
                    isServiceActive = debugState.serviceActive,
                    seenOffersCount = summary.seenOffersCount,
                    onOpenConfig = onOpenConfig
                )
            }
            item {
                HomeHeroCard(
                    summary = summary,
                    isLoading = homeState.isLoading,
                    errorMessage = homeState.errorMessage,
                    yesterdayDelta = yesterdayDelta,
                    isRadarVisible = debugState.piuOverlayShowing,
                    onAnalyze = onToggleRadar,
                    onConfigureGoal = onConfigureGoal
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "R$/km medio",
                        value = summary.averageValuePerKm?.let(::formatPerKm) ?: "--",
                        detail = summary.totalKmToday?.let { "${formatKmCompact(it)} hoje" } ?: "Aguardando corridas",
                        accent = KmOnePalette.Attention,
                        iconResId = R.drawable.ic_goal_remaining
                    )
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Corridas",
                        value = summary.acceptedRidesCount.toString(),
                        detail = "${summary.seenOffersCount} vistas hoje",
                        accent = KmOnePalette.Positive,
                        iconResId = R.drawable.ic_clipboard
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Abastecido",
                        value = if (todayFuelSpend > 0.0) formatMoney(todayFuelSpend) else "--",
                        detail = if (todayFuelCount > 0) "$todayFuelCount abastecimento(s)" else "Nenhum abastecimento hoje",
                        accent = KmOnePalette.ElectricBlue,
                        iconResId = R.drawable.ic_settings
                    )
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Lucro liquido",
                        value = formatMoney(summary.earnedToday - todayFuelSpend),
                        detail = profitShareText(summary.earnedToday, todayFuelSpend),
                        accent = if (summary.earnedToday - todayFuelSpend >= 0.0) KmOnePalette.Neon else KmOnePalette.Negative,
                        iconResId = R.drawable.ic_today_earnings
                    )
                }
            }
            item {
                TrackingLiveCard(onSaveTrackingRecord = onSaveTrackingRecord)
            }
            item {
                HomeRecentActivityCard(
                    recentActivity = recentActivity,
                    onOpenSeen = onOpenSeen,
                    onOpenRecords = onOpenRecords
                )
            }
            item {
                HomeShortcutGrid(
                    onOpenRecords = onOpenRecords,
                    onOpenSeen = onOpenSeen
                )
            }
            item {
                HomeInsightCard(
                    summary = summary,
                    todayFuelSpend = todayFuelSpend
                )
            }
        }
    }
}

@Composable
private fun KmOneLaunchBrandScreen() {
    val launchBackgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                KmOnePalette.BackgroundDeep,
                KmOnePalette.Background,
                Color(0xFF0C1E17)
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(launchBackgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.kmone_splash_brand),
            contentDescription = "KM One",
            modifier = Modifier.fillMaxHeight(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun HomeDashboardBackgroundDecor() {
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF02070D),
                Color(0xFF07111C),
                Color(0xFF06251A),
                Color(0xFF02070D)
            )
        )
    }
    val radarGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(Color(0x3300E676), Color.Transparent)
        )
    }
    val sweepBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color(0x1400E676), Color.Transparent)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(radarGlowBrush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(sweepBrush)
        )
    }
}

@Composable
private fun HomeBrandHeader(
    isServiceActive: Boolean,
    seenOffersCount: Int,
    onOpenConfig: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(id = R.drawable.kmone_logo_full),
                contentDescription = "Marca KM One",
                modifier = Modifier.height(42.dp),
                contentScale = ContentScale.Fit
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeStatusPill(
                    text = if (isServiceActive) "KM One ativo" else "Radar em espera",
                    accent = if (isServiceActive) KmOnePalette.Neon else KmOnePalette.Attention
                )
                HomeStatusPill(
                    text = "$seenOffersCount vistas hoje",
                    accent = KmOnePalette.ElectricBlue
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x22102033))
                .border(1.dp, KmOnePalette.Line, CircleShape)
                .clickable(onClick = onOpenConfig),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "Configuracoes",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(KmOnePalette.TextPrimary)
            )
        }
    }
}

@Composable
private fun HomeStatusPill(
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x1A102033),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HomeHeroCard(
    summary: HomeDailySummary,
    isLoading: Boolean,
    errorMessage: String?,
    yesterdayDelta: Double?,
    isRadarVisible: Boolean,
    onAnalyze: () -> Unit,
    onConfigureGoal: () -> Unit
) {
    val heroGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(Color(0x3300E676), Color.Transparent)
        )
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xD90B1624),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4700E676))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(brush = heroGlowBrush)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Total do dia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KmOnePalette.TextSecondary
                        )
                        Text(
                            text = formatMoney(summary.earnedToday),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = KmOnePalette.TextPrimary
                        )
                        Text(
                            text = yesterdayDeltaLabel(yesterdayDelta),
                            style = MaterialTheme.typography.bodySmall,
                            color = if ((yesterdayDelta ?: 0.0) >= 0.0) KmOnePalette.Neon else KmOnePalette.Negative
                        )
                    }
                    HomeStatusPill(
                        text = if (summary.isGoalReached) "Meta batida" else "${summary.progressPercent ?: 0}% da meta",
                        accent = if (summary.isGoalReached) KmOnePalette.Positive else KmOnePalette.ElectricBlue
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Meta diaria",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KmOnePalette.TextSecondary
                        )
                        Text(
                            text = summary.dailyGoal?.let(::formatMoney) ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KmOnePalette.TextPrimary
                        )
                    }
                    GoalProgressBar(progress = summary.progressFraction)
                    Text(
                        text = when {
                            errorMessage != null -> errorMessage
                            isLoading -> "Atualizando painel do dia..."
                            summary.goalSource == HomeGoalSource.MISSING -> "Defina uma meta para acompanhar seu progresso."
                            summary.isGoalReached -> "Voce passou da meta e segue acelerando acima do alvo."
                            else -> "Faltam ${formatMoney(summary.remainingToGoal)} para bater a meta."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onAnalyze,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KmOnePalette.Neon,
                            contentColor = KmOnePalette.BackgroundDeep
                        )
                    ) {
                        Text(homeRadarActionLabel(isRadarVisible), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onConfigureGoal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
                    ) {
                        Text("Meta diaria")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    detail: String,
    accent: Color,
    iconResId: Int
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xD9102033),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(accent)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
        }
    }
}

@Composable
private fun HomeRecentActivityCard(
    recentActivity: HomeRecentActivity?,
    onOpenSeen: () -> Unit,
    onOpenRecords: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xD90B1624),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2E5AA8FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ultima oferta ou corrida",
                style = MaterialTheme.typography.titleMedium,
                color = KmOnePalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (recentActivity == null) {
                Text(
                    text = "Nenhuma oferta vista ainda. Ative o radar e aguarde novas corridas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KmOnePalette.TextSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeStatusPill(recentActivity.platformLabel, recentActivity.platformAccent)
                            HomeStatusPill(recentActivity.kindLabel, KmOnePalette.ElectricBlue)
                        }
                        Text(
                            text = recentActivity.priceLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = KmOnePalette.TextPrimary
                        )
                        Text(
                            text = recentActivity.valuePerKmLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KmOnePalette.Neon
                        )
                    }
                    Text(
                        text = recentActivity.timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                }
                DetailLine("Origem", recentActivity.originLabel)
                DetailLine("Destino", recentActivity.destinationLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeStatusPill(recentActivity.tripTimeLabel, KmOnePalette.Attention)
                    HomeStatusPill(recentActivity.tripDistanceLabel, KmOnePalette.Neon)
                    HomeStatusPill(recentActivity.totalDistanceLabel, KmOnePalette.ElectricBlue)
                }
                TextButton(onClick = if (recentActivity.opensSeen) onOpenSeen else onOpenRecords) {
                    Text(
                        text = if (recentActivity.opensSeen) "Ver vistas >" else "Ver registros >",
                        color = KmOnePalette.Neon
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeShortcutGrid(
    onOpenRecords: () -> Unit,
    onOpenSeen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Atalhos principais",
            style = MaterialTheme.typography.titleMedium,
            color = KmOnePalette.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeShortcutCard(
                modifier = Modifier.weight(1f),
                title = "Corrida manual",
                subtitle = "Registrar corrida",
                iconResId = R.drawable.ic_clipboard,
                accent = KmOnePalette.Positive,
                onClick = onOpenRecords
            )
            HomeShortcutCard(
                modifier = Modifier.weight(1f),
                title = "Abastecimento",
                subtitle = "Registrar gasto",
                iconResId = R.drawable.ic_settings,
                accent = KmOnePalette.ElectricBlue,
                onClick = onOpenRecords
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeShortcutCard(
                modifier = Modifier.weight(1f),
                title = "Vistas",
                subtitle = "Ver ofertas",
                iconResId = R.drawable.ic_eye,
                accent = KmOnePalette.Neon,
                emphasized = true,
                onClick = onOpenSeen
            )
            HomeShortcutCard(
                modifier = Modifier.weight(1f),
                title = "Registros",
                subtitle = "Historico completo",
                iconResId = R.drawable.ic_home,
                accent = KmOnePalette.Attention,
                onClick = onOpenRecords
            )
        }
    }
}

@Composable
private fun HomeShortcutCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    iconResId: Int,
    accent: Color,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (emphasized) Color(0xE3122531) else Color(0xD9102033),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = if (emphasized) 0.36f else 0.22f))
    ) {
        Box {
            if (emphasized) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x2600E676), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = title,
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(accent)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KmOnePalette.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HomeInsightCard(
    summary: HomeDailySummary,
    todayFuelSpend: Double
) {
    val message = when {
        summary.averageValuePerKm != null && summary.averageValuePerKm >= 2.0 ->
            "Seu R$/km medio esta acima da meta de refer\u00EAncia. Mantenha esse ritmo nas proximas corridas."
        todayFuelSpend > 0.0 && summary.earnedToday > todayFuelSpend ->
            "Seu lucro liquido segue positivo mesmo com abastecimento hoje. Vale priorizar corridas com busca curta."
        else ->
            "Acompanhe seu R$/km medio para evitar corridas abaixo da sua meta do dia."
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xD9102033),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33F7C948))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AF7C948))
                        .border(1.dp, Color(0x44F7C948), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = KmOnePalette.Attention,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Dica para voce",
                        style = MaterialTheme.typography.titleMedium,
                        color = KmOnePalette.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Leitura rapida do seu painel de hoje",
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = KmOnePalette.TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeenOffersTab(
    state: SeenOffersUiState,
    onRefresh: (((Int, Int) -> Unit)?) -> Unit,
    onSave: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onClearPending: (List<String>) -> Unit
) {
    var pullRefreshing by remember { mutableStateOf(false) }
    val filteredOffers = remember(state.offers) {
        state.offers.filter { offer ->
            offer.status == SeenOfferStatus.SEEN &&
                !SeenOfferSanitizationRules.isSuspiciousForPendingQueue(offer)
        }
    }
    val uiMapper = remember { SeenOfferUiMapper() }
    val uiModels = remember(filteredOffers) { filteredOffers.map(uiMapper::map) }
    val offerById = remember(filteredOffers) { filteredOffers.associateBy { it.id } }
    var expandedSeenOfferId by remember(filteredOffers) { mutableStateOf<String?>(null) }
    val isRefreshing = pullRefreshing || (state.isLoading && state.offers.isNotEmpty())

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (pullRefreshing) return@PullToRefreshBox
            pullRefreshing = true
            RadarLogger.i("KM_V2_SEEN", "KM_V2_SEEN_OFFERS_PULL_REFRESH_STARTED")
            onRefresh { seenOfferCount, savedRideCount ->
                pullRefreshing = false
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SEEN_OFFERS_PULL_REFRESH_FINISHED",
                    "seenOfferCount" to seenOfferCount,
                    "savedRideCount" to savedRideCount
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSection(
                title = "Ofertas vistas",
                subtitle = "Decida o que fazer com as ofertas pendentes."
            )
            when {
                state.isLoading && state.offers.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KmOnePalette.Neon)
                }

                state.errorMessage != null -> EmptyState(
                    title = "Nao foi possivel carregar",
                    message = state.errorMessage,
                    actionLabel = "Tentar novamente",
                    onAction = { onRefresh(null) }
                )

                filteredOffers.isEmpty() -> EmptyState(
                    title = "Nenhuma oferta pendente",
                    message = "As proximas ofertas detectadas pelo KM One aparecerao aqui.",
                    actionLabel = "Atualizar",
                    onAction = { onRefresh(null) }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SeenOffersHeaderActions(
                            pendingCount = filteredOffers.size,
                            onClearPending = {
                                expandedSeenOfferId = null
                                onClearPending(filteredOffers.map { it.id })
                            }
                        )
                    }
                    items(uiModels, key = { it.id }) { uiModel ->
                        val offer = offerById[uiModel.id] ?: return@items
                        SeenOfferCard(
                            offer = offer,
                            uiModel = uiModel,
                            expanded = expandedSeenOfferId == offer.id,
                            onToggleExpanded = {
                                expandedSeenOfferId = if (expandedSeenOfferId == offer.id) null else offer.id
                            },
                            onSave = {
                                expandedSeenOfferId = null
                                onSave(offer.id)
                            },
                            onIgnore = {
                                expandedSeenOfferId = null
                                onIgnore(offer.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(
    rides: List<SavedRide>,
    fuelEntries: List<FuelEntry>,
    trackingRecords: List<TrackingRecord>,
    isLoading: Boolean,
    error: String?,
    recordsSummaryProvider: RecordsSummaryProvider,
    onRefresh: () -> Unit,
    onCreateFuelEntry: (FuelEntry) -> Unit,
    onSaveEditedFuelEntry: (FuelEntry) -> Unit,
    onSaveEditedRide: (SavedRide) -> Unit,
    onDeleteRide: (String) -> Unit,
    onDeleteFuelEntry: (String) -> Unit,
    onSaveEditedTrackingRecord: (TrackingRecord) -> Unit,
    onDeleteTrackingRecord: (TrackingRecord) -> Unit
) {
    var period by remember { mutableStateOf(RecordsPeriodFilter.DAY) }
    var expandedItemId by remember(rides, fuelEntries, trackingRecords, period) { mutableStateOf<String?>(null) }
    var editingRide by remember { mutableStateOf<SavedRide?>(null) }
    var editingFuelEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var editingTrackingRecord by remember { mutableStateOf<TrackingRecord?>(null) }
    var deleteConfirmationRide by remember { mutableStateOf<SavedRide?>(null) }
    var deleteConfirmationFuelEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var deleteConfirmationTrackingRecord by remember { mutableStateOf<TrackingRecord?>(null) }
    var showFuelEntryDialog by remember { mutableStateOf(false) }
    var showManualRideDialog by remember { mutableStateOf(false) }
    val filteredRides = remember(rides, period) {
        recordsSummaryProvider.filterRides(rides, period)
    }
    val displayRides = remember(filteredRides) {
        recordsDisplayRides(filteredRides)
    }
    val filteredFuelEntries = remember(fuelEntries, period) {
        recordsSummaryProvider.filterFuelEntries(fuelEntries, period)
    }
    val filteredTrackingRecords = remember(trackingRecords, period) {
        filterTrackingRecords(trackingRecords, period)
    }
    val recordsItems = remember(displayRides, filteredFuelEntries, filteredTrackingRecords) {
        (
            displayRides.map(RecordsListItem::RideItem) +
                filteredFuelEntries.map(RecordsListItem::FuelItem) +
                filteredTrackingRecords.map(RecordsListItem::TrackingItem)
            )
            .sortedByDescending { it.timestampMs }
    }
    val summary = remember(rides, fuelEntries, period) {
        recordsSummaryProvider.summarize(
            rides = rides,
            fuelEntries = fuelEntries,
            period = period
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(
            title = "Registros",
            subtitle = "Resumo financeiro e operacional das corridas salvas."
        )
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KmOnePalette.ElectricBlue)
            }

            error != null -> EmptyState(
                title = "Nao foi possivel carregar",
                message = error,
                actionLabel = "Tentar novamente",
                onAction = onRefresh
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    RecordsPeriodFilterTabs(
                        selectedPeriod = period,
                        onPeriodSelected = {
                            period = it
                            expandedItemId = null
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_RECORDS_FILTER_CHANGED",
                                "period" to it.name
                            )
                        }
                    )
                }
                item {
                    RecordsSummaryCard(summary = summary)
                }
                item {
                    RecordsTopActions(
                        onAddFuel = {
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_FUEL_ENTRY_CREATE_OPENED"
                            )
                            showFuelEntryDialog = true
                        },
                        onManualRide = {
                            RadarLogger.i(
                                "KM_V2_SEEN",
                                "KM_V2_MANUAL_RIDE_CREATE_OPENED"
                            )
                            showManualRideDialog = true
                        }
                    )
                }
                if (recordsItems.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Nenhum registro neste periodo",
                            message = "Corridas, tracking e abastecimentos aparecerao aqui."
                        )
                    }
                } else {
                    items(recordsItems, key = { it.stableId }) { item ->
                        when (item) {
                            is RecordsListItem.RideItem -> SavedRideAccordionCard(
                                ride = item.ride,
                                period = period,
                                expanded = expandedItemId == item.stableId,
                                onToggleExpanded = {
                                    expandedItemId = if (expandedItemId == item.stableId) null else item.stableId
                                },
                                onEdit = {
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SAVED_RIDE_EDIT_OPENED",
                                        "savedRideId" to item.ride.id
                                    )
                                    editingRide = item.ride
                                },
                                onDelete = {
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_SAVED_RIDE_DELETE_CONFIRMATION_OPENED",
                                        "savedRideId" to item.ride.id
                                    )
                                    deleteConfirmationRide = item.ride
                                }
                            )

                            is RecordsListItem.FuelItem -> FuelEntryAccordionCard(
                                fuelEntry = item.fuelEntry,
                                period = period,
                                expanded = expandedItemId == item.stableId,
                                onToggleExpanded = {
                                    expandedItemId = if (expandedItemId == item.stableId) null else item.stableId
                                },
                                onEdit = {
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_FUEL_ENTRY_EDIT_OPENED",
                                        "fuelEntryId" to item.fuelEntry.id
                                    )
                                    editingFuelEntry = item.fuelEntry
                                },
                                onDelete = {
                                    RadarLogger.i(
                                        "KM_V2_SEEN",
                                        "KM_V2_FUEL_ENTRY_DELETE_CONFIRMATION_OPENED",
                                        "fuelEntryId" to item.fuelEntry.id
                                    )
                                    deleteConfirmationFuelEntry = item.fuelEntry
                                }
                            )

                            is RecordsListItem.TrackingItem -> TrackingRecordCard(
                                record = item.trackingRecord,
                                period = period,
                                onEdit = {
                                    RadarLogger.i(
                                        "KM_V2_TRACKING",
                                        "KM_V2_TRACKING_RECORD_EDIT_REQUESTED",
                                        "id" to item.trackingRecord.id,
                                        "type" to item.trackingRecord.type
                                    )
                                    editingTrackingRecord = item.trackingRecord
                                },
                                onDelete = {
                                    deleteConfirmationTrackingRecord = item.trackingRecord
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFuelEntryDialog) {
        FuelEntryDialog(
            onDismiss = {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_FUEL_ENTRY_CREATE_CANCELLED"
                )
                showFuelEntryDialog = false
            },
            onSave = { fuelEntry ->
                showFuelEntryDialog = false
                onCreateFuelEntry(fuelEntry)
            }
        )
    }

    if (showManualRideDialog) {
        ManualRideDialog(
            onDismiss = {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_MANUAL_RIDE_CREATE_CANCELLED"
                )
                showManualRideDialog = false
            },
            onSave = { manualRide ->
                showManualRideDialog = false
                onSaveEditedRide(manualRide)
            }
        )
    }

    editingRide?.let { ride ->
        SavedRideEditDialog(
            ride = ride,
            onDismiss = {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SAVED_RIDE_EDIT_CANCELLED",
                    "savedRideId" to ride.id
                )
                editingRide = null
            },
            onSave = { updatedRide ->
                editingRide = null
                expandedItemId = null
                onSaveEditedRide(updatedRide)
            }
        )
    }

    editingFuelEntry?.let { fuelEntry ->
        FuelEntryDialog(
            initialEntry = fuelEntry,
            onDismiss = {
                editingFuelEntry = null
            },
            onSave = { updatedEntry ->
                editingFuelEntry = null
                expandedItemId = null
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_FUEL_ENTRY_EDIT_SAVED",
                    "fuelEntryId" to updatedEntry.id,
                    "amountBrl" to updatedEntry.amountBrl,
                    "liters" to updatedEntry.liters
                )
                onSaveEditedFuelEntry(updatedEntry)
            }
        )
    }

    editingTrackingRecord?.let { record ->
        TrackingRecordEditDialog(
            record = record,
            onDismiss = {
                editingTrackingRecord = null
            },
            onSave = { updatedRecord ->
                editingTrackingRecord = null
                expandedItemId = null
                onSaveEditedTrackingRecord(updatedRecord)
            }
        )
    }

    deleteConfirmationRide?.let { ride ->
        AlertDialog(
            onDismissRequest = {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SAVED_RIDE_DELETE_CANCELLED",
                    "savedRideId" to ride.id
                )
                deleteConfirmationRide = null
            },
            title = { Text("Excluir este registro?") },
            text = { Text("Essa acao removera a corrida dos seus registros.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmationRide = null
                        expandedItemId = null
                        onDeleteRide(ride.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.Negative,
                        contentColor = KmOnePalette.TextPrimary
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        RadarLogger.i(
                            "KM_V2_SEEN",
                            "KM_V2_SAVED_RIDE_DELETE_CANCELLED",
                            "savedRideId" to ride.id
                        )
                        deleteConfirmationRide = null
                    }
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = KmOnePalette.Card
        )
    }

    deleteConfirmationFuelEntry?.let { fuelEntry ->
        AlertDialog(
            onDismissRequest = {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_FUEL_ENTRY_DELETE_CANCELLED",
                    "fuelEntryId" to fuelEntry.id
                )
                deleteConfirmationFuelEntry = null
            },
            title = { Text("Excluir este abastecimento?") },
            text = { Text("Essa acao removera o abastecimento dos seus registros.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmationFuelEntry = null
                        expandedItemId = null
                        onDeleteFuelEntry(fuelEntry.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.Negative,
                        contentColor = KmOnePalette.TextPrimary
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        RadarLogger.i(
                            "KM_V2_SEEN",
                            "KM_V2_FUEL_ENTRY_DELETE_CANCELLED",
                            "fuelEntryId" to fuelEntry.id
                        )
                        deleteConfirmationFuelEntry = null
                    }
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = KmOnePalette.Card
        )
    }

    deleteConfirmationTrackingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = {
                deleteConfirmationTrackingRecord = null
            },
            title = { Text("Excluir tracking?") },
            text = {
                Text(
                    if (record.type == TrackingRecordType.PRIVATE_RIDE) {
                        "Essa acao removera a corrida particular e ajustara o total do dia."
                    } else {
                        "Essa acao removera o deslocamento dos registros."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmationTrackingRecord = null
                        expandedItemId = null
                        onDeleteTrackingRecord(record)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.Negative,
                        contentColor = KmOnePalette.TextPrimary
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationTrackingRecord = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = KmOnePalette.Card
        )
    }
}

@Composable
private fun ConfigTab(
    debugState: RadarDebugState,
    settings: DriverSettings,
    isExportingLogs: Boolean,
    exportMessage: String?,
    onSaveDailyGoal: (Double) -> Unit,
    onExportDebugLogs: () -> Unit
) {
    var dailyGoalText by remember(settings.dailyGoalBrl) {
        mutableStateOf(settings.dailyGoalBrl?.let(::formatNumberInput) ?: "")
    }
    val dailyGoalValid = isValidPositiveOrBlank(dailyGoalText) && dailyGoalText.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderSection(
            title = "Config.",
            subtitle = "Area tecnica do app, com permissões e sinais atuais."
        )
        CockpitCard(title = "Metas", accent = KmOnePalette.Neon) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dailyGoalText,
                    onValueChange = { dailyGoalText = it },
                    label = { Text("Meta diaria (R$)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                Button(
                    onClick = {
                        parsePositiveOrNull(dailyGoalText)?.let(onSaveDailyGoal)
                    },
                    enabled = dailyGoalValid,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.NeonSoft,
                        contentColor = KmOnePalette.BackgroundDeep
                    )
                ) {
                    Text("Salvar meta")
                }
            }
        }
        CockpitCard(title = "Diagnostico", accent = KmOnePalette.ElectricBlue) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Gera um pacote com logs e arquivos recentes para analise no PC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KmOnePalette.TextSecondary
                )
                Text(
                    text = "O pacote pode conter prints, textos OCR, rotas e informacoes de corridas usadas apenas para debug.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
                Button(
                    onClick = onExportDebugLogs,
                    enabled = !isExportingLogs,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.ElectricBlue,
                        contentColor = KmOnePalette.BackgroundDeep
                    )
                ) {
                    if (isExportingLogs) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = KmOnePalette.BackgroundDeep
                        )
                    } else {
                        Text("Exportar logs")
                    }
                }
                exportMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                }
            }
        }
        CockpitCard(title = "Servico e overlay") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine("Servico", debugState.serviceActive.toString())
                DetailLine("Overlay permitido", debugState.piuOverlayPermissionGranted.toString())
                DetailLine("Overlay visivel", debugState.piuOverlayShowing.toString())
                DetailLine("Ultimo erro", debugState.piuLastError ?: "nenhum")
            }
        }
        CockpitCard(title = "Leitura atual") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine("OCR", debugState.lastOcrRawTextPreview ?: "sem leitura")
                DetailLine("Fingerprint", debugState.lastFingerprintKind ?: "n/d")
                DetailLine("Parser", debugState.lastParserResultStatus ?: "n/d")
                DetailLine("Decisao", debugState.lastEconomicDecision ?: "n/d")
                DetailLine("Apresentacao", debugState.lastPresentationKind ?: "n/d")
            }
        }
        CockpitCard(title = "Recuperacao e visao") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine("Melhor crop", debugState.lastBestCropKind ?: "n/d")
                DetailLine("Recovery visual", debugState.lastAutoVisionRecoveryReason ?: "n/d")
                DetailLine("Burst", debugState.lastAutoBurstReason ?: "n/d")
                DetailLine("Obstrucao", debugState.lastFloatingObstructionReason ?: "n/d")
            }
        }
    }
}

@Composable
private fun HeroPanel(
    title: String,
    subtitle: String,
    statusLabel: String
) {
    CockpitCard(
        title = title,
        accent = KmOnePalette.Neon,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusChip(
                    text = statusLabel,
                    background = Color(0x1F5BFF9A),
                    textColor = KmOnePalette.Neon
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KmOnePalette.TextSecondary
                )
            }
        }
    )
}

@Composable
private fun MetricPanel(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = KmOnePalette.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = value,
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
        }
    }
}

@Composable
private fun CockpitCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = KmOnePalette.Line,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = KmOnePalette.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = KmOnePalette.TextPrimary
            )
            content()
        }
    }
}

@Composable
private fun ActionGrid(actions: List<QuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowActions.forEach { action ->
                    OutlinedButton(
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KmOnePalette.TextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            KmOnePalette.Line
                        )
                    ) {
                        Text(
                            text = action.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusChip(
            text = "KM One ativo",
            background = Color(0x1F5BFF9A),
            textColor = KmOnePalette.Neon
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KmOnePalette.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = KmOnePalette.TextSecondary
        )
    }
}

@Composable
private fun FilterRow(
    selectedFilter: SeenFilter,
    onFilterSelected: (SeenFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SeenFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) Color(0x1F5BFF9A) else KmOnePalette.CardAlt,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) KmOnePalette.Neon else Color(0x3327C87B)
                ),
                modifier = Modifier.clickable { onFilterSelected(filter) }
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (selected) KmOnePalette.Neon else KmOnePalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SeenOfferCard(
    offer: SeenOffer,
    uiModel: SeenOfferUiModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSave: () -> Unit,
    onIgnore: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(24.dp),
        color = KmOnePalette.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3327C87B))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeenOfferCollapsedContent(
                uiModel = uiModel,
                expanded = expanded
            )
            if (expanded) {
                SeenOfferExpandedContent(
                    offer = offer,
                    uiModel = uiModel,
                    onSave = onSave,
                    onIgnore = onIgnore
                )
            }
        }
    }
}

@Composable
private fun SeenOffersHeaderActions(
    pendingCount: Int,
    onClearPending: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Toque em uma oferta para ver detalhes",
            style = MaterialTheme.typography.bodyMedium,
            color = KmOnePalette.TextSecondary
        )
        TextButton(
            onClick = onClearPending,
            enabled = pendingCount > 0
        ) {
            Text(
                text = "Apagar todas",
                color = if (pendingCount > 0) KmOnePalette.Attention else KmOnePalette.TextSecondary
            )
        }
    }
}

@Composable
private fun SeenOfferCollapsedContent(
    uiModel: SeenOfferUiModel,
    expanded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    text = uiModel.platformLabel,
                    background = Color(0x1F5AA8FF),
                    textColor = KmOnePalette.ElectricBlue
                )
                SeenOfferSourceBadge(uiModel.sourceBadgeLabel)
                SeenOfferDecisionBadge(uiModel.decisionBadgeLabel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    text = uiModel.valuePerKmLabel,
                    background = Color(0x1AFFFFFF),
                    textColor = if (uiModel.valuePerKmLabel != "R$/km -") KmOnePalette.Neon else KmOnePalette.TextSecondary
                )
            }
            Text(
                text = uiModel.priceLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            Text(
                text = uiModel.collapsedSummaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiModel.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
            Text(
                text = if (expanded) "Fechar" else "Detalhes",
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.Neon,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SeenOfferExpandedContent(
    offer: SeenOffer,
    uiModel: SeenOfferUiModel,
    onSave: () -> Unit,
    onIgnore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DividerLine()
        uiModel.productName?.let { DetailLine("Produto", it) }
        DetailLine("R$/km", if (uiModel.valuePerKmLabel == "R$/km -") "-" else uiModel.valuePerKmLabel)
        DetailLine("Origem", uiModel.originLabel)
        DetailLine("Destino", uiModel.destinationLabel)
        DetailLine("Busca", uiModel.pickupLabel)
        DetailLine("Viagem", uiModel.tripLabel)
        DetailLine("Total", uiModel.totalDistanceLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF153326),
                    contentColor = KmOnePalette.Positive
                )
            ) {
                Text("Salvar")
            }
            OutlinedButton(
                onClick = onIgnore,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
            ) {
                Text("Ignorar")
            }
        }
    }
}



@Composable
private fun SeenOfferSourceBadge(label: String) {
    val (background, textColor) = when (label) {
        "MANUAL" -> Color(0x2A7D93A8) to Color(0xFFE2E8F0)
        "RETRY" -> Color(0x2A5AA8FF) to KmOnePalette.ElectricBlue
        else -> Color(0x1F27C87B) to KmOnePalette.Neon
    }
    StatusChip(
        text = label,
        background = background,
        textColor = textColor
    )
}

@Composable
private fun SeenOfferDecisionBadge(label: String) {
    val (background, textColor) = when (label) {
        "BOA" -> Color(0x1F27C87B) to KmOnePalette.Positive
        "ATEN\u00C7\u00C3O" -> Color(0x1FFFC857) to KmOnePalette.Attention
        "RUIM" -> Color(0x1FFF6F7D) to KmOnePalette.Negative
        else -> Color(0x2A7D93A8) to KmOnePalette.TextSecondary
    }
    StatusChip(
        text = label,
        background = background,
        textColor = textColor
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x2238FF97))
    )
}

@Composable
private fun LegacySeenOfferCard(
    offer: SeenOffer,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onIgnore: () -> Unit
) {
    CockpitCard(
        title = displaySeenOfferTitle(offer),
        accent = statusColor(offer.status)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            text = platformLabel(offer.platform),
                            background = Color(0x1F5AA8FF),
                            textColor = KmOnePalette.ElectricBlue
                        )
                        StatusChip(
                            text = statusLabel(offer.status),
                            background = Color(0x1AFFFFFF),
                            textColor = statusColor(offer.status)
                        )
                    }
                    Text(
                        text = formatMoney(offer.price),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = KmOnePalette.TextPrimary
                    )
                    Text(
                        text = seenOfferPerKmLabel(offer),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (resolvedSeenOfferValuePerKm(offer) != null) KmOnePalette.Neon else KmOnePalette.TextSecondary
                    )
                }
                Text(
                    text = formatTimeStamp(offer.createdAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
            }
            MetricsRow(
                title = "Busca",
                time = offer.pickupTimeMin,
                distance = normalizedDistanceForDisplay(resolvedSeenOfferEconomics(offer).pickupDistanceKm)
            )
            MetricsRow(
                title = "Viagem",
                time = offer.tripTimeMin,
                distance = normalizedDistanceForDisplay(resolvedSeenOfferEconomics(offer).tripDistanceKm)
            )
            DetailLine("Origem", offer.originPreview ?: "—")
            DetailLine("Destino", offer.destinationPreview ?: "—")
            DetailLine(
                "Total",
                normalizedDistanceForDisplay(resolvedSeenOfferDistanceKm(offer))?.let(::formatKm) ?: "—"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF153326),
                        contentColor = KmOnePalette.Positive
                    )
                ) {
                    Text("Aceitei")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.Negative)
                ) {
                    Text("Recusei")
                }
                TextButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ignorar", color = KmOnePalette.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DailyGoalCard(
    summary: HomeDailySummary,
    isLoading: Boolean,
    errorMessage: String?,
    onConfigureGoal: () -> Unit
) {
    val statusText = when {
        summary.goalSource == HomeGoalSource.CONFIGURED -> "Meta diaria"
        summary.goalSource == HomeGoalSource.FALLBACK -> "Meta base do dia"
        else -> "Meta diaria"
    }
    CockpitCard(
        title = "Meta diaria",
        accent = KmOnePalette.Neon
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        text = statusText,
                        background = Color(0x1F5BFF9A),
                        textColor = KmOnePalette.Neon
                    )
                    Text(
                        text = when {
                            summary.goalSource == HomeGoalSource.MISSING -> "Defina uma meta diaria"
                            summary.isGoalReached -> "Meta batida"
                            else -> "Faltam ${formatMoney(summary.remainingToGoal)}"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = KmOnePalette.TextPrimary
                    )
                    Text(
                        text = when {
                            summary.goalSource == HomeGoalSource.MISSING ->
                                "Configure uma meta para acompanhar seu progresso do dia."
                            summary.isGoalReached ->
                                "Voce passou ${formatMoney(summary.earnedToday - (summary.dailyGoal ?: 0.0))} da meta de hoje."
                            else -> "para bater sua meta diaria"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KmOnePalette.TextSecondary
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = KmOnePalette.Neon
                    )
                } else {
                    StatusChip(
                        text = "${summary.progressPercent ?: 0}% concluido",
                        background = Color(0x14244D78),
                        textColor = KmOnePalette.ElectricBlue
                    )
                }
            }

            GoalProgressBar(progress = summary.progressFraction)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Hoje",
                    value = formatMoney(summary.earnedToday),
                    accent = KmOnePalette.Neon,
                    iconResId = R.drawable.ic_today_earnings
                )
                SummaryMetricPanel(
                    modifier = Modifier.weight(1f),
                    label = if (summary.goalSource == HomeGoalSource.MISSING) "Meta" else "Falta",
                    value = if (summary.goalSource == HomeGoalSource.MISSING) {
                        "Nao definida"
                    } else {
                        formatMoney(summary.remainingToGoal)
                    },
                    accent = KmOnePalette.ElectricBlue,
                    iconResId = R.drawable.ic_goal_remaining
                )
            }

            DetailLine(
                label = "Meta",
                value = summary.dailyGoal?.let(::formatMoney)
                    ?: "Nao configurada"
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.Attention
                )
            }
            if (summary.goalSource != HomeGoalSource.CONFIGURED) {
                TextButton(onClick = onConfigureGoal) {
                    Text("Configurar meta", color = KmOnePalette.Neon)
                }
            }
        }
    }
}

@Composable
private fun GoalProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF112033))
            .border(1.dp, KmOnePalette.Line, RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(KmOnePalette.NeonSoft, KmOnePalette.Neon)
                    )
                )
        )
    }
}

@Composable
private fun SummaryMetricPanel(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    iconResId: Int
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = KmOnePalette.CardAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(iconResId),
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(accent)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
        }
    }
}

@Composable
private fun TrackingLiveCard(
    onSaveTrackingRecord: (TrackingRecord) -> Unit
) {
    val context = LocalContext.current
    val locationClient = remember(context) { AndroidTrackingLocationClient(context) }
    val distanceCalculator = remember { TrackingDistanceCalculator() }
    var session by remember { mutableStateOf(TrackingUiSession()) }
    var distanceState by remember { mutableStateOf(TrackingDistanceState()) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showSaveTypeDialog by remember { mutableStateOf(false) }
    var selectedSaveType by remember { mutableStateOf<TrackingSaveType?>(null) }
    var privateRideAmountText by remember { mutableStateOf("") }
    var privateRideAmountError by remember { mutableStateOf<String?>(null) }
    var pendingEndedAtMs by remember { mutableStateOf<Long?>(null) }
    val elapsedMs = session.startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun stopGps(reason: String) {
        locationClient.stop()
        RadarLogger.i("KM_V2_TRACKING", "KM_V2_TRACKING_GPS_STOPPED", "reason" to reason)
    }

    fun startTrackingSession(startedAt: Long) {
        distanceState = TrackingDistanceState(gpsStatus = TrackingGpsStatus.WAITING_FIRST_FIX)
        session = TrackingUiSession(
            status = TrackingUiStatus.RUNNING,
            startedAtMs = startedAt,
            gpsStatus = TrackingGpsStatus.WAITING_FIRST_FIX
        )
        val started = locationClient.start(
            onPoint = { point ->
                val update = distanceCalculator.addPoint(distanceState, point)
                distanceState = update.state
                session = session.copy(
                    distanceKm = update.state.distanceKm,
                    pointCount = update.state.pointCount,
                    gpsStatus = update.state.gpsStatus
                )
                if (update.accepted) {
                    if (update.reason == "first_fix") {
                        RadarLogger.i(
                            "KM_V2_TRACKING",
                            "KM_V2_TRACKING_GPS_FIRST_FIX",
                            "accuracyMeters" to update.accuracyMeters
                        )
                    }
                    RadarLogger.i(
                        "KM_V2_TRACKING",
                        "KM_V2_TRACKING_GPS_POINT_ACCEPTED",
                        "accuracyMeters" to update.accuracyMeters,
                        "deltaMeters" to update.deltaMeters,
                        "distanceKm" to update.state.distanceKm,
                        "pointCount" to update.state.pointCount
                    )
                } else {
                    RadarLogger.i(
                        "KM_V2_TRACKING",
                        "KM_V2_TRACKING_GPS_POINT_REJECTED",
                        "reason" to update.reason,
                        "accuracyMeters" to update.accuracyMeters,
                        "deltaMeters" to update.deltaMeters
                    )
                }
            },
            onStatus = { status ->
                session = session.copy(gpsStatus = status)
            },
            onError = { error ->
                session = session.copy(gpsStatus = TrackingGpsStatus.ERROR)
                RadarLogger.w(
                    "KM_V2_TRACKING",
                    "KM_V2_TRACKING_GPS_ERROR",
                    "error" to (error.message ?: error::class.java.simpleName)
                )
            }
        )
        if (started) {
            RadarLogger.i("KM_V2_TRACKING", "KM_V2_TRACKING_GPS_STARTED", "startedAtMs" to startedAt)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        RadarLogger.i(
            "KM_V2_TRACKING",
            "KM_V2_TRACKING_LOCATION_PERMISSION_RESULT",
            "granted" to granted
        )
        if (granted) {
            startTrackingSession(session.startedAtMs ?: System.currentTimeMillis())
        } else {
            session = session.copy(gpsStatus = TrackingGpsStatus.PERMISSION_DENIED)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationClient.stop()
        }
    }

    LaunchedEffect(session.status, session.startedAtMs) {
        if (session.status != TrackingUiStatus.RUNNING || session.startedAtMs == null) return@LaunchedEffect
        while (session.status == TrackingUiStatus.RUNNING) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    if (showSaveTypeDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveTypeDialog = false
                selectedSaveType = null
                privateRideAmountText = ""
                privateRideAmountError = null
            },
            title = { Text("Como deseja salvar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Selecione o tipo do tracking encerrado para salvar em Registros.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Duracao: ${formatTrackingElapsed(((pendingEndedAtMs ?: nowMs) - (session.startedAtMs ?: nowMs)).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                selectedSaveType = TrackingSaveType.DISPLACEMENT
                                privateRideAmountError = null
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_SAVE_TYPE_SELECTED",
                                    "type" to TrackingSaveType.DISPLACEMENT.name
                                )
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedSaveType == TrackingSaveType.DISPLACEMENT) KmOnePalette.Neon else KmOnePalette.Line
                            )
                        ) {
                            Text(trackingTypeLabel(TrackingSaveType.DISPLACEMENT))
                        }
                        OutlinedButton(
                            onClick = {
                                selectedSaveType = TrackingSaveType.PRIVATE_RIDE
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_SAVE_TYPE_SELECTED",
                                    "type" to TrackingSaveType.PRIVATE_RIDE.name
                                )
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedSaveType == TrackingSaveType.PRIVATE_RIDE) KmOnePalette.Neon else KmOnePalette.Line
                            )
                        ) {
                            Text(trackingTypeLabel(TrackingSaveType.PRIVATE_RIDE))
                        }
                    }
                    if (selectedSaveType == TrackingSaveType.PRIVATE_RIDE) {
                        OutlinedTextField(
                            value = privateRideAmountText,
                            onValueChange = {
                                privateRideAmountText = it
                                privateRideAmountError = null
                            },
                            label = { Text("Valor em R$") },
                            singleLine = true,
                            isError = privateRideAmountError != null,
                            supportingText = {
                                privateRideAmountError?.let {
                                    Text(it, color = KmOnePalette.Negative)
                                }
                            },
                            colors = darkInputColors()
                        )
                    }
                    selectedSaveType?.let { saveType ->
                        Text(
                            text = if (saveType == TrackingSaveType.DISPLACEMENT) {
                                "Sera salvo sem receita e sem distancia medida nesta etapa."
                            } else {
                                "Sera salvo em Registros e somado ao total do dia."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = KmOnePalette.TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val saveType = selectedSaveType ?: return@TextButton
                        val startedAt = session.startedAtMs ?: return@TextButton
                        val endedAt = pendingEndedAtMs ?: System.currentTimeMillis()
                        val amount = if (saveType == TrackingSaveType.PRIVATE_RIDE) {
                            parsePositiveOrNull(privateRideAmountText)
                        } else {
                            null
                        }
                        if (saveType == TrackingSaveType.PRIVATE_RIDE &&
                            !TrackingRecordFactory.isValidPrivateRideAmount(amount)
                        ) {
                            privateRideAmountError = "Informe um valor valido"
                            return@TextButton
                        }
                        val recordType = when (saveType) {
                            TrackingSaveType.DISPLACEMENT -> TrackingRecordType.DISPLACEMENT
                            TrackingSaveType.PRIVATE_RIDE -> TrackingRecordType.PRIVATE_RIDE
                        }
                        val record = TrackingRecordFactory.create(
                            type = recordType,
                            startedAtMs = startedAt,
                            endedAtMs = endedAt,
                            distanceKm = session.distanceKm.takeIf { it > 0.0 },
                            amount = amount,
                            createdAtMs = System.currentTimeMillis()
                        )
                        onSaveTrackingRecord(record)
                        showSaveTypeDialog = false
                        session = TrackingUiSession()
                        distanceState = TrackingDistanceState()
                        selectedSaveType = null
                        privateRideAmountText = ""
                        privateRideAmountError = null
                        pendingEndedAtMs = null
                    }
                    ,
                    enabled = selectedSaveType != null
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveTypeDialog = false
                    privateRideAmountError = null
                }) {
                    Text("Cancelar")
                }
            },
            containerColor = KmOnePalette.Card
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xD90B1624),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3327C87B))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2200E676), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1A00E676))
                                    .border(1.dp, KmOnePalette.Line, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_route),
                                    contentDescription = "Tracking",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(KmOnePalette.Neon)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (session.status == TrackingUiStatus.RUNNING) "Tracking em andamento" else "Tracking ao vivo",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KmOnePalette.TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (session.status == TrackingUiStatus.RUNNING) {
                                        "Use para marcar deslocamentos sem passageiro ou preparar corrida particular."
                                    } else {
                                        "Registre deslocamentos sem passageiro ou corridas particulares em tempo real."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KmOnePalette.TextSecondary
                                )
                            }
                        }
                    }
                    HomeStatusPill(
                        text = if (session.status == TrackingUiStatus.RUNNING) "Rodando" else "Preparando",
                        accent = if (session.status == TrackingUiStatus.RUNNING) KmOnePalette.Neon else KmOnePalette.Attention
                    )
                }

                if (session.status == TrackingUiStatus.RUNNING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeStatusPill(trackingGpsStatusLabel(session.gpsStatus), KmOnePalette.ElectricBlue)
                        HomeStatusPill(formatTrackingElapsed(elapsedMs), KmOnePalette.Neon)
                        HomeStatusPill(formatKmCompact(session.distanceKm), KmOnePalette.Attention)
                    }
                    Text(
                        text = "Distancia ao vivo: ${formatKm(session.distanceKm)} • pontos GPS: ${session.pointCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val endedAt = System.currentTimeMillis()
                                pendingEndedAtMs = endedAt
                                stopGps("finished")
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_FINISH_REQUESTED",
                                    "startedAtMs" to session.startedAtMs,
                                    "endedAtMs" to endedAt,
                                    "durationSeconds" to session.startedAtMs?.let {
                                        TrackingRecordFactory.durationSeconds(it, endedAt)
                                    }
                                )
                                showSaveTypeDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KmOnePalette.Neon,
                                contentColor = KmOnePalette.BackgroundDeep
                            )
                        ) {
                            Text(trackingRunningActionLabels().first(), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val cancelledAt = System.currentTimeMillis()
                                stopGps("cancelled")
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_CANCELLED",
                                    "startedAtMs" to session.startedAtMs,
                                    "durationSeconds" to session.startedAtMs?.let {
                                        TrackingRecordFactory.durationSeconds(it, cancelledAt)
                                    }
                                )
                                session = TrackingUiSession()
                                distanceState = TrackingDistanceState()
                                selectedSaveType = null
                                privateRideAmountText = ""
                                privateRideAmountError = null
                                pendingEndedAtMs = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
                        ) {
                            Text(trackingRunningActionLabels().last())
                        }
                    }
                } else {
                    Text(
                        text = "Km inicial e final ainda nao usam GPS. O registro salvo entra em Registros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KmOnePalette.TextSecondary
                    )
                    Button(
                        onClick = {
                            val startedAt = System.currentTimeMillis()
                            RadarLogger.i(
                                "KM_V2_TRACKING",
                                "KM_V2_TRACKING_STARTED",
                                "startedAtMs" to startedAt
                            )
                            nowMs = startedAt
                            if (hasLocationPermission()) {
                                startTrackingSession(startedAt)
                            } else {
                                session = TrackingUiSession(
                                    status = TrackingUiStatus.RUNNING,
                                    startedAtMs = startedAt,
                                    gpsStatus = TrackingGpsStatus.WAITING_PERMISSION
                                )
                                RadarLogger.i(
                                    "KM_V2_TRACKING",
                                    "KM_V2_TRACKING_LOCATION_PERMISSION_REQUESTED"
                                )
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KmOnePalette.Neon,
                            contentColor = KmOnePalette.BackgroundDeep
                        )
                    ) {
                        Text(trackingPrimaryButtonLabel(TrackingUiStatus.IDLE), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzeOfferCta(
    isServiceActive: Boolean,
    isOverlayPermissionGranted: Boolean,
    isRadarVisible: Boolean,
    onToggleRadar: () -> Unit
) {
    val actionLabel = homeRadarActionLabel(isRadarVisible)
    val title = actionLabel
    val description = when {
        !isServiceActive -> "Ative a acessibilidade do KM One para abrir o radar."
        !isOverlayPermissionGranted -> "Permita sobreposicao para abrir o radar do KM One."
        isRadarVisible -> "Radar visivel. Toque para ocultar o painel."
        else -> "Abra o radar para acompanhar e analisar ofertas."
    }
    val actionIcon = homeRadarActionIconRes(isRadarVisible)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = KmOnePalette.CardAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KmOnePalette.TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
            }
            Button(
                onClick = onToggleRadar,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.Neon,
                    contentColor = KmOnePalette.Background
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(actionIcon),
                        contentDescription = actionLabel,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(KmOnePalette.Background)
                    )
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BestRideCard(summary: HomeDailySummary) {
    CockpitCard(
        title = "Melhor corrida do dia",
        accent = Color(0x445AA8FF)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = summary.bestRideValuePerKm?.let(::formatPerKm) ?: formatMoney(summary.bestRidePrice),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            Text(
                text = summary.bestRideProductName ?: "Corrida aceita manualmente",
                style = MaterialTheme.typography.bodyMedium,
                color = KmOnePalette.TextSecondary
            )
            summary.bestRidePrice?.let {
                DetailLine("Valor", formatMoney(it))
            }
            summary.bestRideValuePerKm?.let {
                DetailLine("R$/km", formatPerKm(it))
            }
        }
    }
}

@Composable
private fun RecordsSummaryCard(summary: RecordsSummary) {
    CockpitCard(
        title = recordsPeriodTitle(summary.period),
        accent = KmOnePalette.ElectricBlue
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = formatMoney(summary.totalEarned),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Corridas",
                    value = summary.ridesCount.toString(),
                    accent = KmOnePalette.Neon,
                    valueStyle = MaterialTheme.typography.titleLarge
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Km",
                    value = summary.totalKm?.let(::formatKmCompact) ?: "--",
                    accent = KmOnePalette.ElectricBlue,
                    valueStyle = MaterialTheme.typography.titleLarge
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Media",
                    value = summary.averageValuePerKm?.let(::formatPerKm) ?: "--",
                    accent = KmOnePalette.Attention,
                    valueStyle = MaterialTheme.typography.titleLarge
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Abastecido",
                    value = summary.fuelSpentAmount?.let(::formatMoney) ?: formatMoney(0.0),
                    accent = KmOnePalette.Negative,
                    valueStyle = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun RecordsTopActions(
    onAddFuel: () -> Unit,
    onManualRide: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onAddFuel,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KmOnePalette.CardAlt,
                contentColor = KmOnePalette.Neon
            )
        ) {
            Text("+ Abastecimento")
        }
        OutlinedButton(
            onClick = onManualRide,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
        ) {
            Text("Corrida manual")
        }
    }
}

@Composable
private fun RecordsPeriodFilterTabs(
    selectedPeriod: RecordsPeriodFilter,
    onPeriodSelected: (RecordsPeriodFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecordsPeriodFilter.entries.forEach { period ->
            val selected = period == selectedPeriod
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) Color(0x1F5BFF9A) else KmOnePalette.CardAlt,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) KmOnePalette.Neon else Color(0x3327C87B)
                ),
                modifier = Modifier.clickable { onPeriodSelected(period) }
            ) {
                Text(
                    text = recordsPeriodShortLabel(period),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) KmOnePalette.Neon else KmOnePalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SavedRideAccordionCard(
    ride: SavedRide,
    period: RecordsPeriodFilter,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(24.dp),
        color = KmOnePalette.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x335AA8FF))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SavedRideCollapsedContent(
                ride = ride,
                period = period,
                expanded = expanded
            )
            if (expanded) {
                SavedRideExpandedContent(
                    ride = ride,
                    period = period,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun FuelEntryAccordionCard(
    fuelEntry: FuelEntry,
    period: RecordsPeriodFilter,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(24.dp),
        color = KmOnePalette.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFC857))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FuelEntryCollapsedContent(
                fuelEntry = fuelEntry,
                period = period,
                expanded = expanded
            )
            if (expanded) {
                FuelEntryExpandedContent(
                    fuelEntry = fuelEntry,
                    period = period,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun FuelEntryCollapsedContent(
    fuelEntry: FuelEntry,
    period: RecordsPeriodFilter,
    expanded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(
                text = "Abastecimento",
                background = Color(0x1AFFFFFF),
                textColor = KmOnePalette.Attention
            )
            Text(
                text = formatMoney(fuelEntry.amountBrl),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            val secondary = listOfNotNull(
                fuelEntry.fuelType,
                fuelEntry.liters?.let { "${formatDecimal(it)} L" }
            ).joinToString(" • ")
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KmOnePalette.TextSecondary
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatRecordStamp(fuelEntry.createdAtMs, period),
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
            Text(
                text = if (expanded) "Ocultar" else "Detalhes",
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.Attention,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FuelEntryExpandedContent(
    fuelEntry: FuelEntry,
    period: RecordsPeriodFilter,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DividerLine()
        DetailLine("Tipo", "Abastecimento")
        DetailLine("Valor", formatMoney(fuelEntry.amountBrl))
        DetailLine("Combustivel", fuelEntry.fuelType ?: "-")
        DetailLine("Litros", fuelEntry.liters?.let { "${formatDecimal(it)} L" } ?: "-")
        DetailLine("Observacao", fuelEntry.note ?: "-")
        DetailLine("Quando", formatRecordStamp(fuelEntry.createdAtMs, period, includeDateForDay = true))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.ElectricBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.ElectricBlue)
            ) {
                Text("Editar")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.Negative),
                border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Negative)
            ) {
                Text("Excluir")
            }
        }
    }
}

@Composable
private fun SavedRideCollapsedContent(
    ride: SavedRide,
    period: RecordsPeriodFilter,
    expanded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    text = platformLabel(ride.platform),
                    background = Color(0x1F5AA8FF),
                    textColor = KmOnePalette.ElectricBlue
                )
                StatusChip(
                    text = savedRidePerKmLabel(ride),
                    background = Color(0x1AFFFFFF),
                    textColor = if (resolvedSavedRideValuePerKm(ride) != null) KmOnePalette.Neon else KmOnePalette.TextSecondary
                )
            }
            Text(
                text = formatMoney(ride.price),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatRecordStamp(ride.acceptedAtMs, period),
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.TextSecondary
            )
            Text(
                text = if (expanded) "Ocultar" else "Detalhes",
                style = MaterialTheme.typography.bodySmall,
                color = KmOnePalette.Neon,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SavedRideExpandedContent(
    ride: SavedRide,
    period: RecordsPeriodFilter,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val resolvedEconomics = resolvedSavedRideEconomics(ride)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DividerLine()
        DetailLine("Produto", ride.productName ?: platformLabel(ride.platform))
        DetailLine("Quando", formatRecordStamp(ride.acceptedAtMs, period, includeDateForDay = true))
        DetailLine("Origem", ride.originPreview ?: "—")
        DetailLine("Destino", ride.destinationPreview ?: "—")
        MetricsRow("Busca", ride.pickupTimeMin, normalizedDistanceForDisplay(resolvedEconomics.pickupDistanceKm))
        MetricsRow("Viagem", ride.tripTimeMin, normalizedDistanceForDisplay(resolvedEconomics.tripDistanceKm))
        DetailLine(
            "Total",
            normalizedDistanceForDisplay(resolvedEconomics.totalDistanceKm)?.let(::formatKm) ?: "—"
        )
        DetailLine("Fonte", savedRideSourceLabel(ride.source))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.ElectricBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.ElectricBlue)
            ) {
                Text("Editar")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.Negative),
                border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Negative)
            ) {
                Text("Excluir")
            }
        }
    }
}

@Composable
private fun SavedRideEditDialog(
    ride: SavedRide,
    onDismiss: () -> Unit,
    onSave: (SavedRide) -> Unit
) {
    var priceText by remember(ride.id) { mutableStateOf(ride.price?.toString().orEmpty()) }
    var productText by remember(ride.id) { mutableStateOf(ride.productName.orEmpty()) }
    var originText by remember(ride.id) { mutableStateOf(ride.originPreview.orEmpty()) }
    var destinationText by remember(ride.id) { mutableStateOf(ride.destinationPreview.orEmpty()) }
    var pickupDistanceText by remember(ride.id) { mutableStateOf(ride.pickupDistanceKm?.toString().orEmpty()) }
    var pickupTimeText by remember(ride.id) { mutableStateOf(ride.pickupTimeMin?.toString().orEmpty()) }
    var tripDistanceText by remember(ride.id) { mutableStateOf(ride.tripDistanceKm?.toString().orEmpty()) }
    var tripTimeText by remember(ride.id) { mutableStateOf(ride.tripTimeMin?.toString().orEmpty()) }
    var totalDistanceText by remember(ride.id) { mutableStateOf(ride.totalDistanceKm?.toString().orEmpty()) }
    val draftRide = ride.copy(
        price = parsePositiveOrNull(priceText),
        productName = normalizeOptionalText(productText),
        originPreview = normalizeOptionalText(originText),
        destinationPreview = normalizeOptionalText(destinationText),
        pickupDistanceKm = parseNonNegativeOrNull(pickupDistanceText),
        pickupTimeMin = parseNonNegativeOrNull(pickupTimeText),
        tripDistanceKm = parseNonNegativeOrNull(tripDistanceText),
        tripTimeMin = parseNonNegativeOrNull(tripTimeText),
        totalDistanceKm = parseNonNegativeOrNull(totalDistanceText)
    )
    val recalculatedValuePerKm = recalculateValuePerKm(draftRide)

    val validInputs = listOf(
        isValidPositiveOrBlank(priceText),
        isValidNonNegativeOrBlank(pickupDistanceText),
        isValidNonNegativeOrBlank(pickupTimeText),
        isValidNonNegativeOrBlank(tripDistanceText),
        isValidNonNegativeOrBlank(tripTimeText),
        isValidNonNegativeOrBlank(totalDistanceText)
    ).all { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar registro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preco") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = productText,
                    onValueChange = { productText = it },
                    label = { Text("Produto") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = originText,
                    onValueChange = { originText = it },
                    label = { Text("Origem") },
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = destinationText,
                    onValueChange = { destinationText = it },
                    label = { Text("Destino") },
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = pickupDistanceText,
                    onValueChange = { pickupDistanceText = it },
                    label = { Text("Distancia de busca (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = pickupTimeText,
                    onValueChange = { pickupTimeText = it },
                    label = { Text("Tempo de busca (min)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = tripDistanceText,
                    onValueChange = { tripDistanceText = it },
                    label = { Text("Distancia da viagem (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = tripTimeText,
                    onValueChange = { tripTimeText = it },
                    label = { Text("Tempo da viagem (min)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = totalDistanceText,
                    onValueChange = { totalDistanceText = it },
                    label = { Text("Distancia total (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                DetailLine(
                    "R$/km calculado",
                    recalculatedValuePerKm?.let(::formatPerKm) ?: "-"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draftRide.copy(
                            valuePerKm = recalculatedValuePerKm,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                },
                enabled = validInputs,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.NeonSoft,
                    contentColor = KmOnePalette.BackgroundDeep
                )
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = KmOnePalette.Card
    )
}

@Composable
private fun FuelEntryDialog(
    initialEntry: FuelEntry? = null,
    onDismiss: () -> Unit,
    onSave: (FuelEntry) -> Unit
) {
    var amountText by remember(initialEntry?.id) { mutableStateOf(initialEntry?.amountBrl?.let(::formatNumberInput) ?: "") }
    var litersText by remember(initialEntry?.id) { mutableStateOf(initialEntry?.liters?.let(::formatDecimal) ?: "") }
    var fuelTypeText by remember(initialEntry?.id) { mutableStateOf(initialEntry?.fuelType.orEmpty()) }
    var noteText by remember(initialEntry?.id) { mutableStateOf(initialEntry?.note.orEmpty()) }
    val valid = isValidPositiveOrBlank(amountText) && amountText.isNotBlank() && isValidPositiveOrBlank(litersText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar abastecimento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor abastecido (R$)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("Litros") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = fuelTypeText,
                    onValueChange = { fuelTypeText = it },
                    label = { Text("Tipo de combustivel") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Observacao") },
                    colors = darkInputColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        FuelEntry(
                            id = initialEntry?.id ?: java.util.UUID.randomUUID().toString(),
                            amountBrl = parsePositiveOrNull(amountText) ?: 0.0,
                            liters = parsePositiveOrNull(litersText),
                            fuelType = normalizeOptionalText(fuelTypeText),
                            createdAtMs = initialEntry?.createdAtMs ?: System.currentTimeMillis(),
                            note = normalizeOptionalText(noteText)
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.NeonSoft,
                    contentColor = KmOnePalette.BackgroundDeep
                )
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = KmOnePalette.Card
    )
}

@Composable
private fun ManualRideDialog(
    onDismiss: () -> Unit,
    onSave: (SavedRide) -> Unit
) {
    var platformText by remember { mutableStateOf("Uber") }
    var productText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var originText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var pickupDistanceText by remember { mutableStateOf("") }
    var pickupTimeText by remember { mutableStateOf("") }
    var tripDistanceText by remember { mutableStateOf("") }
    var tripTimeText by remember { mutableStateOf("") }
    var totalDistanceText by remember { mutableStateOf("") }
    val validInputs = listOf(
        isValidPositiveOrBlank(priceText) && priceText.isNotBlank(),
        isValidNonNegativeOrBlank(pickupDistanceText),
        isValidNonNegativeOrBlank(pickupTimeText),
        isValidNonNegativeOrBlank(tripDistanceText),
        isValidNonNegativeOrBlank(tripTimeText),
        isValidNonNegativeOrBlank(totalDistanceText)
    ).all { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrida manual") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = platformText,
                    onValueChange = { platformText = it },
                    label = { Text("Plataforma") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = productText,
                    onValueChange = { productText = it },
                    label = { Text("Produto/categoria") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preco") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = originText,
                    onValueChange = { originText = it },
                    label = { Text("Origem") },
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = destinationText,
                    onValueChange = { destinationText = it },
                    label = { Text("Destino") },
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = pickupDistanceText,
                    onValueChange = { pickupDistanceText = it },
                    label = { Text("Distancia de busca (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = pickupTimeText,
                    onValueChange = { pickupTimeText = it },
                    label = { Text("Tempo de busca (min)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = tripDistanceText,
                    onValueChange = { tripDistanceText = it },
                    label = { Text("Distancia da viagem (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = tripTimeText,
                    onValueChange = { tripTimeText = it },
                    label = { Text("Tempo da viagem (min)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = totalDistanceText,
                    onValueChange = { totalDistanceText = it },
                    label = { Text("Distancia total (km)") },
                    singleLine = true,
                    colors = darkInputColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nowMs = System.currentTimeMillis()
                    val ride = SavedRide(
                        id = java.util.UUID.randomUUID().toString(),
                        sourceSeenOfferId = null,
                        platform = parseManualRidePlatform(platformText),
                        price = parsePositiveOrNull(priceText),
                        valuePerKm = null,
                        pickupDistanceKm = parseNonNegativeOrNull(pickupDistanceText),
                        pickupTimeMin = parseNonNegativeOrNull(pickupTimeText),
                        tripDistanceKm = parseNonNegativeOrNull(tripDistanceText),
                        tripTimeMin = parseNonNegativeOrNull(tripTimeText),
                        totalDistanceKm = parseNonNegativeOrNull(totalDistanceText),
                        estimatedTotalTimeMin = listOfNotNull(
                            parseNonNegativeOrNull(pickupTimeText),
                            parseNonNegativeOrNull(tripTimeText)
                        ).takeIf { it.isNotEmpty() }?.sum(),
                        productName = normalizeOptionalText(productText),
                        originPreview = normalizeOptionalText(originText),
                        destinationPreview = normalizeOptionalText(destinationText),
                        acceptedAtMs = nowMs,
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs,
                        source = SavedRideSource.MANUAL_ENTRY
                    )
                    onSave(ride.copy(valuePerKm = recalculateValuePerKm(ride)))
                },
                enabled = validInputs,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.NeonSoft,
                    contentColor = KmOnePalette.BackgroundDeep
                )
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = KmOnePalette.Card
    )
}

private fun displaySeenOfferTitle(offer: SeenOffer): String {
    val productName = offer.productName?.takeUnless(SeenOfferSanitizationRules::isBadProductName)
    return productName ?: platformLabel(offer.platform)
}

private fun normalizedDistanceForDisplay(value: Double?): Double? {
    return value?.takeIf { it > 0.0 }
}

@Composable
private fun MetricsRow(
    title: String,
    time: Double?,
    distance: Double?
) {
    DetailLine(
        label = title,
        value = listOfNotNull(
            time?.let(::formatMinutes),
            distance?.let(::formatKm)
        ).joinToString(" • ").ifBlank { "n/d" }
    )
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String
) {
    CockpitCard(title = title, accent = KmOnePalette.Line) {
        Text(
            text = message,
            color = KmOnePalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = KmOnePalette.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = KmOnePalette.TextPrimary
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String?,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        CockpitCard(title = title, accent = KmOnePalette.Line) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message ?: "",
                    color = KmOnePalette.TextSecondary
                )
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KmOnePalette.CardAlt,
                        contentColor = KmOnePalette.TextPrimary
                    )
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun KmOneBottomBar(
    selectedTab: AppTab,
    onSelected: (AppTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        color = Color(0xE20B1624),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelected(tab) }
                        .background(
                            if (selected) {
                                Color(0x2600E676)
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selected) KmOnePalette.Neon.copy(alpha = 0.95f) else KmOnePalette.CardAlt)
                            .border(1.dp, if (selected) KmOnePalette.Neon.copy(alpha = 0.5f) else KmOnePalette.Line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(tab.iconResId),
                            contentDescription = tab.label,
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(
                                if (selected) KmOnePalette.Background else KmOnePalette.TextSecondary
                            )
                        )
                    }
                    Text(
                        text = tab.label,
                        color = if (selected) KmOnePalette.Neon else KmOnePalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingRecordCard(
    record: TrackingRecord,
    period: RecordsPeriodFilter,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val model = remember(record) { TrackingRecordUiMapper.map(record) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = KmOnePalette.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3327C87B))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            text = "Tracking",
                            background = Color(0x1F27C87B),
                            textColor = KmOnePalette.Neon
                        )
                        StatusChip(
                            text = model.revenueLabel,
                            background = Color(0x1AFFFFFF),
                            textColor = if (record.type == TrackingRecordType.PRIVATE_RIDE) KmOnePalette.Positive else KmOnePalette.TextSecondary
                        )
                    }
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KmOnePalette.TextPrimary
                    )
                    Text(
                        text = model.amountLabel ?: "Sem receita",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (model.amountLabel != null) KmOnePalette.TextPrimary else KmOnePalette.TextSecondary
                    )
                }
                Text(
                    text = formatRecordStamp(record.endedAtMs, period),
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
            }
            DividerLine()
            DetailLine("Duracao", model.durationLabel)
            DetailLine("Distancia", model.distanceLabel)
            record.notes?.takeIf { it.isNotBlank() }?.let { DetailLine("Observacao", it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.ElectricBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.ElectricBlue)
                ) {
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KmOnePalette.Negative),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KmOnePalette.Negative)
                ) {
                    Text("Excluir")
                }
            }
        }
    }
}

@Composable
private fun TrackingRecordEditDialog(
    record: TrackingRecord,
    onDismiss: () -> Unit,
    onSave: (TrackingRecord) -> Unit
) {
    var amountText by remember(record.id) { mutableStateOf(record.amount?.let(::formatNumberInput) ?: "") }
    var distanceText by remember(record.id) { mutableStateOf(record.distanceKm?.let(::formatNumberInput) ?: "") }
    var notesText by remember(record.id) { mutableStateOf(record.notes.orEmpty()) }
    val amountValid = record.type != TrackingRecordType.PRIVATE_RIDE ||
        (isValidPositiveOrBlank(amountText) && amountText.isNotBlank())
    val distanceValid = isValidNonNegativeOrBlank(distanceText)
    val valid = amountValid && distanceValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (record.type == TrackingRecordType.PRIVATE_RIDE) {
                    "Editar corrida particular"
                } else {
                    "Editar deslocamento"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (record.type == TrackingRecordType.PRIVATE_RIDE) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Valor R$") },
                        singleLine = true,
                        isError = !amountValid,
                        colors = darkInputColors()
                    )
                }
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distancia km") },
                    singleLine = true,
                    isError = !distanceValid,
                    colors = darkInputColors()
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Observacao") },
                    colors = darkInputColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        record.copy(
                            amount = if (record.type == TrackingRecordType.PRIVATE_RIDE) {
                                parsePositiveOrNull(amountText)
                            } else {
                                null
                            },
                            distanceKm = parseNonNegativeOrNull(distanceText),
                            notes = normalizeOptionalText(notesText)
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.NeonSoft,
                    contentColor = KmOnePalette.BackgroundDeep
                )
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = KmOnePalette.Card
    )
}

private data class HomeRecentActivity(
    val kindLabel: String,
    val opensSeen: Boolean,
    val platformLabel: String,
    val platformAccent: Color,
    val priceLabel: String,
    val valuePerKmLabel: String,
    val originLabel: String,
    val destinationLabel: String,
    val tripTimeLabel: String,
    val tripDistanceLabel: String,
    val totalDistanceLabel: String,
    val timeLabel: String
)

internal fun visibleHomeSeenOffers(seenOffers: List<SeenOffer>): List<SeenOffer> {
    return seenOffers.filter { offer ->
        offer.status == SeenOfferStatus.SEEN &&
            !SeenOfferSanitizationRules.isSuspiciousForPendingQueue(offer)
    }
}

private fun buildHomeRecentActivity(
    seenOffers: List<SeenOffer>,
    savedRides: List<SavedRide>
): HomeRecentActivity? {
    val lastSeen = seenOffers.maxByOrNull { it.createdAtMs }
    val lastRide = savedRides.maxByOrNull { it.acceptedAtMs }
    return when {
        lastRide == null && lastSeen == null -> null
        lastRide != null && (lastSeen == null || lastRide.acceptedAtMs >= lastSeen.createdAtMs) -> {
            val economics = resolvedSavedRideEconomics(lastRide)
            HomeRecentActivity(
                kindLabel = "CORRIDA",
                opensSeen = false,
                platformLabel = platformLabel(lastRide.platform),
                platformAccent = platformAccent(lastRide.platform),
                priceLabel = formatMoney(lastRide.price),
                valuePerKmLabel = savedRidePerKmLabel(lastRide),
                originLabel = lastRide.originPreview ?: "Nao identificada",
                destinationLabel = lastRide.destinationPreview ?: "Nao identificado",
                tripTimeLabel = lastRide.tripTimeMin?.let(::formatMinutes) ?: "Tempo n/d",
                tripDistanceLabel = economics.tripDistanceKm?.let(::formatKmCompact) ?: "Km n/d",
                totalDistanceLabel = economics.totalDistanceKm?.let(::formatKmCompact)?.plus(" total") ?: "Total n/d",
                timeLabel = formatRecordStamp(lastRide.acceptedAtMs, RecordsPeriodFilter.DAY, includeDateForDay = true)
            )
        }
        else -> {
            val seen = lastSeen ?: return null
            val economics = resolvedSeenOfferEconomics(seen)
            HomeRecentActivity(
                kindLabel = "OFERTA",
                opensSeen = true,
                platformLabel = platformLabel(seen.platform),
                platformAccent = platformAccent(seen.platform),
                priceLabel = formatMoney(seen.price),
                valuePerKmLabel = seenOfferPerKmLabel(seen),
                originLabel = seen.originPreview ?: "Nao identificada",
                destinationLabel = seen.destinationPreview ?: "Nao identificado",
                tripTimeLabel = seen.tripTimeMin?.let(::formatMinutes) ?: "Tempo n/d",
                tripDistanceLabel = economics.tripDistanceKm?.let(::formatKmCompact) ?: "Km n/d",
                totalDistanceLabel = economics.totalDistanceKm?.let(::formatKmCompact)?.plus(" total") ?: "Total n/d",
                timeLabel = formatTimeStamp(seen.createdAtMs)
            )
        }
    }
}

private fun List<FuelEntry>.sumFuelSpentToday(nowMs: Long = System.currentTimeMillis()): Double {
    val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return filter {
        Instant.ofEpochMilli(it.createdAtMs).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }.sumOf { it.amountBrl }
}

private fun List<FuelEntry>.countTodayEntries(nowMs: Long = System.currentTimeMillis()): Int {
    val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return count {
        Instant.ofEpochMilli(it.createdAtMs).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
}

private fun List<SavedRide>.dayOverDayEarnedDelta(
    todayEarned: Double,
    nowMs: Long = System.currentTimeMillis()
): Double? {
    val zoneId = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
    val yesterday = today.minus(1, ChronoUnit.DAYS)
    val yesterdayEarned = filter {
        Instant.ofEpochMilli(it.acceptedAtMs).atZone(zoneId).toLocalDate() == yesterday
    }.sumOf { it.price ?: 0.0 }
    if (yesterdayEarned == 0.0) return null
    return ((todayEarned - yesterdayEarned) / yesterdayEarned) * 100.0
}

private fun yesterdayDeltaLabel(delta: Double?): String {
    if (delta == null) return "Sem base de ontem"
    val prefix = if (delta >= 0.0) "+" else ""
    return "$prefix${String.format(Locale.US, "%.0f", delta)}% vs ontem"
}

private fun profitShareText(earnedToday: Double, fuelSpendToday: Double): String {
    if (earnedToday <= 0.0) return "Sem bruto fechado ainda"
    val net = earnedToday - fuelSpendToday
    val share = (net / earnedToday) * 100.0
    return "${String.format(Locale.US, "%.0f", share.coerceAtLeast(0.0))}% do bruto"
}

internal fun homeRadarActionLabel(isRadarVisible: Boolean): String {
    return if (isRadarVisible) "Ocultar Radar" else "Abrir Radar"
}

internal fun homeRadarActionIconRes(isRadarVisible: Boolean): Int {
    return if (isRadarVisible) R.drawable.ic_close else R.drawable.ic_eye
}

internal fun trackingPrimaryButtonLabel(status: TrackingUiStatus): String {
    return if (status == TrackingUiStatus.IDLE) "Iniciar tracking" else "Tracking ativo"
}

internal fun trackingRunningActionLabels(): List<String> {
    return listOf("Encerrar e salvar", "Cancelar")
}

internal fun trackingTypeLabel(type: TrackingSaveType): String {
    return when (type) {
        TrackingSaveType.DISPLACEMENT -> "Deslocamento"
        TrackingSaveType.PRIVATE_RIDE -> "Corrida particular"
    }
}

private fun formatTrackingElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun platformAccent(platform: RidePlatform): Color {
    return when (platform) {
        RidePlatform.NINETY_NINE -> KmOnePalette.Attention
        RidePlatform.UBER -> KmOnePalette.Neon
        RidePlatform.UNKNOWN -> KmOnePalette.ElectricBlue
    }
}

private fun statusLabel(status: SeenOfferStatus): String {
    return when (status) {
        SeenOfferStatus.SEEN -> "Vista"
        SeenOfferStatus.ACCEPTED_MANUALLY -> "Aceita"
        SeenOfferStatus.REJECTED_MANUALLY -> "Recusada"
        SeenOfferStatus.IGNORED -> "Ignorada"
        SeenOfferStatus.EXPIRED -> "Expirada"
    }
}

private fun statusColor(status: SeenOfferStatus): Color {
    return when (status) {
        SeenOfferStatus.SEEN -> KmOnePalette.ElectricBlue
        SeenOfferStatus.ACCEPTED_MANUALLY -> KmOnePalette.Positive
        SeenOfferStatus.REJECTED_MANUALLY -> KmOnePalette.Negative
        SeenOfferStatus.IGNORED -> KmOnePalette.Neutral
        SeenOfferStatus.EXPIRED -> KmOnePalette.TextSecondary
    }
}

private fun platformLabel(platform: RidePlatform): String {
    return when (platform) {
        RidePlatform.UBER -> "Uber"
        RidePlatform.NINETY_NINE -> "99"
        RidePlatform.UNKNOWN -> "Indefinida"
    }
}

private fun formatMoney(value: Double?): String {
    if (value == null) return "--"
    return "R$ " + String.format(Locale.US, "%.2f", value).replace(".", ",")
}

private fun formatPerKm(value: Double): String {
    return "${formatMoney(value)}/km"
}

private fun formatKm(value: Double): String {
    return String.format(Locale.US, "%.1f km", value).replace(".", ",")
}

private fun formatKmCompact(value: Double): String {
    return String.format(Locale.US, "%.1f", value).replace(".", ",") + " km"
}

private fun formatMinutes(value: Double): String {
    return "${value.toInt()} min"
}

private fun formatTimeStamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
}

private fun formatRecordStamp(
    timestamp: Long,
    period: RecordsPeriodFilter,
    includeDateForDay: Boolean = false
): String {
    val pattern = when {
        period == RecordsPeriodFilter.DAY && !includeDateForDay -> "HH:mm"
        else -> "dd/MM HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
}

private fun recordsPeriodTitle(period: RecordsPeriodFilter): String {
    return when (period) {
        RecordsPeriodFilter.DAY -> "Total do dia"
        RecordsPeriodFilter.WEEK -> "Total da semana"
        RecordsPeriodFilter.MONTH -> "Total do mes"
    }
}

private fun recordsPeriodShortLabel(period: RecordsPeriodFilter): String {
    return when (period) {
        RecordsPeriodFilter.DAY -> "Dia"
        RecordsPeriodFilter.WEEK -> "Semana"
        RecordsPeriodFilter.MONTH -> "Mes"
    }
}

private fun rideDistanceKmForDisplay(ride: SavedRide): Double? {
    return normalizedDistanceForDisplay(
        RideEconomicsCalculator.resolveRideEconomics(
            platform = ride.platform,
            price = ride.price,
            explicitValuePerKm = ride.valuePerKm,
            totalDistanceKm = ride.totalDistanceKm,
            pickupDistanceKm = ride.pickupDistanceKm,
            tripDistanceKm = ride.tripDistanceKm
        ).totalDistanceKm
    )
}

private fun savedRideSourceLabel(source: SavedRideSource): String {
    return when (source) {
        SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT -> "Oferta salva"
        SavedRideSource.MANUAL_ENTRY -> "Manual"
        SavedRideSource.PRIVATE_RIDE -> "Corrida particular"
    }
}

private fun filterTrackingRecords(
    records: List<TrackingRecord>,
    period: RecordsPeriodFilter,
    nowMs: Long = System.currentTimeMillis()
): List<TrackingRecord> {
    val zoneId = ZoneId.systemDefault()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
    return records.filter { record ->
        val date = Instant.ofEpochMilli(record.endedAtMs).atZone(zoneId).toLocalDate()
        when (period) {
            RecordsPeriodFilter.DAY -> date == nowDate
            RecordsPeriodFilter.WEEK -> {
                val startOfWeek = nowDate.minus((nowDate.dayOfWeek.value - 1).toLong(), ChronoUnit.DAYS)
                !date.isBefore(startOfWeek) && !date.isAfter(nowDate)
            }
            RecordsPeriodFilter.MONTH -> date.year == nowDate.year && date.month == nowDate.month
        }
    }
}

internal fun recordsDisplayRides(rides: List<SavedRide>): List<SavedRide> {
    return rides.filterNot { it.source == SavedRideSource.PRIVATE_RIDE }
}

private fun createSavedRideFromPrivateTracking(record: TrackingRecord): SavedRide {
    val amount = record.amount ?: 0.0
    return SavedRide(
        id = java.util.UUID.randomUUID().toString(),
        sourceSeenOfferId = record.id,
        platform = RidePlatform.UNKNOWN,
        price = amount,
        valuePerKm = null,
        pickupDistanceKm = null,
        pickupTimeMin = null,
        tripDistanceKm = record.distanceKm,
        tripTimeMin = record.durationSeconds / 60.0,
        totalDistanceKm = record.distanceKm,
        estimatedTotalTimeMin = record.durationSeconds / 60.0,
        productName = "Corrida particular",
        originPreview = null,
        destinationPreview = null,
        acceptedAtMs = record.endedAtMs,
        createdAtMs = record.createdAtMs,
        updatedAtMs = record.createdAtMs,
        source = SavedRideSource.PRIVATE_RIDE
    )
}

@Composable
private fun darkInputColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = KmOnePalette.CardAlt,
    unfocusedContainerColor = KmOnePalette.CardAlt,
    disabledContainerColor = KmOnePalette.CardAlt,
    focusedTextColor = KmOnePalette.TextPrimary,
    unfocusedTextColor = KmOnePalette.TextPrimary,
    disabledTextColor = KmOnePalette.TextSecondary,
    cursorColor = KmOnePalette.Neon,
    focusedLabelColor = KmOnePalette.TextSecondary,
    unfocusedLabelColor = KmOnePalette.TextSecondary,
    focusedBorderColor = KmOnePalette.Neon,
    unfocusedBorderColor = KmOnePalette.ElectricBlue.copy(alpha = 0.5f)
)

private fun isValidPositiveOrBlank(value: String): Boolean {
    if (value.isBlank()) return true
    return parseLocalizedDouble(value)?.let { it > 0.0 } == true
}

private fun isValidNonNegativeOrBlank(value: String): Boolean {
    if (value.isBlank()) return true
    return parseLocalizedDouble(value)?.let { it >= 0.0 } == true
}

private fun parsePositiveOrNull(value: String): Double? {
    return value.trim().takeIf { it.isNotEmpty() }?.let(::parseLocalizedDouble)?.takeIf { it > 0.0 }
}

private fun parseNonNegativeOrNull(value: String): Double? {
    return value.trim().takeIf { it.isNotEmpty() }?.let(::parseLocalizedDouble)?.takeIf { it >= 0.0 }
}

private fun normalizeOptionalText(value: String): String? {
    return value.trim().takeIf { it.isNotEmpty() }
}

private fun parseLocalizedDouble(value: String): Double? {
    return value.trim().replace(",", ".").toDoubleOrNull()
}

private fun formatNumberInput(value: Double): String {
    return String.format(Locale.US, "%.2f", value).replace(".", ",")
}

private fun formatDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value).replace(".", ",")
}

private fun recalculateValuePerKm(ride: SavedRide): Double? {
    return RideEconomicsCalculator.resolveRideEconomics(
        platform = ride.platform,
        price = ride.price,
        explicitValuePerKm = ride.valuePerKm,
        totalDistanceKm = ride.totalDistanceKm,
        pickupDistanceKm = ride.pickupDistanceKm,
        tripDistanceKm = ride.tripDistanceKm
    ).valuePerKm
}

private fun resolvedSeenOfferEconomics(offer: SeenOffer): ResolvedRideEconomics {
    val normalized = com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferConsistencyAuditor()
        .audit(offer)
        .normalizedOffer
    return RideEconomicsCalculator.resolveRideEconomics(
        platform = normalized.platform,
        price = normalized.price,
        explicitValuePerKm = normalized.valuePerKm,
        totalDistanceKm = normalized.totalDistanceKm,
        pickupDistanceKm = normalized.pickupDistanceKm,
        tripDistanceKm = normalized.tripDistanceKm
    )
}

private fun resolvedSeenOfferDistanceKm(offer: SeenOffer): Double? {
    return resolvedSeenOfferEconomics(offer).totalDistanceKm
}

private fun resolvedSeenOfferValuePerKm(offer: SeenOffer): Double? {
    return resolvedSeenOfferEconomics(offer).valuePerKm
}

private fun seenOfferPerKmLabel(offer: SeenOffer): String {
    return resolvedSeenOfferValuePerKm(offer)?.let(::formatPerKm) ?: "R$/km —"
}

private fun resolvedSavedRideValuePerKm(ride: SavedRide): Double? {
    return resolvedSavedRideEconomics(ride).valuePerKm
}

private fun resolvedSavedRideEconomics(ride: SavedRide): ResolvedRideEconomics {
    return RideEconomicsCalculator.resolveRideEconomics(
        platform = ride.platform,
        price = ride.price,
        explicitValuePerKm = ride.valuePerKm,
        totalDistanceKm = ride.totalDistanceKm,
        pickupDistanceKm = ride.pickupDistanceKm,
        tripDistanceKm = ride.tripDistanceKm
    )
}

private fun savedRidePerKmLabel(ride: SavedRide): String {
    return resolvedSavedRideValuePerKm(ride)?.let(::formatPerKm) ?: "R$/km —"
}

private fun parseManualRidePlatform(value: String): RidePlatform {
    return when (value.trim().lowercase(Locale.ROOT)) {
        "uber" -> RidePlatform.UBER
        "99" -> RidePlatform.NINETY_NINE
        else -> RidePlatform.UNKNOWN
    }
}

private data class QuickAction(
    val label: String,
    val onClick: () -> Unit
)

private fun emptyHomeSummary(): HomeDailySummary {
    return HomeDailySummary(
        dailyGoal = HomeDailySummaryProvider.DEFAULT_FALLBACK_DAILY_GOAL_BRL,
        goalSource = HomeGoalSource.FALLBACK,
        earnedToday = 0.0,
        remainingToGoal = HomeDailySummaryProvider.DEFAULT_FALLBACK_DAILY_GOAL_BRL,
        progressPercent = 0,
        progressFraction = 0f,
        acceptedRidesCount = 0,
        seenOffersCount = 0,
        totalKmToday = null,
        averageValuePerKm = null,
        paidRideKmToday = null,
        displacementKmToday = 0.0,
        operationalKmToday = null,
        paidRideValuePerKm = null,
        operationalValuePerKm = null,
        bestRideValuePerKm = null,
        bestRidePrice = null,
        bestRideProductName = null,
        isGoalReached = false
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewKmOneApp() {
    KMONEMotorTheme(dynamicColor = false) {
        KmOneApp(
            debugStateOverride = RadarDebugState(
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
                ninetyNineOperationalState = OperationalAppState.NINETY_NINE_FOREGROUND_ACTIVE_HINT
            )
        )
    }
}
