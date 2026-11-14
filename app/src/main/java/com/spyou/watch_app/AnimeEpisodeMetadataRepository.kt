package com.kanha.dev

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** AniZip episode metadata: tap-time prefetch, shared in-flight work and session cache. */
internal object AnimeEpisodeMetadataRepository {
    private data class Meta(
        val title: String? = null, val overview: String? = null, val image: String? = null,
        val rating: Double? = null, val runtime: Int? = null, val airDate: String? = null
    )
    private val memo = ConcurrentHashMap<Int, List<TmdbEpisode>>()
    private val inflight = ConcurrentHashMap<Int, Deferred<List<TmdbEpisode>>>()
    private val malCache = ConcurrentHashMap<Int, Map<Int, Meta>>()

    fun prefetch(aniListId: Int) {
        if (aniListId > 0 && !memo.containsKey(aniListId)) start(aniListId)
    }
    fun cached(aniListId: Int): List<TmdbEpisode>? = memo[aniListId]
    suspend fun fetchByAniListId(aniListId: Int): List<TmdbEpisode> =
        memo[aniListId] ?: start(aniListId).await()

    private fun start(id: Int): Deferred<List<TmdbEpisode>> {
        val task = Net.appScope.async(start = CoroutineStart.LAZY) {
            try {
                val result = withTimeoutOrNull(6000) {
                    runCatching { toEpisodes(fetch("https://api.ani.zip/mappings?anilist_id=$id")) }
                        .getOrDefault(emptyList())
                } ?: emptyList()
                if (result.isNotEmpty()) memo[id] = result
                result
            } finally { inflight.remove(id) }
        }
        val existing = inflight.putIfAbsent(id, task)
        if (existing != null) { task.cancel(); return existing }
        task.start()
        return task
    }

    private fun toEpisodes(map: Map<Int, Meta>): List<TmdbEpisode> = map.toSortedMap().map { (number, meta) ->
        TmdbEpisode(number, meta.title?.takeIf { it.isNotBlank() } ?: "Episode $number",
            meta.overview.orEmpty(), meta.image, meta.airDate.orEmpty(),
            meta.runtime ?: 0, meta.rating ?: 0.0)
    }

    suspend fun enrich(episodes: List<TmdbEpisode>, malId: Int?, fallbackTmdbId: Int?): List<TmdbEpisode> {
        if (episodes.isEmpty() || malId == null || malId <= 0) return episodes
        val payload = malCache[malId] ?: runCatching { fetch("https://api.ani.zip/mappings?mal_id=$malId") }
            .getOrDefault(emptyMap()).also { if (it.isNotEmpty()) malCache[malId] = it }
        if (payload.isEmpty()) return episodes
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

    private suspend fun fetch(url: String): Map<Int, Meta> {
        val (code, body) = Net.get(url)
        if (code !in 200..299) return emptyMap()
        val episodes = JSONObject(body).optJSONObject("episodes") ?: return emptyMap()
        val result = linkedMapOf<Int, Meta>()
        val keys = episodes.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val number = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
            val item = episodes.optJSONObject(key) ?: continue
            val title = when (val value = item.opt("title")) {
                is JSONObject -> firstText(value, "en", "x-jat", "ja")
                is String -> value.takeIf { it.isNotBlank() }
                else -> null
            }
            result[number] = Meta(title, firstText(item, "overview", "summary"),
                item.optString("image").takeIf { it.startsWith("http") },
                item.optString("rating").toDoubleOrNull()?.takeIf { it > 0 },
                item.optInt("runtime", item.optInt("length", 0)).takeIf { it > 0 },
                firstText(item, "airDate", "airdate"))
        }
        return result
    }

    private fun firstText(json: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
}
