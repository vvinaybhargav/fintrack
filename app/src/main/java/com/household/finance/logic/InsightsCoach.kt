package com.household.finance.logic

import com.household.finance.data.Entry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object InsightsCoach {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Rule-based fallback summary — no API key required. */
    fun ruleBasedSummary(summary: DashboardSummary): String {
        val fmt = { v: Double -> "₹" + String.format(Locale.US, "%,.0f", v) }
        return buildString {
            append("Household income is ${fmt(summary.totalIncome)}/mo. ")
            append("Expenses are ${fmt(summary.totalExpenses)}/mo and savings/investments are ${fmt(summary.totalSavings)}/mo. ")
            append("That leaves a surplus of ${fmt(summary.surplus)}/mo, ")
            append("a savings rate of ${String.format(Locale.US, "%.1f", summary.savingsRatePct)}%.")
        }
    }

    /** AI-generated plain-English summary. Throws on failure; caller falls back to [ruleBasedSummary]. */
    fun aiSummary(summary: DashboardSummary, entries: List<Entry>, apiKey: String): String {
        val topCategories = entries.groupBy { it.category }
            .mapValues { it.value.sumOf { e -> e.monthlyAmount } }
            .entries.sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key}: ₹${it.value.toInt()}" }

        val prompt = """
            Household income ₹${summary.totalIncome.toInt()}/mo, expenses ₹${summary.totalExpenses.toInt()}/mo,
            savings ₹${summary.totalSavings.toInt()}/mo, surplus ₹${summary.surplus.toInt()}/mo,
            savings rate ${String.format(Locale.US, "%.1f", summary.savingsRatePct)}%.
            Top spend categories: $topCategories.
            Write a warm, neutral, 3-sentence plain-English monthly summary for a married couple's household budget.
            Do not mention diet, health, or lifestyle judgments. Do not blame either partner.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.4)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("OpenAI request failed: ${response.code}")
            val bodyStr = response.body?.string() ?: error("Empty OpenAI response")
            return JSONObject(bodyStr).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        }
    }

    /** Threshold-based nudges — always available. See [Calculations.budgetNudges]. */
    fun fallbackNudges(entries: List<Entry>): List<String> = Calculations.budgetNudges(entries)
}
