package com.kanha.dev

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.addTextChangedListener

/** Native search entry screen following lib/features/home/search_screen.dart. */
class SearchActivity : AppCompatActivity() {
    private lateinit var query: EditText
    private lateinit var clear: ImageButton
    private lateinit var emptyLabel: TextView
    private val voiceRequest = 4207

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(11, 11, 15)
        window.navigationBarColor = Color.rgb(11, 11, 15)
        setContentView(createContent())
    }

    private fun createContent(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 15))
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.search_back_arrow); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Back"
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(52))
            setOnClickListener { finish() }
        }
        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = pill(0xFF20212A.toInt(), 50); setPadding(dp(16), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(8) }
        }
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.search_field_icon); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)); contentDescription = "Search"
        }
        query = EditText(this).apply {
            hint = "Search movies & TV"; setHintTextColor(0xFFA7A7B2.toInt())
            setTextColor(Color.WHITE); textSize = 16f; setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH; setPadding(dp(14), 0, dp(4), 0)
            background = null; layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clear.visibility = if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                    emptyLabel.text = if (s.isNullOrEmpty()) "Search for something to watch" else "Search for “$s”"
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        clear = ImageButton(this).apply {
            setImageResource(R.drawable.search_clear_close); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(36))
            visibility = android.view.View.GONE; contentDescription = "Clear search"
            setOnClickListener { query.text?.clear(); query.requestFocus() }
        }
        val mic = ImageButton(this).apply {
            setImageResource(R.drawable.search_mic); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(36))
            contentDescription = "Voice search"; setOnClickListener { startVoiceSearch() }
        }
        field.addView(searchIcon); field.addView(query); field.addView(clear); field.addView(mic)
        val filter = ImageButton(this).apply {
            setImageResource(R.drawable.search_filter_tune); background = null
            scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Search filters"
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(12) }
            setOnClickListener { showSearchOptions() }
        }
        top.addView(back); top.addView(field); top.addView(filter); root.addView(top)

        root.addView(android.view.View(this).apply {
            setBackgroundColor(0xFF1B1A20.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(80) }
        })
        val empty = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
            translationY = -dp(12).toFloat()
        }
        center.addView(ImageView(this).apply {
            setImageResource(R.drawable.search_empty_state); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(26) }
            contentDescription = "Search"
        })
        emptyLabel = TextView(this).apply {
            text = "Search for something to watch"; setTextColor(0xFFA7A7B2.toInt())
            textSize = 16f; gravity = Gravity.CENTER; letterSpacing = 0.015f
        }
        center.addView(emptyLabel); empty.addView(center); root.addView(empty)
        return root
    }

    private fun showSearchOptions() {
        val options = arrayOf("Movies & TV", "Movies only", "TV shows only")
        android.app.AlertDialog.Builder(this).setTitle("Search scope")
            .setSingleChoiceItems(options, 0) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies & TV")
        }
        runCatching { startActivityForResult(intent, voiceRequest) }
    }

    @Deprecated("Legacy callback for Android recognizer compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == voiceRequest && resultCode == Activity.RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query.setText(it) }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
        query.clearFocus()
    }
    private fun pill(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(color)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
