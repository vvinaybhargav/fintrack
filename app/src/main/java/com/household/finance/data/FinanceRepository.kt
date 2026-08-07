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
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Minimal Task -> suspend bridge, avoiding a dependency on kotlinx-coroutines-play-services for just this. */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val exception = task.exception
        if (exception != null) {
            if (cont.isActive) cont.resumeWith(Result.failure(exception))
        } else {
            if (cont.isActive) cont.resumeWith(Result.success(task.result))
        }
    }
}

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
    /** Atomic +/- applied when a transaction is tagged with an account. [isNewAccount] should reflect
     *  whether the caller's last-synced account list already has this name - determines whether this
     *  writes an initial doc (tagged to [owner]) or increments an existing one (owner untouched).
     *  Deliberately avoids Firestore transactions here, which don't reliably commit offline. */
    suspend fun adjustAccountBalance(name: String, delta: Double, owner: String, isNewAccount: Boolean)
    /** Renames an account and re-tags every entry referencing it (batched). Balance/owner carry over. */
    suspend fun renameAccount(oldName: String, newName: String)
    /** Stops tracking this account. Entries that referenced it keep their historical accountName as-is. */
    suspend fun deleteAccount(name: String)
    suspend fun setGoalCompleted(id: String, completed: Boolean)
    suspend fun addGoalContribution(id: String, amount: Double)
    fun observeLoans(): Flow<List<Loan>>
    suspend fun addLoan(loan: Loan)
    suspend fun setLoanSettled(id: String, settled: Boolean)
    suspend fun deleteLoan(id: String)
    /** Shared across both phones so either can switch to either profile with the same PIN. */
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun saveProfile(profile: Profile)
    suspend fun setProfileSalaryDate(name: String, day: Int?)
    /** Renames a profile everywhere it's referenced (entries, goals, accounts, loans), then removes
     *  the old profile doc. Best-effort in batches - if interrupted partway, re-running with the
     *  same names is safe since every step only touches docs still tagged with the old name. */
    suspend fun renameProfile(oldName: String, newName: String)
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

    override suspend fun renameProfile(oldName: String, newName: String) {
        val db = firestore ?: return
        val profilesCol = profilesCollection() ?: return

        // Copy the profile doc under the new name first (keep pin + salary date), then old doc is
        // removed last - so if this whole operation is interrupted, the old name is still valid
        // and re-running the rename picks up wherever it left off.
        val oldDoc = profilesCol.document(oldName.trim().uppercase()).get().awaitTask()
        val oldProfile = oldDoc.data?.let { Profile.fromMap(oldDoc.id, it) } ?: Profile(name = oldName)
        profilesCol.document(newName.trim().uppercase()).set(oldProfile.copy(name = newName).toMap()).awaitTask()

        renameFieldInCollection(db, entriesCollection(), "person", oldName, newName)
        renameFieldInCollection(db, goalsCollection(), "owner", oldName, newName)
        renameFieldInCollection(db, accountsCollection(), "owner", oldName, newName)
        renameFieldInCollection(db, loansCollection(), "lender", oldName, newName)
        renameFieldInCollection(db, loansCollection(), "borrower", oldName, newName)

        profilesCol.document(oldName.trim().uppercase()).delete().awaitTask()
    }

    /** Batched find-and-replace of a single string field across every doc in a collection that has it set to [oldValue]. */
    private suspend fun renameFieldInCollection(
        db: FirebaseFirestore,
        collection: com.google.firebase.firestore.CollectionReference?,
        field: String,
        oldValue: String,
        newValue: String
    ) {
        val col = collection ?: return
        val matches = col.whereEqualTo(field, oldValue).get().awaitTask()
        if (matches.isEmpty) return
        matches.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc -> batch.update(doc.reference, field, newValue) }
            batch.commit().awaitTask()
        }
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

    override suspend fun adjustAccountBalance(name: String, delta: Double, owner: String, isNewAccount: Boolean) {
        val col = accountsCollection() ?: return
        val docRef = col.document(accountDocId(name))
        if (isNewAccount) {
            // Plain set (not merge) so this also works fully offline - if two devices somehow both
            // think this account is new at the same time, last write wins on the whole doc rather
            // than corrupting a partial merge; a narrow, acceptable edge case for reliability.
            docRef.set(
                mapOf(
                    "name" to name.trim().uppercase(),
                    "balance" to delta,
                    "owner" to owner,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
        } else {
            docRef.set(
                mapOf("balance" to FieldValue.increment(delta), "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge()
            )
        }
    }

    override suspend fun addGoalContribution(id: String, amount: Double) {
        goalsCollection()?.document(id)?.set(
            mapOf("savedSoFar" to FieldValue.increment(amount)),
            SetOptions.merge()
        )
    }

    override suspend fun renameAccount(oldName: String, newName: String) {
        val db = firestore ?: return
        val col = accountsCollection() ?: return
        val oldDoc = col.document(accountDocId(oldName)).get().awaitTask()
        val oldAccount = oldDoc.data?.let { Account.fromMap(oldDoc.id, it) } ?: return
        col.document(accountDocId(newName)).set(oldAccount.copy(name = newName.trim().uppercase()).toMap()).awaitTask()
        renameFieldInCollection(db, entriesCollection(), "accountName", oldAccount.name, newName.trim().uppercase())
        col.document(accountDocId(oldName)).delete().awaitTask()
    }

    override suspend fun deleteAccount(name: String) {
        accountsCollection()?.document(accountDocId(name))?.delete()
    }
}
