package com.household.finance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.household.finance.data.AppSettings
import com.household.finance.data.EmergencyFund
import com.household.finance.data.Entry
import com.household.finance.data.FirestoreFinanceRepository
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
                }
            }
        }
    }

    fun addEntry(entry: Entry) {
        viewModelScope.launch { repository.addEntry(entry) }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun setEmergencyFund(amount: Double) {
        viewModelScope.launch { repository.setEmergencyFund(amount) }
    }
}
