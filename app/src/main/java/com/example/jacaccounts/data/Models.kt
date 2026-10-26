package com.example.jacaccounts.data

val CURRENCIES = listOf("AUD", "INR")
val EXPENSE_CATEGORIES = listOf("Rent", "Shopping", "Entertainment", "Health", "Food", "Maintainance", "Bill", "Credit Payment")
val INCOME_SOURCES = listOf("Salary", "Gift", "Base Amount", "Investment")

/** Today as UTC-midnight epoch millis. */
fun todayMillis(): Long = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

data class Bank(val id: String, val name: String, val currency: String)

/** Shared shape for expenses and income; [label] is the category or source. */
data class Entry(
    val id: String,
    val bankId: String,
    val bankName: String,
    val currency: String,
    val amount: Double,
    val label: String,
    /** Transaction date as UTC-midnight epoch millis (what Material's DatePicker produces). */
    val date: Long,
    val createdAt: Long,
)

data class Credit(
    val id: String,
    val creditor: String,
    val amount: Double,
    val paid: Double,
    val createdAt: Long,
) {
    val balance: Double get() = amount - paid
}
