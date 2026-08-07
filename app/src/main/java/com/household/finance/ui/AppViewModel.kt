package com.household.finance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.household.finance.data.Account
import com.household.finance.data.AppSettings
import com.household.finance.data.EmergencyFund
import com.household.finance.data.Entry
import com.household.finance.data.FirestoreFinanceRepository
import com.household.finance.data.Goal
import com.household.finance.data.Loan
import com.household.finance.data.Profile
import com.household.finance.logic.Calculations
import com.household.finance.logic.DashboardSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val settings = AppSettings(application)
    private val repository = FirestoreFinanceRepository(application)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _emergencyFund = MutableStateFlow(EmergencyFund())
    val emergencyFund: StateFlow<EmergencyFund> = _emergencyFund.asStateFlow()

    private val _summary = MutableStateFlow(Calculations.summarize(emptyList()))
    val summary: StateFlow<DashboardSummary> = _summary.asStateFlow()

    private val _firestoreReady = MutableStateFlow(false)
    val firestoreReady: StateFlow<Boolean> = _firestoreReady.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _loans = MutableStateFlow<List<Loan>>(emptyList())
    val loans: StateFlow<List<Loan>> = _loans.asStateFlow()

    /** The two household profiles (e.g. Vinnu, Rukmini), shared via Firestore. */
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Currently signed-in profile name on this device, or null before one is chosen. */
    private val _currentProfile = MutableStateFlow<String?>(null)
    val currentProfile: StateFlow<String?> = _currentProfile.asStateFlow()

    /** Convenience: the current profile's name, or "" until one is chosen. */
    private val _nameMe = MutableStateFlow("")
    val nameMe: StateFlow<String> = _nameMe.asStateFlow()

    private var profilesEnsured = false

    init {
        viewModelScope.launch {
            settings.currentProfileFlow.collect {
                _currentProfile.value = it
                _nameMe.value = it ?: ""
            }
        }
        viewModelScope.launch {
            settings.firebaseConfigFlow.collect { config ->
                repository.configure(config)
                _firestoreReady.value = repository.isReady()
                if (repository.isReady()) {
                    launch {
                        repository.observeEntries().collect { list ->
                            _entries.value = list
                            _summary.value = Calculations.summarize(list)
                        }
                    }
                    launch {
                        repository.observeEmergencyFund().collect { _emergencyFund.value = it }
                    }
                    launch {
                        repository.observeGoals().collect { _goals.value = it }
                    }
                    launch {
                        repository.observeAccounts().collect { _accounts.value = it }
                    }
                    launch {
                        repository.observeLoans().collect { _loans.value = it }
                    }
                    launch {
                        repository.observeProfiles().collect { list ->
                            _profiles.value = list
                            if (list.isEmpty() && !profilesEnsured) {
                                profilesEnsured = true
                                repository.saveProfile(Profile(name = "Vinnu", pin = ""))
                                repository.saveProfile(Profile(name = "Rukmini", pin = ""))
                            }
                        }
                    }
                }
            }
        }
    }

    /** Name of the other profile relative to whoever is currently signed in. */
    fun otherProfileName(): String =
        profiles.value.map { it.name }.firstOrNull { it != _currentProfile.value } ?: ""

    /** Signs in as [name] on this device. If [remember] is true, this device skips the PIN for it going forward. */
    fun selectProfile(name: String, remember: Boolean) {
        viewModelScope.launch {
            settings.setCurrentProfile(name)
            if (remember) settings.trustProfile(name)
        }
    }

    fun setDefaultProfile(name: String) {
        viewModelScope.launch { settings.setDefaultProfile(name) }
    }

    /** Returns to the profile picker without losing this device's default/trusted state. */
    fun switchProfile() {
        viewModelScope.launch { settings.clearCurrentProfile() }
    }

    fun setProfilePin(name: String, pin: String) {
        viewModelScope.launch { repository.saveProfile(Profile(name = name, pin = pin)) }
    }

    fun setSalaryCreditDate(name: String, day: Int?) {
        viewModelScope.launch { repository.setProfileSalaryDate(name, day) }
    }

    /**
     * Adding a NEW entry (no id yet) is always attributed to whoever is currently signed in on
     * this device — never the partner's, regardless of what the caller passed in. If tagged with
     * an account, applies its signed amount to that account's balance atomically (creating the
     * account at 0 first if new). Edits to existing entries keep whatever person was set on them.
     */
    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            val isNew = entry.id.isBlank()
            val toSave = if (isNew) entry.copy(person = _currentProfile.value ?: entry.person) else entry
            repository.addEntry(toSave)
            if (isNew && !toSave.accountName.isNullOrBlank()) {
                repository.adjustAccountBalance(toSave.accountName, toSave.signedAccountAmount, _currentProfile.value ?: "")
            }
        }
    }

    fun setAccountBalance(name: String, balance: Double) {
        viewModelScope.launch { repository.setAccountBalance(name, balance) }
    }

    /** Deleting an account-tagged entry reverses its effect on that account's balance. */
    fun deleteEntry(id: String) {
        viewModelScope.launch {
            val entry = _entries.value.find { it.id == id }
            repository.deleteEntry(id)
            if (entry != null && !entry.accountName.isNullOrBlank()) {
                // Account already exists at this point (the entry created it), so owner is unused here.
                repository.adjustAccountBalance(entry.accountName, -entry.signedAccountAmount, "")
            }
        }
    }

    fun refreshEntries() {
        viewModelScope.launch {
            _refreshing.value = true
            repository.refreshEntries()
            _refreshing.value = false
        }
    }

    fun setEmergencyFund(amount: Double) {
        viewModelScope.launch { repository.setEmergencyFund(amount) }
    }

    /** New goals are always owned by whoever's signed in on this device. */
    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            val toSave = if (goal.id.isBlank()) goal.copy(owner = _currentProfile.value ?: goal.owner) else goal
            repository.addGoal(toSave)
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { repository.deleteGoal(id) }
    }

    fun setGoalCompleted(id: String, completed: Boolean) {
        viewModelScope.launch { repository.setGoalCompleted(id, completed) }
    }

    fun addLoan(lender: String, borrower: String, amount: Double, note: String) {
        viewModelScope.launch { repository.addLoan(Loan(lender = lender, borrower = borrower, amount = amount, note = note)) }
    }

    fun setLoanSettled(id: String, settled: Boolean) {
        viewModelScope.launch { repository.setLoanSettled(id, settled) }
    }

    fun deleteLoan(id: String) {
        viewModelScope.launch { repository.deleteLoan(id) }
    }
}
