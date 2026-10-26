package com.example.jacaccounts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jacaccounts.data.CURRENCIES
import com.example.jacaccounts.data.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)
private const val MAX_DAYS = 30

private fun Entry.day(): LocalDate =
    Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC).toLocalDate()

private fun compact(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1_000)
    else -> String.format(Locale.US, "%.0f", v)
}

@Composable
fun TrendsScreen(income: List<Entry>, expenses: List<Entry>, modifier: Modifier) {
    var currency by rememberSaveable { mutableStateOf(CURRENCIES.first()) }
    // Transfer legs move your own money between accounts, so they are neither income nor expense.
    val inc = income.filter { it.isDirect && it.currency == currency }
    val exp = expenses.filter { it.isDirect && it.currency == currency }

    // Last MAX_DAYS days ending today, so days with no activity still show as zero.
    val days = remember(inc, exp) {
        val end = LocalDate.now()
        val first = (inc + exp).minOfOrNull { it.day() } ?: end
        val start = maxOf(first, end.minusDays(MAX_DAYS - 1L))
        generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
    }
    val incomeByDay = remember(inc) { inc.groupBy { it.day() }.mapValues { (_, v) -> v.sumOf { it.amount } } }
    val expenseByDay = remember(exp) { exp.groupBy { it.day() }.mapValues { (_, v) -> v.sumOf { it.amount } } }
    val incomeSeries = days.map { incomeByDay[it] ?: 0.0 }
    val expenseSeries = days.map { expenseByDay[it] ?: 0.0 }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CURRENCIES.forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("Income", IncomeColor)
            Legend("Expenses", ExpenseColor)
        }
        if (inc.isEmpty() && exp.isEmpty()) {
            Text("No $currency income or expenses yet.")
        } else {
            LineChart(days, incomeSeries, expenseSeries)
            // Newest first; days without any activity are left out of the list.
            days.indices.reversed().filter { incomeSeries[it] != 0.0 || expenseSeries[it] != 0.0 }.forEach { i ->
                Row(Modifier.fillMaxWidth()) {
                    Text(dayLabel(days[i], long = true), Modifier.weight(1f))
                    Text(money(incomeSeries[i], currency), color = IncomeColor, modifier = Modifier.weight(1.3f))
                    Text(money(expenseSeries[i], currency), color = ExpenseColor, modifier = Modifier.weight(1.3f))
                }
            }
        }
    }
}

private fun dayLabel(d: LocalDate, long: Boolean = false): String =
    d.format(DateTimeFormatter.ofPattern(if (long) "d MMM yyyy" else "d MMM", Locale.getDefault()))

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.size(12.dp).background(color)) {}
        Text("  $text", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LineChart(days: List<LocalDate>, income: List<Double>, expenses: List<Double>) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val yMax = (income + expenses).maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Canvas(Modifier.fillMaxWidth().height(260.dp)) {
        val left = 44.dp.toPx()
        val bottom = 24.dp.toPx()
        val top = 8.dp.toPx()
        val right = 8.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun x(i: Int) = left + if (days.size == 1) w / 2 else w * i / (days.size - 1)
        fun y(v: Double) = top + h - (h * (v / yMax)).toFloat()

        for (g in 0..4) {
            val v = yMax * g / 4
            drawLine(axisColor, Offset(left, y(v)), Offset(size.width - right, y(v)), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(compact(v), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), y(v) - t.size.height / 2f))
        }
        // Thin out day labels so about six fit across the chart.
        val step = maxOf(1, (days.size + 5) / 6)
        days.forEachIndexed { i, d ->
            if (i % step == 0) {
                val t = measurer.measure(dayLabel(d), labelStyle)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, size.height - bottom + 4.dp.toPx()))
            }
        }
        listOf(income to IncomeColor, expenses to ExpenseColor).forEach { (series, color) ->
            val path = Path()
            series.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
            if (series.size <= 31) series.forEachIndexed { i, v -> drawCircle(color, 3.dp.toPx(), Offset(x(i), y(v))) }
        }
    }
}
