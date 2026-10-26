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
import com.example.jacaccounts.data.Account
import com.example.jacaccounts.data.CURRENCIES
import com.example.jacaccounts.data.DEBT_PAYMENT_CATEGORY
import com.example.jacaccounts.data.Debt
import com.example.jacaccounts.data.EXPENSE_CATEGORIES
import com.example.jacaccounts.data.Entry
import com.example.jacaccounts.data.INCOME_SOURCES
import com.example.jacaccounts.data.Rates
import com.example.jacaccounts.data.TRANSFER_CATEGORIES
import com.example.jacaccounts.data.Transfer
import com.example.jacaccounts.data.todayMillis

private val TABS = listOf("Trends", "Accounts", "Expenses", "Income", "Transfers", "Debts")

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
                        1 -> "Add Account"
                        2 -> "Add Expense"
                        3 -> "Add Income"
                        4 -> "Add Transfer"
                        else -> "Add Debt"
                    }
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> TrendsScreen(state.income, state.expenses, modifier)
            1 -> AccountList(state, modifier)
            2 -> EntryList(state.expenses, "No expenses yet", vm::deleteExpense, modifier)
            3 -> EntryList(state.income, "No income yet", vm::deleteIncome, modifier)
            4 -> TransferList(state.transfers, modifier)
            else -> DebtList(state.debts, state.accounts, vm::addDebtPayment, modifier)
        }
    }

    if (showAdd) {
        val close = { showAdd = false }
        when (tab) {
            1 -> AddAccountDialog(close) { n, c -> vm.addAccount(n, c); close() }
            2 -> AddEntryDialog(
                title = "Add Expense", labelName = "Category", options = EXPENSE_CATEGORIES, accounts = state.accounts,
                onDismiss = close,
            ) { a, amt, d, l -> vm.addExpense(a, amt, d, l); close() }
            3 -> AddEntryDialog(
                title = "Add Income", labelName = "Category", options = INCOME_SOURCES, accounts = state.accounts,
                onDismiss = close,
            ) { a, amt, d, l -> vm.addIncome(a, amt, d, l); close() }
            4 -> AddTransferDialog(state.accounts, state.rates, close) { f, t, a, c, d, r -> vm.addTransfer(f, t, a, c, d, r); close() }
            else -> AddDebtDialog(close) { n, a, d, c -> vm.addDebt(n, a, d, c); close() }
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
private fun AccountList(state: UiState, modifier: Modifier) {
    if (state.accounts.isEmpty()) return EmptyHint("No accounts yet. Add one to get started.", modifier)
    // Balance = everything received minus everything spent, transfer legs included (as in the Flask app).
    val balances = state.accounts.map { a ->
        a to (state.income.filter { it.account == a.name }.sumOf { it.amount } -
            state.expenses.filter { it.account == a.name }.sumOf { it.amount })
    }
    val converted = balances.map { (a, b) -> state.rates.convert(b, a.currency, state.displayCurrency) }
    val total = converted.filterNotNull().sum()
    val missing = balances.filterIndexed { i, _ -> converted[i] == null }.map { it.first.currency }.distinct()
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge)
                    Text(money(total, state.displayCurrency), style = MaterialTheme.typography.headlineSmall)
                    if (missing.isNotEmpty()) Text(
                        "No exchange rate for ${missing.joinToString()} - not included.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(balances, key = { it.first.id }) { (account, balance) ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    Text("Currency: ${account.currency}")
                    Text("Balance: ${money(balance, account.currency)}")
                }
            }
        }
    }
}

