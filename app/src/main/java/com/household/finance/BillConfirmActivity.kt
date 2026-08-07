package com.household.finance

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.AppSettings
import com.household.finance.data.Bill
import com.household.finance.data.FirestoreFinanceRepository
import com.household.finance.ui.formatInr
import com.household.finance.ui.theme.GlassSurface
import com.household.finance.ui.theme.HouseholdFinanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val EXTRA_BILL_ID = "bill_id"

fun billConfirmIntent(context: android.content.Context, billId: String) =
    android.content.Intent(context, BillConfirmActivity::class.java).apply {
        putExtra(EXTRA_BILL_ID, billId)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Floating "have you paid this?" confirmation opened by tapping a due-bill notification. */
class BillConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val billId = intent?.getStringExtra(EXTRA_BILL_ID)
        setContent {
            HouseholdFinanceTheme {
                BillConfirmScreen(billId = billId, onDone = { finish() })
            }
        }
    }
}

@Composable
private fun BillConfirmScreen(billId: String?, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    var bill by remember { mutableStateOf<Bill?>(null) }
    var loading by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(billId) {
        if (billId == null) { onDone(); return@LaunchedEffect }
        val config = settings.currentFirebaseConfig()
        if (!config.isComplete) { onDone(); return@LaunchedEffect }
        val repository = FirestoreFinanceRepository(context)
        repository.configure(config)
        val found = repository.observeBills().first().find { it.id == billId }
        if (found == null) { onDone(); return@LaunchedEffect }
        bill = found
        loading = false
    }

    fun confirmPaid() {
        val current = bill ?: return
        confirming = true
        scope.launch {
            val config = settings.currentFirebaseConfig()
            val nameMe = settings.currentProfileFlow.first().orEmpty()
            val repository = FirestoreFinanceRepository(context)
            repository.configure(config)
            repository.markBillPaid(current.id, nameMe)
            val toastNote = current.toAccountName?.let { " — moved into $it" } ?: current.accountName?.let { " — debited from $it" } ?: ""
            Toast.makeText(context, "Marked paid$toastNote", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA05060A))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDone() }
            .padding(top = 140.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                val current = bill
                if (loading || current == null) {
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Have you paid this?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${current.name} — ${formatInr(current.amount)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Due ${current.dueDate}", style = MaterialTheme.typography.bodySmall)
                    run {
                        val debitNote = current.accountName?.let { "debits ${formatInr(current.amount)} from $it" }
                        val creditNote = current.toAccountName?.let { "moves ${formatInr(current.amount)} into $it" }
                        val note = listOfNotNull(debitNote, creditNote).joinToString(" and ")
                        if (note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Confirming $note.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { confirmPaid() }, enabled = !confirming) { Text(if (confirming) "Saving…" else "Yes, paid") }
                        OutlinedButton(onClick = onDone, enabled = !confirming) { Text("Not yet") }
                    }
                }
            }
        }
    }
}
