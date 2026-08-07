package com.household.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Entry
import com.household.finance.ui.theme.GlassSurface

@Composable
fun EntriesScreen(
    entries: List<Entry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Entry) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Entries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No entries yet. Add one from the AI tab's Chat.")
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                GlassSurface(modifier = Modifier.fillMaxWidth().clickable { onEdit(entry) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${entry.category} — ${entry.person}", fontWeight = FontWeight.Bold)
                            Text("${entry.type.name} · ${entry.bucket.name} · ${entry.frequency.name}", style = MaterialTheme.typography.bodySmall)
                            Text(formatInr(entry.amount) + if (entry.frequency.name == "ANNUAL") "/yr" else "/mo", style = MaterialTheme.typography.bodySmall)
                            if (entry.note.isNotBlank()) {
                                Text(entry.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { onDelete(entry.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
