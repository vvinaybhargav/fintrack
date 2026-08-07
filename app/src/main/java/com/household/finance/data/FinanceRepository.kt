package com.household.finance.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Data-layer contract. Swap the implementation without touching UI code. */
interface FinanceRepository {
    fun observeEntries(): Flow<List<Entry>>
    suspend fun addEntry(entry: Entry)
    suspend fun deleteEntry(id: String)
    fun observeEmergencyFund(): Flow<EmergencyFund>
    suspend fun setEmergencyFund(amount: Double)
    fun observeGoals(): Flow<List<Goal>>
    suspend fun addGoal(goal: Goal)
    suspend fun deleteGoal(id: String)
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
}
