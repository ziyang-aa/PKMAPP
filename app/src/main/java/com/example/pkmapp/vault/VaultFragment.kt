package com.example.pkmapp.vault

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
import com.example.pkmapp.databinding.FragmentVaultBinding
import com.example.pkmapp.life.LifeSharedViewModel
import com.example.pkmapp.life.LifeSnapshot
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.profile.AssetAndLedgerDialogs
import com.example.pkmapp.record.MoneyParser
import kotlinx.coroutines.launch

class VaultFragment : Fragment() {

    private val viewModel: LifeSharedViewModel by activityViewModels {
        LifeSharedViewModel.factory(requireContext())
    }

    private var binding: FragmentVaultBinding? = null
    private var assetAndLedgerDialogs: AssetAndLedgerDialogs? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentVaultBinding.inflate(inflater, container, false)
        binding = inflated
        return inflated.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val dialogs = AssetAndLedgerDialogs({ requireContext() }, viewModel)
        assetAndLedgerDialogs = dialogs

        b.pageBackButton.setOnClickListener {
            (requireActivity() as MainActivity).showDestination(AppDestination.HOME)
        }
        b.vaultLedgerSwitchButton.setOnClickListener { dialogs.showLedgerManager() }
        b.vaultManageAssetsButton.setOnClickListener { dialogs.showAssetManager() }
        b.vaultLedgerManageButton.setOnClickListener { dialogs.showLedgerManager() }
        b.vaultCategoryManageButton.setOnClickListener { dialogs.showCategoryManager() }
        b.vaultBorrowingToolButton.setOnClickListener {
            (requireActivity() as MainActivity).openLifeTool(
                AppDestination.BORROWING,
                AppDestination.VAULT
            )
        }
        b.vaultExchangeToolButton.setOnClickListener {
            (requireActivity() as MainActivity).openLifeTool(
                AppDestination.EXCHANGE,
                AppDestination.VAULT
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapshot.collect { snapshot ->
                    renderVault(snapshot)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun renderVault(snapshot: LifeSnapshot) {
        val b = binding ?: return
        b.vaultLedgerSwitchButton.text = "${snapshot.currentLedger.name} ▾"
        b.vaultLedgerCount.text = "${snapshot.ledgers.size} 本账本 ›"
        b.vaultCategoryCount.text = "${snapshot.totalCategoryCount} 个分类 ›"

        val assets = snapshot.assetSnapshot
        val netFlow = snapshot.currentLedgerNetFlowCents
        b.vaultNetWorth.text = MoneyParser.formatCents(assets.netWorthInCents)
        b.vaultAssetTotal.text = MoneyParser.formatCents(assets.currentAssetsInCents)
        b.vaultInitialAssets.text = MoneyParser.formatCents(assets.initialAssetsInCents)
        b.vaultLiability.text = MoneyParser.formatCents(assets.liabilitiesInCents)

        if (assets.initialAssetsInCents == 0L && netFlow == 0L) {
            b.vaultAssetSubtitle.text = "当前账本净资产 · 记账后会自动更新"
        } else {
            val flowPrefix = if (netFlow > 0L) "+" else ""
            b.vaultAssetSubtitle.text = "收支净流入 ${flowPrefix}${MoneyParser.formatCents(netFlow)}"
        }
    }

    override fun onDestroyView() {
        assetAndLedgerDialogs?.dismissActiveSheet()
        assetAndLedgerDialogs = null
        binding = null
        super.onDestroyView()
    }
}
