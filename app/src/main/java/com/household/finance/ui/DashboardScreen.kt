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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Account
import com.household.finance.data.Bucket
import com.household.finance.data.CategoryListLength
import com.household.finance.data.Entry
import com.household.finance.data.Goal
import com.household.finance.data.Loan
import com.household.finance.data.PolicyStatus
import com.household.finance.data.categoriesFor
import com.household.finance.logic.Calculations
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

private enum class DashboardView { PERSONAL, JOINT }

@Composable
fun DashboardScreen(
    entries: List<Entry>,
    accounts: List<Account>,
    goals: List<Goal>,
    loans: List<Loan>,
    categoryLength: CategoryListLength,
    emergencyFundAmount: Double,
    nameMe: String,
    onSetEmergencyFund: (Double) -> Unit,
    onSetAccountBalance: (String, Double) -> Unit,
    onSetGoalCompleted: (String, Boolean) -> Unit,
    onAddGoalContribution: (String, Double) -> Unit,
    onSetLoanSettled: (String, Boolean) -> Unit
) {
    var view by remember { mutableStateOf(DashboardView.PERSONAL) }
    var efInput by remember(emergencyFundAmount) { mutableStateOf(emergencyFundAmount.toInt().toString()) }
    var detailsExpanded by remember { mutableStateOf(false) }

    // Personal shows only entries logged under this device's own name; Joint shows shared entries.
    val filteredEntries = remember(entries, view, nameMe) {
        when (view) {
            DashboardView.PERSONAL -> entries.filter { it.bucket == Bucket.PERSONAL && it.person == nameMe }
            DashboardView.JOINT -> entries.filter { it.bucket == Bucket.JOINT }
        }
    }
    val summary = remember(filteredEntries) { Calculations.summarize(filteredEntries) }

    val target = Calculations.emergencyFundTarget(summary.totalExpenses)
    val efProgress = if (target > 0) (emergencyFundAmount / target).coerceIn(0.0, 1.0) else 0.0
    val nudges = remember(filteredEntries) { InsightsCoach.fallbackNudges(filteredEntries) }
    val recurring = remember(filteredEntries) { Calculations.recurringCommitments(filteredEntries) }
    val policies = remember(filteredEntries) {
        filteredEntries.filter { Calculations.policyStatus(it) != PolicyStatus.ACTIVE || it.category in setOf("LIC", "RD", "FD", "PPF", "SIP") }
    }
    // Organized by the category list configured in Settings (Short/Medium/Long), in that list's
    // own order - so the dashboard always matches how categories were set up, and "Other"
    // (structurally last in every preset) never crowds out real categories.
    val orderedCategorySpend = remember(summary.categorySpend, categoryLength) {
        val amountByCategory = summary.categorySpend.associate { it.category to it.monthlyAmount }
        categoriesFor(categoryLength).mapNotNull { cat ->
            val amount = amountByCategory[cat] ?: return@mapNotNull null
            if (amount <= 0.0) null else cat to amount
        }
    }
    // Only unsettled loans, split by which side of the loan this profile is on.
    val owedToMe = remember(loans, nameMe) { loans.filter { !it.settled && it.lender == nameMe } }
    val iOwe = remember(loans, nameMe) { loans.filter { !it.settled && it.borrower == nameMe } }
    val activeGoals = remember(goals) { goals.filter { !it.completed } }
    val completedGoals = remember(goals) { goals.filter { it.completed } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = view == DashboardView.PERSONAL,
                    onClick = { view = DashboardView.PERSONAL },
                    label = { Text(nameMe) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = view == DashboardView.JOINT,
                    onClick = { view = DashboardView.JOINT },
                    label = { Text("Joint") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Glanceable hero: the numbers that matter for the selected view, big, at the top ---
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    label = "LEFT OVER",
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
                Text("Tap a balance to edit it", style = MaterialTheme.typography.bodySmall)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(accounts, key = { it.name }) { account ->
                        BalanceChip(account = account, onSave = { onSetAccountBalance(account.name, it) })
                    }
                }
            }
        }

        if (owedToMe.isNotEmpty() || iOwe.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("IOUs", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        owedToMe.forEach { loan ->
                            LoanRow(text = "${loan.borrower} owes you ${formatInr(loan.amount)}", accent = Positive, note = loan.note) {
                                onSetLoanSettled(loan.id, true)
                            }
                        }
                        iOwe.forEach { loan ->
                            LoanRow(text = "You owe ${loan.lender} ${formatInr(loan.amount)}", accent = Warning, note = loan.note) {
                                onSetLoanSettled(loan.id, true)
                            }
                        }
                    }
                }
            }
        }

        if (goals.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("GOALS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        activeGoals.forEach { goal ->
                            GoalRow(
                                goal = goal,
                                onAddContribution = { amount -> onAddGoalContribution(goal.id, amount) },
                                onMarkReached = { onSetGoalCompleted(goal.id, true) }
                            )
                        }
                        if (completedGoals.isNotEmpty()) {
                            Text("REACHED", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                            completedGoals.forEach { goal ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✅ ${goal.title}", style = MaterialTheme.typography.bodyMedium, color = Positive)
                                    TextButton(onClick = { onSetGoalCompleted(goal.id, false) }) { Text("Undo") }
                                }
                            }
                        }
                        Text("Manage goals from the AI tab.", style = MaterialTheme.typography.bodySmall)
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

        if (orderedCategorySpend.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("SPEND BY CATEGORY", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        val shown = orderedCategorySpend.take(6)
                        val maxAmount = shown.maxOf { it.second }.coerceAtLeast(1.0)
                        shown.forEach { (category, amount) ->
                            Column(Modifier.padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category, style = MaterialTheme.typography.bodySmall)
                                    Text(formatInr(amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f))
                                            .height(5.dp)
                                            .background(Brush.horizontalGradient(listOf(Violet, Cyan)), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                        if (orderedCategorySpend.size > shown.size) {
                            Text("+${orderedCategorySpend.size - shown.size} more in details below", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (view == DashboardView.PERSONAL) "No personal entries for $nameMe yet." else "No joint entries yet.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                                        Text(item.entry.category, style = MaterialTheme.typography.bodyMedium)
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
                                    Text("${p.category} — ${formatInr(p.amount)}/${if (p.frequency.name == "ANNUAL") "yr" else "mo"}")
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
private fun LoanRow(text: String, accent: Color, note: String, onSettle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = accent, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSettle) { Text("Settle") }
        }
        if (note.isNotBlank()) {
            Text(note, style = MaterialTheme.typography.bodySmall)
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
private fun BalanceChip(account: Account, onSave: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .widthIn(min = 130.dp)
            .animateContentSize()
            .clickable(enabled = !editing) { editing = true },
        cornerRadius = 18,
        contentPadding = 12
    ) {
        if (editing) {
            BalanceEditForm(
                account = account,
                onSave = { onSave(it); editing = false },
                onCancel = { editing = false }
            )
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

/**
 * Separate composable so it's only entered (and its LaunchedEffect fires) exactly once per
 * edit session, right as the field mounts - a top-level effect with a delay hack was unreliable.
 */
@Composable
private fun BalanceEditForm(account: Account, onSave: (Double) -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf(if (account.balance == 0.0) "" else account.balance.toInt().toString()) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column {
        Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } },
            singleLine = true,
            modifier = Modifier.width(120.dp).focusRequester(focusRequester)
        )
        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(onClick = { onSave(input.toDoubleOrNull() ?: account.balance) }) { Text("Save") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, onAddContribution: (Double) -> Unit, onMarkReached: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val progress = if (goal.targetAmount > 0) (goal.savedSoFar / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0

    Column(Modifier.padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(goal.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(formatInr(goal.savedSoFar) + " / " + formatInr(goal.targetAmount), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp))
        Spacer(Modifier.height(4.dp))
        if (adding) {
            GoalContributionForm(
                onAdd = { amount -> onAddContribution(amount); adding = false },
                onCancel = { adding = false }
            )
        } else {
            Row {
                TextButton(onClick = { adding = true }) { Text("Add contribution") }
                TextButton(onClick = onMarkReached) { Text("Mark as reached") }
            }
        }
    }
}

@Composable
private fun GoalContributionForm(onAdd: (Double) -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { c -> c.isDigit() } },
            label = { Text("Amount saved") },
            singleLine = true,
            modifier = Modifier.width(140.dp).focusRequester(focusRequester)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { input.toDoubleOrNull()?.let { if (it > 0) onAdd(it) } }) { Text("Add") }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
