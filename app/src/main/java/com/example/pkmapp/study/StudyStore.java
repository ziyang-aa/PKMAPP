package com.example.pkmapp.study;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/** All learning state is committed together, without touching any financial preferences. */
public final class StudyStore {

    private static StudyStore instance;
    private final SharedPreferences preferences;
    private final Context context;
    private StudyNotebook notebook;
    private boolean recoveryWarning;

    private StudyStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("forest_study_v1", Context.MODE_PRIVATE);
        String json = preferences.getString("notebook", null);
        try {
            notebook = json == null ? new StudyNotebook() : StudyNotebook.fromJson(json);
        } catch (RuntimeException e) {
            // Preserve unreadable data before offering a fresh notebook; never silently overwrite it.
            if (
                !preferences
                    .edit()
                    .putString("unreadable_" + System.currentTimeMillis(), json)
                    .commit()
            ) {
                throw new IllegalStateException("学习存档无法读取，原始数据仍保留在本机", e);
            }
            recoveryWarning = true;
            notebook = new StudyNotebook();
        }
    }

    public static synchronized StudyStore get(Context context) {
        if (instance == null) instance = new StudyStore(context);
        return instance;
    }

    public StudyNotebook notebook() {
        return notebook;
    }

    public boolean takeRecoveryWarning() {
        boolean value = recoveryWarning;
        recoveryWarning = false;
        return value;
    }

    public int boot() {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    public long elapsedMillis() {
        return notebook.elapsedMillis(
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
            boot()
        );
    }

    public synchronized <T> T update(Action<T> action) {
        String previous = notebook.toJson();
        try {
            T result = action.run(notebook);
            if (!preferences.edit().putString("notebook", notebook.toJson()).commit()) {
                throw new IllegalStateException("保存未完成，请检查手机存储空间后重试");
            }
            return result;
        } catch (RuntimeException e) {
            notebook = StudyNotebook.fromJson(previous);
            throw e;
        }
    }

    public void start() {
        update(n -> {
            n.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
            return null;
        });
    }

    public void pause() {
        update(n -> {
            n.pause(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
            return null;
        });
    }

    public void resume() {
        update(n -> {
            n.resume(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
            return null;
        });
    }

    public StudyNotebook.Completion finish(String note) {
        return update(n ->
            n.finish(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(), note)
        );
    }

    public interface Action<T> {
        T run(StudyNotebook notebook);
    }
}
