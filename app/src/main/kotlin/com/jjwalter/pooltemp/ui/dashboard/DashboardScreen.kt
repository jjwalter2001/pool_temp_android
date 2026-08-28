package com.jjwalter.pooltemp.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jjwalter.pooltemp.notification.NotificationPublisher
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.HistoryPoint
import com.jjwalter.pooltemp.data.LightningState
import com.jjwalter.pooltemp.data.Reading
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.data.SwitchState
import com.jjwalter.pooltemp.data.Weather
import com.jjwalter.pooltemp.ui.theme.Danger
import com.jjwalter.pooltemp.ui.theme.HeaterOff
import com.jjwalter.pooltemp.ui.theme.HeaterOn
import com.jjwalter.pooltemp.ui.theme.PoolAccent
import com.jjwalter.pooltemp.ui.theme.PoolAccentDim
import com.jjwalter.pooltemp.ui.theme.PoolOnSurface
import com.jjwalter.pooltemp.ui.theme.ChemAccent
import com.jjwalter.pooltemp.ui.theme.ChemLow
import com.jjwalter.pooltemp.ui.theme.PoolOnSurfaceMuted
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val AUTO_REFRESH_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    settings: Settings,
    onOpenSettings: () -> Unit,
    onOpenChemistry: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(settings))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Keep the ongoing notification in sync with what's on screen. The periodic
    // RefreshWorker only fires every 30 min, so without this an arm/disarm (or
    // heater toggle) would leave the shade showing a stale value until the next
    // background run. Fires once per successful fetch (lastFetchedMs bumps),
    // which includes the refresh() that follows every control action.
    LaunchedEffect(state.lastFetchedMs) {
        if (state.lastFetchedMs != null && settings.current().notificationsEnabled) {
            NotificationPublisher.postStatus(
                context, state.readings, state.switch, state.lightning,
            )
        }
    }

    // Auto-refresh every minute while the screen is in composition. Pauses
    // automatically when the user navigates to Settings.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_MS)
            vm.refresh()
        }
    }

    // Surface errors as a snackbar without dropping out of the dashboard.
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    var pendingToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingArm by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pool Temp") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Danger,
                )
            }
        },
    ) { padding: PaddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.initialLoading -> InitialLoading()
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { LastUpdated(state.lastFetchedMs) }
                        state.switch?.let { sw ->
                            if (sw.state != null) {
                                item {
                                    HeaterCard(
                                        switch = sw,
                                        pending = state.heaterPending,
                                        onToggle = { pendingToggle = it },
                                    )
                                }
                            }
                        }
                        state.lightning?.let { lt ->
                            if (lt.armed != null) {
                                item {
                                    LightningCard(
                                        lightning = lt,
                                        pending = state.lightningPending,
                                        onToggle = { pendingArm = it },
                                    )
                                }
                            }
                        }
                        state.chem?.let { c ->
                            item { ChemistryCard(chem = c, onOpen = onOpenChemistry) }
                        }
                        if (state.readings.isNotEmpty()) {
                            items(state.readings, key = { it.deviceId }) { r ->
                                ReadingCard(r, history = state.histories[r.deviceId])
                            }
                        }
                        state.weather?.let { w ->
                            item { WeatherCard(w) }
                        }
                    }
                }
            }
        }
    }

    pendingToggle?.let { wantOn ->
        AlertDialog(
            onDismissRequest = { pendingToggle = null },
            title = {
                Text(if (wantOn) "Turn pool heater ON?" else "Turn pool heater OFF?")
            },
            text = {
                Text(
                    if (wantOn)
                        "This sends the ON command to the Tuya switch. Your name will be logged on the event."
                    else
                        "This sends the OFF command to the Tuya switch. Your name will be logged on the event."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingToggle = null
                    vm.setHeater(wantOn)
                }) {
                    Text(if (wantOn) "Turn ON" else "Turn OFF")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingToggle = null }) { Text("Cancel") }
            },
        )
    }

    pendingArm?.let { wantArmed ->
        AlertDialog(
            onDismissRequest = { pendingArm = null },
            title = {
                Text(if (wantArmed) "Arm lightning alert?" else "Disarm lightning alert?")
            },
            text = {
                Text(
                    if (wantArmed)
                        "Arms the pool lightning-alert system in Home Assistant. " +
                            "It will announce a warning over the pool speakers when lightning is detected nearby."
                    else
                        "Disarms the pool lightning-alert system in Home Assistant. " +
                            "No lightning warnings will play over the pool speakers until it's armed again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingArm = null
                    vm.setLightning(wantArmed)
                }) {
                    Text(if (wantArmed) "Arm" else "Disarm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InitialLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PoolAccent)
            Spacer(Modifier.height(12.dp))
            Text(
                "Connecting to your pool…",
                style = MaterialTheme.typography.bodyMedium,
                color = PoolOnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun LastUpdated(ms: Long?) {
    val text = ms?.let { agoString(System.currentTimeMillis() - it) } ?: "—"
    Text(
        "Last updated $text",
        style = MaterialTheme.typography.labelLarge,
        color = PoolOnSurfaceMuted,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
    )
}

@Composable
private fun HeaterCard(
    switch: SwitchState,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val on = switch.state == 1
    val accent = if (on) HeaterOn else HeaterOff

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pool Heater",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurface,
                )
                Spacer(Modifier.width(12.dp))
                StatusPill(text = if (on) "ON" else "OFF", color = accent)
            }
            Spacer(Modifier.height(8.dp))
            val since = switch.ts?.let { sinceString(it) } ?: "—"
            val who = switch.user?.takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""
            Text(
                "${if (on) "Running" else "Idle"} since $since$who",
                style = MaterialTheme.typography.bodyMedium,
                color = PoolOnSurfaceMuted,
            )
            Spacer(Modifier.height(16.dp))
            if (on) {
                OutlinedButton(
                    onClick = { onToggle(false) },
                    enabled = !pending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pending) "Sending…" else "Turn OFF")
                }
            } else {
                Button(
                    onClick = { onToggle(true) },
                    enabled = !pending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeaterOn,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pending) "Sending…" else "Turn ON")
                }
            }
        }
    }
}

