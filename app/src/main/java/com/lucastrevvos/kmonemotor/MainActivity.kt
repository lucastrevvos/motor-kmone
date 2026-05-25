package com.lucastrevvos.kmonemotor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestBus
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
import com.lucastrevvos.kmonemotor.ui.theme.KMONEMotorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KMONEMotorTheme(dynamicColor = false) {
                val debugState by RadarDebugStore.state.collectAsStateWithLifecycle()
                KmOneApp(debugState = debugState)
            }
        }
    }
}

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
    val Background = Color(0xFF07111B)
    val BackgroundDeep = Color(0xFF040B13)
    val Card = Color(0xCC111C29)
    val CardAlt = Color(0xFF132234)
    val Neon = Color(0xFF5BFF9A)
    val NeonSoft = Color(0xFF39C97A)
    val ElectricBlue = Color(0xFF5AA8FF)
    val Line = Color(0x6638FF97)
    val TextPrimary = Color(0xFFF4F8FF)
    val TextSecondary = Color(0xFF9FB3C8)
    val Positive = Color(0xFF52D67A)
    val Attention = Color(0xFFFFC857)
    val Negative = Color(0xFFFF6F7D)
    val Neutral = Color(0xFF7D93A8)
}

private data class HomeUiState(
    val summary: HomeDailySummary = emptyHomeSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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
}

