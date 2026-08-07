package com.household.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.Positive
import com.household.finance.ui.theme.Warning
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
    onSetEmergencyFund: (Double) -> Unit
) {
    var efInput by remember(emergencyFundAmount) { mutableStateOf(emergencyFundAmount.toInt().toString()) }

    val target = Calculations.emergencyFundTarget(summary.totalExpenses)
    val progress = if (target > 0) (emergencyFundAmount / target).coerceIn(0.0, 1.0) else 0.0
    val nudges = remember(entries) { InsightsCoach.fallbackNudges(entries) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("THIS MONTH", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Household Income", formatInr(summary.totalIncome))
                    SummaryRow("Expenses", formatInr(summary.totalExpenses))
                    SummaryRow("Savings / Investments", formatInr(summary.totalSavings))
                    Divider(Modifier.padding(vertical = 10.dp), color = androidx.compose.ui.graphics.Color(0x22FFFFFF))
                    SummaryRow("Surplus", formatInr(summary.surplus), bold = true, accent = if (summary.surplus >= 0) Positive else Warning)
                    SummaryRow("Savings Rate", String.format(Locale.US, "%.1f%%", summary.savingsRatePct), bold = true, accent = Positive)
                }
            }
        }

        if (summary.categorySpend.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("CATEGORY SPEND", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        val maxAmount = summary.categorySpend.maxOf { it.monthlyAmount }.coerceAtLeast(1.0)
                        summary.categorySpend.forEach { c ->
                            Column(Modifier.padding(bottom = 10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(c.category, style = MaterialTheme.typography.bodyMedium)
                                    Text(formatInr(c.monthlyAmount) + "/mo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).background(androidx.compose.ui.graphics.Color(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction = (c.monthlyAmount / maxAmount).toFloat().coerceIn(0f, 1f))
                                            .height(6.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    listOf(com.household.finance.ui.theme.Violet, com.household.finance.ui.theme.Cyan)
                                                ),
                                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val recurring = remember(entries) { Calculations.recurringCommitments(entries) }
        if (recurring.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("EMIS & RECURRING — MONTHLY → YEARLY", style = MaterialTheme.typography.labelLarge)
                        Text("For arranging a whole year's budget at once.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        recurring.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("${item.entry.category} (${item.entry.person})", style = MaterialTheme.typography.bodyMedium)
                                    Text(formatInr(item.monthlyAmount) + "/mo", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatInr(item.yearlyAmount) + "/yr", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Divider(Modifier.padding(vertical = 10.dp), color = androidx.compose.ui.graphics.Color(0x22FFFFFF))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total fixed commitments", fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatInr(recurring.sumOf { it.monthlyAmount }) + "/mo", fontWeight = FontWeight.Bold)
                                Text(formatInr(recurring.sumOf { it.yearlyAmount }) + "/yr", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("BY PERSON", style = MaterialTheme.typography.labelLarge)
                    listOf(nameMe, nameWife).forEach { person ->
                        Text(person, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        SummaryRow("Income", formatInr(summary.incomeByPerson[person] ?: 0.0))
                        SummaryRow("Expenses", formatInr(summary.expenseByPerson[person] ?: 0.0))
                        SummaryRow("Savings", formatInr(summary.savingsByPerson[person] ?: 0.0))
                    }
                }
            }
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("BY BUCKET", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Joint", formatInr(summary.byBucket[Bucket.JOINT] ?: 0.0))
                    SummaryRow("Personal ($nameMe)", formatInr(summary.byBucket[Bucket.PERSONAL_ME] ?: 0.0))
                    SummaryRow("Personal ($nameWife)", formatInr(summary.byBucket[Bucket.PERSONAL_WIFE] ?: 0.0))
                    if (summary.incomeRatio.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
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
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("EMERGENCY FUND", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text("Target (6× monthly expenses): ${formatInr(target)}")
                    Text("Current: ${formatInr(emergencyFundAmount)}")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Spacer(Modifier.height(10.dp))
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
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("POLICIES & INVESTMENTS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        policies.forEach { p ->
                            val status = Calculations.policyStatus(p)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
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
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("INSIGHTS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        nudges.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false, accent: androidx.compose.ui.graphics.Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = accent ?: MaterialTheme.colorScheme.onSurface)
    }
}
