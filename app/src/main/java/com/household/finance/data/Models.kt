package com.household.finance.data

enum class EntryType { INCOME, EXPENSE, SAVINGS }

// Whose personal bucket an entry belongs to is carried by `Entry.person`, not by the enum -
// a single PERSONAL value keeps the schema symmetric across both partners' devices.
enum class Bucket { JOINT, PERSONAL }

enum class Frequency { MONTHLY, ANNUAL }

enum class PolicyStatus { ACTIVE, PAID_UP, NEAR_MATURITY }

data class Entry(
    val id: String = "",
    val person: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val bucket: Bucket = Bucket.PERSONAL,
    val category: String = "",
    val amount: Double = 0.0,
    val frequency: Frequency = Frequency.MONTHLY,
    val note: String = "",
    val maturityYear: Int? = null,
    val accountName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Amount normalized to a per-month figure, annual entries divided by 12. */
    val monthlyAmount: Double
        get() = if (frequency == Frequency.ANNUAL) amount / 12.0 else amount

    /** Signed impact on a tagged account's balance: money out for expense/savings, in for income. */
    val signedAccountAmount: Double
        get() = if (type == EntryType.INCOME) amount else -amount

    fun toMap(): Map<String, Any?> = mapOf(
        "person" to person,
        "type" to type.name,
        "bucket" to bucket.name,
        "category" to category,
        "amount" to amount,
        "frequency" to frequency.name,
        "note" to note,
        "maturityYear" to maturityYear,
        "accountName" to accountName,
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
            accountName = map["accountName"] as? String,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class Account(
    val name: String = "",
    val balance: Double = 0.0,
    /** Profile that owns this account; blank means "existed before ownership was tracked" - visible to everyone. */
    val owner: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf("name" to name, "balance" to balance, "owner" to owner, "updatedAt" to updatedAt)

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Account = Account(
            name = map["name"] as? String ?: id,
            balance = (map["balance"] as? Number)?.toDouble() ?: 0.0,
            owner = map["owner"] as? String ?: "",
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
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
    val completed: Boolean = false,
    /** Profile that created this goal; blank means "existed before ownership was tracked" - visible to everyone. */
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "targetAmount" to targetAmount,
        "targetMonths" to targetMonths,
        "monthlyContribution" to monthlyContribution,
        "savedSoFar" to savedSoFar,
        "completed" to completed,
        "owner" to owner,
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
            completed = map["completed"] as? Boolean ?: false,
            owner = map["owner"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

/** A peer-to-peer loan between the two profiles - lender's dashboard shows a receivable, borrower's shows a payable. */
data class Loan(
    val id: String = "",
    val lender: String = "",
    val borrower: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val settled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "lender" to lender,
        "borrower" to borrower,
        "amount" to amount,
        "note" to note,
        "settled" to settled,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Loan = Loan(
            id = id,
            lender = map["lender"] as? String ?: "",
            borrower = map["borrower"] as? String ?: "",
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            note = map["note"] as? String ?: "",
            settled = map["settled"] as? Boolean ?: false,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

/** One of the two household profiles. PIN is shared via Firestore so either phone can switch to either profile. */
data class Profile(
    val name: String = "",
    val pin: String = "",
    /** Day of month (1-31) this profile's salary is credited, or null if not set. */
    val salaryCreditDate: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name, "pin" to pin, "salaryCreditDate" to salaryCreditDate, "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Profile = Profile(
            name = map["name"] as? String ?: id,
            pin = map["pin"] as? String ?: "",
            salaryCreditDate = (map["salaryCreditDate"] as? Number)?.toInt(),
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
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

/** Fixed/recurring commitments — shown separately on the dashboard with monthly + annualized totals. */
val RECURRING_CATEGORIES = setOf(
    "EMI", "Rent", "Health Insurance", "Life Insurance", "Car Insurance", "Home Insurance",
    "LIC", "Music Classes", "Tuition/School Fees", "RD", "FD", "PPF", "SIP", "Mutual Funds",
    "Subscriptions"
)
