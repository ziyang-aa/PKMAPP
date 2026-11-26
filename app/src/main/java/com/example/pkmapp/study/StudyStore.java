package com.example.pkmapp.study;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import com.example.pkmapp.core.database.AccountScope;
import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.PkmStorageBootstrap;
import com.example.pkmapp.core.database.StudyDao;
import com.example.pkmapp.core.database.StudyDatabaseSnapshot;

/** Room-backed learning store scoped by ownerId with per-owner shadow mirror in SharedPreferences. */
public final class StudyStore {

    private static StudyStore instance;
    private final SharedPreferences shadowPreferences;
    private final StudyDao studyDao;
    private final Context context;
    private StudyNotebook notebook;
    private String currentOwnerId;
    private boolean recoveryWarning;

    private StudyStore(Context context) {
        this.context = context.getApplicationContext();
        this.studyDao = AppDatabase.getInstance(this.context).studyDao();
        this.shadowPreferences = this.context.getSharedPreferences(
                "forest_study_v1", Context.MODE_PRIVATE);
        this.recoveryWarning = PkmStorageBootstrap.takeStudyRecoveryWarning();
        this.currentOwnerId = AccountScope.currentOwnerId(this.context);
        loadForOwner(this.currentOwnerId);
    }

    private static String shadowKeyForOwner(String ownerId) {
        if (ownerId == null || ownerId.isEmpty() || AccountScope.GUEST_OWNER_ID.equals(ownerId)) {
            return "notebook";
        }
        return "notebook_" + ownerId;
    }

    private void loadForOwner(String ownerId) {
        StudyDatabaseSnapshot loadedSnapshot = studyDao.loadSnapshot(ownerId);
        StudyNotebook restored = null;
        try {
            restored = StudyNotebookSqlBridge.fromSnapshot(loadedSnapshot);
        } catch (RuntimeException e) {
            recoveryWarning = true;
        }
        if (restored == null) {
            notebook = new StudyNotebook();
            studyDao.replaceSnapshot(StudyNotebookSqlBridge.toSnapshot(notebook, ownerId), ownerId);
            shadowPreferences.edit().putString(shadowKeyForOwner(ownerId), notebook.toJson()).commit();
        } else {
            notebook = restored;
        }
    }

    public static synchronized StudyStore get(Context context) {
        if (instance == null) {
            instance = new StudyStore(context);
        } else {
            instance.ensureCurrentOwner();
        }
        return instance;
    }

    private synchronized void ensureCurrentOwner() {
        String activeOwner = AccountScope.currentOwnerId(context);
        if (!activeOwner.equals(currentOwnerId)) {
            reloadForCurrentAccount();
        }
    }

    public synchronized void reloadForCurrentAccount() {
        if (notebook != null && notebook.running()) {
            try {
                notebook.pause(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
                studyDao.replaceSnapshot(
                        StudyNotebookSqlBridge.toSnapshot(notebook, currentOwnerId),
                        currentOwnerId
                );
                shadowPreferences.edit()
                        .putString(shadowKeyForOwner(currentOwnerId), notebook.toJson())
                        .commit();
            } catch (RuntimeException ignored) {
            }
        }
        currentOwnerId = AccountScope.currentOwnerId(context);
        loadForOwner(currentOwnerId);
        StudyNotifications.refresh(context, this);
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
        ensureCurrentOwner();
        String previous = notebook.toJson();
        T result;
        try {
            result = action.run(notebook);
            studyDao.replaceSnapshot(
                    StudyNotebookSqlBridge.toSnapshot(notebook, currentOwnerId),
                    currentOwnerId
            );
            if (!shadowPreferences.edit().putString(shadowKeyForOwner(currentOwnerId), notebook.toJson()).commit()) {
                throw new IllegalStateException("保存未完成，请检查手机存储空间后重试");
            }
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
