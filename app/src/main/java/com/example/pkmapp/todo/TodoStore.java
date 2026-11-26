package com.example.pkmapp.todo;

import android.content.Context;

import com.example.pkmapp.core.database.AccountScope;
import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.SyncStatus;
import com.example.pkmapp.core.database.TodoDao;
import com.example.pkmapp.core.database.TodoItemEntity;
import com.example.pkmapp.core.datastore.AppPreferences;
import com.example.pkmapp.core.datastore.AppPreferencesSnapshot;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

/** Room + DataStore backed standalone To-Do store, independent of study plans and subjects. */
public final class TodoStore {
    private static TodoStore instance;

    private final Context appContext;
    private final TodoDao todoDao;
    private final AppPreferences appPreferences;
    private final List<TodoItem> items = new ArrayList<>();
    private String currentOwnerId;
    private boolean remindEnabled = true;
    private String defaultRemindTime = "18:00";

    private TodoStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.todoDao = AppDatabase.getInstance(appContext).todoDao();
        this.appPreferences = AppPreferences.getInstance(appContext);
        this.currentOwnerId = AccountScope.currentOwnerId(appContext);
        load();
    }

    public static synchronized TodoStore get(Context context) {
        if (instance == null) {
            instance = new TodoStore(context);
        } else {
            instance.ensureCurrentOwner();
        }
        return instance;
    }

    private synchronized void ensureCurrentOwner() {
        String activeOwner = AccountScope.currentOwnerId(appContext);
        if (!activeOwner.equals(currentOwnerId)) {
            reloadForCurrentAccount();
        }
    }

    public synchronized void reloadForCurrentAccount() {
        currentOwnerId = AccountScope.currentOwnerId(appContext);
        load();
        TodoNotifications.refresh(appContext, this);
    }

    public synchronized boolean remindEnabled() {
        return remindEnabled;
    }

    public synchronized String defaultRemindTime() {
        return defaultRemindTime;
    }

    public synchronized List<TodoItem> pending() {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : items) {
            if (item.completed == 0) result.add(item);
        }
        result.sort((a, b) -> {
            String da = a.day.isEmpty() ? "9999-99-99" : a.day;
            String db = b.day.isEmpty() ? "9999-99-99" : b.day;
            int cmp = da.compareTo(db);
            if (cmp != 0) return cmp;
            String ta = a.dueTime.isEmpty() ? "99:99" : a.dueTime;
            String tb = b.dueTime.isEmpty() ? "99:99" : b.dueTime;
            int tcmp = ta.compareTo(tb);
            return tcmp != 0 ? tcmp : Long.compare(a.created, b.created);
        });
        return result;
    }

    public synchronized List<TodoItem> completed() {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : items) {
            if (item.completed > 0) result.add(item);
        }
        result.sort((a, b) -> Long.compare(b.completed, a.completed));
        return result;
    }

    public synchronized TodoItem add(
        String title,
        String note,
        String day,
        String dueTime,
        boolean remind,
        int remindLeadMinutes
    ) {
        long now = System.currentTimeMillis();
        TodoItem item = new TodoItem(
            UUID.randomUUID().toString(),
            title,
            note,
            day,
            dueTime,
            remind,
            remindLeadMinutes,
            now,
            0
        );
        items.add(item);
        saveItems();
        return item;
    }

    public synchronized void update(
        String id,
        String title,
        String note,
        String day,
        String dueTime,
        boolean remind,
        int remindLeadMinutes,
        boolean done
    ) {
        for (int i = 0; i < items.size(); i++) {
            TodoItem old = items.get(i);
            if (old.id.equals(id)) {
                long completedAt = done
                    ? (old.completed > 0 ? old.completed : System.currentTimeMillis())
                    : 0;
                items.set(i, new TodoItem(
                    id,
                    title,
                    note,
                    day,
                    dueTime,
                    remind,
                    remindLeadMinutes,
                    old.created,
                    completedAt
                ));
                saveItems();
                return;
            }
        }
    }

    public synchronized void toggleDone(String id, boolean done) {
        for (int i = 0; i < items.size(); i++) {
            TodoItem old = items.get(i);
            if (old.id.equals(id)) {
                long completedAt = done ? System.currentTimeMillis() : 0;
                items.set(i, new TodoItem(
                    old.id,
                    old.title,
                    old.note,
                    old.day,
                    old.dueTime,
                    old.remind,
                    old.remindLeadMinutes,
                    old.created,
                    completedAt
                ));
                saveItems();
                return;
            }
        }
    }

    public synchronized void delete(String id) {
        items.removeIf(item -> item.id.equals(id));
        saveItems();
    }

    public synchronized void setReminderConfig(boolean enabled, String defaultTime) {
        String clean = TodoItem.validateTime(defaultTime);
        if (clean.isEmpty()) throw new IllegalArgumentException("请输入时间，例如 18:00");
        this.remindEnabled = enabled;
        this.defaultRemindTime = clean;
        appPreferences.setTodoReminderConfig(enabled, clean);
        TodoNotifications.refresh(appContext, this);
    }

    public static String dayKey(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(millis);
    }

    public static Calendar parseDay(String day) {
        Calendar cal = Calendar.getInstance();
        String clean = TodoItem.validateDay(day);
        if (clean.isEmpty()) return cal;
        String[] p = clean.split("-");
        cal.clear();
        cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        return cal;
    }

    private void load() {
        items.clear();
        AppPreferencesSnapshot prefs = appPreferences.getCurrent();
        remindEnabled = prefs.getTodoRemindEnabled();
        String rt = TodoItem.validateTime(prefs.getTodoDefaultRemindTime());
        defaultRemindTime = rt.isEmpty() ? "18:00" : rt;
        for (TodoItemEntity row : todoDao.getAll(currentOwnerId)) {
            try {
                items.add(new TodoItem(
                    row.getId(),
                    row.getTitle(),
                    row.getNote(),
                    row.getDueDate(),
                    row.getDueTime(),
                    row.getRemind(),
                    row.getRemindBeforeMinutes(),
                    row.getCreatedAt(),
                    row.getCompletedAt()
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveItems() {
        ensureCurrentOwner();
        long now = System.currentTimeMillis();
        List<TodoItemEntity> entities = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            TodoItem item = items.get(index);
            entities.add(new TodoItemEntity(
                item.id,
                item.title,
                item.note,
                item.day,
                item.dueTime,
                item.remind,
                item.remindLeadMinutes,
                item.created,
                item.completed,
                index,
                currentOwnerId,
                now,
                0L,
                SyncStatus.PENDING_PUSH,
                AccountScope.newRequestId()
            ));
        }
        todoDao.replaceAll(entities, currentOwnerId);
        TodoNotifications.refresh(appContext, this);
    }

    public static final class TodoItem {
        public final String id;
        public final String title;
        public final String note;
        public final String day;
        public final String dueTime;
        public final boolean remind;
        public final int remindLeadMinutes;
        public final long created;
        public final long completed;

        public TodoItem(
            String id,
            String title,
            String note,
            String day,
            String dueTime,
            boolean remind,
            int remindLeadMinutes,
            long created,
            long completed
        ) {
            String cleanTitle = title == null ? "" : title.trim();
            if (cleanTitle.isEmpty()) throw new IllegalArgumentException("请填写待办内容");
            if (cleanTitle.length() > 120) throw new IllegalArgumentException("待办内容最多 120 字");
            String cleanNote = note == null ? "" : note.trim();
            if (cleanNote.length() > 200) throw new IllegalArgumentException("备注最多 200 字");
            this.id = id;
            this.title = cleanTitle;
            this.note = cleanNote;
            this.day = validateDay(day);
            this.dueTime = validateTime(dueTime);
            this.remind = remind;
            this.remindLeadMinutes = remindLeadMinutes <= 0 ? 30 : Math.min(remindLeadMinutes, 10080);
            this.created = created;
            this.completed = completed;
        }

        public static String validateDay(String value) {
            if (value == null) return "";
            String date = value.trim();
            if (date.isEmpty()) return "";
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            format.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || format.parse(date, pos) == null
                || pos.getIndex() != date.length()) {
                throw new IllegalArgumentException("日期格式为 YYYY-MM-DD");
            }
            return date;
        }

        public static String validateTime(String value) {
            if (value == null) return "";
            String clean = value.trim().replace('：', ':');
            if (clean.isEmpty()) return "";
            if (!clean.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]")) {
                throw new IllegalArgumentException("时间格式为 HH:mm，例如 18:00");
            }
            String[] parts = clean.split(":");
            return String.format(Locale.ROOT, "%02d:%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        public long deadlineAt() {
            if (day.isEmpty()) return 0;
            Calendar cal = parseDay(day);
            if (!dueTime.isEmpty()) {
                String[] parts = dueTime.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
            }
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }

        public long reminderAt(String defaultRemindTime) {
            if (completed != 0 || !remind || day.isEmpty()) return 0;
            if (!dueTime.isEmpty()) {
                return deadlineAt() - remindLeadMinutes * 60_000L;
            }
            Calendar cal = parseDay(day);
            if (remindLeadMinutes >= 1440) {
                cal.add(Calendar.DATE, -1);
            }
            String hhmm = (defaultRemindTime == null || defaultRemindTime.isEmpty()) ? "18:00" : defaultRemindTime;
            String[] parts = hhmm.split(":");
            cal.set(Calendar.HOUR_OF_DAY, parts.length == 2 ? Integer.parseInt(parts[0]) : 18);
            cal.set(Calendar.MINUTE, parts.length == 2 ? Integer.parseInt(parts[1]) : 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }

        public String leadLabel() {
            if (remindLeadMinutes >= 1440) return "前一天傍晚";
            if (remindLeadMinutes >= 60 && remindLeadMinutes % 60 == 0)
                return "提前" + (remindLeadMinutes / 60) + "小时";
            return "提前" + remindLeadMinutes + "分钟";
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id).put("title", title).put("note", note)
                .put("day", day).put("dueTime", dueTime)
                .put("remind", remind).put("remindLead", remindLeadMinutes)
                .put("created", created).put("completed", completed);
            return obj;
        }

        static TodoItem fromJson(JSONObject obj) throws JSONException {
            return new TodoItem(
                obj.getString("id"),
                obj.getString("title"),
                obj.optString("note", ""),
                obj.optString("day", ""),
                obj.optString("dueTime", ""),
                obj.optBoolean("remind", false),
                obj.optInt("remindLead", 30),
                obj.optLong("created", 0),
                obj.optLong("completed", 0)
            );
        }
    }
}
