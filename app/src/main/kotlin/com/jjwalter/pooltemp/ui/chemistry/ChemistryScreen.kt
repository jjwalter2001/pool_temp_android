package com.jjwalter.pooltemp.ui.chemistry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jjwalter.pooltemp.data.ChemAction
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.data.ChemEntry
import com.jjwalter.pooltemp.data.ChemField
import com.jjwalter.pooltemp.ui.theme.ChemAccent
import com.jjwalter.pooltemp.ui.theme.ChemHigh
import com.jjwalter.pooltemp.ui.theme.ChemLow
import com.jjwalter.pooltemp.ui.theme.ChemOk
import com.jjwalter.pooltemp.ui.theme.ChemUnknown
import com.jjwalter.pooltemp.ui.theme.PoolOnSurface
import com.jjwalter.pooltemp.ui.theme.PoolOnSurfaceMuted
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PARAM_LABELS = listOf(
    "ph" to "pH",
    "fc" to "Free Cl",
    "ta" to "Alk",
    "cya" to "CYA",
    "ch" to "Calcium",
    "salt" to "Salt",
)

private val DATE_ONLY = java.text.SimpleDateFormat("d MMM", Locale.getDefault())

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
    onOpenHistory: () -> Unit,
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
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = "Test history")
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
                        onLog = { confirmDose = it },
                        onSkip = { vm.toggleSkip(it, latest.readingId) },
                    )
                }
            }

            EntryCard(vm = vm, state = state)
            Spacer(Modifier.height(8.dp))
        }
    }

    confirmDose?.let { action ->
        RecordDoseDialog(
            action = action,
            productLabel = state.productLabels[action.product] ?: action.product.orEmpty(),
            onDismiss = { confirmDose = null },
            onConfirm = { amount, onDate ->
                vm.logDose(action, state.latest?.readingId, amount, onDate)
                confirmDose = null
            },
        )
    }
}

/**
 * Confirms a dose and asks how much went in and when.
 *
 * The amount starts at the recommendation but is editable, because what was
 * actually poured is often a judgement call and it is the real figure that
 * calibration has to learn from. The recommendation still goes along with it,
 * so history shows the difference.
 *
 * The timestamp is what calibration pairs are matched on, in hours, so "now"
 * would be wrong every time the pour and the tap happen at different points in
 * the day. Today keeps the real clock time; an earlier date resolves to midday,
 * matching the importer's convention for a date-only row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDoseDialog(
    action: ChemAction,
    productLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, onDate: String?) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var amountText by remember { mutableStateOf(ChemEntry.amountText(action.amount)) }
    val amount = ChemEntry.parseAmount(amountText)
    val unit = action.unit.orEmpty()
    var chosen by remember { mutableStateOf(today) }
    var showPicker by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = chosen
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // A dose cannot have gone in tomorrow; the server rejects it too.
            selectableDates = remember {
                object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis <= today.plusDays(1)
                            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // The picker reports UTC midnight; read the calendar
                        // date off it in UTC or it slips a day west of London.
                        chosen = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record this addition?") },
        text = {
            Column {
                Text(
                    "Recommended: ${action.display ?: "${fmtAmount(action.amount)} " +
                        unit} of $productLabel. Record what actually went in; " +
                        "it is what teaches the app how your products behave.",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount added") },
                    suffix = { Text(unit) },
                    singleLine = true,
                    isError = amount == null,
                    supportingText = when {
                        amount == null -> { { Text("Enter an amount above zero") } }
                        action.amount != null && amount != action.amount ->
                            { { Text("Differs from the recommendation") } }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "When did you add it?",
                    style = MaterialTheme.typography.labelLarge,
                    color = PoolOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chosen == today,
                        onClick = { chosen = today },
                        label = { Text("Today") },
                    )
                    FilterChip(
                        selected = chosen == today.minusDays(1),
                        onClick = { chosen = today.minusDays(1) },
                        label = { Text("Yesterday") },
                    )
                    FilterChip(
                        selected = chosen != today && chosen != today.minusDays(1),
                        onClick = { showPicker = true },
                        label = {
                            Text(
                                if (chosen != today && chosen != today.minusDays(1))
                                    chosen.format(fmt) else "Earlier",
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                val added = amount ?: return@TextButton
                // Today keeps the real clock time. An earlier date goes as a
                // date, for the server to resolve against the pool's timezone;
                // resolving it here used the phone's zone and filed doses hours
                // out, sometimes before the very reading they belonged to.
                onConfirm(
                    added,
                    if (chosen == today) null
                    else chosen.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
            }) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(latest: ChemLatest, config: Map<String, String>) {
    val overdueAfter = config["overdue_days"]?.toIntOrNull() ?: 7
    val days = latest.daysSince
    val closed = !latest.seasonOpen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (latest.reading == null) {
                Text(
                    "No tests logged yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = PoolOnSurface,
                )
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
                    color = if (!closed && days != null && days >= overdueAfter) ChemLow
                    else PoolOnSurface,
                )
                if (!closed && days != null && days >= overdueAfter) {
                    Spacer(Modifier.width(8.dp))
                    Pill("DUE", ChemLow)
                }
                if (closed) {
                    Spacer(Modifier.width(8.dp))
                    Pill("CLOSED", ChemUnknown)
                }
            }
            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PARAM_LABELS.forEach { (key, label) ->
                    Pill("$label ${valueFor(latest, key)}", statusColor(latest.status[key]))
                }
            }

            // The control lives in Settings; this screen only reports the
            // state, so a closed pool says where to reopen it.
            if (closed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Closed for the season, so no test reminders. Reopen it in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PoolOnSurfaceMuted,
                )
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

/** Off-scale readings print the limit that was hit, never an invented number. */
private fun valueFor(latest: ChemLatest, key: String): String {
    val r = latest.reading ?: return ""
    return when (key) {
        "ph" -> when {
            r.phBelow7 == 1 -> "low"
            r.phOver == 1 -> "high"
            else -> r.ph?.toString() ?: "-"
        }
        "fc" -> if (r.fcOver == 1) ">5" else r.fc?.toString() ?: "-"
        "ta" -> if (r.taOver == 1) "high" else r.ta?.toString() ?: "-"
        "cya" -> when {
            r.cyaBelow30 == 1 -> "<30"
            r.cyaOver == 1 -> "high"
            else -> r.cya?.toString() ?: "-"
        }
        "ch" -> when {
            r.chUnder == 1 -> "low"
            r.chOver == 1 -> "high"
            else -> r.ch?.toString() ?: "-"
        }
        "salt" -> r.salt?.toString() ?: "-"
        else -> "-"
    }
}

