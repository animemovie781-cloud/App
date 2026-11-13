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

/** Native recreation of the original three-page Zangetsu onboarding. */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var skip: TextView
    private lateinit var next: ImageView

    private val bg = 0xFF0B0B0F.toInt()
    private val accent = 0xFFFF4D57.toInt()
    private val muted = 0xFFA7A7B2.toInt()
    private val dim = 0xFF6E6E78.toInt()
    private val surface2 = 0xFF20212A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val top = FrameLayout(this)
        skip = TextView(this).apply {
            text = "Skip"
            textSize = 13f
            setTextColor(dim)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(12), 0, dp(20), 0)
            setOnClickListener { finishOnboarding() }
        }
        top.addView(skip, FrameLayout.LayoutParams(-2, dp(40), Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(40)))

        pager = ViewPager2(this).apply {
            adapter = Pages()
            offscreenPageLimit = 3
            setPageTransformer { page, position ->
                page.alpha = 1f - kotlin.math.abs(position) * .55f
                page.translationY = position * dp(14)
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
        next = ImageView(this).apply {
            setImageResource(R.drawable.ic_onboarding_arrow)
            scaleType = ImageView.ScaleType.CENTER
            background = oval(surface2)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "Next page"
            setOnClickListener { if (pager.currentItem < 2) pager.currentItem++ }
        }
        footer.addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(72)))

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
            val page = when (position) {
                0 -> welcome()
                1 -> sourceGuide()
                else -> ready()
            }
            holder.frame.addView(page, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

    /** Page 1 — original logo artwork and wordmark from the source ZIP. */
    private fun welcome(): View {
        val content = pageContent()
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x221F0D12, bg, bg)
            ).apply { shape = GradientDrawable.OVAL }
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.onboarding_logo_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Zangetsu logo"
        }, LinearLayout.LayoutParams(dp(92), dp(92)))
        brand.addView(TextView(this).apply {
            text = "Z A N G E T S U"
            textSize = 21f
            typeface = Typeface.SERIF
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(-1)
            contentDescription = "Zangetsu wordmark"
        }, LinearLayout.LayoutParams(dp(220), dp(42)).apply { topMargin = dp(16) })
        content.addView(brand, LinearLayout.LayoutParams(dp(260), dp(260)))
        content.addView(space(34))
        content.addView(label("Good to have you here.", 22f, 1.15f, -1, true), wrap())
        content.addView(space(12))
        content.addView(label(
            "Anime, manga and novels — all in one app, set\nup your way.",
            15f, 1.35f, muted, false
        ), wrap())
        return pageScroller(content)
    }

    /** Page 2 — Material outlined icons and exact original spacing/scale. */
    private fun sourceGuide(): View {
        val content = pageContent()
        content.addView(label("You choose what's inside", 22f, 1.15f, -1, true), wrap())
        content.addView(space(10))
        content.addView(label("Add the sources you want — here's how.", 15f, 1.35f, muted, false), wrap())
        content.addView(space(30))

        val tiles = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            weightSum = 3f
        }
        addEcosystemTile(tiles, R.drawable.ic_onboarding_streaming, "Streaming")
        addEcosystemTile(tiles, R.drawable.ic_onboarding_manga, "Manga")
        addEcosystemTile(tiles, R.drawable.ic_onboarding_novel, "Novel")
        content.addView(tiles, LinearLayout.LayoutParams(-1, -2))
        content.addView(space(32))

        val steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "Open Providers",
            "Pick streaming, manga or novels",
            "Paste in a repository link",
            "Browse it and grab what looks good"
        ).forEachIndexed { index, text ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(9), 0, dp(9))
            }
            val number = TextView(this).apply {
                this.text = (index + 1).toString()
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(accent)
                background = oval(0x442A1118.toInt())
            }
            row.addView(number, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.addView(label(text, 15f, 1.35f, -1, false),
                LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
            steps.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(steps, LinearLayout.LayoutParams(-1, -2))
        return pageScroller(content)
    }

    private fun addEcosystemTile(parent: LinearLayout, icon: Int, title: String) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        tile.addView(ImageView(this).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER
            background = rounded(surface2, 16)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            contentDescription = title
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        tile.addView(space(9))
        tile.addView(label(title, 13f, 1.3f, dim, true), wrap())
        parent.addView(tile)
    }

    /** Page 3 — hand-off actions, matching the original 52dp CTA. */
    private fun ready(): View {
        val content = pageContent()
        content.addView(label("Ready when you are.", 22f, 1.15f, -1, true), wrap())
        content.addView(space(12))
        content.addView(label(
            "Add a source now, or have a look around first —\nProviders is always waiting in Settings.",
            15f, 1.35f, muted, false
        ), wrap())
        content.addView(space(32))
        val add = TextView(this).apply {
            text = "Add sources now"
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(-1)
            background = rounded(accent, 14)
            setOnClickListener { finishOnboarding(true) }
        }
        content.addView(add, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(TextView(this).apply {
            text = "I'll do it later"
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(dim)
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { finishOnboarding() }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        return pageScroller(content)
    }

    private fun pageContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(12), dp(28), dp(12))
    }

    private fun pageScroller(content: View): View = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(content, ScrollView.LayoutParams(-1, -2))
    }

    private fun addSourcesNow() = finishOnboarding(true)

    private fun finishOnboarding(openProviders: Boolean = false) {
        getSharedPreferences("zangetsu_prefs", MODE_PRIVATE).edit()
            .putBoolean("onboarded", true).apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            if (openProviders) putExtra("open_providers", true)
        })
        finish()
    }

    private fun renderDots(selected: Int) {
        dots.removeAllViews()
        repeat(3) { index ->
            val dot = View(this)
            dot.background = rounded(if (index == selected) accent else surface2, 3)
            dots.addView(dot, LinearLayout.LayoutParams(
                if (index == selected) dp(18) else dp(6), dp(6)
            ).apply { rightMargin = dp(6) })
        }
    }

    private fun label(text: String, size: Float, lineHeight: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(0f, lineHeight)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun wrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
}
