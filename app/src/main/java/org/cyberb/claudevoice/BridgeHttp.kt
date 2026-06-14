package org.cyberb.claudevoice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class BridgeHttp {

    val jsonType = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .callTimeout(200, TimeUnit.SECONDS)
        .readTimeout(200, TimeUnit.SECONDS)
        .build()

    @Volatile private var currentCall: Call? = null

    fun cancel() { currentCall?.cancel() }

    suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.trim() else null
            }
        } catch (e: Exception) { null }
    }

    suspend fun delete(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).delete().build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.trim() else null
            }
        } catch (e: Exception) { null }
    }

    suspend fun post(url: String, body: RequestBody): String? = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).post(body).build())
        currentCall = call
        try {
            call.execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.trim() else null
            }
        } catch (e: Exception) { null } finally { currentCall = null }
    }

    suspend fun postBytes(url: String, body: RequestBody): ByteArray? = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).post(body).build())
        currentCall = call
        try {
            call.execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (e: Exception) { null } finally { currentCall = null }
    }

    suspend fun stream(url: String, body: RequestBody, onLine: suspend (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).post(body).build())
        currentCall = call
        try {
            call.execute().use { r ->
                val src = r.body?.source()
                if (!r.isSuccessful || src == null) return@use false
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    onLine(line)
                }
                true
            }
        } catch (e: Exception) { false } finally { currentCall = null }
    }
}
