package com.household.finance.data

enum class EntryType { INCOME, EXPENSE, SAVINGS }

/** Stable doc id for a profile's standing salary entry, so Settings can both write and look it up. */
fun salaryEntryId(person: String): String =
    "salary_" + person.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_")

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
    val toAccountName: String? = null,
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
        "toAccountName" to toAccountName,
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
            toAccountName = map["toAccountName"] as? String,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class Account(
    val name: String = "",
    val balance: Double = 0.0,
    /** Profile that owns this account; blank means "existed before ownership was tracked" - visible to everyone. */
    val owner: String = "",
    /** Who last touched this balance (edit or a tagged entry/loan), for a lightweight audit trail. */
    val lastEditedBy: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name, "balance" to balance, "owner" to owner, "lastEditedBy" to lastEditedBy, "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Account = Account(
            name = map["name"] as? String ?: id,
            balance = (map["balance"] as? Number)?.toDouble() ?: 0.0,
            owner = map["owner"] as? String ?: "",
            lastEditedBy = map["lastEditedBy"] as? String ?: "",
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class EmergencyFund(
    val currentAmount: Double = 0.0,
    /** Blank means the shared/joint fund (legacy default); a profile name means that profile's personal fund. */
    val owner: String = ""
)

data class Goal(
    val id: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    /** Legacy fixed months-at-creation, kept for goals saved before target dates were tracked. */
    val targetMonths: Int = 1,
    /** Legacy fixed monthly figure computed at creation; prefer the live recompute in Calculations. */
    val monthlyContribution: Double = 0.0,
    val savedSoFar: Double = 0.0,
    val completed: Boolean = false,
    /** Actual calendar target, if known - lets "months remaining" count down instead of staying fixed. */
    val targetMonth: Int? = null,
    val targetYear: Int? = null,
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
        "targetMonth" to targetMonth,
        "targetYear" to targetYear,
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
            targetMonth = (map["targetMonth"] as? Number)?.toInt(),
            targetYear = (map["targetYear"] as? Number)?.toInt(),
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
    /** Which of the lender's accounts the money actually left from, if mentioned - debited when the
     *  loan is created, credited back when it's settled, so balances stay honest either way. */
    val accountName: String? = null,
    /** Optional "settle by" date as an ISO string (yyyy-MM-dd), for a due-date reminder. */
    val dueDate: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "lender" to lender,
        "borrower" to borrower,
        "amount" to amount,
        "note" to note,
        "settled" to settled,
        "accountName" to accountName,
        "dueDate" to dueDate,
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
            accountName = map["accountName"] as? String,
            dueDate = map["dueDate"] as? String,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

enum class BillType { EMI, CREDIT_CARD, OTHER }

/**
 * A recurring external payable (EMI, credit card due, or any other bill) with a due date and,
 * optionally, which account it gets debited from when marked paid. Distinct from [Loan], which
 * is peer-to-peer between the two profiles - this is money owed to someone outside the household.
 */
data class Bill(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    /** Next due date, ISO yyyy-MM-dd. */
    val dueDate: String = "",
    val accountName: String? = null,
    /** If set, "Mark Paid" TRANSFERS the amount into this account instead of just debiting
     *  [accountName] - a sinking fund, e.g. setting aside a yearly premium's monthly share
     *  into a separate savings account ahead of the real due date. */
    val toAccountName: String? = null,
    val type: BillType = BillType.OTHER,
    /** If true, marking paid advances dueDate by a month instead of deleting the bill. */
    val recurring: Boolean = true,
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "amount" to amount,
        "dueDate" to dueDate,
        "accountName" to accountName,
        "toAccountName" to toAccountName,
        "type" to type.name,
        "recurring" to recurring,
        "owner" to owner,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Bill = Bill(
            id = id,
            name = map["name"] as? String ?: "",
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            dueDate = map["dueDate"] as? String ?: "",
            accountName = map["accountName"] as? String,
            toAccountName = map["toAccountName"] as? String,
            type = runCatching { BillType.valueOf(map["type"] as String) }.getOrDefault(BillType.OTHER),
            recurring = map["recurring"] as? Boolean ?: true,
            owner = map["owner"] as? String ?: "",
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
    val salaryAmount: Double? = null,
    /** This profile's default account - the "from" side of a chat-based transfer when none is named. */
    val defaultAccountName: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name, "pin" to pin, "salaryCreditDate" to salaryCreditDate,
        "salaryAmount" to salaryAmount,
        "defaultAccountName" to defaultAccountName, "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Profile = Profile(
            name = map["name"] as? String ?: id,
            pin = map["pin"] as? String ?: "",
            salaryCreditDate = (map["salaryCreditDate"] as? Number)?.toInt(),
            salaryAmount = (map["salaryAmount"] as? Number)?.toDouble(),
            defaultAccountName = map["defaultAccountName"] as? String,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

enum class CategoryListLength { SHORT, MEDIUM, LONG }

val SHORT_CATEGORIES = listOf(
    "EMI", "Home Expenses", "Groceries", "Eating Out", "Utilities", "LIC", "SIP", "Other"
)

val MEDIUM_CATEGORIES = listOf(
    "EMI", "Health Insurance", "Car Insurance", "LIC", "Music Classes",
    "RD", "FD", "PPF", "SIP", "Home Expenses", "Groceries", "Eating Out", "Utilities", "Other"
)

val LONG_CATEGORIES = listOf(
    "EMI", "Rent", "Health Insurance", "Life Insurance", "Car Insurance", "Home Insurance",
    "LIC", "Music Classes", "Tuition/School Fees", "RD", "FD", "PPF", "SIP", "Mutual Funds",
    "Stocks", "Gold", "Home Expenses", "Groceries", "Eating Out", "Utilities", "Mobile/Internet", "Fuel",
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
    "Subscriptions", "Home Expenses"
)

data class ActiveLoan(
    val id: String = "",
    val name: String = "",
    val principal: Double = 0.0,
    val interestRate: Double = 0.0,
    val totalTenureMonths: Int = 120,
    val remainingTenureMonths: Int = 120,
    val monthlyEmi: Double = 0.0,
    val extraPrepayment: Double = 0.0,
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "principal" to principal,
        "interestRate" to interestRate,
        "totalTenureMonths" to totalTenureMonths,
        "remainingTenureMonths" to remainingTenureMonths,
        "monthlyEmi" to monthlyEmi,
        "extraPrepayment" to extraPrepayment,
        "owner" to owner,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ActiveLoan = ActiveLoan(
            id = id,
            name = map["name"] as? String ?: "",
            principal = (map["principal"] as? Number)?.toDouble() ?: 0.0,
            interestRate = (map["interestRate"] as? Number)?.toDouble() ?: 0.0,
            totalTenureMonths = (map["totalTenureMonths"] as? Number)?.toInt() ?: 120,
            remainingTenureMonths = (map["remainingTenureMonths"] as? Number)?.toInt() ?: 120,
            monthlyEmi = (map["monthlyEmi"] as? Number)?.toDouble() ?: 0.0,
            extraPrepayment = (map["extraPrepayment"] as? Number)?.toDouble() ?: 0.0,
            owner = map["owner"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
