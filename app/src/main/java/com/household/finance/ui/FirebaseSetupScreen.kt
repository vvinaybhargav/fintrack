package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.AppSettings
import com.household.finance.ui.theme.GlassSurface

/**
 * Shown before any profile can be picked, whenever Firestore isn't configured yet on this device -
 * without this, a fresh install has no way to reach Settings, since the profile gate that normally
 * comes first has nothing to show until Firestore config lets it see the shared profile list.
 */
@Composable
fun FirebaseSetupScreen(onSaveFirebaseConfig: (AppSettings.FirebaseConfig) -> Unit) {
    var combinedConfig by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Household Finance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Connect to your household's Firebase project to continue", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Paste the same comma-separated config used on the other phone: apiKey,appId,projectId,storageBucket,messagingSenderId,openAiKey " +
                        "(the last three may be left blank, just keep the commas).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = combinedConfig,
                    onValueChange = { combinedConfig = it; error = null; saved = false },
                    label = { Text("apiKey,appId,projectId,storageBucket,messagingSenderId,openAiKey") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (saved) {
                    Spacer(Modifier.height(6.dp))
                    Text("Saved — connecting…", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val parts = combinedConfig.split(",").map { it.trim() }
                        if (parts.size != 6) {
                            error = "Expected 6 comma-separated values, got ${parts.size}."
                            return@Button
                        }
                        if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                            error = "apiKey, appId, and projectId can't be blank."
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
                        saved = true
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Connect") }
            }
        }
    }
}
