package com.household.finance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "household_finance_settings")

/**
 * Local-only settings: this device's active/default/trusted profile, Firebase config, and the
 * OpenAI key. Firebase config + OpenAI key are stored ONLY on-device (never written to Firestore).
 * Profile identity (name + PIN) itself lives in Firestore - see [Profile] - so it's shared across
 * both phones; only which profile this particular device currently trusts/defaults to is local.
 */
class AppSettings(private val context: Context) {

    private object Keys {
        val CURRENT_PROFILE = stringPreferencesKey("current_profile")
        val DEFAULT_PROFILE = stringPreferencesKey("default_profile")
        val TRUSTED_PROFILES = stringSetPreferencesKey("trusted_profiles")
        val FB_API_KEY = stringPreferencesKey("fb_api_key")
        val FB_APP_ID = stringPreferencesKey("fb_app_id")
        val FB_PROJECT_ID = stringPreferencesKey("fb_project_id")
        val FB_STORAGE_BUCKET = stringPreferencesKey("fb_storage_bucket")
        val FB_MESSAGING_SENDER_ID = stringPreferencesKey("fb_messaging_sender_id")
        val OPENAI_KEY = stringPreferencesKey("openai_key")
        val CATEGORY_LENGTH = stringPreferencesKey("category_length")
    }

    /** The profile currently signed in on this device, or null if none has been chosen yet. */
    val currentProfileFlow: Flow<String?> = context.dataStore.data.map { it[Keys.CURRENT_PROFILE] }
    /** Which profile auto-signs-in on launch without a PIN prompt. */
    val defaultProfileFlow: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_PROFILE] }
    /** Profiles that skip the PIN prompt on this device (the default is always included). */
    val trustedProfilesFlow: Flow<Set<String>> = context.dataStore.data.map { it[Keys.TRUSTED_PROFILES] ?: emptySet() }

    val openAiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.OPENAI_KEY] ?: "" }
    val categoryLengthFlow: Flow<CategoryListLength> = context.dataStore.data.map {
        runCatching { CategoryListLength.valueOf(it[Keys.CATEGORY_LENGTH] ?: "MEDIUM") }.getOrDefault(CategoryListLength.MEDIUM)
    }
    suspend fun setCurrentProfile(name: String) {
        context.dataStore.edit { it[Keys.CURRENT_PROFILE] = name }
    }

    /** Used by "Switch Profile" in Settings to return to the picker without losing default/trust state. */
    suspend fun clearCurrentProfile() {
        context.dataStore.edit { it.remove(Keys.CURRENT_PROFILE) }
    }

    suspend fun setDefaultProfile(name: String) {
        context.dataStore.edit {
            it[Keys.DEFAULT_PROFILE] = name
            it[Keys.TRUSTED_PROFILES] = (it[Keys.TRUSTED_PROFILES] ?: emptySet()) + name
        }
    }

    suspend fun trustProfile(name: String) {
        context.dataStore.edit { it[Keys.TRUSTED_PROFILES] = (it[Keys.TRUSTED_PROFILES] ?: emptySet()) + name }
    }

    suspend fun saveCategoryLength(length: CategoryListLength) {
        context.dataStore.edit { it[Keys.CATEGORY_LENGTH] = length.name }
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
