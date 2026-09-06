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
import com.example.pkmapp.home.HomeFragment;
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
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNavigation.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            AppDestination destination = destinationForButton(checkedId);
            if (destination == null) {
                return;
            }
            if (isChecked && destination != currentDestination) {
                showDestination(destination);
            } else if (!isChecked && destination == currentDestination) {
                showDestination(AppDestination.HOME);
            }
        });

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showDestination(AppDestination.HOME);
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
            default:
                throw new IllegalArgumentException("Unknown destination: " + destination);
        }
    }

    private void syncBottomBar(@NonNull AppDestination destination) {
        int buttonId = buttonForDestination(destination);
        if (buttonId == 0) {
            binding.bottomNavigation.clearChecked();
        } else {
            binding.bottomNavigation.check(buttonId);
        }
        backPressedCallback.setEnabled(destination != AppDestination.HOME);
    }

    @Nullable
    private AppDestination destinationForButton(int buttonId) {
        if (buttonId == R.id.nav_details) {
            return AppDestination.DETAILS;
        }
        if (buttonId == R.id.nav_charts) {
            return AppDestination.CHARTS;
        }
        if (buttonId == R.id.nav_record) {
            return AppDestination.RECORD;
        }
        if (buttonId == R.id.nav_savings) {
            return AppDestination.SAVINGS;
        }
        if (buttonId == R.id.nav_profile) {
            return AppDestination.PROFILE;
        }
        return null;
    }

    private int buttonForDestination(@NonNull AppDestination destination) {
        switch (destination) {
            case DETAILS:
                return R.id.nav_details;
            case CHARTS:
                return R.id.nav_charts;
            case RECORD:
                return R.id.nav_record;
            case SAVINGS:
                return R.id.nav_savings;
            case PROFILE:
                return R.id.nav_profile;
            case HOME:
            default:
                return 0;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_DESTINATION, currentDestination.name());
        super.onSaveInstanceState(outState);
    }
}
