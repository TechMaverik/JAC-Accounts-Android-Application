package com.example.jacaccounts.data

// Mirrors menus/currencies.py and menus/categories.py of the Flask app (currency-dynamic branch).
// AUD first: it is the default in the currency chips and dropdowns. Missing values still fall back to DEFAULT_CURRENCY, as in Flask.
val CURRENCIES = listOf("AUD", "INR")
const val DEFAULT_CURRENCY = "INR"
const val DEBT_PAYMENT_CATEGORY = "💳Debt Payment"

val EXPENSE_CATEGORIES = listOf(
    "🍔Food", "🛒Shopping", "🚗Transport", "🏠Rent", "🎉Entertainment", "💊Health", "📚Education", "🎁Gifts",
    "💡Utilities", "🧳Travel", "📱Bills", "⚙️Maintenance", "🧾Taxes", DEBT_PAYMENT_CATEGORY, "🐾Pet Care",
    "🌿Personal Care", "👗Clothing", "🍿Subscriptions", "🧸Kids", "🎄Holidays", "🛠️Repairs", "🚿Hygiene",
)
val INCOME_SOURCES = listOf("💼Salary", "🎁Gifts", "💰Freelance", "💼Initial Balance")
val TRANSFER_CATEGORIES = listOf(
    "📈Stocks", "🏠Real Estate", "💼Mutual Funds", "💎Precious Metals", "🪙Cryptocurrency",
    "🏦Bank Transfer", "💸Cash Withdrawal",
)

fun normaliseCurrency(code: String?): String =
    code?.trim()?.uppercase()?.takeIf { it in CURRENCIES } ?: DEFAULT_CURRENCY

/** Today as UTC-midnight epoch millis. */
fun todayMillis(): Long = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

/** A row of the `banker` collection. Transactions refer to an account by [name]. */
data class Account(val id: String, val name: String, val currency: String)

/** Row of `expense` or `income`; [account] is the `from` / `to` account name and [label] the category. */
data class Entry(
    val id: String,
    val account: String,
    val currency: String,
    val amount: Double,
    val label: String,
    /** Transaction date as UTC-midnight epoch millis (what Material's DatePicker produces). */
    val date: Long,
    /** "direct" for user entries, "transfer" for the legs written by a transfer. */
    val source: String,
) {
    val isDirect: Boolean get() = source == "direct"
}

data class Transfer(
    val id: String,
    val from: String,
    val to: String,
    val category: String,
    val date: Long,
    val amount: Double,
    val currency: String,
    val toAmount: Double,
    val toCurrency: String,
    val rate: Double,
)

data class Debt(
    /** The Flask integer id that `debt_payment.debt_id` points at. */
    val debtId: Int,
    val name: String,
    val description: String,
    val currency: String,
    val amount: Double,
    val paid: Double,
) {
    val outstanding: Double get() = Math.round((amount - paid) * 100) / 100.0
    val closed: Boolean get() = outstanding <= 0
}

data class ExchangeRate(val base: String, val quote: String, val rate: Double, val date: String)

/** Exchange-rate lookup that follows services.py: direct, then inverse, then one hop through a pivot. */
class Rates(all: List<ExchangeRate>) {
    private val latest: Map<Pair<String, String>, Double> =
        all.groupBy { it.base to it.quote }.mapValues { (_, v) -> v.maxBy { it.date }.rate }

    fun rate(from: String, to: String): Double? {
        if (from == to) return 1.0
        latest[from to to]?.takeIf { it > 0 }?.let { return it }
        latest[to to from]?.takeIf { it > 0 }?.let { return 1 / it }
        for (pivot in CURRENCIES) {
            if (pivot == from || pivot == to) continue
            val first = latest[from to pivot]
            val second = latest[pivot to to]
            if (first != null && second != null) return first * second
        }
        return null
    }

    fun convert(amount: Double, from: String, to: String): Double? =
        rate(from, to)?.let { Math.round(amount * it * 100) / 100.0 }
}
