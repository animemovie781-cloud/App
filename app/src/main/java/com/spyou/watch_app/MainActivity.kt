package com.kanha.dev

import android.os.Bundle
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
            .setBlurRadius(5f)
            .setOverlayColor(Color.argb(5, 255, 255, 255))
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
                height = (screenDp * 0.68f).toInt().coerceIn(430, 620).let { (it * density).toInt() }
            }
            home.heroSection.requestLayout()
            HomeMetadataController(this, home, contentMode).load()
            home.root
        } else {
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
            label.setTextColor(getColor(if (selected) R.color.z_text_primary else R.color.z_text_secondary))
            container.alpha = if (selected) 1f else 0.86f
            if (tab == Tab.HOME) icon.setImageResource(if (selected) R.drawable.ic_dock_home_filled else R.drawable.ic_dock_home)
            if (tab == Tab.LIBRARY) icon.setImageResource(R.drawable.dock_bookmark)
            if (tab == Tab.SEARCH) icon.setImageResource(R.drawable.ic_dock_puzzle)
            if (tab == Tab.SETTINGS) icon.setImageResource(R.drawable.ic_dock_profile_filled)
        }
    }

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
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(118, 24, 23, 34))
                setStroke(dp(1), Color.argb(32, 255, 255, 255))
            }
            clipToOutline = true
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val options = listOf(
            Triple("Anime", R.drawable.ic_onboarding_streaming, "Anime"),
            Triple("Movie/TV", R.drawable.ic_dock_sources, "Movie / TV"),
            Triple("Manga", R.drawable.ic_onboarding_manga, "Manga"),
            Triple("Novel", R.drawable.ic_onboarding_novel, "Novel")
        )
        options.forEach { (label, iconRes, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(64))
                isClickable = true
                isFocusable = true
                background = android.util.TypedValue().let {
                    val out = android.util.TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                    getDrawable(out.resourceId)
                }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val title = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(18), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val check = TextView(this).apply {
                text = if (contentMode == value) "✓" else ""
                setTextColor(Color.rgb(255, 77, 87))
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(icon); row.addView(title); row.addView(check)
            row.setOnClickListener {
                modePopup?.dismiss()
                if (contentMode != value) { contentMode = value; showTab(Tab.HOME) }
            }
            list.addView(row)
        }
        glass.addView(list, FrameLayout.LayoutParams(-1, -2))
        modePopup = PopupWindow(glass, dp(250), -2, true).apply {
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
