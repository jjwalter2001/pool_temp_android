package com.jjwalter.pooltemp.ui.chemistry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jjwalter.pooltemp.data.ChemAction
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.ui.theme.ChemAccent
import com.jjwalter.pooltemp.ui.theme.ChemHigh
import com.jjwalter.pooltemp.ui.theme.ChemLow
import com.jjwalter.pooltemp.ui.theme.ChemOk
import com.jjwalter.pooltemp.ui.theme.ChemUnknown
import com.jjwalter.pooltemp.ui.theme.PoolOnSurface
import com.jjwalter.pooltemp.ui.theme.PoolOnSurfaceMuted

private val PARAM_LABELS = listOf(
    "ph" to "pH",
    "fc" to "Free Cl",
    "ta" to "Alk",
    "cya" to "CYA",
    "ch" to "Calcium",
    "salt" to "Salt",
)

private fun statusColor(status: String?): Color = when (status) {
    "ok" -> ChemOk
    "low" -> ChemLow
    "high" -> ChemHigh
    else -> ChemUnknown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChemistryScreen(
    settings: Settings,
    onBack: () -> Unit,
) {
    val vm: ChemistryViewModel = viewModel(factory = ChemistryViewModelFactory(settings))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDose by remember { mutableStateOf<ChemAction?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Chemistry") },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (state.loading && state.latest == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ChemAccent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.latest?.let { latest ->
                StatusCard(latest, state.config)
                if (latest.actions.isNotEmpty() ||
                    latest.blocked.isNotEmpty() ||
                    latest.warnings.isNotEmpty()
                ) {
                    ActionsCard(
                        latest = latest,
                        labels = state.productLabels,
                        logged = state.doseLogged,
                        onLog = { confirmDose = it },
                    )
                }
            }

            EntryCard(vm = vm, state = state)
            Spacer(Modifier.height(8.dp))
        }
    }

    // Logging a dose writes to the calibration training set, so it gets the
    // same confirm step as the heater and lightning controls.
    confirmDose?.let { action ->
        val label = state.productLabels[action.product] ?: action.product.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDose = null },
            title = { Text("Record this addition?") },
            text = {
                Text(
                    "Logs ${fmtAmount(action.amount)} ${action.unit.orEmpty()} of $label as " +
                        "actually added. This is what teaches the app how your " +
                        "products behave, so only confirm once it is in the water.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.logDose(action, state.latest?.readingId)
                    confirmDose = null
                }) { Text("Record") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDose = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(latest: ChemLatest, config: Map<String, String>) {
    val overdueAfter = config["overdue_days"]?.toIntOrNull() ?: 7
    val days = latest.daysSince
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (latest.reading == null) {
                Text("No tests logged yet", style = MaterialTheme.typography.titleMedium, color = PoolOnSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add your first below and you will get dosing recommendations straight away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoolOnSurfaceMuted,
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (days) {
                        null -> "Last test"
                        0 -> "Tested today"
                        1 -> "Tested yesterday"
                        else -> "Tested $days days ago"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (days != null && days >= overdueAfter) ChemLow else PoolOnSurface,
                )
                if (days != null && days >= overdueAfter) {
                    Spacer(Modifier.width(8.dp))
                    Pill("DUE", ChemLow)
                }
            }
            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PARAM_LABELS.forEach { (key, label) ->
                    val st = latest.status[key]
                    Pill("$label ${valueFor(latest, key)}", statusColor(st))
                }
            }

            latest.lsi?.let { lsi ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "Saturation index ${lsi.value} (${lsi.band}). ${lsi.note.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PoolOnSurfaceMuted,
                )
            }
        }
    }
}

/** Censored readings print the limit that was hit, not a number, because the
 *  kit could not resolve one. */
private fun valueFor(latest: ChemLatest, key: String): String {
    val r = latest.reading ?: return ""
    return when (key) {
        "ph" -> if (r.phBelow7 == 1) "<7" else r.ph?.toString() ?: "-"
        "fc" -> if (r.fcOver == 1) ">5" else r.fc?.toString() ?: "-"
        "ta" -> r.ta?.toString() ?: "-"
        "cya" -> if (r.cyaBelow30 == 1) "<30" else r.cya?.toString() ?: "-"
        "ch" -> r.ch?.toString() ?: "-"
        "salt" -> r.salt?.toString() ?: "-"
        else -> "-"
    }
}

