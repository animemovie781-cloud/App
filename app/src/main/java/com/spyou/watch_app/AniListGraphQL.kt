package com.kanha.dev

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Shared AniList GraphQL transport.
 *
 * AniList currently documents a reduced 30 requests/minute limit plus a burst
 * limiter. All app screens use this one process-wide gate so Home, Detail and
 * Schedule cannot independently flood the same endpoint.
 */
internal object AniListGraphQL {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val MIN_INTERVAL_MS = 2_100L
    private val lock = Any()
    private var nextRequestAt = 0L

    fun query(query: String, variables: JSONObject = JSONObject()): JSONObject = synchronized(lock) {
        var attempt = 0
        while (true) {
            val waitFor = nextRequestAt - System.currentTimeMillis()
            if (waitFor > 0L) Thread.sleep(waitFor)

            val payload = JSONObject()
                .put("query", query)
                .put("variables", variables)
                .toString()
                .toByteArray(Charsets.UTF_8)

            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ZangetsuAndroid/2.3.5")
                setRequestProperty("Origin", "https://anilist.co")
                setRequestProperty("Referer", "https://anilist.co/")
            }

            val status: Int
            val body: String
            val retryAfter: Long
            try {
                connection.outputStream.use { it.write(payload) }
                status = connection.responseCode
                retryAfter = retryDelayMillis(connection)
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } finally {
                connection.disconnect()
                // Pace every attempt, including failed requests.
                nextRequestAt = max(nextRequestAt, System.currentTimeMillis() + MIN_INTERVAL_MS)
            }

            val envelope = try {
                JSONObject(body)
            } catch (error: Exception) {
                throw IllegalStateException("AniList returned an invalid response (HTTP $status)", error)
            }

            if (status == 429) {
                nextRequestAt = max(nextRequestAt, System.currentTimeMillis() + retryAfter)
                throw IllegalStateException("AniList rate limit reached. Retry in about ${(retryAfter / 1000L).coerceAtLeast(1L)} seconds.")
            }

            if (status in 500..599 && attempt < 2) {
                val backoff = 1_500L * (attempt + 1)
                nextRequestAt = max(nextRequestAt, System.currentTimeMillis() + backoff)
                attempt++
                continue
            }

            if (status !in 200..299) {
                val message = envelope.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "AniList HTTP $status")
            }

            val errors = envelope.optJSONArray("errors")
            if (errors != null && errors.length() > 0 && envelope.optJSONObject("data") == null) {
                val message = errors.optJSONObject(0)?.optString("message")
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "AniList GraphQL query failed")
            }
            return@synchronized envelope.optJSONObject("data")
                ?: throw IllegalStateException("AniList returned no data")
        }
        throw IllegalStateException("AniList request failed")
    }

    private fun retryDelayMillis(connection: HttpURLConnection): Long {
        val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
        if (retryAfter != null && retryAfter > 0L) return retryAfter.coerceAtMost(120L) * 1_000L
        val resetAt = connection.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull()
        if (resetAt != null) {
            val delta = resetAt * 1_000L - System.currentTimeMillis()
            if (delta > 0L) return delta.coerceAtMost(120_000L)
        }
        return 60_000L
    }
}
