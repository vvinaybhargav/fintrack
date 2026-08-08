package com.household.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.unit.sp
import com.household.finance.data.Account
import com.household.finance.data.ActiveLoan
import com.household.finance.data.Bill
import com.household.finance.data.BillType
import com.household.finance.data.Bucket
import com.household.finance.data.CategoryListLength
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Frequency
import com.household.finance.data.Goal
import com.household.finance.data.INVESTMENT_CATEGORIES
import com.household.finance.data.Loan
import com.household.finance.data.PolicyStatus
import com.household.finance.data.categoriesFor
import com.household.finance.logic.Calculations
import com.household.finance.logic.InsightsCoach
import com.household.finance.logic.SmartAddParser
import com.household.finance.ui.theme.Cyan
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.InkRaised
import com.household.finance.ui.theme.Positive
import com.household.finance.ui.theme.Violet
import com.household.finance.ui.theme.Warning
import java.util.Calendar
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
    bills: List<Bill>,
    categoryLength: CategoryListLength,
    jointFundAmount: Double,
    personalFundAmount: Double,
    budgets: Map<String, Double>,
    nameMe: String,
    defaultAccount: String?,
    activeLoans: List<ActiveLoan>,
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
    onAddActiveLoan: (ActiveLoan) -> Unit,
    onDeleteActiveLoan: (String) -> Unit,
    onUpdateActiveLoanPrepayment: (String, Double) -> Unit,
    onQuickAddEntry: (Entry) -> Unit = {},
    onDeleteCommitmentTemplate: (String) -> Unit = {}
) {
    val commitmentsChecklist = remember(entries, nameMe) {
        Calculations.getCommitmentsChecklist(entries, nameMe)
    }
    var view by remember { mutableStateOf(DashboardView.PERSONAL) }
    var balanceVisible by remember { mutableStateOf(true) }
    var quickAddText by remember { mutableStateOf("") }
    var quickAddConfirm by remember { mutableStateOf<String?>(null) }
    val categories = remember(categoryLength) { categoriesFor(categoryLength) }

    val currentMonthExpenses = remember(entries) {
        val cal = Calendar.getInstance()
        val curYr = cal.get(Calendar.YEAR)
        val curMo = cal.get(Calendar.MONTH)
        entries.filter {
            val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            eCal.get(Calendar.YEAR) == curYr && eCal.get(Calendar.MONTH) == curMo && it.type == EntryType.EXPENSE
        }
    }
    val categorySpends = remember(currentMonthExpenses) {
        currentMonthExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }
    val categoriesWithBudgetsOrSpend = remember(budgets, categorySpends) {
        (budgets.keys + categorySpends.keys).distinct().sorted()
    }

    val totalAcrossAccounts = remember(accounts) { accounts.sumOf { it.balance } }

    val emergencyFundAmount = if (view == DashboardView.PERSONAL) personalFundAmount else jointFundAmount
    val onSetEmergencyFund = if (view == DashboardView.PERSONAL) onSetPersonalFund else onSetJointFund
    var efInput by remember(emergencyFundAmount) { mutableStateOf(emergencyFundAmount.toInt().toString()) }
    var detailsExpanded by remember { mutableStateOf(false) }

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
    val orderedCategorySpend = remember(summary.categorySpend, categoryLength) {
        val amountByCategory = summary.categorySpend.associate { it.category to it.monthlyAmount }
        categoriesFor(categoryLength).mapNotNull { cat ->
            val amount = amountByCategory[cat] ?: return@mapNotNull null
            if (amount <= 0.0) null else cat to amount
        }
    }
    val owedToMe = remember(loans, nameMe) { loans.filter { !it.settled && it.lender == nameMe } }
    val iOwe = remember(loans, nameMe) { loans.filter { !it.settled && it.borrower == nameMe } }
    val activeGoals = remember(goals) { goals.filter { !it.completed } }
    val completedGoals = remember(goals) { goals.filter { it.completed } }
    val trend = remember(filteredEntries) { Calculations.monthlyTrend(filteredEntries) }
    val creditCards = remember(bills) { bills.filter { it.type == BillType.CREDIT_CARD }.sortedBy { it.dueDate } }
    val otherBills = remember(bills) { bills.filter { it.type != BillType.CREDIT_CARD }.sortedBy { it.dueDate } }
    val myActiveLoans = remember(activeLoans, nameMe) { activeLoans.filter { it.owner == nameMe } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Top bar: avatar + greeting + bucket toggle ---
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).background(Violet, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(nameMe.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Good to see you", style = MaterialTheme.typography.bodySmall)
                        Text(nameMe, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    Modifier
                        .background(InkRaised, RoundedCornerShape(999.dp))
                        .padding(4.dp)
                ) {
                    PillToggleOption(text = nameMe, selected = view == DashboardView.PERSONAL) { view = DashboardView.PERSONAL }
                    PillToggleOption(text = "Joint", selected = view == DashboardView.JOINT) { view = DashboardView.JOINT }
                }
            }
        }

        // --- Quick add search bar ---
        item {
            Column {
                GlassSurface(cornerRadius = 999, contentPadding = 10) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = quickAddText,
                            onValueChange = { quickAddText = it },
                            placeholder = { Text("Add a transaction… e.g. 22k EMI") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Violet)
                                .clickable {
                                    if (quickAddText.isNotBlank()) {
                                        val entry = SmartAddParser.parseRuleBased(quickAddText, nameMe, "", categories)
                                        onQuickAddEntry(entry)
                                        quickAddConfirm = "Logged ${entry.category} — ${formatInr(entry.amount)}"
                                        quickAddText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                quickAddConfirm?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
            }
        }

        // --- Total across accounts hero ---
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth().then(
                Modifier.background(
                    Brush.radialGradient(listOf(Violet.copy(alpha = 0.16f), Color.Transparent), radius = 260f),
                    RoundedCornerShape(20.dp)
                )
            )) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text("TOTAL ACROSS ACCOUNTS", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { balanceVisible = !balanceVisible }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (balanceVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle balance visibility",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (balanceVisible) formatInr(totalAcrossAccounts) else "••••••",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 34.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Across ${accounts.size} accounts", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // --- Accounts ---
        if (accounts.isNotEmpty()) {
            item { SectionLabel(Icons.Filled.AccountBalanceWallet, "Accounts") }
            items(accounts, key = { it.name }) { account ->
                AccountRow(
                    account = account,
                    onSave = { onSetAccountBalance(account.name, it) },
                    onRename = { newName -> onRenameAccount(account.name, newName) },
                    onDelete = { onDeleteAccount(account.name) }
                )
            }
        }

        // --- This month: 2x2 stat grid ---
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 0) {
                Column {
                    Text("This month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth()) {
                            StatCell("INCOME", formatInr(summary.totalIncome), Positive, Modifier.weight(1f))
                            StatCell("EXPENSES", formatInr(summary.totalExpenses), Warning, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatCell("SAVINGS", formatInr(summary.totalSavings), Cyan, Modifier.weight(1f))
                            StatCell("LEFT OVER", formatInr(summary.surplus), if (summary.surplus >= 0) Positive else Warning, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // --- Category budgets ---
        if (categoriesWithBudgetsOrSpend.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.PieChart, "Category budgets")
                        Spacer(Modifier.height(12.dp))
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
                            var editingLimit by remember(category) { mutableStateOf(false) }

                            Column(Modifier.padding(bottom = 14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (limit != null) "${formatInr(spent)} / ${formatInr(limit)}" else formatInr(spent),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable { editingLimit = true }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x11FFFFFF))) {
                                    if (limit != null && limit > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(fraction = progress.toFloat().coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(progressColor)
                                        )
                                    }
                                }
                                if (editingLimit) {
                                    var input by remember(category) { mutableStateOf(limit?.toInt()?.toString() ?: "") }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        OutlinedTextField(
                                            value = input,
                                            onValueChange = { input = it.filter { c -> c.isDigit() } },
                                            label = { Text("Monthly limit") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { onSetBudgetLimit(category, input.toDoubleOrNull() ?: 0.0); editingLimit = false }) { Text("Save") }
                                        TextButton(onClick = { editingLimit = false }) { Text("Cancel") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Loans (amortizing bank loans) ---
        item {
            var showingAddLoan by remember { mutableStateOf(false) }
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(Icons.Filled.AccountBalanceWallet, "Loans")
                        TextButton(onClick = { showingAddLoan = !showingAddLoan }) { Text(if (showingAddLoan) "Close" else "+ Add") }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (showingAddLoan) {
                        AddLoanForm(onAdd = { onAddActiveLoan(it); showingAddLoan = false }, onCancel = { showingAddLoan = false })
                        Divider(Modifier.padding(vertical = 12.dp), color = Color(0x11FFFFFF))
                    }
                    if (myActiveLoans.isEmpty()) {
                        Text("No loans tracked yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        myActiveLoans.forEach { loan ->
                            ActiveLoanRow(
                                loan = loan,
                                onDelete = { onDeleteActiveLoan(loan.id) },
                                onSave = { updated -> onAddActiveLoan(updated) }
                            )
                        }
                    }
                }
            }
        }

        // --- Credit cards ---
        item {
            var showingAddCard by remember { mutableStateOf(false) }
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(Icons.Filled.CreditCard, "Credit cards")
                        TextButton(onClick = { showingAddCard = !showingAddCard }) { Text(if (showingAddCard) "Close" else "+ Add") }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (showingAddCard) {
                        AddCreditCardForm(onAdd = { onAddBill(it); showingAddCard = false }, onCancel = { showingAddCard = false })
                        Divider(Modifier.padding(vertical = 12.dp), color = Color(0x11FFFFFF))
                    }
                    if (creditCards.isEmpty()) {
                        Text("No credit cards tracked yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        creditCards.forEach { card ->
                            CreditCardRow(card = card, onDelete = { onDeleteBill(card.id) }, onPay = { onMarkBillPaid(card.id) })
                        }
                    }
                }
            }
        }

        // --- IOUs (between the two of you) ---
        if (owedToMe.isNotEmpty() || iOwe.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.SwapHoriz, "IOUs")
                        Spacer(Modifier.height(10.dp))
                        owedToMe.forEach { loan ->
                            LoanRow(
                                text = "${loan.borrower} owes you ${formatInr(loan.amount)}",
                                accent = Positive, note = loan.note, dueDate = loan.dueDate,
                                onSettle = { onSetLoanSettled(loan.id, true) },
                                onSetDueDate = { date -> onSetLoanDueDate(loan.id, date) }
                            )
                        }
                        iOwe.forEach { loan ->
                            LoanRow(
                                text = "You owe ${loan.lender} ${formatInr(loan.amount)}",
                                accent = Warning, note = loan.note, dueDate = loan.dueDate,
                                onSettle = { onSetLoanSettled(loan.id, true) },
                                onSetDueDate = { date -> onSetLoanDueDate(loan.id, date) }
                            )
                        }
                    }
                }
            }
        }

        // --- Goals ---
        if (goals.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.Flag, "Goals")
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
                    }
                }
            }
        }

        // --- Other bills (EMI / sinking-fund style, non-credit-card) ---
        if (otherBills.isNotEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(Icons.Filled.Repeat, "Bills")
                        Spacer(Modifier.height(10.dp))
                        BillsSection(bills = otherBills, onAddBill = onAddBill, onDeleteBill = onDeleteBill, onMarkPaid = onMarkBillPaid)
                    }
                }
            }
        }

        // --- Confirm this month ---
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel(Icons.Filled.Repeat, "Confirm this month")
                    Text(
                        "Recurring savings & investments — log them once they've gone through.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    if (commitmentsChecklist.isEmpty()) {
                        Text("You haven't set up your monthly commitments checklist yet.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onSeedCommitments) { Text("Set up commitments checklist") }
                    } else {
                        commitmentsChecklist.forEach { item ->
                            CommitmentRow(item, onCompleteCommitment, onUndoCommitment, onDeleteCommitmentTemplate)
                        }
                    }
                }
            }
        }

        if (orderedCategorySpend.isEmpty() && commitmentsChecklist.isEmpty() && accounts.isEmpty()) {
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
                        SectionLabel(Icons.Filled.Shield, "Emergency fund")
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
                            SectionLabel(Icons.Filled.Repeat, "EMIs & recurring")
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
                            SectionLabel(Icons.Filled.VerifiedUser, "Policies & investments")
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
                            SectionLabel(Icons.Filled.PieChart, "Trends")
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
                                                .background(if (monthSummary.surplus >= 0) Positive else Warning, RoundedCornerShape(3.dp))
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
                            SectionLabel(Icons.Filled.Lightbulb, "Insights")
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
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

/** One segment of the Personal/Joint pill toggle — a clearly-filled violet state when selected. */
@Composable
private fun PillToggleOption(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Violet else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccountRow(account: Account, onSave: (Double) -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var input by remember(account.balance, editing) { mutableStateOf(if (account.balance == 0.0) "" else account.balance.toInt().toString()) }
    var nameInput by remember(account.name, editing) { mutableStateOf(account.name) }
    var confirmingDelete by remember { mutableStateOf(false) }

    GlassSurface(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        if (!editing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(account.name, fontWeight = FontWeight.SemiBold)
                        if (account.owner.isNotBlank()) {
                            Text(account.owner, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatInr(account.balance), fontWeight = FontWeight.Bold, color = if (account.balance < 0) Warning else Positive)
                    IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = Violet, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else if (confirmingDelete) {
            Column {
                Text("Delete ${account.name}?", style = MaterialTheme.typography.bodySmall)
                Text("Past entries keep their history.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = onDelete) { Text("Delete") }
                    TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
                }
            }
        } else {
            Column {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = input, onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Balance") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        if (nameInput.isNotBlank() && !nameInput.equals(account.name, ignoreCase = true)) onRename(nameInput)
                        onSave(input.toDoubleOrNull() ?: account.balance)
                        editing = false
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun CreditCardRow(card: Bill, onDelete: () -> Unit, onPay: () -> Unit) {
    var confirmingPaid by remember(card.id) { mutableStateOf(false) }
    var confirmingDelete by remember(card.id) { mutableStateOf(false) }
    val limit = card.creditLimit
    val progress = if (limit != null && limit > 0) (card.amount / limit).coerceIn(0.0, 1.0) else 0.0

    GlassSurface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(card.name, fontWeight = FontWeight.SemiBold)
                    Text("Due ${card.dueDate}" + (card.owner.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatInr(card.amount), fontWeight = FontWeight.Bold)
                    if (confirmingDelete) {
                        TextButton(onClick = onDelete) { Text("Confirm") }
                        TextButton(onClick = { confirmingDelete = false }) { Text("No") }
                    } else {
                        TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
                    }
                }
            }
            if (limit != null && limit > 0) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x11FFFFFF))) {
                    Box(
                        Modifier.fillMaxWidth(fraction = progress.toFloat()).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                            .background(if (progress > 0.8) Warning else Violet)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                card.minDue?.let { Text("Min due ${formatInr(it)}", style = MaterialTheme.typography.bodySmall) }
                if (confirmingPaid) {
                    Row {
                        TextButton(onClick = { onPay(); confirmingPaid = false }) { Text("Yes, paid") }
                        TextButton(onClick = { confirmingPaid = false }) { Text("No") }
                    }
                } else {
                    TextButton(onClick = { confirmingPaid = true }) { Text("Pay") }
                }
            }
        }
    }
}

@Composable
private fun AddCreditCardForm(onAdd: (Bill) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var minDue by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Card name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = limit, onValueChange = { limit = it.filter { c -> c.isDigit() } }, label = { Text("Credit limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = balance, onValueChange = { balance = it.filter { c -> c.isDigit() } }, label = { Text("Current balance") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = minDue, onValueChange = { minDue = it.filter { c -> c.isDigit() } }, label = { Text("Minimum due") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = {
                    val bal = balance.toDoubleOrNull() ?: return@Button
                    if (name.isBlank() || due.isBlank()) return@Button
                    onAdd(
                        Bill(
                            name = name.trim(), amount = bal, dueDate = due.trim(), type = BillType.CREDIT_CARD,
                            creditLimit = limit.toDoubleOrNull(), minDue = minDue.toDoubleOrNull(),
                            owner = owner.trim(), recurring = true
                        )
                    )
                },
                enabled = name.isNotBlank() && due.isNotBlank() && balance.isNotBlank()
            ) { Text("Add card") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun BillsSection(bills: List<Bill>, onAddBill: (Bill) -> Unit, onDeleteBill: (String) -> Unit, onMarkPaid: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) }

    if (bills.isEmpty() && !adding) {
        Text("No EMIs or sinking-fund bills tracked yet.", style = MaterialTheme.typography.bodySmall)
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
        }
    }

    if (adding) {
        AddBillForm(onAdd = { bill -> onAddBill(bill); adding = false }, onCancel = { adding = false })
    } else {
        TextButton(onClick = { adding = true }) { Text("+ Add EMI / bill") }
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
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. ICICI EMI)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BillType.EMI, BillType.OTHER).forEach { option ->
                FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option.name.replace("_", " ")) })
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("Amount due") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Debit from account (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = toAccountName, onValueChange = { toAccountName = it }, label = { Text("Or: set aside into account (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@TextButton
                    if (name.isBlank() || dueDate.isBlank() || amt <= 0) return@TextButton
                    onAdd(Bill(name = name.trim(), amount = amt, dueDate = dueDate.trim(), accountName = accountName.trim().ifBlank { null }, toAccountName = toAccountName.trim().ifBlank { null }, type = type))
                },
                enabled = name.isNotBlank() && dueDate.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Add") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Plain bold section heading, matching the design import's h5 style (no icon, no all-caps). */
@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Due date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.weight(1f))
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
            GoalContributionForm(onAdd = { amount -> onAddContribution(amount); adding = false }, onCancel = { adding = false })
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
    onComplete: (Entry) -> Unit,
    onUndo: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var confirmingDelete by remember(item.template.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(item.template.note.ifBlank { item.template.category }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${item.template.person} · ${formatInr(item.monthlyAmount)}/mo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (item.template.frequency == Frequency.ANNUAL) "Yearly" else "Recurring",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (item.isCompletedThisMonth) {
                TextButton(onClick = { item.completedEntryId?.let { onUndo(it) } }) { Text("Done ✓") }
            } else {
                Button(
                    onClick = { onComplete(item.template) },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) { Text("Confirm") }
            }
            IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove commitment", tint = Violet, modifier = Modifier.size(16.dp))
            }
        }
        if (confirmingDelete) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Stop tracking this commitment?", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onDelete(item.template.id); confirmingDelete = false }) { Text("Remove") }
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        }
        Divider(Modifier.padding(top = 10.dp), color = Color(0x14FFFFFF))
    }
}