@Composable
private fun ActionsCard(
    latest: ChemLatest,
    labels: Map<String, String>,
    onLog: (ChemAction) -> Unit,
    onSkip: (ChemAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("What to add", style = MaterialTheme.typography.titleMedium, color = PoolOnSurface)
            if (latest.actions.isNotEmpty() && latest.pendingCount == 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "All handled for this test. Retest in a day or two to see " +
                        "where it landed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChemOk,
                )
            }
            Spacer(Modifier.height(12.dp))

            latest.actions.forEach { a ->
                val label = labels[a.product] ?: a.product.orEmpty()
                val headline = when {
                    a.swgPct != null -> "Set the cell to ${a.swgPct}%"
                    // The server renders the amount into something pourable,
                    // e.g. "1 gallon (128 oz)".
                    a.amount != null ->
                        "${a.display ?: "${fmtAmount(a.amount)} ${a.unit.orEmpty()}"} $label"
                    else -> label.ifBlank { a.reason }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            headline,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (a.dismissed || a.done) PoolOnSurfaceMuted
                            else PoolOnSurface,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (a.dismissed || a.done)
                                TextDecoration.LineThrough else null,
                        )
                        Text(
                            a.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = PoolOnSurfaceMuted,
                            textDecoration = if (a.dismissed) TextDecoration.LineThrough else null,
                        )
                        if (a.done) {
                            Text(
                                "Added" + (a.doneTs?.let {
                                    " " + DATE_ONLY.format(Date(it * 1000))
                                } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = ChemOk,
                            )
                        }
                        if (!a.dismissed && !a.done) {
                            if (a.waitMinutes > 0) {
                                Text(
                                    "Wait ${a.waitMinutes / 60} h first",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChemLow,
                                )
                            }
                            a.note?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PoolOnSurfaceMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        // A skipped action keeps its Undo so the decision stays
                        // reversible. Only "Added" disappears, because it is no
                        // longer the next step.
                        if (!a.dismissed && !a.done &&
                            a.product != null && a.amount != null
                        ) {
                            OutlinedButton(onClick = { onLog(a) }) { Text("Added") }
                        }
                        if (!a.done) {
                            TextButton(onClick = { onSkip(a) }) {
                                Text(if (a.dismissed) "Undo" else "Skip")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            latest.blocked.forEach { Banner(it.reason, ChemLow) }
            latest.warnings.forEach { Banner(it, ChemHigh) }
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EntryCard(vm: ChemistryViewModel, state: ChemistryViewModel.UiState) {
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

            // The shape of this form comes from the server, so adding a
            // parameter is a server change rather than an edit in three places.
            state.fields.forEach { f ->
                DropdownField(
                    field = f,
                    value = state.selections[f.key].orEmpty(),
                    onValue = { vm.setValue(f.key, it) },
                )
            }

            // Salt stays typed: the cell display gives a precise value like
            // 3250, and a dropdown would round away real precision.
            OutlinedTextField(
                value = state.salt,
                onValueChange = vm::setSalt,
                label = { Text("Salt (cell display)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = vm::setNote,
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

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
                Text(if (state.saving) "Saving..." else "Save test")
            }
        }
    }
}

/**
 * One control per parameter. Off-scale readings are entries in the list, so
 * picking "Below 6.8" cannot contradict a value the way a checkbox beside a
 * filled field could. Options come from ChemEntry, which is unit tested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    field: ChemField,
    value: String,
    onValue: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val options = remember(field) { ChemEntry.optionsFor(field) }
    val shown = options.firstOrNull { it.value == value }?.label ?: ""
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label) },
            placeholder = { Text("Not tested", color = PoolOnSurfaceMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // A blank first entry is how a value gets cleared back to
            // "not tested" without resetting the whole form.
            DropdownMenuItem(
                text = { Text("Not tested", color = PoolOnSurfaceMuted) },
                onClick = { onValue(""); open = false },
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = { onValue(opt.value); open = false },
                )
            }
        }
    }
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

private fun fmtAmount(amount: Double?): String {
    if (amount == null) return ""
    return if (amount % 1.0 == 0.0) amount.toInt().toString()
    else String.format(Locale.US, "%.1f", amount)
}
