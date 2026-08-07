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

    private val _nameMe = MutableStateFlow("Me")
    val nameMe: StateFlow<String> = _nameMe.asStateFlow()

    private val _nameWife = MutableStateFlow("Wife")
    val nameWife: StateFlow<String> = _nameWife.asStateFlow()

    private val _firestoreReady = MutableStateFlow(false)
    val firestoreReady: StateFlow<Boolean> = _firestoreReady.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            settings.nameMeFlow.collect { _nameMe.value = it }
        }
        viewModelScope.launch {
            settings.nameWifeFlow.collect { _nameWife.value = it }
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
                }
            }
        }
    }

    /**
     * Adding a NEW entry (no id yet) is always attributed to this device's own identity
     * (nameMe) — never the partner's, regardless of what the caller passed in. If tagged with
     * an account, applies its signed amount to that account's balance atomically (creating the
     * account at 0 first if new). Edits to existing entries keep whatever person was set on them.
     */
    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            val isNew = entry.id.isBlank()
            val toSave = if (isNew) entry.copy(person = _nameMe.value) else entry
            repository.addEntry(toSave)
            if (isNew && !toSave.accountName.isNullOrBlank()) {
                repository.adjustAccountBalance(toSave.accountName, toSave.signedAccountAmount)
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
                repository.adjustAccountBalance(entry.accountName, -entry.signedAccountAmount)
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

    fun addGoal(goal: Goal) {
        viewModelScope.launch { repository.addGoal(goal) }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { repository.deleteGoal(id) }
    }
}
