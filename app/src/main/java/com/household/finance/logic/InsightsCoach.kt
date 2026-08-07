package com.household.finance.logic

import com.household.finance.data.Entry

object InsightsCoach {
    /** Threshold-based nudges — always available, no API key required. See [Calculations.budgetNudges]. */
    fun fallbackNudges(entries: List<Entry>): List<String> = Calculations.budgetNudges(entries)
}
