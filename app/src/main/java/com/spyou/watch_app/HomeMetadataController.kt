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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val mediaType: String,
        val episodes: Int = 0
    )

    private val isVideo = mode == "Movie / TV"
    private val contentKind = when (mode) {
        "Manga" -> "manga"
        "Novel" -> "novel"
        "Movie / TV" -> "video"
        else -> "anime"
    }

    fun load() {
        // Render a recent snapshot immediately; refresh it without blocking first paint.
        val cached = readCachedRows()
        if (cached.isNotEmpty()) renderRows(cached)
        else {
            binding.heroTitle.text = "Loading $mode…"
            binding.heroMeta.text = "DISCOVERING CONTENT"
        }
        owner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    if (isVideo) loadTmdbRows() else loadAniListRows()
                }
                if (rows.isNotEmpty()) {
                    writeCachedRows(rows)
                    renderRows(rows)
                } else if (cached.isEmpty()) {
                    binding.heroTitle.text = "No metadata available"
                    binding.heroMeta.text = "CHECK YOUR INTERNET CONNECTION"
                }
            } catch (e: Exception) {
                if (cached.isEmpty()) {
                    binding.heroTitle.text = "Metadata couldn't load"
                    binding.heroMeta.text = e.message?.take(90) ?: "PLEASE TRY AGAIN"
                }
            }
        }
    }

    private fun renderRows(rows: List<Pair<String, List<Media>>>) {
        // The original source PNGs are 96px assets; Flutter displays them at 20dp.
        listOf(binding.scheduleCard, binding.genresCard).forEach { card ->
            val group = card as ViewGroup
            val label = (0 until group.childCount).mapNotNull { group.getChildAt(it) as? TextView }.firstOrNull()
            label?.let { text ->
                val drawables = text.compoundDrawablesRelative
                val icon = drawables[0]
                icon?.setBounds(0, 0, dp(18), dp(18))
                text.setCompoundDrawablesRelative(icon, drawables[1], drawables[2], drawables[3])
                text.compoundDrawablePadding = dp(8)
            }
        }
        if (rows.isEmpty()) return
        val first = rows.first()
        val featured = (rows.firstOrNull { it.first.startsWith("Trending", ignoreCase = true) }?.second ?: first.second).take(6)
        binding.heroPager.adapter = HeroAdapter(featured)
        binding.heroPager.offscreenPageLimit = 1
        // Match the Dart edge banner: 650ms cross-fade and 5-second rotation.
        binding.heroPager.setPageTransformer { page, position ->
            page.alpha = (1f - kotlin.math.abs(position)).coerceIn(0f, 1f)
        }
        owner.lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val count = binding.heroPager.adapter?.itemCount ?: 0
                if (count > 1 && binding.heroPager.isShown) {
                    binding.heroPager.setCurrentItem((binding.heroPager.currentItem + 1) % count, true)
                }
            }
        }
        binding.heroLogo.visibility = View.GONE
        binding.heroTitle.visibility = View.VISIBLE
        binding.heroTitle.text = featured.firstOrNull()?.title ?: first.first
        updateHeroMeta(featured.firstOrNull())
        loadHeroLogo(featured.firstOrNull())
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
        binding.recentSeeAll.setOnClickListener { openSeeAll(first.first, first.second) }
        renderPosters(binding.recentPosters, first.second)
        val second = rows.getOrNull(1)
        renderAdditionalSections(rows.drop(1))
        if (isVideo) loadStreamingServices()
        first.second.firstOrNull()?.let { binding.scheduleArt.load(it.cover) { allowHardware(false); crossfade(true) } }
        (second?.second?.firstOrNull() ?: first.second.getOrNull(1))?.let { binding.genresArt.load(it.cover) { allowHardware(false); crossfade(true) } }
    }

    private fun openSeeAll(title: String, items: List<Media>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title).put("cover", item.cover)
                .put("score", item.score).put("year", item.year).put("mediaType", item.mediaType))
        }
        binding.root.context.startActivity(android.content.Intent(binding.root.context, SeeAllActivity::class.java)
            .putExtra(SeeAllActivity.EXTRA_TITLE, title).putExtra(SeeAllActivity.EXTRA_ITEMS, array.toString()))
    }

    private fun renderAdditionalSections(rows: List<Pair<String, List<Media>>>) {
        val host = binding.additionalSections
        host.removeAllViews()
        rows.forEach { (heading, items) ->
            if (items.isEmpty()) return@forEach
            val header = LinearLayout(binding.root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(24), dp(16), 0)
            }
            val title = TextView(binding.root.context).apply {
                text = heading
                setTextColor(Color.WHITE)
                textSize = 17f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val seeAll = TextView(binding.root.context).apply {
                text = "See All"
                setTextColor(0xFFFF4D57.toInt())
                textSize = 13f
                setPadding(dp(8), dp(6), 0, dp(6))
                setOnClickListener { openSeeAll(heading, items) }
            }
            header.addView(title)
            header.addView(seeAll)
            host.addView(header, LinearLayout.LayoutParams(-1, -2))
            val scroller = android.widget.HorizontalScrollView(binding.root.context).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                setPadding(dp(16), dp(14), dp(16), 0)
            }
            val posters = LinearLayout(binding.root.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            scroller.addView(posters, ViewGroup.LayoutParams(-2, -2))
            renderPosters(posters, items)
            host.addView(scroller, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /**
     * Native port of Dart StreamingProvidersService + StreamingServicesRow.
     * Merge movie and TV watch providers for the user's configured region,
     * deduplicate by provider_id, pin the same major services first, and
     * render TMDB's original provider logos as compact horizontal cards.
     */
    private fun loadStreamingServices() {
        owner.lifecycleScope.launch {
            val providers = withContext(Dispatchers.IO) {
                try {
                    val region = binding.root.context.getSharedPreferences("zangetsu_settings", android.content.Context.MODE_PRIVATE)
                        .getString("streaming_region", "IN") ?: "IN"
                    val endpoints = listOf("/watch/providers/tv?watch_region=$region", "/watch/providers/movie?watch_region=$region")
                    val byId = linkedMapOf<Int, JSONObject>()
                    endpoints.forEach { endpoint ->
                        val results = tmdbRequest(endpoint).optJSONArray("results") ?: return@forEach
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val id = item.optInt("provider_id", -1)
                            val name = item.optString("provider_name").trim()
                            if (id <= 0 || name.isBlank() || !isSubscribableProvider(id, name)) continue
                            byId.putIfAbsent(id, item)
                        }
                    }
                    val popularIds = listOf(8, 9, 119, 337, 350, 283, 1899, 15, 531, 386, 2336, 232)
                    byId.values.sortedWith(compareBy<JSONObject> {
                        val index = popularIds.indexOf(it.optInt("provider_id"))
                        if (index < 0) popularIds.size else index
                    }.thenBy { it.optInt("display_priority", 9999) })
                } catch (_: Exception) { emptyList() }
            }
            if (providers.isNotEmpty()) renderStreamingServices(providers)
        }
    }

    private fun isSubscribableProvider(id: Int, name: String): Boolean {
        val excludedIds = setOf(2, 3, 10, 68, 192, 175, 2285)
        val n = name.lowercase().trim()
        return id !in excludedIds && !n.endsWith("channel") && !n.endsWith("store") &&
            !n.endsWith("kids") && !n.contains("with ads")
    }

    private fun renderStreamingServices(providers: List<JSONObject>) {
        val host = binding.additionalSections
        val title = TextView(binding.root.context).apply {
            text = "Streaming services"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(16), dp(26), dp(16), dp(14))
        }
        // Insert before the metadata catalogue rails, matching the Dart home
        // row placement without shifting the hero or the Recently released row.
        host.addView(title, 0, LinearLayout.LayoutParams(-1, -2))
        val scroller = android.widget.HorizontalScrollView(binding.root.context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(16), 0, dp(16), 0)
        }
        val rail = LinearLayout(binding.root.context).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(rail, ViewGroup.LayoutParams(-2, -2))
        providers.take(12).forEach { provider ->
            val name = provider.optString("provider_name")
            val logoPath = provider.optString("logo_path").takeIf { it.isNotBlank() && it != "null" }
            val tile = FrameLayout(binding.root.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(128), dp(70)).apply { marginEnd = dp(12) }
                background = rounded(18, 0xFF20212A.toInt())
                clipToOutline = true
                contentDescription = name
            }
            if (logoPath != null) {
                val logo = ImageView(binding.root.context).apply {
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    load("https://image.tmdb.org/t/p/original$logoPath") { allowHardware(false); crossfade(true) }
                }
                tile.addView(logo)
            } else {
                val label = TextView(binding.root.context).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                }
                tile.addView(label, FrameLayout.LayoutParams(-1, -1))
            }
            tile.setOnClickListener {
                Toast.makeText(binding.root.context, "$name catalogue", Toast.LENGTH_SHORT).show()
            }
            rail.addView(tile)
        }
        host.addView(scroller, 1, LinearLayout.LayoutParams(-1, -2))
    }

    private fun updateHeroMeta(item: Media?) {
        if (item == null) return
        val meta = buildList {
            item.genres.split("·").map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach { add(it.uppercase()) }
            if (item.episodes > 0) add(item.episodes.toString() + " EPISODES") else if (item.year.isNotBlank()) add(item.year)
        }
        binding.heroMeta.text = meta.joinToString("  ·  ")
    }

    private fun loadHeroLogo(item: Media?) {
        if (item == null) return
        owner.lifecycleScope.launch {
            val logo = withContext(Dispatchers.IO) { resolveTitleLogo(item) }
            if (binding.heroTitle.text?.toString() != item.title) return@launch
            if (!logo.isNullOrBlank()) {
                binding.heroLogo.load(logo) { allowHardware(false); crossfade(250) }
                binding.heroLogo.visibility = View.VISIBLE
                binding.heroTitle.visibility = View.GONE
            }
        }
    }

    /** TMDB title-logo lookup: multi-search, then localized images. */
    private fun resolveTitleLogo(item: Media): String? {
        return try {
        val query = java.net.URLEncoder.encode(item.title, "UTF-8")
        val search = tmdbRequest("/search/multi?query=" + query)
        val results = search.optJSONArray("results") ?: return null
        var id: String? = null
        var kind: String? = null
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            val mediaType = result.optString("media_type")
            if (mediaType == "movie" || mediaType == "tv") { id = result.optString("id"); kind = mediaType; break }
        }
        if (id.isNullOrBlank() || kind.isNullOrBlank()) return null
        val logos = tmdbRequest("/" + kind + "/" + id + "/images?include_image_language=en,null").optJSONArray("logos") ?: return null
        var chosen: JSONObject? = null
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            if (logo.optString("iso_639_1") == "en") { chosen = logo; break }
            if (chosen == null) chosen = logo
        }
        chosen?.optString("file_path")?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w500" + it }
        } catch (_: Exception) { null }
    }

    private fun cachePrefs() = binding.root.context.getSharedPreferences("zangetsu_metadata_cache", android.content.Context.MODE_PRIVATE)

    private fun readCachedRows(): List<Pair<String, List<Media>>> {
        return try {
            val raw = cachePrefs().getString("home_$contentKind", null) ?: return emptyList()
            val savedAt = cachePrefs().getLong("home_${contentKind}_time", 0L)
            if (System.currentTimeMillis() - savedAt > 6 * 60 * 60 * 1000L) return emptyList()
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                val items = row.optJSONArray("items") ?: return@mapNotNull null
                val media = (0 until items.length()).mapNotNull { j ->
                    val m = items.optJSONObject(j) ?: return@mapNotNull null
                    val title = m.optString("title").cleanValue()
                    val cover = m.optString("cover").cleanValue()
                    if (!title.isMeaningful() || cover.isBlank()) return@mapNotNull null
                    Media(m.optString("id"), title, cover, m.optString("banner").takeIf { it.isMeaningful() }, m.optString("score").cleanValue(), m.optString("description").cleanValue(), m.optString("year").cleanValue(), m.optString("genres").cleanValue(), m.optString("mediaType").cleanValue(), m.optInt("episodes"))
                }
                if (media.isEmpty()) null else row.optString("title") to media
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeCachedRows(rows: List<Pair<String, List<Media>>>) {
        try {
            val array = org.json.JSONArray()
            rows.forEach { (title, items) ->
                val media = org.json.JSONArray()
                items.forEach { m ->
                    media.put(JSONObject().put("id", m.id).put("title", m.title).put("cover", m.cover).put("banner", m.banner).put("score", m.score).put("description", m.description).put("year", m.year).put("genres", m.genres).put("mediaType", m.mediaType).put("episodes", m.episodes))
                }
                array.put(JSONObject().put("title", title).put("items", media))
            }
            cachePrefs().edit().putString("home_$contentKind", array.toString()).putLong("home_${contentKind}_time", System.currentTimeMillis()).apply()
        } catch (_: Exception) { }
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
                layoutParams = LinearLayout.LayoutParams(dp(116), dp(174))
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
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(2), dp(5), dp(2))
                    background = rounded(4, 0x99000000.toInt())
                }
                val badgeLp = FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END).apply {
                    topMargin = dp(6)
                    marginEnd = dp(6)
                }
                posterFrame.addView(score, badgeLp)
            }
            card.addView(posterFrame)
            val title = TextView(binding.root.context).apply {
                text = item.title
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 2
                setLineSpacing(dp(0).toFloat(), 1.3f)
                setPadding(0, dp(8), 0, 0)
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
        val rows = if (contentKind == "anime") {
            val now = java.time.LocalDate.now()
            val season = when (now.monthValue) {
                in 1..3 -> "WINTER"
                in 4..6 -> "SPRING"
                in 7..9 -> "SUMMER"
                else -> "FALL"
            }
            val (nextSeason, nextYear) = when (season) {
                "WINTER" -> "SPRING" to now.year
                "SPRING" -> "SUMMER" to now.year
                "SUMMER" -> "FALL" to now.year
                else -> "WINTER" to now.year + 1
            }
            val today = now.year * 10000 + now.monthValue * 100 + now.dayOfMonth
            listOf(
                "Recently released" to "sort:START_DATE_DESC,status_in:[RELEASING,FINISHED],popularity_greater:2000,startDate_lesser:$today",
                "Trending" to "sort:TRENDING_DESC",
                "Popular this season" to "sort:POPULARITY_DESC,season:$season,seasonYear:${now.year}",
                "Upcoming next season" to "sort:POPULARITY_DESC,season:$nextSeason,seasonYear:$nextYear,status:NOT_YET_RELEASED",
                "All-time popular" to "sort:POPULARITY_DESC",
                "Top rated" to "sort:SCORE_DESC",
                "Action" to "genre_in:[\"Action\"],sort:POPULARITY_DESC",
                "Romance" to "genre_in:[\"Romance\"],sort:POPULARITY_DESC"
            )
        } else listOf(
            "Recently released" to "sort:START_DATE_DESC,status_in:[RELEASING,FINISHED],popularity_greater:${if (contentKind == "novel") 50 else 1000}",
            "Trending" to "sort:TRENDING_DESC",
            "Popular" to "sort:POPULARITY_DESC",
            "Top rated" to "sort:SCORE_DESC"
        )
        val aliases = rows.mapIndexed { i, row ->
            "r$i: Page(perPage:30){ media(type:$type$adult$format,${row.second}){ id title{romaji english native} coverImage{large} bannerImage averageScore genres seasonYear chapters episodes status } }"
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
        // org.json serialises JSONObject.NULL as the literal text "null".
        // AniList often has no English title, and accepting that literal was
        // what produced the visible "null" labels in the native home screen.
        val title = listOf(
            titles?.optString("english"),
            titles?.optString("romaji"),
            titles?.optString("native")
        ).firstOrNull { it.isMeaningful() }.orEmpty()
        val cover = o.optJSONObject("coverImage")?.optString("large").orEmpty().cleanValue()
        if (title.isBlank() || cover.isBlank()) return null
        val genres = o.optJSONArray("genres")
        val genreText = genres?.let { a -> (0 until a.length()).map { a.optString(it) }.joinToString(" · ") }.orEmpty()
        return Media(o.optString("id"), title, cover, o.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" },
            o.optInt("averageScore").takeIf { it > 0 }?.let { "%.1f".format(it / 10.0) }.orEmpty(),
            "", o.optInt("seasonYear").takeIf { it > 0 }?.toString().orEmpty(), genreText, contentKind, o.optInt("episodes"))
    }

    private suspend fun loadTmdbRows(): List<Pair<String, List<Media>>> = coroutineScope {
        val endpoints = listOf(
            "Now playing" to "/movie/now_playing",
            "Airing now" to "/tv/on_the_air",
            "Trending" to "/trending/all/week",
            "Popular movies" to "/movie/popular",
            "Popular series" to "/tv/popular"
        )
        endpoints.map { row -> async(Dispatchers.IO) { fetchTmdbRow(row.first, row.second) } }
            .awaitAll().filterNotNull()
    }

    private fun fetchTmdbRow(label: String, path: String): Pair<String, List<Media>>? {
        val json = tmdbRequest(path)
        val results = json.optJSONArray("results") ?: return null
        val items = (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("media_type").ifBlank {
                if (path.startsWith("/tv/")) "tv" else if (path.startsWith("/movie/")) "movie" else "movie"
            }
            val title = o.optString("title").cleanValue().ifBlank { o.optString("name").cleanValue() }
            val poster = o.optString("poster_path").cleanValue()
            if (title.isBlank() || poster.isBlank()) return@mapNotNull null
            val backdrop = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w1280$it" }
            val genres = o.optJSONArray("genre_ids")
            Media(o.optString("id"), title, "https://image.tmdb.org/t/p/w500$poster", backdrop,
                o.optDouble("vote_average").takeIf { it > 0 }?.let { "%.1f".format(it) }.orEmpty(),
                o.optString("overview"), o.optString("release_date").ifBlank { o.optString("first_air_date") }.take(4),
                genres?.let { a -> (0 until a.length()).joinToString(" · ") { a.optString(it) } }.orEmpty(), type)
        }
        return if (items.isEmpty()) null else label to items
    }

    private fun aniListRequest(query: String): JSONObject {
        val body = JSONObject().put("query", query)
        val connection = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 6000
        connection.readTimeout = 9000
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
                val separator = if (path.contains("?")) "&" else "?"
                val url = "$host$path${separator}api_key=$TMDB_API_KEY&language=en-US&page=1"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
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

    private fun String?.isMeaningful(): Boolean = !this.isNullOrBlank() && !equals("null", ignoreCase = true)
    private fun String.cleanValue(): String = if (equals("null", ignoreCase = true)) "" else this

    companion object {
        // Exact TMDB v3 key embedded by the original Dart Tmdb.apiKey.
        private const val TMDB_API_KEY = "fab792d6c5936a7332045ca4565c7353"
    }
}
