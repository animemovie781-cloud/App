package com.spyou.watch_app

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView

/** Three-page native onboarding recreation based on the original Zangetsu onboarding UI. */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var skip: TextView
    private lateinit var next: TextView
    private val bg = 0xFF0B0B0F.toInt()
    private val accent = 0xFFFF4D57.toInt()
    private val muted = 0xFFA7A7B2.toInt()
    private val dim = 0xFF6E6E78.toInt()
    private val surface = 0xFF20212A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val top = FrameLayout(this)
        skip = TextView(this).apply {
            text = "Skip"; textSize = 14f; setTextColor(dim)
            gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(20), 0)
            setOnClickListener { finishOnboarding() }
        }
        top.addView(skip, FrameLayout.LayoutParams(-2, dp(52), Gravity.END))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(52)))
        pager = ViewPager2(this).apply {
            adapter = Pages()
            offscreenPageLimit = 3
            setPageTransformer { page, position ->
                page.alpha = 1f - kotlin.math.abs(position) * .5f
                page.translationY = position * dp(12)
            }
        }
        root.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(20))
        }
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(dots)
        footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        next = TextView(this).apply {
            text = "→"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(accent)
            background = oval(surface)
            setOnClickListener { if (pager.currentItem < 2) pager.currentItem++ }
        }
        footer.addView(next, LinearLayout.LayoutParams(dp(60), dp(60)))
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(80)))
        setContentView(root)
        renderDots(0)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderDots(position)
                skip.visibility = if (position == 2) View.INVISIBLE else View.VISIBLE
                next.visibility = if (position == 2) View.INVISIBLE else View.VISIBLE
            }
        })
    }

    private inner class Pages : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = 3
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            // ViewPager2 requires every page root to occupy the full pager viewport.
            val pageRoot = FrameLayout(this@OnboardingActivity).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return Holder(pageRoot)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.frame.removeAllViews()
            val page = when (position) { 0 -> welcome(); 1 -> sourceGuide(); else -> ready() }
            holder.frame.addView(page, FrameLayout.LayoutParams(-1, -1))
        }
    }
    private class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

    private fun welcome(): View {
        val col = centeredColumn()
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x221F0D12, bg, bg)).apply { shape = GradientDrawable.OVAL }
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.onboarding_logo_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Zangetsu original logo mark"
        }, LinearLayout.LayoutParams(dp(128), dp(128)))
        brand.addView(TextView(this).apply {
            text = "Z A N G E T S U"; textSize = 21f; setTextColor(-1)
            gravity = Gravity.CENTER; typeface = Typeface.SERIF
        }, LinearLayout.LayoutParams(dp(250), dp(48)))
        col.addView(brand, LinearLayout.LayoutParams(dp(280), dp(280)))
        col.addView(space(48))
        col.addView(label("Good to have you here.", 22f, -1, true), wrap())
        col.addView(space(12))
        col.addView(label("Anime, manga and novels — all in one app, set\nup your way.", 17f, muted, false), wrap())
        return col
    }

    private fun sourceGuide(): View {
        val col = centeredColumn()
        col.addView(label("You choose what's inside", 22f, -1, true), wrap())
        col.addView(space(10))
        col.addView(label("Add the sources you want — here's how.", 17f, muted, false), wrap())
        col.addView(space(34))
        val tiles = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("▧" to "Streaming", "▤" to "Manga", "◫" to "Novel").forEach { (symbol, name) ->
            val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            tile.addView(TextView(this).apply {
                text = symbol; textSize = 31f; gravity = Gravity.CENTER; setTextColor(muted); background = rounded(surface, 25)
            }, LinearLayout.LayoutParams(dp(88), dp(88)))
            tile.addView(space(12))
            tile.addView(label(name, 14f, dim, true), wrap())
            tiles.addView(tile)
        }
        col.addView(tiles, LinearLayout.LayoutParams(-1, -2))
        col.addView(space(48))
        listOf("Open Providers", "Pick streaming, manga or novels", "Paste in a repository link", "Browse it and grab what looks good").forEachIndexed { i, text ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
            val number = TextView(this).apply {
                this.text = (i + 1).toString(); textSize = 15f; gravity = Gravity.CENTER; setTextColor(accent)
                background = oval(0x442A1118.toInt())
            }
            row.addView(number, LinearLayout.LayoutParams(dp(38), dp(38)))
            val textView = label(text, 16f, -1, false)
            row.addView(textView, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(18) })
            col.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return col
    }

    private fun ready(): View {
        val col = centeredColumn()
        col.addView(label("Ready when you are.", 22f, -1, true), wrap())
        col.addView(space(18))
        col.addView(label("Add a source now, or have a look around first —\nProviders is always waiting in Settings.", 17f, muted, false), wrap())
        col.addView(space(58))
        val add = TextView(this).apply {
            text = "Add sources now"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(-1); background = rounded(accent, 24)
            setOnClickListener { finishOnboarding() }
        }
        col.addView(add, LinearLayout.LayoutParams(-1, dp(76)))
        col.addView(space(22))
        col.addView(TextView(this).apply {
            text = "I'll do it later"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(dim)
            setOnClickListener { finishOnboarding() }
        }, wrap())
        return col
    }

    private fun finishOnboarding() {
        getSharedPreferences("zangetsu_prefs", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    private fun renderDots(selected: Int) {
        dots.removeAllViews()
        repeat(3) { i ->
            val v = View(this)
            v.background = rounded(if (i == selected) accent else surface, 10)
            dots.addView(v, LinearLayout.LayoutParams(if (i == selected) dp(28) else dp(8), dp(8)).apply { rightMargin = dp(8) })
        }
    }
    private fun centeredColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(28), dp(12), dp(28), dp(12))
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun wrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
}