@Composable
private fun LightningCard(
    lightning: LightningState,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val armed = lightning.armed == true
    val accent = if (armed) PoolAccent else HeaterOff

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Thunderstorm,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Lightning Alert",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurface,
                )
                Spacer(Modifier.width(12.dp))
                StatusPill(text = if (armed) "ARMED" else "OFF", color = accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (armed)
                    "Watching for nearby lightning; will warn over the pool speakers."
                else
                    "Disarmed — no lightning warnings will play.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoolOnSurfaceMuted,
            )
            Spacer(Modifier.height(16.dp))
            if (armed) {
                OutlinedButton(
                    onClick = { onToggle(false) },
                    enabled = !pending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pending) "Sending…" else "Disarm")
                }
            } else {
                Button(
                    onClick = { onToggle(true) },
                    enabled = !pending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PoolAccent,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pending) "Sending…" else "Arm")
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(r: Reading, history: List<HistoryPoint>?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = PoolAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    r.name ?: r.deviceId,
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurface,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatTempF(r.temperatureF),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PoolAccent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "°F",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolAccentDim,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            r.temperatureC?.let {
                Text(
                    "${String.format(Locale.US, "%.1f", it)} °C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoolOnSurfaceMuted,
                )
            }
            Sparkline24h(history = history)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                r.humidity?.let {
                    SubMetric("Humidity", "${String.format(Locale.US, "%.0f", it)}%")
                }
                r.battery?.let {
                    SubMetric("Battery", "$it%")
                }
                r.lastUpdateAgo?.let {
                    SubMetric("Updated", agoString(it * 1000L))
                }
            }
        }
    }
}

/**
 * 24-hour temperature trace. Shows a line + light fill of tempF over the
 * window, with the value range labeled underneath. Renders blank space at
 * the same height when history is null (loading) so the card doesn't reflow
 * once data arrives.
 */
@Composable
private fun Sparkline24h(history: List<HistoryPoint>?) {
    Spacer(Modifier.height(10.dp))
    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        if (history == null) return@Box  // pre-load placeholder
        val temps = history.mapNotNull { it.tempF }
        if (temps.size < 2) return@Box   // not enough data yet
        val minT = temps.min()
        val maxT = temps.max()
        val range = (maxT - minT).takeIf { it > 0.01 } ?: 1.0

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stepX = if (temps.size > 1) w / (temps.size - 1) else w

            // Build the line path once; the fill path reuses it + closes
            // the bottom edge.
            val linePath = Path()
            temps.forEachIndexed { i, t ->
                val x = i * stepX
                val y = h - ((t - minT) / range * h).toFloat()
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fillPath, color = PoolAccent.copy(alpha = 0.18f))
            drawPath(linePath, color = PoolAccent, style = Stroke(width = 2.5f))
        }
    }
    val temps = history?.mapNotNull { it.tempF }.orEmpty()
    if (temps.size >= 2) {
        Spacer(Modifier.height(4.dp))
        Text(
            "24h: ${String.format(Locale.US, "%.1f", temps.min())} – " +
                "${String.format(Locale.US, "%.1f", temps.max())} °F",
            style = MaterialTheme.typography.bodyMedium,
            color = PoolOnSurfaceMuted,
        )
    }
}

@Composable
private fun WeatherCard(w: Weather) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = HeaterOn,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Weather",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatTempF(w.airTempF),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PoolOnSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "°F",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurfaceMuted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                w.humidity?.let {
                    SubMetric("Humidity", "${String.format(Locale.US, "%.0f", it)}%")
                }
                w.windAvgMph?.let {
                    SubMetric("Wind", "${String.format(Locale.US, "%.1f", it)} mph")
                }
                w.solarRadiation?.let {
                    SubMetric("Solar", "${it.toInt()} W/m²")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SubMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = PoolOnSurfaceMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = PoolOnSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatTempF(f: Double?): String =
    if (f == null) "—" else String.format(Locale.US, "%.1f", f)

private fun agoString(deltaMs: Long): String {
    val s = (deltaMs / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

private val sinceFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

private fun sinceString(unixSec: Long): String =
    Instant.ofEpochSecond(unixSec).atZone(ZoneId.systemDefault()).format(sinceFmt)

@Composable
private fun ChemistryCard(chem: ChemLatest, onOpen: () -> Unit) {
    val days = chem.daysSince
    // Days since the last test is the number that actually changes behavior:
    // the real failure mode here is a three week gap, not a bad reading.
    val overdue = days != null && days >= 7
    val accent = if (overdue) ChemLow else ChemAccent

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Science,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Water Chemistry",
                    style = MaterialTheme.typography.titleLarge,
                    color = PoolOnSurface,
                )
                Spacer(Modifier.width(12.dp))
                if (overdue) StatusPill(text = "DUE", color = ChemLow)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    chem.reading == null -> "No tests logged yet. Tap to add one."
                    days == null -> "Last test recorded."
                    days == 0 -> "Tested today."
                    days == 1 -> "Tested yesterday."
                    else -> "Tested $days days ago."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PoolOnSurfaceMuted,
            )
            // Only what is still outstanding: an action already added or
            // skipped is not something to do.
            if (chem.pendingCount > 0) {
                Spacer(Modifier.height(8.dp))
                val n = chem.pendingCount
                Text(
                    if (n == 1) "1 thing to add" else "$n things to add",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
            }
        }
    }
}
