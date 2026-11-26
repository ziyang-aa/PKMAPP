package com.example.pkmapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.pkmapp.account.AccountSheets;
import com.example.pkmapp.account.AuthActivity;
import com.example.pkmapp.account.HomeDrawerController;
import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.borrowing.BorrowingFragment;
import com.example.pkmapp.charts.ChartsFragment;
import com.example.pkmapp.core.account.AuthRepository;
import com.example.pkmapp.core.account.GuestMergeSummary;
import com.example.pkmapp.core.database.LocalAccountEntity;
import com.example.pkmapp.core.work.ReminderRescheduleWorker;
import com.example.pkmapp.core.work.SyncWorker;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.databinding.ActivityMainBinding;
import com.example.pkmapp.details.DetailsFragment;
import com.example.pkmapp.exchange.ExchangeFragment;
import com.example.pkmapp.feedback.FeedbackFragment;
import com.example.pkmapp.forest.ForestButton;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSurface;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.forest.FrostedNavigationBar;
import com.example.pkmapp.forest.StudyBackdrop;
import com.example.pkmapp.home.HomeFragment;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.profile.AvatarCropSheetController;
import com.example.pkmapp.profile.BackupCoordinator;
import com.example.pkmapp.profile.ProfileIdentityDialogs;
import com.example.pkmapp.record.RecordFragment;
import com.example.pkmapp.savings.SavingsFragment;
import com.example.pkmapp.study.BaseStudyFragment;
import com.example.pkmapp.study.StudyFocusFragment;
import com.example.pkmapp.study.StudyGardenFragment;
import com.example.pkmapp.study.StudyNotifications;
import com.example.pkmapp.study.StudySharedViewModel;
import com.example.pkmapp.study.StudyStatsFragment;
import com.example.pkmapp.study.StudyTodoFragment;
import com.example.pkmapp.vault.VaultFragment;
import com.google.android.material.button.MaterialButton;

public final class MainActivity extends AppCompatActivity {
    public static final String EXTRA_WIDGET_DESTINATION = "com.example.pkmapp.EXTRA_WIDGET_DESTINATION";
    private static final String STATE_DESTINATION = "destination";
    private static final int BOTTOM_BAR_MARGIN_DP = 10;

    private ActivityMainBinding binding;
    private AppDestination currentDestination = AppDestination.HOME;
    private AppDestination lifeToolReturnTarget = AppDestination.DETAILS;
    private OnBackPressedCallback backPressedCallback;
    private FrostedNavigationBar studyNavigation;
    private StudyBackdrop studyBackdrop;
    private androidx.core.graphics.Insets systemBars = androidx.core.graphics.Insets.NONE;
    private final java.util.List<MaterialButton> studyButtons = new java.util.ArrayList<>();

