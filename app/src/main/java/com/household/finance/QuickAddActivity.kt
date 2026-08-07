package com.household.finance

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.AppSettings
import com.household.finance.data.FirestoreFinanceRepository
import com.household.finance.data.categoriesFor
import com.household.finance.logic.SmartAddParser
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.HouseholdFinanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A quick, floating "type it and it's saved" entry point launched from the home-screen widget -
 * skips the full app UI entirely, closest thing to a Chrome-search-widget style capture box that
 * Android's RemoteViews/Glance widgets can support (they can't host a live text field in-widget).
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HouseholdFinanceTheme {
                QuickAddScreen(onDone = { finish() })
            }
        }
    }
}

@Composable
private fun QuickAddScreen(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    fun submit() {
        val note = text.trim()
        if (note.isBlank() || saving) return
        saving = true
        scope.launch {
            val nameMe = settings.currentProfileFlow.first().orEmpty()
            if (nameMe.isBlank()) {
                Toast.makeText(context, "Sign in to a profile in the app first.", Toast.LENGTH_LONG).show()
                onDone()
                return@launch
            }
            val config = settings.currentFirebaseConfig()
            if (!config.isComplete) {
                Toast.makeText(context, "Set up Firebase in the app first.", Toast.LENGTH_LONG).show()
                onDone()
                return@launch
            }
            val categoryLength = settings.categoryLengthFlow.first()
            val repository = FirestoreFinanceRepository(context)
            repository.configure(config)
            val entry = SmartAddParser.parseRuleBased(note, nameMe, "", categoriesFor(categoryLength))
            repository.addEntry(entry)
            if (!entry.accountName.isNullOrBlank()) {
                val accounts = repository.observeAccounts().first()
                val isNewAccount = accounts.none { it.name.equals(entry.accountName, ignoreCase = true) }
                repository.adjustAccountBalance(entry.accountName, entry.signedAccountAmount, nameMe, isNewAccount, nameMe)
            }
            Toast.makeText(context, "Logged: ${entry.category} — ₹${entry.amount.toInt()}", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA05060A))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDone() }
            .padding(top = 90.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Quick Add", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Type it, hit send — saved instantly, no need to open the app.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("e.g. \"22k emi from icici\"") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { submit() }, enabled = !saving && text.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = "Save")
                    }
                }
                if (saving) {
                    Spacer(Modifier.height(6.dp))
                    Text("Saving…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
