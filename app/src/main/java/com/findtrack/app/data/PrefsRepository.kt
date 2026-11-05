package com.findtrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Small on-device preferences store. All user data stays on the phone —
 * the app has no accounts and no cloud sync.
 */
class PrefsRepository(private val context: Context) {

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_version")
    }

    /** Simulated devices for reviewing the UI without BLE hardware. */
    val demoMode: Flow<Boolean> = context.settingsStore.data.map { it[Keys.DEMO_MODE] ?: false }

    /** Proximity beeps while tracking. */
    val soundEnabled: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOUND_ENABLED] ?: true }

    /** True once the user has finished (or skipped) the welcome screen. */
    val onboardingDone: Flow<Boolean> = context.settingsStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Version we last raised an "update available" notification for. */
    val lastNotifiedVersion: Flow<String?> = context.settingsStore.data.map { it[Keys.LAST_NOTIFIED_VERSION] }

    suspend fun setDemoMode(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.DEMO_MODE] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setLastNotifiedVersion(version: String) {
        context.settingsStore.edit { it[Keys.LAST_NOTIFIED_VERSION] = version }
    }
}
