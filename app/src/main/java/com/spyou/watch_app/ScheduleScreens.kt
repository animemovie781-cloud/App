package com.kanha.dev

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val SCH_BG = 0xFF0B0B0F.toInt()
private const val SCH_CARD = 0xFF19191F.toInt()
private const val SCH_PANEL = 0xFF20212A.toInt()
private const val SCH_RED = 0xFFFF4D63.toInt()
private const val SCH_WHITE = 0xFFF7F7FA.toInt()
private const val SCH_GREY = 0xFFA4A4AE.toInt()
private const val SCH_MUTED = 0xFF74747F.toInt()

class ScheduleHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = SCH_BG
        window.navigationBarColor = SCH_BG
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SCH_BG)
            setPadding(dp(18), dp(18), dp(18), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(SCH_WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        header.addView(TextView(this).apply {
            text = "Schedule & lists"; textSize = 22f; setTextColor(SCH_WHITE)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(24), 0, 0, 0)
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "BROWSE"; textSize = 13f; letterSpacing = .08f
            setTextColor(SCH_GREY); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(28), 0, dp(16))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(24, SCH_CARD); setPadding(dp(18), dp(18), dp(18), dp(18))
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@ScheduleHubActivity, ScheduleActivity::class.java)) }
        }
        val iconBox = FrameLayout(this).apply { background = rounded(20, 0xFF22212C.toInt()) }
        iconBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_calendar_days)
            imageTintList = android.content.res.ColorStateList.valueOf(SCH_RED)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(dp(76), dp(76)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        labels.addView(TextView(this).apply {
            text = "Schedule"; textSize = 20f; setTextColor(SCH_WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = "What is airing, day by day"; textSize = 14f; setTextColor(SCH_MUTED); setPadding(0, dp(5), 0, 0)
        })
        card.addView(iconBox); card.addView(labels)
        card.addView(TextView(this).apply {
            text = "›"; textSize = 34f; setTextColor(SCH_GREY); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(30), dp(60)))
        root.addView(card)
        setContentView(root)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(r: Int, c: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }
}

class ScheduleActivity : AppCompatActivity() {
    private data class Entry(val malId: Int?, val title: String, val poster: String?, val episode: Int, val at: Long, val video: Boolean = false, val label: String = "")
    private var anime = emptyList<Entry>()
    private var video = emptyList<Entry>()
    private var day = dayStart(System.currentTimeMillis())
    private var tab = 0
    private var myList = false
    private lateinit var days: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var heading: TextView
    private lateinit var filter: TextView
    private lateinit var animeTab: TextView
    private lateinit var videoTab: TextView
    private lateinit var indicator: View
    private lateinit var loading: TextView
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = SCH_BG; window.navigationBarColor = SCH_BG
        buildScreen()
        restoreCache()
        renderDays(); renderRows(false)
        refresh()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SCH_BG) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(18), dp(8))
        }
        top.addView(TextView(this).apply {
            text = "Schedule"; textSize = 34f; setTextColor(SCH_WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        filter = TextView(this).apply {
            text = "☷  All  ⌄"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(SCH_GREY); gravity = Gravity.CENTER
            background = rounded(28, SCH_PANEL); setPadding(dp(16), dp(11), dp(16), dp(11))
            setOnClickListener { showFilter() }
        }
        top.addView(filter); root.addView(top)
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(20), dp(12), dp(20), 0) }
        animeTab = tabText("Anime", true); videoTab = tabText("Movies & TV", false)
        tabRow.addView(animeTab, LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(30) })
        tabRow.addView(videoTab, LinearLayout.LayoutParams(-2, dp(42)))
        root.addView(tabRow)
        val indicatorHost = FrameLayout(this).apply { setPadding(dp(20), 0, 0, 0) }
        indicator = View(this).apply { background = rounded(3, SCH_RED) }
        indicatorHost.addView(indicator, FrameLayout.LayoutParams(dp(55), dp(3), Gravity.START))
        root.addView(indicatorHost, LinearLayout.LayoutParams(-1, dp(5)))
        days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false
            setPadding(dp(20), dp(12), dp(12), dp(8)); addView(days)
        }, LinearLayout.LayoutParams(-1, dp(70)))
        heading = TextView(this).apply {
            textSize = 17f; setTextColor(SCH_WHITE); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        root.addView(heading)
        loading = TextView(this).apply {
            text = "Loading schedule…"; textSize = 13f; setTextColor(SCH_GREY)
            setPadding(dp(20), dp(10), dp(20), dp(10)); visibility = View.GONE
        }
        root.addView(loading)
        val scroll = ScrollView(this).apply { clipToPadding = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(28)) }
        scroll.addView(rows); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        animeTab.setOnClickListener { selectTab(0) }; videoTab.setOnClickListener { selectTab(1) }
        setContentView(root)
        tabRow.post { indicator.translationX = 0f }
    }

    private fun tabText(s: String, selected: Boolean) = TextView(this).apply {
        text = s; textSize = 16f; gravity = Gravity.CENTER_VERTICAL
        typeface = Typeface.DEFAULT_BOLD; setTextColor(if (selected) SCH_WHITE else SCH_MUTED)
    }

    private fun selectTab(value: Int) {
        if (tab == value) return
        tab = value; myList = false; filter.text = "☷  All  ⌄"
        animeTab.setTextColor(if (tab == 0) SCH_WHITE else SCH_MUTED)
        videoTab.setTextColor(if (tab == 1) SCH_WHITE else SCH_MUTED)
        indicator.animate().translationX(if (tab == 0) 0f else dp(85).toFloat()).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        filter.animate().alpha(if (tab == 0) 1f else .35f).setDuration(160).start()
        renderDays(); renderRows(true)
    }

    private fun showFilter() {
        if (tab != 0) return
        val menu = PopupMenu(this, filter)
        menu.menu.add("All"); menu.menu.add("My List")
        menu.setOnMenuItemClickListener {
            myList = it.title == "My List"
            filter.text = if (myList) "☷  My List  ⌄" else "☷  All  ⌄"
            renderDays(); renderRows(true); true
        }
        menu.show()
    }

    private fun refresh() {
        loading.visibility = if (anime.isEmpty() && tab == 0) View.VISIBLE else View.GONE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val a = runCatching { fetchAniList() }.getOrDefault(emptyList())
                val v = runCatching { fetchTmdb() }.getOrDefault(emptyList())
                a to v
            }
            anime = result.first; video = result.second
            failed = anime.isEmpty() && video.isEmpty()
            saveCache(); loading.visibility = View.GONE
            renderDays(); renderRows(true)
        }
    }

    private fun fetchAniList(): List<Entry> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis / 1000L
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val end = cal.timeInMillis / 1000L
        val query = "query(\$start:Int,\$end:Int,\$page:Int){Page(page:\$page,perPage:50){pageInfo{hasNextPage} airingSchedules(airingAt_greater:\$start,airingAt_lesser:\$end,sort:TIME){episode airingAt media{idMal isAdult title{english romaji} coverImage{large}}}}}"
        val result = mutableListOf<Entry>()
        for (page in 1..5) {
            val body = JSONObject().put("query", query).put("variables", JSONObject().put("start", start).put("end", end).put("page", page))
            val c = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 4500; c.readTimeout = 6500; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Origin", "https://anilist.co"); c.setRequestProperty("Referer", "https://anilist.co/")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val json = JSONObject(stream.bufferedReader().use { it.readText() }); c.disconnect()
            val pageData = json.optJSONObject("data")?.optJSONObject("Page") ?: break
            val schedules = pageData.optJSONArray("airingSchedules") ?: break
            for (i in 0 until schedules.length()) {
                val item = schedules.optJSONObject(i) ?: continue
                val media = item.optJSONObject("media") ?: continue
                if (media.optBoolean("isAdult")) continue
                val titles = media.optJSONObject("title")
                val en = titles?.optString("english").orEmpty()
                val title = if (en.isNotBlank() && en != "null") en else titles?.optString("romaji").orEmpty()
                val at = item.optLong("airingAt")
                if (title.isBlank() || at <= 0L) continue
                result.add(Entry(if (media.isNull("idMal")) null else media.optInt("idMal"), title,
                    media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() && it != "null" },
                    item.optInt("episode"), at))
            }
            if (pageData.optJSONObject("pageInfo")?.optBoolean("hasNextPage") != true) break
        }
        return result.distinctBy { it.malId.toString() + ":" + it.episode + ":" + it.at }.sortedBy { it.at }
    }

    private fun fetchTmdb(): List<Entry> {
        val key = "fab792d6c5936a7332045ca4565c7353"
        val out = mutableListOf<Entry>()
        listOf("/movie/upcoming" to "movie", "/tv/on_the_air" to "tv").forEach { pair ->
            val c = URL("https://api.themoviedb.org/3" + pair.first + "?api_key=" + key + "&language=en-US&page=1").openConnection() as HttpURLConnection
            c.connectTimeout = 4500; c.readTimeout = 6000
            val json = JSONObject((if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }); c.disconnect()
            val array = json.optJSONArray("results") ?: return@forEach
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: o.optString("name")
                val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
                val millis = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: 0L }.getOrDefault(0L)
                if (title.isBlank() || millis <= 0L) continue
                val poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342" + it }
                out.add(Entry(null, title, poster, 0, millis / 1000L, true, if (pair.second == "movie") "Movie" else "TV"))
            }
        }
        val start = dayStart(System.currentTimeMillis()) / 1000L
        return out.filter { it.at >= start && it.at < start + 7 * 86400L }.distinctBy { it.title + it.at }.sortedBy { it.at }
    }

    private fun restoreCache() {
        val p = getSharedPreferences("schedule_cache", MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("time", 0L) > 6 * 60 * 60 * 1000L) return
        fun read(key: String): List<Entry> = runCatching {
            val a = org.json.JSONArray(p.getString(key, "[]"))
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                Entry(if (o.isNull("id")) null else o.optInt("id"), o.optString("title"), o.optString("poster").takeIf { it.isNotBlank() },
                    o.optInt("episode"), o.optLong("at"), o.optBoolean("video"), o.optString("label"))
            }
        }.getOrDefault(emptyList())
        anime = read("anime"); video = read("video")
    }

    private fun saveCache() {
        fun encode(list: List<Entry>) = org.json.JSONArray().apply {
            list.forEach { put(JSONObject().put("id", it.malId).put("title", it.title).put("poster", it.poster).put("episode", it.episode).put("at", it.at).put("video", it.video).put("label", it.label)) }
        }.toString()
        getSharedPreferences("schedule_cache", MODE_PRIVATE).edit()
            .putString("anime", encode(anime)).putString("video", encode(video))
            .putLong("time", System.currentTimeMillis()).apply()
    }

    private fun currentAnime(): List<Entry> {
        if (!myList) return anime
        val followed = getSharedPreferences("zangetsu_tracker", MODE_PRIVATE)
            .getStringSet("followed_anime_titles", emptySet()).orEmpty().map { normalize(it) }.toSet()
        return anime.filter { normalize(it.title) in followed }
    }

    private fun renderDays() {
        days.removeAllViews()
        val source = if (tab == 0) currentAnime() else video
        val counts = source.groupingBy { dayStart(it.at * 1000L) }.eachCount()
        val today = dayStart(System.currentTimeMillis())
        for (i in 0..6) {
            val value = today + i * 86400000L
            val label = if (i == 0) "Today" else SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(value))
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(2), dp(18), dp(2)); isClickable = true
                setOnClickListener { day = value; renderDays(); renderRows(true) }
            }
            chip.addView(TextView(this).apply {
                text = label + "  (" + (counts[value] ?: 0) + ")"; textSize = 14f
                typeface = Typeface.DEFAULT_BOLD; setTextColor(if (day == value) SCH_WHITE else SCH_MUTED)
            })
            chip.addView(View(this).apply { background = rounded(3, if (day == value) SCH_RED else Color.TRANSPARENT) },
                LinearLayout.LayoutParams(dp(32), dp(3)).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
            days.addView(chip)
        }
    }

    private fun renderRows(animate: Boolean) {
        rows.removeAllViews()
        val source = if (tab == 0) currentAnime() else video
        val items = source.filter { dayStart(it.at * 1000L) == day }.sortedBy { it.at }
        val dayName = if (day == dayStart(System.currentTimeMillis())) "Today" else SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(day))
        heading.text = dayName + "  ·  " + items.size + if (tab == 0) " airing" else " releases"
        if (items.isEmpty()) {
            val message = when {
                myList -> "No followed anime airing on this day."
                failed -> "Couldn't load the schedule. Check your connection and tap to retry."
                tab == 1 -> "No movie or TV releases scheduled for this day."
                else -> "No anime episodes airing on this day."
            }
            rows.addView(TextView(this).apply {
                text = message + if (failed) "\n\nTap to retry" else ""
                textSize = 14f; setTextColor(SCH_GREY); gravity = Gravity.CENTER
                setPadding(dp(34), dp(34), dp(34), dp(34)); if (failed) setOnClickListener { refresh() }
            })
        } else {
            var previous = ""
            items.forEachIndexed { index, item ->
                val hour = Calendar.getInstance().apply { timeInMillis = item.at * 1000L }.get(Calendar.HOUR_OF_DAY)
                val slot = when { hour < 5 || hour >= 21 -> "LATE NIGHT"; hour < 12 -> "MORNING"; hour < 17 -> "AFTERNOON"; else -> "EVENING" }
                if (slot != previous) {
                    rows.addView(TextView(this).apply {
                        text = slot; textSize = 10f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(SCH_MUTED); setPadding(dp(32), dp(if (index == 0) 4 else 18), dp(20), dp(8))
                    }); previous = slot
                }
                rows.addView(makeRow(item, index == items.lastIndex))
            }
        }
        if (animate) {
            rows.alpha = 0f; rows.translationY = dp(12).toFloat()
            rows.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun makeRow(item: Entry, last: Boolean): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(dp(18), dp(3), dp(16), dp(3)) }
        val date = Date(item.at * 1000L)
        val rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutParams = LinearLayout.LayoutParams(dp(42), -1) }
        rail.addView(TextView(this).apply { text = SimpleDateFormat("h:mm", Locale.getDefault()).format(date); textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SCH_GREY) })
        rail.addView(TextView(this).apply { text = SimpleDateFormat("a", Locale.getDefault()).format(date); textSize = 8f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SCH_MUTED) })
        if (!last) rail.addView(View(this).apply { setBackgroundColor(0xFF292932.toInt()) }, LinearLayout.LayoutParams(dp(1), dp(58)).apply { topMargin = dp(7) })
        row.addView(rail)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(18, SCH_CARD); setPadding(dp(10), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(76), 1f).apply { marginStart = dp(10); bottomMargin = dp(8) }
            isClickable = true; isFocusable = true; setOnClickListener { openItem(item) }
        }
        val poster = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(58)); scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(8, SCH_PANEL); clipToOutline = true
            item.poster?.let { load(it) { crossfade(180); allowHardware(false) } }
        }
        card.addView(poster)
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(6), 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        text.addView(TextView(this).apply {
            this.text = item.title; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SCH_WHITE)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        text.addView(TextView(this).apply {
            this.text = if (item.video) item.label else "Episode " + item.episode
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SCH_MUTED); setPadding(0, dp(3), 0, 0)
        })
        card.addView(text)
        val aired = item.at * 1000L < System.currentTimeMillis()
        card.addView(TextView(this).apply {
            this.text = if (aired) "Aired" else countdown(item.at * 1000L)
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SCH_RED); gravity = Gravity.CENTER; maxLines = 1
        })
        row.addView(card)
        row.alpha = 0f; row.animate().alpha(1f).setStartDelay(18L).setDuration(180L).start()
        return row
    }

    private fun openItem(item: Entry) {
        if (item.video || item.malId == null) return
        val payload = JSONObject().put("id", "mal:" + item.malId).put("title", item.title)
            .put("cover", item.poster ?: "").put("mediaType", "ANIME")
        startActivity(DetailActivity.intent(this, payload))
    }

    private fun countdown(at: Long): String {
        val mins = max(0L, (at - System.currentTimeMillis()) / 60000L)
        return if (mins < 60) mins.toString() + "m" else if (mins < 1440) (mins / 60).toString() + "h " + (mins % 60) + "m" else (mins / 1440).toString() + "d " + (mins / 60 % 24) + "h"
    }
    private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    private fun rounded(r: Int, c: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
