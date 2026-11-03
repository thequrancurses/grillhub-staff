package pl.grillhub.staff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val code: String?, val status: Int, override val message: String) : Exception(message)

data class Card(
    val programId: Int,
    val programName: String,
    val stamps: Int,
    val required: Int,
    val rewardTitle: String
)

data class Program(val id: Int, val name: String)

data class Customer(
    val name: String,
    val phone: String,
    val cards: List<Card>,
    val programs: List<Program>
)

data class StampResult(
    val customer: String,
    val stamps: Int,
    val completed: Boolean,
    val rewardCode: String?,
    val rewardTitle: String?
)

data class RedeemResult(val title: String, val customer: String)

object Api {
    /** Configurable for staging. */
    var baseUrl = "https://grillhub.pl/wp-json/loyalty/v1"

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (status !in 200..299) {
                throw ApiError(
                    json.optString("code").ifEmpty { null },
                    status,
                    json.optString("message", "HTTP $status")
                )
            }
            json
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw ApiError("net", 0, "net")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun find(pin: String, q: String): Customer {
        val j = post("/staff/find", JSONObject().put("staff_pin", pin).put("q", q))
        val cards = mutableListOf<Card>()
        j.optJSONArray("cards")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                cards += Card(
                    c.optInt("program_id"),
                    c.optString("program_name"),
                    c.optInt("stamps"),
                    c.optInt("stamps_required"),
                    c.optString("reward_title")
                )
            }
        }
        val programs = mutableListOf<Program>()
        j.optJSONArray("programs")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                programs += Program(p.optInt("id"), p.optString("name"))
            }
        }
        return Customer(j.optString("name"), j.optString("phone"), cards, programs)
    }

    /** Identify the customer by token (from QR) when available, otherwise by phone. */
    suspend fun stamp(pin: String, phone: String, token: String?, programId: Int): StampResult {
        val body = JSONObject().put("staff_pin", pin).put("program_id", programId)
        if (token != null) body.put("token", token) else body.put("phone", phone)
        val j = post("/stamp", body)
        val reward = j.optJSONObject("reward")
        return StampResult(
            j.optString("customer"),
            j.optInt("stamps"),
            j.optBoolean("completed"),
            reward?.optString("code"),
            reward?.optString("title")
        )
    }

    suspend fun redeem(pin: String, code: String): RedeemResult {
        val j = post("/redeem", JSONObject().put("staff_pin", pin).put("code", code))
        return RedeemResult(j.optString("title"), j.optString("customer"))
    }

    /** 403 ls_bad_pin = wrong PIN. Anything else (even 404 no customer) = PIN is fine. */
    suspend fun verifyPin(pin: String): Boolean {
        return try {
            find(pin, "__pin_check__"); true
        } catch (e: ApiError) {
            if (e.code == "net") throw e
            e.code != "ls_bad_pin"
        }
    }
}
