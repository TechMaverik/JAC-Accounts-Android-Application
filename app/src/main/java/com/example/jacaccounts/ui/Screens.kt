package com.example.jacaccounts.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.example.jacaccounts.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jacaccounts.data.Bank
import com.example.jacaccounts.data.CURRENCIES
import com.example.jacaccounts.data.Credit
import com.example.jacaccounts.data.EXPENSE_CATEGORIES
import com.example.jacaccounts.data.Entry
import com.example.jacaccounts.data.INCOME_SOURCES
import com.example.jacaccounts.data.todayMillis

private val TABS = listOf("Trends", "Banks", "Expenses", "Income", "Credits")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JacApp(vm: JacViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(32.dp))
                            Text("  JAC Accounts")
                        }
                    },
                    actions = { TextButton(onClick = vm::refresh) { Text("Refresh") } },
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab != 0) ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                Text(
                    when (tab) {
                        1 -> "Add Bank"
                        2 -> "Add Expense"
                        3 -> "Add Income"
                        else -> "Add Credit"
                    }
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> TrendsScreen(state.income, state.expenses, modifier)
            1 -> BankList(state.banks, state.income, state.expenses, vm::deleteBank, modifier)
            2 -> EntryList(state.expenses, "No expenses yet", vm::deleteExpense, modifier)
            3 -> EntryList(state.income, "No income yet", vm::deleteIncome, modifier)
            else -> CreditList(state.credits, vm::addCreditPayment, vm::deleteCredit, modifier)
        }
    }

    if (showAdd) {
        val close = { showAdd = false }
        when (tab) {
            1 -> AddBankDialog(close) { n, c -> vm.addBank(n, c); close() }
            2 -> AddEntryDialog(
                title = "Add Expense", labelName = "Used for", options = EXPENSE_CATEGORIES, banks = state.banks,
                onDismiss = close,
            ) { b, a, d, l -> vm.addExpense(b, a, d, l); close() }
            3 -> AddEntryDialog(
                title = "Add Income", labelName = "Source", options = INCOME_SOURCES, banks = state.banks,
                onDismiss = close,
            ) { b, a, d, l -> vm.addIncome(b, a, d, l); close() }
            else -> AddCreditDialog(close) { n, a, p -> vm.addCredit(n, a, p); close() }
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)

@Composable
private fun BankList(
    banks: List<Bank>, income: List<Entry>, expenses: List<Entry>,
    onDelete: (String) -> Unit, modifier: Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Bank?>(null) }
    if (banks.isEmpty()) return EmptyHint("No banks yet. Add one to get started.", modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(banks, key = { it.id }) { bank ->
            val net = income.filter { it.bankId == bank.id }.sumOf { it.amount } -
                expenses.filter { it.bankId == bank.id }.sumOf { it.amount }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bank.name, style = MaterialTheme.typography.titleMedium)
                        Text("Currency: ${bank.currency}")
                        Text("Income - Expenses: ${money(net, bank.currency)}")
                    }
                    TextButton(onClick = { pendingDelete = bank }) { Text("Delete") }
                }
            }
        }
    }
    pendingDelete?.let { b ->
        ConfirmDeleteDialog("bank ${b.name}", onYes = { onDelete(b.id); pendingDelete = null }, onNo = { pendingDelete = null })
    }
}

@Composable
private fun EntryList(entries: List<Entry>, emptyText: String, onDelete: (String) -> Unit, modifier: Modifier) {
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    if (entries.isEmpty()) return EmptyHint(emptyText, modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(e.amount, e.currency), style = MaterialTheme.typography.titleMedium)
                        Text("${e.label} • ${e.bankName}")
                        Text(formatDate(e.date), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pendingDelete = e }) { Text("Delete") }
                }
            }
        }
    }
    pendingDelete?.let { e ->
        ConfirmDeleteDialog(
            "this ${money(e.amount, e.currency)} (${e.label}) entry",
            onYes = { onDelete(e.id); pendingDelete = null }, onNo = { pendingDelete = null },
        )
    }
}

