package com.household.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Account
import com.household.finance.data.Bill
import com.household.finance.data.BillType
import com.household.finance.data.Bucket
import com.household.finance.data.CategoryListLength
import com.household.finance.data.Entry
import com.household.finance.data.Goal
import com.household.finance.data.Loan
import com.household.finance.data.PolicyStatus
import com.household.finance.data.ActiveLoan
import com.household.finance.data.INVESTMENT_CATEGORIES
import com.household.finance.data.categoriesFor
import com.household.finance.logic.Calculations
import com.household.finance.logic.InsightsCoach
import java.util.Calendar
import java.util.Locale
import com.household.finance.ui.theme.Cyan
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.InkRaised
import com.household.finance.ui.theme.Positive
import com.household.finance.ui.theme.Violet
import com.household.finance.ui.theme.Warning

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
    bills: List<Bill>,
    categoryLength: CategoryListLength,
    jointFundAmount: Double,
    personalFundAmount: Double,
    budgets: Map<String, Double>,
    nameMe: String,
    defaultAccount: String?,
    activeLoans: List<com.household.finance.data.ActiveLoan>,
    salaryAmount: Double?,
    salaryCreditDate: Int?,
    onSetJointFund: (Double) -> Unit,
    onSetPersonalFund: (Double) -> Unit,
    onSetBudgetLimit: (String, Double) -> Unit,
    onSetAccountBalance: (String, Double) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSetGoalCompleted: (String, Boolean) -> Unit,
    onAddGoalContribution: (String, Double) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onSetLoanSettled: (String, Boolean) -> Unit,
    onSetLoanDueDate: (String, String?) -> Unit,
    onAddBill: (Bill) -> Unit,
    onDeleteBill: (String) -> Unit,
    onMarkBillPaid: (String) -> Unit,
    onCompleteCommitment: (Entry) -> Unit,
    onUndoCommitment: (String) -> Unit,
    onSeedCommitments: () -> Unit,
    onAddActiveLoan: (com.household.finance.data.ActiveLoan) -> Unit,
    onDeleteActiveLoan: (String) -> Unit,
    onUpdateActiveLoanPrepayment: (String, Double) -> Unit
) {
    val commitmentsChecklist = remember(entries, nameMe) {
        Calculations.getCommitmentsChecklist(entries, nameMe)
    }
    var view by remember { mutableStateOf(DashboardView.PERSONAL) }
    var showQuickFillDialogFor by remember { mutableStateOf<Triple<String, Double, String>?>(null) }

    val currentMonthExpenses = remember(entries) {
        val cal = Calendar.getInstance()
        val curYr = cal.get(Calendar.YEAR)
        val curMo = cal.get(Calendar.MONTH)
        entries.filter {
            val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            eCal.get(Calendar.YEAR) == curYr && eCal.get(Calendar.MONTH) == curMo &&
            it.type == com.household.finance.data.EntryType.EXPENSE
        }
    }
    val categorySpends = remember(currentMonthExpenses) {
        currentMonthExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }
    val categoriesWithBudgetsOrSpend = remember(budgets, categorySpends) {
        (budgets.keys + categorySpends.keys).distinct().sorted()
    }

    val showSalaryNudge = remember(entries, salaryCreditDate, salaryAmount, nameMe) {
        if (salaryCreditDate == null || salaryAmount == null || salaryAmount <= 0.0) false
        else {
            val cal = Calendar.getInstance()
            val todayDay = cal.get(Calendar.DAY_OF_MONTH)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)

            val hasSalaryThisMonth = entries.any {
                val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                eCal.get(Calendar.YEAR) == currentYear && eCal.get(Calendar.MONTH) == currentMonth &&
                it.person.equals(nameMe, ignoreCase = true) &&
                it.category.equals("Salary", ignoreCase = true)
            }
            !hasSalaryThisMonth && todayDay >= salaryCreditDate
        }
    }
    // Emergency fund and its edit callback both follow whichever view (Personal/Joint) is selected.
    val emergencyFundAmount = if (view == DashboardView.PERSONAL) personalFundAmount else jointFundAmount
    val onSetEmergencyFund = if (view == DashboardView.PERSONAL) onSetPersonalFund else onSetJointFund
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
    val trend = remember(filteredEntries) { Calculations.monthlyTrend(filteredEntries) }
    val sortedBills = remember(bills) { bills.sortedBy { it.dueDate } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        nameMe.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF120E2A)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Hi, $nameMe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Here's where things stand", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            // Pill toggle: a rounded track with a clearly-filled mint active segment.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(InkRaised, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PillToggleOption(text = nameMe, selected = view == DashboardView.PERSONAL, modifier = Modifier.weight(1f)) {
                    view = DashboardView.PERSONAL
                }
                PillToggleOption(text = "Joint", selected = view == DashboardView.JOINT, modifier = Modifier.weight(1f)) {
                    view = DashboardView.JOINT
                }
            }
        }

        // --- Salary Credit Nudge Banner ---
        if (showSalaryNudge && salaryAmount != null) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = "Reminder", tint = Positive)
                            Text("SALARY CREDIT DETECTED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Positive)
                        }
                        Text(
                            "It's salary time! Your expected credit date was the ${salaryCreditDate}th. Would you like to log your ₹${salaryAmount.toInt()} salary into your default account?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.US)
                                    val entry = Entry(
                                        person = nameMe,
                                        type = com.household.finance.data.EntryType.INCOME,
                                        bucket = com.household.finance.data.Bucket.PERSONAL,
                                        category = "Salary",
                                        amount = salaryAmount,
                                        frequency = com.household.finance.data.Frequency.MONTHLY,
                                        note = "Salary Credit ($monthName)",
                                        accountName = defaultAccount ?: accounts.firstOrNull()?.name
                                    )
                                    onCompleteCommitment(entry)
                                }
                            ) {
                                Text("Log Salary")
                            }
                        }
                    }
                }
            }
        }

        // --- Glanceable hero: the numbers that matter for the selected view, big, at the top ---
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStat(
                    label = "LEFT OVER",
                    value = formatInr(summary.surplus),
                    accent = if (summary.surplus >= 0) Positive else Warning,
                    modifier = Modifier.weight(1.3f),
                    gradient = true
                )
                if (summary.totalIncome <= 0.0) {
                    GlassSurface(modifier = Modifier.weight(1f)) {
                        Column {
                            Text("SAVINGS RATE", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Add income to see your savings rate",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    HeroStat(
                        label = "SAVINGS RATE",
                        value = String.format(Locale.US, "%.0f%%", summary.savingsRatePct),
                        accent = Positive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Unified Budget Progress Card ---
        
        if (categoriesWithBudgetsOrSpend.isNotEmpty()) {
            item {
                var selectedCoachCategory by remember { mutableStateOf<String?>(null) }
                var selectedCoachLimit by remember { mutableStateOf<Double?>(null) }

                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.PieChart, "MONTHLY BUDGET PROGRESS")
                        Spacer(Modifier.height(10.dp))
                        
                        categoriesWithBudgetsOrSpend.forEach { category ->
                            val spent = categorySpends[category] ?: 0.0
                            val limit = budgets[category]
                            
                            val progress = if (limit != null && limit > 0) spent / limit else 0.0
                            val progressColor = when {
                                limit == null -> Cyan
                                progress < 0.7 -> Positive
                                progress <= 1.0 -> Warning
                                else -> MaterialTheme.colorScheme.error
                            }

                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        val subText = if (limit != null) {
                                            "Spent ${formatInr(spent)} of ${formatInr(limit)}"
                                        } else {
                                            "Spent ${formatInr(spent)} (No limit set)"
                                        }
                                        Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            selectedCoachCategory = category
                                            selectedCoachLimit = limit
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = "AI Coach Insight",
                                            tint = progressColor
                                        )
                                    }
                                }
                                
                                if (limit != null && limit > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .background(Color(0x11FFFFFF))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(fraction = progress.toFloat().coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                .background(progressColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Category Coach Dialog
                if (selectedCoachCategory != null) {
                    val cat = selectedCoachCategory!!
                    val lim = selectedCoachLimit
                    val coachInsightText = remember(cat, entries, lim) {
                        Calculations.getCategoryInsights(cat, entries, lim)
                    }
                    AlertDialog(
                        onDismissRequest = {
                            selectedCoachCategory = null
                            selectedCoachLimit = null
                        },
                        title = {
                            Text("AI Coach: $cat Insight", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(coachInsightText, style = MaterialTheme.typography.bodyMedium)
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                selectedCoachCategory = null
                                selectedCoachLimit = null
                            }) {
                                Text("Got it")
                            }
                        }
                    )
                }
            }
        }

        // --- Monthly Commitments Checklist ---
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel(Icons.Filled.Repeat, "MONTHLY COMMITMENTS & SINKING FUNDS")
                    Spacer(Modifier.height(10.dp))
                    
                    val emisExpenses = commitmentsChecklist.filter { it.template.category in setOf("EMI", "Home Expenses", "Rent", "Groceries", "Utilities", "Other") }
                    val sinkingFunds = commitmentsChecklist.filter { it.template.category in setOf("Health Insurance", "Car Insurance", "Home Insurance", "Life Insurance") }
                    val investmentsSavings = commitmentsChecklist.filter { it.template.category in INVESTMENT_CATEGORIES || it.template.category == "Music Classes" }
                    
                    if (commitmentsChecklist.isEmpty()) {
                        Text(
                            "You haven't initialized your monthly commitments checklist yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onSeedCommitments) {
                            Text("Quick-seed $nameMe's Commitments Checklist")
                        }
                    } else {
                        if (defaultAccount.isNullOrBlank()) {
                            Text("⚠️ Please set a Default Account in Settings to enable one-tap payments.", color = Warning, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        if (emisExpenses.isNotEmpty()) {
                            Text("EMIS & MONTHLY EXPENSES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Violet, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            emisExpenses.forEach { item ->
                                CommitmentRow(item, defaultAccount, onCompleteCommitment, onUndoCommitment)
                            }
                        }
                        if (sinkingFunds.isNotEmpty()) {
                            Text("SINKING FUNDS (YEARLY PREMIUMS)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Violet, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            sinkingFunds.forEach { item ->
                                CommitmentRow(item, defaultAccount, onCompleteCommitment, onUndoCommitment)
                            }
                        }
                        if (investmentsSavings.isNotEmpty()) {
                            Text("INVESTMENTS & SAVINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Violet, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            investmentsSavings.forEach { item ->
                                CommitmentRow(item, defaultAccount, onCompleteCommitment, onUndoCommitment)
                            }
                        }
                    }
                }
            }
        }

        // --- Sinking Fund Runway Timeline Card ---
        val sinkingFundAccount = accounts.find { it.name.equals("Sinking Fund", ignoreCase = true) }
        val sinkingFundBalance = sinkingFundAccount?.balance ?: 0.0
        val annualCommitments = commitmentsChecklist
            .filter { it.template.frequency == com.household.finance.data.Frequency.ANNUAL }
            .map {
                val dueMonth = when {
                    it.template.note.contains("parents", ignoreCase = true) -> "September"
                    it.template.category.contains("Car", ignoreCase = true) -> "November"
                    it.template.category.contains("LIC", ignoreCase = true) -> "December"
                    else -> "September"
                }
                Triple(it.template.note.ifBlank { it.template.category }, it.template.amount, dueMonth)
            }
        
        if (annualCommitments.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.Shield, "SINKING FUND RUNWAY & TIMELINE")
                        Spacer(Modifier.height(10.dp))
                        Text("Total Sinking Fund Balance: ${formatInr(sinkingFundBalance)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Cyan)
                        Spacer(Modifier.height(6.dp))

                        val currentSurplus = summary.surplus
                        val totalNeededMonthly = commitmentsChecklist.filter { it.template.frequency == com.household.finance.data.Frequency.ANNUAL }.sumOf { it.monthlyAmount }
                        val coachRecommendation = when {
                            sinkingFundBalance < 30000 -> "Vinnu, your Sinking Fund is low. Since you have yearly premiums due later this year, try to set aside at least ${formatInr(totalNeededMonthly)} monthly to avoid cash crunch."
                            currentSurplus > 10000 -> "You have a healthy surplus of ${formatInr(currentSurplus)} this month! Consider transferring an extra ₹5,000 to your Sinking Fund or making a prepayment on your EMI."
                            else -> "Your Sinking Fund is on track. Keep transferring the monthly shares of your annual premiums."
                        }

                        Text("AI Coach: \"$coachRecommendation\"", style = MaterialTheme.typography.bodyMedium, color = Positive, modifier = Modifier.padding(vertical = 4.dp))

                        Divider(Modifier.padding(vertical = 10.dp), color = Color(0x22FFFFFF))

                        Text("YEAR-AT-A-GLANCE TIMELINE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        
                        annualCommitments.forEach { (name, amount, dueMonth) ->
                            val isReady = sinkingFundBalance >= amount
                            val shortfall = amount - sinkingFundBalance
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Due in $dueMonth · ${formatInr(amount)} total", style = MaterialTheme.typography.bodySmall)
                                    if (!isReady) {
                                        Text(
                                            text = "Transfer Shortfall",
                                            color = Cyan,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    showQuickFillDialogFor = Triple(name, shortfall, dueMonth)
                                                }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .background(if (isReady) Positive.copy(alpha = 0.2f) else Warning.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isReady) "READY" else "SHORT BY ${formatInr(shortfall)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isReady) Positive else Warning,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Sinking Fund Quick-Fill Dialog ---
        if (showQuickFillDialogFor != null) {
            val (name, shortfall, dueMonth) = showQuickFillDialogFor!!
            item {
                AlertDialog(
                    onDismissRequest = { showQuickFillDialogFor = null },
                    title = { Text("Transfer Shortfall", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Would you like to transfer the shortfall of ${formatInr(shortfall)} from your default account (${defaultAccount ?: "available accounts"}) to the Sinking Fund to make the premium for \"$name\" ready?", style = MaterialTheme.typography.bodyMedium)
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val transferEntry = Entry(
                                    person = nameMe,
                                    type = com.household.finance.data.EntryType.SAVINGS,
                                    bucket = com.household.finance.data.Bucket.PERSONAL,
                                    category = "Other",
                                    amount = shortfall,
                                    frequency = com.household.finance.data.Frequency.MONTHLY,
                                    note = "Sinking Fund top-up for $name",
                                    accountName = defaultAccount ?: accounts.firstOrNull()?.name,
                                    toAccountName = "Sinking Fund"
                                )
                                onCompleteCommitment(transferEntry)
                                showQuickFillDialogFor = null
                            }
                        ) {
                            Text("Transfer")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQuickFillDialogFor = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // --- EMI & Loan Tracker Card ---
        item {
            var showingAddLoan by remember { mutableStateOf(false) }
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(Icons.Filled.AccountBalanceWallet, "EMI & DEBT PAYDOWN TRACKER")
                        TextButton(onClick = { showingAddLoan = !showingAddLoan }) {
                            Text(if (showingAddLoan) "Close Form" else "+ Add Loan")
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (showingAddLoan) {
                        AddLoanForm(
                            onAdd = { loan ->
                                onAddActiveLoan(loan)
                                showingAddLoan = false
                            },
                            onCancel = { showingAddLoan = false }
                        )
                        Divider(Modifier.padding(vertical = 12.dp), color = Color(0x11FFFFFF))
                    }

                    val myLoans = activeLoans.filter { it.owner == nameMe }
                    if (myLoans.isEmpty()) {
                        Text("No long-term loans added yet. Use '+ Add Loan' above to track your EMI amortization & prepayment benefits.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        myLoans.forEach { loan ->
                            ActiveLoanCard(
                                loan = loan,
                                onDelete = { onDeleteActiveLoan(loan.id) },
                                onUpdatePrepayment = { amt -> onUpdateActiveLoanPrepayment(loan.id, amt) }
                            )
                        }
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                Column {
                    SectionLabel(Icons.Filled.AccountBalanceWallet, "BALANCES")
                    Spacer(Modifier.height(2.dp))
                    Text("Tap a balance to edit it", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts, key = { it.name }) { account ->
                        BalanceChip(
                            account = account,
                            onSave = { onSetAccountBalance(account.name, it) },
                            onRename = { newName -> onRenameAccount(account.name, newName) },
                            onDelete = { onDeleteAccount(account.name) }
                        )
                    }
                }
            }
        }

        if (owedToMe.isNotEmpty() || iOwe.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.SwapHoriz, "IOUs")
                        Spacer(Modifier.height(10.dp))
                        owedToMe.forEach { loan ->
                            LoanRow(
                                text = "${loan.borrower} owes you ${formatInr(loan.amount)}",
                                accent = Positive,
                                note = loan.note,
                                dueDate = loan.dueDate,
                                onSettle = { onSetLoanSettled(loan.id, true) },
                                onSetDueDate = { date -> onSetLoanDueDate(loan.id, date) }
                            )
                        }
                        iOwe.forEach { loan ->
                            LoanRow(
                                text = "You owe ${loan.lender} ${formatInr(loan.amount)}",
                                accent = Warning,
                                note = loan.note,
                                dueDate = loan.dueDate,
                                onSettle = { onSetLoanSettled(loan.id, true) },
                                onSetDueDate = { date -> onSetLoanDueDate(loan.id, date) }
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel(Icons.Filled.Repeat, "BILLS & CREDIT CARDS")
                    Spacer(Modifier.height(10.dp))
                    BillsSection(
                        bills = sortedBills,
                        nameMe = nameMe,
                        onAddBill = onAddBill,
                        onDeleteBill = onDeleteBill,
                        onMarkPaid = onMarkBillPaid
                    )
                }
            }
        }

        if (goals.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.Flag, "GOALS")
                        Spacer(Modifier.height(10.dp))
                        activeGoals.forEach { goal ->
                            GoalRow(
                                goal = goal,
                                onAddContribution = { amount -> onAddGoalContribution(goal.id, amount) },
                                onMarkReached = { onSetGoalCompleted(goal.id, true) },
                                onDelete = { onDeleteGoal(goal.id) }
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
                                    Row {
                                        TextButton(onClick = { onSetGoalCompleted(goal.id, false) }) { Text("Undo") }
                                        TextButton(onClick = { onDeleteGoal(goal.id) }) { Text("Delete") }
                                    }
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
                    MiniStat("Income", formatInr(summary.totalIncome), Positive)
                    MiniStat("Expenses", formatInr(summary.totalExpenses), Warning)
                    MiniStat("Savings", formatInr(summary.totalSavings), Cyan)
                }
            }
        }

        if (orderedCategorySpend.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.PieChart, "SPEND BY CATEGORY")
                        Spacer(Modifier.height(10.dp))
                        val shown = orderedCategorySpend.take(6)
                        val maxAmount = shown.maxOf { it.second }.coerceAtLeast(1.0)
                        shown.forEach { (category, amount) ->
                            CategorySpendRow(
                                category = category,
                                amount = amount,
                                maxAmount = maxAmount,
                                budgetLimit = budgets[category],
                                onSetLimit = { limit -> onSetBudgetLimit(category, limit) }
                            )
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
                        SectionLabel(Icons.Filled.Shield, "EMERGENCY FUND")
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
                            SectionLabel(Icons.Filled.Repeat, "EMIS & RECURRING — MONTHLY → YEARLY")
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
                            SectionLabel(Icons.Filled.VerifiedUser, "POLICIES & INVESTMENTS")
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

            if (trend.size > 1) {
                item {
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SectionLabel(Icons.Filled.PieChart, "TRENDS — LEFT OVER BY MONTH")
                            Spacer(Modifier.height(12.dp))
                            val maxAbs = trend.maxOf { Math.abs(it.second.surplus) }.coerceAtLeast(1.0)
                            trend.forEach { (label, monthSummary) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                                    Box(Modifier.weight(1f).height(8.dp).padding(horizontal = 6.dp)) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(fraction = (Math.abs(monthSummary.surplus) / maxAbs).toFloat().coerceIn(0f, 1f))
                                                .height(8.dp)
                                                .background(
                                                    if (monthSummary.surplus >= 0) Positive else Warning,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                    Text(formatInr(monthSummary.surplus), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                            SectionLabel(Icons.Filled.Lightbulb, "INSIGHTS")
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
private fun HeroStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier, gradient: Boolean = false) {
    val bg = if (gradient) {
        Modifier.background(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent), radius = 240f),
            androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        )
    } else Modifier
    GlassSurface(modifier = modifier.then(bg)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** One segment of the Personal/Joint pill toggle — a clearly-filled mint state when selected. */
@Composable
private fun PillToggleOption(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(
                if (selected) Brush.linearGradient(listOf(Positive, Cyan.copy(alpha = 0.85f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF04211C) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BillsSection(bills: List<Bill>, nameMe: String, onAddBill: (Bill) -> Unit, onDeleteBill: (String) -> Unit, onMarkPaid: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) }

    if (bills.isEmpty() && !adding) {
        Text("No EMIs or credit cards tracked yet.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }

    bills.forEach { bill ->
        var confirmingPaid by remember(bill.id) { mutableStateOf(false) }
        val overdue = bill.dueDate.isNotBlank() && bill.dueDate < today
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(bill.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatInr(bill.amount)} · due ${bill.dueDate}" +
                            (if (overdue) " — overdue" else "") +
                            (bill.toAccountName?.let { " · sets aside into $it" } ?: (bill.accountName?.let { " · $it" } ?: "")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (confirmingPaid) {
                    Row {
                        TextButton(onClick = { onMarkPaid(bill.id); confirmingPaid = false }) { Text("Yes, paid") }
                        TextButton(onClick = { confirmingPaid = false }) { Text("No") }
                    }
                } else {
                    Row {
                        TextButton(onClick = { confirmingPaid = true }) { Text("Mark Paid") }
                        TextButton(onClick = { onDeleteBill(bill.id) }) { Text("Delete") }
                    }
                }
            }
            if (confirmingPaid) {
                val debitNote = bill.accountName?.let { "debits ${formatInr(bill.amount)} from $it" }
                val creditNote = bill.toAccountName?.let { "moves ${formatInr(bill.amount)} into $it" }
                val note = listOfNotNull(debitNote, creditNote).joinToString(" and ")
                if (note.isNotBlank()) {
                    Text("This $note.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (adding) {
        AddBillForm(
            onAdd = { bill -> onAddBill(bill); adding = false },
            onCancel = { adding = false }
        )
    } else {
        TextButton(onClick = { adding = true }) { Text("+ Add EMI / credit card") }
    }
}

@Composable
private fun AddBillForm(onAdd: (Bill) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var toAccountName by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BillType.EMI) }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. ICICI EMI, HDFC Card)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BillType.EMI, BillType.CREDIT_CARD, BillType.OTHER).forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.name.replace("_", " ")) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() } },
            label = { Text("Amount due") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due date (yyyy-MM-dd)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = accountName,
            onValueChange = { accountName = it },
            label = { Text("Debit from account (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = toAccountName,
            onValueChange = { toAccountName = it },
            label = { Text("Or: set aside into account (sinking fund, optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Use \"debit from\" to pay an external bill. Use \"set aside into\" to move the monthly share of a yearly cost into a savings account ahead of time.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@TextButton
                    if (name.isBlank() || dueDate.isBlank() || amt <= 0) return@TextButton
                    onAdd(
                        Bill(
                            name = name.trim(),
                            amount = amt,
                            dueDate = dueDate.trim(),
                            accountName = accountName.trim().ifBlank { null },
                            toAccountName = toAccountName.trim().ifBlank { null },
                            type = type
                        )
                    )
                },
                enabled = name.isNotBlank() && dueDate.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Add") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Small icon + all-caps label used for every card's section heading, for quick visual scanning. */
@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoanRow(text: String, accent: Color, note: String, dueDate: String?, onSettle: () -> Unit, onSetDueDate: (String?) -> Unit) {
    var editingDate by remember { mutableStateOf(false) }
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) }
    val overdue = dueDate != null && dueDate < today

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = accent, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSettle) { Text("Settle") }
        }
        if (note.isNotBlank()) {
            Text(note, style = MaterialTheme.typography.bodySmall)
        }
        if (editingDate) {
            var input by remember { mutableStateOf(dueDate ?: "") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Due date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSetDueDate(input.trim().ifBlank { null }); editingDate = false }) { Text("Save") }
                TextButton(onClick = { editingDate = false }) { Text("Cancel") }
            }
        } else {
            Text(
                if (dueDate != null) "Due $dueDate" + if (overdue) " — overdue" else "" else "Set a due date",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { editingDate = true }
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold, color = accent)
    }
}

/** One category's spend bar, with an optional monthly budget limit (tap to set/edit, shown in red once exceeded). */
@Composable
private fun CategorySpendRow(category: String, amount: Double, maxAmount: Double, budgetLimit: Double?, onSetLimit: (Double) -> Unit) {
    var editingLimit by remember(category) { mutableStateOf(false) }
    val overBudget = budgetLimit != null && amount > budgetLimit

    Column(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(category, style = MaterialTheme.typography.bodySmall)
            Text(
                formatInr(amount),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (overBudget) Warning else Color.Unspecified
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x0FFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))) {
            // No budget set: track stays muted/unfilled rather than implying progress against nothing.
            if (budgetLimit != null) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(
                            if (overBudget) Brush.horizontalGradient(listOf(Color(0xFFC85A44), Warning)) else Brush.horizontalGradient(listOf(Color(0xFF1FB39A), Cyan)),
                            androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        if (editingLimit) {
            var input by remember(category) { mutableStateOf(budgetLimit?.toInt()?.toString() ?: "") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Monthly budget") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSetLimit(input.toDoubleOrNull() ?: 0.0); editingLimit = false }) { Text("Save") }
                TextButton(onClick = { editingLimit = false }) { Text("Cancel") }
            }
        } else {
            Text(
                if (budgetLimit != null) "Budget: ${formatInr(budgetLimit)}" + if (overBudget) " — over" else "" else "Set a budget",
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { editingLimit = true }
            )
        }
    }
}

@Composable
private fun BalanceChip(account: Account, onSave: (Double) -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var editing by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .widthIn(min = 130.dp)
            .heightIn(min = 96.dp)
            .animateContentSize()
            .clickable(enabled = !editing) { editing = true },
        cornerRadius = 18,
        contentPadding = 14
    ) {
        if (editing) {
            BalanceEditForm(
                account = account,
                onSave = { onSave(it); editing = false },
                onRename = { onRename(it); editing = false },
                onDelete = { onDelete(); editing = false },
                onCancel = { editing = false }
            )
        } else {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Column {
                    Text(
                        formatInr(account.balance),
                        fontWeight = FontWeight.Bold,
                        color = if (account.balance < 0) Warning else Positive
                    )
                    if (account.lastEditedBy.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("by ${account.lastEditedBy}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Separate composable so it's only entered (and its LaunchedEffect fires) exactly once per
 * edit session, right as the field mounts - a top-level effect with a delay hack was unreliable.
 */
@Composable
private fun BalanceEditForm(
    account: Account,
    onSave: (Double) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf(if (account.balance == 0.0) "" else account.balance.toInt().toString()) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(account.name) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column {
        if (renaming) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier.width(140.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = { if (nameInput.isNotBlank()) onRename(nameInput) }) { Text("Save") }
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            }
        } else if (confirmingDelete) {
            Text("Delete ${account.name}?", style = MaterialTheme.typography.bodySmall)
            Text("Past entries keep their history.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        } else {
            Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } },
                singleLine = true,
                modifier = Modifier.width(120.dp).focusRequester(focusRequester)
            )
            Spacer(Modifier.height(4.dp))
            Row { TextButton(onClick = { onSave(input.toDoubleOrNull() ?: account.balance) }) { Text("Save") } }
            Row {
                TextButton(onClick = { renaming = true }) { Text("Rename") }
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
                TextButton(onClick = onCancel) { Text("Close") }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, onAddContribution: (Double) -> Unit, onMarkReached: () -> Unit, onDelete: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val progress = if (goal.targetAmount > 0) (goal.savedSoFar / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0

    Column(Modifier.padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(goal.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(formatInr(goal.savedSoFar) + " / " + formatInr(goal.targetAmount), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatInr(Calculations.goalMonthlyNeeded(goal))}/mo to stay on track · ${Calculations.goalMonthsRemaining(goal)} months left",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        if (adding) {
            GoalContributionForm(
                onAdd = { amount -> onAddContribution(amount); adding = false },
                onCancel = { adding = false }
            )
        } else if (confirmingDelete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delete this goal?", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        } else {
            Row {
                TextButton(onClick = { adding = true }) { Text("Add contribution") }
                TextButton(onClick = onMarkReached) { Text("Mark as reached") }
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
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

@Composable
private fun CommitmentRow(
    item: Calculations.CommitmentChecklistItem,
    defaultAccount: String?,
    onComplete: (Entry) -> Unit,
    onUndo: (String) -> Unit
) {
    val isSinking = item.template.toAccountName != null
    val actionText = when {
        item.template.toAccountName != null -> "Set Aside"
        item.template.bucket == Bucket.JOINT -> "Transfer"
        else -> "Pay"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.template.note.ifBlank { item.template.category }, fontWeight = FontWeight.SemiBold)
            val subText = if (isSinking) {
                "${formatInr(item.monthlyAmount)}/mo (sinking to ${item.template.toAccountName})"
            } else if (item.template.frequency == com.household.finance.data.Frequency.ANNUAL) {
                "${formatInr(item.monthlyAmount)}/mo (yearly ${formatInr(item.template.amount)})"
            } else {
                "${formatInr(item.monthlyAmount)}/mo"
            }
            Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (item.isCompletedThisMonth) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅ Done", color = Positive, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { item.completedEntryId?.let { onUndo(it) } }) {
                    Text("Undo")
                }
            }
        } else {
            Button(
                onClick = { onComplete(item.template) }
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun ActiveLoanCard(
    loan: com.household.finance.data.ActiveLoan,
    onDelete: () -> Unit,
    onUpdatePrepayment: (Double) -> Unit
) {
    var extraInput by remember(loan.extraPrepayment) { mutableStateOf(loan.extraPrepayment.toInt().toString()) }
    val extraVal = extraInput.toDoubleOrNull() ?: 0.0

    val simResult = Calculations.simulateAmortization(
        principal = loan.principal,
        annualRatePct = loan.interestRate,
        monthlyEmi = loan.monthlyEmi,
        extraMonthly = extraVal
    )

    GlassSurface(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(loan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Violet)
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Principal", style = MaterialTheme.typography.labelSmall)
                    Text(formatInr(loan.principal), fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Interest Rate", style = MaterialTheme.typography.labelSmall)
                    Text("${loan.interestRate}%", fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Monthly EMI", style = MaterialTheme.typography.labelSmall)
                    Text(formatInr(loan.monthlyEmi) + "/mo", fontWeight = FontWeight.SemiBold)
                }
            }

            Divider(color = Color(0x11FFFFFF))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = extraInput,
                    onValueChange = { 
                        extraInput = it.filter { c -> c.isDigit() }
                        val d = it.toDoubleOrNull() ?: 0.0
                        onUpdatePrepayment(d)
                    },
                    label = { Text("Extra monthly prepayment (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(Color(0x11FFFFFF))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("INTEREST SAVED", style = MaterialTheme.typography.labelSmall, color = Positive)
                        Text(formatInr(simResult.interestSaved), fontWeight = FontWeight.Bold, color = Positive)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(Color(0x11FFFFFF))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIME SHAVED OFF", style = MaterialTheme.typography.labelSmall, color = Cyan)
                        val yrs = simResult.monthsSaved / 12
                        val mos = simResult.monthsSaved % 12
                        val timeStr = when {
                            yrs > 0 && mos > 0 -> "${yrs}yr ${mos}mo"
                            yrs > 0 -> "${yrs}yr"
                            mos > 0 -> "${mos}mo"
                            else -> "0mo"
                        }
                        Text(timeStr, fontWeight = FontWeight.Bold, color = Cyan)
                    }
                }
            }
            Text(
                "Remaining tenure: ${simResult.monthsRemaining} months (saved ${simResult.monthsSaved} months)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AddLoanForm(onAdd: (ActiveLoan) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }
    var remainingMonths by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add Active EMI Loan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan Name (e.g. ICICI Home Loan)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = principal, onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Remaining Principal (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Annual Interest Rate (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emi, onValueChange = { emi = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly EMI (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = remainingMonths, onValueChange = { remainingMonths = it.filter { c -> c.isDigit() } }, label = { Text("Remaining Tenure (Months)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row {
            Button(onClick = {
                val p = principal.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                val e = emi.toDoubleOrNull() ?: 0.0
                val t = remainingMonths.toIntOrNull() ?: 120
                if (name.isBlank() || p <= 0 || r <= 0 || e <= 0) return@Button
                onAdd(ActiveLoan(name = name, principal = p, interestRate = r, monthlyEmi = e, remainingTenureMonths = t, totalTenureMonths = t))
            }, enabled = name.isNotBlank() && principal.isNotBlank() && rate.isNotBlank() && emi.isNotBlank()) {
                Text("Add Loan")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
