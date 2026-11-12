package com.spyou.watch_app

import android.os.Bundle
import android.view.View
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
        if (savedInstanceState == null && !getSharedPreferences("zangetsu_prefs", MODE_PRIVATE).getBoolean("onboarded", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.navHome.setOnClickListener { showTab(Tab.HOME) }
        binding.navSearch.setOnClickListener { showTab(Tab.SEARCH) }
        binding.navLibrary.setOnClickListener { showTab(Tab.LIBRARY) }
        binding.navSettings.setOnClickListener { showTab(Tab.SETTINGS) }
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
                    Tab.SEARCH -> "Search"; Tab.LIBRARY -> "My List"; Tab.SETTINGS -> "Settings"; else -> "Home"
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
        listOf(Tab.HOME to binding.navHome, Tab.SEARCH to binding.navSearch, Tab.LIBRARY to binding.navLibrary, Tab.SETTINGS to binding.navSettings)
            .forEach { (item, view) ->
                view.setTextColor(getColor(if (item == selectedTab) R.color.z_accent else R.color.z_text_secondary))
                view.alpha = if (item == selectedTab) 1f else .78f
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", selectedTab.name)
        super.onSaveInstanceState(outState)
    }
}