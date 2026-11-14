package com.kanha.dev

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared cancellable HTTP client with connection reuse and a six-hour GET cache. */
internal object Net {
    @Volatile private var appCtx: Context? = null
    fun init(context: Context) { if (appCtx == null) synchronized(this) { if (appCtx == null) appCtx = context.applicationContext } }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .apply { appCtx?.let { cache(Cache(File(it.cacheDir, "http_meta"), 30L * 1024 * 1024)) } }
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET" && response.isSuccessful)
                    response.newBuilder().removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=21600").build()
                else response
            }.build()
    }

    suspend fun get(url: String): Pair<Int, String> =
        call(Request.Builder().url(url).header("Accept", "application/json").build())

    suspend fun postJson(url: String, json: String): Pair<Int, String> =
        call(Request.Builder().url(url).header("Accept", "application/json")
            .post(json.toRequestBody("application/json".toMediaType())).build())

    suspend fun getJson(url: String): JSONObject {
        val (code, body) = get(url)
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return JSONObject(body)
    }

    private suspend fun call(request: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resume(it.code to body)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun cacheFile(key: String): File? =
        appCtx?.let { File(File(it.cacheDir, "meta_json"), key.hashCode().toUInt().toString(16) + ".json") }

    fun readCache(key: String, ttlMs: Long): String? {
        val file = cacheFile(key) ?: return null
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > ttlMs) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun writeCache(key: String, body: String) {
        val file = cacheFile(key) ?: return
        runCatching { file.parentFile?.mkdirs(); file.writeText(body) }
    }
}
