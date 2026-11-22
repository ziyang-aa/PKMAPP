package com.example.pkmapp.study;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONException;
import org.json.JSONObject;

/** All learning state is committed together, without touching any financial preferences. */
public final class StudyStore {

    private static StudyStore instance;
    private final SharedPreferences preferences;
    private final Context context;
    private StudyNotebook notebook;
    private boolean recoveryWarning;
    private String pendingLegacy;

    private StudyStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("forest_study_v1", Context.MODE_PRIVATE);
        String json = preferences.getString("notebook", null);
        try {
            notebook = json == null ? new StudyNotebook() : StudyNotebook.fromJson(json);
            if (json != null && new JSONObject(json).getInt("version") < 3) {
                pendingLegacy = json;
            }
        } catch (RuntimeException | JSONException e) {
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
        if (pendingLegacy != null && preferences.edit().putString("notebook_before_plans", pendingLegacy)
            .putString("notebook", notebook.toJson()).commit()) {
            pendingLegacy = null;
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
        T result;
        try {
            result = action.run(notebook);
            SharedPreferences.Editor editor = preferences.edit().putString("notebook", notebook.toJson());
            if (pendingLegacy != null) editor.putString("notebook_before_plans", pendingLegacy);
            if (!editor.commit()) {
                throw new IllegalStateException("保存未完成，请检查手机存储空间后重试");
            }
            pendingLegacy = null;
        } catch (RuntimeException e) {
            notebook = StudyNotebook.fromJson(previous);
            throw e;
        }
        StudyNotifications.refresh(context, this);
        return result;
    }

    synchronized String exportBackup() {
        return notebook.portableJson(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
    }

    int importBackup(StudyNotebook backup, boolean preferBackup) {
        return update(n -> n.merge(backup, preferBackup,
            System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot()));
    }

    public void start() {
        start("", "");
    }

    void start(String taskId, String goal) {
        update(n -> {
            n.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(), taskId, goal);
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
        return finish(note, null, 0, false, "");
    }

    StudyNotebook.Completion finish(String note, String subjectId, long correctedMillis,
                                    boolean done, String next) {
        return update(n ->
            n.finish(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(), note, subjectId,
                correctedMillis, done, next)
        );
    }

    public interface Action<T> {
        T run(StudyNotebook notebook);
    }
}
