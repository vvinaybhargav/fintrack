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
    var keyField by remember(openAiKey) { mutableStateOf(openAiKey) }

    var apiKey by remember(firebaseConfig) { mutableStateOf(firebaseConfig.apiKey) }
    var appId by remember(firebaseConfig) { mutableStateOf(firebaseConfig.appId) }
    var projectId by remember(firebaseConfig) { mutableStateOf(firebaseConfig.projectId) }
    var storageBucket by remember(firebaseConfig) { mutableStateOf(firebaseConfig.storageBucket) }
    var senderId by remember(firebaseConfig) { mutableStateOf(firebaseConfig.messagingSenderId) }

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
                Text("FIREBASE (FIRESTORE) SYNC", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (firestoreReady) "Status: connected — entries sync in real time." else "Status: not configured — entries are not being saved.",
                    color = if (firestoreReady) Positive else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Enter the same values on both phones (from Firebase Console > Project settings > Your apps > Web app config).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("apiKey") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("appId") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("projectId") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = storageBucket, onValueChange = { storageBucket = it }, label = { Text("storageBucket (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text("messagingSenderId (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    onSaveFirebaseConfig(
                        AppSettings.FirebaseConfig(
                            apiKey = apiKey.trim(),
                            appId = appId.trim(),
                            projectId = projectId.trim(),
                            storageBucket = storageBucket.trim(),
                            messagingSenderId = senderId.trim()
                        )
                    )
                }) { Text("Save Firebase Config") }
            }
        }

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OPENAI (OPTIONAL)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Powers Smart Add parsing, Chat, Budget Suggestions, Anomaly Detection, and Goal Planning — all via gpt-4o-mini, called only when you tap a button, never automatically. Stored only on this device. Set a low spending cap on this key in your OpenAI dashboard.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = keyField,
                    onValueChange = { keyField = it },
                    label = { Text("OpenAI API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(onClick = { onSaveOpenAiKey(keyField.trim()) }) { Text("Save OpenAI Key") }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
