package com.kanha.dev

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.load
import com.kanha.dev.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadata catalogue ported from lib/core/zmode:
 * AniList GraphQL for anime/manga/light novels; TMDB v3 for movies and TV.
 * Request shapes, catalogue ordering, keys and TMDB fallback host follow the
 * original Dart implementation.
 */
class HomeMetadataController(
    private val owner: LifecycleOwner,
    private val binding: FragmentHomeBinding,
    private val mode: String
) {
    data class Media(
        val id: String,
        val title: String,
        val cover: String,
        val banner: String?,
        val score: String,
        val description: String,
        val year: String,
        val genres: String,
        val mediaType: String
    )

    private val isVideo = mode == "Movie / TV"
    private val contentKind = when (mode) {
        "Manga" -> "manga"
        "Novel" -> "novel"
        "Movie / TV" -> "video"
        else -> "anime"
    }

    fun load() {
        binding.heroTitle.text = "Loading $mode…"
        binding.heroMeta.text = "DISCOVERING CONTENT"
        owner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    if (isVideo) loadTmdbRows() else loadAniListRows()
                }
                if (rows.isEmpty()) {
                    binding.heroTitle.text = "No metadata available"
                    binding.heroMeta.text = "CHECK YOUR INTERNET CONNECTION"
                    return@launch
                }
                val first = rows.first()
                val featured = first.second.take(6)
                binding.heroPager.adapter = HeroAdapter(featured)
                binding.heroPager.offscreenPageLimit = 1
                binding.heroTitle.text = featured.firstOrNull()?.title ?: first.first
                binding.heroMeta.text = listOfNotNull(mode.uppercase(), featured.firstOrNull()?.year?.takeIf { it.isNotBlank() }, featured.firstOrNull()?.score?.takeIf { it.isNotBlank() }?.let { "★ $it" }).joinToString("  ·  ")
                binding.heroDetails.setOnClickListener {
                    Toast.makeText(binding.root.context, first.second.firstOrNull()?.title ?: first.first, Toast.LENGTH_SHORT).show()
                }
                renderDots(featured.size)
                binding.heroPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        val item = featured.getOrNull(position) ?: return
                        binding.heroTitle.text = item.title
                        binding.heroMeta.text = listOf(mode.uppercase(), item.year, item.score.takeIf { it.isNotBlank() }?.let { "★ $it" }).filterNotNull().joinToString("  ·  ")
                        renderDots(featured.size, position)
                    }
                })
                binding.recentHeading.text = first.first
                renderPosters(binding.recentPosters, first.second)
                val second = rows.getOrNull(1)
                if (second != null) {
                    renderPosters(binding.popularPosters, second.second)
                }
                first.second.firstOrNull()?.let { binding.scheduleArt.load(it.cover) { allowHardware(false); crossfade(true) } }
                (second?.second?.firstOrNull() ?: first.second.getOrNull(1))?.let { binding.genresArt.load(it.cover) { allowHardware(false); crossfade(true) } }
            } catch (e: Exception) {
                binding.heroTitle.text = "Metadata couldn't load"
                binding.heroMeta.text = e.message?.take(90) ?: "PLEASE TRY AGAIN"
            }
        }
    }

    private fun renderDots(count: Int, active: Int = 0) {
        binding.heroDots.removeAllViews()
        repeat(count.coerceAtMost(6)) { index ->
            val dot = View(binding.root.context)
            val lp = LinearLayout.LayoutParams(if (index == active) dp(24) else dp(7), dp(7))
            lp.marginEnd = dp(6)
            dot.layoutParams = lp
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(if (index == active) Color.WHITE else 0x88FFFFFF.toInt())
            }
            binding.heroDots.addView(dot)
        }
    }

    private fun renderPosters(target: LinearLayout, items: List<Media>) {
        target.removeAllViews()
        items.take(20).forEach { item ->
            val card = LinearLayout(binding.root.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(12)
                }
            }
            val posterFrame = FrameLayout(binding.root.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(116), dp(166))
                background = rounded(12, 0xFF20212A.toInt())
                clipToOutline = true
            }
            val poster = ImageView(binding.root.context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(item.cover) { allowHardware(false); crossfade(true) }
                contentDescription = item.title
            }
            posterFrame.addView(poster)
            if (item.score.isNotBlank()) {
                val score = TextView(binding.root.context).apply {
                    text = "★ " + item.score
                    setTextColor(0xFFFFD54F.toInt())
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    background = rounded(10, 0xCC101116.toInt())
                }
                val badgeLp = FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END).apply {
                    topMargin = dp(7)
                    marginEnd = dp(7)
                }
                posterFrame.addView(score, badgeLp)
            }
            card.addView(posterFrame)
            val title = TextView(binding.root.context).apply {
                text = item.title
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                setPadding(0, dp(7), 0, 0)
            }
            card.addView(title)
            target.addView(card)
        }
    }

    private inner class HeroAdapter(private val items: List<Media>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<HeroAdapter.Holder>() {
        inner class Holder(val image: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(image)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val image = ImageView(parent.context).apply {
                layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            return Holder(image)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.image.load(item.banner?.takeIf { it.isNotBlank() } ?: item.cover) { allowHardware(false); crossfade(true) }
            holder.image.contentDescription = item.title
        }
    }

    private fun loadAniListRows(): List<Pair<String, List<Media>>> {
        val type = if (contentKind == "anime") "ANIME" else "MANGA"
        val format = when (contentKind) {
            "novel" -> ",format_in:[NOVEL]"
            "manga" -> ",format_not_in:[NOVEL]"
            else -> ""
        }
        val adult = ",isAdult:false"
        val rows = if (contentKind == "anime") listOf(
            "Recently released" to "sort:START_DATE_DESC,status_in:[RELEASING,FINISHED],popularity_greater:2000",
            "Trending" to "sort:TRENDING_DESC",
            "Popular this season" to "sort:POPULARITY_DESC",
            "Top rated" to "sort:SCORE_DESC"
        ) else listOf(
            "Recently released" to "sort:START_DATE_DESC,status_in:[RELEASING,FINISHED],popularity_greater:${if (contentKind == "novel") 50 else 1000}",
            "Trending" to "sort:TRENDING_DESC",
            "Popular" to "sort:POPULARITY_DESC",
            "Top rated" to "sort:SCORE_DESC"
        )
        val aliases = rows.mapIndexed { i, row ->
            "r$i: Page(perPage:20){ media(type:$type$adult$format,${row.second}){ id title{romaji english native} coverImage{large} bannerImage averageScore genres seasonYear chapters episodes status } }"
        }.joinToString(" ")
        val data = aniListRequest("query{ $aliases }")
        return rows.mapIndexedNotNull { i, row ->
            val list = data.optJSONObject("r$i")?.optJSONArray("media") ?: return@mapIndexedNotNull null
            val media = (0 until list.length()).mapNotNull { parseAniList(list.optJSONObject(it)) }
            if (media.isEmpty()) null else row.first to media
        }
    }

    private fun parseAniList(o: JSONObject?): Media? {
        if (o == null) return null
        val titles = o.optJSONObject("title")
        val title = titles?.optString("english").orEmpty().takeIf { it.isNotBlank() }
            ?: titles?.optString("romaji").orEmpty().takeIf { it.isNotBlank() }
            ?: titles?.optString("native").orEmpty()
        val cover = o.optJSONObject("coverImage")?.optString("large").orEmpty()
        if (title.isBlank() || cover.isBlank()) return null
        val genres = o.optJSONArray("genres")
        val genreText = genres?.let { a -> (0 until a.length()).map { a.optString(it) }.joinToString(" · ") }.orEmpty()
        return Media(o.optString("id"), title, cover, o.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" },
            o.optInt("averageScore").takeIf { it > 0 }?.let { "%.1f".format(it / 10.0) }.orEmpty(),
            "", o.optInt("seasonYear").takeIf { it > 0 }?.toString().orEmpty(), genreText, contentKind)
    }

    private fun loadTmdbRows(): List<Pair<String, List<Media>>> {
        val endpoints = listOf(
            "Now playing" to "/movie/now_playing",
            "Airing now" to "/tv/on_the_air",
            "Trending" to "/trending/all/week",
            "Popular movies" to "/movie/popular",
            "Popular series" to "/tv/popular"
        )
        return endpoints.mapNotNull { (label, path) ->
            val json = tmdbRequest(path)
            val results = json.optJSONArray("results") ?: return@mapNotNull null
            val items = (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val type = o.optString("media_type").ifBlank {
                    if (path.startsWith("/tv/")) "tv" else if (path.startsWith("/movie/")) "movie" else "movie"
                }
                val title = o.optString("title").ifBlank { o.optString("name") }
                val poster = o.optString("poster_path")
                if (title.isBlank() || poster.isBlank()) return@mapNotNull null
                val backdrop = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w1280$it" }
                val genres = o.optJSONArray("genre_ids")
                Media(o.optString("id"), title, "https://image.tmdb.org/t/p/w500$poster", backdrop,
                    o.optDouble("vote_average").takeIf { it > 0 }?.let { "%.1f".format(it) }.orEmpty(),
                    o.optString("overview"), o.optString("release_date").ifBlank { o.optString("first_air_date") }.take(4),
                    genres?.let { a -> (0 until a.length()).joinToString(" · ") { a.optString(it) } }.orEmpty(), type)
            }
            if (items.isEmpty()) null else label to items
        }
    }

    private fun aniListRequest(query: String): JSONObject {
        val body = JSONObject().put("query", query)
        val connection = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Origin", "https://anilist.co")
        connection.setRequestProperty("Referer", "https://anilist.co/")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val result = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val envelope = JSONObject(result)
        return envelope.optJSONObject("data") ?: throw IllegalStateException(envelope.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "AniList returned no data")
    }

    private fun tmdbRequest(path: String): JSONObject {
        val hosts = listOf("https://api.themoviedb.org/3", "https://api.tmdb.org/3")
        var last: Exception? = null
        for (host in hosts) {
            try {
                val url = "$host$path?api_key=$TMDB_API_KEY&language=en-US&page=1"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                val status = connection.responseCode
                connection.disconnect()
                if (status !in 200..299) throw IllegalStateException("TMDB HTTP $status")
                return JSONObject(text)
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("TMDB unavailable")
    }

    private fun rounded(radiusDp: Int, color: Int) = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }
    private fun dp(value: Int): Int = (value * binding.root.resources.displayMetrics.density).toInt()

    companion object {
        // Exact TMDB v3 key embedded by the original Dart Tmdb.apiKey.
        private const val TMDB_API_KEY = "fab792d6c5936a7332045ca4565c7353"
    }
}
