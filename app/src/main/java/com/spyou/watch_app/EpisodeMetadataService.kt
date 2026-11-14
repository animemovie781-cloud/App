package com.kanha.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Mirrors the Dart EpisodeMetadataService:
 * - Anime: AniZip by MAL id for episode title, overview, still, rating,
 *   runtime and air date.
 * - Missing anime stills: TMDB season endpoints via AniZip's TMDB mapping.
 * - TMDB TV: season endpoint data directly.
 * Responses are cached in SharedPreferences and in memory. Failures are
 * best-effort and never prevent the title detail screen from rendering.
 */
class EpisodeMetadataService(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("episode_metadata_cache", Context.MODE_PRIVATE)

    data class Meta(
        val number: Int,
        val title: String = "",
        val overview: String = "",
        val image: String = "",
        val rating: Double = 0.0,
        val runtime: Int = 0,
        val airDate: String = "",
        val season: Int = 1
    )

    companion object {
        private const val ANIZIP = "https://api.ani.zip/mappings?mal_id="
        private const val TMDB = "https://api.themoviedb.org/3"
        private const val TMDB_IMAGE = "https://image.tmdb.org/t/p/w300"
        private const val TMDB_KEY = "fab792d6c5936a7332045ca4565c7353"
        private val memory = ConcurrentHashMap<String, JSONArray>()
    }

    fun loadAnime(malId: Int): JSONArray {
        if (malId <= 0) return JSONArray()
        val key = "anime:$malId"
        cached(key)?.let { return it }
        return runCatching {
            val payload = request("$ANIZIP$malId")
            val base = parseAniZip(payload)
            if (base.length() == 0) return@runCatching JSONArray()
            val tmdbId = payload.optJSONObject("mappings")?.optInt("themoviedb_id", 0) ?: 0
            val merged = if (tmdbId > 0) backfillStills(base, tmdbId) else base
            save(key, merged)
            merged
        }.getOrElse { JSONArray() }
    }

    fun loadTmdbTv(tmdbId: Int, totalEpisodes: Int): JSONArray {
        if (tmdbId <= 0) return JSONArray()
        val key = "tv:$tmdbId"
        cached(key)?.let { return it }
        return runCatching {
            val detail = tmdb("/tv/$tmdbId")
            val seasons = detail.optJSONArray("seasons") ?: JSONArray()
            val result = JSONArray()
            var absoluteBase = 0
            for (i in 0 until seasons.length()) {
                val season = seasons.optJSONObject(i) ?: continue
                val seasonNo = season.optInt("season_number")
                val count = season.optInt("episode_count")
                if (seasonNo < 1 || count < 1) continue
                val payload = tmdb("/tv/$tmdbId/season/$seasonNo")
                val eps = parseTmdbSeason(payload, seasonNo)
                val nums = (0 until eps.length()).mapNotNull { eps.optJSONObject(it)?.optInt("number")?.takeIf { n -> n > 0 } }
                val minNo = nums.minOrNull() ?: 1
                val maxNo = nums.maxOrNull() ?: count
                for (j in 0 until eps.length()) {
                    val ep = eps.optJSONObject(j) ?: continue
                    val local = ep.optInt("number")
                    val absolute = if (minNo > 1 || maxNo > count) local else absoluteBase + local
                    ep.put("number", absolute).put("season", seasonNo)
                    result.put(ep)
                }
                absoluteBase += count
                if (totalEpisodes > 0 && absoluteBase >= totalEpisodes) break
            }
            if (result.length() > 0) save(key, result)
            result
        }.getOrElse { JSONArray() }
    }

    private fun parseAniZip(payload: JSONObject): JSONArray {
        val episodes = payload.optJSONObject("episodes") ?: return JSONArray()
        val result = JSONArray()
        val keys = episodes.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted()
        for (number in keys) {
            val ep = episodes.optJSONObject(number.toString()) ?: continue
            val titleValue = ep.opt("title")
            val title = when (titleValue) {
                is JSONObject -> first(titleValue.optString("en"), titleValue.optString("x-jat"), titleValue.optString("ja"))
                is String -> titleValue
                else -> ""
            }
            val overview = first(ep.optString("overview"), ep.optString("summary"))
            val runtime = ep.optInt("runtime", ep.optInt("length"))
            val airDate = first(ep.optString("airDate"), ep.optString("airdate"))
            val rating = ep.optString("rating").toDoubleOrNull() ?: 0.0
            val season = ep.optInt("seasonNumber", 1)
            val image = ep.optString("image").takeIf { it.startsWith("http") }.orEmpty()
            result.put(JSONObject()
                .put("number", number).put("title", title).put("overview", overview)
                .put("image", image).put("rating", rating).put("runtime", runtime)
                .put("airDate", airDate).put("season", season))
        }
        return result
    }

    private fun backfillStills(base: JSONArray, tmdbId: Int): JSONArray {
        if ((0 until base.length()).all { base.optJSONObject(it)?.optString("image").orEmpty().isNotBlank() }) return base
        val detail = tmdb("/tv/$tmdbId")
        val seasons = detail.optJSONArray("seasons") ?: return base
        val out = JSONArray()
        for (i in 0 until base.length()) out.put(JSONObject(base.optJSONObject(i)?.toString() ?: "{}"))
        var absoluteBase = 0
        for (i in 0 until seasons.length()) {
            val season = seasons.optJSONObject(i) ?: continue
            val seasonNo = season.optInt("season_number")
            val count = season.optInt("episode_count")
            if (seasonNo < 1 || count < 1) continue
            val seasonEpisodes = parseTmdbSeason(tmdb("/tv/$tmdbId/season/$seasonNo"), seasonNo)
            val numbers = (0 until seasonEpisodes.length()).mapNotNull { seasonEpisodes.optJSONObject(it)?.optInt("number")?.takeIf { n -> n > 0 } }
            val minNo = numbers.minOrNull() ?: 1
            val maxNo = numbers.maxOrNull() ?: count
            for (j in 0 until seasonEpisodes.length()) {
                val ep = seasonEpisodes.optJSONObject(j) ?: continue
                val local = ep.optInt("number")
                val absolute = if (minNo > 1 || maxNo > count) local else absoluteBase + local
                val still = ep.optString("image")
                if (still.isBlank()) continue
                val existing = (0 until out.length()).firstOrNull { out.optJSONObject(it)?.optInt("number") == absolute }
                if (existing == null) {
                    ep.put("number", absolute)
                    out.put(ep)
                } else {
                    val old = out.optJSONObject(existing) ?: continue
                    if (old.optString("image").isBlank()) old.put("image", still)
                }
            }
            absoluteBase += count
        }
        return out
    }

    private fun parseTmdbSeason(payload: JSONObject, season: Int): JSONArray {
        val rows = payload.optJSONArray("episodes") ?: return JSONArray()
        val out = JSONArray()
        for (i in 0 until rows.length()) {
            val ep = rows.optJSONObject(i) ?: continue
            val still = ep.optString("still_path")
            out.put(JSONObject()
                .put("number", ep.optInt("episode_number"))
                .put("title", ep.optString("name"))
                .put("overview", ep.optString("overview"))
                .put("image", if (still.isNotBlank() && still != "null") "$TMDB_IMAGE$still" else "")
                .put("rating", ep.optDouble("vote_average"))
                .put("runtime", ep.optInt("runtime"))
                .put("airDate", ep.optString("air_date"))
                .put("season", season))
        }
        return out
    }

    private fun tmdb(path: String): JSONObject =
        request("$TMDB$path?api_key=$TMDB_KEY&language=en-US")

    private fun request(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ZangetsuAndroid/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Metadata HTTP $code")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun cached(key: String): JSONArray? {
        memory[key]?.let { return JSONArray(it.toString()) }
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { JSONArray(raw).also { memory[key] = it } }.getOrNull()
    }

    private fun save(key: String, value: JSONArray) {
        memory[key] = JSONArray(value.toString())
        prefs.edit().putString(key, value.toString()).apply()
    }

    private fun first(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
}
