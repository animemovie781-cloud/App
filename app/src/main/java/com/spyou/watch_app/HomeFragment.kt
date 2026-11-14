package com.kanha.dev

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.kanha.dev.databinding.FragmentHomeBinding

/**
 * Native Home presentation. The Dart source uses FeaturedCarousel (max 6 items,
 * 5s auto-advance, 750ms page motion), provider-composed HomeRows and tracker
 * sections. This first native pass wires the same view structure and carousel
 * timing; provider catalogue/repository migration is a separate integration.
 */
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val heroHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentPage = 0
    private val heroTimer = object : Runnable {
        override fun run() {
            val pager = _binding?.heroPager ?: return
            if ((pager.adapter?.itemCount ?: 0) > 1) {
                pager.setCurrentItem((currentPage + 1) % pager.adapter!!.itemCount, true)
                heroHandler.postDelayed(this, 5_000L)
            }
        }
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        // Keep shortcut PNGs at the original Dart UI's 20dp icon size.
        listOf(binding.scheduleCard, binding.genresCard).forEach { card ->
            val label = (0 until (card as ViewGroup).childCount)
                .mapNotNull { card.getChildAt(it) as? TextView }.firstOrNull()
            label?.let { text ->
                val drawables = text.compoundDrawablesRelative
                drawables[0]?.setBounds(0, 0, dp(20), dp(20))
                text.setCompoundDrawablesRelative(drawables[0], drawables[1], drawables[2], drawables[3])
                text.compoundDrawablePadding = dp(8)
            }
        }
        // No provider catalogue is connected in the native port yet. Keep the real empty state visible instead of showing fabricated hero artwork.
        binding.heroPager.visibility = View.GONE
        binding.heroDots.visibility = View.GONE
        binding.heroPager.adapter = HeroSlides()
        binding.heroPager.isUserInputEnabled = true
        binding.heroPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                renderDots(position)
            }
        })
        renderDots(0)
        binding.heroDetails.setOnClickListener {
            Toast.makeText(requireContext(), "Install a provider to open title details", Toast.LENGTH_SHORT).show()
        }
        binding.scheduleCard.setOnClickListener { startActivity(android.content.Intent(requireContext(), ScheduleHubActivity::class.java)) }
        binding.genresCard.setOnClickListener { Toast.makeText(requireContext(), "Genres", Toast.LENGTH_SHORT).show() }
        binding.actionSearch.setOnClickListener { Toast.makeText(requireContext(), "Search", Toast.LENGTH_SHORT).show() }
        binding.actionDownloads.setOnClickListener { Toast.makeText(requireContext(), "Downloads", Toast.LENGTH_SHORT).show() }
        binding.actionNotifications.setOnClickListener { Toast.makeText(requireContext(), "Notifications", Toast.LENGTH_SHORT).show() }
        binding.recentSeeAll.setOnClickListener { Toast.makeText(requireContext(), "Recently released", Toast.LENGTH_SHORT).show() }
        addEmptyState(binding.recentPosters)
    }

    private fun renderDots(active: Int) {
        val holder = binding.heroDots
        holder.removeAllViews()
        val count = binding.heroPager.adapter?.itemCount ?: 1
        repeat(count) { index ->
            val dot = View(requireContext()).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(if (index == active) getColorCompat(R.color.z_accent) else 0x66777782)
                }
            }
            holder.addView(dot, LinearLayout.LayoutParams(if (index == active) dp(34) else dp(8), dp(8)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
    }

    private fun addEmptyState(parent: LinearLayout) {
        parent.removeAllViews()
        parent.addView(TextView(requireContext()).apply {
            text = "Connect a source to discover titles"
            textSize = 13f
            setTextColor(getColorCompat(R.color.z_text_secondary))
            setPadding(0, dp(4), dp(20), dp(4))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private inner class HeroSlides : androidx.recyclerview.widget.RecyclerView.Adapter<HeroSlides.Holder>() {
        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val root = FrameLayout(requireContext()).apply {
                layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -1)
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF173A5C.toInt(), 0xFF101B31.toInt(), 0xFF0B0B0F.toInt())
                )
            }
            return Holder(root)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.root.removeAllViews()
            holder.root.addView(TextView(requireContext()).apply {
                text = "ZANGETSU"
                textSize = 13f
                letterSpacing = .28f
                setTextColor(0x66FFFFFF)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        inner class Holder(val root: FrameLayout) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root)
    }

    override fun onResume() {
        super.onResume()
        heroHandler.removeCallbacks(heroTimer)
        heroHandler.postDelayed(heroTimer, 5_000L)
    }

    override fun onPause() {
        heroHandler.removeCallbacks(heroTimer)
        super.onPause()
    }

    override fun onDestroyView() {
        heroHandler.removeCallbacks(heroTimer)
        _binding = null
        super.onDestroyView()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun getColorCompat(id: Int) = requireContext().getColor(id)
}
