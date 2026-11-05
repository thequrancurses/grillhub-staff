package pl.grillhub.staff

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Staff PIN is encrypted with an Android Keystore AES key. Any failure -> treated as "not logged in". */
class Store(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("staff", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("staff_pin_key", null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder("staff_pin_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val data = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }

    var pin: String
        get() = try {
            val e = prefs.getString("pin", "") ?: ""
            if (e.isEmpty()) "" else decrypt(e)
        } catch (e: Throwable) { "" }
        set(v) {
            try {
                prefs.edit().putString("pin", if (v.isEmpty()) "" else encrypt(v)).apply()
            } catch (e: Throwable) {
                prefs.edit().remove("pin").apply()
            }
        }

    var lang: String
        get() = prefs.getString("lang", "ar") ?: "ar"
        set(v) = prefs.edit().putString("lang", v).apply()

    var baseUrl: String
        get() = prefs.getString("base", "") ?: ""
        set(v) = prefs.edit().putString("base", v).apply()
}
