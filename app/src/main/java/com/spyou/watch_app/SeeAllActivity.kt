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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.json.JSONArray

/** Full-screen three-column poster grid matching the original Dart SeeAllScreen. */
class SeeAllActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TITLE = "see_all_title"
        const val EXTRA_ITEMS = "see_all_items"
    }
    private data class Entry(val title: String, val cover: String, val score: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val entries = parseItems(intent.getStringExtra(EXTRA_ITEMS).orEmpty())
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11, 11, 15)) }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0); layoutParams = LinearLayout.LayoutParams(-1, dp(64))
        }
        val back = TextView(this).apply {
            text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(48), -1); contentDescription = "Back"
            setOnClickListener { finish() }
        }
        val heading = TextView(this).apply {
            text = title; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(20), 0, 0, 0)
        }
        toolbar.addView(back); toolbar.addView(heading)
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@SeeAllActivity, 3)
            clipToPadding = false; setPadding(dp(16), dp(12), dp(16), dp(20))
            overScrollMode = View.OVER_SCROLL_NEVER; adapter = PosterGridAdapter(entries)
        }
        root.addView(toolbar); root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun parseItems(raw: String): List<Entry> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val name = item.optString("title"); val cover = item.optString("cover")
            if (name.isBlank() || cover.isBlank() || cover == "null") null
            else Entry(name, cover, item.optString("score").takeIf { it != "null" }.orEmpty())
        }
    } catch (_: Exception) { emptyList() }

    private inner class PosterGridAdapter(private val items: List<Entry>) : RecyclerView.Adapter<PosterGridAdapter.Holder>() {
        inner class Holder(val card: LinearLayout, val image: ImageView, val score: TextView, val name: TextView) : RecyclerView.ViewHolder(card)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2) }
            val frame = FrameLayout(parent.context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF20212A.toInt()) }
                clipToOutline = true; layoutParams = LinearLayout.LayoutParams(-1, dp(148))
            }
            val image = ImageView(parent.context).apply { layoutParams = FrameLayout.LayoutParams(-1, -1); scaleType = ImageView.ScaleType.CENTER_CROP }
            val score = TextView(parent.context).apply {
                textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFFFFD54F.toInt())
                setPadding(dp(5), dp(3), dp(5), dp(3))
                background = GradientDrawable().apply { cornerRadius = dp(5).toFloat(); setColor(0xCC101014.toInt()) }
            }
            frame.addView(image); frame.addView(score, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(7); marginEnd = dp(7) })
            val name = TextView(parent.context).apply {
                setTextColor(Color.WHITE); textSize = 13f; maxLines = 2
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
