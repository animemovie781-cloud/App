package com.kanha.dev

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Three-column, infinite-scroll catalogue grid ported from Dart SeeAllScreen. */
class SeeAllActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TITLE = "see_all_title"
        const val EXTRA_ITEMS = "see_all_items"
        const val EXTRA_PROVIDER_ID = "see_all_provider_id"
        const val EXTRA_REGION = "see_all_region"
        private const val API_KEY = "fab792d6c5936a7332045ca4565c7353"
    }
    private data class Entry(val id: String, val title: String, val cover: String, val score: String, val mediaType: String)
    private val items = mutableListOf<Entry>()
    private val seen = hashSetOf<String>()
    private lateinit var adapter: PosterGridAdapter
    private lateinit var grid: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private var providerId = -1
    private var region = "IN"
    private var page = 1
    private var loadingPage = false
    private var endReached = false
    private var query = ""
    private var selectedFormat = "Both"
    private var selectedSort = "Popular"
    private var selectedYear = "Any"
    private var selectedMinScore = "Any"
    private var selectedGenre = "Any"
    private val genreOptions = linkedMapOf("Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99, "Drama" to 18, "Family" to 10751, "Fantasy" to 14, "History" to 36, "Horror" to 27, "Music" to 10402, "Mystery" to 9648, "Romance" to 10749, "Science Fiction" to 878, "TV Movie" to 10770, "Thriller" to 53, "War" to 10752, "Western" to 37)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providerId = intent.getIntExtra(EXTRA_PROVIDER_ID, -1)
        region = intent.getStringExtra(EXTRA_REGION) ?: getSharedPreferences("zangetsu_settings", MODE_PRIVATE).getString("streaming_region", "IN").orEmpty().ifBlank { "IN" }
        items.addAll(parseItems(intent.getStringExtra(EXTRA_ITEMS).orEmpty()))
        items.forEach { seen.add(key(it)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11,11,15)) }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(64))
        }
        val back = iconButton(R.drawable.ic_browse_back, "Back") { finish() }
        val title = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE).orEmpty(); textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(18), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { gravity = Gravity.CENTER_VERTICAL }
        }
        val search = iconButton(R.drawable.ic_browse_search, "Search") { showSearch() }
        val filter = iconButton(R.drawable.ic_browse_filter, "Filter") { showFilters() }
        toolbar.addView(back); toolbar.addView(title); toolbar.addView(search); toolbar.addView(filter)
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@SeeAllActivity, 3)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val position = parent.getChildAdapterPosition(view)
                    val column = position % 3
                    outRect.left = dp(if (column == 0) 0 else 5)
                    outRect.right = dp(if (column == 2) 0 else 5)
                    outRect.bottom = dp(24)
                }
            })
            clipToPadding = false; setPadding(dp(16), dp(10), dp(16), dp(20))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = PosterGridAdapter()
        grid.adapter = adapter
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val lm = rv.layoutManager as GridLayoutManager
                val last = lm.findLastVisibleItemPosition()
                if (last >= items.size - 6) loadNextPage()
            }
        })
        val footer = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(44)) }
        loading = ProgressBar(this).apply { visibility = View.GONE; isIndeterminate = true }
        footer.addView(loading, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        empty = TextView(this).apply { text = "No titles found"; setTextColor(0xFFA7A7B2.toInt()); textSize = 14f; gravity = Gravity.CENTER; visibility = View.GONE }
        root.addView(toolbar); root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(footer)
        root.addView(empty, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        if (items.isEmpty() && providerId > 0) loadNextPage()
        if (providerId <= 0) endReached = true
    }

    private fun iconButton(drawable: Int, label: String, action: () -> Unit) = ImageView(this).apply {
        setImageResource(drawable); contentDescription = label; scaleType = ImageView.ScaleType.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(52))
        setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { action() }
    }

    private fun showSearch() {
        val field = EditText(this).apply {
            hint = "Search titles"; setTextColor(Color.WHITE); setHintTextColor(0xFF888891.toInt())
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        AlertDialog.Builder(this).setTitle("Search").setView(field)
            .setPositiveButton("Search") { _, _ -> query = field.text.toString().trim(); resetAndLoad() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showFilters() {
        val sheetDialog = BottomSheetDialog(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(24)); setBackgroundColor(Color.rgb(11, 11, 15)) }
        content.addView(View(this).apply { background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF292832.toInt()) }; layoutParams = LinearLayout.LayoutParams(dp(56), dp(5)).apply { gravity = Gravity.CENTER; bottomMargin = dp(20) } })
        content.addView(TextView(this).apply { text = "Filters"; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(-1, dp(48)) })
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun choice(label: String, options: List<String>, current: String, save: (String) -> Unit) {
            AlertDialog.Builder(this@SeeAllActivity).setTitle(label).setSingleChoiceItems(options.toTypedArray(), options.indexOf(current).coerceAtLeast(0)) { d, which -> save(options[which]); d.dismiss(); sheetDialog.dismiss(); showFilters() }.setNegativeButton("Cancel", null).show()
        }
        fun field(label: String, value: String, action: () -> Unit): View {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(9), dp(14), dp(9)); background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0xFF19181F.toInt()) }; isClickable = true; isFocusable = true; setOnClickListener { action() } }
            box.addView(TextView(this).apply { text = label.uppercase(); textSize = 11f; setTextColor(0xFFA7A7B2.toInt()); typeface = Typeface.DEFAULT_BOLD })
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(TextView(this).apply { text = value; textSize = 16f; setTextColor(if (value == "Any" || value == "Popular" || value == "Both") Color.WHITE else 0xFFFF4D57.toInt()); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            line.addView(TextView(this).apply { text = "⌄"; textSize = 22f; setTextColor(0xFFA7A7B2.toInt()) })
            box.addView(line); return box
        }
        fun pair(left: View?, right: View?) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(left ?: View(this), LinearLayout.LayoutParams(0, dp(96), 1f).apply { marginEnd = dp(8) })
            row.addView(right ?: View(this), LinearLayout.LayoutParams(0, dp(96), 1f).apply { marginStart = dp(8) })
            fields.addView(row)
        }
        pair(field("Sort by", selectedSort) { choice("Sort by", listOf("Popular", "Top rated", "Newest", "Oldest", "Title A–Z"), selectedSort) { selectedSort = it } }, field("Year", selectedYear) { choice("Year", listOf("Any") + (2026 downTo 1970).map { it.toString() }, selectedYear) { selectedYear = it } })
        pair(null, field("Minimum score", selectedMinScore) { choice("Minimum score", listOf("Any", "5.0+", "6.0+", "7.0+", "8.0+", "9.0+"), selectedMinScore) { selectedMinScore = it } })
        pair(null, field("Format", selectedFormat) { choice("Format", listOf("Both", "Movies", "TV Shows"), selectedFormat) { selectedFormat = it } })
        fields.addView(field("Genres", selectedGenre) { choice("Genres", listOf("Any") + genreOptions.keys.toList(), selectedGenre) { selectedGenre = it } }, LinearLayout.LayoutParams(-1, dp(96)).apply { topMargin = dp(8) })
        content.addView(fields)
        content.addView(TextView(this).apply { text = "Apply"; gravity = Gravity.CENTER; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFFFF3155.toInt()) }; layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(30) }; setOnClickListener { sheetDialog.dismiss(); resetAndLoad() } })
        sheetDialog.setContentView(content)
        sheetDialog.setOnShowListener {
            val sheet = sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            sheet.layoutParams.height = (resources.displayMetrics.heightPixels * 0.94f).toInt()
            val behavior = BottomSheetBehavior.from(sheet); behavior.isFitToContents = false; behavior.halfExpandedRatio = 0.62f; behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED; behavior.skipCollapsed = false; behavior.isDraggable = true
        }
        sheetDialog.show()
    }
    private fun resetAndLoad() {
        page = 0; endReached = false; loadingPage = false; items.clear(); seen.clear(); adapter.notifyDataSetChanged()
        loadNextPage()
    }

    private fun loadNextPage() {
        if (providerId <= 0 || loadingPage || endReached) return
        loadingPage = true; loading.visibility = View.VISIBLE; empty.visibility = View.GONE
        val nextPage = page + 1
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchProviderPage(nextPage) }
            loadingPage = false; loading.visibility = View.GONE
            val fresh = result.first.filter { seen.add(key(it)) }
            if (fresh.isEmpty()) {
                endReached = true
                if (items.isEmpty()) empty.visibility = View.VISIBLE
            } else {
                val start = items.size; items.addAll(fresh); page = nextPage
                adapter.notifyItemRangeInserted(start, fresh.size)
            }
            if (!result.second) endReached = true
        }
    }

    private fun fetchProviderPage(requestPage: Int): Pair<List<Entry>, Boolean> {
        return try {
            val types = when (selectedFormat) { "Movies" -> listOf("movie"); "TV Shows" -> listOf("tv"); else -> listOf("movie", "tv") }
            val all = mutableListOf<Entry>()
            var hasMore = false
            types.forEach { kind ->
                val sort = when (selectedSort) {
                    "Top rated" -> "vote_average.desc"
                    "Newest" -> if (kind == "movie") "primary_release_date.desc" else "first_air_date.desc"
                    "Oldest" -> if (kind == "movie") "primary_release_date.asc" else "first_air_date.asc"
                    "Title A–Z" -> if (kind == "movie") "original_title.asc" else "original_name.asc"
                    else -> "popularity.desc"
                }
                val filters = StringBuilder()
                if (selectedYear != "Any") filters.append("&").append(if (kind == "movie") "primary_release_year" else "first_air_date_year").append("=").append(selectedYear)
                if (selectedMinScore != "Any") filters.append("&vote_average.gte=").append(selectedMinScore.removeSuffix("+"))
                genreOptions[selectedGenre]?.let { filters.append("&with_genres=").append(it) }
                val path = if (query.isBlank()) "/discover/" + kind + "?watch_region=" + region + "&with_watch_providers=" + providerId + "&watch_monetization_types=flatrate&sort_by=" + sort + "&page=" + requestPage + filters.toString()
                    else "/search/" + kind + "?query=" + URLEncoder.encode(query, "UTF-8") + "&page=" + requestPage
                val json = request(path)
                val results = json.optJSONArray("results") ?: JSONArray()
                val totalPages = json.optInt("total_pages", 1)
                if (requestPage < totalPages) hasMore = true
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val poster = obj.optString("poster_path")
                    val name = obj.optString(if (kind == "movie") "title" else "name")
                    if (id.isBlank() || poster.isBlank() || name.isBlank() || poster == "null") continue
                    all.add(Entry("$kind:$id", name, "https://image.tmdb.org/t/p/w500$poster",
                        obj.optDouble("vote_average").takeIf { it > 0 }?.let { "%.1f".format(it) }.orEmpty(), kind))
                }
            }
            all.sortedByDescending { it.score.toDoubleOrNull() ?: 0.0 } to hasMore
        } catch (_: Exception) { emptyList<Entry>() to false }
    }

    private fun request(path: String): JSONObject {
        var failure: Exception? = null
        listOf("https://api.themoviedb.org/3", "https://api.tmdb.org/3").forEach { host ->
            try {
                val sep = if (path.contains("?")) "&" else "?"
                val c = URL("$host$path${sep}api_key=$API_KEY&language=en-US").openConnection() as HttpURLConnection
                c.connectTimeout = 7000; c.readTimeout = 10000; c.setRequestProperty("Accept", "application/json")
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val body = stream.bufferedReader().use { it.readText() }; c.disconnect()
                if (code !in 200..299) throw IllegalStateException("TMDB HTTP $code")
                return JSONObject(body)
            } catch (e: Exception) { failure = e }
        }
        throw failure ?: IllegalStateException("TMDB unavailable")
    }

    private fun parseItems(raw: String): List<Entry> = try {
        val a = JSONArray(raw); (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title"); val cover = o.optString("cover")
            if (title.isBlank() || cover.isBlank() || cover == "null") null
            else Entry(o.optString("id", "$i"), title, cover, o.optString("score").takeIf { it != "null" }.orEmpty(), o.optString("mediaType", "movie"))
        }
    } catch (_: Exception) { emptyList() }

    private fun key(e: Entry) = if (e.id.isNotBlank()) e.id else e.cover

    private inner class PosterGridAdapter : RecyclerView.Adapter<PosterGridAdapter.Holder>() {
        inner class Holder(val card: LinearLayout, val image: ImageView, val score: TextView, val name: TextView) : RecyclerView.ViewHolder(card)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2) }
            val frame = FrameLayout(parent.context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF20212A.toInt()) }
                clipToOutline = true; layoutParams = LinearLayout.LayoutParams(-1, dp(148))
            }
            val image = ImageView(parent.context).apply { layoutParams = FrameLayout.LayoutParams(-1, -1); scaleType = ImageView.ScaleType.CENTER_CROP }
            val score = TextView(parent.context).apply {
                textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFFFFD54F.toInt())
                setPadding(dp(5), dp(3), dp(5), dp(3))
                background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(0xCC101014.toInt()) }
            }
            frame.addView(image); frame.addView(score, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(7); marginEnd = dp(7) })
            val name = TextView(parent.context).apply {
                setTextColor(Color.WHITE); textSize = 14f; maxLines = 2
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(8), 0, 0)
            }
            card.addView(frame); card.addView(name)
            return Holder(card, image, score, name)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.image.load(item.cover) { allowHardware(false); crossfade(true) }
            holder.name.text = item.title
            holder.score.visibility = if (item.score.isBlank()) View.GONE else View.VISIBLE
            holder.score.text = "★ " + item.score
        }
        override fun getItemCount() = items.size
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
