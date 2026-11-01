package com.monicacare.app.core.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** New account caches and sessions are authenticated-encrypted and excluded from OS backup. */
class AndroidTextCipher : TextCipher {
    private val alias = "monicacare_backend_v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun seal(value: String, purpose: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun open(value: String, purpose: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28) { "Invalid encrypted storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}

class AndroidSessionManager(context: Context, private val cipher: TextCipher) : SessionManager {
    private val store = PreferenceDataStoreFactory.create {
        File(context.noBackupFilesDir, "monicacare_session.preferences_pb")
    }
    private val key = stringPreferencesKey("encrypted_session")
    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val encrypted = cipher.seal(BackendJson.encodeToString(session), "session")
        store.edit { it[key] = encrypted }
        Unit
    }
    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        store.data.first()[key]?.let {
            BackendJson.decodeFromString<UserSession>(cipher.open(it, "session"))
        }
    }
    override suspend fun deleteSession() {
        store.edit { it.remove(key) }
    }
}
