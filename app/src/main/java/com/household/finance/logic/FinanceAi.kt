package com.household.finance.logic

import com.household.finance.data.Entry
import com.household.finance.data.Goal
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String) // role: "user" or "assistant"

data class AnomalyFlag(val entryId: String, val label: String, val reason: String)

/** Either a plain answer, a parsed transaction, a direct balance statement, or a new savings goal. */
data class ChatResult(
    val replyText: String?,
    val draftEntry: Entry?,
    val balanceUpdate: BalanceUpdate? = null,
    val goalDraft: Goal? = null
)

data class BalanceUpdate(val account: String, val balance: Double)

/**
 * Number of monthly contributions from NEXT month through the target month/year inclusive -
 * plain calendar arithmetic, not an AI estimate. E.g. today is Aug 2026, target Jan 2027 ->
 * Sep, Oct, Nov, Dec, Jan = 5 months.
 */
private fun monthsUntilInclusive(targetMonth: Int, targetYear: Int): Int {
    val now = Calendar.getInstance()
    val currentTotal = now.get(Calendar.YEAR) * 12 + (now.get(Calendar.MONTH) + 1)
    val targetTotal = targetYear * 12 + targetMonth.coerceIn(1, 12)
    return (targetTotal - currentTotal).coerceAtLeast(1)
}

/**
 * All OpenAI-backed features, gpt-4o-mini only, called strictly on user action
 * (never automatically) to keep API cost near zero for a household use case.
 */
object FinanceAi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun chatCompletion(apiKey: String, systemPrompt: String, userPrompt: String, temperature: Double = 0.3): String {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", temperature)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
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

    private fun entriesDigest(entries: List<Entry>): String {
        if (entries.isEmpty()) return "No entries recorded yet."
        return entries.joinToString("\n") {
            "${it.person} | ${it.type} | ${it.bucket} | ${it.category} | ₹${it.amount.toInt()} " +
                "(${it.frequency}, ₹${it.monthlyAmount.toInt()}/mo effective)" +
                (if (it.note.isNotBlank()) " | note: ${it.note}" else "")
        }
    }

    /**
     * "Chat with your finances" — doubles as chat-based entry: if the message describes a transaction
     * ("paid 22k emi", "add 4500 wife music class"), returns a draft [Entry] to confirm instead of text.
     * Otherwise answers the question grounded only in the household's real entries.
     */
    fun chat(
        question: String,
        history: List<ChatMessage>,
        entries: List<Entry>,
        accounts: List<com.household.finance.data.Account>,
        categories: List<String>,
        nameMe: String,
        nameWife: String,
        apiKey: String
    ): ChatResult {
        val accountsDigest = if (accounts.isEmpty()) "No accounts tracked yet." else
            accounts.joinToString(", ") { "${it.name}: ₹${it.balance.toInt()}" }

        val system = """
            You are a household budgeting assistant for an Indian married couple using an app called
            Household Finance. Amounts are in INR.

            If the user's latest message is REPORTING A NEW TRANSACTION (an expense, income, or saving —
            e.g. "paid 22k emi", "22k emi from icici", "20k rd"), reply with ONLY a single line:
            ENTRY:{"type":"INCOME|EXPENSE|SAVINGS","bucket":"JOINT|PERSONAL","category":"one of: ${categories.joinToString(", ")}","amount":number,"frequency":"MONTHLY|ANNUAL","note":"short string","account":"bank/account name if mentioned, else null"}
            Default bucket to PERSONAL unless the user explicitly says "joint".
            RD, FD, PPF, SIP, LIC, Mutual Funds, Stocks, Gold are SAVINGS not EXPENSE.

            If the user's latest message is DIRECTLY STATING AN ACCOUNT'S BALANCE (not a transaction —
            e.g. "sbi balance is 50k", "icici has 2 lakhs", "hdfc balance 30000"), reply with ONLY a single line:
            BALANCE:{"account":"bank/account name","balance":number}

            If the user's latest message is DESCRIBING A NEW SAVINGS GOAL with a target amount and a target
            month/year (e.g. "add goal to buy a car in 2027 jan with down payment of 100000"), reply with
            ONLY a single line - extract the fields exactly as stated, do NOT compute months or a monthly
            figure yourself (that's done in code, not by you):
            GOAL:{"title":"short name","targetAmount":number,"targetMonth":1-12,"targetYear":number,"note":"short string"}

            For any of these three cases: no prose, no markdown fences, just that one line.

            Otherwise, answer the user's question using ONLY the data below — never invent numbers.
            Be concise (2-5 sentences), warm, neutral, never judgmental about spending choices.
            If the data doesn't cover the question, say so plainly.

            Current entries:
            ${entriesDigest(entries)}

            Current account balances:
            $accountsDigest
        """.trimIndent()

        val historyText = history.takeLast(6).joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = if (historyText.isBlank()) question else "$historyText\nuser: $question"
        val raw = chatCompletion(apiKey, system, prompt, temperature = 0.2).trim()

        if (raw.startsWith("ENTRY:")) {
            val jsonText = raw.removePrefix("ENTRY:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                Entry(
                    person = nameMe,
                    type = runCatching { com.household.finance.data.EntryType.valueOf(json.getString("type")) }
                        .getOrDefault(com.household.finance.data.EntryType.EXPENSE),
                    bucket = runCatching { com.household.finance.data.Bucket.valueOf(json.getString("bucket")) }
                        .getOrDefault(com.household.finance.data.Bucket.PERSONAL),
                    category = json.optString("category", categories.firstOrNull() ?: "Other"),
                    amount = json.optDouble("amount", 0.0),
                    frequency = runCatching { com.household.finance.data.Frequency.valueOf(json.getString("frequency")) }
                        .getOrDefault(com.household.finance.data.Frequency.MONTHLY),
                    note = json.optString("note", question),
                    accountName = json.optString("account", "").ifBlank { null }.takeIf { it != "null" }
                )
            }.getOrNull()
            if (parsed != null && parsed.amount > 0) return ChatResult(null, parsed)
        }