@Composable
private fun KmOneApp(debugState: RadarDebugState) {
    val context = LocalContext.current
    val module = remember { SeenOfferRuntime.get(context) }
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
    var ridesLoading by remember { mutableStateOf(true) }
    var ridesError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf(HomeUiState(isLoading = true)) }
    var driverSettings by remember { mutableStateOf(DriverSettings()) }
    var debugExporting by remember { mutableStateOf(false) }
    var debugExportMessage by remember { mutableStateOf<String?>(null) }
    var showLaunchBrand by remember { mutableStateOf(true) }

    suspend fun reloadSeenOffers(): List<SeenOffer> {
        seenState = seenState.copy(isLoading = true, errorMessage = null)
        return runCatching {
            withContext(Dispatchers.IO) {
                module.seenOfferRepository.listSeenOffers(limit = 200)
            }
        }.onSuccess { offers ->
            seenState = SeenOffersUiState(offers = offers, isLoading = false, errorMessage = null)
        }.onFailure { error ->
            seenState = SeenOffersUiState(isLoading = false, errorMessage = error.message ?: "Falha ao carregar ofertas")
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun reloadSavedRides(): List<SavedRide> {
        ridesLoading = true
        ridesError = null
        return runCatching {
            withContext(Dispatchers.IO) {
                module.savedRideRepository.listSavedRides(limit = 100)
            }
        }.onSuccess { rides ->
            savedRides = rides
            ridesLoading = false
        }.onFailure { error ->
            savedRides = emptyList()
            ridesLoading = false
            ridesError = error.message ?: "Falha ao carregar registros"
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

    suspend fun reloadDriverSettings(): DriverSettings {
        return withContext(Dispatchers.IO) {
            module.driverSettingsRepository.getSettings()
        }.also {
            driverSettings = it
        }
    }

    fun refreshAll() {
        coroutineScope.launch {
            homeState = homeState.copy(isLoading = true, errorMessage = null)
            reloadDriverSettings()
            val offers = reloadSeenOffers()
            val rides = reloadSavedRides()
            reloadFuelEntries()
            val summary = homeSummaryProvider.summarize(
                seenOffers = offers,
                savedRides = rides
            )
            homeState = HomeUiState(
                summary = summary,
                isLoading = false,
                errorMessage = seenState.errorMessage ?: ridesError
            )
        }
    }

    LaunchedEffect(Unit) {
        val permissionGranted = Settings.canDrawOverlays(context)
        RadarDebugStore.updatePiuOverlayState(
            permissionGranted = permissionGranted,
            showing = overlayController.isShowing(),
            x = debugState.piuLastX
        )
        refreshAll()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.HOME) {
            refreshAll()
        }
    }

    LaunchedEffect(Unit) {
        delay(1000L)
        showLaunchBrand = false
    }

    if (showLaunchBrand) {
        KmOneLaunchBrandScreen()
        return
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
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(KmOnePalette.Background, KmOnePalette.BackgroundDeep)
                    )
                )
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.HOME -> HomeDashboardFinalTab(
                    debugState = debugState,
                    homeState = homeState,
                    onOpenAccessibility = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onRequestOverlayPermission = {
                        RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_OVERLAY_PERMISSION_REQUESTED")
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    onShowPiu = {
                        val shown = overlayController.show()
                        RadarDebugStore.updatePiuOverlayState(
                            permissionGranted = Settings.canDrawOverlays(context),
                            showing = shown,
                            x = debugState.piuLastX
                        )
                    },
                    onHidePiu = {
                        overlayController.hide()
                        RadarDebugStore.updatePiuOverlayState(
                            permissionGranted = Settings.canDrawOverlays(context),
                            showing = false,
                            x = debugState.piuLastX
                        )
                    },
                    onManualAnalyze = {
                        val requested = ManualAnalysisRequestBus.requestAnalysis(
                            source = "home_manual_action",
                            clickedAtMs = System.currentTimeMillis()
                        )
                        if (!requested) {
                            RadarLogger.w(
                                "KM_V2_OVERLAY",
                                "KM_V2_PIU_ERROR",
                                "message" to "manual_analysis_listener_not_registered"
                            )
                        }
                    },
                    onConfigureGoal = { selectedTab = AppTab.CONFIG }
                )

                AppTab.SEEN -> SeenOffersTab(
                    state = seenState,
                    onRefresh = { refreshAll() },
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
                                }
                            }
                            reloadSeenOffers()
                            reloadSavedRides()
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
                            reloadSeenOffers()
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
                            reloadSeenOffers()
                        }
                    }
                )

                AppTab.RIDES -> RecordsTab(
                    rides = savedRides,
                    fuelEntries = fuelEntries,
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
                                }
                            }
                            val rides = reloadSavedRides()
                            homeState = homeState.copy(
                                summary = homeSummaryProvider.summarize(
                                    seenOffers = seenState.offers,
                                    savedRides = rides
                                )
                            )
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
                            reloadSavedRides()
                            homeState = homeState.copy(
                                summary = homeSummaryProvider.summarize(
                                    seenOffers = seenState.offers,
                                    savedRides = savedRides
                                )
                            )
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
                    }
                )

                AppTab.CONFIG -> ConfigTab(
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
            HomeBrandHeader()
        }
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
                    value = homeState.summary.totalKmToday?.let(::formatKmCompact) ?: "--",
                    accent = KmOnePalette.Neon
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Media",
                    value = homeState.summary.averageValuePerKm?.let(::formatPerKm) ?: "--",
                    accent = KmOnePalette.Attention,
                    valueStyle = MaterialTheme.typography.titleLarge
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Abastecido",
                    value = "--",
                    accent = KmOnePalette.ElectricBlue
                )
            }
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
private fun KmOneLaunchBrandScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        KmOnePalette.BackgroundDeep,
                        KmOnePalette.Background,
                        Color(0xFF0C1E17)
                    )
                )
            ),
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
private fun HomeBrandHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = "KM One",
                background = Color(0x1F5BFF9A),
                textColor = KmOnePalette.Neon
            )
            Spacer(modifier = Modifier.width(12.dp))
            Image(
                painter = painterResource(id = R.drawable.kmone_logo_full),
                contentDescription = "Marca KM One",
                modifier = Modifier.height(30.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun SeenOffersTab(
    state: SeenOffersUiState,
    onRefresh: () -> Unit,
    onSave: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onClearPending: (List<String>) -> Unit
) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(
            title = "Ofertas vistas",
            subtitle = "Decida o que fazer com as ofertas pendentes."
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KmOnePalette.Neon)
            }

            state.errorMessage != null -> EmptyState(
                title = "Nao foi possivel carregar",
                message = state.errorMessage,
                actionLabel = "Tentar novamente",
                onAction = onRefresh
            )

            filteredOffers.isEmpty() -> EmptyState(
                title = "Nenhuma oferta pendente",
                message = "As proximas ofertas detectadas pelo KM One aparecerao aqui.",
                actionLabel = "Atualizar",
                onAction = onRefresh
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

@Composable
private fun RecordsTab(
    rides: List<SavedRide>,
    fuelEntries: List<FuelEntry>,
    isLoading: Boolean,
    error: String?,
    recordsSummaryProvider: RecordsSummaryProvider,
    onRefresh: () -> Unit,
    onCreateFuelEntry: (FuelEntry) -> Unit,
    onSaveEditedFuelEntry: (FuelEntry) -> Unit,
    onSaveEditedRide: (SavedRide) -> Unit,
    onDeleteRide: (String) -> Unit,
    onDeleteFuelEntry: (String) -> Unit
) {
    var period by remember { mutableStateOf(RecordsPeriodFilter.DAY) }
    var expandedItemId by remember(rides, fuelEntries, period) { mutableStateOf<String?>(null) }
    var editingRide by remember { mutableStateOf<SavedRide?>(null) }
    var editingFuelEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var deleteConfirmationRide by remember { mutableStateOf<SavedRide?>(null) }
    var deleteConfirmationFuelEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var showFuelEntryDialog by remember { mutableStateOf(false) }
    var showManualRideDialog by remember { mutableStateOf(false) }
    val filteredRides = remember(rides, period) {
        recordsSummaryProvider.filterRides(rides, period)
    }
    val filteredFuelEntries = remember(fuelEntries, period) {
        recordsSummaryProvider.filterFuelEntries(fuelEntries, period)
    }
    val recordsItems = remember(filteredRides, filteredFuelEntries) {
        (filteredRides.map(RecordsListItem::RideItem) + filteredFuelEntries.map(RecordsListItem::FuelItem))
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
                            message = "Corridas e abastecimentos aparecerao aqui."
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
private fun AnalyzeOfferCta(
    isServiceActive: Boolean,
    onManualAnalyze: () -> Unit
) {
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
                    text = "Analisar oferta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KmOnePalette.TextPrimary
                )
                Text(
                    text = if (isServiceActive) {
                        "Use quando quiser avaliar uma oferta manualmente."
                    } else {
                        "Ative o KM One para usar a leitura manual."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
            }
            Button(
                onClick = onManualAnalyze,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KmOnePalette.Neon,
                    contentColor = KmOnePalette.Background
                )
            ) {
                Text("Analisar agora", fontWeight = FontWeight.Bold)
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
        color = Color(0xE6121E2B),
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
                        .background(if (selected) Color(0x185BFF9A) else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selected) KmOnePalette.Neon else KmOnePalette.CardAlt)
                            .border(1.dp, KmOnePalette.Line, CircleShape),
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
    }
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
                ninetyNineOperationalState = OperationalAppState.NINETY_NINE_FOREGROUND_ACTIVE_HINT
            )
        )
    }
}
