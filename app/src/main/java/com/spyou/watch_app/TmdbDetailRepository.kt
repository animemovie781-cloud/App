package com.kanha.dev

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TMDB detail data adapter. Supports TMDB movie/TV ids directly and resolves
 * AniList-backed anime titles through TMDB TV search when no TMDB id is supplied.
 */
internal object TmdbConfig {
    // Same v3 key already used by the app's HomeMetadataController.
    const val API_KEY = "fab792d6c5936a7332045ca4565c7353"
}

internal data class TmdbPerson(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?
)

internal data class TmdbRelated(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val mediaType: String
)

internal data class TmdbDetailBundle(
    val mediaType: String,
    val tmdbId: Int,
    val cast: List<TmdbPerson>,
    val relations: List<TmdbRelated>,
    val details: JSONObject
)

internal class TmdbDetailRepository {
    private val imageBase = "https://image.tmdb.org/t/p/w500"

    fun load(title: String, sourceType: String, sourceId: String, season: Int, episode: Int): TmdbDetailBundle {
        val resolved = resolve(title, sourceType, sourceId)
            ?: throw IllegalStateException("TMDB could not find this title. Check the title or provide a TMDB ID.")
        val (type, id) = resolved
        val details = request("/$type/$id")
        val creditsPath = if (type == "tv") {
            "/tv/$id/season/$season/episode/$episode/credits"
        } else {
            "/movie/$id/credits"
        }
        val credits = runCatching { request(creditsPath) }
            .recoverCatching { request("/$type/$id/credits") }
            .getOrDefault(JSONObject())
        val cast = parseCast(credits.optJSONArray("cast"))
        val recommendations = runCatching { request("/$type/$id/recommendations") }.getOrDefault(JSONObject())
        val similar = runCatching { request("/$type/$id/similar") }.getOrDefault(JSONObject())
        val relations = mergeResults(recommendations.optJSONArray("results"), similar.optJSONArray("results"), type)
        return TmdbDetailBundle(type, id, cast, relations, details)
    }

    private fun resolve(title: String, sourceType: String, sourceId: String): Pair<String, Int>? {
        val normalizedType = sourceType.lowercase()
        val explicitId = sourceId.toIntOrNull()
        if (explicitId != null && explicitId > 0 && normalizedType in listOf("tv", "movie")) {
            return normalizedType to explicitId
        }
        val query = URLEncoder.encode(title, "UTF-8")
        val searchType = if (normalizedType == "movie") "movie" else "tv"
        val results = request("/search/$searchType?query=$query").optJSONArray("results") ?: JSONArray()
        val wanted = normalize(title)
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val candidate = item.optString(if (searchType == "movie") "title" else "name")
            val original = item.optString(if (searchType == "movie") "original_title" else "original_name")
            val score = when {
                normalize(candidate) == wanted || normalize(original) == wanted -> 100
                normalize(candidate).contains(wanted) || wanted.contains(normalize(candidate)) -> 70
                normalize(original).contains(wanted) || wanted.contains(normalize(original)) -> 60
                else -> 0
            }
            if (score > bestScore) { best = item; bestScore = score }
        }
        val selected = best ?: return null
        if (bestScore < 60) return null
        val id = selected.optInt("id", -1)
        return if (id > 0) searchType to id else null
    }

    private fun normalize(value: String) = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun parseCast(array: JSONArray?): List<TmdbPerson> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = item.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }
            TmdbPerson(item.optInt("id"), name, item.optString("character"), path?.let { imageBase + it })
        }
    }

    private fun mergeResults(first: JSONArray?, second: JSONArray?, type: String): List<TmdbRelated> {
        val seen = linkedSetOf<Int>()
        val output = mutableListOf<TmdbRelated>()
        listOf(first, second).forEach { array ->
            if (array == null) return@forEach
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id", -1)
                if (id <= 0 || !seen.add(id)) continue
                val title = item.optString("name").takeIf { it.isNotBlank() }
                    ?: item.optString("title").takeIf { it.isNotBlank() } ?: continue
                val path = item.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                output += TmdbRelated(id, title, item.optString("overview"), path?.let { imageBase + it }, type)
            }
        }
        return output.take(20)
    }

    private fun request(path: String): JSONObject {
        val url = URL("https://api.themoviedb.org/3$path" +
            (if (path.contains("?")) "&" else "?") +
            "api_key=${TmdbConfig.API_KEY}&language=en-US")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optString("status_message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "TMDB HTTP $status")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
