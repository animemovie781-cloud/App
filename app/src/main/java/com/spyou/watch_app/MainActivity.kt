package com.spyou.watch_app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.spyou.watch_app.databinding.ActivityMainBinding
import com.spyou.watch_app.databinding.FragmentHomeBinding
import com.spyou.watch_app.databinding.FragmentSimpleBinding

/** Native Kotlin/XML host. UI is migrated screen-by-screen from original Dart source. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = Tab.HOME
    private enum class Tab { HOME, SEARCH, LIBRARY, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.navHome.setOnClickListener { showTab(Tab.HOME) }
        binding.navSearch.setOnClickListener { showTab(Tab.SEARCH) }
        binding.navLibrary.setOnClickListener { showTab(Tab.LIBRARY) }
        binding.navSettings.setOnClickListener { showTab(Tab.SETTINGS) }
        selectedTab = savedInstanceState?.getString("selected_tab")?.let {
            runCatching { Tab.valueOf(it) }.getOrDefault(Tab.HOME)
        } ?: Tab.HOME
        showTab(selectedTab)
    }

    private fun showTab(tab: Tab) {
        selectedTab = tab
        binding.contentHost.removeAllViews()
        val page: View = if (tab == Tab.HOME) {
            FragmentHomeBinding.inflate(layoutInflater).root
        } else {
            FragmentSimpleBinding.inflate(layoutInflater).apply {
                pageTitle.text = when (tab) {
                    Tab.SEARCH -> "Search"
                    Tab.LIBRARY -> "My List"
                    Tab.SETTINGS -> "Settings"
                    else -> "Home"
                }
                pageDescription.text = when (tab) {
                    Tab.SEARCH -> "Search catalogues and providers"
                    Tab.LIBRARY -> "Your saved anime, movies, manga and novels"
                    Tab.SETTINGS -> "Personalize your Zangetsu experience"
                    else -> ""
                }
            }.root
        }
        binding.contentHost.addView(page)
        updateDock()
    }

    private fun updateDock() {
        listOf(
            Tab.HOME to binding.navHome,
            Tab.SEARCH to binding.navSearch,
            Tab.LIBRARY to binding.navLibrary,
            Tab.SETTINGS to binding.navSettings
        ).forEach { (tab, view) ->
            view.setTextColor(getColor(if (tab == selectedTab) R.color.z_accent else R.color.z_text_secondary))
            view.alpha = if (tab == selectedTab) 1f else 0.78f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", selectedTab.name)
        super.onSaveInstanceState(outState)
    }
}