        if (raw.startsWith("BALANCE:")) {
            val jsonText = raw.removePrefix("BALANCE:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val account = json.optString("account", "").trim()
                val balance = json.optDouble("balance", Double.NaN)
                if (account.isBlank() || balance.isNaN()) null else BalanceUpdate(account, balance)
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, parsed)
        }

        if (raw.startsWith("GOAL:")) {
            val jsonText = raw.removePrefix("GOAL:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val targetAmount = json.optDouble("targetAmount", Double.NaN)
                val targetMonth = json.optInt("targetMonth", -1)
                val targetYear = json.optInt("targetYear", -1)
                if (targetAmount.isNaN() || targetMonth !in 1..12 || targetYear < 2000) return@runCatching null
                val months = monthsUntilInclusive(targetMonth, targetYear)
                Goal(
                    title = json.optString("title", question.take(40)),
                    targetAmount = targetAmount,
                    targetMonths = months,
                    monthlyContribution = (targetAmount / months)
                )
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, parsed)
        }

        return ChatResult(raw, null)
    }

    /** Smart budget suggestions: a proposed monthly cap per category based on actual spend. */
    fun suggestBudgets(entries: List<Entry>, apiKey: String): String {
        val expenseDigest = entriesDigest(entries.filter { it.type.name == "EXPENSE" })
        val system = """
            You are a budgeting assistant for an Indian household. Based on the expense entries below,
            propose a realistic monthly budget cap (INR) for each distinct category, with a one-line reason.
            Be practical, not aggressive — round to sensible numbers. Format as a short bulleted list,
            one line per category: "Category: ₹X/mo — reason". No preamble, no summary paragraph after.
        """.trimIndent()
        return chatCompletion(apiKey, system, expenseDigest, temperature = 0.3)
    }

    /** Anomaly detection: flags entries that look like duplicates, outliers, or miscategorized. */
    fun detectAnomalies(entries: List<Entry>, apiKey: String): List<AnomalyFlag> {
        val digest = entries.joinToString("\n") { "${it.id} | ${it.person} | ${it.type} | ${it.category} | ₹${it.amount.toInt()} | ${it.frequency}" }
        val system = """
            Review this list of household finance entries (id | person | type | category | amount | frequency).
            Flag entries that look like: likely duplicates, unusually large for their category, or possibly
            miscategorized. Reply with ONLY a JSON array, each item: {"id": "...", "label": "short tag",
            "reason": "one sentence"}. If nothing stands out, reply with []. No prose, no markdown fences.
        """.trimIndent()
        val raw = chatCompletion(apiKey, system, digest, temperature = 0.2)
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            AnomalyFlag(
                entryId = obj.optString("id"),
                label = obj.optString("label", "Flagged"),
                reason = obj.optString("reason", "")
            )
        }
    }

    /** Suggests the single best category for a note/amount, from the household's current category list. */
    fun suggestCategory(note: String, availableCategories: List<String>, apiKey: String): String {
        val system = """
            Pick exactly ONE category from this list that best fits the note below:
            ${availableCategories.joinToString(", ")}
            Reply with ONLY the category text, exactly as written in the list, nothing else.
        """.trimIndent()
        val result = chatCompletion(apiKey, system, note.ifBlank { "Other" }, temperature = 0.0)
        return availableCategories.firstOrNull { it.equals(result.trim(), ignoreCase = true) }
            ?: availableCategories.first()
    }

    /**
     * Goal planning: the model only extracts title/amount/target month-year from free text -
     * the number of months and monthly contribution are computed in code (plain arithmetic),
     * never estimated by the model. If no target date is given, defaults to 12 months out.
     */
    fun planGoal(description: String, apiKey: String): Goal {
        val system = """
            Extract fields from this household savings goal description into strict JSON:
            {"title": "short name", "targetAmount": number, "targetMonth": 1-12 or null, "targetYear": number or null}.
            Currency is INR, no symbols in numbers. If no target date is mentioned, use null for both.
            Do not compute months or a monthly figure - just extract what's stated. Reply with ONLY the JSON object.
        """.trimIndent()
        val content = chatCompletion(apiKey, system, description, temperature = 0.2)
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(content)
        val targetMonth = if (json.isNull("targetMonth")) -1 else json.optInt("targetMonth", -1)
        val targetYear = if (json.isNull("targetYear")) -1 else json.optInt("targetYear", -1)
        val months = if (targetMonth in 1..12 && targetYear >= 2000) monthsUntilInclusive(targetMonth, targetYear) else 12
        val targetAmount = json.optDouble("targetAmount", 0.0)
        return Goal(
            title = json.optString("title", description.take(40)),
            targetAmount = targetAmount,
            targetMonths = months,
            monthlyContribution = if (months > 0) targetAmount / months else 0.0
        )
    }
}
