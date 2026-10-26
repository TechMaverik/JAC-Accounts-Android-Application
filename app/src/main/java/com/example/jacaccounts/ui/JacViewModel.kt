package com.example.jacaccounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jacaccounts.data.Account
import com.example.jacaccounts.data.DEFAULT_CURRENCY
import com.example.jacaccounts.data.Debt
import com.example.jacaccounts.data.Entry
import com.example.jacaccounts.data.MongoRepository
import com.example.jacaccounts.data.Rates
import com.example.jacaccounts.data.Transfer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val accounts: List<Account> = emptyList(),
    val expenses: List<Entry> = emptyList(),
    val income: List<Entry> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val rates: Rates = Rates(emptyList()),
    val displayCurrency: String = DEFAULT_CURRENCY,
)

class JacViewModel(private val repo: MongoRepository = MongoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = run {
        val accounts = repo.listAccounts()
        val expenses = repo.listExpenses()
        val income = repo.listIncome()
        val transfers = repo.listTransfers()
        val debts = repo.listDebts()
        val rates = Rates(repo.listRates())
        val display = repo.displayCurrency()
        _state.update {
            it.copy(
                accounts = accounts, expenses = expenses, income = income, transfers = transfers,
                debts = debts, rates = rates, displayCurrency = display,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun addAccount(name: String, currency: String) = mutate { repo.addAccount(name, currency) }
    fun addExpense(account: Account, amount: Double, date: Long, category: String) = mutate { repo.addExpense(account, amount, date, category); null }
    fun deleteExpense(id: String) = mutate { repo.deleteExpense(id); null }
    fun addIncome(account: Account, amount: Double, date: Long, category: String) = mutate { repo.addIncome(account, amount, date, category); null }
    fun deleteIncome(id: String) = mutate { repo.deleteIncome(id); null }
    fun addTransfer(from: Account, to: Account, amount: Double, category: String, date: Long, rate: Double) =
        mutate { repo.addTransfer(from, to, amount, category, date, rate); null }
    fun addDebt(name: String, amount: Double, description: String, currency: String) =
        mutate { repo.addDebt(name, amount, description, currency); null }

    fun addDebtPayment(debt: Debt, amount: Double, date: Long, from: Account?) = mutate {
        val spent = from?.let { _state.value.rates.convert(amount, debt.currency, it.currency) }
        if (from != null && spent == null) "No exchange rate for ${debt.currency} to ${from.currency}."
        else { repo.addDebtPayment(debt, amount, date, from, spent); null }
    }

    /** Runs a write that may return a validation message, then reloads. */
    private fun mutate(write: suspend () -> String?) = run {
        val problem = write()
        if (problem != null) _state.update { it.copy(error = problem) }
        refresh().join()
    }

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
