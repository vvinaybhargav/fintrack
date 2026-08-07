package com.household.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Account
import com.household.finance.data.Bucket
import com.household.finance.data.Entry
import com.household.finance.data.PolicyStatus
import com.household.finance.logic.Calculations
import com.household.finance.logic.DashboardSummary
import com.household.finance.logic.InsightsCoach
import com.household.finance.ui.theme.Cyan
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.Positive
import com.household.finance.ui.theme.Violet
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
    accounts: List<Account>,
    emergencyFundAmount: Double,
    nameMe: String,
    nameWife: String,
    onSetEmergencyFund: (Double) -> Unit,
    onSetAccountBalance: (String, Double) -> Unit
) {
    var efInput by remember(emergencyFundAmount) { mutableStateOf(emergencyFundAmount.toInt().toString()) }
    var detailsExpanded by remember { mutableStateOf(false) }

    val target = Calculations.emergencyFundTarget(summary.totalExpenses)
    val efProgress = if (target > 0) (emergencyFundAmount / target).coerceIn(0.0, 1.0) else 0.0
    val nudges = remember(entries) { InsightsCoach.fallbackNudges(entries) }
    val recurring = Calculations.recurringCommitments(entries)
    val policies = entries.filter { Calculations.policyStatus(it) != PolicyStatus.ACTIVE || it.category in setOf("LIC", "RD", "FD", "PPF", "SIP") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- Glanceable hero: the three numbers that matter, big, at the top ---
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    label = "SURPLUS",
                    value = formatInr(summary.surplus),
                    accent = if (summary.surplus >= 0) Positive else Warning,
                    modifier = Modifier.weight(1.3f)
                )
                HeroStat(
                    label = "SAVINGS RATE",
                    value = String.format(Locale.US, "%.0f%%", summary.savingsRatePct),
                    accent = Positive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(accounts, key = { it.name }) { account ->
                        BalanceChip(account = account, onSave = { onSetAccountBalance(account.name, it) })
                    }
                }
            }
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("Income", formatInr(summary.totalIncome))
                    MiniStat("Expenses", formatInr(summary.totalExpenses))
                    MiniStat("Savings", formatInr(summary.totalSavings))
                }
            }
        }

        if (summary.categorySpend.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("TOP SPEND", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        val top = summary.categorySpend.take(4)
                        val maxAmount = top.maxOf { it.monthlyAmount }.coerceAtLeast(1.0)
                        top.forEach { c ->
                            Column(Modifier.padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(c.category, style = MaterialTheme.typography.bodySmall)
                                    Text(formatInr(c.monthlyAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction = (c.monthlyAmount / maxAmount).toFloat().coerceIn(0f, 1f))
                                            .height(5.dp)
                                            .background(Brush.horizontalGradient(listOf(Violet, Cyan)), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Everything else lives behind one toggle so the first screen stays glanceable ---
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth().clickable { detailsExpanded = !detailsExpanded }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (detailsExpanded) "Hide details" else "Show more details", fontWeight = FontWeight.SemiBold)
                    Icon(if (detailsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        }

        if (detailsExpanded) {
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
                        Text("${formatInr(emergencyFundAmount)} of ${formatInr(target)} target (6× monthly expenses)", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { efProgress.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
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
                            Button(onClick = { onSetEmergencyFund(efInput.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
                        }
                    }
                }
            }

            if (recurring.isNotEmpty()) {
                item {
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("EMIS & RECURRING — MONTHLY → YEARLY", style = MaterialTheme.typography.labelLarge)
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
                            Divider(Modifier.padding(vertical = 10.dp), color = Color(0x22FFFFFF))
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
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeroStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false, accent: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = accent ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BalanceChip(account: Account, onSave: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var input by remember(account.balance, editing) { mutableStateOf(account.balance.toInt().toString()) }

    GlassSurface(
        modifier = Modifier
            .widthIn(min = 130.dp)
            .animateContentSize()
            .clickable(enabled = !editing) { editing = true },
        cornerRadius = 18,
        contentPadding = 12
    ) {
        if (editing) {
            Column {
                Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    onSave(input.toDoubleOrNull() ?: account.balance)
                    editing = false
                }) { Text("Save") }
            }
        } else {
            Column {
                Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatInr(account.balance),
                    fontWeight = FontWeight.Bold,
                    color = if (account.balance < 0) Warning else Positive
                )
            }
        }
    }
}
