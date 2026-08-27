package com.jjwalter.pooltemp.ui.chemistry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jjwalter.pooltemp.data.ChemReading
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.ui.theme.ChemAccent
import com.jjwalter.pooltemp.ui.theme.ChemLow
import com.jjwalter.pooltemp.ui.theme.PoolOnSurface
import com.jjwalter.pooltemp.ui.theme.PoolOnSurfaceMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChemistryHistoryScreen(
    settings: Settings,
    onBack: () -> Unit,
) {
    val vm: ChemistryHistoryViewModel =
        viewModel(factory = ChemistryHistoryViewModelFactory(settings))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test History") },
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
    ) { pad ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = ChemAccent) }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: "",
                        color = PoolOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = vm::load) { Text("Try again") }
                }
            }

            state.readings.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No tests logged yet.",
                    color = PoolOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${state.readings.size} tests, newest first. " +
                            "Amber values were past what the kit can read. " +
                            "Corrections are made on the web page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PoolOnSurfaceMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(state.readings, key = { it.id ?: it.ts ?: 0L }) { r ->
                    HistoryRow(r)
                }
            }
        }
    }
}

private val DATE_FMT = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())

/** A value plus whether the kit hit its limit reading it. */
private data class Cell(val label: String, val text: String, val censored: Boolean)

private fun cells(r: ChemReading): List<Cell> = buildList {
    when {
        r.phBelow7 == 1 -> add(Cell("pH", "<7", true))
        // pH always carries its decimal: "8" reads as a different kind of
        // measurement than "8.0", and the kit reports to one place.
        r.ph != null -> add(Cell("pH", String.format(Locale.US, "%.1f", r.ph), false))
    }
    when {
        r.fcOver == 1 -> add(Cell("FC", ">5", true))
        r.fc != null -> add(Cell("FC", r.fc.toString(), false))
    }
    when {
        r.tcOver == 1 -> add(Cell("TC", ">5", true))
        r.tc != null -> add(Cell("TC", r.tc.toString(), false))
    }
    r.ta?.let { add(Cell("TA", it.toString(), false)) }
    when {
        r.cyaBelow30 == 1 -> add(Cell("CYA", "<30", true))
        r.cya != null -> add(Cell("CYA", r.cya.toString(), false))
    }
    r.ch?.let { add(Cell("CH", it.toString(), false)) }
    r.salt?.let { add(Cell("Salt", it.toString(), false)) }
    r.swgPct?.let { add(Cell("SWG", "$it%", false)) }
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(r: ChemReading) {
    val values = cells(r)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    r.ts?.let { DATE_FMT.format(Date(it * 1000)) } ?: "Unknown date",
                    style = MaterialTheme.typography.labelLarge,
                    color = PoolOnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                r.waterTempF?.let {
                    Text(
                        "${fmt(it)}°F",
                        style = MaterialTheme.typography.labelMedium,
                        color = PoolOnSurfaceMuted,
                    )
                }
            }

            if (values.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No values recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = PoolOnSurfaceMuted,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    values.forEach { ValueChip(it) }
                }
            }

            r.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = PoolOnSurfaceMuted,
                )
            }
        }
    }
}

/**
 * Chips are neutral except where the reading was censored.
 *
 * Deliberately not colored by in-range or out-of-range: targets have changed
 * over the three years of history, so scoring a 2024 reading against today's
 * bands would be a fabrication. The web page's charts are the place to judge
 * trends.
 */
@Composable
private fun ValueChip(c: Cell) {
    val fg = if (c.censored) ChemLow else PoolOnSurface
    Surface(
        color = if (c.censored) ChemLow.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                c.label,
                style = MaterialTheme.typography.labelSmall,
                color = PoolOnSurfaceMuted,
            )
            Spacer(Modifier.padding(horizontal = 2.dp))
            Text(
                c.text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