@Composable
private fun ActiveLoanRow(loan: ActiveLoan, onDelete: () -> Unit, onSave: (ActiveLoan) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val monthsElapsed = (loan.totalTenureMonths - loan.remainingTenureMonths).coerceIn(0, loan.totalTenureMonths)
    val progress = if (loan.totalTenureMonths > 0) (monthsElapsed.toFloat() / loan.totalTenureMonths).coerceIn(0f, 1f) else 0f

    GlassSurface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize()) {
        if (editing) {
            EditLoanForm(loan, onCancel = { editing = false }, onDelete = { onDelete(); editing = false }, onSave = { onSave(it); editing = false })
        } else {
            Column(Modifier.clickable { expanded = !expanded }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(loan.name, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = {}, label = { Text("Loan", style = MaterialTheme.typography.labelSmall) })
                        }
                        Text(loan.owner, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatInr(loan.monthlyEmi), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Edit, contentDescription = "Edit loan", tint = Violet, modifier = Modifier.size(16.dp)) }
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    Text("$monthsElapsed of ${loan.totalTenureMonths} months paid", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${loan.remainingTenureMonths} months left", style = MaterialTheme.typography.bodySmall)
                        Text(formatInr(loan.principal) + " remaining", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditLoanForm(loan: ActiveLoan, onCancel: () -> Unit, onDelete: () -> Unit, onSave: (ActiveLoan) -> Unit) {
    var name by remember { mutableStateOf(loan.name) }
    var emi by remember { mutableStateOf(loan.monthlyEmi.toInt().toString()) }
    var totalMonths by remember { mutableStateOf(loan.totalTenureMonths.toString()) }
    var remainingMonths by remember { mutableStateOf(loan.remainingTenureMonths.toString()) }

    Column {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = emi, onValueChange = { emi = it.filter { c -> c.isDigit() } }, label = { Text("Monthly EMI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = totalMonths, onValueChange = { totalMonths = it.filter { c -> c.isDigit() } }, label = { Text("Total months") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = remainingMonths, onValueChange = { remainingMonths = it.filter { c -> c.isDigit() } }, label = { Text("Remaining") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = onDelete) { Text("Delete") }
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = {
                onSave(
                    loan.copy(
                        name = name.trim().ifBlank { loan.name },
                        monthlyEmi = emi.toDoubleOrNull() ?: loan.monthlyEmi,
                        totalTenureMonths = totalMonths.toIntOrNull() ?: loan.totalTenureMonths,
                        remainingTenureMonths = remainingMonths.toIntOrNull() ?: loan.remainingTenureMonths
                    )
                )
            }) { Text("Save") }
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
        Text("Add loan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name (e.g. ICICI Home Loan)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = principal, onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Remaining principal (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Annual interest rate (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emi, onValueChange = { emi = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly EMI (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = remainingMonths, onValueChange = { remainingMonths = it.filter { c -> c.isDigit() } }, label = { Text("Remaining tenure (months)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Button(onClick = {
                val p = principal.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                val e = emi.toDoubleOrNull() ?: 0.0
                val t = remainingMonths.toIntOrNull() ?: 120
                if (name.isBlank() || p <= 0 || e <= 0) return@Button
                onAdd(ActiveLoan(name = name, principal = p, interestRate = r, monthlyEmi = e, remainingTenureMonths = t, totalTenureMonths = t))
            }, enabled = name.isNotBlank() && principal.isNotBlank() && emi.isNotBlank()) { Text("Add loan") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