@Composable
private fun ActionsCard(
    latest: ChemLatest,
    labels: Map<String, String>,
    logged: Set<Int>,
    onLog: (ChemAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("What to add", style = MaterialTheme.typography.titleMedium, color = PoolOnSurface)
            Spacer(Modifier.height(12.dp))

            latest.actions.forEach { a ->
                val label = labels[a.product] ?: a.product.orEmpty()
                val headline = when {
                    a.swgPct != null -> "Set the cell to ${a.swgPct}%"
                    a.amount != null -> "${fmtAmount(a.amount)} ${a.unit.orEmpty()} $label"
                    else -> label.ifBlank { a.reason }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(headline, style = MaterialTheme.typography.bodyLarge, color = PoolOnSurface, fontWeight = FontWeight.SemiBold)
                        Text(a.reason, style = MaterialTheme.typography.bodySmall, color = PoolOnSurfaceMuted)
                        if (a.waitMinutes > 0) {
                            Text(
                                "Wait ${a.waitMinutes / 60} h first",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChemLow,
                            )
                        }
                        a.note?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = PoolOnSurfaceMuted)
                        }
                    }
                    if (a.product != null && a.amount != null) {
                        Spacer(Modifier.width(8.dp))
                        if (logged.contains(a.order)) {
                            Text("Recorded", style = MaterialTheme.typography.labelMedium, color = ChemOk)
                        } else {
                            OutlinedButton(onClick = { onLog(a) }) { Text("Added") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            latest.blocked.forEach { b ->
                Surface(
                    color = ChemLow.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        b.reason,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ChemLow,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            latest.warnings.forEach { w ->
                Surface(
                    color = ChemHigh.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        w,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ChemHigh,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EntryCard(vm: ChemistryViewModel, state: ChemistryViewModel.UiState) {
    val f = state.form
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Log a test", style = MaterialTheme.typography.titleMedium, color = PoolOnSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Leave anything you did not test blank.",
                style = MaterialTheme.typography.bodySmall,
                color = PoolOnSurfaceMuted,
            )
            Spacer(Modifier.height(16.dp))

            CensorableField(
                label = "pH",
                value = f.ph.text,
                onValue = vm::setPh,
                censorLabel = "Below 7",
                censored = f.ph.censored,
                onCensor = vm::setPhBelow7,
                decimal = true,
            )
            CensorableField(
                label = "Free chlorine",
                value = f.fc.text,
                onValue = vm::setFc,
                censorLabel = "Over 5",
                censored = f.fc.censored,
                onCensor = vm::setFcOver,
            )
            CensorableField(
                label = "Total chlorine",
                value = f.tc.text,
                onValue = vm::setTc,
                censorLabel = "Over 5",
                censored = f.tc.censored,
                onCensor = vm::setTcOver,
            )
            CensorableField(
                label = "Cyanuric acid",
                value = f.cya.text,
                onValue = vm::setCya,
                censorLabel = "Below 30",
                censored = f.cya.censored,
                onCensor = vm::setCyaBelow30,
            )
            PlainField("Alkalinity", f.ta, vm::setTa)
            PlainField("Calcium hardness", f.ch, vm::setCh)
            PlainField("Salt (cell display)", f.salt, vm::setSalt)
            PlainField("SWG output %", f.swg, vm::setSwg)
            NoteField(value = f.note, onValue = vm::setNote)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChemAccent,
                    contentColor = Color.Black,
                ),
            ) {
                Text(if (state.saving) "Saving…" else "Save test")
            }
        }
    }
}

@Composable
private fun CensorableField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    censorLabel: String,
    censored: Boolean,
    onCensor: (Boolean) -> Unit,
    decimal: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            enabled = !censored,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = censored,
                onCheckedChange = onCensor,
                colors = CheckboxDefaults.colors(checkedColor = ChemAccent),
                modifier = Modifier.size(40.dp),
            )
            Text(censorLabel, style = MaterialTheme.typography.bodySmall, color = PoolOnSurfaceMuted)
        }
    }
}

@Composable
private fun PlainField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun NoteField(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Note") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Doses print as whole numbers when they are whole; "96 oz" not "96.0 oz". */
private fun fmtAmount(amount: Double?): String {
    if (amount == null) return ""
    return if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format("%.1f", amount)
}
