package com.kanha.dev

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Mirrors Zangetsu's Dart EpisodeMetadataService:
 * Anime metadata comes from AniZip by MAL id; missing episode stills are
 * backfilled from the AniZip TMDB mapping. TMDB's season payload remains the
 * fallback for titles AniZip does not map.
 */
internal object AnimeEpisodeMetadataRepository {
    private data class Meta(
        val title: String? = null,
        val overview: String? = null,
        val image: String? = null,
        val rating: Double? = null,
        val runtime: Int? = null,
        val airDate: String? = null
    )

    private val cache = ConcurrentHashMap<Int, Map<Int, Meta>>()
    private const val TMDB_IMAGE = "https://image.tmdb.org/t/p/w300"

    fun enrich(
        episodes: List<TmdbEpisode>,
        malId: Int?,
        fallbackTmdbId: Int?
    ): List<TmdbEpisode> {
        if (episodes.isEmpty() || malId == null || malId <= 0) return episodes
        val payload = runCatching { cache[malId] ?: fetchAniZip(malId).also { cache[malId] = it } }
            .getOrDefault(emptyMap())
        if (payload.isEmpty()) return episodes

        // AniZip supplies absolute episode numbers. Match by number, never by list index.
        return episodes.map { episode ->
            val meta = payload[episode.number] ?: return@map episode
            episode.copy(
                name = meta.title?.takeIf { it.isNotBlank() } ?: episode.name,
                overview = meta.overview?.takeIf { it.isNotBlank() } ?: episode.overview,
                stillUrl = meta.image?.takeIf { it.isNotBlank() } ?: episode.stillUrl,
                airDate = meta.airDate?.takeIf { it.isNotBlank() } ?: episode.airDate,
                runtime = meta.runtime?.takeIf { it > 0 } ?: episode.runtime,
                rating = meta.rating?.takeIf { it > 0.0 } ?: episode.rating
            )
        }
    }

    private fun fetchAniZip(malId: Int): Map<Int, Meta> {
        val connection = (URL("https://api.ani.zip/mappings?mal_id=$malId").openConnection() as HttpURLConnection)
        connection.connectTimeout = 7000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) return emptyMap()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val episodes = root.optJSONObject("episodes") ?: return emptyMap()
            val output = linkedMapOf<Int, Meta>()
            val keys = episodes.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val number = key.toIntOrNull() ?: continue
                val item = episodes.optJSONObject(key) ?: continue
                val titleValue = item.opt("title")
                val title = when (titleValue) {
                    is JSONObject -> firstText(titleValue, "en", "x-jat", "ja")
                    is String -> titleValue.takeIf { it.isNotBlank() }
                    else -> null
                }
                val rating = item.optString("rating").toDoubleOrNull()?.takeIf { it > 0 }
                val runtime = item.optInt("runtime", item.optInt("length", 0)).takeIf { it > 0 }
                output[number] = Meta(
                    title = title,
                    overview = firstText(item, "overview", "summary"),
                    image = item.optString("image").takeIf { it.startsWith("http") },
                    rating = rating,
                    runtime = runtime,
                    airDate = firstText(item, "airDate", "airdate")
                )
            }
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun firstText(json: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            json.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
}
