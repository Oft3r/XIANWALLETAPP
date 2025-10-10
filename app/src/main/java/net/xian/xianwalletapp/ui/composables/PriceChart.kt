package net.xian.xianwalletapp.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun PriceChart(chartModelProducer: ChartEntryModelProducer, modifier: Modifier = Modifier) {
    val entries = listOf(entryOf(0, 4), entryOf(1, 2), entryOf(2, 5), entryOf(3, 3), entryOf(4, 6))
    chartModelProducer.setEntries(entries)

    Chart(
            chart = lineChart(),
            chartModelProducer = chartModelProducer,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(),
            modifier = modifier
    )
}
