package com.spyou.watch_app

import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.view.View
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.spyou.watch_app.databinding.ActivityMainBinding
import com.spyou.watch_app.databinding.FragmentHomeBinding
import com.spyou.watch_app.databinding.FragmentSimpleBinding

/** Native Kotlin/XML host; feature screens are migrated from the original Dart app incrementally. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = Tab.HOME
    private enum class Tab { HOME, SEARCH, LIBRARY, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val savedCrash = java.io.File(filesDir, ZangetsuApp.CRASH_FILE_NAME)
        if (savedInstanceState == null && savedCrash.exists()) {
            startActivity(android.content.Intent(this, CrashReportActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null && !getSharedPreferences("zangetsu_prefs", MODE_PRIVATE).getBoolean("onboarded", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Native frosted-glass equivalent of liquidNavGlassSettings() in Dart.
        binding.bottomDock.setupWith(binding.root as ViewGroup)
            .setFrameClearDrawable(binding.root.background)
            .setBlurRadius(18f)
            .setOverlayColor(Color.argb(42, 24, 24, 32))
        binding.navHome.setOnClickListener { showTab(Tab.HOME) }
        binding.navSearch.setOnClickListener { showTab(Tab.LIBRARY) }
        binding.navLibrary.setOnClickListener { showTab(Tab.SEARCH) }
        binding.navSettings.setOnClickListener { startActivity(android.content.Intent(this, ProfilePickerActivity::class.java)) }
        binding.modeButton.setOnClickListener { showModeMenu() }
        if (savedInstanceState != null) selectedTab = runCatching {
            Tab.valueOf(savedInstanceState.getString("selected_tab", "HOME")!!)
        }.getOrDefault(Tab.HOME)
        showTab(selectedTab)
    }

    private fun showTab(tab: Tab) {
        selectedTab = tab
        binding.contentHost.removeAllViews()
        val page: View = if (tab == Tab.HOME) FragmentHomeBinding.inflate(layoutInflater).root else {
            FragmentSimpleBinding.inflate(layoutInflater).apply {
                pageTitle.text = when (tab) {
                    Tab.SEARCH -> "Sources"; Tab.LIBRARY -> "My List"; Tab.SETTINGS -> "Profile"; else -> "Home"
                }
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
            val tint = if (selected) getColor(R.color.z_accent) else getColor(R.color.z_text_primary)
            icon.imageTintList = ColorStateList.valueOf(tint)
            label.setTextColor(getColor(if (selected) R.color.z_text_primary else R.color.z_text_secondary))
            container.alpha = if (selected) 1f else 0.86f
            if (tab == Tab.HOME) icon.setImageResource(R.drawable.dock_house)
            if (tab == Tab.LIBRARY) icon.setImageResource(R.drawable.dock_bookmark)
        }
    }

    private fun showModeMenu() {
        val menu = PopupMenu(this, binding.modeButton)
        listOf("Anime", "Movie / TV", "Manga", "Novel").forEach { menu.menu.add(it) }
        menu.setOnMenuItemClickListener { item ->
            android.widget.Toast.makeText(this, "${item.title} mode selected", android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        menu.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", selectedTab.name)
        super.onSaveInstanceState(outState)
    }
}