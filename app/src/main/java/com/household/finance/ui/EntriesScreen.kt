package com.household.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.data.Bucket
import com.household.finance.data.Entry
import com.household.finance.ui.theme.GlassSurface
import java.text.SimpleDateFormat
import java.util.Locale

private enum class EntriesView { PERSONAL, JOINT }

private val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)

@Composable
fun EntriesScreen(
    entries: List<Entry>,
    nameMe: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Entry) -> Unit
) {
    var view by remember { mutableStateOf(EntriesView.PERSONAL) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var monthFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Same split as the dashboard: Personal shows only this profile's own entries, Joint shows shared ones.
    val bucketFiltered = remember(entries, view, nameMe) {
        when (view) {
            EntriesView.PERSONAL -> entries.filter { it.bucket == Bucket.PERSONAL && it.person == nameMe }
            EntriesView.JOINT -> entries.filter { it.bucket == Bucket.JOINT }
        }
    }
    val availableCategories = remember(bucketFiltered) { bucketFiltered.map { it.category }.distinct().sorted() }
    val availableMonths = remember(bucketFiltered) {
        bucketFiltered.map { monthFormat.format(java.util.Date(it.createdAt)) }.distinct()
    }
    val filtered = remember(bucketFiltered, categoryFilter, monthFilter, searchQuery) {
        bucketFiltered.filter { entry ->
            (categoryFilter == null || entry.category == categoryFilter) &&
                (monthFilter == null || monthFormat.format(java.util.Date(entry.createdAt)) == monthFilter) &&
                (searchQuery.isBlank() || entry.category.contains(searchQuery, ignoreCase = true) || entry.note.contains(searchQuery, ignoreCase = true))
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = view == EntriesView.PERSONAL,
                onClick = { view = EntriesView.PERSONAL },
                label = { Text(nameMe) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = view == EntriesView.JOINT,
                onClick = { view = EntriesView.JOINT },
                label = { Text("Joint") },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search category or note…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (availableMonths.size > 1 || availableCategories.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = monthFilter == null, onClick = { monthFilter = null }, label = { Text("All months") })
                }
                items(availableMonths) { month ->
                    FilterChip(selected = monthFilter == month, onClick = { monthFilter = if (monthFilter == month) null else month }, label = { Text(month) })
                }
                item {
                    FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All categories") })
                }
                items(availableCategories) { category ->
                    FilterChip(selected = categoryFilter == category, onClick = { categoryFilter = if (categoryFilter == category) null else category }, label = { Text(category) })
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (categoryFilter != null || monthFilter != null) "No entries match these filters."
                    else if (view == EntriesView.PERSONAL) "No personal entries yet. Add one from the AI tab's Chat."
                    else "No joint entries yet."
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered, key = { it.id }) { entry ->
                GlassSurface(modifier = Modifier.fillMaxWidth().clickable { onEdit(entry) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // No need to name the person on your own personal entries - only Joint
                            // items (which either partner can add) show who logged them.
                            Text(
                                if (view == EntriesView.JOINT) "${entry.category} — ${entry.person}" else entry.category,
                                fontWeight = FontWeight.Bold
                            )
                            Text("${entry.type.name} · ${entry.frequency.name}", style = MaterialTheme.typography.bodySmall)
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
