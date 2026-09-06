package com.example.pkmapp.savings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.navigation.AppDestination;

import com.example.pkmapp.databinding.FragmentSavingsBinding;

public final class SavingsFragment extends Fragment {
    private FragmentSavingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSavingsBinding.inflate(inflater, container, false);
        binding.pageBackButton.setOnClickListener(v -> ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
