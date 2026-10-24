package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.CalculationOutcome
import com.smartexpense.domain.NaturalCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun CalculatorScreen(onBack: () -> Unit = {}) {
    var input by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("Type naturally") }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var request by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CalculationOutcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun changed() { request = null; result = null; busy = false }
    LaunchedEffect(request) {
        val text = request ?: return@LaunchedEffect
        busy = true
        result = withContext(Dispatchers.Default) { NaturalCalculator.calculate(text) }
        busy = false
    }
    Page("Calculate", "Works offline. Results do not change your records.", onBack = onBack) {
        item { Text("Type a calculation or let the app ask for the numbers.") }
        item { Column {
            listOf("Type naturally", "Percentage of an amount", "Split a bill", "Convert units").forEach { choice ->
                OutlinedButton(onClick = { changed(); mode = choice }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (mode == choice) "✓ $choice" else choice)
                }
            }
        } }
        if (mode == "Type naturally") {
            item { OutlinedTextField(input, { changed(); input = it }, label = { Text("What would you like to calculate?") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item { Column {
                Text("Try an example", style = MaterialTheme.typography.titleSmall)
                listOf("120 + 85 + 40", "add 5% tax to 450", "split 100 between 3 people", "convert 2.5 km to m", "tip 10% on 500", "average 10, 20, 30").forEach { example ->
                    TextButton(onClick = { changed(); input = example }, modifier = Modifier.heightIn(min = 48.dp)) { Text(example) }
                }
            } }
        } else {
            item { OutlinedTextField(first, { changed(); first = it }, label = { Text(when (mode) { "Split a bill" -> "Bill total in rupees"; "Convert units" -> "Amount and unit, e.g. 2.5 km"; else -> "Percentage, e.g. 15" }) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(second, { changed(); second = it }, label = { Text(when (mode) { "Split a bill" -> "How many people?"; "Convert units" -> "Target unit, e.g. m"; else -> "Amount, e.g. 200" }) }, modifier = Modifier.fillMaxWidth()) }
        }
        item { Button(onClick = {
            request = when (mode) { "Split a bill" -> "split $first between $second people"; "Percentage of an amount" -> "$first% of $second"; "Convert units" -> "convert $first to $second"; else -> input }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "Calculating…" else "Calculate") } }
        item { Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val current = result) {
                is CalculationOutcome.Answer -> {
                    Text(if (current.approximate) "Approximate result" else "Answer", style = MaterialTheme.typography.titleMedium)
                    Text(current.value, style = MaterialTheme.typography.headlineSmall)
                    Text("Interpreted as: ${current.expression}", style = MaterialTheme.typography.bodyMedium)
                }
                is CalculationOutcome.NeedsInput -> { Text("A little more detail", style = MaterialTheme.typography.titleMedium); Text(current.message) }
                null -> Unit
            }
        } }
        item { Text("Supports arithmetic, percentages, equal bill splits, conversions, statistics, ratios, tips, reverse tax and equations up to degree two. Try percentage change from 80 to 100, or simple interest on 1000 at 10% per year for 2 years. Compound interest requires a schedule such as compounded monthly. Use sin_deg or sin_rad for angles. Currency conversion needs a rate you supply.", style = MaterialTheme.typography.bodySmall) }
    }
}
