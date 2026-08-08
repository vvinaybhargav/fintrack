package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.ActiveLoan
import com.household.finance.data.Bill
import com.household.finance.data.BillType
import com.household.finance.data.Bucket
import com.household.finance.data.CategoryListLength
import com.household.finance.data.Entry
import com.household.finance.data.EntryType
import com.household.finance.data.Frequency
import com.household.finance.data.categoriesFor
import com.household.finance.logic.SmartAddParser
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.Violet

private enum class AddKind(val label: String) {
    EXPENSE("Expense"), BILL("Bill"), EMI_LOAN("EMI / Loan"), INVESTMENT("Investment"),
    ONE_TIME("One-time"), BANK_ACCOUNT("Bank Account"), CREDIT_CARD("Credit Card")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    nameMe: String,
    nameWife: String,
    openAiKey: String,
    categoryLength: CategoryListLength,
    editingEntry: Entry?,
    onSave: (Entry) -> Unit,
    onCancelEdit: () -> Unit,
    onAddBill: (Bill) -> Unit = {},
    onAddActiveLoan: (ActiveLoan) -> Unit = {},
    onAddAccount: (name: String, owner: String, balance: Double) -> Unit = { _, _, _ -> }
) {
    val categories = remember(categoryLength) { categoriesFor(categoryLength) }
    var kind by remember { mutableStateOf(AddKind.EXPENSE) }

    var smartText by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    var editId by remember { mutableStateOf("") }
    var person by remember { mutableStateOf(nameMe) }
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var bucket by remember { mutableStateOf(Bucket.JOINT) }
    var category by remember { mutableStateOf(categories.first()) }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var note by remember { mutableStateOf("") }
    var accountText by remember { mutableStateOf("") }
    var toAccountText by remember { mutableStateOf("") }

    fun resetForm() {
        editId = ""
        person = nameMe
        type = if (kind == AddKind.INVESTMENT) EntryType.SAVINGS else EntryType.EXPENSE
        bucket = Bucket.JOINT
        category = categories.first()
        amountText = ""
        frequency = Frequency.MONTHLY
        note = ""
        smartText = ""
        accountText = ""
        toAccountText = ""
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
        toAccountText = entry.toAccountName ?: ""
    }

    LaunchedEffect(editingEntry) {
        if (editingEntry != null) applyDraft(editingEntry) else resetForm()
    }

    LaunchedEffect(kind) {
        if (editingEntry == null) type = if (kind == AddKind.INVESTMENT) EntryType.SAVINGS else EntryType.EXPENSE
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(nameMe = nameMe, bucketLabel = bucket.name.lowercase().replaceFirstChar { it.uppercase() })

        if (editingEntry != null) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Editing entry", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onCancelEdit(); resetForm() }) { Text("Cancel") }
                }
            }
        } else {
            Text("Smart Add", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Hi! Tell me what to add — e.g. \"22k EMI\" or \"4500 wife music class\".",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = smartText,
                    onValueChange = { smartText = it },
                    placeholder = { Text("e.g. 22k EMI, 4500 wife music class") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
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
                    },
                    enabled = smartText.isNotBlank() && !parsing,
                    shape = RoundedCornerShape(999.dp)
                ) { Text(if (parsing) "…" else "Send") }
            }
            parseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Divider()

            Text("What are you adding?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AddKindChips(selected = kind, onSelect = { kind = it })
        }

        when {
            editingEntry != null -> GenericEntryForm(
                nameMe, nameWife, person, { person = it }, type, { type = it }, bucket, { bucket = it },
                category, { category = it }, categories, amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                frequency, { frequency = it }, note, { note = it }, accountText, { accountText = it },
                toAccountText, { toAccountText = it },
                showType = true, saveLabel = "Save Changes",
                onSave = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount <= 0) return@GenericEntryForm
                    onSave(
                        Entry(
                            id = editId, person = person, type = type, bucket = bucket, category = category,
                            amount = amount, frequency = frequency, note = note,
                            accountName = accountText.trim().ifBlank { null }, toAccountName = toAccountText.trim().ifBlank { null }
                        )
                    )
                    resetForm()
                    onCancelEdit()
                }
            )
            kind == AddKind.BILL -> AddBillKindForm(onAdd = { onAddBill(it); resetForm() })
            kind == AddKind.EMI_LOAN -> AddLoanKindForm(nameMe = nameMe, nameWife = nameWife, onAdd = { onAddActiveLoan(it); resetForm() })
            kind == AddKind.BANK_ACCOUNT -> AddAccountKindForm(nameMe = nameMe, nameWife = nameWife, onAdd = { n, o, b -> onAddAccount(n, o, b); resetForm() })
            kind == AddKind.CREDIT_CARD -> AddCreditCardKindForm(nameMe = nameMe, nameWife = nameWife, onAdd = { onAddBill(it); resetForm() })
            else -> GenericEntryForm(
                nameMe, nameWife, person, { person = it }, type, { type = it }, bucket, { bucket = it },
                category, { category = it }, categories, amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                frequency, { frequency = it }, note, { note = it }, accountText, { accountText = it },
                toAccountText, { toAccountText = it },
                showType = false, saveLabel = "Save entry",
                onSave = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount <= 0) return@GenericEntryForm
                    onSave(
                        Entry(
                            person = person, type = type, bucket = bucket, category = category,
                            amount = amount, frequency = frequency, note = note,
                            accountName = accountText.trim().ifBlank { null }, toAccountName = toAccountText.trim().ifBlank { null }
                        )
                    )
                    resetForm()
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AddKindChips(selected: AddKind, onSelect: (AddKind) -> Unit) {
    val rows = AddKind.entries.toList().chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    FilterChip(
                        selected = selected == k,
                        onClick = { onSelect(k) },
                        label = { Text(k.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet, selectedLabelColor = Color.White)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericEntryForm(
    nameMe: String, nameWife: String,
    person: String, onPersonChange: (String) -> Unit,
    type: EntryType, onTypeChange: (EntryType) -> Unit,
    bucket: Bucket, onBucketChange: (Bucket) -> Unit,
    category: String, onCategoryChange: (String) -> Unit, categories: List<String>,
    amountText: String, onAmountChange: (String) -> Unit,
    frequency: Frequency, onFrequencyChange: (Frequency) -> Unit,
    note: String, onNoteChange: (String) -> Unit,
    accountText: String, onAccountChange: (String) -> Unit,
    toAccountText: String, onToAccountChange: (String) -> Unit,
    showType: Boolean, saveLabel: String,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledDropdown("Person", listOf(nameMe, nameWife), person, onPersonChange)
        if (showType) LabeledDropdown("Type", EntryType.entries.map { it.name }, type.name) { onTypeChange(EntryType.valueOf(it)) }
        LabeledDropdown("Bucket", Bucket.entries.map { it.name }, bucket.name) { onBucketChange(Bucket.valueOf(it)) }
        LabeledDropdown("Category", categories, category, onCategoryChange)
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            label = { Text("Amount (₹)") },
            placeholder = { Text("e.g. 5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LabeledDropdown("Frequency", Frequency.entries.map { it.name }, frequency.name) { onFrequencyChange(Frequency.valueOf(it)) }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Note (optional)") },
            placeholder = { Text("e.g. Groceries, electricity bill…") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = accountText,
            onValueChange = onAccountChange,
            label = { Text("Account (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = toAccountText,
            onValueChange = onToAccountChange,
            label = { Text("Set aside into account (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSave,
            enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) { Text(saveLabel) }
    }
}

@Composable
private fun AddBillKindForm(onAdd: (Bill) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var toAccountName by remember { mutableStateOf("") }
    var billType by remember { mutableStateOf(BillType.EMI) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bill name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BillType.EMI, BillType.OTHER).forEach { option ->
                FilterChip(selected = billType == option, onClick = { billType = option }, label = { Text(option.name.replace("_", " ")) })
            }
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("Amount due (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Debit from account (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = toAccountName, onValueChange = { toAccountName = it }, label = { Text("Or: set aside into account (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull() ?: return@Button
                if (name.isBlank() || dueDate.isBlank() || amt <= 0) return@Button
                onAdd(Bill(name = name.trim(), amount = amt, dueDate = dueDate.trim(), accountName = accountName.trim().ifBlank { null }, toAccountName = toAccountName.trim().ifBlank { null }, type = billType))
            },
            enabled = name.isNotBlank() && dueDate.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) { Text("Save bill") }
    }
}

@Composable
private fun AddLoanKindForm(nameMe: String, nameWife: String, onAdd: (ActiveLoan) -> Unit) {
    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(nameMe) }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }
    var remainingMonths by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name (e.g. ICICI Home Loan)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LabeledDropdown("Person", listOf(nameMe, nameWife), owner) { owner = it }
        OutlinedTextField(value = principal, onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Remaining principal (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Annual interest rate (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emi, onValueChange = { emi = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly EMI (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = remainingMonths, onValueChange = { remainingMonths = it.filter { c -> c.isDigit() } }, label = { Text("Remaining tenure (months)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val p = principal.toDoubleOrNull() ?: 0.0
                val e = emi.toDoubleOrNull() ?: 0.0
                val t = remainingMonths.toIntOrNull() ?: 120
                if (name.isBlank() || p <= 0 || e <= 0) return@Button
                onAdd(ActiveLoan(name = name.trim(), principal = p, interestRate = rate.toDoubleOrNull() ?: 0.0, monthlyEmi = e, remainingTenureMonths = t, totalTenureMonths = t, owner = owner))
            },
            enabled = name.isNotBlank() && principal.isNotBlank() && emi.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) { Text("Save loan") }
    }
}

@Composable
private fun AddAccountKindForm(nameMe: String, nameWife: String, onAdd: (name: String, owner: String, balance: Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(nameMe) }
    var balance by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account name (e.g. HDFC Savings)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LabeledDropdown("Owner", listOf(nameMe, nameWife, "Joint"), owner) { owner = it }
        OutlinedTextField(value = balance, onValueChange = { balance = it.filter { c -> c.isDigit() } }, label = { Text("Current balance (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val bal = balance.toDoubleOrNull() ?: return@Button
                if (name.isBlank()) return@Button
                onAdd(name.trim(), owner, bal)
            },
            enabled = name.isNotBlank() && balance.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) { Text("Save account") }
    }
}

@Composable
private fun AddCreditCardKindForm(nameMe: String, nameWife: String, onAdd: (Bill) -> Unit) {
    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(nameMe) }
    var limit by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var minDue by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Card name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LabeledDropdown("Owner", listOf(nameMe, nameWife, "Joint"), owner) { owner = it }
        OutlinedTextField(value = limit, onValueChange = { limit = it.filter { c -> c.isDigit() } }, label = { Text("Credit limit (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = balance, onValueChange = { balance = it.filter { c -> c.isDigit() } }, label = { Text("Current balance (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = minDue, onValueChange = { minDue = it.filter { c -> c.isDigit() } }, label = { Text("Minimum due (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val bal = balance.toDoubleOrNull() ?: return@Button
                if (name.isBlank() || due.isBlank()) return@Button
                onAdd(Bill(name = name.trim(), amount = bal, dueDate = due.trim(), type = BillType.CREDIT_CARD, creditLimit = limit.toDoubleOrNull(), minDue = minDue.toDoubleOrNull(), owner = owner))
            },
            enabled = name.isNotBlank() && due.isNotBlank() && balance.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) { Text("Save card") }
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
