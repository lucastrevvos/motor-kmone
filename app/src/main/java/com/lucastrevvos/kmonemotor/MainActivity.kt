package com.lucastrevvos.kmonemotor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.seenoffers.RidePlatform
import com.lucastrevvos.kmonemotor.radar.seenoffers.SavedRide
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffer
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferRuntime
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferStatus
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffersUiState
import com.lucastrevvos.kmonemotor.ui.theme.KMONEMotorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    ALL("Todas"),
    SEEN("Vistas"),
    ACCEPTED("Aceitas"),
    REJECTED("Recusadas"),
    IGNORED("Ignoradas")
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

@Composable
private fun KmOneApp(debugState: RadarDebugState) {
    val context = LocalContext.current
    val module = remember { SeenOfferRuntime.get(context) }
    val overlayController = remember { PiuOverlayRuntime.get(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var selectedFilter by remember { mutableStateOf(SeenFilter.ALL) }
    var seenState by remember { mutableStateOf(SeenOffersUiState(isLoading = true)) }
    var savedRides by remember { mutableStateOf<List<SavedRide>>(emptyList()) }
    var ridesLoading by remember { mutableStateOf(true) }
    var ridesError by remember { mutableStateOf<String?>(null) }

    suspend fun reloadSeenOffers() {
        seenState = seenState.copy(isLoading = true, errorMessage = null)
        runCatching {
            withContext(Dispatchers.IO) {
                module.seenOfferRepository.listSeenOffers(limit = 200)
            }
        }.onSuccess { offers ->
            seenState = SeenOffersUiState(offers = offers, isLoading = false, errorMessage = null)
        }.onFailure { error ->
            seenState = SeenOffersUiState(isLoading = false, errorMessage = error.message ?: "Falha ao carregar ofertas")
        }
    }

    suspend fun reloadSavedRides() {
        ridesLoading = true
        ridesError = null
        runCatching {
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
        }
    }

    fun refreshAll() {
        coroutineScope.launch {
            reloadSeenOffers()
            reloadSavedRides()
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
                AppTab.HOME -> HomeTab(
                    debugState = debugState,
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
                    seenCount = seenState.offers.size,
                    acceptedCount = seenState.offers.count { it.status == SeenOfferStatus.ACCEPTED_MANUALLY },
                    ridesCount = savedRides.size
                )

                AppTab.SEEN -> SeenOffersTab(
                    state = seenState,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    onRefresh = { refreshAll() },
                    onAccept = { offerId ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.manualActions.acceptSeenOfferManually(offerId)
                            }
                            reloadSeenOffers()
                            reloadSavedRides()
                        }
                    },
                    onReject = { offerId ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.manualActions.rejectSeenOfferManually(offerId)
                            }
                            reloadSeenOffers()
                        }
                    },
                    onIgnore = { offerId ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                module.manualActions.ignoreSeenOffer(offerId)
                            }
                            reloadSeenOffers()
                        }
                    }
                )

                AppTab.RIDES -> RecordsTab(
                    rides = savedRides,
                    isLoading = ridesLoading,
                    error = ridesError,
                    onRefresh = { refreshAll() }
                )

                AppTab.CONFIG -> ConfigTab(debugState = debugState)
            }
        }
    }
}

@Composable
private fun HomeTab(
    debugState: RadarDebugState,
    onOpenAccessibility: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onShowPiu: () -> Unit,
    onHidePiu: () -> Unit,
    onManualAnalyze: () -> Unit,
    seenCount: Int,
    acceptedCount: Int,
    ridesCount: Int
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroPanel(
                title = "KM One",
                subtitle = "Monitoramento visual e revisao das ultimas ofertas vistas.",
                statusLabel = if (debugState.serviceActive) "KM One ativo" else "Ativacao pendente"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Ofertas vistas",
                    value = seenCount.toString(),
                    accent = KmOnePalette.Neon
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Aceitas",
                    value = acceptedCount.toString(),
                    accent = KmOnePalette.Positive
                )
                MetricPanel(
                    modifier = Modifier.weight(1f),
                    label = "Registros",
                    value = ridesCount.toString(),
                    accent = KmOnePalette.ElectricBlue
                )
            }
        }
        item {
            CockpitCard(title = "Painel rapido") {
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
        item {
            CockpitCard(title = "Status operacional") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("Servico", if (debugState.serviceActive) "Ativo" else "Inativo")
                    DetailLine("Overlay", if (debugState.piuOverlayShowing) "Visivel" else "Oculto")
                    DetailLine("Permissao", if (debugState.piuOverlayPermissionGranted) "Concedida" else "Pendente")
                    DetailLine("Uber", debugState.uberOperationalState.name.replace('_', ' '))
                    DetailLine("99", debugState.ninetyNineOperationalState.name.replace('_', ' '))
                }
            }
        }
    }
}

