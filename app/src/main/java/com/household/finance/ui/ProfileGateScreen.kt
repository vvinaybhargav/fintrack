package com.household.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Profile
import com.household.finance.ui.theme.Violet

private enum class GateStep { PICK, SET_PIN, ENTER_PIN }

/**
 * Handles picking which profile is active on this device, and PIN entry for it via a numeric
 * keypad (matching the FinTrack design) rather than a plain text field.
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
    var confirmStage by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var rememberChecked by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetToPick() {
        step = GateStep.PICK
        chosen = null
        pinInput = ""
        firstPin = ""
        confirmStage = false
        error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(Violet, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("F", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "FINTRACK",
            style = MaterialTheme.typography.labelLarge,
            color = Violet,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        when (step) {
            GateStep.PICK -> {
                Text("Who's checking in?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(28.dp))
                if (profiles.isEmpty()) {
                    Text("Setting up profiles…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        profiles.forEach { profile ->
                            OutlinedButton(
                                onClick = {
                                    chosen = profile
                                    pinInput = ""
                                    firstPin = ""
                                    confirmStage = false
                                    error = null
                                    step = if (profile.pin.isBlank()) GateStep.SET_PIN else GateStep.ENTER_PIN
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            GateStep.SET_PIN -> {
                val profile = chosen ?: return@Column
                val prompt = if (!confirmStage) "Set a PIN for ${profile.name}" else "Confirm the PIN"
                Text(prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                PinDots(length = pinInput.length, error = error != null)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(28.dp))
                PinKeypad(
                    onDigit = { d ->
                        if (pinInput.length < 4) {
                            error = null
                            pinInput += d
                            if (pinInput.length == 4) {
                                if (!confirmStage) {
                                    firstPin = pinInput
                                    pinInput = ""
                                    confirmStage = true
                                } else if (pinInput == firstPin) {
                                    onSetPin(profile.name, pinInput)
                                    onSelectProfile(profile.name, true)
                                    onSetDefault(profile.name)
                                    onSignedIn()
                                } else {
                                    error = "PINs don't match — try again"
                                    pinInput = ""
                                    firstPin = ""
                                    confirmStage = false
                                }
                            }
                        }
                    },
                    onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) }
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { resetToPick() }) { Text("Back") }
            }

            GateStep.ENTER_PIN -> {
                val profile = chosen ?: return@Column
                Text("Enter PIN for ${profile.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                PinDots(length = pinInput.length, error = error != null)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(28.dp))
                PinKeypad(
                    onDigit = { d ->
                        if (pinInput.length < 4) {
                            error = null
                            pinInput += d
                            if (pinInput.length == 4) {
                                if (pinInput == profile.pin) {
                                    onSelectProfile(profile.name, rememberChecked)
                                    onSignedIn()
                                } else {
                                    error = "Incorrect PIN"
                                    pinInput = ""
                                }
                            }
                        }
                    },
                    onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) }
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberChecked, onCheckedChange = { rememberChecked = it })
                    Text("Remember me on this device", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { resetToPick() }) { Text("Back") }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4) { i ->
            val filled = i < length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = when {
                            error -> MaterialTheme.colorScheme.error
                            filled -> Violet
                            else -> Color(0x22FFFFFF)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    if (key.isBlank()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        OutlinedButton(
                            onClick = { if (key == "⌫") onBackspace() else onDigit(key) },
                            modifier = Modifier.weight(1f).aspectRatio(1.6f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
