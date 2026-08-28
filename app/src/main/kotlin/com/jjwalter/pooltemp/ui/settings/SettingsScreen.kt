package com.jjwalter.pooltemp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.notification.Scheduler
import com.jjwalter.pooltemp.ui.theme.Danger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    val cfg by settings.config.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var confirmReset by remember { mutableStateOf(false) }

    // Season state is server config, not device config, so it is fetched
    // rather than read from the DataStore. Null means not loaded or the
    // backend did not answer, and the card stays hidden rather than showing a
    // switch that does nothing.
    var seasonOpen by remember { mutableStateOf<Boolean?>(null) }
    var seasonBusy by remember { mutableStateOf(false) }

    LaunchedEffect(cfg?.isComplete) {
        val c = cfg
        if (c == null || !c.isComplete) return@LaunchedEffect
        seasonOpen = runCatching {
            ApiClient.forConfig(c).chemConfig().config["season_open"]
        }.getOrNull()?.let { it != "0" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding: PaddingValues ->
        val c = cfg ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Connection card ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("Connection")
                    Field("Base URL", c.baseUrl.ifBlank { "—" })
                    Field("CF Access Client ID", c.cfClientId.ifBlank { "—" })
                    Field("CF Access Secret", if (c.cfClientSecret.isBlank()) "—" else "•".repeat(10))
                    Field("API Token", if (c.apiToken.isBlank()) "—" else "•".repeat(10))
                    Field("Your name", c.userName.ifBlank { "—" })
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Edit connection") }
                }
            }

            // ── Notification card ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("Notifications")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Persistent pool temperature",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Updates every 30 min in the notification shade.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = c.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settings.setNotificationsEnabled(enabled)
                                    if (enabled) Scheduler.schedule(context) else Scheduler.cancel(context)
                                }
                            },
                        )
                    }
                }
            }

            // ── Pool card ──
            seasonOpen?.let { open ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Pool")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Pool is open",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "Turning this off stops the overdue-test reminders " +
                                        "for the winter. A covered pool does not need " +
                                        "testing, and a reminder that fires all season " +
                                        "is one you learn to ignore.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = open,
                                enabled = !seasonBusy,
                                onCheckedChange = { wanted ->
                                    val c = cfg ?: return@Switch
                                    seasonBusy = true
                                    scope.launch {
                                        val ok = runCatching {
                                            ApiClient.forConfig(c).postChemConfig(
                                                mapOf(
                                                    "season_open" to
                                                        if (wanted) "1" else "0",
                                                ),
                                            )
                                        }.isSuccess
                                        // Only move the switch if the server took
                                        // it; otherwise it would lie about state
                                        // that lives elsewhere.
                                        if (ok) seasonOpen = wanted
                                        seasonBusy = false
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Danger card ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("Reset")
                    Text(
                        "Wipes saved credentials and cancels the background notification job. The app returns to the first-launch screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Forget configuration", color = Danger)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Forget pool_temp configuration?") },
            text = { Text("Saved credentials will be wiped from this device and the background notification job will stop.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        Scheduler.cancel(context)
                        settings.clear()
                        onReset()
                    }
                }) { Text("Forget", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun Field(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
