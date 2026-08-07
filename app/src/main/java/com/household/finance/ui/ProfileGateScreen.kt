package com.household.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.household.finance.data.Profile
import com.household.finance.ui.theme.GlassSurface

private enum class GateStep { PICK, SET_PIN, ENTER_PIN }

/**
 * Handles picking which profile is active on this device, and PIN entry for it.
 * - Tapping a profile with no PIN yet goes to first-time PIN setup (with a "make default" option).
 * - Tapping a profile that already has a PIN asks for it (with a "remember on this device" option).
 * - A profile already in [trustedProfiles] on this device is signed in without ever reaching here
 *   (that check happens in the caller before showing this screen at all).
 */
@Composable
fun ProfileGateScreen(
    profiles: List<Profile>,
    onSelectProfile: (name: String, remember: Boolean) -> Unit,
    onSetPin: (name: String, pin: String) -> Unit,
    onSetDefault: (name: String) -> Unit,
    onSignedIn: () -> Unit
) {
    var step by remember { mutableStateOf(GateStep.PICK) }
    var chosen by remember { mutableStateOf<Profile?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var rememberChecked by remember { mutableStateOf(false) }
    var defaultChecked by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Household Finance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        when (step) {
            GateStep.PICK -> {
                Text("Who's this?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                if (profiles.isEmpty()) {
                    Text("Setting up profiles…", style = MaterialTheme.typography.bodySmall)
                } else {
                    profiles.forEach { profile ->
                        Button(
                            onClick = {
                                chosen = profile
                                pinInput = ""
                                confirmPinInput = ""
                                error = null
                                step = if (profile.pin.isBlank()) GateStep.SET_PIN else GateStep.ENTER_PIN
                            },
                            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp)
                        ) { Text(profile.name) }
                    }
                }
            }

            GateStep.SET_PIN -> {
                val profile = chosen ?: return@Column
                Text("Set a PIN for ${profile.name}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                GlassSurface(modifier = Modifier.fillMaxWidth(0.75f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) pinInput = it.filter { c -> c.isDigit() } },
                            label = { Text("4-digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 4) confirmPinInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Confirm PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = defaultChecked, onCheckedChange = { defaultChecked = it })
                            Text("Make this my default profile on this device", style = MaterialTheme.typography.bodySmall)
                        }
                        error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (pinInput.length != 4) {
                                    error = "PIN must be 4 digits."
                                } else if (pinInput != confirmPinInput) {
                                    error = "PINs don't match."
                                } else {
                                    onSetPin(profile.name, pinInput)
                                    onSelectProfile(profile.name, true)
                                    if (defaultChecked) onSetDefault(profile.name)
                                    onSignedIn()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Continue") }
                        TextButton(onClick = { step = GateStep.PICK }) { Text("Back") }
                    }
                }
            }

            GateStep.ENTER_PIN -> {
                val profile = chosen ?: return@Column
                Text("Enter PIN for ${profile.name}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                GlassSurface(modifier = Modifier.fillMaxWidth(0.75f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) { pinInput = it.filter { c -> c.isDigit() }; error = null } },
                            label = { Text("4-digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = error != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberChecked, onCheckedChange = { rememberChecked = it })
                            Text("Remember me on this device", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (pinInput == profile.pin) {
                                    onSelectProfile(profile.name, rememberChecked)
                                    onSignedIn()
                                } else {
                                    error = "Incorrect PIN"
                                }
                            },
                            enabled = pinInput.length == 4,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Unlock") }
                        TextButton(onClick = { step = GateStep.PICK }) { Text("Back") }
                    }
                }
            }
        }
    }
}
