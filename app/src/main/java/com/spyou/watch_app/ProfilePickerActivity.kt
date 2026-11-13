package com.spyou.watch_app

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.google.android.material.button.MaterialButton

/** Profile chooser recreated from the original Flutter viewer profile picker. */
class ProfilePickerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("zangetsu_profiles", MODE_PRIVATE) }
    private lateinit var grid: GridLayout
    private lateinit var subtitle: TextView
    private var managing = false

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
        findViewById<MaterialButton>(R.id.manageProfiles).setOnClickListener {
            managing = !managing
            renderProfiles()
            Toast.makeText(this, if (managing) "Tap a profile to remove it" else "Profile management closed", Toast.LENGTH_SHORT).show()
        }
        renderProfiles()
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
            setImageResource(if (add) android.R.drawable.ic_input_add else android.R.drawable.ic_menu_myplaces)
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
}
