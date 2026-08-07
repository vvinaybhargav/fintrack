package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.household.finance.data.AppSettings
import com.household.finance.data.CategoryListLength
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.Positive

@Composable
fun SettingsScreen(
    nameMe: String,
    isDefaultProfile: Boolean,
    openAiKey: String,
    firebaseConfig: AppSettings.FirebaseConfig,
    firestoreReady: Boolean,
    categoryLength: CategoryListLength,
    salaryCreditDate: Int?,
    onSwitchProfile: () -> Unit,
    onSetDefaultProfile: () -> Unit,
    onChangePin: (String) -> Unit,
    onRenameProfile: (String) -> Unit,
    onSaveOpenAiKey: (String) -> Unit,
    onSaveFirebaseConfig: (AppSettings.FirebaseConfig) -> Unit,
    onSaveCategoryLength: (CategoryListLength) -> Unit,
    onSaveSalaryCreditDate: (Int?) -> Unit
) {
    var salaryDateField by remember(salaryCreditDate) { mutableStateOf(salaryCreditDate?.toString() ?: "") }
    var newPin by remember { mutableStateOf("") }
    var pinSaved by remember { mutableStateOf(false) }
    var renameField by remember(nameMe) { mutableStateOf(nameMe) }
    var renameError by remember { mutableStateOf<String?>(null) }

    // One comma-separated field: apiKey,appId,projectId,storageBucket,messagingSenderId,openAiKey
    var combinedConfig by remember(firebaseConfig, openAiKey) {
        mutableStateOf(
            listOf(
                firebaseConfig.apiKey, firebaseConfig.appId, firebaseConfig.projectId,
                firebaseConfig.storageBucket, firebaseConfig.messagingSenderId, openAiKey
            ).joinToString(",")
        )
    }
    var combinedError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PROFILE", style = MaterialTheme.typography.labelLarge)
                Text("Signed in as $nameMe" + if (isDefaultProfile) " (default on this device)" else "", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSwitchProfile) { Text("Switch Profile") }
                    if (!isDefaultProfile) {
                        OutlinedButton(onClick = onSetDefaultProfile) { Text("Make Default") }
                    }
                }

                Divider(Modifier.padding(vertical = 4.dp))

                Text("Rename this profile", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Updates every entry, goal, account, and IOU tagged with the old name - may take a few seconds.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = renameField,
                    onValueChange = { renameField = it; renameError = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Button(
                    onClick = {
                        if (renameField.isBlank()) { renameError = "Name can't be blank."; return@Button }
                        onRenameProfile(renameField.trim())
                    },
                    enabled = renameField.isNotBlank() && renameField.trim() != nameMe
                ) { Text("Rename") }
                renameError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Divider(Modifier.padding(vertical = 4.dp))

                Text("Change PIN for $nameMe", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) { newPin = it.filter { c -> c.isDigit() }; pinSaved = false } },
                    label = { Text("New 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
                Button(
                    onClick = { onChangePin(newPin); pinSaved = true; newPin = "" },
                    enabled = newPin.length == 4
                ) { Text("Save PIN") }
                if (pinSaved) {
                    Text("PIN updated.", color = Positive, style = MaterialTheme.typography.bodySmall)
                }

                Divider(Modifier.padding(vertical = 4.dp))

                Text("Salary credit date", style = MaterialTheme.typography.labelLarge)
                Text("Day of the month your salary is usually credited (1-31), for reference.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = salaryDateField,
                    onValueChange = { salaryDateField = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Day of month") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
                Button(onClick = { onSaveSalaryCreditDate(salaryDateField.toIntOrNull()) }) { Text("Save Salary Date") }
            }
        }

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CATEGORY LIST LENGTH", style = MaterialTheme.typography.labelLarge)
                Text("Controls how many categories show up on the dashboard and in entries.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryListLength.entries.forEach { length ->
                        FilterChip(
                            selected = categoryLength == length,
                            onClick = { onSaveCategoryLength(length) },
                            label = { Text(length.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("KEYS & CONFIG", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (firestoreReady) "Status: connected — entries sync in real time." else "Status: not configured — entries are not being saved.",
                    color = if (firestoreReady) Positive else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Paste one comma-separated line, in this exact order: apiKey,appId,projectId,storageBucket,messagingSenderId,openAiKey. " +
                        "storageBucket, messagingSenderId, and openAiKey may be left blank (still keep the commas) — enter the same Firebase values on both phones; the OpenAI key is optional and stays local to this device.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = combinedConfig,
                    onValueChange = { combinedConfig = it; combinedError = null },
                    label = { Text("apiKey,appId,projectId,storageBucket,messagingSenderId,openAiKey") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                combinedError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    val parts = combinedConfig.split(",").map { it.trim() }
                    if (parts.size != 6) {
                        combinedError = "Expected 6 comma-separated values, got ${parts.size}."
                        return@Button
                    }
                    onSaveFirebaseConfig(
                        AppSettings.FirebaseConfig(
                            apiKey = parts[0],
                            appId = parts[1],
                            projectId = parts[2],
                            storageBucket = parts[3],
                            messagingSenderId = parts[4]
                        )
                    )
                    onSaveOpenAiKey(parts[5])
                    combinedError = null
                }) { Text("Save All") }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
