package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Bucket
import com.household.finance.data.CategoryListLength
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Frequency
import com.household.finance.data.categoriesFor
import com.household.finance.logic.FinanceAi
import com.household.finance.logic.SmartAddParser
import com.household.finance.ui.theme.GlassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    nameMe: String,
    nameWife: String,
    openAiKey: String,
    categoryLength: CategoryListLength,
    editingEntry: Entry?,
    onSave: (Entry) -> Unit,
    onCancelEdit: () -> Unit
) {
    val categories = remember(categoryLength) { categoriesFor(categoryLength) }

    var smartText by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var suggestingCategory by remember { mutableStateOf(false) }

    var editId by remember { mutableStateOf("") }
    var person by remember { mutableStateOf(nameMe) }
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var bucket by remember { mutableStateOf(Bucket.JOINT) }
    var category by remember { mutableStateOf(categories.first()) }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var note by remember { mutableStateOf("") }
    var accountText by remember { mutableStateOf("") }

    fun resetForm() {
        editId = ""
        person = nameMe
        type = EntryType.EXPENSE
        bucket = Bucket.JOINT
        category = categories.first()
        amountText = ""
        frequency = Frequency.MONTHLY
        note = ""
        smartText = ""
        accountText = ""
    }

    fun applyDraft(entry: Entry) {
        editId = entry.id
        person = entry.person.ifBlank { nameMe }
        type = entry.type
        bucket = entry.bucket
        category = entry.category.ifBlank { categories.first() }
        amountText = if (entry.amount > 0) entry.amount.toInt().toString() else ""
        frequency = entry.frequency
        note = entry.note
        accountText = entry.accountName ?: ""
    }

    LaunchedEffect(editingEntry) {
        if (editingEntry != null) applyDraft(editingEntry) else resetForm()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (editingEntry != null) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Editing entry", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onCancelEdit(); resetForm() }) { Text("Cancel") }
                }
            }
        } else {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Smart Add", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Type something like \"22k EMI\" or \"4500 wife music class\"", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smartText,
                        onValueChange = { smartText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Smart add") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (smartText.isBlank()) return@Button
                        if (openAiKey.isBlank()) {
                            applyDraft(SmartAddParser.parseRuleBased(smartText, nameMe, nameWife, categories))
                            parseError = null
                        } else {
                            parsing = true
                            parseError = null
                            Thread {
                                val result = runCatching { SmartAddParser.parseWithOpenAi(smartText, openAiKey, nameMe, nameWife, categories) }
                                val entry = result.getOrElse { SmartAddParser.parseRuleBased(smartText, nameMe, nameWife, categories) }
                                if (result.isFailure) parseError = "AI parse failed, used offline rules instead."
                                applyDraft(entry)
                                parsing = false
                            }.start()
                        }
                    }, enabled = smartText.isNotBlank() && !parsing) {
                        Text(if (parsing) "Parsing..." else "Parse")
                    }
                    parseError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (editingEntry != null) "Edit Entry" else "Confirm / Edit Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LabeledDropdown("Person", listOf(nameMe, nameWife), person) { person = it }
                LabeledDropdown("Type", EntryType.entries.map { it.name }, type.name) { type = EntryType.valueOf(it) }
                LabeledDropdown("Bucket", Bucket.entries.map { it.name }, bucket.name) { bucket = Bucket.valueOf(it) }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (helps AI pick a category)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        LabeledDropdown("Category", categories, category) { category = it }
                    }
                    if (openAiKey.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                suggestingCategory = true
                                Thread {
                                    val result = runCatching { FinanceAi.suggestCategory(note, categories, openAiKey) }
                                    result.getOrNull()?.let { category = it }
                                    suggestingCategory = false
                                }.start()
                            },
                            enabled = !suggestingCategory && note.isNotBlank()
                        ) {
                            Text(if (suggestingCategory) "…" else "AI")
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LabeledDropdown("Frequency", Frequency.entries.map { it.name }, frequency.name) { frequency = Frequency.valueOf(it) }

                OutlinedTextField(
                    value = accountText,
                    onValueChange = { accountText = it },
                    label = { Text("Account (optional, e.g. ICICI, SBI)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (accountText.isNotBlank() && editId.isBlank()) {
                    Text(
                        "New entries update this account's balance automatically (${if (type == EntryType.INCOME) "+" else "-"}₹${amountText.ifBlank { "0" }}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount <= 0) return@Button
                        onSave(
                            Entry(
                                id = editId,
                                person = person,
                                type = type,
                                bucket = bucket,
                                category = category,
                                amount = amount,
                                frequency = frequency,
                                note = note,
                                accountName = accountText.trim().ifBlank { null }
                            )
                        )
                        resetForm()
                        onCancelEdit()
                    },
                    enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editingEntry != null) "Save Changes" else "Save Entry")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}
