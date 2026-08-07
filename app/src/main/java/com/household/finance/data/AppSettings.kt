package com.household.finance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "household_finance_settings")

/**
 * Local-only settings: this device's identity (person names), Firebase config, and the OpenAI key.
 * Firebase config + OpenAI key are stored ONLY on-device (never written to Firestore).
 */
class AppSettings(private val context: Context) {

    private object Keys {
        val NAME_ME = stringPreferencesKey("name_me")
        val NAME_WIFE = stringPreferencesKey("name_wife")
        val FB_API_KEY = stringPreferencesKey("fb_api_key")
        val FB_APP_ID = stringPreferencesKey("fb_app_id")
        val FB_PROJECT_ID = stringPreferencesKey("fb_project_id")
        val FB_STORAGE_BUCKET = stringPreferencesKey("fb_storage_bucket")
        val FB_MESSAGING_SENDER_ID = stringPreferencesKey("fb_messaging_sender_id")
        val OPENAI_KEY = stringPreferencesKey("openai_key")
        val CATEGORY_LENGTH = stringPreferencesKey("category_length")
        val SALARY_CREDIT_DATE = intPreferencesKey("salary_credit_date")
    }

    val nameMeFlow: Flow<String> = context.dataStore.data.map { it[Keys.NAME_ME] ?: "Me" }
    val nameWifeFlow: Flow<String> = context.dataStore.data.map { it[Keys.NAME_WIFE] ?: "Wife" }
    val openAiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.OPENAI_KEY] ?: "" }
    val categoryLengthFlow: Flow<CategoryListLength> = context.dataStore.data.map {
        runCatching { CategoryListLength.valueOf(it[Keys.CATEGORY_LENGTH] ?: "MEDIUM") }.getOrDefault(CategoryListLength.MEDIUM)
    }
    /** Day of month (1-31) this device owner's salary is credited, or null if not set. */
    val salaryCreditDateFlow: Flow<Int?> = context.dataStore.data.map { it[Keys.SALARY_CREDIT_DATE] }

    suspend fun saveCategoryLength(length: CategoryListLength) {
        context.dataStore.edit { it[Keys.CATEGORY_LENGTH] = length.name }
    }

    suspend fun saveSalaryCreditDate(day: Int?) {
        context.dataStore.edit {
            if (day == null) it.remove(Keys.SALARY_CREDIT_DATE) else it[Keys.SALARY_CREDIT_DATE] = day.coerceIn(1, 31)
        }
    }

    data class FirebaseConfig(
        val apiKey: String = "",
        val appId: String = "",
        val projectId: String = "",
        val storageBucket: String = "",
        val messagingSenderId: String = ""
    ) {
        val isComplete: Boolean
            get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()
    }

    val firebaseConfigFlow: Flow<FirebaseConfig> = context.dataStore.data.map {
        FirebaseConfig(
            apiKey = it[Keys.FB_API_KEY] ?: "",
            appId = it[Keys.FB_APP_ID] ?: "",
            projectId = it[Keys.FB_PROJECT_ID] ?: "",
            storageBucket = it[Keys.FB_STORAGE_BUCKET] ?: "",
            messagingSenderId = it[Keys.FB_MESSAGING_SENDER_ID] ?: ""
        )
    }

    suspend fun currentFirebaseConfig(): FirebaseConfig = firebaseConfigFlow.first()

    suspend fun saveNames(me: String, wife: String) {
        context.dataStore.edit {
            it[Keys.NAME_ME] = me
            it[Keys.NAME_WIFE] = wife
        }
    }

    suspend fun saveOpenAiKey(key: String) {
        context.dataStore.edit { it[Keys.OPENAI_KEY] = key }
    }

    suspend fun saveFirebaseConfig(config: FirebaseConfig) {
        context.dataStore.edit {
            it[Keys.FB_API_KEY] = config.apiKey
            it[Keys.FB_APP_ID] = config.appId
            it[Keys.FB_PROJECT_ID] = config.projectId
            it[Keys.FB_STORAGE_BUCKET] = config.storageBucket
            it[Keys.FB_MESSAGING_SENDER_ID] = config.messagingSenderId
        }
    }
}
