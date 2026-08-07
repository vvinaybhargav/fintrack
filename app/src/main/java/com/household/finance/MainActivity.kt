package com.household.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.household.finance.data.Entry
import com.household.finance.ui.AddEntryScreen
import com.household.finance.ui.AiHubScreen
import com.household.finance.ui.AppViewModel
import com.household.finance.ui.DashboardScreen
import com.household.finance.ui.EntriesScreen
import com.household.finance.ui.PinLockScreen
import com.household.finance.ui.SettingsScreen
import com.household.finance.ui.theme.GlassBackdrop
import com.household.finance.ui.theme.HouseholdFinanceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HouseholdFinanceTheme {
                GlassBackdrop {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Filled.Home),
    Tab("add", "Add", Icons.Filled.Add),
    Tab("entries", "Entries", Icons.Filled.List),
    Tab("ai", "AI", Icons.Filled.AutoAwesome),
    Tab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val pin by viewModel.settings.pinFlow.collectAsStateWithLifecycle(initialValue = "1234")
    var unlocked by remember { mutableStateOf(false) }

    if (!unlocked) {
        PinLockScreen(correctPin = pin) { unlocked = true }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val emergencyFund by viewModel.emergencyFund.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val nameMe by viewModel.nameMe.collectAsStateWithLifecycle()
    val nameWife by viewModel.nameWife.collectAsStateWithLifecycle()
    val openAiKey by viewModel.settings.openAiKeyFlow.collectAsStateWithLifecycle(initialValue = "")
    val firebaseConfig by viewModel.settings.firebaseConfigFlow.collectAsStateWithLifecycle(
        initialValue = com.household.finance.data.AppSettings.FirebaseConfig()
    )
    val firestoreReady by viewModel.firestoreReady.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var editingEntry by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (tab.route != "add") editingEntry = null
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            composable("dashboard") {
                DashboardScreen(
                    summary = summary,
                    entries = entries,
                    emergencyFundAmount = emergencyFund.currentAmount,
                    nameMe = nameMe,
                    nameWife = nameWife,
                    onSetEmergencyFund = { viewModel.setEmergencyFund(it) }
                )
            }
            composable("add") {
                AddEntryScreen(
                    nameMe = nameMe,
                    nameWife = nameWife,
                    openAiKey = openAiKey,
                    editingEntry = editingEntry,
                    onSave = { viewModel.addEntry(it) },
                    onCancelEdit = { editingEntry = null }
                )
            }
            composable("entries") {
                EntriesScreen(
                    entries = entries,
                    onDelete = { viewModel.deleteEntry(it) },
                    onEdit = { entry ->
                        editingEntry = entry
                        navController.navigate("add") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("ai") {
                AiHubScreen(
                    entries = entries,
                    goals = goals,
                    monthlySurplus = summary.surplus,
                    openAiKey = openAiKey,
                    onAddGoal = { viewModel.addGoal(it) },
                    onDeleteGoal = { viewModel.deleteGoal(it) }
                )
            }
            composable("settings") {
                SettingsScreen(
                    nameMe = nameMe,
                    nameWife = nameWife,
                    pin = pin,
                    openAiKey = openAiKey,
                    firebaseConfig = firebaseConfig,
                    firestoreReady = firestoreReady,
                    onSaveNames = { me, wife -> scope.launch { viewModel.settings.saveNames(me, wife) } },
                    onSavePin = { newPin -> scope.launch { viewModel.settings.savePin(newPin) } },
                    onSaveOpenAiKey = { key -> scope.launch { viewModel.settings.saveOpenAiKey(key) } },
                    onSaveFirebaseConfig = { config -> scope.launch { viewModel.settings.saveFirebaseConfig(config) } }
                )
            }
        }
    }
}
