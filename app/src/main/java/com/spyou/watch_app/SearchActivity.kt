package com.kanha.dev

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.view.View
import android.widget.ScrollView
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import coil.load
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.addTextChangedListener

/** Native search entry screen following lib/features/home/search_screen.dart. */
class SearchActivity : AppCompatActivity() {
    private lateinit var query: EditText
    private lateinit var clear: ImageButton
    private lateinit var emptyLabel: TextView
    private val voiceRequest = 4207
    private lateinit var resultsHost: LinearLayout
    private lateinit var emptyIcon: ImageView
    private lateinit var progress: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchGeneration = 0
    private var pendingSearch: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(11, 11, 15)
        window.navigationBarColor = Color.rgb(11, 11, 15)
        setContentView(createContent())
    }

    private fun createContent(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 15))
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.search_back_arrow); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Back"
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(52))
            setOnClickListener { finish() }
        }
        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = pill(0xFF20212A.toInt(), 50); setPadding(dp(16), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) }
        }
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.search_field_icon); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)); contentDescription = "Search"
        }
        query = EditText(this).apply {
            hint = "Search movies & TV"; setHintTextColor(0xFFA7A7B2.toInt())
            setTextColor(Color.WHITE); textSize = 16f; setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH; setPadding(dp(14), 0, dp(4), 0)
            background = null; layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); runSearch(query.text.toString()); true } else false
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clear.visibility = if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                    if (s.isNullOrBlank()) { pendingSearch?.let(mainHandler::removeCallbacks); searchGeneration++; showIdle() } else { pendingSearch?.let(mainHandler::removeCallbacks); val value=s.toString(); pendingSearch=Runnable { runSearch(value) }; mainHandler.postDelayed(pendingSearch!!,450) }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        clear = ImageButton(this).apply {
            setImageResource(R.drawable.search_clear_close); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(36))
            visibility = android.view.View.GONE; contentDescription = "Clear search"
            setOnClickListener { query.text?.clear(); query.requestFocus() }
        }
        val mic = ImageButton(this).apply {
            setImageResource(R.drawable.search_mic); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(36))
            contentDescription = "Voice search"; setOnClickListener { startVoiceSearch() }
        }
        field.addView(searchIcon); field.addView(query); field.addView(clear); field.addView(mic)
        val filter = ImageButton(this).apply {
            setImageResource(R.drawable.search_filter_tune); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Search filters"
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) }
            setOnClickListener { showSearchOptions() }
        }
        top.addView(back); top.addView(field); top.addView(filter); root.addView(top)

        root.addView(android.view.View(this).apply {
            setBackgroundColor(0xFF1B1A20.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        val contentFrame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER) }
        emptyIcon = ImageView(this).apply { setImageResource(R.drawable.search_empty_state); scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(22) }; contentDescription = "Search" }
        emptyLabel = TextView(this).apply { text = "Search for something to watch"; setTextColor(0xFFA7A7B2.toInt()); textSize = 16f; gravity = Gravity.CENTER; letterSpacing = 0.015f }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        center.addView(emptyIcon); center.addView(progress); center.addView(emptyLabel); contentFrame.addView(center)
        val scroll = ScrollView(this).apply { visibility = View.GONE; isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        resultsHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(28)) }
        scroll.addView(resultsHost); contentFrame.addView(scroll); root.addView(contentFrame)
        return root
    }

    private fun showIdle() { val frame=resultsHost.parent?.parent as? FrameLayout ?: return; frame.getChildAt(1).visibility=View.GONE; emptyIcon.visibility=View.VISIBLE; progress.visibility=View.GONE; emptyLabel.text="Search for something to watch"; resultsHost.removeAllViews() }
    private fun showStatus(message:String,loading:Boolean=false) { val frame=resultsHost.parent?.parent as? FrameLayout ?: return; frame.getChildAt(1).visibility=View.GONE; emptyIcon.visibility=if(loading) View.GONE else View.VISIBLE; progress.visibility=if(loading) View.VISIBLE else View.GONE; emptyLabel.text=message }
    private fun runSearch(raw:String) { val q=raw.trim(); if(q.length<2){showIdle();return}; val requestId=++searchGeneration; showStatus("Searching…",true); Thread { try { val items=if(intent.getStringExtra("content_mode")=="Anime") searchAniList(q) else searchTmdb(q); runOnUiThread { if(isFinishing || requestId!=searchGeneration)return@runOnUiThread; if(items.isEmpty()) showStatus("No results found for “$q”") else renderSearchResults(items) } } catch(e:Exception) { runOnUiThread { if(!isFinishing && requestId==searchGeneration) showStatus(e.message ?: "Search failed. Check your internet connection.") } } }.start() }
    private fun searchAniList(q:String):List<JSONObject> {
        val gql="query(\$search:String!){Page(page:1,perPage:30){media(search:\$search,type:ANIME,isAdult:false,sort:POPULARITY_DESC){id title{english romaji native} coverImage{large} description(asHtml:false) seasonYear episodes status format genres averageScore}}}"
        val payload=JSONObject().put("query",gql).put("variables",JSONObject().put("search",q)); val arr=postJson("https://graphql.anilist.co",payload).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i -> val o=arr.optJSONObject(i) ?: return@mapNotNull null; val names=o.optJSONObject("title"); val title=listOf("english","romaji","native").map{names?.optString(it).orEmpty()}.firstOrNull{it.isNotBlank()&&it!="null"} ?: return@mapNotNull null; val cover=o.optJSONObject("coverImage")?.optString("large").orEmpty(); if(cover.isBlank()||cover=="null")return@mapNotNull null; val genres=o.optJSONArray("genres")?.let{a->(0 until a.length()).map{a.optString(it)}.joinToString(" · ")}.orEmpty(); JSONObject().put("id",o.optString("id")).put("title",title).put("cover",cover).put("description",o.optString("description")).put("year",o.optInt("seasonYear").takeIf{it>0}?.toString().orEmpty()).put("genres",genres).put("mediaType","anime").put("episodes",o.optInt("episodes")).put("score",o.optInt("averageScore").takeIf{it>0}?.let{"%.1f".format(it/10.0)}.orEmpty()) }
    }
    private fun searchTmdb(q:String):List<JSONObject> { val encoded=URLEncoder.encode(q,"UTF-8"); val url="https://api.themoviedb.org/3/search/multi?query=$encoded&include_adult=false&language=en-US&page=1&api_key=${TmdbConfig.API_KEY}"; val arr=getJson(url).optJSONArray("results") ?: JSONArray(); return (0 until arr.length()).mapNotNull { i -> val o=arr.optJSONObject(i) ?: return@mapNotNull null; val type=o.optString("media_type"); if(type!="movie"&&type!="tv")return@mapNotNull null; val title=o.optString("title").ifBlank{o.optString("name")}; val poster=o.optString("poster_path"); if(title.isBlank()||poster.isBlank()||poster=="null")return@mapNotNull null; val year=o.optString("release_date").ifBlank{o.optString("first_air_date")}.take(4); JSONObject().put("id",o.optString("id")).put("title",title).put("cover","https://image.tmdb.org/t/p/w500$poster").put("description",o.optString("overview")).put("year",year).put("genres","").put("mediaType",type).put("episodes",0).put("score",o.optDouble("vote_average").takeIf{it>0}?.let{"%.1f".format(it)}.orEmpty()) } }
    private fun renderSearchResults(items:List<JSONObject>) { val frame=resultsHost.parent?.parent as? FrameLayout ?: return; frame.getChildAt(1).visibility=View.VISIBLE; emptyIcon.visibility=View.GONE; progress.visibility=View.GONE; emptyLabel.text=""; resultsHost.removeAllViews(); items.forEach { item -> val card=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(10),dp(10),dp(10));background=pill(0xFF1B1B23.toInt(),14);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)};isClickable=true;isFocusable=true}; val poster=ImageView(this).apply{layoutParams=LinearLayout.LayoutParams(dp(76),dp(108));scaleType=ImageView.ScaleType.CENTER_CROP;load(item.optString("cover")){crossfade(true);allowHardware(false)}}; card.addView(poster); val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(2),dp(4),dp(2));layoutParams=LinearLayout.LayoutParams(0,-2,1f)}; info.addView(TextView(this).apply{text=item.optString("title");setTextColor(Color.WHITE);textSize=16f;typeface=android.graphics.Typeface.DEFAULT_BOLD;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}); val meta=listOf(item.optString("year"),item.optString("mediaType").uppercase(),item.optString("score").takeIf{it.isNotBlank()}?.let{"★ $it"}.orEmpty()).filter{it.isNotBlank()}.joinToString("  ·  "); info.addView(TextView(this).apply{text=meta;setTextColor(0xFFAAAAB4.toInt());textSize=12f;setPadding(0,dp(6),0,dp(5))}); val overview=item.optString("description").replace(Regex("<[^>]*>"),"").trim(); if(overview.isNotBlank())info.addView(TextView(this).apply{text=overview;setTextColor(0xFFAAAAB4.toInt());textSize=12f;maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END});card.addView(info);card.setOnClickListener{startActivity(DetailActivity.intent(this,item))};resultsHost.addView(card) } }
    private fun getJson(address:String):JSONObject { val c=URL(address).openConnection() as HttpURLConnection;c.connectTimeout=8000;c.readTimeout=12000;c.setRequestProperty("Accept","application/json");try{val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val body=stream.bufferedReader().use{it.readText()};if(code !in 200..299)throw IllegalStateException("Search request failed (HTTP $code)");return JSONObject(body)}finally{c.disconnect()} }
    private fun postJson(address:String,payload:JSONObject):JSONObject { val c=URL(address).openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=8000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");try{c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val body=stream.bufferedReader().use{it.readText()};if(code !in 200..299)throw IllegalStateException("Anime search failed (HTTP $code)");return JSONObject(body)}finally{c.disconnect()} }
    override fun onDestroy(){pendingSearch?.let(mainHandler::removeCallbacks);searchGeneration++;super.onDestroy()}

    private fun showSearchOptions() {
        val prefs = getSharedPreferences("search_filters", MODE_PRIVATE)
        val yearNow = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val isAnime = intent.getStringExtra("content_mode") == "Anime"
        val defaults = linkedMapOf("sort" to "Popular", "year" to "Any", "status" to "Any", "score" to "Any", "season" to "Any", "format" to "Any", "genres" to "Any")
        val options = linkedMapOf(
            "sort" to listOf("Popular", "Score", "Trending", "Newest", "Title"),
            "year" to (yearNow downTo yearNow - 24).map { it.toString() },
            "status" to if (isAnime) listOf("Releasing", "Finished", "Not yet released", "Cancelled") else listOf("Returning Series", "Ended", "In Production", "Canceled"),
            "score" to listOf("50+", "60+", "70+", "80+", "90+"),
            "season" to listOf("Winter", "Spring", "Summer", "Fall"),
            "format" to if (isAnime) listOf("TV", "Movie", "OVA", "Special") else listOf("Movie", "TV"),
            "genres" to if (isAnime) listOf("Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller") else listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western")
        )
        val selected = defaults.mapValues { (key, value) -> prefs.getString(key, value) ?: value }.toMutableMap()
        val genres = selected["genres"]?.split(", ")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        if (selected["genres"] == "Any") genres.clear()

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF0B0B0F.toInt())
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        panel.addView(android.view.View(this).apply {
            background = GradientDrawable().apply { setColor(0xFF292833.toInt()); cornerRadius = dp(3).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER; bottomMargin = dp(14) }
        })
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, dp(42)) }
        heading.addView(TextView(this).apply {
            text = "Filters"; setTextColor(Color.WHITE); textSize = 20f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        heading.addView(TextView(this).apply {
            text = "Reset"; setTextColor(0xFFFF4D57.toInt()); textSize = 14f
            visibility = if (selected.any { (k,v) -> v != defaults[k] }) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener { prefs.edit().clear().apply(); sheet.dismiss(); showSearchOptions() }
        })
        panel.addView(heading)

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
        fun makeCell(key: String, label: String): LinearLayout {
            val value = if (key == "genres") { if (genres.isEmpty()) "Any" else genres.joinToString(", ") } else selected[key] ?: "Any"
            val active = if (key == "genres") genres.isNotEmpty() else value != defaults[key]
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(12), dp(9))
                background = GradientDrawable().apply {
                    setColor(0xFF19181F.toInt()); cornerRadius = dp(16).toFloat()
                    if (active) setStroke(dp(1), 0x99FF4D57.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(88), 1f).apply { if (key == "genres") { width = -1 } }
                isClickable = true; isFocusable = true
                addView(TextView(this@SearchActivity).apply {
                    text = label; setTextColor(0xFFA7A5AF.toInt()); textSize = 11f
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD); letterSpacing = 0.035f
                })
                val valueRow = LinearLayout(this@SearchActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
                }
                valueRow.addView(TextView(this@SearchActivity).apply {
                    text = value; setTextColor(if (active) 0xFFFF4D57.toInt() else Color.WHITE)
                    textSize = 16f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                valueRow.addView(TextView(this@SearchActivity).apply {
                    text = "⌄"; textSize = 22f; setTextColor(0xFFA7A7B2.toInt()); gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(24))
                })
                addView(valueRow)
                setOnClickListener {
                    if (key == "genres") showGenrePicker(options[key] ?: emptyList(), genres) { picked ->
                        genres.clear(); genres.addAll(picked); selected["genres"] = if (genres.isEmpty()) "Any" else genres.joinToString(", ")
                        prefs.edit().putString("genres", selected["genres"]).apply()
                        sheet.dismiss(); showSearchOptions()
                    } else showValuePicker(label, options[key] ?: emptyList(), selected[key] ?: defaults[key]!!) { picked ->
                        selected[key] = picked; prefs.edit().putString(key, picked).apply(); sheet.dismiss(); showSearchOptions()
                    }
                }
            }
        }
        fun addPair(left: String?, right: String?) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
            if (left != null) row.addView(makeCell(left, left.uppercase())) else row.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(88), 1f) })
            row.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 1) })
            if (right != null) row.addView(makeCell(right, right.uppercase())) else row.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(88), 1f) })
            content.addView(row)
            content.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) })
        }
        addPair("sort", "year")
        addPair("status", "score")
        if (isAnime) addPair("season", "format") else addPair(null, "format")
        content.addView(makeCell("genres", "GENRES").apply { layoutParams = LinearLayout.LayoutParams(-1, dp(88)) })
        scroll.addView(content); panel.addView(scroll)

        panel.addView(android.widget.Button(this).apply {
            text = "Apply"; isAllCaps = false; textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xFFFF304F.toInt()); cornerRadius = dp(14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) }
            setOnClickListener {
                val edit = prefs.edit()
                selected.forEach { (key, value) -> edit.putString(key, value) }
                edit.putString("genres", if (genres.isEmpty()) "Any" else genres.joinToString(", ")).apply()
                emptyLabel.text = if (query.text.isNullOrEmpty()) "Filters applied" else "Search for “\${query.text}”"
                sheet.dismiss()
            }
        })
        sheet.setContentView(panel)
        sheet.setOnShowListener {
            val bottom = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottom?.let { view ->
                view.setBackgroundColor(Color.TRANSPARENT)
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(view)
                behavior.isFitToContents = false; behavior.skipCollapsed = false
                behavior.expandedOffset = dp(24); behavior.halfExpandedRatio = 0.54f
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.48f).toInt()
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }
        sheet.show()
    }

    private fun showValuePicker(title: String, values: List<String>, current: String, onPicked: (String) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(16))
            background = GradientDrawable().apply { setColor(0xFF0B0B0F.toInt()); cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f) }
        }
        list.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 0, 0, dp(12)); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        (listOf("Any") + values).distinct().forEach { value ->
            list.addView(TextView(this).apply {
                text = if (value == current) "✓    $value" else value
                setTextColor(if (value == current) 0xFFFF4D57.toInt() else Color.WHITE)
                textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(48))
                setOnClickListener { dialog.dismiss(); onPicked(value) }
            })
        }
        dialog.setContentView(list)
        dialog.setOnShowListener { dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT) }
        dialog.show()
    }

    private fun showGenrePicker(values: List<String>, current: Set<String>, onPicked: (Set<String>) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply { setColor(0xFF0B0B0F.toInt()); cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f) }
        }
        panel.addView(TextView(this).apply { text = "Genres"; setTextColor(Color.WHITE); textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        val chosen = current.toMutableSet()
        val scroll = android.widget.ScrollView(this)
        val wrap = android.widget.GridLayout(this).apply { columnCount = 3; useDefaultMargins = true }
        values.forEach { genre ->
            val chip = TextView(this).apply {
                text = genre; gravity = Gravity.CENTER; setPadding(dp(10), dp(9), dp(10), dp(9)); textSize = 12f
                fun style() {
                    setTextColor(if (chosen.contains(genre)) 0xFFFF4D57.toInt() else Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(20).toFloat()
                        setColor(if (chosen.contains(genre)) 0x33FF4D57 else 0xFF19181F.toInt())
                        if (chosen.contains(genre)) setStroke(dp(1), 0xFFFF4D57.toInt())
                    }
                }
                style()
                setOnClickListener { if (!chosen.add(genre)) chosen.remove(genre); style() }
            }
            wrap.addView(chip, android.widget.GridLayout.LayoutParams().apply {
                width = 0; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }
        scroll.addView(wrap); panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(300)))
        panel.addView(android.widget.Button(this).apply {
            text = "Done"; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xFFFF304F.toInt()); cornerRadius = dp(14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) }
            setOnClickListener { dialog.dismiss(); onPicked(chosen) }
        })
        dialog.setContentView(panel); dialog.show()
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies & TV")
        }
        runCatching { startActivityForResult(intent, voiceRequest) }
    }

    @Deprecated("Legacy callback for Android recognizer compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == voiceRequest && resultCode == Activity.RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query.setText(it) }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
        query.clearFocus()
    }
    private fun pill(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(color)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
