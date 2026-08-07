package com.household.finance.data

enum class EntryType { INCOME, EXPENSE, SAVINGS }

enum class Bucket { JOINT, PERSONAL_ME, PERSONAL_WIFE }

enum class Frequency { MONTHLY, ANNUAL }

enum class PolicyStatus { ACTIVE, PAID_UP, NEAR_MATURITY }

data class Entry(
    val id: String = "",
    val person: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val bucket: Bucket = Bucket.JOINT,
    val category: String = "",
    val amount: Double = 0.0,
    val frequency: Frequency = Frequency.MONTHLY,
    val note: String = "",
    val maturityYear: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Amount normalized to a per-month figure, annual entries divided by 12. */
    val monthlyAmount: Double
        get() = if (frequency == Frequency.ANNUAL) amount / 12.0 else amount

    fun toMap(): Map<String, Any?> = mapOf(
        "person" to person,
        "type" to type.name,
        "bucket" to bucket.name,
        "category" to category,
        "amount" to amount,
        "frequency" to frequency.name,
        "note" to note,
        "maturityYear" to maturityYear,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Entry = Entry(
            id = id,
            person = map["person"] as? String ?: "",
            type = runCatching { EntryType.valueOf(map["type"] as String) }.getOrDefault(EntryType.EXPENSE),
            bucket = runCatching { Bucket.valueOf(map["bucket"] as String) }.getOrDefault(Bucket.JOINT),
            category = map["category"] as? String ?: "",
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            frequency = runCatching { Frequency.valueOf(map["frequency"] as String) }.getOrDefault(Frequency.MONTHLY),
            note = map["note"] as? String ?: "",
            maturityYear = (map["maturityYear"] as? Number)?.toInt(),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class EmergencyFund(
    val currentAmount: Double = 0.0
)

data class Goal(
    val id: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val targetMonths: Int = 1,
    val monthlyContribution: Double = 0.0,
    val savedSoFar: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "targetAmount" to targetAmount,
        "targetMonths" to targetMonths,
        "monthlyContribution" to monthlyContribution,
        "savedSoFar" to savedSoFar,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Goal = Goal(
            id = id,
            title = map["title"] as? String ?: "",
            targetAmount = (map["targetAmount"] as? Number)?.toDouble() ?: 0.0,
            targetMonths = (map["targetMonths"] as? Number)?.toInt() ?: 1,
            monthlyContribution = (map["monthlyContribution"] as? Number)?.toDouble() ?: 0.0,
            savedSoFar = (map["savedSoFar"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

enum class CategoryListLength { SHORT, MEDIUM, LONG }

val SHORT_CATEGORIES = listOf(
    "EMI", "Groceries", "Eating Out", "Utilities", "LIC", "SIP", "Other"
)

val MEDIUM_CATEGORIES = listOf(
    "EMI", "Health Insurance", "Car Insurance", "LIC", "Music Classes",
    "RD", "FD", "PPF", "SIP", "Groceries", "Eating Out", "Utilities", "Other"
)

val LONG_CATEGORIES = listOf(
    "EMI", "Rent", "Health Insurance", "Life Insurance", "Car Insurance", "Home Insurance",
    "LIC", "Music Classes", "Tuition/School Fees", "RD", "FD", "PPF", "SIP", "Mutual Funds",
    "Stocks", "Gold", "Groceries", "Eating Out", "Utilities", "Mobile/Internet", "Fuel",
    "Transport", "Travel", "Medical", "Shopping", "Subscriptions", "Gifts", "Donations",
    "Home Maintenance", "Salary", "Bonus", "Other"
)

fun categoriesFor(length: CategoryListLength): List<String> = when (length) {
    CategoryListLength.SHORT -> SHORT_CATEGORIES
    CategoryListLength.MEDIUM -> MEDIUM_CATEGORIES
    CategoryListLength.LONG -> LONG_CATEGORIES
}

/** Kept for any lingering references; prefer [categoriesFor]. */
val DEFAULT_CATEGORIES = MEDIUM_CATEGORIES

val INVESTMENT_CATEGORIES = setOf("LIC", "RD", "FD", "PPF", "SIP", "Mutual Funds", "Stocks", "Gold")
