package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.household.finance.data.Account
import com.household.finance.data.Entry
import com.household.finance.data.Goal
import com.household.finance.data.Loan
import com.household.finance.logic.AnomalyFlag
import com.household.finance.logic.BalanceUpdate
import com.household.finance.logic.ChatMessage
import com.household.finance.logic.DeleteTarget
import com.household.finance.logic.EditTarget
import com.household.finance.logic.FinanceAi
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.Positive
import com.household.finance.ui.theme.Warning

private enum class AiTab { CHAT, BUDGET, ANOMALIES, GOALS }

@Composable
fun AiHubScreen(
    entries: List<Entry>,
    goals: List<Goal>,
    accounts: List<Account>,
    loans: List<Loan>,
    emergencyFundAmount: Double,
    openAiKey: String,
    categories: List<String>,
    nameMe: String,
    nameWife: String,
    onAddGoal: (Goal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddEntry: (Entry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onSetAccountBalance: (String, Double) -> Unit,
    onAddLoan: (lender: String, borrower: String, amount: Double, note: String) -> Unit,
    onDeleteLoan: (String) -> Unit,
    onAddGoalContribution: (String, Double) -> Unit
) {
    var tab by remember { mutableStateOf(AiTab.CHAT) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (openAiKey.isBlank()) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text("Add an OpenAI key in Settings to unlock AI features: chat-based entries, budget suggestions, anomaly detection, and goal planning.")
            }
            return
        }

        SingleChoiceSegmented(
            options = listOf("Chat", "Budget", "Anomalies", "Goals"),
            selectedIndex = tab.ordinal,
            onSelect = { tab = AiTab.entries[it] }
        )
        Spacer(Modifier.height(14.dp))

        when (tab) {
            AiTab.CHAT -> ChatPane(
                entries, accounts, goals, loans, emergencyFundAmount, categories, nameMe, nameWife,
                openAiKey, onAddEntry, onDeleteEntry, onEditEntry, onSetAccountBalance, onAddGoal,
                onDeleteGoal, onAddLoan, onDeleteLoan
            )
            AiTab.BUDGET -> BudgetPane(entries, openAiKey)
            AiTab.ANOMALIES -> AnomaliesPane(entries, openAiKey)
            AiTab.GOALS -> GoalsPane(goals, openAiKey, onAddGoal, onDeleteGoal, onAddGoalContribution)
        }
    }
}

@Composable
private fun SingleChoiceSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(i) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private sealed class ChatBubble {
    data class Text(val role: String, val content: String) : ChatBubble()
    data class AddedEntry(val entry: Entry) : ChatBubble()
    data class AppliedBalance(val update: BalanceUpdate) : ChatBubble()
    data class AddedGoal(val goal: Goal) : ChatBubble()
    data class AddedLoan(val loan: Loan) : ChatBubble()
    data class PendingDelete(val target: DeleteTarget) : ChatBubble()
    data class DidDelete(val label: String) : ChatBubble()
    data class Discarded(val label: String) : ChatBubble()
    data class PendingEdit(val target: EditTarget) : ChatBubble()
    data class DidEdit(val label: String) : ChatBubble()
}

