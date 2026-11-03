package pl.grillhub.staff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object L {
    var lang by mutableStateOf("ar")

    private val ar = mapOf(
        "login" to "دخول", "logout" to "خروج", "scan" to "مسح QR", "byPhone" to "بحث بالتليفون",
        "redeem" to "استلام مكافأة", "scanTitle" to "وجّه الكاميرا على الـ QR",
        "phoneTitle" to "رقم تليفون العميل", "find" to "بحث", "back" to "رجوع", "home" to "الرئيسية",
        "codeTitle" to "كود المكافأة", "confirm" to "تأكيد الاستلام", "addStamp" to "إضافة ختم",
        "stamps" to "ختم", "stamped" to "تم إضافة الختم", "complete" to "الكارت اكتمل! كود المكافأة:",
        "redeemed" to "تم استلام المكافأة", "wrongPin" to "الـ PIN غلط", "net" to "مشكلة في الاتصال",
        "noCustomer" to "العميل مش موجود", "newCard" to "كارت جديد", "enterPin" to "ادخل الـ PIN (4-8 أرقام)",
        "camPerm" to "محتاج إذن الكاميرا", "allow" to "السماح"
    )
    private val pl = mapOf(
        "login" to "Zaloguj", "logout" to "Wyloguj", "scan" to "Skanuj QR", "byPhone" to "Szukaj po telefonie",
        "redeem" to "Odbierz nagrodę", "scanTitle" to "Skieruj kamerę na kod QR",
        "phoneTitle" to "Telefon klienta", "find" to "Szukaj", "back" to "Wstecz", "home" to "Start",
        "codeTitle" to "Kod nagrody", "confirm" to "Potwierdź odbiór", "addStamp" to "Dodaj pieczątkę",
        "stamps" to "pieczątek", "stamped" to "Pieczątka dodana", "complete" to "Karta pełna! Kod nagrody:",
        "redeemed" to "Nagroda odebrana", "wrongPin" to "Zły PIN", "net" to "Błąd połączenia",
        "noCustomer" to "Nie znaleziono klienta", "newCard" to "Nowa karta", "enterPin" to "Wpisz PIN (4-8 cyfr)",
        "camPerm" to "Wymagany dostęp do kamery", "allow" to "Zezwól"
    )
    private val en = mapOf(
        "login" to "Log in", "logout" to "Log out", "scan" to "Scan QR", "byPhone" to "Find by phone",
        "redeem" to "Redeem reward", "scanTitle" to "Point the camera at the QR",
        "phoneTitle" to "Customer phone", "find" to "Find", "back" to "Back", "home" to "Home",
        "codeTitle" to "Reward code", "confirm" to "Confirm redeem", "addStamp" to "Add stamp",
        "stamps" to "stamps", "stamped" to "Stamp added", "complete" to "Card complete! Reward code:",
        "redeemed" to "Reward redeemed", "wrongPin" to "Wrong PIN", "net" to "Connection problem",
        "noCustomer" to "Customer not found", "newCard" to "New card", "enterPin" to "Enter PIN (4-8 digits)",
        "camPerm" to "Camera permission needed", "allow" to "Allow"
    )

    fun t(key: String): String {
        val m = when (lang) { "pl" -> pl; "en" -> en; else -> ar }
        return m[key] ?: key
    }

    fun error(e: Throwable): String = when {
        e is ApiError && e.code == "ls_bad_pin" -> t("wrongPin")
        e is ApiError && e.code == "ls_no_customer" -> t("noCustomer")
        e is ApiError && e.code == "net" -> t("net")
        e is ApiError -> e.message
        else -> t("net")
    }
}
