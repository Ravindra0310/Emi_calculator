package com.example.emicalculator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "emi_app_preferences")

class AppPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    val selectedLanguage: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_LANGUAGE] ?: "en"
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setSelectedLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_LANGUAGE] = languageCode
        }
    }

    // In-memory session entitlement tracking
    private val _isScheduleUnlocked = MutableStateFlow(false)
    val isScheduleUnlocked: StateFlow<Boolean> = _isScheduleUnlocked.asStateFlow()

    fun unlockScheduleForSession() {
        _isScheduleUnlocked.value = true
    }

    private val _pdfExportEntitlementCount = MutableStateFlow(0)
    val pdfExportEntitlementCount: StateFlow<Int> = _pdfExportEntitlementCount.asStateFlow()

    fun grantPdfExportEntitlement() {
        _pdfExportEntitlementCount.value += 1
    }

    fun consumePdfExportEntitlement(): Boolean {
        if (_pdfExportEntitlementCount.value > 0) {
            _pdfExportEntitlementCount.value -= 1
            return true
        }
        return false
    }

    companion object {
        @Volatile
        private var INSTANCE: AppPreferencesRepository? = null

        fun getInstance(context: Context): AppPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
