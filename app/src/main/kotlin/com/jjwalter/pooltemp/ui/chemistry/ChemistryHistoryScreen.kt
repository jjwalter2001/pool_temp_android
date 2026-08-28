package com.jjwalter.pooltemp.ui.chemistry

import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.jjwalter.pooltemp.data.ChemDose
import com.jjwalter.pooltemp.data.ChemReading
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.ui.chemistry.ChemistryHistoryViewModel.Tab as HistoryTab
import com.jjwalter.pooltemp.ui.theme.ChemAccent
import com.jjwalter.pooltemp.ui.theme.ChemLow
import com.jjwalter.pooltemp.ui.theme.ChemOk
import com.jjwalter.pooltemp.ui.theme.PoolOnSurface
import com.jjwalter.pooltemp.ui.theme.PoolOnSurfaceMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Past tests and past additions, as grids.
 *
 * The tests grid's column order deliberately matches the Pool Measurements
 * spreadsheet this history was imported from, so scanning it feels like the
 * sheet rather than like a different tool showing the same numbers.
 *
 * In both grids the date column is frozen and the rest scroll horizontally as
 * one, sharing a single scroll state with the header, which is the freeze-pane
 * behaviour a spreadsheet gives you. Read only: editing lives on the web page.
 */

private val DATE_W = 74.dp
private val NARROW = 46.dp
private val WIDE = 56.dp
private val NOTE_W = 230.dp
// Wide enough for the longest seeded product name ("Calcium hardness
// increaser") and for the AMOUNT heading, which clipped to "AMOUN" at 56dp.
private val PRODUCT_W = 200.dp
private val AMOUNT_W = 76.dp
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

/** A rendered value plus whether the kit hit its limit reading it. */
private data class Val(val text: String, val censored: Boolean = false)

private val EMPTY = Val("")

private data class Col(val head: String, val width: Dp, val get: (ChemReading) -> Val)

private fun ph(r: ChemReading): Val = when {
    r.phBelow7 == 1 -> Val("<7", true)
    r.phOver == 1 -> Val(">8", true)
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
    r.cyaOver == 1 -> Val(">120", true)
    r.cya != null -> Val(r.cya.toString())
    else -> EMPTY
}

private fun ch(r: ChemReading): Val = when {
    r.chUnder == 1 -> Val("low", true)
    r.chOver == 1 -> Val("high", true)
    r.ch != null -> Val(r.ch.toString())
    else -> EMPTY
}

private fun ta(r: ChemReading): Val = when {
    r.taOver == 1 -> Val(">150", true)
    r.ta != null -> Val(r.ta.toString())
    else -> EMPTY
}

private fun num(v: Int?): Val = if (v == null) EMPTY else Val(v.toString())

private val TEST_COLUMNS = listOf(
    Col("pH", NARROW) { ph(it) },
    Col("TC", NARROW) { chlorine(it.tc, it.tcOver) },
    Col("FC", NARROW) { chlorine(it.fc, it.fcOver) },
    Col("TA", NARROW) { ta(it) },
    Col("CYA", WIDE) { cya(it) },
    Col("SALT", WIDE) { num(it.salt) },
    Col("CH", WIDE) { ch(it) },
    Col("SWG", WIDE) { r -> r.swgPct?.let { Val("$it%") } ?: EMPTY },
    Col("TEMP", WIDE) { r ->
        r.waterTempF?.let { Val(String.format(Locale.US, "%.0f", it)) } ?: EMPTY
    },
)

private val DATE_FMT = SimpleDateFormat("d MMM yy", Locale.getDefault())

private fun amount(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChemistryHistoryScreen(
    settings: Settings,
    onBack: () -> Unit,
) {
    val vm: ChemistryHistoryViewModel =
        viewModel(factory = ChemistryHistoryViewModelFactory(settings))
    val state by vm.state.collectAsState()

    // One scroll state per grid, shared by that grid's header and every row so
    // the columns stay aligned while it scrolls sideways.
    val testsScroll = rememberScrollState()
    val dosesScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
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
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(
                selectedTabIndex = if (state.tab == HistoryTab.TESTS) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ChemAccent,
            ) {
                Tab(
                    selected = state.tab == HistoryTab.TESTS,
                    onClick = { vm.setTab(HistoryTab.TESTS) },
                    text = { Text("Tests (${state.readings.size})") },
                )
                Tab(
                    selected = state.tab == HistoryTab.ADDITIONS,
                    onClick = { vm.setTab(HistoryTab.ADDITIONS) },
                    text = { Text("Additions (${state.doses.size})") },
                )
            }

            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ChemAccent) }

                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
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

                state.tab == HistoryTab.TESTS ->
                    TestsGrid(state.readings, testsScroll)

                else -> AdditionsGrid(state.doses, dosesScroll)
            }
        }
    }
}

