package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Bucket
import com.household.finance.data.DEFAULT_CATEGORIES
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Frequency
import com.household.finance.logic.SmartAddParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    nameMe: String,
    nameWife: String,
    openAiKey: String,
    onSave: (Entry) -> Unit
) {
    var smartText by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<Entry?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Quick-tap form state (works fully without an API key)
    var person by remember { mutableStateOf(nameMe) }
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var bucket by remember { mutableStateOf(Bucket.JOINT) }
    var category by remember { mutableStateOf(DEFAULT_CATEGORIES.first()) }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var note by remember { mutableStateOf("") }

    fun applyDraft(entry: Entry) {
        draft = entry
        person = entry.person.ifBlank { nameMe }
        type = entry.type
        bucket = entry.bucket
        category = entry.category.ifBlank { DEFAULT_CATEGORIES.first() }
        amountText = if (entry.amount > 0) entry.amount.toInt().toString() else ""
        frequency = entry.frequency
        note = entry.note
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart Add", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Type something like \"22k EMI\" or \"4500 wife music class\"", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = smartText,
            onValueChange = { smartText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Smart add") },
            singleLine = true
        )
        Row {
            Button(onClick = {
                if (smartText.isBlank()) return@Button
                if (openAiKey.isBlank()) {
                    applyDraft(SmartAddParser.parseRuleBased(smartText, nameMe, nameWife))
                    parseError = null
                } else {
                    parsing = true
                    parseError = null
                    Thread {
                        val result = runCatching { SmartAddParser.parseWithOpenAi(smartText, openAiKey, nameMe, nameWife) }
                        val entry = result.getOrElse { SmartAddParser.parseRuleBased(smartText, nameMe, nameWife) }
                        if (result.isFailure) parseError = "AI parse failed, used offline rules instead."
                        applyDraft(entry)
                        parsing = false
                    }.start()
                }
            }, enabled = smartText.isNotBlank() && !parsing) {
                Text(if (parsing) "Parsing..." else "Parse")
            }
        }
        parseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Divider()

        Text("Confirm / Edit Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        LabeledDropdown("Person", listOf(nameMe, nameWife), person) { person = it }
        LabeledDropdown("Type", EntryType.entries.map { it.name }, type.name) { type = EntryType.valueOf(it) }
        LabeledDropdown("Bucket", Bucket.entries.map { it.name }, bucket.name) { bucket = Bucket.valueOf(it) }
        LabeledDropdown("Category", DEFAULT_CATEGORIES, category) { category = it }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Amount (₹)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LabeledDropdown("Frequency", Frequency.entries.map { it.name }, frequency.name) { frequency = Frequency.valueOf(it) }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                if (amount <= 0) return@Button
                onSave(
                    Entry(
                        person = person,
                        type = type,
                        bucket = bucket,
                        category = category,
                        amount = amount,
                        frequency = frequency,
                        note = note
                    )
                )
                smartText = ""
                draft = null
                amountText = ""
                note = ""
            },
            enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Entry")
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
