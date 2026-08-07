package com.household.finance

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.household.finance.ui.FirebaseSetupScreen
import com.household.finance.ui.ProfileGateScreen
import com.household.finance.ui.SettingsScreen
import com.household.finance.ui.theme.GlassBackdrop
import com.household.finance.ui.theme.HouseholdFinanceTheme
import com.household.finance.widget.LoanReminderScheduler
import com.household.finance.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        LoanReminderScheduler.schedule(applicationContext)
        val startRoute = intent?.getStringExtra("start_route")?.takeIf { it in setOf("dashboard", "add", "entries", "ai", "settings") } ?: "dashboard"
        setContent {
            HouseholdFinanceTheme {
                GlassBackdrop {
                    AppRoot(viewModel, startRoute)
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// "add" is intentionally not a tab: entries are added only via AI > Chat. The route still
// exists in the NavHost below, reached only when editing an existing entry from the Entries list.
private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Filled.Home),
    Tab("entries", "Entries", Icons.Filled.List),
    Tab("ai", "AI", Icons.Filled.AutoAwesome),
    Tab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
private fun AppRoot(viewModel: AppViewModel, startRoute: String) {
    val firestoreReadyEarly by viewModel.firestoreReady.collectAsStateWithLifecycle()
    val scopeEarly = rememberCoroutineScope()

    // Without Firestore configured, the profile gate below has nothing to show (the profile list
    // only exists once connected) - so a fresh install would be stuck forever. Let config happen first.
    if (!firestoreReadyEarly) {
        FirebaseSetupScreen(
            onSaveFirebaseConfig = { config -> scopeEarly.launch { viewModel.settings.saveFirebaseConfig(config) } }
        )
        return
    }

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val trustedProfiles by viewModel.settings.trustedProfilesFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    var manuallyUnlocked by remember { mutableStateOf(false) }

    val isTrusted = currentProfile != null && currentProfile in trustedProfiles
    val unlocked = currentProfile != null && (isTrusted || manuallyUnlocked)

    if (!unlocked) {
        ProfileGateScreen(
            profiles = profiles,
            onSelectProfile = { name, rememberDevice -> viewModel.selectProfile(name, rememberDevice) },
            onSetPin = { name, pin -> viewModel.setProfilePin(name, pin) },
            onSetDefault = { name -> viewModel.setDefaultProfile(name) },
            onSignedIn = { manuallyUnlocked = true }
        )
        return
    }

    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val emergencyFund by viewModel.emergencyFund.collectAsStateWithLifecycle()
    val personalEmergencyFund by viewModel.personalEmergencyFund.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val activeLoans by viewModel.activeLoans.collectAsStateWithLifecycle()
    val nameMe by viewModel.nameMe.collectAsStateWithLifecycle()
    val nameWife = profiles.map { it.name }.firstOrNull { it != nameMe } ?: ""
    val openAiKey by viewModel.settings.openAiKeyFlow.collectAsStateWithLifecycle(initialValue = "")
    val firebaseConfig by viewModel.settings.firebaseConfigFlow.collectAsStateWithLifecycle(
        initialValue = com.household.finance.data.AppSettings.FirebaseConfig()
    )
    val firestoreReady by viewModel.firestoreReady.collectAsStateWithLifecycle()
    val defaultProfile by viewModel.settings.defaultProfileFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var editingEntry by remember { mutableStateOf<Entry?>(null) }

    LaunchedEffect(summary.surplus, accounts) {
        WidgetUpdater.update(context, summary.surplus, accounts)
    }

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
            startDestination = startRoute,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            composable("dashboard") {
                val categoryLength by viewModel.settings.categoryLengthFlow.collectAsStateWithLifecycle(
                    initialValue = com.household.finance.data.CategoryListLength.MEDIUM
                )
                val myProfile = profiles.find { it.name == nameMe }
                val defaultAccount = myProfile?.defaultAccountName
                val salaryAmount = myProfile?.salaryAmount
                val salaryCreditDate = myProfile?.salaryCreditDate
                DashboardScreen(
                    entries = entries,
                    accounts = accounts,
                    goals = goals,
                    loans = loans,
                    bills = bills,
                    categoryLength = categoryLength,
                    jointFundAmount = emergencyFund.currentAmount,
                    personalFundAmount = personalEmergencyFund.currentAmount,
                    budgets = budgets,
                    nameMe = nameMe,
                    defaultAccount = defaultAccount,
                    activeLoans = activeLoans,
                    salaryAmount = salaryAmount,
                    salaryCreditDate = salaryCreditDate,
                    onSetJointFund = { viewModel.setEmergencyFund(it) },
                    onSetPersonalFund = { viewModel.setPersonalEmergencyFund(it) },
                    onSetBudgetLimit = { category, limit -> viewModel.setBudgetLimit(category, limit) },
                    onSetAccountBalance = { name, balance -> viewModel.setAccountBalance(name, balance) },
                    onRenameAccount = { old, new -> viewModel.renameAccount(old, new) },
                    onDeleteAccount = { viewModel.deleteAccount(it) },
                    onSetGoalCompleted = { id, completed -> viewModel.setGoalCompleted(id, completed) },
                    onAddGoalContribution = { id, amount -> viewModel.addGoalContribution(id, amount) },
                    onDeleteGoal = { viewModel.deleteGoal(it) },
                    onSetLoanSettled = { id, settled -> viewModel.setLoanSettled(id, settled) },
                    onSetLoanDueDate = { id, date -> viewModel.setLoanDueDate(id, date) },
                    onAddBill = { viewModel.addBill(it) },
                    onDeleteBill = { viewModel.deleteBill(it) },
                    onMarkBillPaid = { viewModel.markBillPaid(it) },
                    onCompleteCommitment = { template ->
                        val entry = Entry(
                            person = nameMe,
                            type = template.type,
                            bucket = template.bucket,
                            category = template.category,
                            amount = template.monthlyAmount,
                            frequency = com.household.finance.data.Frequency.MONTHLY,
                            note = template.note,
                            accountName = defaultAccount ?: accounts.firstOrNull()?.name,
                            toAccountName = template.toAccountName
                        )
                        viewModel.addEntry(entry)
                    },
                    onUndoCommitment = { id -> viewModel.deleteEntry(id) },
                    onSeedCommitments = { viewModel.seedDefaultCommitments() },
                    onAddActiveLoan = { loan -> viewModel.addActiveLoan(loan) },
                    onDeleteActiveLoan = { id -> viewModel.deleteActiveLoan(id) },
                    onUpdateActiveLoanPrepayment = { id, amount -> viewModel.updateActiveLoanPrepayment(id, amount) }
                )
            }
            composable("add") {
                val categoryLength by viewModel.settings.categoryLengthFlow.collectAsStateWithLifecycle(
                    initialValue = com.household.finance.data.CategoryListLength.MEDIUM
                )
                AddEntryScreen(
                    nameMe = nameMe,
                    nameWife = nameWife,
                    openAiKey = openAiKey,
                    categoryLength = categoryLength,
                    editingEntry = editingEntry,
                    onSave = { viewModel.addEntry(it) },
                    onCancelEdit = { editingEntry = null }
                )
            }
            composable("entries") {
                val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
                EntriesScreen(
                    entries = entries,
                    nameMe = nameMe,
                    refreshing = refreshing,
                    onRefresh = { viewModel.refreshEntries() },
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
                val categoryLength by viewModel.settings.categoryLengthFlow.collectAsStateWithLifecycle(
                    initialValue = com.household.finance.data.CategoryListLength.MEDIUM
                )
                AiHubScreen(
                    entries = entries,
                    goals = goals,
                    accounts = accounts,
                    loans = loans,
                    emergencyFundAmount = emergencyFund.currentAmount,
                    defaultAccount = profiles.find { it.name == nameMe }?.defaultAccountName,
                    openAiKey = openAiKey,
                    categories = com.household.finance.data.categoriesFor(categoryLength),
                    nameMe = nameMe,
                    nameWife = nameWife,
                    onAddGoal = { viewModel.addGoal(it) },
                    onDeleteGoal = { viewModel.deleteGoal(it) },
                    onAddEntry = { viewModel.addEntry(it) },
                    onDeleteEntry = { viewModel.deleteEntry(it) },
                    onEditEntry = { viewModel.addEntry(it) },
                    onSetAccountBalance = { name, balance -> viewModel.setAccountBalance(name, balance) },
                    onAddLoan = { lender, borrower, amount, note, accountName -> viewModel.addLoan(lender, borrower, amount, note, accountName) },
                    onDeleteLoan = { viewModel.deleteLoan(it) },
                    onAddGoalContribution = { id, amount -> viewModel.addGoalContribution(id, amount) },
                    onTransfer = { from, to, amount, note -> viewModel.transferBetweenAccounts(from, to, amount, note) }
                )
            }
            composable("settings") {
                val categoryLength by viewModel.settings.categoryLengthFlow.collectAsStateWithLifecycle(
                    initialValue = com.household.finance.data.CategoryListLength.MEDIUM
                )
                val salaryCreditDate = profiles.find { it.name == nameMe }?.salaryCreditDate
                val salaryAmount = entries.find { it.id == com.household.finance.data.salaryEntryId(nameMe) }?.amount
                val defaultAccountName = profiles.find { it.name == nameMe }?.defaultAccountName
                SettingsScreen(
                    nameMe = nameMe,
                    nameWife = nameWife,
                    isDefaultProfile = defaultProfile == nameMe,
                    openAiKey = openAiKey,
                    firebaseConfig = firebaseConfig,
                    firestoreReady = firestoreReady,
                    categoryLength = categoryLength,
                    salaryCreditDate = salaryCreditDate,
                    salaryAmount = salaryAmount,
                    defaultAccountName = defaultAccountName,
                    entries = entries,
                    accounts = accounts,
                    goals = goals,
                    loans = loans,
                    onSwitchProfile = { manuallyUnlocked = false; viewModel.switchProfile() },
                    onSetDefaultProfile = { viewModel.setDefaultProfile(nameMe) },
                    onChangePin = { pin -> viewModel.setProfilePin(nameMe, pin) },
                    onRenameProfile = { newName -> viewModel.renameCurrentProfile(newName) },
                    onResetPartnerPin = { viewModel.resetOtherProfilePin(nameWife) },
                    onRenameCategory = { old, new -> viewModel.renameCategory(old, new) },
                    onSaveOpenAiKey = { key -> scope.launch { viewModel.settings.saveOpenAiKey(key) } },
                    onSaveFirebaseConfig = { config -> scope.launch { viewModel.settings.saveFirebaseConfig(config) } },
                    onSaveSalaryCreditDate = { day -> viewModel.setSalaryCreditDate(nameMe, day) },
                    onSaveSalaryAmount = { amount -> viewModel.setSalaryAmount(amount) },
                    onSetDefaultAccount = { account -> viewModel.setDefaultAccount(account) },
                    onSaveCategoryLength = { length -> scope.launch { viewModel.settings.saveCategoryLength(length) } }
                )
            }
        }
    }
}
