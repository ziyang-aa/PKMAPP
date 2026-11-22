package com.example.pkmapp;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.pkmapp.charts.ChartsFragment;
import com.example.pkmapp.databinding.ActivityMainBinding;
import com.example.pkmapp.details.DetailsFragment;
import com.example.pkmapp.exchange.ExchangeFragment;
import com.example.pkmapp.borrowing.BorrowingFragment;
import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.home.HomeFragment;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.profile.ProfileFragment;
import com.example.pkmapp.record.RecordFragment;
import com.example.pkmapp.savings.SavingsFragment;
import com.example.pkmapp.study.StudyFragment;
import com.example.pkmapp.study.StudyNotifications;
import com.example.pkmapp.forest.FrostedNavigationBar;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.forest.ForestButton;
import com.example.pkmapp.forest.ForestSurface;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.StudyBackdrop;
import com.google.android.material.button.MaterialButton;

public final class MainActivity extends AppCompatActivity {
    private static final String STATE_DESTINATION = "destination";
    private static final int BOTTOM_BAR_MARGIN_DP = 10;

    private ActivityMainBinding binding;
    private AppDestination currentDestination = AppDestination.HOME;
    private OnBackPressedCallback backPressedCallback;
    private FrostedNavigationBar studyNavigation;
    private StudyBackdrop studyBackdrop;
    private androidx.core.graphics.Insets systemBars = androidx.core.graphics.Insets.NONE;
    private final java.util.List<MaterialButton> studyButtons = new java.util.ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        InMemoryLedgerRepository.initialize(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        studyBackdrop = new StudyBackdrop(this);
        binding.bottomNavigation.setBackdropContent(binding.fragmentContainer);
        setContentView(binding.getRoot());
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
                                && !(fragment instanceof StudyFragment)) {
                            configureLedgerInsets((ViewGroup) view);
                        }
                    }
                }, false);
        createStudyNavigation();

        binding.navDetails.setCheckable(true);
        binding.navCharts.setCheckable(true);
        binding.navRecord.setCheckable(true);
        binding.navSavings.setCheckable(true);
        binding.navProfile.setCheckable(true);
        binding.navDetails.setOnClickListener(v -> handleNavClick(AppDestination.DETAILS));
        binding.navCharts.setOnClickListener(v -> handleNavClick(AppDestination.CHARTS));
        binding.navRecord.setOnClickListener(v -> handleNavClick(AppDestination.RECORD));
        binding.navSavings.setOnClickListener(v -> handleNavClick(AppDestination.SAVINGS));
        binding.navProfile.setOnClickListener(v -> handleNavClick(AppDestination.PROFILE));
        for (View navigation : new View[]{binding.navDetails,binding.navCharts,binding.navRecord,
                binding.navSavings,binding.navProfile}) ForestMotion.bind(navigation);

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showDestination(currentDestination == AppDestination.EXCHANGE
                        || currentDestination == AppDestination.BORROWING
                        ? AppDestination.DETAILS : AppDestination.HOME);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        if (savedInstanceState == null) {
            showDestination(HomeFragment.resolveStartupDestination(this));
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
        openStudyNotification(intent);
    }

    private void openStudyNotification(android.content.Intent intent) {
        String action = intent.getAction();
        String id = intent.getStringExtra(StudyNotifications.SESSION);
        if (com.example.pkmapp.todo.TodoNotifications.OPEN_TODO.equals(action)) {
            showDestination(AppDestination.STUDY_TODO, StudyFragment.newInstance(AppDestination.STUDY_TODO));
        } else if (StudyNotifications.OPEN_AGENDA.equals(action)) {
            StudyNotifications.selectAgendaPlan(this, id);
            showDestination(AppDestination.STUDY_TODO, StudyFragment.newInstance(AppDestination.STUDY_TODO));
        } else if (StudyNotifications.OPEN_TIMER.equals(action) || StudyNotifications.FINISH_TIMER.equals(action)) {
            if (StudyNotifications.selectTimerPlan(this, id)) {
                showDestination(AppDestination.STUDY_FOCUS, StudyFragment.fromNotification(action, id));
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

    private void showDestination(AppDestination destination, Fragment fragment) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (destination.isStudy() && currentDestination.isStudy() && ForestMotion.enabled())
            transaction.setCustomAnimations(R.anim.forest_page_enter,R.anim.forest_page_exit);
        transaction.setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment, destination.name())
                .commit();
        currentDestination = destination;
        HomeFragment.recordLastCottage(this, destination);
        syncBottomBar(destination);
    }

    public void showBorrowingComposer(@NonNull BorrowingDirection direction, long amountCents,
            long occurredAtMillis, @Nullable String sourceTransactionId) {
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
            case PROFILE:
                return new ProfileFragment();
            case EXCHANGE:
                return new ExchangeFragment();
            case BORROWING:
                return new BorrowingFragment();
            case STUDY_FOCUS:
            case STUDY_TODO:
            case STUDY_STATS:
            case STUDY_GARDEN:
                return StudyFragment.newInstance(destination);
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
        binding.bottomNavigation.setVisibility(destination != AppDestination.HOME && !destination.isStudy()
                ? View.VISIBLE : View.GONE);
        studyNavigation.setVisibility(destination.isStudy() ? View.VISIBLE : View.GONE);
        binding.bottomNavScrim.setVisibility(destination != AppDestination.HOME ? View.VISIBLE : View.GONE);
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
        binding.navDetails.setChecked(destination == AppDestination.DETAILS);
        binding.navCharts.setChecked(destination == AppDestination.CHARTS);
        binding.navRecord.setChecked(destination == AppDestination.RECORD);
        binding.navSavings.setChecked(destination == AppDestination.SAVINGS);
        binding.navProfile.setChecked(destination == AppDestination.PROFILE);
        backPressedCallback.setEnabled(destination != AppDestination.HOME);
    }

    private void handleNavClick(@NonNull AppDestination destination) {
        if (destination != currentDestination) showDestination(destination);
    }

    private void applySystemInsets() {
        boolean home = currentDestination == AppDestination.HOME;
        int systemBarColor = home ? getColor(R.color.cream_background) : Color.TRANSPARENT;
        getWindow().setStatusBarColor(systemBarColor);
        getWindow().setNavigationBarColor(systemBarColor);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(home);
        binding.getRoot().setPadding(systemBars.left, home ? systemBars.top : 0,
                systemBars.right, home ? systemBars.bottom : 0);
        FrameLayout.LayoutParams containerParams = (FrameLayout.LayoutParams) binding.fragmentContainer.getLayoutParams();
        int containerTop = home ? 0 : systemBars.top;
        if (containerParams.topMargin != containerTop) {
            containerParams.topMargin = containerTop;
            binding.fragmentContainer.setLayoutParams(containerParams);
        }
        positionBottomBar(binding.bottomNavigation, home ? 0 : systemBars.bottom);
        if (studyNavigation != null) {
            positionBottomBar(studyNavigation, home ? 0 : systemBars.bottom);
        }
        if (binding.bottomNavScrim != null) {
            int barH = currentDestination.isStudy() ? ForestUi.dp(this, 66) : binding.bottomNavigation.getLayoutParams().height;
            int scrimH = (barH * 2) / 3 + ForestUi.dp(this, BOTTOM_BAR_MARGIN_DP + 10) + (home ? 0 : systemBars.bottom);
            FrameLayout.LayoutParams scrimParams = (FrameLayout.LayoutParams) binding.bottomNavScrim.getLayoutParams();
            if (scrimParams.height != scrimH) {
                scrimParams.height = scrimH;
                binding.bottomNavScrim.setLayoutParams(scrimParams);
            }
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
            view.setPadding(left, top, right, Math.max(bottom + bars.bottom, keyboard));
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
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
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
}
