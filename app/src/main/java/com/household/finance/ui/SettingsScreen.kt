package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    nameWife: String,
    pin: String,
    openAiKey: String,
    firebaseConfig: AppSettings.FirebaseConfig,
    firestoreReady: Boolean,
    categoryLength: CategoryListLength,
    onSaveNames: (String, String) -> Unit,
    onSavePin: (String) -> Unit,
    onSaveOpenAiKey: (String) -> Unit,
    onSaveFirebaseConfig: (AppSettings.FirebaseConfig) -> Unit,
    onSaveCategoryLength: (CategoryListLength) -> Unit
) {
    var meField by remember(nameMe) { mutableStateOf(nameMe) }
    var wifeField by remember(nameWife) { mutableStateOf(nameWife) }
    var pinField by remember(pin) { mutableStateOf(pin) }

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
                Text("HOUSEHOLD", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = meField, onValueChange = { meField = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = wifeField, onValueChange = { wifeField = it }, label = { Text("Partner's name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSaveNames(meField, wifeField) }) { Text("Save Names") }
            }
        }

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CATEGORY LIST LENGTH", style = MaterialTheme.typography.labelLarge)
                Text("Controls how many categories show up in the Add screen's dropdown.", style = MaterialTheme.typography.bodySmall)
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
                Text("APP LOCK", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = pinField,
                    onValueChange = { if (it.length <= 4) pinField = it.filter { c -> c.isDigit() } },
                    label = { Text("4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { if (pinField.length == 4) onSavePin(pinField) }, enabled = pinField.length == 4) { Text("Save PIN") }
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
