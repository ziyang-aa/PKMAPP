package com.example.pkmapp.feedback

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pkmapp.MainActivity
import com.example.pkmapp.core.database.FeedbackRecordEntity
import com.example.pkmapp.core.feedback.FeedbackRepository
import com.example.pkmapp.databinding.FragmentFeedbackBinding
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.navigation.AppDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedbackFragment : Fragment() {

    private var binding: FragmentFeedbackBinding? = null
    private lateinit var repository: FeedbackRepository
    private var selectedCategory = FeedbackRepository.Category.SUGGESTION
    private val categoryChips = ArrayList<Pair<FeedbackRepository.Category, TextView>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentFeedbackBinding.inflate(inflater, container, false)
        binding = inflated
        return inflated.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        repository = FeedbackRepository(requireContext())

        b.pageBackButton.setOnClickListener {
            (requireActivity() as MainActivity).showDestination(AppDestination.HOME)
        }

        setupCategoryChips(b.feedbackCategoryRow)
        b.feedbackCharCount.text = "0 / 500"
        b.feedbackContentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val len = s?.toString()?.trim()?.length ?: 0
                binding?.feedbackCharCount?.text = "$len / 500"
                binding?.feedbackErrorText?.visibility = View.GONE
            }
        })

        b.feedbackSubmitButton.setOnClickListener { submitFeedback() }
        refreshHistory(tryRemote = true)
    }

    private fun setupCategoryChips(row: LinearLayout) {
        row.removeAllViews()
        categoryChips.clear()
        val ctx = requireContext()
        FeedbackRepository.Category.entries.forEachIndexed { idx, cat ->
            val chip = TextView(ctx).apply {
                text = cat.label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCategory = cat
                    updateChipStyles()
                }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx > 0) marginStart = dp(6)
            }
            row.addView(chip, lp)
            categoryChips.add(cat to chip)
        }
        updateChipStyles()
    }

    private fun updateChipStyles() {
        val ctx = context ?: return
        for ((cat, view) in categoryChips) {
            val active = cat == selectedCategory
            view.background = ForestUi.rounded(
                ctx,
                if (active) 0xFFE9EEDC.toInt() else 0xFFFAF6EC.toInt(),
                if (active) 0xFF456444.toInt() else 0x225C4938,
                999
            )
            view.setTextColor(if (active) 0xFF315E3A.toInt() else 0xFF7B5C3B.toInt())
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun submitFeedback() {
        val b = binding ?: return
        val content = b.feedbackContentInput.text?.toString().orEmpty()
        val contact = b.feedbackContactInput.text?.toString().orEmpty()
        b.feedbackSubmitButton.isEnabled = false
        b.feedbackErrorText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.submit(selectedCategory, content, contact)
            }
            if (!isAdded || binding == null) return@launch
            b.feedbackSubmitButton.isEnabled = true
            when (result) {
                is FeedbackRepository.SubmitResult.ValidationError -> {
                    b.feedbackErrorText.text = result.message
                    b.feedbackErrorText.visibility = View.VISIBLE
                }
                is FeedbackRepository.SubmitResult.Saved -> {
                    b.feedbackContentInput.setText("")
                    b.feedbackContactInput.setText("")
                    val msg = if (result.syncedRemote) "已提交至服务器，感谢你的建议" else "已保存在本机队列，联网并配置后台后自动发送"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    refreshHistory(tryRemote = false)
                }
            }
        }
    }

    private fun refreshHistory(tryRemote: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (tryRemote) repository.refreshFromRemoteIfConfigured() else repository.listMine()
            }
            if (!isAdded || binding == null) return@launch
            renderHistory(items)
        }
    }

    private fun renderHistory(items: List<FeedbackRecordEntity>) {
        val b = binding ?: return
        val ctx = requireContext()
        b.feedbackHistoryCount.text = "共 ${items.size} 条"
        val container = b.feedbackHistoryContainer
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(ctx).apply {
                text = "还没有提交过反馈，期待听见你的声音。"
                textSize = 13f
                setTextColor(0xFF7B5C3B.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            container.addView(empty)
            return
        }

        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        items.forEachIndexed { index, item ->
            if (index > 0) {
                val divider = View(ctx).apply {
                    setBackgroundColor(0x185C4938)
                }
                container.addView(
                    divider,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                )
            }
            val block = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val catBadge = TextView(ctx).apply {
                text = FeedbackRepository.Category.labelFor(item.category)
                textSize = 11f
                setTextColor(0xFF315E3A.toInt())
                setTypeface(null, Typeface.BOLD)
                background = ForestUi.rounded(ctx, 0xFFE9EEDC.toInt(), 0, 999)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            val resolved = item.status == "resolved"
            val statusBadge = TextView(ctx).apply {
                text = FeedbackRepository.statusLabel(item.status)
                textSize = 11f
                setTextColor(if (resolved) 0xFF315E3A.toInt() else 0xFF9E4C37.toInt())
                background = ForestUi.rounded(
                    ctx,
                    if (resolved) 0xFFE9EEDC.toInt() else 0xFFF6E6D7.toInt(),
                    0,
                    999
                )
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            val timeView = TextView(ctx).apply {
                text = if (item.createdAt > 0L) fmt.format(Date(item.createdAt)) else ""
                textSize = 11f
                setTextColor(0xFF847462.toInt())
                fontFeatureSettings = "tnum"
                gravity = Gravity.END
            }
            topRow.addView(catBadge)
            topRow.addView(statusBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            })
            topRow.addView(timeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            block.addView(topRow)

            val contentView = TextView(ctx).apply {
                text = item.content
                textSize = 13.5f
                setTextColor(0xFF243624.toInt())
                setPadding(0, dp(8), 0, 0)
            }
            block.addView(contentView)

            if (item.reply.isNotBlank()) {
                val replyView = TextView(ctx).apply {
                    text = "开发者回复：${item.reply}"
                    textSize = 12f
                    setTextColor(0xFF315E3A.toInt())
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(dp(10), dp(6), 0, 0)
                }
                block.addView(replyView)
            }
            container.addView(block)
        }
    }

    private fun dp(value: Int): Int =
        ForestUi.dp(requireContext(), value.toFloat())

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
