package com.example.jacaccounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jacaccounts.data.Bank
import com.example.jacaccounts.data.Credit
import com.example.jacaccounts.data.Entry
import com.example.jacaccounts.data.MongoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val banks: List<Bank> = emptyList(),
    val expenses: List<Entry> = emptyList(),
    val income: List<Entry> = emptyList(),
    val credits: List<Credit> = emptyList(),
)

class JacViewModel(private val repo: MongoRepository = MongoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = run {
        val banks = repo.listBanks()
        val expenses = repo.listExpenses()
        val income = repo.listIncome()
        val credits = repo.listCredits()
        _state.update { it.copy(banks = banks, expenses = expenses, income = income, credits = credits) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun addBank(name: String, currency: String) = mutate { repo.addBank(name, currency) }
    fun deleteBank(id: String) = mutate { repo.deleteBank(id) }
    fun addExpense(bank: Bank, amount: Double, date: Long, usedFor: String) = mutate { repo.addExpense(bank, amount, date, usedFor) }
    fun deleteExpense(id: String) = mutate { repo.deleteExpense(id) }
    fun addIncome(bank: Bank, amount: Double, date: Long, source: String) = mutate { repo.addIncome(bank, amount, date, source) }
    fun deleteIncome(id: String) = mutate { repo.deleteIncome(id) }
    fun addCredit(creditor: String, amount: Double, paid: Double) = mutate { repo.addCredit(creditor, amount, paid) }
    fun addCreditPayment(id: String, extra: Double) = mutate { repo.addCreditPayment(id, extra) }
    fun deleteCredit(id: String) = mutate { repo.deleteCredit(id) }

    private fun mutate(write: suspend () -> Unit) = run { write(); refresh().join() }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        try {
            block()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }
}