@Composable
private fun TestsGrid(rows: List<ChemReading>, hScroll: ScrollState) {
    if (rows.isEmpty()) {
        Empty("No tests logged yet.")
        return
    }
    Column {
        GridHeader(hScroll, TEST_COLUMNS.map { it.head to it.width }, trailing = "NOTE")
        HorizontalDivider(color = GRID_LINE)
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, r -> r.id ?: r.ts ?: 0L }) { i, r ->
                GridRow(
                    date = r.ts,
                    zebra = i % 2 == 1,
                    hScroll = hScroll,
                    cells = TEST_COLUMNS.map { it.get(r) to it.width },
                    trailing = r.note.orEmpty(),
                )
            }
            item {
                Footnote(
                    "${rows.size} tests, newest first. Swipe sideways for more " +
                        "columns. Amber values were past what the kit can read. " +
                        "Editing is on the web page.",
                )
            }
        }
    }
}

@Composable
private fun AdditionsGrid(rows: List<ChemDose>, hScroll: ScrollState) {
    if (rows.isEmpty()) {
        Empty("Nothing logged as added yet.")
        return
    }
    Column {
        GridHeader(
            hScroll,
            listOf("AMOUNT" to AMOUNT_W, "PRODUCT" to PRODUCT_W, "REC" to WIDE),
            trailing = "NOTE",
        )
        HorizontalDivider(color = GRID_LINE)
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, d -> d.id ?: d.ts ?: 0L }) { i, d ->
                // The recommended column carries the useful comparison: blank
                // when nothing was recommended, a tick when the amount matched,
                // otherwise the number that was suggested instead.
                val rec = when {
                    d.recommendedAmount == null -> Val("")
                    d.asRecommended -> Val("same")
                    else -> Val(amount(d.recommendedAmount), censored = true)
                }
                GridRow(
                    date = d.ts,
                    zebra = i % 2 == 1,
                    hScroll = hScroll,
                    cells = listOf(
                        Val("${amount(d.amount)} ${d.unit}") to AMOUNT_W,
                        Val(d.productLabel) to PRODUCT_W,
                        rec to WIDE,
                    ),
                    trailing = d.note.orEmpty(),
                    alignFirstStart = true,
                    okCell = if (d.asRecommended) 2 else -1,
                )
            }
            item {
                Footnote(
                    "${rows.size} additions, newest first. REC compares what you " +
                        "added against what was recommended; a difference is what " +
                        "teaches the app how your products behave.",
                )
            }
        }
    }
}

@Composable
private fun GridHeader(
    hScroll: ScrollState,
    columns: List<Pair<String, Dp>>,
    trailing: String,
) {
    Row(
        Modifier.fillMaxWidth().background(HEADER_BG).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadCell("DATE", DATE_W, TextAlign.Start)
        VLine()
        Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
            columns.forEach { (head, w) ->
                HeadCell(head, w, TextAlign.End)
                VLine()
            }
            HeadCell(trailing, NOTE_W, TextAlign.Start)
        }
    }
}

@Composable
private fun GridRow(
    date: Long?,
    zebra: Boolean,
    hScroll: ScrollState,
    cells: List<Pair<Val, Dp>>,
    trailing: String,
    alignFirstStart: Boolean = false,
    okCell: Int = -1,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (zebra) ZEBRA else Color.Transparent)
                .height(ROW_H),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Frozen: the date stays put so a row stays identifiable while the
            // values scroll.
            Text(
                date?.let { DATE_FMT.format(Date(it * 1000)) } ?: "?",
                style = CELL_STYLE,
                color = PoolOnSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(DATE_W).padding(horizontal = 6.dp),
            )
            VLine()
            Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { i, (v, w) ->
                    Text(
                        v.text,
                        style = CELL_STYLE,
                        color = when {
                            i == okCell -> ChemOk
                            v.censored -> ChemLow
                            else -> PoolOnSurface
                        },
                        fontWeight = if (v.censored || i == okCell) FontWeight.SemiBold
                        else FontWeight.Normal,
                        textAlign = if (alignFirstStart && i == 1) TextAlign.Start
                        else TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(w).padding(horizontal = 6.dp),
                    )
                    VLine()
                }
                Text(
                    trailing,
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

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = PoolOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = PoolOnSurfaceMuted,
        modifier = Modifier.padding(16.dp),
    )
}