@Composable
private fun ChatPane(
    entries: List<Entry>,
    accounts: List<Account>,
    goals: List<Goal>,
    loans: List<Loan>,
    emergencyFundAmount: Double,
    categories: List<String>,
    nameMe: String,
    nameWife: String,
    apiKey: String,
    onAddEntry: (Entry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onSetAccountBalance: (String, Double) -> Unit,
    onAddGoal: (Goal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddLoan: (lender: String, borrower: String, amount: Double, note: String) -> Unit,
    onDeleteLoan: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<ChatMessage>() }
    val bubbles = remember { mutableStateListOf<ChatBubble>() }

    fun send() {
        val question = input.trim()
        if (question.isBlank() || loading) return
        bubbles.add(ChatBubble.Text("user", question))
        history.add(ChatMessage("user", question))
        input = ""
        loading = true
        error = null
        Thread {
            val result = runCatching {
                FinanceAi.chat(question, history.toList(), entries, accounts, goals, loans, emergencyFundAmount, categories, nameMe, nameWife, apiKey)
            }
            result.onSuccess { r ->
                when {
                    r.draftEntry != null -> {
                        onAddEntry(r.draftEntry)
                        bubbles.add(ChatBubble.AddedEntry(r.draftEntry))
                        history.add(ChatMessage("assistant", "Logged it."))
                    }
                    r.balanceUpdate != null -> {
                        onSetAccountBalance(r.balanceUpdate.account, r.balanceUpdate.balance)
                        bubbles.add(ChatBubble.AppliedBalance(r.balanceUpdate))
                        history.add(ChatMessage("assistant", "Balance updated."))
                    }
                    r.goalDraft != null -> {
                        onAddGoal(r.goalDraft)
                        bubbles.add(ChatBubble.AddedGoal(r.goalDraft))
                        history.add(ChatMessage("assistant", "Goal added."))
                    }
                    r.loanDraft != null -> {
                        onAddLoan(r.loanDraft.lender, r.loanDraft.borrower, r.loanDraft.amount, r.loanDraft.note)
                        bubbles.add(ChatBubble.AddedLoan(r.loanDraft))
                        history.add(ChatMessage("assistant", "IOU recorded."))
                    }
                    r.deleteTarget != null -> {
                        bubbles.add(ChatBubble.PendingDelete(r.deleteTarget))
                        history.add(ChatMessage("assistant", "Awaiting confirmation to delete ${r.deleteTarget.label}."))
                    }
                    r.editTarget != null -> {
                        bubbles.add(ChatBubble.PendingEdit(r.editTarget))
                        history.add(ChatMessage("assistant", "Awaiting confirmation to change ${r.editTarget.label}."))
                    }
                    r.replyText != null -> {
                        bubbles.add(ChatBubble.Text("assistant", r.replyText))
                        history.add(ChatMessage("assistant", r.replyText))
                    }
                }
            }
            result.onFailure { error = "Couldn't reach OpenAI — check your key and connection." }
            loading = false
        }.start()
    }

    fun confirmDelete(bubble: ChatBubble.PendingDelete) {
        when (bubble.target.kind) {
            "ENTRY" -> onDeleteEntry(bubble.target.id)
            "GOAL" -> onDeleteGoal(bubble.target.id)
            "LOAN" -> onDeleteLoan(bubble.target.id)
        }
        val idx = bubbles.indexOf(bubble)
        if (idx >= 0) bubbles[idx] = ChatBubble.DidDelete(bubble.target.label)
    }

    fun discardDelete(bubble: ChatBubble.PendingDelete) {
        val idx = bubbles.indexOf(bubble)
        if (idx >= 0) bubbles[idx] = ChatBubble.Discarded(bubble.target.label)
    }

    fun confirmEdit(bubble: ChatBubble.PendingEdit) {
        onEditEntry(bubble.target.updatedEntry)
        val idx = bubbles.indexOf(bubble)
        if (idx >= 0) bubbles[idx] = ChatBubble.DidEdit(bubble.target.label)
    }

    fun discardEdit(bubble: ChatBubble.PendingEdit) {
        val idx = bubbles.indexOf(bubble)
        if (idx >= 0) bubbles[idx] = ChatBubble.Discarded(bubble.target.label)
    }

    Column(Modifier.fillMaxSize()) {
        if (bubbles.isEmpty()) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Type \"22k emi\" and it's logged instantly under your name as Personal — say \"joint\" if it's shared. " +
                        "\"22k emi from icici\" also updates that account's balance. \"sbi balance is 50k\" sets a balance directly. " +
                        "\"add goal to buy a car in 2027 jan with down payment 100000\" plans a goal. " +
                        "\"gave ${nameWife} 2k\" records an IOU. \"delete the car goal\" or \"change the EMI amount to 25k\" " +
                        "will ask you to confirm before doing anything. Or just ask a question — I can see all your entries, goals, and IOUs.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bubbles) { bubble ->
                when (bubble) {
                    is ChatBubble.Text -> {
                        val isUser = bubble.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                            GlassSurface(modifier = Modifier.fillMaxWidth(0.85f)) { Text(bubble.content) }
                        }
                    }
                    is ChatBubble.AddedEntry -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✅", modifier = Modifier.padding(end = 8.dp))
                                    Text("Logged ${bubble.entry.category} — ${formatInr(bubble.entry.amount)}", color = Positive)
                                }
                                Text(
                                    "${bubble.entry.person} · ${bubble.entry.bucket}" +
                                        (bubble.entry.accountName?.let { " · $it ${if (bubble.entry.type.name == "INCOME") "+" else "-"}${formatInr(bubble.entry.amount)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    is ChatBubble.AppliedBalance -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", modifier = Modifier.padding(end = 8.dp))
                                Text("${bubble.update.account} balance set to ${formatInr(bubble.update.balance)}", color = Positive)
                            }
                        }
                    }
                    is ChatBubble.AddedGoal -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✅", modifier = Modifier.padding(end = 8.dp))
                                    Text("Goal added: ${bubble.goal.title}", color = Positive)
                                }
                                Text(
                                    "${formatInr(bubble.goal.monthlyContribution)}/mo for ${bubble.goal.targetMonths} months → ${formatInr(bubble.goal.targetAmount)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    is ChatBubble.AddedLoan -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", modifier = Modifier.padding(end = 8.dp))
                                Text("${bubble.loan.borrower} owes ${bubble.loan.lender} ${formatInr(bubble.loan.amount)}", color = Positive)
                            }
                        }
                    }
                    is ChatBubble.PendingDelete -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("Delete ${bubble.target.label}?", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { confirmDelete(bubble) }) { Text("Delete") }
                                    OutlinedButton(onClick = { discardDelete(bubble) }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                    is ChatBubble.DidDelete -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🗑️", modifier = Modifier.padding(end = 8.dp))
                                Text("Deleted ${bubble.label}", color = Warning)
                            }
                        }
                    }
                    is ChatBubble.PendingEdit -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("Change ${bubble.target.label}?", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${bubble.target.updatedEntry.category} — ${formatInr(bubble.target.updatedEntry.amount)}, ${bubble.target.updatedEntry.bucket}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { confirmEdit(bubble) }) { Text("Save Change") }
                                    OutlinedButton(onClick = { discardEdit(bubble) }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                    is ChatBubble.DidEdit -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", modifier = Modifier.padding(end = 8.dp))
                                Text("Updated ${bubble.label}", color = Positive)
                            }
                        }
                    }
                    is ChatBubble.Discarded -> {
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Text("Discarded — ${bubble.label} left unchanged.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        GlassSurface { Text("Thinking…", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Log an entry or ask a question…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { send() }, enabled = !loading && input.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun BudgetPane(entries: List<Entry>, apiKey: String) {
    var result by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Smart Budget Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Proposes a monthly cap per category based on your actual spend.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    loading = true; error = null
                    Thread {
                        val r = runCatching { FinanceAi.suggestBudgets(entries, apiKey) }
                        result = r.getOrNull()
                        if (r.isFailure) error = "Couldn't generate suggestions right now."
                        loading = false
                    }.start()
                }, enabled = !loading && entries.isNotEmpty()) {
                    Text(if (loading) "Generating…" else "Suggest budgets")
                }
                if (entries.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Add a few expense entries first.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let {
            Spacer(Modifier.height(14.dp))
            GlassSurface(modifier = Modifier.fillMaxWidth()) { Text(it) }
        }
    }
}

