package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Bucket
import com.household.finance.data.Entry
import com.household.finance.data.PolicyStatus
import com.household.finance.logic.Calculations
import com.household.finance.logic.DashboardSummary
import com.household.finance.logic.InsightsCoach
import java.util.Locale

fun formatInr(value: Double): String {
    val absVal = Math.abs(value)
    val sign = if (value < 0) "-" else ""
    val s = String.format(Locale.US, "%.0f", absVal)
    if (s.length <= 3) return "$sign₹$s"
    val last3 = s.substring(s.length - 3)
    var rest = s.substring(0, s.length - 3)
    val sb = StringBuilder()
    while (rest.length > 2) {
        sb.insert(0, "," + rest.substring(rest.length - 2))
        rest = rest.substring(0, rest.length - 2)
    }
    sb.insert(0, rest)
    return "$sign₹$sb,$last3"
}

@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    entries: List<Entry>,
    emergencyFundAmount: Double,
    nameMe: String,
    nameWife: String,
    openAiKey: String,
    onSetEmergencyFund: (Double) -> Unit
) {
    var aiSummaryText by remember { mutableStateOf<String?>(null) }
    var loadingAi by remember { mutableStateOf(false) }
    var efInput by remember(emergencyFundAmount) { mutableStateOf(emergencyFundAmount.toInt().toString()) }

    val target = Calculations.emergencyFundTarget(summary.totalExpenses)
    val progress = if (target > 0) (emergencyFundAmount / target).coerceIn(0.0, 1.0) else 0.0
    val nudges = remember(entries) { InsightsCoach.fallbackNudges(entries) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This Month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Household Income", formatInr(summary.totalIncome))
                    SummaryRow("Expenses", formatInr(summary.totalExpenses))
                    SummaryRow("Savings / Investments", formatInr(summary.totalSavings))
                    Divider(Modifier.padding(vertical = 8.dp))
                    SummaryRow("Surplus", formatInr(summary.surplus), bold = true)
                    SummaryRow("Savings Rate", String.format(Locale.US, "%.1f%%", summary.savingsRatePct), bold = true)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("By Person", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    listOf(nameMe, nameWife).forEach { person ->
                        Text(person, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        SummaryRow("  Income", formatInr(summary.incomeByPerson[person] ?: 0.0))
                        SummaryRow("  Expenses", formatInr(summary.expenseByPerson[person] ?: 0.0))
                        SummaryRow("  Savings", formatInr(summary.savingsByPerson[person] ?: 0.0))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("By Bucket", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Joint", formatInr(summary.byBucket[Bucket.JOINT] ?: 0.0))
                    SummaryRow("Personal ($nameMe)", formatInr(summary.byBucket[Bucket.PERSONAL_ME] ?: 0.0))
                    SummaryRow("Personal ($nameWife)", formatInr(summary.byBucket[Bucket.PERSONAL_WIFE] ?: 0.0))
                    if (summary.incomeRatio.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Joint split by income ratio: " + summary.incomeRatio.entries.joinToString(" : ") {
                                "${it.key} ${String.format(Locale.US, "%.0f%%", it.value * 100)}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Emergency Fund", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Target (6x monthly expenses): ${formatInr(target)}")
                    Text("Current: ${formatInr(emergencyFundAmount)}")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = efInput,
                            onValueChange = { efInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Update amount") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onSetEmergencyFund(efInput.toDoubleOrNull() ?: 0.0) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        val policies = entries.filter { Calculations.policyStatus(it) != PolicyStatus.ACTIVE || it.category in setOf("LIC", "RD", "FD", "PPF", "SIP") }
        if (policies.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Policies & Investments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        policies.forEach { p ->
                            val status = Calculations.policyStatus(p)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${p.category} (${p.person}) — ${formatInr(p.amount)}/${if (p.frequency.name == "ANNUAL") "yr" else "mo"}")
                                AssistChip(onClick = {}, label = { Text(status.name.replace("_", " ")) })
                            }
                        }
                    }
                }
            }
        }

        if (nudges.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        nudges.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Monthly Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(aiSummaryText ?: InsightsCoach.ruleBasedSummary(summary))
                    if (openAiKey.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            loadingAi = true
                            Thread {
                                val result = runCatching { InsightsCoach.aiSummary(summary, entries, openAiKey) }
                                aiSummaryText = result.getOrNull()
                                loadingAi = false
                            }.start()
                        }, enabled = !loadingAi) {
                            Text(if (loadingAi) "Generating..." else "Generate AI summary")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
