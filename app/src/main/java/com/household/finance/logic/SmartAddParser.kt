package com.household.finance.logic

import com.household.finance.data.Bucket
import com.household.finance.data.DEFAULT_CATEGORIES
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Frequency
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Parses natural-language smart-add text into a draft Entry. `person` is always the device's
 * own identity (nameMe) — this app never lets one phone log an entry as the other partner.
 * Bucket defaults to PERSONAL unless the text explicitly says "joint".
 */
object SmartAddParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val savingsKeywords = setOf("rd", "fd", "ppf", "sip", "investment", "invest")
    private val annualKeywords = setOf("annual", "yearly", "/yr", "per year")
    private val knownBanks = setOf(
        "icici", "sbi", "hdfc", "axis", "kotak", "idfc", "yes bank", "federal", "rbl",
        "canara", "pnb", "union bank", "boi", "indian bank", "paytm", "phonepe", "cash"
    )

    private fun extractAccount(lower: String): String? =
        knownBanks.firstOrNull { lower.contains(it) }?.uppercase()

    /** Offline rule-based parse. Always available, no network / API key required. */
    fun parseRuleBased(text: String, nameMe: String, nameWife: String, categories: List<String> = DEFAULT_CATEGORIES): Entry {
        val lower = text.lowercase(Locale.ROOT)

        val amount = extractAmount(lower)

        val category = categories.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) }
            ?: when {
                lower.contains("emi") -> "EMI"
                lower.contains("insurance") -> "Health Insurance"
                lower.contains("lic") -> "LIC"
                lower.contains("class") -> "Music Classes"
                lower.contains("grocer") -> "Groceries"
                lower.contains("eat") || lower.contains("restaurant") || lower.contains("dine") -> "Eating Out"
                lower.contains("rd") -> "RD"
                lower.contains("fd") -> "FD"
                lower.contains("ppf") -> "PPF"
                lower.contains("sip") -> "SIP"
                else -> "Other"
            }

        val type = when {
            savingsKeywords.any { lower.contains(it) } || category in setOf("RD", "FD", "PPF", "SIP") -> EntryType.SAVINGS
            lower.contains("salary") || lower.contains("income") -> EntryType.INCOME
            else -> EntryType.EXPENSE
        }

        val frequency = if (annualKeywords.any { lower.contains(it) }) Frequency.ANNUAL else Frequency.MONTHLY

        // Personal by default; only Joint if explicitly said - no confirmation asked either way.
        val bucket = if (lower.contains("joint")) Bucket.JOINT else Bucket.PERSONAL

        return Entry(
            person = nameMe,
            type = type,
            bucket = bucket,
            category = category,
            amount = amount,
            frequency = frequency,
            note = text,
            accountName = extractAccount(lower)
        )
    }

    private fun extractAmount(lower: String): Double {
        val kMatch = Regex("""(\d+(?:\.\d+)?)\s*k""").find(lower)
        if (kMatch != null) {
            return kMatch.groupValues[1].toDouble() * 1000.0
        }
        val plain = Regex("""(\d+(?:,\d+)*(?:\.\d+)?)""").find(lower)
        if (plain != null) {
            return plain.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    /**
     * Optional AI-assisted parse via OpenAI gpt-4o-mini. Throws on any failure;
     * caller should catch and fall back to [parseRuleBased]. `person` is always
     * overridden to nameMe regardless of what the model returns.
     */
    fun parseWithOpenAi(text: String, apiKey: String, nameMe: String, nameWife: String, categories: List<String> = DEFAULT_CATEGORIES): Entry {
        val systemPrompt = """
            You convert a short household-finance note into strict JSON with keys:
            type (INCOME, EXPENSE, or SAVINGS), bucket (JOINT or PERSONAL - default to PERSONAL
            unless the text explicitly says "joint"), category (pick the single best match from this
            exact list: ${categories.joinToString(", ")}), amount (number, INR), frequency (MONTHLY or ANNUAL),
            note (short string), account (bank/account name if mentioned in the text, e.g. "ICICI", "SBI", else null).
            RD, FD, PPF, SIP, LIC, Mutual Funds, Stocks, Gold are SAVINGS not EXPENSE.
            Reply with ONLY the JSON object, no prose.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", text) })
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
            val content = JSONObject(bodyStr)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val json = JSONObject(content)
            return Entry(
                person = nameMe,
                type = runCatching { EntryType.valueOf(json.getString("type")) }.getOrDefault(EntryType.EXPENSE),
                bucket = runCatching { Bucket.valueOf(json.getString("bucket")) }.getOrDefault(Bucket.PERSONAL),
                category = json.optString("category", "Other"),
                amount = json.optDouble("amount", 0.0),
                frequency = runCatching { Frequency.valueOf(json.getString("frequency")) }.getOrDefault(Frequency.MONTHLY),
                note = json.optString("note", text),
                accountName = json.optString("account", "").ifBlank { null }.takeIf { it != "null" }
            )
        }
    }
}
