package pl.grillhub.staff

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Staff PIN lives in EncryptedSharedPreferences (Android Keystore backed). */
class Store(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "staff_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var pin: String
        get() = prefs.getString("pin", "") ?: ""
        set(v) = prefs.edit().putString("pin", v).apply()

    var lang: String
        get() = prefs.getString("lang", "ar") ?: "ar"
        set(v) = prefs.edit().putString("lang", v).apply()

    var baseUrl: String
        get() = prefs.getString("base", "") ?: ""
        set(v) = prefs.edit().putString("base", v).apply()
}
