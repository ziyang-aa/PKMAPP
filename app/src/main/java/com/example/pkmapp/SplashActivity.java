package com.example.pkmapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Displays the illustrated launch page briefly before opening the notebook. */
public final class SplashActivity extends AppCompatActivity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable openNotebook = this::openNotebook;
    private boolean displayTimerStarted;
    private boolean openedNotebook;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        setContentView(R.layout.activity_splash);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !displayTimerStarted) {
            displayTimerStarted = true;
            mainHandler.postDelayed(openNotebook, SplashScreenTiming.displayDurationMillis());
        }
    }

    private void openNotebook() {
        if (openedNotebook || isFinishing()) {
            return;
        }
        openedNotebook = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(openNotebook);
        super.onDestroy();
    }
}