@Composable
private fun SeenOffersTab(
    state: SeenOffersUiState,
    selectedFilter: SeenFilter,
    onFilterSelected: (SeenFilter) -> Unit,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onIgnore: (String) -> Unit
) {
    val filteredOffers = remember(state.offers, selectedFilter) {
        state.offers.filter { offer ->
            when (selectedFilter) {
                SeenFilter.ALL -> true
                SeenFilter.SEEN -> offer.status == SeenOfferStatus.SEEN
                SeenFilter.ACCEPTED -> offer.status == SeenOfferStatus.ACCEPTED_MANUALLY
                SeenFilter.REJECTED -> offer.status == SeenOfferStatus.REJECTED_MANUALLY
                SeenFilter.IGNORED -> offer.status == SeenOfferStatus.IGNORED
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(
            title = "Ofertas vistas",
            subtitle = "Revise as ultimas ofertas detectadas pelo KM One."
        )
        FilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected
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
                title = "Nenhuma oferta vista ainda",
                message = "Assim que o KM One detectar ofertas, elas aparecerao aqui.",
                actionLabel = "Atualizar",
                onAction = onRefresh
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredOffers, key = { it.id }) { offer ->
                    SeenOfferCard(
                        offer = offer,
                        onAccept = { onAccept(offer.id) },
                        onReject = { onReject(offer.id) },
                        onIgnore = { onIgnore(offer.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(
    rides: List<SavedRide>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(
            title = "Registros",
            subtitle = "Corridas confirmadas manualmente a partir das ofertas vistas."
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

            rides.isEmpty() -> EmptyState(
                title = "Nenhum registro ainda",
                message = "Quando voce marcar uma oferta como aceita, ela aparecera aqui.",
                actionLabel = "Atualizar",
                onAction = onRefresh
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(rides, key = { it.id }) { ride ->
                    SavedRideCard(ride = ride)
                }
            }
        }
    }
}

@Composable
private fun ConfigTab(debugState: RadarDebugState) {
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
    accent: Color
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
                    Button(
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KmOnePalette.CardAlt,
                            contentColor = KmOnePalette.TextPrimary
                        )
                    ) {
                        Text(action.label)
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
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onIgnore: () -> Unit
) {
    CockpitCard(
        title = offer.productName ?: platformLabel(offer.platform),
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
                    offer.valuePerKm?.let {
                        Text(
                            text = "${formatMoney(it)}/km",
                            style = MaterialTheme.typography.titleMedium,
                            color = KmOnePalette.Neon
                        )
                    }
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
                distance = offer.pickupDistanceKm
            )
            MetricsRow(
                title = "Viagem",
                time = offer.tripTimeMin,
                distance = offer.tripDistanceKm
            )
            offer.totalDistanceKm?.let {
                DetailLine("Total", formatKm(it))
            }
            if (!offer.rawTextPreview.isNullOrBlank()) {
                Text(
                    text = offer.rawTextPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
private fun SavedRideCard(ride: SavedRide) {
    CockpitCard(
        title = ride.productName ?: platformLabel(ride.platform),
        accent = KmOnePalette.ElectricBlue
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusChip(
                    text = platformLabel(ride.platform),
                    background = Color(0x1F5AA8FF),
                    textColor = KmOnePalette.ElectricBlue
                )
                Text(
                    text = formatTimeStamp(ride.acceptedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = KmOnePalette.TextSecondary
                )
            }
            Text(
                text = formatMoney(ride.price),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KmOnePalette.TextPrimary
            )
            ride.valuePerKm?.let { DetailLine("R$/km", "${formatMoney(it)}/km") }
            MetricsRow("Busca", ride.pickupTimeMin, ride.pickupDistanceKm)
            MetricsRow("Viagem", ride.tripTimeMin, ride.tripDistanceKm)
            ride.totalDistanceKm?.let { DetailLine("Total", formatKm(it)) }
        }
    }
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
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
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

private fun formatKm(value: Double): String {
    return String.format(Locale.US, "%.1f km", value).replace(".", ",")
}

private fun formatMinutes(value: Double): String {
    return "${value.toInt()} min"
}

private fun formatTimeStamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
}

private data class QuickAction(
    val label: String,
    val onClick: () -> Unit
)

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
