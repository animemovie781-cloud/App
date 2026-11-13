package com.kanha.dev

import android.os.Bundle
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.animation.doOnEnd
import com.kanha.dev.databinding.ActivityMainBinding
import com.kanha.dev.databinding.FragmentHomeBinding
import com.kanha.dev.databinding.FragmentSimpleBinding
import eightbitlab.com.blurview.BlurView

/** Native home shell, following root_shell.dart and liquid_nav_glass.dart. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = Tab.HOME
    private var contentMode = "Anime"
    private var modePopup: PopupWindow? = null
    private var dockCollapsed = false
    private var dockAnimator: ValueAnimator? = null
    private var expandedDockWidth = 0
    private var scrollDirectionDistance = 0
    private enum class Tab { HOME, SEARCH, LIBRARY, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val savedCrash = java.io.File(filesDir, ZangetsuApp.CRASH_FILE_NAME)
        if (savedInstanceState == null && savedCrash.exists()) {
            startActivity(android.content.Intent(this, CrashReportActivity::class.java)); finish(); return
        }
        if (savedInstanceState == null && !getSharedPreferences("zangetsu_prefs", MODE_PRIVATE).getBoolean("onboarded", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java)); finish(); return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomDock.setupWith(binding.root as ViewGroup)
            .setFrameClearDrawable(binding.root.background)
            // Flutter's liquid nav uses a subtle blur (3), not a frosted
            // sheet.  A radius of 22 made the native port look like a grey box.
            .setBlurRadius(3f)
            .setOverlayColor(Color.argb(12, 255, 255, 255))
        binding.navHome.setOnClickListener { showTab(Tab.HOME) }
        binding.navSearch.setOnClickListener { showTab(Tab.LIBRARY) }
        binding.navLibrary.setOnClickListener { showTab(Tab.SEARCH) }
        binding.navSettings.setOnClickListener { startActivity(android.content.Intent(this, ProfilePickerActivity::class.java)) }
        binding.modeButton.setOnClickListener { showModeMenu() }
        if (savedInstanceState != null) contentMode = savedInstanceState.getString("content_mode", "Anime") ?: "Anime"
        if (savedInstanceState != null) selectedTab = runCatching { Tab.valueOf(savedInstanceState.getString("selected_tab", "HOME")!!) }.getOrDefault(Tab.HOME)
        showTab(selectedTab)
    }

    private fun showTab(tab: Tab) {
        selectedTab = tab
        binding.contentHost.removeAllViews()
        val page: View = if (tab == Tab.HOME) {
            val home = FragmentHomeBinding.inflate(layoutInflater)
            val density = resources.displayMetrics.density
            val screenDp = resources.displayMetrics.heightPixels / density
            home.heroSection.layoutParams = home.heroSection.layoutParams.apply {
                // The original phone hero is deliberately cinematic: it occupies
                // roughly two thirds of the viewport before the compact shortcut
                // cards.  Keeping this in pixels avoids a short banner on dense
                // screens, which was pushing the title/CTA into a cramped stack.
                height = (screenDp * 0.62f).toInt().coerceIn(380, 560).let { (it * density).toInt() }
            }
            home.heroSection.requestLayout()
            HomeMetadataController(this, home, contentMode).load()
            home.homeScroll.setOnScrollChangeListener { _, scrollY, oldScrollY, _, _ ->
                // Match DockScrollCollapse from the Flutter shell: accumulate
                // 60dp in one direction, reset on a reversal, and always open
                // at the top. Per-frame deltas are too small on slow drags.
                val delta = scrollY - oldScrollY
                if (scrollY <= dp(8)) {
                    scrollDirectionDistance = 0
                    setDockCollapsed(false)
                } else if (delta != 0) {
                    if ((delta > 0 && scrollDirectionDistance < 0) ||
                        (delta < 0 && scrollDirectionDistance > 0)) {
                        scrollDirectionDistance = 0
                    }
                    scrollDirectionDistance += delta
                    when {
                        scrollDirectionDistance > dp(60) && !dockCollapsed -> {
                            setDockCollapsed(true)
                            scrollDirectionDistance = 0
                        }
                        scrollDirectionDistance < -dp(60) && dockCollapsed -> {
                            setDockCollapsed(false)
                            scrollDirectionDistance = 0
                        }
                    }
                }
            }
            home.root
        } else {
            setDockCollapsed(false, immediate = true)
            FragmentSimpleBinding.inflate(layoutInflater).apply {
                pageTitle.text = when (tab) { Tab.SEARCH -> "Sources"; Tab.LIBRARY -> "My List"; Tab.SETTINGS -> "Profile"; else -> "Home" }
                pageDescription.text = when (tab) {
                    Tab.SEARCH -> "Browse installed streaming, manga and novel sources"
                    Tab.LIBRARY -> "Your saved anime, movies, manga and novels"
                    Tab.SETTINGS -> "Choose and manage your viewing profile"
                    else -> ""
                }
            }.root
        }
        binding.contentHost.addView(page)
        updateDockSelection()
    }

    private fun updateDockSelection() {
        val items = listOf(
            Triple(Tab.HOME, binding.navHome, binding.navHomeIcon),
            Triple(Tab.LIBRARY, binding.navSearch, binding.navSearchIcon),
            Triple(Tab.SEARCH, binding.navLibrary, binding.navLibraryIcon),
            Triple(Tab.SETTINGS, binding.navSettings, binding.navSettingsIcon)
        )
        items.forEach { (tab, container, icon) ->
            val selected = tab == selectedTab
            val label = container.getChildAt(1) as TextView
            icon.imageTintList = ColorStateList.valueOf(getColor(if (selected) R.color.z_accent else R.color.z_text_primary))
            // Flutter GlassTabBar's selected indicator is a subtle accent wash,
            // not a solid Material pill.
            container.background = if (selected) GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(36, 255, 77, 87))
            } else null
            label.setTextColor(getColor(if (selected) R.color.z_text_primary else R.color.z_text_secondary))
            container.alpha = if (selected) 1f else 0.86f
            if (tab == Tab.HOME) icon.setImageResource(R.drawable.ic_house)
            if (tab == Tab.LIBRARY) icon.setImageResource(R.drawable.ic_bookmark)
            if (tab == Tab.SEARCH) icon.setImageResource(R.drawable.ic_book_open_text)
            if (tab == Tab.SETTINGS) icon.setImageResource(R.drawable.ic_settings)
        }
    }

    /** Mirrors _LiquidGlassTabBar: full 60dp labelled dock → 50dp icon capsule. */
    private fun setDockCollapsed(collapsed: Boolean, immediate: Boolean = false) {
        if (dockCollapsed == collapsed && !immediate) return
        val wasCollapsed = dockCollapsed
        dockCollapsed = collapsed
        binding.bottomDock.post {
            val dock = binding.bottomDock
            if (!wasCollapsed && dock.width > 0) expandedDockWidth = dock.width
            val fullWidth = expandedDockWidth.takeIf { it > 0 } ?: dock.width.takeIf { it > 0 } ?: return@post
            val compactWidth = minOf(fullWidth, dp(4 * 52 + 8))
            val fromWidth = (dock.layoutParams as LinearLayout.LayoutParams).width.takeIf { it > 0 } ?: fullWidth
            val fromHeight = dock.height.takeIf { it > 0 } ?: dp(60)
            val toWidth = if (collapsed) compactWidth else fullWidth
            val toHeight = if (collapsed) dp(50) else dp(60)
            val labels = listOf(binding.navHomeLabel, binding.navSearchLabel, binding.navLibraryLabel, binding.navSettingsLabel)

            dockAnimator?.cancel()
            fun apply(progress: Float) {
                val lp = dock.layoutParams as LinearLayout.LayoutParams
                lp.weight = 0f
                lp.width = (fromWidth + (toWidth - fromWidth) * progress).toInt()
                lp.height = (fromHeight + (toHeight - fromHeight) * progress).toInt()
                dock.layoutParams = lp
                labels.forEach { it.alpha = if (collapsed) 1f - progress else progress }
            }
            if (immediate) {
                apply(1f)
                if (!collapsed) restoreDockWeight(dock, labels)
                return@post
            }
            dockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                interpolator = android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f)
                addUpdateListener { apply(it.animatedValue as Float) }
                doOnEnd { if (!collapsed) restoreDockWeight(dock, labels) }
                start()
            }
        }
    }

    private fun restoreDockWeight(dock: View, labels: List<TextView>) {
        val lp = dock.layoutParams as LinearLayout.LayoutParams
        lp.width = 0
        lp.weight = 1f
        lp.height = dp(60)
        dock.layoutParams = lp
        labels.forEach { it.alpha = 1f }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Frosted mode selector patterned after metadata_switch_sheet.dart. */
    private fun showModeMenu() {
        if (modePopup?.isShowing == true) { modePopup?.dismiss(); return }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val glass = BlurView(this).apply {
            setupWith(binding.root as ViewGroup)
                .setFrameClearDrawable(binding.root.background)
                .setBlurRadius(22f)
                .setOverlayColor(Color.argb(112, 22, 21, 31))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(118, 24, 23, 34))
                setStroke(dp(1), Color.argb(32, 255, 255, 255))
            }
            clipToOutline = true
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        // Use the exact Material icon PNGs supplied for the original mode menu.
        val options = listOf(
            Triple("Anime", R.drawable.mode_anime, "Anime"),
            Triple("Movie/TV", R.drawable.mode_movie_tv, "Movie / TV"),
            Triple("Manga", R.drawable.mode_manga, "Manga"),
            Triple("Novel", R.drawable.mode_novel, "Novel")
        )
        options.forEach { (label, iconRes, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(52))
                isClickable = true
                isFocusable = true
                background = android.util.TypedValue().let {
                    val out = android.util.TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                    getDrawable(out.resourceId)
                }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                // Keep the thin clapperboard/book strokes legible at popup scale.
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            val title = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(14), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val check = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                setImageResource(R.drawable.mode_check_red)
                visibility = if (contentMode == value) View.VISIBLE else View.INVISIBLE
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = if (contentMode == value) "Selected" else null
            }
            row.addView(icon); row.addView(title); row.addView(check)
            row.setOnClickListener {
                modePopup?.dismiss()
                if (contentMode != value) { contentMode = value; showTab(Tab.HOME) }
            }
            list.addView(row)
        }
        glass.addView(list, FrameLayout.LayoutParams(-1, -2))
        modePopup = PopupWindow(glass, dp(236), -2, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(24).toFloat()
            showAtLocation(binding.root, Gravity.BOTTOM or Gravity.END, dp(22), dp(82))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", selectedTab.name)
        outState.putString("content_mode", contentMode)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        modePopup?.dismiss()
        super.onDestroy()
    }
}
