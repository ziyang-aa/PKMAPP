package com.example.pkmapp.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.databinding.FragmentHomeBinding;

public final class HomeFragment extends Fragment {
    private static final String STATE_SCROLL_X = "scroll_x";

    private FragmentHomeBinding binding;
    private int restoredScrollX;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restoredScrollX = savedInstanceState.getInt(STATE_SCROLL_X);
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.homeScroll.post(() -> {
            int sceneWidth = PanoramaSizing.requiredWidthPx(binding.homeScroll.getWidth());
            ViewGroup.LayoutParams sceneParams = binding.panoramaScene.getRoot().getLayoutParams();
            sceneParams.width = sceneWidth;
            binding.panoramaScene.getRoot().setLayoutParams(sceneParams);

            binding.panoramaScene.bulbasaurSleeping.setX(sceneWidth * 0.12f);
            binding.panoramaScene.treeckoWaiting.setX(sceneWidth * 0.47f);
            binding.panoramaScene.turtwigSaving.setX(sceneWidth * 0.76f);
            binding.homeScroll.scrollTo(restoredScrollX, 0);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt(STATE_SCROLL_X, binding.homeScroll.getScrollX());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