@Composable
private fun CreditList(credits: List<Credit>, onPay: (String, Double) -> Unit, onDelete: (String) -> Unit, modifier: Modifier) {
    var pendingDelete by remember { mutableStateOf<Credit?>(null) }
    var paying by remember { mutableStateOf<Credit?>(null) }
    if (credits.isEmpty()) return EmptyHint("No credits tracked yet.", modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(credits, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.creditor, style = MaterialTheme.typography.titleMedium)
                    LabelValue("Amount", "%,.2f".format(c.amount))
                    LabelValue("Paid", "%,.2f".format(c.paid))
                    LabelValue("Balance", "%,.2f".format(c.balance))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { paying = c }) { Text("Add payment") }
                        TextButton(onClick = { pendingDelete = c }) { Text("Delete") }
                    }
                }
            }
        }
    }
    paying?.let { c ->
        var text by remember { mutableStateOf("") }
        val amt = parseAmount(text)
        FormDialog("Payment to ${c.creditor}", confirmEnabled = amt != null && amt > 0, onDismiss = { paying = null },
            onConfirm = { onPay(c.id, amt!!); paying = null }) {
            AmountField("Amount paid now", text) { text = it }
        }
    }
    pendingDelete?.let { c ->
        ConfirmDeleteDialog("credit to ${c.creditor}", onYes = { onDelete(c.id); pendingDelete = null }, onNo = { pendingDelete = null })
    }
}

@Composable
private fun AddBankDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<String?>(null) }
    FormDialog("Add Bank", confirmEnabled = name.isNotBlank() && currency != null, onDismiss = onDismiss,
        onConfirm = { onSave(name.trim(), currency!!) }) {
        androidx.compose.material3.OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Bank name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FieldGap()
        Dropdown("Currency", CURRENCIES, currency, { it }) { currency = it }
    }
}

@Composable
private fun AddEntryDialog(
    title: String, labelName: String, options: List<String>, banks: List<Bank>,
    onDismiss: () -> Unit, onSave: (Bank, Double, Long, String) -> Unit,
) {
    var bank by remember { mutableStateOf<Bank?>(null) }
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(todayMillis()) }
    val amt = parseAmount(amount)
    FormDialog(title, confirmEnabled = bank != null && amt != null && amt > 0 && label != null, onDismiss = onDismiss,
        onConfirm = { onSave(bank!!, amt!!, date, label!!) }) {
        if (banks.isEmpty()) Text("Add a bank first.")
        Dropdown("Bank", banks, bank, { "${it.name} (${it.currency})" }) { bank = it }
        FieldGap()
        AmountField("Amount${bank?.let { " (${it.currency})" } ?: ""}", amount) { amount = it }
        FieldGap()
        Dropdown(labelName, options, label, { it }) { label = it }
        FieldGap()
        DateField(date) { date = it }
    }
}

@Composable
private fun AddCreditDialog(onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var who by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    val amt = parseAmount(amount)
    val paidAmt = if (paid.isBlank()) 0.0 else parseAmount(paid)
    val balance = if (amt != null && paidAmt != null) amt - paidAmt else null
    FormDialog("Add Credit", confirmEnabled = who.isNotBlank() && amt != null && amt > 0 && paidAmt != null && paidAmt <= amt,
        onDismiss = onDismiss, onConfirm = { onSave(who.trim(), amt!!, paidAmt!!) }) {
        androidx.compose.material3.OutlinedTextField(
            value = who, onValueChange = { who = it }, label = { Text("To whom I owe") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FieldGap()
        AmountField("Amount", amount) { amount = it }
        FieldGap()
        AmountField("Amount paid", paid) { paid = it }
        FieldGap()
        Text("Balance: ${balance?.let { "%,.2f".format(it) } ?: "-"}")
    }
}
