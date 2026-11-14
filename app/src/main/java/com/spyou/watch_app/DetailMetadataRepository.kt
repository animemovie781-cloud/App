package com.kanha.dev

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Detail metadata follows the source catalogue used by the Dart app:
 * every anime (TV, movie, OVA, ONA and special) is resolved on AniList;
 * ordinary movie/TV catalogue entries continue to use TMDB.
 */
class DetailMetadataRepository {
    fun load(item: JSONObject): JSONObject? {
        val kind = item.optString("mediaType").trim().lowercase()
        return when (kind) {
            "anime" -> loadAniList(item)
            "movie", "tv" -> loadTmdb(kind, item.optString("id"))
            else -> null
        }
    }

    private fun loadAniList(item: JSONObject): JSONObject? {
        val rawId = item.optString("id").trim()
        val prefixedMalId = rawId.takeIf { it.startsWith("mal:", true) }
            ?.substringAfter(":")?.toIntOrNull()
        val anilistId = if (prefixedMalId != null) null else rawId.toIntOrNull()
        val malId = item.optInt("malId").takeIf { it > 0 } ?: prefixedMalId
        if (anilistId == null && malId == null) return null
        val query = """
            query {
              Media(id: ${anilistId ?: "null"}, idMal: ${malId ?: "null"}, type: ANIME) {
                id idMal format status episodes duration season seasonYear averageScore meanScore popularity favourites
                title { romaji english native }
                coverImage { extraLarge large }
                bannerImage
                description(asHtml: false)
                startDate { year month day }
                endDate { year month day }
                genres
                studios(isMain: true) { nodes { name } }
                tags { name rank }
                characters(sort: ROLE, page: 1, perPage: 30) {
                  edges {
                    role
                    node { id name { full } image { large medium } }
                    voiceActors(language: JAPANESE, sort: RELEVANCE) { id name { full } image { medium } }
                  }
                }
                relations {
                  edges {
                    relationType
                    node { id idMal type format title { romaji english native } coverImage { large } }
                  }
                }
                recommendations(sort: RATING_DESC, page: 1, perPage: 12) {
                  nodes { mediaRecommendation { id format title { romaji english native } coverImage { large } } }
                }
              }
            }
        """.trimIndent()
        val media = postAniList(query, JSONObject())
            .optJSONObject("Media") ?: return null
        val title = chooseTitle(media.optJSONObject("title"))
        val date = media.optJSONObject("startDate")
        val genres = strings(media.optJSONArray("genres"))
        val studios = JSONArray()
        val studioNodes = media.optJSONObject("studios")?.optJSONArray("nodes")
        if (studioNodes != null) for (i in 0 until studioNodes.length()) {
            studioNodes.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { studios.put(it) }
        }
        val cast = JSONArray()
        val edges = media.optJSONObject("characters")?.optJSONArray("edges")
        if (edges != null) for (i in 0 until edges.length()) {
            val edge = edges.optJSONObject(i) ?: continue
            val character = edge.optJSONObject("node") ?: continue
            val name = character.optJSONObject("name")?.optString("full").orEmpty()
            if (name.isBlank()) continue
            val va = edge.optJSONArray("voiceActors")?.optJSONObject(0)
            cast.put(JSONObject()
                .put("name", name)
                .put("role", edge.optString("role"))
                .put("image", character.optJSONObject("image")?.optString("large").orEmpty())
                .put("voiceActor", va?.optJSONObject("name")?.optString("full").orEmpty())
                .put("voiceImage", va?.optJSONObject("image")?.optString("medium").orEmpty()))
        }
        val relations = JSONArray()
        val relationEdges = media.optJSONObject("relations")?.optJSONArray("edges")
        if (relationEdges != null) for (i in 0 until relationEdges.length()) {
            val edge = relationEdges.optJSONObject(i) ?: continue
            val node = edge.optJSONObject("node") ?: continue
            if (node.optString("type") != "ANIME") continue
            val relTitle = chooseTitle(node.optJSONObject("title"))
            if (relTitle.isBlank()) continue
            relations.put(JSONObject().put("id", node.optInt("id"))
                .put("title", relTitle).put("relation", relationLabel(edge.optString("relationType")))
                .put("cover", node.optJSONObject("coverImage")?.optString("large").orEmpty())
                .put("format", node.optString("format")))
        }
        val recs = media.optJSONObject("recommendations")?.optJSONArray("nodes")
        if (recs != null) for (i in 0 until recs.length()) {
            val node = recs.optJSONObject(i)?.optJSONObject("mediaRecommendation") ?: continue
            val recTitle = chooseTitle(node.optJSONObject("title"))
            if (recTitle.isBlank()) continue
            relations.put(JSONObject().put("id", node.optInt("id")).put("title", recTitle)
                .put("relation", "Recommended").put("cover", node.optJSONObject("coverImage")?.optString("large").orEmpty())
                .put("format", node.optString("format")))
        }
        return JSONObject()
            .put("metadataSource", "AniList")
            .put("anilistId", media.optInt("id"))
            .put("malId", media.optInt("idMal"))
            .put("title", title)
            .put("description", cleanDescription(media.optString("description")))
            .put("cover", media.optJSONObject("coverImage")?.optString("extraLarge").orEmpty().ifBlank { media.optJSONObject("coverImage")?.optString("large").orEmpty() })
            .put("banner", media.optString("bannerImage"))
            .put("year", date?.optInt("year")?.takeIf { it > 0 }?.toString() ?: media.optInt("seasonYear").takeIf { it > 0 }?.toString().orEmpty())
            .put("format", media.optString("format"))
            .put("status", media.optString("status"))
            .put("episodes", media.optInt("episodes"))
            .put("duration", media.optInt("duration"))
            .put("score", media.optDouble("averageScore").takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it / 10.0) }.orEmpty())
            .put("genres", genres.joinToString(" · "))
            .put("creators", studios.toStringArray().joinToString(" · "))
            .put("castMembers", cast)
            .put("relationsList", relations)
            .put("isAnimeMovie", media.optString("format") == "MOVIE")
    }

    private fun loadTmdb(kind: String, rawId: String): JSONObject? {
        val id = rawId.toIntOrNull() ?: return null
        val detail = tmdb("/$kind/$id?append_to_response=credits")
        val title = detail.optString(if (kind == "tv") "name" else "title")
        val credits = detail.optJSONObject("credits")
        val cast = JSONArray()
        val castRows = credits?.optJSONArray("cast")
        if (castRows != null) for (i in 0 until minOf(castRows.length(), 30)) {
            val p = castRows.optJSONObject(i) ?: continue
            cast.put(JSONObject().put("name", p.optString("name"))
                .put("role", p.optString("character"))
                .put("image", p.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w185$it" }.orEmpty()))
        }
        val genres = detail.optJSONArray("genres")
        val genreNames = mutableListOf<String>()
        if (genres != null) for (i in 0 until genres.length()) genres.optJSONObject(i)?.optString("name")?.let(genreNames::add)
        val creators = JSONArray()
        val crew = credits?.optJSONArray("crew")
        if (crew != null) for (i in 0 until crew.length()) {
            val p = crew.optJSONObject(i) ?: continue
            if (p.optString("job") in setOf("Director", "Writer", "Screenplay", "Creator")) creators.put(p.optString("name"))
        }
        return JSONObject().put("metadataSource", "TMDB").put("title", title)
            .put("description", detail.optString("overview"))
            .put("year", (detail.optString(if (kind == "tv") "first_air_date" else "release_date")).take(4))
            .put("status", detail.optString("status"))
            .put("episodes", detail.optInt("number_of_episodes"))
            .put("genres", genreNames.joinToString(" · "))
            .put("creators", creators.toStringArray().distinct().joinToString(" · "))
            .put("score", detail.optDouble("vote_average").takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it) }.orEmpty())
            .put("castMembers", cast).put("relationsList", JSONArray())
    }

    private fun postAniList(query: String, variables: JSONObject): JSONObject {
        val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "ZangetsuAndroid/1.0")
        conn.outputStream.use { it.write(JSONObject().put("query", query).put("variables", variables).toString().toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val envelope = JSONObject(body)
        if (status !in 200..299) throw IllegalStateException(envelope.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "AniList HTTP $status")
        return envelope.optJSONObject("data") ?: JSONObject()
    }

    private fun tmdb(path: String): JSONObject {
        val key = "fab792d6c5936a7332045ca4565c7353"
        val url = "https://api.themoviedb.org/3$path&api_key=$key&language=en-US"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 10000
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        conn.disconnect()
        if (status !in 200..299) throw IllegalStateException("TMDB HTTP $status")
        return JSONObject(body)
    }

    private fun chooseTitle(t: JSONObject?): String =
        listOf(t?.optString("english"), t?.optString("romaji"), t?.optString("native"))
            .firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() && it != "null" }

    private fun JSONArray.toStringArray(): List<String> =
        (0 until length()).map { optString(it) }.filter { it.isNotBlank() && it != "null" }

    private fun cleanDescription(value: String): String =
        value.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").trim()

    private fun relationLabel(value: String): String = value.lowercase().replace('_', ' ')
        .split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