    private LifeSharedViewModel lifeViewModel;
    private StudySharedViewModel studyViewModel;
    private AvatarCropSheetController avatarController;
    private BackupCoordinator backupCoordinator;
    private ProfileIdentityDialogs identityDialogs;
    private AccountSheets accountSheets;
    private HomeDrawerController drawerController;
    private ActivityResultLauncher<Intent> authLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        return;
                    }
                    notifyActiveAccountOrProfileChanged();
                    Intent data = result.getData();
                    if (data != null && data.getBooleanExtra(AuthActivity.EXTRA_PROMPT_GUEST_MERGE, false)) {
                        maybeShowGuestMergeSheet();
                    } else if (data != null) {
                        String nick = data.getStringExtra(AuthActivity.EXTRA_ACCOUNT_NICKNAME);
                        if (nick != null && !nick.isEmpty()) {
                            Toast.makeText(this, "欢迎回来，" + nick, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        InMemoryLedgerRepository.initialize(this);
        ReminderRescheduleWorker.enqueuePeriodic(this);
        SyncWorker.enqueuePeriodic(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        studyBackdrop = new StudyBackdrop(this);
        binding.bottomNavigation.setBackdropContent(binding.fragmentContainer);
        setContentView(binding.getRoot());

        lifeViewModel = LifeSharedViewModel.get(this);
        studyViewModel = StudySharedViewModel.get(this);

        avatarController = new AvatarCropSheetController(this, bmp -> {
            notifyActiveAccountOrProfileChanged();
            return kotlin.Unit.INSTANCE;
        });
        backupCoordinator = new BackupCoordinator(this, () -> {
            lifeViewModel.refresh();
            if (drawerController != null) drawerController.refreshIfVisible();
            return kotlin.Unit.INSTANCE;
        });
        identityDialogs = new ProfileIdentityDialogs(
                () -> this,
                lifeViewModel,
                () -> {
                    notifyActiveAccountOrProfileChanged();
                    return kotlin.Unit.INSTANCE;
                });
        accountSheets = new AccountSheets(
                () -> this,
                () -> androidx.lifecycle.LifecycleOwnerKt.getLifecycleScope(this),
                () -> !isFinishing() && !isDestroyed(),
                backupCoordinator,
                () -> {
                    notifyActiveAccountOrProfileChanged();
                    return kotlin.Unit.INSTANCE;
                });
        drawerController = new HomeDrawerController(
                this,
                binding.homeDrawerScrim,
                binding.homeDrawerPanel,
                binding.homeDrawerContent,
                lifeViewModel,
                avatarController,
                identityDialogs,
                accountSheets,
                destination -> {
                    showDestination(destination);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    notifyActiveAccountOrProfileChanged();
                    return kotlin.Unit.INSTANCE;
                });

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            applySystemInsets();
            return insets;
        });
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager manager,
                            @NonNull Fragment fragment, @NonNull View view, @Nullable Bundle state) {
                        if (view instanceof ViewGroup && !(fragment instanceof HomeFragment)
                                && !(fragment instanceof BaseStudyFragment)) {
                            configureLedgerInsets((ViewGroup) view);
                        }
                    }
                }, false);
        createStudyNavigation();

        binding.navDetails.present(ForestSurface.Kind.NAV, 0, 0, ForestUi.GREEN);
        binding.navCharts.present(ForestSurface.Kind.NAV, 0, 0, ForestUi.GREEN);
        binding.navSavings.present(ForestSurface.Kind.NAV, 0, 0, ForestUi.GREEN);
        binding.navVault.present(ForestSurface.Kind.NAV, 0, 0, ForestUi.GREEN);
        binding.navDetails.setOnClickListener(v -> handleNavClick(AppDestination.DETAILS));
        binding.navCharts.setOnClickListener(v -> handleNavClick(AppDestination.CHARTS));
        binding.navSavings.setOnClickListener(v -> handleNavClick(AppDestination.SAVINGS));
        binding.navVault.setOnClickListener(v -> handleNavClick(AppDestination.VAULT));
        binding.fabRecord.setOnClickListener(v -> showDestination(AppDestination.RECORD));
        for (View navigation : new View[]{binding.navDetails, binding.navCharts,
                binding.navSavings, binding.navVault, binding.fabRecord}) {
            ForestMotion.bind(navigation);
        }

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (drawerController != null && drawerController.isOpen()) {
                    drawerController.close();
                    updateBackCallback();
                    return;
                }
                if (currentDestination == AppDestination.EXCHANGE
                        || currentDestination == AppDestination.BORROWING) {
                    navigateBackFromLifeTool();
                } else {
                    showDestination(AppDestination.HOME);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        if (savedInstanceState == null) {
            AppDestination widgetTarget = parseWidgetDestination(getIntent());
            showDestination(widgetTarget != null ? widgetTarget : HomeFragment.resolveStartupDestination(this));
            if (widgetTarget != null) {
                getIntent().removeExtra(EXTRA_WIDGET_DESTINATION);
            }
            if (getIntent().getBooleanExtra(AuthActivity.EXTRA_PROMPT_GUEST_MERGE, false)) {
                getIntent().removeExtra(AuthActivity.EXTRA_PROMPT_GUEST_MERGE);
                binding.getRoot().post(this::maybeShowGuestMergeSheet);
            }
        } else {
            currentDestination = AppDestination.valueOf(
                    savedInstanceState.getString(STATE_DESTINATION, AppDestination.HOME.name()));
            syncBottomBar(currentDestination);
        }
        openStudyNotification(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AppDestination widgetTarget = parseWidgetDestination(intent);
        if (widgetTarget != null) {
            intent.removeExtra(EXTRA_WIDGET_DESTINATION);
            showDestination(widgetTarget);
            return;
        }
        openStudyNotification(intent);
    }

    @Nullable
    private static AppDestination parseWidgetDestination(@Nullable Intent intent) {
        if (intent == null) return null;
        String raw = intent.getStringExtra(EXTRA_WIDGET_DESTINATION);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return AppDestination.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void openStudyNotification(android.content.Intent intent) {
        String action = intent.getAction();
        String id = intent.getStringExtra(StudyNotifications.SESSION);
        if (com.example.pkmapp.todo.TodoNotifications.OPEN_TODO.equals(action)) {
            showDestination(AppDestination.STUDY_TODO, new StudyTodoFragment());
        } else if (StudyNotifications.OPEN_AGENDA.equals(action)) {
            StudyNotifications.selectAgendaPlan(this, id);
            showDestination(AppDestination.STUDY_TODO, new StudyTodoFragment());
        } else if (StudyNotifications.OPEN_TIMER.equals(action) || StudyNotifications.FINISH_TIMER.equals(action)) {
            if (StudyNotifications.selectTimerPlan(this, id)) {
                showDestination(AppDestination.STUDY_FOCUS, StudyFocusFragment.fromNotification(action, id));
            }
        } else {
            return;
        }
        intent.setAction(null);
        intent.removeExtra(StudyNotifications.SESSION);
    }

    public void showDestination(@NonNull AppDestination destination) {
        showDestination(destination, createFragment(destination));
    }

    public void openLifeTool(@NonNull AppDestination toolDestination, @NonNull AppDestination returnTo) {
        lifeToolReturnTarget = returnTo;
        showDestination(toolDestination);
    }

    public void navigateBackFromLifeTool() {
        showDestination(lifeToolReturnTarget == AppDestination.VAULT
                ? AppDestination.VAULT : AppDestination.DETAILS);
    }

    public void openHomeDrawer() {
        if (drawerController != null) {
            drawerController.open();
            updateBackCallback();
        }
    }

    public void closeHomeDrawer() {
        if (drawerController != null) {
            drawerController.close();
            updateBackCallback();
        }
    }

    public void showAuthSheet(boolean registerTab) {
        String step = registerTab ? AuthActivity.STEP_REGISTER : null;
        Intent intent = AuthActivity.createIntent(this, true, step);
        if (authLauncher != null) {
            authLauncher.launch(intent);
        } else if (accountSheets != null) {
            accountSheets.showAuthSheet(registerTab);
        }
    }

    public void logoutAndOpenAuth() {
        Intent intent = AuthActivity.createIntent(this, false, AuthActivity.STEP_PICK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void maybeShowGuestMergeSheet() {
        if (accountSheets == null || isFinishing() || isDestroyed()) {
            return;
        }
        AuthRepository repo = new AuthRepository(getApplicationContext());
        LocalAccountEntity account = repo.currentAccount();
        if (account.isGuest()) {
            return;
        }
        GuestMergeSummary summary = repo.inspectGuestMergeCandidate();
        if (summary.getHasMergeableData()) {
            accountSheets.showGuestMergeSheet(account, summary);
        }
    }

    public void showBackupAndSyncSheet() {
        if (accountSheets != null) {
            accountSheets.showBackupAndSyncSheet();
        }
    }

    private void notifyActiveAccountOrProfileChanged() {
        lifeViewModel.reloadForAccountSwitch();
        studyViewModel.syncFromStores(this);
        if (drawerController != null) {
            drawerController.refreshIfVisible();
        }
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof HomeFragment) {
            ((HomeFragment) current).refreshFromHost();
        }
    }

    private void showDestination(AppDestination destination, Fragment fragment) {
        if (drawerController != null && drawerController.isOpen()) {
            drawerController.close();
        }
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (destination.isStudy() && currentDestination.isStudy() && ForestMotion.enabled())
            transaction.setCustomAnimations(R.anim.forest_page_enter, R.anim.forest_page_exit);
        transaction.setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment, destination.name())
                .commit();
        currentDestination = destination;
        if (destination != AppDestination.FEEDBACK) {
            HomeFragment.recordLastCottage(this, destination);
        }
        syncBottomBar(destination);
    }

    public void showBorrowingComposer(@NonNull BorrowingDirection direction, long amountCents,
            long occurredAtMillis, @Nullable String sourceTransactionId) {
        lifeToolReturnTarget = AppDestination.DETAILS;
        Fragment fragment = BorrowingFragment.newComposer(direction, amountCents,
                occurredAtMillis, sourceTransactionId);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment, AppDestination.BORROWING.name())
                .commit();
        currentDestination = AppDestination.BORROWING;
        syncBottomBar(AppDestination.BORROWING);
    }

    public void showEditTransaction(@NonNull Transaction transaction) {
        Fragment fragment = RecordFragment.newEditor(transaction);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment, AppDestination.RECORD.name())
                .commit();
        currentDestination = AppDestination.RECORD;
        syncBottomBar(AppDestination.RECORD);
    }

    @NonNull
    private Fragment createFragment(@NonNull AppDestination destination) {
        switch (destination) {
            case HOME:
                return new HomeFragment();
            case DETAILS:
                return new DetailsFragment();
            case CHARTS:
                return new ChartsFragment();
            case RECORD:
                return new RecordFragment();
            case SAVINGS:
                return new SavingsFragment();
            case VAULT:
                return new VaultFragment();
            case EXCHANGE:
                return new ExchangeFragment();
            case BORROWING:
                return new BorrowingFragment();
            case FEEDBACK:
                return new FeedbackFragment();
            case STUDY_FOCUS:
                return new StudyFocusFragment();
            case STUDY_TODO:
                return new StudyTodoFragment();
            case STUDY_STATS:
                return new StudyStatsFragment();
            case STUDY_GARDEN:
                return new StudyGardenFragment();
            default:
                throw new IllegalArgumentException("Unknown destination: " + destination);
        }
    }

    private void syncBottomBar(@NonNull AppDestination destination) {
        applySystemInsets();
        if (destination.isStudy()) {
            if (binding.getRoot().getBackground() != studyBackdrop)
                binding.getRoot().setBackground(studyBackdrop);
        } else if (binding.getRoot().getBackground() == studyBackdrop) {
            binding.getRoot().setBackgroundResource(R.drawable.bg_fixed_forest_backdrop);
        }
        boolean showLifeBar = destination != AppDestination.HOME
                && destination != AppDestination.FEEDBACK
                && !destination.isStudy();
        binding.bottomNavigation.setVisibility(showLifeBar ? View.VISIBLE : View.GONE);
        studyNavigation.setVisibility(destination.isStudy() ? View.VISIBLE : View.GONE);
        binding.bottomNavScrim.setVisibility(
                destination != AppDestination.HOME && destination != AppDestination.FEEDBACK
                        ? View.VISIBLE : View.GONE);
        binding.fabRecord.setVisibility(destination.showsLifeRecordFab() ? View.VISIBLE : View.GONE);
        binding.bottomNavigation.refreshGlass();
        studyNavigation.refreshGlass();
        ViewCompat.requestApplyInsets(binding.getRoot());

        AppDestination[] studyPages = {
                AppDestination.STUDY_FOCUS, AppDestination.STUDY_TODO, AppDestination.STUDY_STATS, AppDestination.STUDY_GARDEN};
        for (int i = 0; i < studyButtons.size(); i++) {
            boolean selected = destination == studyPages[i];
            studyButtons.get(i).setSelected(selected);
            int ink = selected ? ForestUi.DEEP : 0xff4d5746;
            studyButtons.get(i).setTextColor(ink);
            studyButtons.get(i).setIconTint(ColorStateList.valueOf(ink));
        }

        syncLifeNavTab(binding.navDetails, destination == AppDestination.DETAILS);
        syncLifeNavTab(binding.navCharts, destination == AppDestination.CHARTS);
        syncLifeNavTab(binding.navSavings, destination == AppDestination.SAVINGS);
        syncLifeNavTab(binding.navVault, destination == AppDestination.VAULT);
        updateBackCallback();
    }

    private void syncLifeNavTab(MaterialButton button, boolean selected) {
        button.setSelected(selected);
        int ink = selected ? ForestUi.DEEP : 0xff4d5746;
        button.setTextColor(ink);
        button.setIconTint(ColorStateList.valueOf(ink));
    }

    private void updateBackCallback() {
        if (backPressedCallback != null) {
            boolean drawerOpen = drawerController != null && drawerController.isOpen();
            backPressedCallback.setEnabled(drawerOpen || currentDestination != AppDestination.HOME);
        }
    }

    private void handleNavClick(@NonNull AppDestination destination) {
        if (destination != currentDestination) showDestination(destination);
    }

    private void applySystemInsets() {
        boolean fullBleed = currentDestination == AppDestination.HOME
                || currentDestination == AppDestination.FEEDBACK;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        binding.getRoot().setPadding(systemBars.left, 0, systemBars.right, 0);
        FrameLayout.LayoutParams containerParams = (FrameLayout.LayoutParams) binding.fragmentContainer.getLayoutParams();
        if (containerParams.topMargin != 0) {
            containerParams.topMargin = 0;
            binding.fragmentContainer.setLayoutParams(containerParams);
        }
        int bottomSafe = fullBleed ? 0 : systemBars.bottom;
        positionBottomBar(binding.bottomNavigation, bottomSafe);
        if (studyNavigation != null) {
            positionBottomBar(studyNavigation, bottomSafe);
        }
        if (binding.fabRecord != null) {
            FrameLayout.LayoutParams fabParams = (FrameLayout.LayoutParams) binding.fabRecord.getLayoutParams();
            int fabBottom = binding.bottomNavigation.getLayoutParams().height
                    + ForestUi.dp(this, BOTTOM_BAR_MARGIN_DP + 14) + bottomSafe;
            if (fabParams.bottomMargin != fabBottom) {
                fabParams.bottomMargin = fabBottom;
                binding.fabRecord.setLayoutParams(fabParams);
            }
        }
        if (binding.bottomNavScrim != null) {
            int barH = currentDestination.isStudy()
                    ? ForestUi.dp(this, 66)
                    : binding.bottomNavigation.getLayoutParams().height;
            int scrimH = (barH * 2) / 3 + ForestUi.dp(this, BOTTOM_BAR_MARGIN_DP + 10) + bottomSafe;
            FrameLayout.LayoutParams scrimParams = (FrameLayout.LayoutParams) binding.bottomNavScrim.getLayoutParams();
            if (scrimParams.height != scrimH) {
                scrimParams.height = scrimH;
                binding.bottomNavScrim.setLayoutParams(scrimParams);
            }
        }
        if (drawerController != null) {
            drawerController.applyInsets(systemBars);
        }
    }

    private void positionBottomBar(View bar, int systemBottom) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bar.getLayoutParams();
        int bottom = ForestUi.dp(this, BOTTOM_BAR_MARGIN_DP) + systemBottom;
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            bar.setLayoutParams(params);
        }
    }

    private void configureLedgerInsets(ViewGroup scroll) {
        int left = scroll.getPaddingLeft();
        int top = scroll.getPaddingTop();
        int right = scroll.getPaddingRight();
        int bottom = Math.max(scroll.getPaddingBottom(), binding.bottomNavigation.getLayoutParams().height
                + ForestUi.dp(this, BOTTOM_BAR_MARGIN_DP + 28));
        scroll.setClipToPadding(false);
        androidx.core.view.OnApplyWindowInsetsListener listener = (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(left, top + bars.top, right, Math.max(bottom + bars.bottom, keyboard));
            return insets;
        };
        ViewCompat.setOnApplyWindowInsetsListener(scroll, listener);
        WindowInsetsCompat initial = ViewCompat.getRootWindowInsets(binding.getRoot());
        if (initial != null) listener.onApplyWindowInsets(scroll, initial);
    }

    private void createStudyNavigation() {
        studyNavigation = new FrostedNavigationBar(this);
        studyNavigation.setBackdropContent(binding.fragmentContainer);
        studyNavigation.setId(R.id.study_bottom_navigation);
        studyNavigation.setOrientation(LinearLayout.HORIZONTAL);
        studyNavigation.setGravity(Gravity.CENTER);
        studyNavigation.setElevation(ForestUi.dp(this, 8));
        studyNavigation.setPadding(ForestUi.dp(this, 8), ForestUi.dp(this, 6),
                ForestUi.dp(this, 8), ForestUi.dp(this, 6));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                -1, ForestUi.dp(this, 66), Gravity.BOTTOM);
        params.setMargins(ForestUi.dp(this, 12), 0, ForestUi.dp(this, 12), ForestUi.dp(this, 10));
        binding.getRoot().addView(studyNavigation, params);
        String[] labels = {"专注", "待办", "学习足迹", "小林地"};
        int[] icons = {R.drawable.ic_study_book, R.drawable.ic_list, R.drawable.ic_study_trail, R.drawable.ic_study_sprout};
        int[] ids = {R.id.nav_study_focus, R.id.nav_study_todo, R.id.nav_study_stats, R.id.nav_study_garden};
        AppDestination[] destinations = {
                AppDestination.STUDY_FOCUS, AppDestination.STUDY_TODO, AppDestination.STUDY_STATS, AppDestination.STUDY_GARDEN};
        for (int i = 0; i < labels.length; i++) {
            MaterialButton button = ForestUi.button(this, labels[i], false);
            button.setId(ids[i]);
            button.setIconResource(icons[i]);
            button.setIconTint(ColorStateList.valueOf(ForestUi.DEEP));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
            button.setIconSize(ForestUi.dp(this, 20));
            button.setIconPadding(ForestUi.dp(this, 3));
            button.setStrokeWidth(0);
            button.setTextSize(11);
            ((ForestButton) button).present(ForestSurface.Kind.NAV, 0, 0, ForestUi.GREEN);
            final AppDestination target = destinations[i];
            button.setOnClickListener(v -> handleNavClick(target));
            studyButtons.add(button);
            studyNavigation.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_DESTINATION, currentDestination.name());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (accountSheets != null) accountSheets.dismissActiveSheet();
        if (identityDialogs != null) identityDialogs.dismissActiveSheet();
        if (avatarController != null) avatarController.dismissActiveSheet();
        super.onDestroy();
    }
}
