package com.darryl.unscroll.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class StoredState(val settings: Settings, val lock: LockSecret?) {
    val locked get() = lock != null
}

class SettingsRepository(private val context: Context) {
    private val lockHash = stringPreferencesKey("lock_hash")
    private val lockSalt = stringPreferencesKey("lock_salt")

    val state: Flow<StoredState> = context.dataStore.data.map { prefs ->
        val values = Toggle.entries.mapNotNull { t -> prefs[booleanPreferencesKey(t.key)]?.let { t to it } }.toMap()
        val hash = prefs[lockHash]
        val salt = prefs[lockSalt]
        StoredState(Settings(values), if (hash != null && salt != null) LockSecret(hash, salt) else null)
    }

    /** Ignored while locked: the lock covers every switch. */
    suspend fun setToggle(toggle: Toggle, on: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[lockHash] == null) prefs[booleanPreferencesKey(toggle.key)] = on
        }
    }

    suspend fun lock(password: String) {
        val secret = PasswordLock.create(password)
        context.dataStore.edit { prefs ->
            prefs[lockHash] = secret.hash
            prefs[lockSalt] = secret.salt
        }
    }

    /** Returns false when the password is wrong. */
    suspend fun unlock(password: String, current: LockSecret): Boolean {
        if (!PasswordLock.verify(password, current)) return false
        context.dataStore.edit { prefs ->
            prefs.remove(lockHash)
            prefs.remove(lockSalt)
        }
        return true
    }
}
