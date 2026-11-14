package com.kanha.dev

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal object TmdbConfig {
    const val API_KEY = "fab792d6c5936a7332045ca4565c7353"
}
internal data class TmdbPerson(val id: Int, val name: String, val character: String, val profileUrl: String?)
internal data class TmdbRelated(val id: Int, val title: String, val overview: String, val posterUrl: String?, val mediaType: String)
internal data class TmdbEpisode(
    val number: Int, val name: String, val overview: String, val stillUrl: String?,
    val airDate: String, val runtime: Int, val rating: Double
)
internal data class TmdbDetailBundle(
    val mediaType: String, val tmdbId: Int, val cast: List<TmdbPerson>,
    val relations: List<TmdbRelated>, val details: JSONObject, val episodes: List<TmdbEpisode>
)

internal class TmdbDetailRepository {
    private val imageBase = "https://image.tmdb.org/t/p/w500"

    /** Resolve once, then fetch details, credits and season data concurrently. */
    suspend fun load(
        title: String, sourceType: String, sourceId: String,
        season: Int, episode: Int, includeExtras: Boolean = true
    ): TmdbDetailBundle = coroutineScope {
        val resolved = resolve(title, sourceType, sourceId)
            ?: throw IllegalStateException("TMDB could not find this title. Check the title or provide a TMDB ID.")
        val (type, id) = resolved
        val creditsPath = if (type == "tv") "/tv/$id/season/$season/episode/$episode/credits" else "/movie/$id/credits"
        val detailsDeferred = if (includeExtras) async(Dispatchers.IO) {
            request("/$type/$id?append_to_response=recommendations,similar")
        } else null
        val creditsDeferred = if (includeExtras) async(Dispatchers.IO) {
            runCatching { request(creditsPath) }.getOrElse { JSONObject() }
        } else null
        val seasonDeferred = if (type == "tv") async(Dispatchers.IO) {
            request("/tv/$id/season/$season")
        } else null

        val details = detailsDeferred?.await() ?: JSONObject()
        val cast = parseCast(creditsDeferred?.await()?.optJSONArray("cast"))
        val relations = mergeResults(
            details.optJSONObject("recommendations")?.optJSONArray("results"),
            details.optJSONObject("similar")?.optJSONArray("results"), type
        )
        val episodeArray = seasonDeferred?.await()?.optJSONArray("episodes") ?: JSONArray()
        if (type == "tv" && episodeArray.length() == 0)
            throw IllegalStateException("TMDB Season $season currently has no episode records.")
        val episodeItems = (0 until episodeArray.length()).mapNotNull { index ->
            val item = episodeArray.optJSONObject(index) ?: return@mapNotNull null
            val number = item.optInt("episode_number", 0)
            if (number <= 0) return@mapNotNull null
            val still = item.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
            TmdbEpisode(number, item.optString("name").ifBlank { "Episode $number" },
                item.optString("overview"), still?.let { imageBase + it },
                item.optString("air_date"), item.optInt("runtime"), item.optDouble("vote_average"))
        }
        TmdbDetailBundle(type, id, cast, relations, details, episodeItems)
    }

    suspend fun episodeCast(tmdbId: Int, season: Int, episode: Int): List<TmdbPerson> =
        runCatching { parseCast(request("/tv/$tmdbId/season/$season/episode/$episode/credits").optJSONArray("cast")) }
            .getOrDefault(emptyList())

    private suspend fun resolve(title: String, sourceType: String, sourceId: String): Pair<String, Int>? {
        val normalizedType = sourceType.lowercase()
        val explicitId = sourceId.toIntOrNull()
        if (explicitId != null && explicitId > 0 && normalizedType in listOf("tv", "movie"))
            return normalizedType to explicitId

        val baseTitle = title
            .replace(Regex("(?i)\\s+(?:season|s)\\s*[-:#]?\\s*\\d+.*$"), "")
            .replace(Regex("(?i)\\s+\\d+(?:st|nd|rd|th)\\s+season.*$"), "")
            .trim().ifBlank { title }
        val query = URLEncoder.encode(baseTitle, "UTF-8")
        val searchType = if (normalizedType == "movie") "movie" else "tv"
        val results = request("/search/$searchType?query=$query").optJSONArray("results") ?: JSONArray()
        val wanted = normalize(baseTitle)
        var best: JSONObject? = null
        var bestScore = -1
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
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
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
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

    private suspend fun request(path: String): JSONObject {
        val url = "https://api.themoviedb.org/3$path" +
            (if (path.contains("?")) "&" else "?") +
            "api_key=${TmdbConfig.API_KEY}&language=en-US"
        val (status, body) = Net.get(url)
        if (status !in 200..299) {
            val message = runCatching { JSONObject(body).optString("status_message") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "TMDB HTTP $status")
        }
        return JSONObject(body)
    }
}
