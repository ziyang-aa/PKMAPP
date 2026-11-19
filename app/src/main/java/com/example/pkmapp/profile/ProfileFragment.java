package com.example.pkmapp.profile;

import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.backup.BackupCodec;
import com.example.pkmapp.backup.BackupImportResult;
import com.example.pkmapp.backup.BackupManager;
import com.example.pkmapp.backup.BackupSnapshot;
import com.example.pkmapp.data.AssetSnapshot;
import com.example.pkmapp.data.AssetStore;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.LedgerDataListener;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentProfileBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.borrowing.BorrowingRecordStore;
import com.example.pkmapp.record.MoneyParser;
import com.example.pkmapp.record.CategoryStore;
import com.example.pkmapp.savings.SavingsGoalStore;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.example.pkmapp.forest.ForestDialogBuilder;

import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfileFragment extends Fragment {
    private static final String PREFS = "forest_profile";
    private static final String CHECKED_DATES = "checked_dates";
    private static final String NICKNAME = "nickname";
    private static final String DEFAULT_NICKNAME = "森林守护员";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
    private final LedgerDataListener ledgerDataListener = this::handleLedgerDataChanged;
    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::handleAvatarPicked);
    private final ActivityResultLauncher<String> exportPicker = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::handleExportDestination);
    private final ActivityResultLauncher<String[]> importPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::handleImportSelected);
    private FragmentProfileBinding binding;
    private SharedPreferences preferences;
    private AssetStore assetStore;
    private CategoryStore categoryStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        preferences = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        assetStore = new AssetStore(requireContext());
        categoryStore = new CategoryStore(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.profileAvatarButton.setOnClickListener(v -> showAvatarPreview());
        binding.profileNicknameEdit.setOnClickListener(v -> showNicknameEditor());
        binding.profileCheckinButton.setOnClickListener(v -> showCheckInDialog());
        binding.profileManageAssetsButton.setOnClickListener(v -> showAssetManager());
        binding.profileCategoryManageButton.setOnClickListener(v -> showCategoryManager());
        binding.profileLedgerManageButton.setOnClickListener(v -> showLedgerManager());
        binding.profileExportDataButton.setOnClickListener(v -> startExport());
        binding.profileImportDataButton.setOnClickListener(v -> startImport());
        binding.profileClearDataButton.setOnClickListener(v -> confirmClearData());
        Bitmap savedAvatar = AvatarStore.load(requireContext());
        if (savedAvatar != null) {
            binding.profileAvatarImage.setImageBitmap(savedAvatar);
        }
        renderProfileIdentity();
        renderProfileSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        repository.addListener(ledgerDataListener);
        renderProfileSummary();
    }

    @Override
    public void onPause() {
        repository.removeListener(ledgerDataListener);
        super.onPause();
    }

    private void renderProfileSummary() {
        if (binding == null || preferences == null) {
            return;
        }
        renderProfileIdentity();
        int days = checkedDates().size();
        int level = CheckInProgress.levelForDays(days);
        binding.profileLevelText.setText("Lv." + level + " · " + CheckInProgress.nameForLevel(level));
        binding.profileStreakText.setText("已在这里生活 " + days + " 天");
        binding.profileCheckinSummary.setText("累计打卡 " + days + " 天 · Lv." + level + " "
                + CheckInProgress.nameForLevel(level));
        boolean checkedToday = checkedDates().contains(formatDate(Calendar.getInstance()));
        binding.profileCheckinStatus.setText(checkedToday ? "今日已签到" : "去签到 ›");
        binding.profileCategoryCount.setText(categoryStore.getCategoryCount() + " 个分类  ·›");
        binding.profileLedgerCount.setText(repository.getLedgers().size() + " 本账本  ·›");
        renderAssetSummary();
    }

    private void handleLedgerDataChanged() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(this::renderProfileSummary);
    }

    private void renderProfileIdentity() {
        if (binding == null || preferences == null) {
            return;
        }
        String nickname = preferences.getString(NICKNAME, DEFAULT_NICKNAME);
        binding.profileNickname.setText(nickname);
        binding.profileGuardianName.setText(nickname);
    }

    private void renderAssetSummary() {
        if (binding == null || assetStore == null) {
            return;
        }
        AssetSnapshot snapshot = currentAssetSnapshot();
        binding.profileAssetTotal.setText(MoneyParser.formatCents(
                snapshot.getCurrentAssetsInCents()));
        binding.profileNetWorth.setText(MoneyParser.formatCents(snapshot.getNetWorthInCents()));
        binding.profileLiability.setText(MoneyParser.formatCents(snapshot.getLiabilitiesInCents()));
    }

    private AssetSnapshot currentAssetSnapshot() {
        return assetStore.getSnapshot(repository.getCurrentLedger().getId(),
                repository.getCurrentLedgerNetFlowInCents());
    }

    private void startExport() {
        exportPicker.launch("芽叶记备份_" + formatDate(Calendar.getInstance())
                + ".pkmbackup.json");
    }

    private void startImport() {
        importPicker.launch(new String[]{"application/json", "text/plain", "*/*"});
    }

    private void handleExportDestination(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        backupExecutor.execute(() -> {
            try {
                String encoded = BackupCodec.encode(new BackupManager(context).capture());
                ContentResolver resolver = context.getContentResolver();
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null) {
                        throw new IOException("无法打开备份文件");
                    }
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                            output, StandardCharsets.UTF_8))) {
                        writer.write(encoded);
                    }
                }
                postBackupUi(() -> Toast.makeText(requireContext(), "账单已导出",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception exception) {
                postBackupUi(() -> Toast.makeText(requireContext(),
                        "导出失败：" + safeMessage(exception), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void handleImportSelected(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        backupExecutor.execute(() -> {
            try {
                String encoded = readText(context.getContentResolver(), uri);
                BackupSnapshot snapshot = BackupCodec.decode(encoded);
                postBackupUi(() -> showImportModeDialog(snapshot));
            } catch (Exception exception) {
                postBackupUi(() -> Toast.makeText(requireContext(),
                        "导入失败：" + safeMessage(exception), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showImportModeDialog(BackupSnapshot snapshot) {
        String message = "备份包含 " + snapshot.getLedgers().size() + " 个账本、"
                + snapshot.getTransactions().size() + " 笔交易、"
                + snapshot.getSavingsGoals().size() + " 个攒钱目标。\n\n"
                + "请选择导入方式";
        new ForestDialogBuilder(requireContext())
                .setTitle("导入账单")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setNeutralButton("合并导入", (dialog, which) -> runImport(snapshot,
                        BackupManager.ImportMode.MERGE))
                .setPositiveButton("完全覆盖", (dialog, which) -> confirmOverwrite(snapshot))
                .show();
    }

    private void confirmOverwrite(BackupSnapshot snapshot) {
        new ForestDialogBuilder(requireContext())
                .setTitle("确认完全覆盖？")
                .setMessage("当前账本、交易、攒钱目标、资产和借钱记录将被备份内容替换。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认覆盖", (dialog, which) -> runImport(snapshot,
                        BackupManager.ImportMode.OVERWRITE))
                .show();
    }

    private void runImport(BackupSnapshot snapshot, BackupManager.ImportMode mode) {
        Context context = requireContext().getApplicationContext();
        backupExecutor.execute(() -> {
            try {
                BackupImportResult result = new BackupManager(context)
                        .importSnapshot(snapshot, mode);
                postBackupUi(() -> {
                    renderProfileSummary();
                    Toast.makeText(requireContext(), importResultMessage(result, mode),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) {
                postBackupUi(() -> Toast.makeText(requireContext(),
                        "导入失败：" + safeMessage(exception), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String importResultMessage(BackupImportResult result, BackupManager.ImportMode mode) {
        if (mode == BackupManager.ImportMode.OVERWRITE) {
            return "已恢复 " + result.getImportedTransactions() + " 笔交易和 "
                    + result.getImportedSavingsGoals() + " 个攒钱目标";
        }
        return "合并完成：新增 " + result.getImportedTransactions() + " 笔交易，跳过重复 "
                + result.getSkippedTransactions() + " 笔，新增 " + result.getImportedSavingsGoals()
                + " 个攒钱目标";
    }

    private String readText(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("无法读取备份文件");
        }
        try (InputStream stream = input;
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        stream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        }
    }

    private void postBackupUi(Runnable action) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (isAdded() && binding != null) {
                action.run();
            }
        });
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "文件处理失败" : exception.getMessage();
    }

    private void showAssetManager() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_profile_assets, null);
        EditText initialAssets = root.findViewById(R.id.profile_initial_assets_input);
        EditText initialLiabilities = root.findViewById(R.id.profile_initial_liabilities_input);
        TextView current = root.findViewById(R.id.profile_asset_dialog_current);
        TextView netWorth = root.findViewById(R.id.profile_asset_dialog_net_worth);
        TextView liabilities = root.findViewById(R.id.profile_asset_dialog_liability);
        AssetSnapshot snapshot = currentAssetSnapshot();
        initialAssets.setText(inputAmount(snapshot.getInitialAssetsInCents()));
        initialLiabilities.setText(inputAmount(snapshot.getLiabilitiesInCents()));
        current.setText(MoneyParser.formatCents(snapshot.getCurrentAssetsInCents()));
        netWorth.setText(MoneyParser.formatCents(snapshot.getNetWorthInCents()));
        liabilities.setText(MoneyParser.formatCents(snapshot.getLiabilitiesInCents()));

        root.findViewById(R.id.profile_asset_dialog_close).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.profile_asset_dialog_cancel).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_asset_dialog_save).setOnClickListener(v -> {
            long initialAssetsCents;
            try {
                initialAssetsCents = MoneyParser.parseYuanToCentsAllowZero(
                        initialAssets.getText().toString());
            } catch (IllegalArgumentException exception) {
                initialAssets.setError(exception.getMessage());
                return;
            }
            long initialLiabilitiesCents;
            try {
                initialLiabilitiesCents = MoneyParser.parseYuanToCentsAllowZero(
                        initialLiabilities.getText().toString());
            } catch (IllegalArgumentException exception) {
                initialLiabilities.setError(exception.getMessage());
                return;
            }
            assetStore.saveStartingValues(repository.getCurrentLedger().getId(),
                    initialAssetsCents, initialLiabilitiesCents);
            renderProfileSummary();
            dialog.dismiss();
            Toast.makeText(requireContext(), "资产底子已保存，之后会随收支更新",
                    Toast.LENGTH_SHORT).show();
        });
        showPaperSheet(dialog, root);
    }

    private void confirmClearData() {
        new ForestDialogBuilder(requireContext())
                .setTitle("清空全部数据？")
                .setMessage("账本、交易、攒钱目标、资产、负债、借还记录和小屋资料都会恢复到初始状态，此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (dialog, which) -> clearAllUserData())
                .show();
    }

    private void clearAllUserData() {
        Context context = requireContext();
        new SavingsGoalStore(context).clear();
        new BorrowingRecordStore(context).clear();
        assetStore.clear();
        preferences.edit().clear().apply();
        AvatarStore.clear(context);
        repository.resetToInitialState();
        binding.profileAvatarImage.setImageResource(R.drawable.ic_launcher_character);
        renderProfileSummary();
        Toast.makeText(context, "已恢复初始状态", Toast.LENGTH_SHORT).show();
    }

    private String currentNickname() {
        return preferences.getString(NICKNAME, DEFAULT_NICKNAME);
    }

    private void showCategoryManager() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout root = managementRoot("CATEGORY MANAGEMENT", "管理记账分类",
                "删除只影响以后记账，历史账单会保留原分类。\n在记账页点击“自定义”可以新增分类。");
        ScrollView listScroll = new ScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        addCategorySection(list, TransactionType.EXPENSE, "支出分类");
        addCategorySection(list, TransactionType.INCOME, "收入分类");
        listScroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(430));
        scrollParams.topMargin = dp(12);
        root.addView(listScroll, scrollParams);
        TextView done = managementAction("完成", R.color.forest_green);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        doneParams.topMargin = dp(8);
        root.addView(done, doneParams);
        showPaperSheet(dialog, root);
    }

    private void addCategorySection(LinearLayout list, TransactionType type, String title) {
        TextView section = managementLabel(title, 14, R.color.wood_brown);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sectionParams.topMargin = dp(8);
        list.addView(section, sectionParams);
        for (String category : categoryStore.getCategories(type)) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0);
            row.setBackgroundResource(R.drawable.bg_details_picker_option);
            TextView name = managementLabel(category, 14, R.color.ink);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
            TextView delete = managementAction("删除", R.color.expense_red);
            delete.setContentDescription("删除分类" + category);
            delete.setOnClickListener(v -> confirmDeleteCategory(type, category));
            row.addView(delete, new LinearLayout.LayoutParams(dp(56), dp(48)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            rowParams.topMargin = dp(6);
            list.addView(row, rowParams);
        }
    }

    private void confirmDeleteCategory(TransactionType type, String category) {
        new ForestDialogBuilder(requireContext())
                .setTitle("删除分类？")
                .setMessage("“" + category + "”会从以后记账的分类列表中移除，历史账单不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) -> {
                    if (categoryStore.deleteCategory(type, category)) {
                        renderProfileSummary();
                        Toast.makeText(requireContext(), "分类已删除", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void showLedgerManager() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout root = managementRoot("FOREST LEDGERS", "管理账本",
                "点击账本名称可切换。删除账本会同时删除它的交易、资产和借还记录。");
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        for (Ledger ledger : repository.getLedgers()) {
            addLedgerRow(list, dialog, ledger);
        }
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView create = managementAction("＋  新建账本", R.color.forest_green);
        create.setBackgroundResource(R.drawable.bg_details_picker_selected);
        create.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateLedgerDialog();
        });
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        createParams.topMargin = dp(12);
        root.addView(create, createParams);
        TextView done = managementAction("完成", R.color.wood_brown);
        done.setOnClickListener(v -> dialog.dismiss());
        root.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        showPaperSheet(dialog, root);
    }

    private void addLedgerRow(LinearLayout list, BottomSheetDialog dialog, Ledger ledger) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(8), 0);
        row.setBackgroundResource(R.drawable.bg_details_picker_option);
        TextView name = managementLabel(ledger.getName()
                + (ledger.getId().equals(repository.getCurrentLedger().getId()) ? "  · 当前" : ""),
                14, R.color.ink);
        name.setContentDescription("切换到" + ledger.getName());
        name.setOnClickListener(v -> {
            repository.switchLedger(ledger.getId());
            dialog.dismiss();
        });
        row.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView delete = managementAction("删除", R.color.expense_red);
        delete.setContentDescription("删除账本" + ledger.getName());
        delete.setOnClickListener(v -> confirmDeleteLedger(dialog, ledger));
        row.addView(delete, new LinearLayout.LayoutParams(dp(56), dp(48)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rowParams.topMargin = dp(6);
        list.addView(row, rowParams);
    }

    private void confirmDeleteLedger(BottomSheetDialog managerDialog, Ledger ledger) {
        int transactionCount = 0;
        for (com.example.pkmapp.data.Transaction transaction : repository.getAllTransactions()) {
            if (ledger.getId().equals(transaction.getLedgerId())) {
                transactionCount++;
            }
        }
        new ForestDialogBuilder(requireContext())
                .setTitle("删除账本？")
                .setMessage("将删除“" + ledger.getName() + "”及其中 " + transactionCount
                        + " 笔交易、资产和借还记录，此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) -> {
                    try {
                        if (repository.getLedgers().size() <= 1) {
                            Toast.makeText(requireContext(), "至少保留一本账本", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new BorrowingRecordStore(requireContext()).deleteForLedger(ledger.getId());
                        assetStore.deleteStartingValues(ledger.getId());
                        repository.deleteLedger(ledger.getId());
                        managerDialog.dismiss();
                        renderProfileSummary();
                        Toast.makeText(requireContext(), "账本已删除", Toast.LENGTH_SHORT).show();
                    } catch (IllegalStateException exception) {
                        Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void showCreateLedgerDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("例如：北京旅行账本");
        new ForestDialogBuilder(requireContext())
                .setTitle("新建账本")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建并切换", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入账本名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.switchLedger(repository.createLedger(name).getId());
                    renderProfileSummary();
                    Toast.makeText(requireContext(), "账本已创建", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private LinearLayout managementRoot(String eyebrowText, String titleText, String hintText) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.setBackgroundResource(R.drawable.bg_details_picker_sheet);
        TextView eyebrow = managementLabel(eyebrowText, 9, R.color.forest_green);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        TextView title = managementLabel(titleText, 21, R.color.wood_brown);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        TextView hint = managementLabel(hintText, 12, R.color.wood_brown);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private TextView managementLabel(String value, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(requireContext(), color));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView managementAction(String value, int color) {
        TextView view = managementLabel(value, 13, color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void showAvatarPreview() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_avatar_preview, null);
        ImageView preview = root.findViewById(R.id.profile_avatar_preview_image);
        Bitmap savedAvatar = AvatarStore.load(requireContext());
        if (savedAvatar != null) {
            preview.setImageBitmap(savedAvatar);
        }
        root.findViewById(R.id.profile_avatar_preview_close).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_avatar_preview_change).setOnClickListener(v -> {
            dialog.dismiss();
            openAvatarPicker();
        });
        showPaperSheet(dialog, root);
    }

    private void openAvatarPicker() {
        avatarPicker.launch("image/*");
    }

    private void showNicknameEditor() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_profile_nickname, null);
        EditText input = root.findViewById(R.id.profile_nickname_input);
        input.setText(currentNickname());
        input.setSelection(input.length());
        root.findViewById(R.id.profile_nickname_dialog_close).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_nickname_dialog_cancel).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_nickname_dialog_save).setOnClickListener(v -> {
            String nickname = input.getText().toString().trim();
            if (nickname.isEmpty()) {
                input.setError("昵称不能为空");
                return;
            }
            preferences.edit().putString(NICKNAME, nickname).apply();
            renderProfileIdentity();
            dialog.dismiss();
            Toast.makeText(requireContext(), "森林昵称已更新", Toast.LENGTH_SHORT).show();
        });
        showPaperSheet(dialog, root);
    }

    private void handleAvatarPicked(@Nullable Uri uri) {
        if (uri == null || getContext() == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        avatarExecutor.execute(() -> {
            Bitmap decoded = null;
            String error = null;
            try {
                decoded = decodeAvatarBitmap(context.getContentResolver(), uri);
            } catch (IOException | RuntimeException exception) {
                error = exception.getMessage();
            }
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            Bitmap result = decoded;
            String message = error;
            activity.runOnUiThread(() -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (result == null) {
                    Toast.makeText(requireContext(), message == null ? "这张图片暂时无法使用" : "图片读取失败",
                            Toast.LENGTH_SHORT).show();
                } else {
                    showAvatarCrop(result);
                }
            });
        });
    }

    private Bitmap decodeAvatarBitmap(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法打开图片");
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("图片尺寸无效");
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法打开图片");
            }
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) {
                throw new IOException("无法解码图片");
            }
            return bitmap;
        }
    }

    private void showAvatarCrop(Bitmap bitmap) {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_avatar_crop, null);
        FrameLayout cropStage = root.findViewById(R.id.avatar_crop_stage);
        AvatarCropView cropView = new AvatarCropView(requireContext());
        cropStage.addView(cropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cropView.setBitmap(bitmap);
        root.findViewById(R.id.profile_avatar_dialog_close).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_avatar_dialog_cancel).setOnClickListener(
                v -> dialog.dismiss());
        root.findViewById(R.id.profile_avatar_dialog_save).setOnClickListener(v -> {
            Bitmap cropped = cropView.getCroppedBitmap();
            if (AvatarStore.save(requireContext(), cropped)) {
                binding.profileAvatarImage.setImageBitmap(cropped);
                dialog.dismiss();
                Toast.makeText(requireContext(), "头像已放进森林档案", Toast.LENGTH_SHORT).show();
            } else {
                cropped.recycle();
                Toast.makeText(requireContext(), "头像保存失败，请稍后再试", Toast.LENGTH_SHORT).show();
            }
        });
        showPaperSheet(dialog, root);
    }

    private void showCheckInDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_checkin, null);
        TextView close = root.findViewById(R.id.checkin_close);
        TextView previous = root.findViewById(R.id.checkin_previous_month);
        TextView next = root.findViewById(R.id.checkin_next_month);
        TextView monthLabel = root.findViewById(R.id.checkin_month_label);
        TextView levelLabel = root.findViewById(R.id.checkin_level_text);
        TextView progressLabel = root.findViewById(R.id.checkin_progress_label);
        ProgressBar progressBar = root.findViewById(R.id.checkin_progress);
        TextView nextLevelLabel = root.findViewById(R.id.checkin_next);
        TextView checkIn = root.findViewById(R.id.checkin_action);
        GridLayout calendar = root.findViewById(R.id.checkin_calendar_grid);
        LinearLayout weekdays = root.findViewById(R.id.checkin_weekdays);
        String[] weekdayLabels = {"一", "二", "三", "四", "五", "六", "日"};
        for (String label : weekdayLabels) {
            TextView weekday = text(label, 9, R.color.wood_brown);
            weekday.setGravity(Gravity.CENTER);
            weekdays.addView(weekday, new LinearLayout.LayoutParams(0, dp(24), 1));
        }

        close.setOnClickListener(v -> dialog.dismiss());
        Calendar visibleMonth = Calendar.getInstance();
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        View.OnClickListener redraw = v -> {
            renderCheckInProgress(levelLabel, progressLabel, progressBar, nextLevelLabel, checkIn);
            renderCalendar(calendar, monthLabel, visibleMonth);
        };
        previous.setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, -1);
            redraw.onClick(v);
        });
        next.setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, 1);
            redraw.onClick(v);
        });
        checkIn.setOnClickListener(v -> {
            String today = formatDate(Calendar.getInstance());
            Set<String> dates = checkedDates();
            if (CheckInProgress.checkInToday(today, today, dates)) {
                saveCheckedDates(dates);
                renderProfileSummary();
                Toast.makeText(requireContext(), "今天的森林足迹已留下", Toast.LENGTH_SHORT).show();
                renderCheckInProgress(levelLabel, progressLabel, progressBar, nextLevelLabel, checkIn);
                renderCalendar(calendar, monthLabel, visibleMonth);
            } else {
                Toast.makeText(requireContext(), "今天已经打卡，明天再来吧", Toast.LENGTH_SHORT).show();
            }
        });
        renderCheckInProgress(levelLabel, progressLabel, progressBar, nextLevelLabel, checkIn);
        renderCalendar(calendar, monthLabel, visibleMonth);
        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void renderCheckInProgress(TextView levelLabel, TextView progressLabel,
            ProgressBar progressBar, TextView nextLevelLabel, TextView checkIn) {
        int days = checkedDates().size();
        int level = CheckInProgress.levelForDays(days);
        int nextThreshold = CheckInProgress.nextThresholdForLevel(level);
        boolean capped = level >= CheckInProgress.maxLevel();
        int currentThreshold = CheckInProgress.currentThresholdForLevel(level);
        int progress = capped ? 100 : Math.round((days - currentThreshold) * 100f
                / Math.max(1, nextThreshold - currentThreshold));
        levelLabel.setText("Lv." + level + " · " + CheckInProgress.nameForLevel(level));
        progressLabel.setText(capped ? days + " 天 · 已达上限" : days + " / " + nextThreshold + " 天");
        progressBar.setProgress(Math.max(0, Math.min(100, progress)));
        if (capped) {
            nextLevelLabel.setText("已达到最高等级，森林会一直记得你的脚印。");
        } else {
            nextLevelLabel.setText("再坚持 " + (nextThreshold - days) + " 天，升级为 Lv."
                    + (level + 1) + " · " + CheckInProgress.nameForLevel(level + 1));
        }
        boolean checkedToday = checkedDates().contains(formatDate(Calendar.getInstance()));
        checkIn.setText(checkedToday ? "今日已签到" : "今日签到");
        checkIn.setTextColor(ContextCompat.getColor(requireContext(),
                checkedToday ? R.color.wood_brown : R.color.paper_light));
        checkIn.setEnabled(!checkedToday);
    }

    private void renderCalendar(GridLayout calendar, TextView monthLabel, Calendar visibleMonth) {
        calendar.removeAllViews();
        monthLabel.setText(String.format(Locale.CHINA, "%d 年 %02d 月",
                visibleMonth.get(Calendar.YEAR), visibleMonth.get(Calendar.MONTH) + 1));
        Set<String> dates = checkedDates();
        Calendar cursor = (Calendar) visibleMonth.clone();
        int firstDay = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        if (firstDay < 0) {
            firstDay += 7;
        }
        int daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH);
        int previousMonthDays = 0;
        if (firstDay > 0) {
            Calendar previousMonth = (Calendar) visibleMonth.clone();
            previousMonth.add(Calendar.MONTH, -1);
            previousMonthDays = previousMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        Calendar today = Calendar.getInstance();
        for (int index = 0; index < 42; index++) {
            int day = index - firstDay + 1;
            Calendar cellDate = (Calendar) visibleMonth.clone();
            boolean outside = false;
            if (day < 1) {
                cellDate.add(Calendar.MONTH, -1);
                cellDate.set(Calendar.DAY_OF_MONTH, previousMonthDays + day);
                outside = true;
            } else if (day > daysInMonth) {
                cellDate.add(Calendar.MONTH, 1);
                cellDate.set(Calendar.DAY_OF_MONTH, day - daysInMonth);
                outside = true;
            } else {
                cellDate.set(Calendar.DAY_OF_MONTH, day);
            }
            int shownDay = cellDate.get(Calendar.DAY_OF_MONTH);
            String key = formatDate(cellDate);
            boolean checked = !outside && dates.contains(key);
            boolean isToday = !outside && today.get(Calendar.YEAR) == cellDate.get(Calendar.YEAR)
                    && today.get(Calendar.DAY_OF_YEAR) == cellDate.get(Calendar.DAY_OF_YEAR);
            TextView cell = new TextView(requireContext());
            cell.setText(String.valueOf(shownDay));
            cell.setTextSize(10);
            cell.setGravity(Gravity.CENTER);
            cell.setContentDescription(shownDay + "日" + (checked ? "，已签到" : "，未签到"));
            cell.setTextColor(ContextCompat.getColor(requireContext(), outside ? R.color.wood_brown
                    : checked ? R.color.paper_light : R.color.ink));
            cell.setAlpha(outside ? 0.5f : 1f);
            cell.setBackground(calendarCell(checked, isToday));
            calendar.addView(cell, cellParams());
        }
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(48);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private GradientDrawable calendarCell(boolean checked, boolean today) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(14));
        drawable.setColor(ContextCompat.getColor(requireContext(), checked
                ? R.color.forest_green : android.R.color.transparent));
        if (today) {
            drawable.setStroke(dp(2), ContextCompat.getColor(requireContext(), R.color.warm_orange));
        }
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(requireContext(), color));
        return view;
    }

    private Set<String> checkedDates() {
        return new HashSet<>(preferences.getStringSet(CHECKED_DATES, Collections.emptySet()));
    }

    private void saveCheckedDates(Set<String> dates) {
        preferences.edit().putStringSet(CHECKED_DATES, new HashSet<>(dates)).apply();
    }

    private String formatDate(Calendar calendar) {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(calendar.getTime());
    }

    private String inputAmount(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private void showPaperSheet(BottomSheetDialog dialog, View root) {
        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        avatarExecutor.shutdownNow();
        backupExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        assetStore = null;
    }
}
