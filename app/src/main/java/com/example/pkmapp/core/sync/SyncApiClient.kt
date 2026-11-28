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
    private const val READ_TIMEOUT_MS = 20_000

    data class AuthApiResponse(
        val ok: Boolean,
        val userId: String = "",
        val nickname: String = "",
        val accessToken: String = "",
        val refreshToken: String = "",
        val errorMessage: String = "",
        val httpStatus: Int = 0
    ) {
        val unauthorized: Boolean
            get() = httpStatus == 401
    }

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
        val errorMessage: String = "",
        val httpStatus: Int = 0
    ) {
        val unauthorized: Boolean
            get() = httpStatus == 401
    }

    data class SyncPullResponse(
        val ok: Boolean,
        val data: JSONObject? = null,
        val errorMessage: String = "",
        val httpStatus: Int = 0
    ) {
        val unauthorized: Boolean
            get() = httpStatus == 401
    }

    data class FeedbackSubmitResponse(
        val ok: Boolean,
        val errorMessage: String = "",
        val httpStatus: Int = 0
    ) {
        val unauthorized: Boolean
            get() = httpStatus == 401
    }

    private data class HttpJsonResult(
        val httpStatus: Int,
        val body: JSONObject?
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
        val res = postJsonWithStatus("$baseUrl/api/v1/auth/register", body, null) ?: return null
        return parseAuthResponse(res.body, res.httpStatus)
    }

    fun login(baseUrl: String, identifier: String, password: String): AuthApiResponse? {
        val body = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
        val res = postJsonWithStatus("$baseUrl/api/v1/auth/login", body, null) ?: return null
        return parseAuthResponse(res.body, res.httpStatus)
    }

    fun refresh(baseUrl: String, refreshToken: String): AuthApiResponse? {
        val body = JSONObject().put("refreshToken", refreshToken)
        val res = postJsonWithStatus("$baseUrl/api/v1/auth/refresh", body, null) ?: return null
        return parseAuthResponse(res.body, res.httpStatus)
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
        val res = postJsonWithStatus("$baseUrl/api/v1/sync/push", payload, accessToken) ?: return null
        val json = res.body
        val ok = res.httpStatus in 200..299 && json?.optBoolean("ok", false) == true
        val data = json?.optJSONObject("data")
        val err = json?.optJSONObject("error")
        return SyncPushResponse(
            ok = ok,
            serverSeq = data?.optLong("serverSeq", 0L) ?: 0L,
            errorMessage = err?.optString("message", "").orEmpty(),
            httpStatus = res.httpStatus
        )
    }

    fun pullSyncDeltaWithStatus(
        baseUrl: String,
        accessToken: String,
        sinceCursor: Long
    ): SyncPullResponse? {
        val res = requestJsonWithStatus("GET", "$baseUrl/api/v1/sync/pull?since=$sinceCursor", null, accessToken)
            ?: return null
        val json = res.body
        val ok = res.httpStatus in 200..299 && json?.optBoolean("ok", false) == true
        val err = json?.optJSONObject("error")
        return SyncPullResponse(
            ok = ok,
            data = if (ok) json?.optJSONObject("data") else null,
            errorMessage = err?.optString("message", "").orEmpty(),
            httpStatus = res.httpStatus
        )
    }

    fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? {
        val res = pullSyncDeltaWithStatus(baseUrl, accessToken, sinceCursor) ?: return null
        if (!res.ok) return null
        return res.data
    }

    fun submitFeedbackWithStatus(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): FeedbackSubmitResponse? {
        val body = JSONObject()
            .put("category", category)
            .put("content", content)
            .put("contact", contact)
            .put("appVersion", appVersion)
            .put("platform", "android")
            .put("clientRequestId", clientRequestId)
        val res = postJsonWithStatus("$baseUrl/api/v1/feedbacks", body, accessToken) ?: return null
        val json = res.body
        val ok = res.httpStatus in 200..299 && json?.optBoolean("ok", false) == true
        val err = json?.optJSONObject("error")
        return FeedbackSubmitResponse(
            ok = ok,
            errorMessage = err?.optString("message", "").orEmpty(),
            httpStatus = res.httpStatus
        )
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
        return submitFeedbackWithStatus(
            baseUrl = baseUrl,
            accessToken = accessToken,
            category = category,
            content = content,
            contact = contact,
            appVersion = appVersion,
            clientRequestId = clientRequestId
        )?.ok == true
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

    private fun parseAuthResponse(res: JSONObject?, httpStatus: Int): AuthApiResponse {
        val ok = httpStatus in 200..299 && res?.optBoolean("ok", false) == true
        val data = res?.optJSONObject("data")
        val err = res?.optJSONObject("error")
        val user = data?.optJSONObject("user")
        return AuthApiResponse(
            ok = ok,
            userId = user?.optString("id", "").orEmpty(),
            nickname = user?.optString("nickname", "").orEmpty(),
            accessToken = data?.optString("accessToken", "").orEmpty(),
            refreshToken = data?.optString("refreshToken", "").orEmpty(),
            errorMessage = err?.optString("message", "").orEmpty(),
            httpStatus = httpStatus
        )
    }

    private fun postJsonWithStatus(url: String, body: JSONObject, token: String?): HttpJsonResult? =
        requestJsonWithStatus("POST", url, body, token)

    private fun requestJson(
        method: String,
        urlString: String,
        body: JSONObject?,
        token: String?
    ): JSONObject? = requestJsonWithStatus(method, urlString, body, token)?.body

    private fun requestJsonWithStatus(
        method: String,
        urlString: String,
        body: JSONObject?,
        token: String?
    ): HttpJsonResult? {
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
            val parsed = if (stream != null) {
                val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                if (text.isBlank()) null else try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            HttpJsonResult(httpStatus = code, body = parsed)
        } catch (ex: Exception) {
            android.util.Log.w("SyncApiClient", "$method $urlString failed: ${ex.message}", ex)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
