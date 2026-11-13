package com.kanha.dev

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Profile chooser and rotating artwork, following viewer_profiles_screen.dart. */
class ProfilePickerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("zangetsu_profiles", MODE_PRIVATE) }
    private lateinit var grid: GridLayout
    private lateinit var subtitle: TextView
    private lateinit var backdrop: ImageView
    private var managing = false
    private val artworkHandler = Handler(Looper.getMainLooper())
    private var artworkUrls: List<String> = emptyList()
    private var artworkIndex = 0
    private var currentArtwork: String? = null
    private val imageLoader by lazy { ImageLoader.Builder(this).crossfade(900).build() }

    private val rotateArtwork = object : Runnable {
        override fun run() {
            if (artworkUrls.size > 1 && !isFinishing && !isDestroyed) {
                showNextArtwork()
                artworkHandler.postDelayed(this, 3_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.rgb(11, 11, 15)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContentView(R.layout.activity_profile_picker)
        grid = findViewById(R.id.profileGrid)
        subtitle = findViewById(R.id.profileSubtitle)
        backdrop = findViewById(R.id.profileBackdrop)

        // Original Flutter picker shows portrait artwork through roughly the top 60%.
        backdrop.layoutParams = backdrop.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        }
        backdrop.visibility = View.VISIBLE
        backdrop.alpha = 0f

        findViewById<MaterialButton>(R.id.manageProfiles).setOnClickListener {
            managing = !managing
            renderProfiles()
            Toast.makeText(this, if (managing) "Tap a profile to remove it" else "Profile management closed", Toast.LENGTH_SHORT).show()
        }
        renderProfiles()
        loadProfilePickerArtwork()
    }

    private fun loadProfilePickerArtwork() {
        // Match ProfilePickerArtwork.cachedUrl: keep the last successful image for offline launch.
        prefs.getString("profile_picker_artwork_last_url", null)?.let { cached ->
            if (isAllowedArtworkUrl(cached)) showArtwork(cached)
        }
        lifecycleScope.launch {
            val urls = withContext(Dispatchers.IO) {
                fetchArtworkManifest()
            }
            if (urls.isNotEmpty() && !isFinishing) {
                artworkUrls = urls
                val cached = prefs.getString("profile_picker_artwork_last_url", null)
                artworkIndex = urls.indexOf(cached).let { if (it >= 0) (it + 1) % urls.size else 0 }
                if (currentArtwork == null) showNextArtwork()
                if (urls.size > 1) artworkHandler.postDelayed(rotateArtwork, 3_000L)
            }
        }
    }

    private fun fetchArtworkManifest(): List<String> {
        val connection = (URL(ARTWORK_MANIFEST_URL).openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val names = JSONObject(body).optJSONArray("images") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(names.length(), 3)) {
                    val name = names.optString(i)
                    if (FILE_NAME.matches(name)) add("$ARTWORK_BASE_URL/$name")
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun isAllowedArtworkUrl(value: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(value)
            uri.scheme == "https" &&
                uri.host == "avatars.zangetsu.online" &&
                uri.pathSegments.size == 2 &&
                uri.pathSegments[0] == "profile-picker" &&
                FILE_NAME.matches(uri.lastPathSegment.orEmpty()) &&
                uri.query == null
        } catch (_: Exception) { false }
    }

    private fun showNextArtwork() {
        if (artworkUrls.isEmpty()) return
        val url = artworkUrls[artworkIndex % artworkUrls.size]
        artworkIndex = (artworkIndex + 1) % artworkUrls.size
        prefs.edit().putString("profile_picker_artwork_last_url", url).apply()
        showArtwork(url)
    }

    private fun showArtwork(url: String) {
        if (!isAllowedArtworkUrl(url) || url == currentArtwork) return
        val request = ImageRequest.Builder(this)
            .data(url)
            .target(
                onStart = { },
                onSuccess = { drawable ->
                    if (isFinishing || isDestroyed) return@target
                    backdrop.setImageDrawable(drawable)
                    backdrop.visibility = View.VISIBLE
                    backdrop.alpha = 0f
                    backdrop.translationX = dp(12).toFloat()
                    backdrop.scaleX = 1.04f
                    backdrop.scaleY = 1.04f
                    backdrop.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                        .setDuration(900L).start()
                    currentArtwork = url
                },
                onError = { }
            )
            .build()
        imageLoader.enqueue(request)
    }

    override fun onDestroy() {
        artworkHandler.removeCallbacks(rotateArtwork)
        super.onDestroy()
    }

    private fun profileNames(): MutableList<String> {
        val saved = prefs.getStringSet("names", null)?.toMutableList()?.sorted()?.toMutableList()
        return saved?.takeIf { it.isNotEmpty() } ?: mutableListOf("Home")
    }

    private fun renderProfiles() {
        val names = profileNames()
        val active = prefs.getString("active", "Home") ?: "Home"
        subtitle.text = "Ready for the next watch, $active?"
        grid.removeAllViews()
        val showAdd = names.size < 5
        val total = names.size + if (showAdd) 1 else 0
        grid.columnCount = 2
        grid.rowCount = (total + 1) / 2

        names.forEachIndexed { index, name ->
            val selected = name == active
            val item = profileItem(name, selected, false)
            item.setOnClickListener {
                if (managing && name != "Home") {
                    AlertDialog.Builder(this)
                        .setTitle("Remove $name?")
                        .setMessage("This removes the profile from this device.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            val updated = profileNames().filterNot { it == name }.toSet()
                            prefs.edit().putStringSet("names", updated).apply()
                            if (prefs.getString("active", "Home") == name) prefs.edit().putString("active", "Home").apply()
                            renderProfiles()
                        }.show()
                } else if (!managing) {
                    prefs.edit().putString("active", name).apply()
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
            }
            addCell(item, index)
        }
        if (showAdd) {
            val add = profileItem("Add", false, true)
            add.setOnClickListener { showAddProfileDialog() }
            addCell(add, names.size)
        }
    }

    private fun profileItem(name: String, selected: Boolean, add: Boolean): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(8))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null
            )
        }
        val diameter = dp(100)
        val avatar = FrameAvatar(this)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(getColor(if (add) R.color.z_surface_2 else if (selected) R.color.z_accent else R.color.z_surface_2))
            setStroke(dp(1), getColor(if (selected && !add) R.color.z_accent else R.color.z_text_tertiary))
        }
        avatar.background = circle
        val icon = ImageView(this).apply {
            setImageResource(if (add) R.drawable.ic_profile_add else R.drawable.ic_profile_person)
            setColorFilter(android.graphics.Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(30), dp(30), dp(30), dp(30))
            contentDescription = if (add) "Add profile" else "$name profile"
        }
        avatar.addView(icon, android.widget.FrameLayout.LayoutParams(-1, -1))
        column.addView(avatar, LinearLayout.LayoutParams(diameter, diameter))
        column.addView(TextView(this).apply {
            text = name
            textSize = 15f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setTypeface(Typeface.create("sans-serif", if (selected) Typeface.BOLD else Typeface.NORMAL))
            setTextColor(getColor(if (selected && !add) R.color.z_text_primary else R.color.z_text_secondary))
            setPadding(0, dp(7), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        return column
    }

    private fun addCell(view: View, index: Int) {
        val lp = GridLayout.LayoutParams(
            GridLayout.spec(index / 2, 1f),
            GridLayout.spec(index % 2, 1f)
        ).apply {
            width = 0
            height = dp(148)
            setMargins(dp(4), 0, dp(4), dp(8))
        }
        grid.addView(view, lp)
    }

    private fun showAddProfileDialog() {
        val field = android.widget.EditText(this).apply {
            hint = "Profile name"
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Add profile")
            .setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotEmpty() && !profileNames().contains(name)) {
                    val updated = (profileNames() + name).toSet()
                    prefs.edit().putStringSet("names", updated).apply()
                    renderProfiles()
                } else {
                    Toast.makeText(this, "Enter a unique profile name", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class FrameAvatar(context: android.content.Context) : android.widget.FrameLayout(context)

    companion object {
        private const val ARTWORK_MANIFEST_URL = "https://avatars.zangetsu.online/profile-picker/manifest.json"
        private const val ARTWORK_BASE_URL = "https://avatars.zangetsu.online/profile-picker"
        private val FILE_NAME = Regex("^[a-zA-Z0-9_-]{1,64}\\.(?:webp|png|jpe?g)$", RegexOption.IGNORE_CASE)
    }
}