@Composable
private fun AnomaliesPane(entries: List<Entry>, apiKey: String) {
    var flags by remember { mutableStateOf<List<AnomalyFlag>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Anomaly Detection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Flags likely duplicates, outliers, or miscategorized entries.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    loading = true; error = null
                    Thread {
                        val r = runCatching { FinanceAi.detectAnomalies(entries, apiKey) }
                        flags = r.getOrNull()
                        if (r.isFailure) error = "Couldn't scan entries right now."
                        loading = false
                    }.start()
                }, enabled = !loading && entries.isNotEmpty()) {
                    Text(if (loading) "Scanning…" else "Scan for anomalies")
                }
            }
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        flags?.let { list ->
            Spacer(Modifier.height(14.dp))
            if (list.isEmpty()) {
                GlassSurface(modifier = Modifier.fillMaxWidth()) { Text("Nothing stood out — entries look consistent.") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list) { flag ->
                        val entry = entries.find { it.id == flag.entryId }
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry?.let { "${it.category} — ${it.person}" } ?: "Entry", fontWeight = FontWeight.Bold)
                                    AssistChip(onClick = {}, label = { Text(flag.label) }, colors = AssistChipDefaults.assistChipColors(labelColor = Warning))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(flag.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsPane(
    goals: List<Goal>,
    apiKey: String,
    onAddGoal: (Goal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddContribution: (String, Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Goal Planning Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Describe a goal — e.g. \"save 5L for a car in 18 months\".", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Describe your goal") }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (description.isBlank()) return@Button
                    loading = true; error = null
                    Thread {
                        val r = runCatching { FinanceAi.planGoal(description, apiKey) }
                        r.onSuccess { onAddGoal(it); description = "" }
                        if (r.isFailure) error = "Couldn't plan that goal right now."
                        loading = false
                    }.start()
                }, enabled = !loading && description.isNotBlank()) {
                    Text(if (loading) "Planning…" else "Plan this goal")
                }
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            goals.forEach { goal ->
                var adding by remember(goal.id) { mutableStateOf(false) }
                var amountInput by remember(goal.id) { mutableStateOf("") }
                val progress = if (goal.targetAmount > 0) (goal.savedSoFar / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0
                GlassSurface(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(goal.title, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onDeleteGoal(goal.id) }) { Text("Remove") }
                        }
                        Text(
                            "${formatInr(goal.savedSoFar)} / ${formatInr(goal.targetAmount)} · ${formatInr(goal.monthlyContribution)}/mo for ${goal.targetMonths} months",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        Spacer(Modifier.height(8.dp))
                        if (adding) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Amount saved") },
                                    singleLine = true,
                                    modifier = Modifier.width(140.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    amountInput.toDoubleOrNull()?.let { if (it > 0) onAddContribution(goal.id, it) }
                                    amountInput = ""
                                    adding = false
                                }) { Text("Add") }
                                TextButton(onClick = { adding = false }) { Text("Cancel") }
                            }
                        } else {
                            TextButton(onClick = { adding = true }) { Text("Add contribution") }
                        }
                    }
                }
            }
        }
    }
}
