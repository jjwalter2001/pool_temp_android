package com.jjwalter.pooltemp.ui.chemistry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Past tests as a grid.
 *
 * Column order deliberately matches the Pool Measurements spreadsheet this
 * history was imported from (pH, total chlorine, free chlorine, alkalinity,
 * cyanuric acid, salt, calcium), so scanning it feels like the sheet rather
 * than like a different tool showing the same numbers.
 *
 * The date column is frozen and the rest scroll horizontally as one, sharing a
 * single scroll state with the header, which is the freeze-pane behaviour a
 * spreadsheet gives you. Read only: editing lives on the web page.
 */

private val DATE_W = 74.dp
private val NARROW = 46.dp
private val WIDE = 56.dp
private val NOTE_W = 230.dp
private val ROW_H = 38.dp

private val GRID_LINE = Color.White.copy(alpha = 0.06f)
private val ZEBRA = Color.White.copy(alpha = 0.028f)
private val HEADER_BG = Color(0xFF1A1533)

/** Numeric cells use tabular figures so digits line up down a column. */
private val CELL_STYLE = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum")
private val HEAD_STYLE = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.4.sp,
)

private data class Col(val head: String, val width: Dp, val get: (ChemReading) -> Val)

/** A rendered value plus whether the kit hit its limit reading it. */
private data class Val(val text: String, val censored: Boolean = false)

private val EMPTY = Val("")

private fun ph(r: ChemReading): Val = when {
    r.phBelow7 == 1 -> Val("<7", true)
    r.ph != null -> Val(String.format(Locale.US, "%.1f", r.ph))
    else -> EMPTY
}

private fun chlorine(v: Int?, over: Int): Val = when {
    over == 1 -> Val(">5", true)
    v != null -> Val(v.toString())
    else -> EMPTY
}

private fun cya(r: ChemReading): Val = when {
    r.cyaBelow30 == 1 -> Val("<30", true)
    r.cya != null -> Val(r.cya.toString())
    else -> EMPTY
}

private fun num(v: Int?): Val = if (v == null) EMPTY else Val(v.toString())

private val COLUMNS = listOf(
    Col("pH", NARROW) { ph(it) },
    Col("TC", NARROW) { chlorine(it.tc, it.tcOver) },
    Col("FC", NARROW) { chlorine(it.fc, it.fcOver) },
    Col("TA", NARROW) { num(it.ta) },
    Col("CYA", WIDE) { cya(it) },
    Col("SALT", WIDE) { num(it.salt) },
    Col("CH", WIDE) { num(it.ch) },
    Col("SWG", WIDE) { r -> r.swgPct?.let { Val("$it%") } ?: EMPTY },
    Col("TEMP", WIDE) { r ->
        r.waterTempF?.let { Val(String.format(Locale.US, "%.0f", it)) } ?: EMPTY
    },
)

private val DATE_FMT = SimpleDateFormat("d MMM yy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChemistryHistoryScreen(
    settings: Settings,
    onBack: () -> Unit,
) {
    val vm: ChemistryHistoryViewModel =
        viewModel(factory = ChemistryHistoryViewModelFactory(settings))
    val state by vm.state.collectAsState()

    // One scroll state shared by the header and every row keeps the columns
    // aligned while the grid scrolls sideways.
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()

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

            else -> Column(Modifier.fillMaxSize().padding(pad)) {
                HeaderRow(hScroll)
                HorizontalDivider(color = GRID_LINE)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        state.readings,
                        key = { _, r -> r.id ?: r.ts ?: 0L },
                    ) { i, r ->
                        DataRow(r, hScroll, zebra = i % 2 == 1)
                    }
                    item {
                        Text(
                            "${state.readings.size} tests, newest first. " +
                                "Swipe sideways for more columns. " +
                                "Amber values were past what the kit can read. " +
                                "Editing is on the web page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PoolOnSurfaceMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(hScroll: ScrollState) {
    Row(
        Modifier.fillMaxWidth().background(HEADER_BG).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadCell("DATE", DATE_W, TextAlign.Start)
        VLine()
        Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
            COLUMNS.forEach {
                HeadCell(it.head, it.width, TextAlign.End)
                VLine()
            }
            HeadCell("NOTE", NOTE_W, TextAlign.Start)
        }
    }
}

@Composable
private fun DataRow(
    r: ChemReading,
    hScroll: ScrollState,
    zebra: Boolean,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (zebra) ZEBRA else Color.Transparent)
                .height(ROW_H),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Frozen: the date stays put so a row stays identifiable while
            // the values scroll.
            Text(
                r.ts?.let { DATE_FMT.format(Date(it * 1000)) } ?: "?",
                style = CELL_STYLE,
                color = PoolOnSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(DATE_W).padding(horizontal = 6.dp),
            )
            VLine()
            Row(
                Modifier.horizontalScroll(hScroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                COLUMNS.forEach { col ->
                    val v = col.get(r)
                    Text(
                        v.text,
                        style = CELL_STYLE,
                        color = if (v.censored) ChemLow else PoolOnSurface,
                        fontWeight = if (v.censored) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(col.width).padding(horizontal = 6.dp),
                    )
                    VLine()
                }
                Text(
                    r.note.orEmpty(),
                    style = CELL_STYLE,
                    color = PoolOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(NOTE_W).padding(horizontal = 6.dp),
                )
            }
        }
        HorizontalDivider(color = GRID_LINE)
    }
}

@Composable
private fun HeadCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text,
        style = HEAD_STYLE,
        color = PoolOnSurfaceMuted,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width).padding(horizontal = 6.dp),
    )
}

/** Column rule. Thin enough to read as a grid, not as a border. */
@Composable
private fun VLine() {
    Box(Modifier.width(1.dp).height(ROW_H).background(GRID_LINE))
}
