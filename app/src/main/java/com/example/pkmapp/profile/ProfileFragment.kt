package com.example.pkmapp.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pkmapp.MainActivity
import com.example.pkmapp.R
import com.example.pkmapp.databinding.FragmentProfileBinding
import com.example.pkmapp.life.LifeSharedViewModel
import com.example.pkmapp.life.LifeSnapshot
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.record.MoneyParser
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private val viewModel: LifeSharedViewModel by activityViewModels {
        LifeSharedViewModel.factory(requireContext())
    }

    private val avatarController = AvatarCropSheetController(this) { cropped ->
        binding?.profileAvatarImage?.setImageBitmap(cropped)
    }

    private val backupCoordinator = BackupCoordinator(this) {
        viewModel.refresh()
    }

    private var binding: FragmentProfileBinding? = null
    private var assetAndLedgerDialogs: AssetAndLedgerDialogs? = null
    private var identityDialogs: ProfileIdentityDialogs? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentProfileBinding.inflate(inflater, container, false)
        binding = inflated
        return inflated.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val assetDialogs = AssetAndLedgerDialogs({ requireContext() }, viewModel)
        val idDialogs = ProfileIdentityDialogs({ requireContext() }, viewModel) {
            binding?.profileAvatarImage?.setImageResource(R.drawable.ic_launcher_character)
        }
        assetAndLedgerDialogs = assetDialogs
        identityDialogs = idDialogs

        b.pageBackButton.setOnClickListener {
            (requireActivity() as MainActivity).showDestination(AppDestination.HOME)
        }
        b.profileAvatarButton.setOnClickListener { avatarController.showAvatarPreview() }
        b.profileNicknameEdit.setOnClickListener { idDialogs.showNicknameEditor() }
        b.profileCheckinButton.setOnClickListener { idDialogs.showCheckInDialog() }
        b.profileManageAssetsButton.setOnClickListener { assetDialogs.showAssetManager() }
        b.profileCategoryManageButton.setOnClickListener { assetDialogs.showCategoryManager() }
        b.profileLedgerManageButton.setOnClickListener { assetDialogs.showLedgerManager() }
        b.profileExportDataButton.setOnClickListener { backupCoordinator.startExport() }
        b.profileImportDataButton.setOnClickListener { backupCoordinator.startImport() }
        b.profileClearDataButton.setOnClickListener { idDialogs.confirmClearData() }

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val savedAvatar = withContext(Dispatchers.IO) { AvatarStore.load(appContext) }
            if (savedAvatar != null) {
                binding?.profileAvatarImage?.setImageBitmap(savedAvatar)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapshot.collect { snapshot ->
                    renderProfileSummary(snapshot)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun renderProfileSummary(snapshot: LifeSnapshot) {
        val b = binding ?: return
        b.profileNickname.text = snapshot.nickname
        b.profileGuardianName.text = snapshot.nickname

        val days = snapshot.checkedDates.size
        val level = CheckInProgress.levelForDays(days)
        val levelName = CheckInProgress.nameForLevel(level)
        b.profileLevelText.text = "Lv.$level · $levelName"
        b.profileStreakText.text = "已在这里生活 $days 天"
        b.profileCheckinSummary.text = "累计打卡 $days 天 · Lv.$level $levelName"
        val checkedToday = snapshot.checkedDates.contains(
            ProfileIdentityDialogs.formatDate(Calendar.getInstance())
        )
        b.profileCheckinStatus.text = if (checkedToday) "今日已签到" else "去签到 ›"
        b.profileCategoryCount.text = "${snapshot.totalCategoryCount} 个分类  ·›"
        b.profileLedgerCount.text = "${snapshot.ledgers.size} 本账本  ·›"

        val assets = snapshot.assetSnapshot
        val netFlow = snapshot.currentLedgerNetFlowCents
        b.profileAssetTotal.text = MoneyParser.formatCents(assets.currentAssetsInCents)
        b.profileNetWorth.text = MoneyParser.formatCents(assets.netWorthInCents)
        b.profileLiability.text = MoneyParser.formatCents(assets.liabilitiesInCents)
        if (assets.initialAssetsInCents == 0L && netFlow == 0L) {
            b.profileAssetSubtitle.text = "当前资产 · 记账后会自动更新"
        } else {
            val flowPrefix = if (netFlow > 0L) "+" else ""
            b.profileAssetSubtitle.text = "初始资产 " +
                MoneyParser.formatCents(assets.initialAssetsInCents) +
                " · 历史账本累计 " + flowPrefix + MoneyParser.formatCents(netFlow)
        }
    }

    override fun onDestroyView() {
        avatarController.dismissActiveSheet()
        assetAndLedgerDialogs?.dismissActiveSheet()
        identityDialogs?.dismissActiveSheet()
        assetAndLedgerDialogs = null
        identityDialogs = null
        binding = null
        super.onDestroyView()
    }
}
