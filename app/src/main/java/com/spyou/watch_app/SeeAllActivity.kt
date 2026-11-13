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
        val options = arrayOf("Both", "Movies", "TV Shows")
        AlertDialog.Builder(this).setTitle("Filter catalogue")
            .setSingleChoiceItems(options, options.indexOf(selectedFormat)) { dialog, which ->
                selectedFormat = options[which]; dialog.dismiss(); resetAndLoad()
            }.setNegativeButton("Cancel", null).show()
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
                val path = if (query.isBlank()) "/discover/$kind?watch_region=$region&with_watch_providers=$providerId&watch_monetization_types=flatrate&sort_by=popularity.desc&page=$requestPage"
                    else "/search/$kind?query=${URLEncoder.encode(query, "UTF-8")}&page=$requestPage"
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
                clipToOutline = true; layoutParams = LinearLayout.LayoutParams(-1, dp(296))
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
