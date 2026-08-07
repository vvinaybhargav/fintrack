package com.household.finance.logic

import com.household.finance.data.Entry
import com.household.finance.data.Goal
import com.household.finance.data.Loan
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

/** Either a plain answer, a parsed transaction, a direct balance statement, a new savings goal, a loan, or an edit/delete request. */
data class ChatResult(
    val replyText: String?,
    val draftEntry: Entry?,
    val balanceUpdate: BalanceUpdate? = null,
    val goalDraft: Goal? = null,
    val loanDraft: Loan? = null,
    val deleteTarget: DeleteTarget? = null,
    val editTarget: EditTarget? = null,
    val transferDraft: TransferDraft? = null
)

data class BalanceUpdate(val account: String, val balance: Double)

/** Moves money between two of the household's own accounts - not a new expense, just a balance move. */
data class TransferDraft(val fromAccount: String, val toAccount: String, val amount: Double, val note: String)

/** kind is one of "ENTRY", "GOAL", "LOAN". Requires explicit user confirmation before applying - never auto-applied. */
data class DeleteTarget(val kind: String, val id: String, val label: String)

/** Entry edit only, for now. Requires explicit user confirmation before applying - never auto-applied. */
data class EditTarget(val id: String, val label: String, val updatedEntry: Entry)

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
            "id:${it.id} | ${it.person} | ${it.type} | ${it.bucket} | ${it.category} | ₹${it.amount.toInt()} " +
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
        goals: List<Goal>,
        loans: List<Loan>,
        emergencyFundAmount: Double,
        categories: List<String>,
        nameMe: String,
        nameWife: String,
        apiKey: String,
        defaultAccount: String? = null
    ): ChatResult {
        val accountsDigest = if (accounts.isEmpty()) "No accounts tracked yet." else
            accounts.joinToString(", ") { "${it.name}: ₹${it.balance.toInt()}" }
        val goalsDigest = if (goals.isEmpty()) "No goals set." else
            goals.joinToString("; ") { "id:${it.id} ${it.title}: ₹${it.savedSoFar.toInt()}/₹${it.targetAmount.toInt()} saved, ₹${it.monthlyContribution.toInt()}/mo, ${it.targetMonths}mo plan${if (it.completed) " (REACHED)" else ""}" }
        val loansDigest = if (loans.isEmpty()) "No IOUs." else
            loans.joinToString("; ") { "id:${it.id} ${it.lender} lent ${it.borrower} ₹${it.amount.toInt()}${if (it.settled) " (settled)" else " (outstanding)"}" }

        val system = """
            You are a household budgeting assistant for an Indian married couple using an app called
            Household Finance, currently talking to $nameMe (their partner is $nameWife). Amounts are in INR.
            You have access to all of the household's entries, goals, IOUs, account balances, and the
            emergency fund - use them freely to answer questions. You do NOT have access to Firebase or
            OpenAI keys in Settings, and never need them.

            If the user's latest message is REPORTING A NEW TRANSACTION (an expense, income, or saving —
            e.g. "paid 22k emi", "22k emi from icici", "20k rd", "55k health insurance yearly"), reply with
            ONLY a single line:
            ENTRY:{"type":"INCOME|EXPENSE|SAVINGS","bucket":"JOINT|PERSONAL","category":"one of: ${categories.joinToString(", ")}","amount":number,"frequency":"MONTHLY|ANNUAL","note":"short string","account":"bank/account name if mentioned, else null"}
            Default bucket to PERSONAL unless the user explicitly says "joint". Set frequency to ANNUAL
            whenever the user says "yearly"/"annual"/"/yr"/"per year", and use the FULL yearly amount as
            given - never divide it yourself, that math happens in the app, not here.
            RD, FD, PPF, SIP, LIC, Mutual Funds, Stocks, Gold are SAVINGS not EXPENSE.
            IMPORTANT: a recurring bill or premium (EMI, insurance, LIC, subscriptions, rent) is ALWAYS an
            ENTRY, even when it's yearly/annual — it is NEVER a GOAL, no matter how large the amount. Only
            classify something as GOAL if the user is explicitly describing saving up toward a one-off future
            purchase or target (see below).

            If the user's latest message is DIRECTLY STATING AN ACCOUNT'S BALANCE (not a transaction —
            e.g. "sbi balance is 50k", "icici has 2 lakhs", "hdfc balance 30000"), reply with ONLY a single line:
            BALANCE:{"account":"bank/account name","balance":number}

            If the user's latest message EXPLICITLY describes SAVING UP TOWARD A ONE-OFF FUTURE PURCHASE
            (using words like "goal", "save for", "target", "want to buy") with a target amount and a target
            month/year (e.g. "add goal to buy a car in 2027 jan with down payment of 100000") — NOT a
            recurring bill, even a yearly one — reply with ONLY a single line - extract the fields exactly
            as stated, do NOT compute months or a monthly figure yourself (that's done in code, not by you):
            GOAL:{"title":"short name","targetAmount":number,"targetMonth":1-12,"targetYear":number,"note":"short string"}

            If the user's latest message describes LENDING OR GIVING MONEY TO THEIR PARTNER (e.g. "gave
            $nameWife 2k", "lent $nameWife 2000 from icici") - meaning $nameWife now owes $nameMe - reply
            with ONLY a single line:
            LOAN:{"amount":number,"note":"short string","account":"bank/account name if mentioned, else null"}
            (the lender is always the person chatting, the borrower is always their partner - don't ask, just extract the amount.
            If an account is given, that account is debited now and credited back when the loan is settled.)

            If the user's latest message asks to DELETE an entry, goal, or IOU (e.g. "delete the car goal",
            "remove that EMI entry", "delete the loan to $nameWife"), find the best-matching item by its id in
            the data below and reply with ONLY a single line:
            DELETE:{"kind":"ENTRY|GOAL|LOAN","id":"the matching id","label":"short human description of what would be deleted"}
            If nothing matches clearly, answer normally instead explaining you couldn't find it - don't guess.

            If the user's latest message asks to EDIT/CHANGE an existing entry (e.g. "change the EMI amount
            to 25k", "update groceries note to include this month's vegetables"), find the best-matching entry
            by its id and reply with ONLY a single line, including EVERY field with either the new value or
            the entry's existing unchanged value (never leave a field out):
            EDIT_ENTRY:{"id":"the matching id","type":"INCOME|EXPENSE|SAVINGS","bucket":"JOINT|PERSONAL","category":"...","amount":number,"frequency":"MONTHLY|ANNUAL","note":"...","label":"short human description of the change"}
            If nothing matches clearly, answer normally instead explaining you couldn't find it - don't guess.

            If the user's latest message asks to TRANSFER or MOVE money to an account (e.g. "transfer 5000 to
            kotak", "transfer for insurance to kotak" - if no amount is stated but a category is referenced,
            use that category's amount from the entries below), reply with ONLY a single line. This always
            moves money FROM the user's default account (already known to the app, never ask which account
            it's from) TO the account named:
            TRANSFER:{"amount":number,"toAccount":"bank/account name","note":"short string"}
            ${if (defaultAccount.isNullOrBlank()) "No default account is set yet - if asked to transfer, answer normally telling the user to set one in Settings first, instead of replying TRANSFER." else "Default account: $defaultAccount"}

            For any of these seven cases: no prose, no markdown fences, just that one line. DELETE and EDIT_ENTRY
            will always be shown to the user for confirmation before anything actually happens - you never
            need to ask for confirmation yourself, just extract the request.

            Otherwise, answer the user's question using ONLY the data below — never invent numbers. You can
            do arithmetic/projections over this data (e.g. "how much can I save by July if my rent goes up
            5k" - compute using current income/expense figures plus the hypothetical).
            Be concise (2-5 sentences), warm, neutral, never judgmental about spending choices.
            If the data doesn't cover the question, say so plainly.

            Current entries:
            ${entriesDigest(entries)}

            Current account balances:
            $accountsDigest

            Emergency fund: ₹${emergencyFundAmount.toInt()}

            Goals:
            $goalsDigest

            IOUs:
            $loansDigest
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
                    monthlyContribution = (targetAmount / months),
                    targetMonth = targetMonth,
                    targetYear = targetYear
                )
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, parsed)
        }

        if (raw.startsWith("LOAN:")) {
            val jsonText = raw.removePrefix("LOAN:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val amount = json.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount <= 0) return@runCatching null
                Loan(
                    lender = nameMe,
                    borrower = nameWife,
                    amount = amount,
                    note = json.optString("note", question),
                    accountName = json.optString("account", "").ifBlank { null }.takeIf { it != "null" }
                )
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, null, parsed)
        }

        if (raw.startsWith("TRANSFER:") && !defaultAccount.isNullOrBlank()) {
            val jsonText = raw.removePrefix("TRANSFER:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val amount = json.optDouble("amount", Double.NaN)
                val toAccount = json.optString("toAccount", "").trim()
                if (amount.isNaN() || amount <= 0 || toAccount.isBlank()) return@runCatching null
                TransferDraft(fromAccount = defaultAccount, toAccount = toAccount, amount = amount, note = json.optString("note", question))
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, null, null, null, null, parsed)
        }

        if (raw.startsWith("DELETE:")) {
            val jsonText = raw.removePrefix("DELETE:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val kind = json.optString("kind", "").uppercase()
                val id = json.optString("id", "").trim()
                if (kind !in setOf("ENTRY", "GOAL", "LOAN") || id.isBlank()) return@runCatching null
                val exists = when (kind) {
                    "ENTRY" -> entries.any { it.id == id }
                    "GOAL" -> goals.any { it.id == id }
                    else -> loans.any { it.id == id }
                }
                if (!exists) return@runCatching null
                DeleteTarget(kind, id, json.optString("label", "this item"))
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, null, null, parsed)
        }

        if (raw.startsWith("EDIT_ENTRY:")) {
            val jsonText = raw.removePrefix("EDIT_ENTRY:").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = runCatching {
                val json = JSONObject(jsonText)
                val id = json.optString("id", "").trim()
                val original = entries.find { it.id == id } ?: return@runCatching null
                val updated = original.copy(
                    type = runCatching { com.household.finance.data.EntryType.valueOf(json.getString("type")) }.getOrDefault(original.type),
                    bucket = runCatching { com.household.finance.data.Bucket.valueOf(json.getString("bucket")) }.getOrDefault(original.bucket),
                    category = json.optString("category", original.category),
                    amount = json.optDouble("amount", original.amount),
                    frequency = runCatching { com.household.finance.data.Frequency.valueOf(json.getString("frequency")) }.getOrDefault(original.frequency),
                    note = json.optString("note", original.note)
                )
                EditTarget(id, json.optString("label", "this entry"), updated)
            }.getOrNull()
            if (parsed != null) return ChatResult(null, null, null, null, null, null, parsed)
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
        val hasTargetDate = targetMonth in 1..12 && targetYear >= 2000
        val months = if (hasTargetDate) monthsUntilInclusive(targetMonth, targetYear) else 12
        val targetAmount = json.optDouble("targetAmount", 0.0)
        return Goal(
            title = json.optString("title", description.take(40)),
            targetAmount = targetAmount,
            targetMonths = months,
            monthlyContribution = if (months > 0) targetAmount / months else 0.0,
            targetMonth = if (hasTargetDate) targetMonth else null,
            targetYear = if (hasTargetDate) targetYear else null
        )
    }
}
