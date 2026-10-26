package com.example.jacaccounts.data

import com.example.jacaccounts.BuildConfig
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.MongoClient
import com.mongodb.kotlin.client.MongoCollection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

/**
 * All access is confined to the jac_accounts database, whose collections mirror the Flask app's SQLite
 * tables (currency-dynamic branch). Amounts and dates are stored there as strings ("12.5", "2026-08-20"),
 * each document carries the SQLite integer `id`, and accounts are referenced by name.
 * Calls are blocking, so they run on IO.
 */
class MongoRepository {
    private val client: MongoClient by lazy {
        MongoClient.create(
            MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(BuildConfig.MONGO_URI))
                .applyToClusterSettings { it.serverSelectionTimeout(8, TimeUnit.SECONDS) }
                .applyToSocketSettings { it.connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS) }
                .build()
        )
    }
    private val db get() = client.getDatabase(BuildConfig.MONGO_DB)
    private fun col(name: String): MongoCollection<Document> = db.getCollection(name)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---- helpers ----
    private fun Document.text(key: String) = get(key)?.toString().orEmpty()
    private fun Document.number(key: String) = get(key)?.toString()?.toDoubleOrNull() ?: 0.0
    private fun Document.hexId() = getObjectId("_id").toHexString()

    private fun parseDate(s: String): Long = runCatching {
        LocalDate.parse(s.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** SQLite-style AUTOINCREMENT: one past the highest `id` in the collection. */
    private fun nextId(c: MongoCollection<Document>): Int =
        ((c.find().sort(Sorts.descending("id")).limit(1).firstOrNull()?.get("id") as? Number)?.toInt() ?: 0) + 1

    // ---- Accounts (banker) ----
    suspend fun listAccounts(): List<Account> = io {
        col("banker").find().sort(Sorts.ascending("id")).map {
            Account(it.hexId(), it.text("account_name"), normaliseCurrency(it.text("currency")))
        }.toList()
    }

    /** Names are unique (case-insensitively) because transactions join on them. Returns an error or null. */
    suspend fun addAccount(name: String, currency: String): String? = io {
        val c = col("banker")
        if (c.find().toList().any { it.text("account_name").equals(name, ignoreCase = true) })
            return@io "An account named $name already exists."
        c.insertOne(Document("id", nextId(c)).append("account_name", name).append("currency", currency))
        null
    }

    // ---- Expenses / income ----
    private fun entries(name: String, accountKey: String): List<Entry> =
        col(name).find().map {
            Entry(
                id = it.hexId(),
                account = it.text(accountKey),
                currency = normaliseCurrency(it.text("currency")),
                amount = it.number("amount"),
                label = it.text("category"),
                date = parseDate(it.text("date")),
                source = it.text("source").ifBlank { "direct" },
            )
        }.toList().sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id })

    private fun insertEntry(
        name: String, accountKey: String, account: String, amount: Double, category: String,
        date: Long, currency: String, source: String,
    ) {
        val c = col(name)
        c.insertOne(
            Document("id", nextId(c)).append("amount", amount.toString()).append(accountKey, account)
                .append("category", category).append("date", formatDate(date))
                .append("currency", currency).append("source", source)
        )
    }

    suspend fun listExpenses() = io { entries("expense", "from") }
    suspend fun listIncome() = io { entries("income", "to") }

    suspend fun addExpense(account: Account, amount: Double, date: Long, category: String) = io {
        insertEntry("expense", "from", account.name, amount, category, date, account.currency, "direct")
    }

    suspend fun addIncome(account: Account, amount: Double, date: Long, category: String) = io {
        insertEntry("income", "to", account.name, amount, category, date, account.currency, "direct")
    }

    suspend fun deleteExpense(id: String) = io { col("expense").deleteOne(Filters.eq("_id", ObjectId(id))); Unit }
    suspend fun deleteIncome(id: String) = io { col("income").deleteOne(Filters.eq("_id", ObjectId(id))); Unit }

    // ---- Transfers ----
    suspend fun listTransfers(): List<Transfer> = io {
        col("transfer").find().map {
            Transfer(
                id = it.hexId(), from = it.text("from"), to = it.text("to"), category = it.text("category"),
                date = parseDate(it.text("date")), amount = it.number("amount"),
                currency = normaliseCurrency(it.text("currency")),
                toAmount = it.number("to_amount"), toCurrency = normaliseCurrency(it.text("to_currency")),
                rate = it.number("rate").takeIf { r -> r > 0 } ?: 1.0,
            )
        }.toList().sortedWith(compareByDescending<Transfer> { it.date }.thenByDescending { it.id })
    }

    /** Records the transfer and the two legs Flask books with it (expense in sender's, income in receiver's currency). */
    suspend fun addTransfer(from: Account, to: Account, amount: Double, category: String, date: Long, rate: Double) = io {
        val toAmount = Math.round(amount * rate * 100) / 100.0
        val c = col("transfer")
        c.insertOne(
            Document("id", nextId(c)).append("amount", amount.toString()).append("from", from.name)
                .append("to", to.name).append("category", category).append("date", formatDate(date))
                .append("currency", from.currency).append("to_amount", toAmount.toString())
                .append("to_currency", to.currency).append("rate", rate)
        )
        insertEntry("income", "to", to.name, toAmount, category, date, to.currency, "transfer")
        insertEntry("expense", "from", from.name, amount, category, date, from.currency, "transfer")
    }

    // ---- Debts ----
    suspend fun listDebts(): List<Debt> = io {
        val paid = col("debt_payment").find().toList()
            .groupBy { (it.get("debt_id") as? Number)?.toInt() }
            .mapValues { (_, v) -> v.sumOf { it.number("amount") } }
        col("debt").find().sort(Sorts.descending("id")).map {
            val id = (it.get("id") as? Number)?.toInt() ?: -1
            Debt(
                debtId = id, name = it.text("name"), description = it.text("description"),
                currency = normaliseCurrency(it.text("currency")), amount = it.number("amount"),
                paid = Math.round((paid[id] ?: 0.0) * 100) / 100.0,
            )
        }.toList()
    }

    suspend fun addDebt(name: String, amount: Double, description: String, currency: String) = io {
        val c = col("debt")
        c.insertOne(
            Document("id", nextId(c)).append("name", name).append("amount", amount.toString())
                .append("description", description).append("currency", currency)
        )
        Unit
    }

    /**
     * Records a payment against [debt]. With [from], the payment is also booked as a "Debt Payment" expense of
     * [spent] (the payment converted into the account's currency), as the Flask app does.
     */
    suspend fun addDebtPayment(debt: Debt, amount: Double, date: Long, from: Account?, spent: Double?) = io {
        if (from != null && spent != null)
            insertEntry("expense", "from", from.name, spent, DEBT_PAYMENT_CATEGORY, date, from.currency, "direct")
        val c = col("debt_payment")
        c.insertOne(
            Document("id", nextId(c)).append("debt_id", debt.debtId).append("amount", amount.toString())
                .append("currency", debt.currency).append("date", formatDate(date))
                .append("from_account", from?.name.orEmpty())
        )
        Unit
    }

    // ---- Exchange rates & settings (read-only here; edited in the Flask app) ----
    suspend fun listRates(): List<ExchangeRate> = io {
        col("exchange_rate").find().map {
            ExchangeRate(it.text("base_currency"), it.text("quote_currency"), it.number("rate"), it.text("date"))
        }.toList()
    }

    suspend fun displayCurrency(): String = io {
        normaliseCurrency(col("app_settings").find(Filters.eq("key", "display_currency")).firstOrNull()?.text("value"))
    }
}
