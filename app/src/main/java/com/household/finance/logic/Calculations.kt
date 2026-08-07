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

    /** Every non-discretionary, recurring commitment (EMI, insurance, LIC, RD/FD/PPF/SIP, rent, etc.),
     *  each with its monthly figure and the annualized total — for budgeting a whole year at a glance. */
    fun recurringCommitments(entries: List<Entry>): List<RecurringItem> =
        entries
            .filter { it.category in RECURRING_CATEGORIES }
            .map { RecurringItem(it, it.monthlyAmount, it.monthlyAmount * 12.0) }
            .sortedByDescending { it.monthlyAmount }

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
}
