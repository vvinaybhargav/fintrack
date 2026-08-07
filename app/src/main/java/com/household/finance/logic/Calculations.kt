package com.household.finance.logic

import com.household.finance.data.Bucket
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Goal
import com.household.finance.data.INVESTMENT_CATEGORIES
import com.household.finance.data.PolicyStatus
import com.household.finance.data.RECURRING_CATEGORIES
import java.util.Calendar

data class DashboardSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalSavings: Double,
    val surplus: Double,
    val savingsRatePct: Double,
    val incomeByPerson: Map<String, Double>,
    val expenseByPerson: Map<String, Double>,
    val savingsByPerson: Map<String, Double>,
    val byBucket: Map<Bucket, Double>,
    val incomeRatio: Map<String, Double>, // person -> share of joint costs
    val categorySpend: List<CategoryTotal>
)

data class CategoryTotal(val category: String, val monthlyAmount: Double)

data class RecurringItem(
    val entry: Entry,
    val monthlyAmount: Double,
    val yearlyAmount: Double
)

object Calculations {

    fun summarize(entries: List<Entry>): DashboardSummary {
        val income = entries.filter { it.type == EntryType.INCOME }
        val expenses = entries.filter { it.type == EntryType.EXPENSE }
        val savings = entries.filter { it.type == EntryType.SAVINGS }

        val totalIncome = income.sumOf { it.monthlyAmount }
        val totalExpenses = expenses.sumOf { it.monthlyAmount }
        val totalSavings = savings.sumOf { it.monthlyAmount }
        val surplus = totalIncome - totalExpenses - totalSavings
        val savingsRate = if (totalIncome > 0) (totalSavings / totalIncome) * 100.0 else 0.0

        val incomeByPerson = income.groupBy { it.person }.mapValues { it.value.sumOf { e -> e.monthlyAmount } }
        val expenseByPerson = expenses.groupBy { it.person }.mapValues { it.value.sumOf { e -> e.monthlyAmount } }
        val savingsByPerson = savings.groupBy { it.person }.mapValues { it.value.sumOf { e -> e.monthlyAmount } }

        val byBucket = (expenses + savings).groupBy { it.bucket }.mapValues { it.value.sumOf { e -> e.monthlyAmount } }

        val totalIncomeForRatio = incomeByPerson.values.sum()
        val incomeRatio = if (totalIncomeForRatio > 0) {
            incomeByPerson.mapValues { it.value / totalIncomeForRatio }
        } else emptyMap()

        val categorySpend = (expenses + savings)
            .groupBy { it.category }
            .map { (category, items) -> CategoryTotal(category, items.sumOf { it.monthlyAmount }) }
            .sortedByDescending { it.monthlyAmount }

        return DashboardSummary(
            totalIncome, totalExpenses, totalSavings, surplus, savingsRate,
            incomeByPerson, expenseByPerson, savingsByPerson, byBucket, incomeRatio, categorySpend
        )
    }

    data class CommitmentChecklistItem(
        val template: Entry,
        val monthlyAmount: Double,
        val isCompletedThisMonth: Boolean,
        val completedEntryId: String? = null
    )

    data class AmortizationResult(
        val totalInterestPaid: Double,
        val monthsRemaining: Int,
        val interestSaved: Double,
        val monthsSaved: Int
    )

    /** Every non-discretionary, recurring commitment (EMI, insurance, LIC, RD/FD/PPF/SIP, rent, etc.),
     *  each with its monthly figure and the annualized total — for budgeting a whole year at a glance.
     *  Deduplicated by category and note so only the latest template is shown. */
    fun recurringCommitments(entries: List<Entry>): List<RecurringItem> {
        val recurringEntries = entries.filter { it.category in RECURRING_CATEGORIES }
        val uniqueCommitments = recurringEntries
            .groupBy { it.category + "_" + it.note }
            .map { (_, list) -> list.maxByOrNull { it.createdAt }!! }
        return uniqueCommitments
            .map { RecurringItem(it, it.monthlyAmount, it.monthlyAmount * 12.0) }
            .sortedByDescending { it.monthlyAmount }
    }

    /** Returns the commitments checklist for a given person for the current month. */
    fun getCommitmentsChecklist(entries: List<Entry>, person: String): List<CommitmentChecklistItem> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        // 1. Find all entries belonging to this person (or joint bucket) that are recurring commitments.
        val recurringEntries = entries.filter {
            (it.person.equals(person, ignoreCase = true) || it.bucket == Bucket.JOINT) &&
            it.category in RECURRING_CATEGORIES
        }

