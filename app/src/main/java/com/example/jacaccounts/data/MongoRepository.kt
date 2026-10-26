package com.example.jacaccounts.data

import com.example.jacaccounts.BuildConfig
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.MongoClient
import com.mongodb.kotlin.client.MongoCollection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

/** All access is confined to the jac_accounts database. Calls are blocking, so they run on IO. */
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
    private val banks: MongoCollection<Document> get() = db.getCollection("banks")
    private val expenses: MongoCollection<Document> get() = db.getCollection("expenses")
    private val incomes: MongoCollection<Document> get() = db.getCollection("income")
    private val credits: MongoCollection<Document> get() = db.getCollection("credits")

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---- Banks ----
    suspend fun listBanks(): List<Bank> = io {
        banks.find().sort(Sorts.ascending("name")).map {
            Bank(it.getObjectId("_id").toHexString(), it.getString("name"), it.getString("currency"))
        }.toList()
    }

    suspend fun addBank(name: String, currency: String) = io {
        banks.insertOne(Document("name", name).append("currency", currency).append("createdAt", now()))
        Unit
    }

    suspend fun deleteBank(id: String) = io {
        banks.deleteOne(Filters.eq("_id", ObjectId(id)))
        Unit
    }

    // ---- Expenses / Income ----
    private fun entries(col: MongoCollection<Document>, labelKey: String): List<Entry> =
        col.find().map {
            val created = (it["createdAt"] as Number).toLong()
            Entry(
                id = it.getObjectId("_id").toHexString(),
                bankId = it.getString("bankId"),
                bankName = it.getString("bankName"),
                currency = it.getString("currency"),
                amount = (it["amount"] as Number).toDouble(),
                label = it.getString(labelKey),
                // Entries saved before dates existed fall back to the day they were created.
                date = (it["date"] as? Number)?.toLong() ?: java.time.Instant.ofEpochMilli(created)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                createdAt = created,
            )
        }.toList().sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })

    private fun entryDoc(bank: Bank, amount: Double, date: Long, labelKey: String, label: String) =
        Document("bankId", bank.id).append("bankName", bank.name).append("currency", bank.currency)
            .append("amount", amount).append("date", date).append(labelKey, label).append("createdAt", now())

    suspend fun listExpenses() = io { entries(expenses, "usedFor") }
    suspend fun addExpense(bank: Bank, amount: Double, date: Long, usedFor: String) = io {
        expenses.insertOne(entryDoc(bank, amount, date, "usedFor", usedFor)); Unit
    }
    suspend fun deleteExpense(id: String) = io { expenses.deleteOne(Filters.eq("_id", ObjectId(id))); Unit }

    suspend fun listIncome() = io { entries(incomes, "source") }
    suspend fun addIncome(bank: Bank, amount: Double, date: Long, source: String) = io {
        incomes.insertOne(entryDoc(bank, amount, date, "source", source)); Unit
    }
    suspend fun deleteIncome(id: String) = io { incomes.deleteOne(Filters.eq("_id", ObjectId(id))); Unit }

    // ---- Credit tracker ----
    suspend fun listCredits(): List<Credit> = io {
        credits.find().sort(Sorts.descending("createdAt")).map {
            Credit(
                id = it.getObjectId("_id").toHexString(),
                creditor = it.getString("creditor"),
                amount = (it["amount"] as Number).toDouble(),
                paid = (it["paid"] as Number).toDouble(),
                createdAt = (it["createdAt"] as Number).toLong(),
            )
        }.toList()
    }

    suspend fun addCredit(creditor: String, amount: Double, paid: Double) = io {
        credits.insertOne(
            Document("creditor", creditor).append("amount", amount).append("paid", paid).append("createdAt", now())
        ); Unit
    }

    /** Adds [extra] to the amount already paid. */
    suspend fun addCreditPayment(id: String, extra: Double) = io {
        credits.updateOne(Filters.eq("_id", ObjectId(id)), Updates.inc("paid", extra)); Unit
    }

    suspend fun deleteCredit(id: String) = io { credits.deleteOne(Filters.eq("_id", ObjectId(id))); Unit }

    private fun now() = System.currentTimeMillis()
}
