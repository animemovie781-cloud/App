package com.kanha.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Full provider directory and provider-specific movie/TV catalogue. */
class StreamingServicesActivity : AppCompatActivity() {
    private data class Provider(val id: Int, val name: String, val logo: String?, val priority: Int)
    companion object {
        const val EXTRA_PROVIDER_ID = "streaming_provider_id"
        const val EXTRA_PROVIDER_NAME = "streaming_provider_name"
        private const val TMDB_API_KEY = "fab792d6c5936a7332045ca4565c7353"
    }
    private val providers = mutableListOf<Provider>()
    private lateinit var adapter: ProviderAdapter
    private lateinit var status: TextView
    private var region = "IN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        region = getSharedPreferences("zangetsu_settings", MODE_PRIVATE).getString("streaming_region", "IN") ?: "IN"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 15))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val back = TextView(this).apply {
            text = "‹"; textSize = 38f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(dp(48), dp(64))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Streaming services"; textSize = 19f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(18), 0, 0, 0)
        }
        toolbar.addView(back); toolbar.addView(title)
        status = TextView(this).apply {
            text = "Loading services…"; textSize = 13f; setTextColor(0xFFA7A7B2.toInt())
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@StreamingServicesActivity, 2)
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(24))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = ProviderAdapter()
        grid.adapter = adapter
        root.addView(toolbar)
        root.addView(status)
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val selectedId = intent.getIntExtra(EXTRA_PROVIDER_ID, -1)
        if (selectedId > 0) {
            val selectedName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: "Streaming service"
            status.text = "Loading $selectedName catalogue…"
            openProvider(Provider(selectedId, selectedName, null, 0))
        } else loadProviders()
    }

    private fun loadProviders() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val byId = linkedMapOf<Int, Provider>()
                    listOf("/watch/providers/tv", "/watch/providers/movie").forEach { path ->
                        val arr = request("$path?watch_region=$region").optJSONArray("results") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val id = item.optInt("provider_id", -1)
                            val name = item.optString("provider_name").trim()
                            if (id <= 0 || name.isBlank() || !isSubscribable(id, name)) continue
                            byId.putIfAbsent(id, Provider(
                                id, name,
                                item.optString("logo_path").takeIf { it.isNotBlank() && it != "null" },
                                item.optInt("display_priority", 9999)
                            ))
                        }
                    }
                    val popular = listOf(8, 9, 119, 337, 350, 283, 1899, 15, 531, 386, 2336, 232)
                    byId.values.sortedWith(compareBy<Provider> {
                        val index = popular.indexOf(it.id); if (index < 0) popular.size else index
                    }.thenBy { it.priority }.thenBy { it.name })
                } catch (_: Exception) { emptyList() }
            }
            providers.clear()
            providers.addAll(result)
            adapter.notifyDataSetChanged()
            status.text = if (providers.isEmpty()) "No streaming services found for $region" else "${providers.size} services available in $region"
        }
    }

    private fun isSubscribable(id: Int, name: String): Boolean {
        val excluded = setOf(2, 3, 10, 68, 192, 175, 2285)
        val n = name.lowercase().trim()
        return id !in excluded && !n.endsWith("channel") && !n.endsWith("store") &&
            !n.endsWith("kids") && !n.contains("with ads")
    }

    private fun openProvider(provider: Provider) {
        status.text = "Loading ${provider.name} catalogue…"
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val merged = linkedMapOf<String, JSONObject>()
                    listOf("movie" to "Movie", "tv" to "TV").forEach { (kind, _) ->
                        val path = "/discover/$kind?watch_region=$region&with_watch_providers=${provider.id}&watch_monetization_types=flatrate&sort_by=popularity.desc"
                        val results = request(path).optJSONArray("results") ?: JSONArray()
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val id = item.optString("id")
                            val poster = item.optString("poster_path")
                            val name = item.optString(if (kind == "movie") "title" else "name")
                            if (id.isBlank() || poster.isBlank() || name.isBlank()) continue
                            val key = "$kind:$id"
                            merged[key] = JSONObject()
                                .put("id", id).put("title", name)
                                .put("cover", "https://image.tmdb.org/t/p/w500$poster")
                                .put("score", item.optDouble("vote_average").takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "")
                                .put("mediaType", kind)
                        }
                    }
                    JSONArray().also { out -> merged.values.forEach(out::put) }.toString()
                } catch (_: Exception) { "[]" }
            }
            val count = try { JSONArray(items).length() } catch (_: Exception) { 0 }
            if (count == 0) {
                status.text = "No catalogue titles found for ${provider.name} in $region"
                Toast.makeText(this@StreamingServicesActivity, "No titles available for this service in $region", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(android.content.Intent(this@StreamingServicesActivity, SeeAllActivity::class.java)
                    .putExtra(SeeAllActivity.EXTRA_TITLE, provider.name)
                    .putExtra(SeeAllActivity.EXTRA_ITEMS, items))
                status.text = "${providers.size} services available in $region"
            }
        }
    }

    private fun request(path: String): JSONObject {
        val hosts = listOf("https://api.themoviedb.org/3", "https://api.tmdb.org/3")
        var last: Exception? = null
        hosts.forEach { host ->
            try {
                val separator = if (path.contains("?")) "&" else "?"
                val connection = URL("$host$path${separator}api_key=$TMDB_API_KEY&language=en-US&page=1").openConnection() as HttpURLConnection
                connection.connectTimeout = 6000; connection.readTimeout = 9000
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException("TMDB HTTP $code")
                return JSONObject(body)
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("TMDB unavailable")
    }

    private inner class ProviderAdapter : RecyclerView.Adapter<ProviderAdapter.Holder>() {
        inner class Holder(val tile: FrameLayout, val logo: ImageView, val label: TextView) : RecyclerView.ViewHolder(tile)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val tile = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, dp(104)).also {
                    it.marginEnd = dp(8); it.bottomMargin = dp(12)
                }
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFF20212A.toInt()) }
                clipToOutline = true; isClickable = true; isFocusable = true
            }
            val logo = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(88), dp(64), Gravity.CENTER)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(parent.context).apply {
                setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD; maxLines = 2
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setBackgroundColor(0x66000000)
                layoutParams = FrameLayout.LayoutParams(-1, dp(28), Gravity.BOTTOM)
            }
            tile.addView(logo); tile.addView(label)
            return Holder(tile, logo, label)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val provider = providers[position]
            holder.label.text = provider.name
            if (provider.logo != null) {
                holder.logo.visibility = View.VISIBLE
                holder.logo.load("https://image.tmdb.org/t/p/w185${provider.logo}") { allowHardware(false); crossfade(true) }
            } else holder.logo.visibility = View.GONE
            holder.tile.setOnClickListener { openProvider(provider) }
        }
        override fun getItemCount() = providers.size
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
