package com.household.finance.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Data-layer contract. Swap the implementation without touching UI code. */
interface FinanceRepository {
    fun observeEntries(): Flow<List<Entry>>
    /** Forces a one-time server round-trip so the live listener's local cache is guaranteed current. */
    suspend fun refreshEntries()
    suspend fun addEntry(entry: Entry)
    suspend fun deleteEntry(id: String)
    fun observeEmergencyFund(): Flow<EmergencyFund>
    suspend fun setEmergencyFund(amount: Double)
    fun observeGoals(): Flow<List<Goal>>
    suspend fun addGoal(goal: Goal)
    suspend fun deleteGoal(id: String)
    fun observeAccounts(): Flow<List<Account>>
    /** Absolute overwrite — used for manual edits and explicit "X balance is Y" statements. Never touches owner. */
    suspend fun setAccountBalance(name: String, balance: Double)
    /** Atomic +/- applied when a transaction is tagged with an account. If the account doesn't exist yet,
     *  creates it at 0 first, tagged to [owner] - existing accounts keep whoever originally owned them. */
    suspend fun adjustAccountBalance(name: String, delta: Double, owner: String)
    suspend fun setGoalCompleted(id: String, completed: Boolean)
    fun observeLoans(): Flow<List<Loan>>
    suspend fun addLoan(loan: Loan)
    suspend fun setLoanSettled(id: String, settled: Boolean)
    suspend fun deleteLoan(id: String)
    /** Shared across both phones so either can switch to either profile with the same PIN. */
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun saveProfile(profile: Profile)
    suspend fun setProfileSalaryDate(name: String, day: Int?)
    fun isReady(): Boolean
}

private const val WORKSPACE_ID = "household" // single shared workspace, both users write here
private const val FIREBASE_APP_NAME = "household_finance"

/**
 * Firestore-backed implementation. Initialized dynamically from config the user
 * enters in Settings, so no google-services.json / build-time secret is needed.
 */
class FirestoreFinanceRepository(private val context: Context) : FinanceRepository {

    private var firestore: FirebaseFirestore? = null

    fun configure(config: AppSettings.FirebaseConfig) {
        if (!config.isComplete) {
            firestore = null
            return
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(config.apiKey)
            .setApplicationId(config.appId)
            .setProjectId(config.projectId)
            .apply {
                if (config.storageBucket.isNotBlank()) setStorageBucket(config.storageBucket)
                if (config.messagingSenderId.isNotBlank()) setGcmSenderId(config.messagingSenderId)
            }
            .build()

        val app = FirebaseApp.getApps(context).find { it.name == FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)

        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        firestore = db
    }

    override fun isReady(): Boolean = firestore != null

    private fun entriesCollection() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("entries")

    private fun metaDoc() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("meta")?.document("emergencyFund")

    private fun goalsCollection() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("goals")

    private fun accountsCollection() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("accounts")

    private fun loansCollection() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("loans")

    private fun profilesCollection() =
        firestore?.collection("workspaces")?.document(WORKSPACE_ID)?.collection("profiles")

    /** Normalizes an account name to a stable doc id so "icici"/"ICICI"/"Icici" all resolve to one account. */
    private fun accountDocId(name: String) =
        name.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifBlank { "ACCOUNT" }

    override fun observeEntries(): Flow<List<Entry>> = callbackFlow {
        val col = entriesCollection()
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = col.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Entry.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(list.sortedByDescending { it.createdAt })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun refreshEntries() {
        val col = entriesCollection() ?: return
        suspendCancellableCoroutine<Unit> { cont ->
            col.get(Source.SERVER)
                .addOnCompleteListener { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
        }
    }

    override suspend fun addEntry(entry: Entry) {
        val col = entriesCollection() ?: return
        val docRef = if (entry.id.isBlank()) col.document() else col.document(entry.id)
        docRef.set(entry.toMap()).let { }
    }

    override suspend fun deleteEntry(id: String) {
        entriesCollection()?.document(id)?.delete()
    }

    override fun observeEmergencyFund(): Flow<EmergencyFund> = callbackFlow {
        val doc = metaDoc()
        if (doc == null) {
            trySend(EmergencyFund())
            awaitClose { }
            return@callbackFlow
        }
        val registration = doc.addSnapshotListener { snapshot, _ ->
            val amount = (snapshot?.getDouble("currentAmount")) ?: 0.0
            trySend(EmergencyFund(amount))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setEmergencyFund(amount: Double) {
        metaDoc()?.set(mapOf("currentAmount" to amount))
    }

    override fun observeGoals(): Flow<List<Goal>> = callbackFlow {
        val col = goalsCollection()
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = col.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Goal.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(list.sortedByDescending { it.createdAt })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addGoal(goal: Goal) {
        val col = goalsCollection() ?: return
        val docRef = if (goal.id.isBlank()) col.document() else col.document(goal.id)
        docRef.set(goal.toMap())
    }

    override suspend fun deleteGoal(id: String) {
        goalsCollection()?.document(id)?.delete()
    }

    override suspend fun setGoalCompleted(id: String, completed: Boolean) {
        goalsCollection()?.document(id)?.set(mapOf("completed" to completed), SetOptions.merge())
    }

    override fun observeLoans(): Flow<List<Loan>> = callbackFlow {
        val col = loansCollection()
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = col.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Loan.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(list.sortedByDescending { it.createdAt })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addLoan(loan: Loan) {
        val col = loansCollection() ?: return
        col.document().set(loan.toMap())
    }

    override suspend fun setLoanSettled(id: String, settled: Boolean) {
        loansCollection()?.document(id)?.set(mapOf("settled" to settled), SetOptions.merge())
    }

    override suspend fun deleteLoan(id: String) {
        loansCollection()?.document(id)?.delete()
    }

    override fun observeProfiles(): Flow<List<Profile>> = callbackFlow {
        val col = profilesCollection()
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = col.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Profile.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(list.sortedBy { it.name })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveProfile(profile: Profile) {
        val col = profilesCollection() ?: return
        col.document(profile.name.trim().uppercase()).set(profile.toMap(), SetOptions.merge())
    }

    override suspend fun setProfileSalaryDate(name: String, day: Int?) {
        val col = profilesCollection() ?: return
        col.document(name.trim().uppercase()).set(mapOf("salaryCreditDate" to day), SetOptions.merge())
    }

    override fun observeAccounts(): Flow<List<Account>> = callbackFlow {
        val col = accountsCollection()
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = col.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { Account.fromMap(doc.id, it) }
            } ?: emptyList()
            trySend(list.sortedBy { it.name })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setAccountBalance(name: String, balance: Double) {
        val col = accountsCollection() ?: return
        col.document(accountDocId(name)).set(
            mapOf("name" to name.trim().uppercase(), "balance" to balance, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    override suspend fun adjustAccountBalance(name: String, delta: Double, owner: String) {
        val db = firestore ?: return
        val col = accountsCollection() ?: return
        val docRef = col.document(accountDocId(name))
        suspendCancellableCoroutine<Unit> { cont ->
            db.runTransaction { txn ->
                val snapshot = txn.get(docRef)
                if (!snapshot.exists()) {
                    txn.set(
                        docRef,
                        mapOf(
                            "name" to name.trim().uppercase(),
                            "balance" to delta,
                            "owner" to owner,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                } else {
                    txn.update(docRef, mapOf("balance" to FieldValue.increment(delta), "updatedAt" to System.currentTimeMillis()))
                }
            }.addOnCompleteListener { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
        }
    }
}
