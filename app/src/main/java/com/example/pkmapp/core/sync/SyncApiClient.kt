package com.example.pkmapp.core.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight HTTPS/HTTP client aligned with the Spring Boot 3 + MySQL 8 backend contract.
 * Returns null when network is unreachable so Offline-First callers degrade gracefully.
 */
object SyncApiClient {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    data class AuthApiResponse(
        val ok: Boolean,
        val userId: String = "",
        val nickname: String = "",
        val accessToken: String = "",
        val refreshToken: String = "",
        val errorMessage: String = ""
    )

    data class RemoteFeedbackItem(
        val id: String,
        val category: String,
        val content: String,
        val contact: String,
        val appVersion: String,
        val status: String,
        val reply: String,
        val clientRequestId: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    data class SyncPushResponse(
        val ok: Boolean,
        val serverSeq: Long = 0L,
        val errorMessage: String = ""
    )

    fun register(
        baseUrl: String,
        identifier: String,
        password: String,
        nickname: String
    ): AuthApiResponse? {
        val body = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
            .put("nickname", nickname)
        val res = postJson("$baseUrl/api/v1/auth/register", body, null) ?: return null
        return parseAuthResponse(res)
    }

    fun login(baseUrl: String, identifier: String, password: String): AuthApiResponse? {
        val body = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
        val res = postJson("$baseUrl/api/v1/auth/login", body, null) ?: return null
        return parseAuthResponse(res)
    }

    fun deleteAccount(baseUrl: String, accessToken: String): Boolean {
        return requestJson("DELETE", "$baseUrl/api/v1/auth/account", null, accessToken)
            ?.optBoolean("ok", false) == true
    }

    fun pushSyncBatch(
        baseUrl: String,
        accessToken: String,
        payload: JSONObject
    ): SyncPushResponse? {
        val res = postJson("$baseUrl/api/v1/sync/push", payload, accessToken) ?: return null
        val ok = res.optBoolean("ok", false)
        val data = res.optJSONObject("data")
        val err = res.optJSONObject("error")
        return SyncPushResponse(
            ok = ok,
            serverSeq = data?.optLong("serverSeq", 0L) ?: 0L,
            errorMessage = err?.optString("message", "").orEmpty()
        )
    }

    fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? {
        val res = requestJson("GET", "$baseUrl/api/v1/sync/pull?since=$sinceCursor", null, accessToken)
            ?: return null
        if (!res.optBoolean("ok", false)) return null
        return res.optJSONObject("data")
    }

    fun submitFeedback(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): Boolean {
        val body = JSONObject()
            .put("category", category)
            .put("content", content)
            .put("contact", contact)
            .put("appVersion", appVersion)
            .put("platform", "android")
            .put("clientRequestId", clientRequestId)
        val res = postJson("$baseUrl/api/v1/feedbacks", body, accessToken) ?: return false
        return res.optBoolean("ok", false)
    }

    fun fetchMyFeedbacks(baseUrl: String, accessToken: String): List<RemoteFeedbackItem>? {
        val res = requestJson("GET", "$baseUrl/api/v1/feedbacks/mine", null, accessToken) ?: return null
        if (!res.optBoolean("ok", false)) return null
        val arr = res.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
        val list = ArrayList<RemoteFeedbackItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                RemoteFeedbackItem(
                    id = obj.optString("id"),
                    category = obj.optString("category", "other"),
                    content = obj.optString("content", ""),
                    contact = obj.optString("contact", ""),
                    appVersion = obj.optString("appVersion", "4.0.0"),
                    status = obj.optString("status", "pending"),
                    reply = obj.optString("reply", ""),
                    clientRequestId = obj.optString("clientRequestId", ""),
                    createdAt = obj.optLong("createdAt", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            )
        }
        return list
    }

    private fun parseAuthResponse(res: JSONObject): AuthApiResponse {
        val ok = res.optBoolean("ok", false)
        val data = res.optJSONObject("data")
        val err = res.optJSONObject("error")
        val user = data?.optJSONObject("user")
        return AuthApiResponse(
            ok = ok,
            userId = user?.optString("id", "").orEmpty(),
            nickname = user?.optString("nickname", "").orEmpty(),
            accessToken = data?.optString("accessToken", "").orEmpty(),
            refreshToken = data?.optString("refreshToken", "").orEmpty(),
            errorMessage = err?.optString("message", "").orEmpty()
        )
    }

    private fun postJson(url: String, body: JSONObject, token: String?): JSONObject? =
        requestJson("POST", url, body, token)

    private fun requestJson(
        method: String,
        urlString: String,
        body: JSONObject?,
        token: String?
    ): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    doOutput = true
                }
            }
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (stream == null) return null
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            JSONObject(text)
        } catch (ex: Exception) {
            android.util.Log.w("SyncApiClient", "$method $urlString failed: ${ex.message}", ex)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
