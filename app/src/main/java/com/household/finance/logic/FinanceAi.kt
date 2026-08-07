package com.household.finance.logic

import com.household.finance.data.Entry
import com.household.finance.data.Goal
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String) // role: "user" or "assistant"

data class AnomalyFlag(val entryId: String, val label: String, val reason: String)

/** Either a plain answer, or a parsed transaction ready for one-tap confirm (chat-based entry). */
data class ChatResult(val replyText: String?, val draftEntry: Entry?)

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
    fun chat(question: String, history: List<ChatMessage>, entries: List<Entry>, categories: List<String>, nameMe: String, nameWife: String, apiKey: String): ChatResult {
        val system = """
            You are a household budgeting assistant for an Indian married couple using an app called
            Household Finance. Amounts are in INR.

            If the user's latest message is REPORTING A NEW TRANSACTION (an expense, income, or saving —
            e.g. "paid 22k emi", "add 4500 wife music class", "20k rd"), reply with ONLY a single line:
            ENTRY:{"person":"$nameMe or $nameWife","type":"INCOME|EXPENSE|SAVINGS","bucket":"JOINT|PERSONAL_ME|PERSONAL_WIFE","category":"one of: ${categories.joinToString(", ")}","amount":number,"frequency":"MONTHLY|ANNUAL","note":"short string"}
            RD, FD, PPF, SIP, LIC, Mutual Funds, Stocks, Gold are SAVINGS not EXPENSE. No prose, no markdown, just that one line.

            Otherwise, answer the user's question using ONLY the data below — never invent numbers.
            Be concise (2-5 sentences), warm, neutral, never judgmental about spending choices.
            If the data doesn't cover the question, say so plainly.

            Current entries:
            ${entriesDigest(entries)}
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
                    person = json.optString("person", nameMe),
                    type = runCatching { com.household.finance.data.EntryType.valueOf(json.getString("type")) }
                        .getOrDefault(com.household.finance.data.EntryType.EXPENSE),
                    bucket = runCatching { com.household.finance.data.Bucket.valueOf(json.getString("bucket")) }
                        .getOrDefault(com.household.finance.data.Bucket.JOINT),
                    category = json.optString("category", categories.firstOrNull() ?: "Other"),
                    amount = json.optDouble("amount", 0.0),
                    frequency = runCatching { com.household.finance.data.Frequency.valueOf(json.getString("frequency")) }
                        .getOrDefault(com.household.finance.data.Frequency.MONTHLY),
                    note = json.optString("note", question)
                )
            }.getOrNull()
            if (parsed != null && parsed.amount > 0) return ChatResult(null, parsed)
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

    /** Goal planning: turns a free-text goal into a structured monthly-contribution plan. */
    fun planGoal(description: String, monthlySurplus: Double, apiKey: String): Goal {
        val system = """
            Convert a household savings goal into strict JSON: {"title": "short name", "targetAmount": number,
            "targetMonths": integer, "monthlyContribution": number}. monthlyContribution = targetAmount /
            targetMonths, rounded to nearest 100. Currency is INR, no symbols in numbers. Reply with ONLY the JSON object.
            The household's current free monthly surplus is ₹${monthlySurplus.toInt()} — mention nothing about this
            in the JSON, it's just context for realism.
        """.trimIndent()
        val content = chatCompletion(apiKey, system, description, temperature = 0.2)
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(content)
        return Goal(
            title = json.optString("title", description.take(40)),
            targetAmount = json.optDouble("targetAmount", 0.0),
            targetMonths = json.optInt("targetMonths", 12).coerceAtLeast(1),
            monthlyContribution = json.optDouble("monthlyContribution", 0.0)
        )
    }
}
