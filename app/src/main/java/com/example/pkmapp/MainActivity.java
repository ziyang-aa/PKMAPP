package com.example.pkmapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.charts.ChartsFragment;
import com.example.pkmapp.databinding.ActivityMainBinding;
import com.example.pkmapp.details.DetailsFragment;
import com.example.pkmapp.exchange.ExchangeFragment;
import com.example.pkmapp.borrowing.BorrowingFragment;
import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.home.HomeFragment;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.profile.ProfileFragment;
import com.example.pkmapp.record.RecordFragment;
import com.example.pkmapp.savings.SavingsFragment;

public final class MainActivity extends AppCompatActivity {
    private static final String STATE_DESTINATION = "destination";

    private ActivityMainBinding binding;
    private AppDestination currentDestination = AppDestination.HOME;
    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InMemoryLedgerRepository.initialize(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            showDestination(AppDestination.HOME);
        } else {
            currentDestination = AppDestination.valueOf(
                    savedInstanceState.getString(STATE_DESTINATION, AppDestination.HOME.name()));
            syncBottomBar(currentDestination);
        }
    }

    public void showDestination(@NonNull AppDestination destination) {
        Fragment fragment = createFragment(destination);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment, destination.name())
                .commit();
        currentDestination = destination;
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
            default:
                throw new IllegalArgumentException("Unknown destination: " + destination);
        }
    }

    private void syncBottomBar(@NonNull AppDestination destination) {
        binding.navDetails.setChecked(destination == AppDestination.DETAILS);
        binding.navCharts.setChecked(destination == AppDestination.CHARTS);
        binding.navRecord.setChecked(destination == AppDestination.RECORD);
        binding.navSavings.setChecked(destination == AppDestination.SAVINGS);
        binding.navProfile.setChecked(destination == AppDestination.PROFILE);
        backPressedCallback.setEnabled(destination != AppDestination.HOME);
    }

    private void handleNavClick(@NonNull AppDestination destination) {
        showDestination(destination == currentDestination ? AppDestination.HOME : destination);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_DESTINATION, currentDestination.name());
        super.onSaveInstanceState(outState);
    }
}