        // 2. Group them by category + note. Identify the standing template vs current month's completions.
        val templates = recurringEntries
            .groupBy { it.category + "_" + it.note }
            .map { (_, list) ->
                val sorted = list.sortedBy { it.createdAt }
                val currentMonthEntries = sorted.filter { e ->
                    val cal = Calendar.getInstance().apply { timeInMillis = e.createdAt }
                    cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
                }
                // The template is the oldest one or the one NOT in the current month.
                val template = sorted.firstOrNull { e ->
                    val cal = Calendar.getInstance().apply { timeInMillis = e.createdAt }
                    cal.get(Calendar.YEAR) != currentYear || cal.get(Calendar.MONTH) != currentMonth
                } ?: sorted.first()
                template to currentMonthEntries
            }

        return templates.map { (template, currentMonthEntries) ->
            CommitmentChecklistItem(
                template = template,
                monthlyAmount = template.monthlyAmount,
                isCompletedThisMonth = currentMonthEntries.isNotEmpty(),
                completedEntryId = currentMonthEntries.firstOrNull()?.id
            )
        }.sortedByDescending { it.monthlyAmount }
    }

    /** Simulates month-by-month loan amortization with and without prepayments. */
    fun simulateAmortization(
        principal: Double,
        annualRatePct: Double,
        monthlyEmi: Double,
        extraMonthly: Double = 0.0
    ): AmortizationResult {
        if (principal <= 0.0 || monthlyEmi <= 0.0) {
            return AmortizationResult(0.0, 0, 0.0, 0)
        }
        val r = annualRatePct / 12.0 / 100.0

        // 1. Simulate baseline (no extra prepayment)
        var balanceBase = principal
        var interestBase = 0.0
        var monthsBase = 0
        while (balanceBase > 0.0 && monthsBase < 600) {
            val interestMonth = balanceBase * r
            interestBase += interestMonth
            val principalMonth = (monthlyEmi - interestMonth).coerceAtLeast(0.0)
            if (principalMonth <= 0.0 && r > 0.0) {
                monthsBase = 600 // unable to pay off
                break
            }
            balanceBase -= principalMonth
            monthsBase++
        }

        // 2. Simulate with prepayment
        var balancePre = principal
        var interestPre = 0.0
        var monthsPre = 0
        while (balancePre > 0.0 && monthsPre < 600) {
            val interestMonth = balancePre * r
            interestPre += interestMonth
            val principalMonth = (monthlyEmi - interestMonth).coerceAtLeast(0.0)
            val totalReduction = principalMonth + extraMonthly
            if (totalReduction <= 0.0 && r > 0.0) {
                monthsPre = 600 // unable to pay off
                break
            }
            balancePre -= totalReduction
            monthsPre++
        }

        return AmortizationResult(
            totalInterestPaid = interestPre,
            monthsRemaining = if (monthsPre >= 600) 0 else monthsPre,
            interestSaved = (interestBase - interestPre).coerceAtLeast(0.0),
            monthsSaved = (monthsBase - monthsPre).coerceAtLeast(0)
        )
    }

    fun emergencyFundTarget(totalExpenses: Double): Double = totalExpenses * 6.0

    /** Income/expense/savings totals grouped by the calendar month entries were logged in, oldest first. */
    fun monthlyTrend(entries: List<Entry>, months: Int = 6): List<Pair<String, DashboardSummary>> {
        val format = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US)
        return entries
            .groupBy { format.format(java.util.Date(it.createdAt)) }
            .toList()
            .sortedBy { (_, items) -> items.minOf { it.createdAt } }
            .takeLast(months)
            .map { (label, items) -> label to summarize(items) }
    }

    fun policyStatus(entry: Entry): PolicyStatus {
        if (entry.category !in INVESTMENT_CATEGORIES) return PolicyStatus.ACTIVE
        val year = entry.maturityYear ?: return PolicyStatus.ACTIVE
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return when {
            year < currentYear -> PolicyStatus.PAID_UP
            year - currentYear <= 1 -> PolicyStatus.NEAR_MATURITY
            else -> PolicyStatus.ACTIVE
        }
    }

    /**
     * Months remaining, computed LIVE against today's date - not the fixed count stored at creation.
     * Falls back to the stored [Goal.targetMonths] for goals saved before target dates were tracked.
     */
    fun goalMonthsRemaining(goal: Goal): Int {
        val month = goal.targetMonth
        val year = goal.targetYear
        if (month == null || year == null) return goal.targetMonths.coerceAtLeast(1)
        val now = Calendar.getInstance()
        val currentTotal = now.get(Calendar.YEAR) * 12 + (now.get(Calendar.MONTH) + 1)
        val targetTotal = year * 12 + month.coerceIn(1, 12)
        return (targetTotal - currentTotal).coerceAtLeast(1)
    }

    /** What's needed per month to hit the goal from here, given what's left to save and months remaining. */
    fun goalMonthlyNeeded(goal: Goal): Double {
        val remaining = (goal.targetAmount - goal.savedSoFar).coerceAtLeast(0.0)
        return remaining / goalMonthsRemaining(goal)
    }

    /** Rule-based nudge: category up X% vs its rolling average of the prior N months' entries of the same category. */
    fun budgetNudges(entries: List<Entry>): List<String> {
        val expenseEntries = entries.filter { it.type == EntryType.EXPENSE }
        val byCategory = expenseEntries.groupBy { it.category }
        val nudges = mutableListOf<String>()
        for ((category, catEntries) in byCategory) {
            if (catEntries.size < 2) continue
            val sorted = catEntries.sortedBy { it.createdAt }
            val latest = sorted.last().monthlyAmount
            val priorAvg = sorted.dropLast(1).map { it.monthlyAmount }.average()
            if (priorAvg <= 0) continue
            val changePct = ((latest - priorAvg) / priorAvg) * 100.0
            if (changePct >= 30.0) {
                nudges.add("$category is up ${changePct.toInt()}% vs its average — room to trim.")
            }
        }
        return nudges
    }

    /** Calculates MoM insights for a specific category. */
    fun getCategoryInsights(category: String, entries: List<Entry>, budgetLimit: Double?): String {
        val cal = Calendar.getInstance()
        val curYr = cal.get(Calendar.YEAR)
        val curMo = cal.get(Calendar.MONTH)

        // Current month's spend for this category
        val currentMonthSpent = entries.filter {
            val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            eCal.get(Calendar.YEAR) == curYr && eCal.get(Calendar.MONTH) == curMo &&
            it.type == EntryType.EXPENSE && it.category.equals(category, ignoreCase = true)
        }.sumOf { it.amount }

        // Previous month's spend for this category
        val prevMonthSpent = entries.filter {
            val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            val itemYr = eCal.get(Calendar.YEAR)
            val itemMo = eCal.get(Calendar.MONTH)
            val targetYr = if (curMo == 0) curYr - 1 else curYr
            val targetMo = if (curMo == 0) 11 else curMo - 1
            itemYr == targetYr && itemMo == targetMo &&
            it.type == EntryType.EXPENSE && it.category.equals(category, ignoreCase = true)
        }.sumOf { it.amount }

        // 3-month average spent (excluding current month)
        val historicalSpends = mutableListOf<Double>()
        for (i in 1..3) {
            val targetMonthIndex = curMo - i
            val targetYr = if (targetMonthIndex < 0) curYr - 1 else curYr
            val targetMo = if (targetMonthIndex < 0) 12 + targetMonthIndex else targetMonthIndex
            val spentInMonth = entries.filter {
                val eCal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                eCal.get(Calendar.YEAR) == targetYr && eCal.get(Calendar.MONTH) == targetMo &&
                it.type == EntryType.EXPENSE && it.category.equals(category, ignoreCase = true)
            }.sumOf { it.amount }
            historicalSpends.add(spentInMonth)
        }
        val avg3Months = historicalSpends.filter { it > 0.0 }.average()

        val diffPct = if (prevMonthSpent > 0) {
            (((currentMonthSpent - prevMonthSpent) / prevMonthSpent) * 100).toInt()
        } else 0

        val limitMsg = if (budgetLimit != null) {
            val remaining = (budgetLimit - currentMonthSpent).coerceAtLeast(0.0)
            if (currentMonthSpent > budgetLimit) {
                "You have exceeded your limit of ₹${budgetLimit.toInt()} by ₹${(currentMonthSpent - budgetLimit).toInt()}."
            } else {
                "You have ₹${remaining.toInt()} remaining of your ₹${budgetLimit.toInt()} limit."
            }
        } else {
            "No budget limit is configured for this category. Consider setting one in Settings."
        }

        val trendMsg = when {
            prevMonthSpent == 0.0 -> "This is your first recorded spending in this category recently."
            currentMonthSpent > prevMonthSpent -> "This is higher than last month's spend of ₹${prevMonthSpent.toInt()} (+${diffPct}%)."
            currentMonthSpent < prevMonthSpent -> "This is lower than last month's spend of ₹${prevMonthSpent.toInt()} (${diffPct}%)."
            else -> "This matches last month's spend of ₹${prevMonthSpent.toInt()} exactly."
        }

        val avgMsg = if (!avg3Months.isNaN() && avg3Months > 0) {
            "Your 3-month historical average for $category is ₹${avg3Months.toInt()}."
        } else ""

        return "This month, you spent ₹${currentMonthSpent.toInt()} on $category. $limitMsg $trendMsg $avgMsg"
    }
}