@Composable
private fun EntryList(entries: List<Entry>, emptyText: String, onDelete: (String) -> Unit, modifier: Modifier) {
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    if (entries.isEmpty()) return EmptyHint(emptyText, modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.id }) { e ->
            // Transfer legs and debt payments are owned by their parent record, so they are not deleted here.
            val deletable = e.isDirect && e.label != DEBT_PAYMENT_CATEGORY
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(e.amount, e.currency), style = MaterialTheme.typography.titleMedium)
                        Text("${e.label} • ${e.account}")
                        Text(
                            formatDate(e.date) + if (e.isDirect) "" else " • via transfer",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (deletable) TextButton(onClick = { pendingDelete = e }) { Text("Delete") }
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
private fun TransferList(transfers: List<Transfer>, modifier: Modifier) {
    if (transfers.isEmpty()) return EmptyHint("No transfers yet", modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(transfers, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${money(t.amount, t.currency)} → ${money(t.toAmount, t.toCurrency)}", style = MaterialTheme.typography.titleMedium)
                    Text("${t.from} → ${t.to}")
                    Text(t.category)
                    val rate = if (t.currency != t.toCurrency) " • 1 ${t.currency} = ${"%g".format(t.rate)} ${t.toCurrency}" else ""
                    Text(formatDate(t.date) + rate, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DebtList(
    debts: List<Debt>, accounts: List<Account>,
    onPay: (Debt, Double, Long, Account?) -> Unit, modifier: Modifier,
) {
    var paying by remember { mutableStateOf<Debt?>(null) }
    if (debts.isEmpty()) return EmptyHint("No debts tracked yet.", modifier)
    LazyColumn(modifier, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(debts, key = { it.debtId }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    if (d.description.isNotBlank()) Text(d.description, style = MaterialTheme.typography.bodySmall)
                    LabelValue("Amount", money(d.amount, d.currency))
                    LabelValue("Paid", money(d.paid, d.currency))
                    LabelValue("Outstanding", money(d.outstanding.coerceAtLeast(0.0), d.currency))
                    LabelValue("Status", if (d.closed) "Closed" else "Open")
                    if (!d.closed) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { paying = d }) { Text("Add payment") }
                    }
                }
            }
        }
    }
    paying?.let { d ->
        DebtPaymentDialog(d, accounts, onDismiss = { paying = null }) { amt, date, from -> onPay(d, amt, date, from); paying = null }
    }
}

@Composable
private fun DebtPaymentDialog(debt: Debt, accounts: List<Account>, onDismiss: () -> Unit, onSave: (Double, Long, Account?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<Account?>(null) }
    var date by remember { mutableStateOf(todayMillis()) }
    val amt = parseAmount(text)
    FormDialog(
        "Payment to ${debt.name}", confirmEnabled = amt != null && amt > 0 && amt <= debt.outstanding,
        onDismiss = onDismiss, onConfirm = { onSave(amt!!, date, from) },
    ) {
        AmountField("Amount (${debt.currency}, up to ${"%,.2f".format(debt.outstanding)})", text) { text = it }
        FieldGap()
        Dropdown("Paid from account (optional)", listOf<Account?>(null) + accounts, from, { it?.let { a -> "${a.name} (${a.currency})" } ?: "No account" }) { from = it }
        FieldGap()
        DateField(date) { date = it }
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<String?>(null) }
    FormDialog("Add Account", confirmEnabled = name.isNotBlank() && currency != null, onDismiss = onDismiss,
        onConfirm = { onSave(name.trim(), currency!!) }) {
        androidx.compose.material3.OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Account name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FieldGap()
        Dropdown("Currency", CURRENCIES, currency, { it }) { currency = it }
    }
}

@Composable
private fun AddEntryDialog(
    title: String, labelName: String, options: List<String>, accounts: List<Account>,
    onDismiss: () -> Unit, onSave: (Account, Double, Long, String) -> Unit,
) {
    var account by remember { mutableStateOf<Account?>(null) }
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(todayMillis()) }
    val amt = parseAmount(amount)
    FormDialog(title, confirmEnabled = account != null && amt != null && amt > 0 && label != null, onDismiss = onDismiss,
        onConfirm = { onSave(account!!, amt!!, date, label!!) }) {
        if (accounts.isEmpty()) Text("Add an account first.")
        Dropdown("Account", accounts, account, { "${it.name} (${it.currency})" }) { account = it }
        FieldGap()
        AmountField("Amount${account?.let { " (${it.currency})" } ?: ""}", amount) { amount = it }
        FieldGap()
        Dropdown(labelName, options, label, { it }) { label = it }
        FieldGap()
        DateField(date) { date = it }
    }
}

@Composable
private fun AddTransferDialog(
    accounts: List<Account>, rates: Rates, onDismiss: () -> Unit,
    onSave: (Account, Account, Double, String, Long, Double) -> Unit,
) {
    var from by remember { mutableStateOf<Account?>(null) }
    var to by remember { mutableStateOf<Account?>(null) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(todayMillis()) }
    var rateText by remember { mutableStateOf("") }
    val amt = parseAmount(amount)
    val sameCurrency = from != null && to != null && from!!.currency == to!!.currency
    val known = if (from != null && to != null) rates.rate(from!!.currency, to!!.currency) else null
    // A rate typed here wins over the stored one, as on the Flask form.
    val rate = if (sameCurrency) 1.0 else parseAmount(rateText)?.takeIf { it > 0 } ?: known
    val differentAccounts = from != null && to != null && from!!.id != to!!.id
    FormDialog(
        "Add Transfer",
        confirmEnabled = differentAccounts && amt != null && amt > 0 && category != null && rate != null,
        onDismiss = onDismiss, onConfirm = { onSave(from!!, to!!, amt!!, category!!, date, rate!!) },
    ) {
        if (accounts.size < 2) Text("Add at least two accounts first.")
        Dropdown("From account", accounts, from, { "${it.name} (${it.currency})" }) { from = it }
        FieldGap()
        Dropdown("To account", accounts, to, { "${it.name} (${it.currency})" }) { to = it }
        FieldGap()
        AmountField("Amount${from?.let { " (${it.currency})" } ?: ""}", amount) { amount = it }
        if (from != null && to != null && !sameCurrency) {
            FieldGap()
            AmountField("Rate: 1 ${from!!.currency} = ? ${to!!.currency}${known?.let { " (stored ${"%g".format(it)})" } ?: ""}", rateText) { rateText = it }
            if (rate != null && amt != null) Text("Receives ${money(Math.round(amt * rate * 100) / 100.0, to!!.currency)}", style = MaterialTheme.typography.bodySmall)
        }
        FieldGap()
        Dropdown("Category", TRANSFER_CATEGORIES, category, { it }) { category = it }
        FieldGap()
        DateField(date) { date = it }
    }
}

@Composable
private fun AddDebtDialog(onDismiss: () -> Unit, onSave: (String, Double, String, String) -> Unit) {
    var who by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<String?>(null) }
    val amt = parseAmount(amount)
    FormDialog("Add Debt", confirmEnabled = who.isNotBlank() && amt != null && amt > 0 && currency != null,
        onDismiss = onDismiss, onConfirm = { onSave(who.trim(), amt!!, description.trim(), currency!!) }) {
        androidx.compose.material3.OutlinedTextField(
            value = who, onValueChange = { who = it }, label = { Text("To whom I owe") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FieldGap()
        AmountField("Amount", amount) { amount = it }
        FieldGap()
        androidx.compose.material3.OutlinedTextField(
            value = description, onValueChange = { description = it }, label = { Text("Description") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FieldGap()
        Dropdown("Currency", CURRENCIES, currency, { it }) { currency = it }
    }
}
