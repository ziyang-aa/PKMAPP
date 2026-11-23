package com.example.pkmapp.study;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class StudyPlan {
    static final String LEGACY_ID = "legacy-408";

    enum Kind {
        GRADUATE("考研"), FINALS("期末"), CUSTOM("自定义");

        final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    enum Partner {
        SQUIRREL("松栗", "爱翻书的小松鼠"),
        BEAR("团团", "爱记笔记的小棕熊"),
        BUNNY("糯糯", "细心刷题的小白兔");

        final String title;
        final String subtitle;

        Partner(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        static Partner parse(String raw) {
            if (raw == null || raw.isEmpty()) return SQUIRREL;
            if ("BEAR".equals(raw) || "TREECKO".equals(raw)) return BEAR;
            if ("BUNNY".equals(raw) || "BULBASAUR".equals(raw)) return BUNNY;
            return SQUIRREL;
        }
    }

    final String id;
    final Kind kind;
    String previous;
    final long created;
    String title;
    String due;
    Partner partner;
    long archived;
    int sessionGoal = 45;
    int dailyGoal = 180;
    boolean remindDeadlines;
    String remindTime = "18:00";
    String selected;
    private List<StudySubject> books;

    private StudyPlan(String id, String title, Kind kind, String due, Partner partner,
                      String previous, long created, List<StudySubject> books) {
        this.id = required(id, 120, "计划标识");
        this.title = required(title, 40, "计划名称");
        this.kind = kind;
        this.due = date(due);
        this.partner = partner;
        this.previous = optional(previous, 120, "上一轮标识");
        if (created < 0) throw new IllegalArgumentException("无效的计划创建时间");
        this.created = created;
        setBooks(books);
    }

    static StudyPlan legacy() {
        StudyPlan plan = new StudyPlan(LEGACY_ID, "408 考研", Kind.GRADUATE, "", Partner.SQUIRREL,
            "", 0, java.util.Arrays.asList(StudySubject.values()));
        plan.selected = StudySubject.DATA_STRUCTURES.id;
        return plan;
    }

    static StudyPlan create(String title, Kind kind, String due, Partner partner,
                            long now, List<StudySubject> books, StudyPlan previous) {
        StudyPlan plan = new StudyPlan(UUID.randomUUID().toString(), title, kind, due, partner,
            previous == null ? "" : previous.id, now, books);
        if (previous != null) {
            plan.sessionGoal = previous.sessionGoal;
            plan.dailyGoal = previous.dailyGoal;
            if (plan.book(previous.selected) != null) plan.selected = previous.selected;
        }
        return plan;
    }

    List<StudySubject> books() {
        return Collections.unmodifiableList(books);
    }

    StudySubject book(String id) {
        for (StudySubject subject : books) if (subject.id.equals(id)) return subject;
        return null;
    }

    void setBooks(List<StudySubject> value) {
        if (value.isEmpty()) throw new IllegalArgumentException("请至少选择一门科目");
        Set<String> ids = new HashSet<>();
        for (StudySubject subject : value)
            if (!ids.add(subject.id)) throw new IllegalArgumentException("书架里有重复科目");
        books = new ArrayList<>(value);
        if (book(selected) == null) selected = books.get(0).id;
    }

    static String required(String value, int max, String label) {
        String clean = optional(value, max, label);
        if (clean.isEmpty()) throw new IllegalArgumentException("请填写" + label);
        return clean;
    }

    static String optional(String value, int max, String label) {
        String clean = value.trim();
        if (clean.length() > max) throw new IllegalArgumentException(label + "最多 " + max + " 字");
        return clean;
    }

    static String date(String value) {
        String date = value.trim();
        if (date.isEmpty()) return date;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || format.parse(date, position) == null
            || position.getIndex() != date.length()) {
            throw new IllegalArgumentException("日期格式为 YYYY-MM-DD，例如 2027-01-15");
        }
        return date;
    }

    JSONObject toJson() throws JSONException {
        JSONArray shelf = new JSONArray();
        for (StudySubject subject : books) shelf.put(subject.toJson());
        JSONObject out = new JSONObject().put("id", id).put("title", title).put("kind", kind.name())
            .put("due", due).put("partner", partner.name()).put("previous", previous)
            .put("created", created).put("archived", archived).put("selected", selected)
            .put("sessionGoal", sessionGoal).put("dailyGoal", dailyGoal).put("books", shelf)
            .put("remindDeadlines", remindDeadlines);
        if (remindTime != null && !remindTime.isEmpty() && !"18:00".equals(remindTime)) {
            out.put("remindTime", remindTime);
        }
        return out;
    }

    static StudyPlan fromJson(JSONObject source) throws JSONException {
        List<StudySubject> books = new ArrayList<>();
        JSONArray shelf = source.getJSONArray("books");
        for (int i = 0; i < shelf.length(); i++) books.add(StudySubject.fromJson(shelf.getJSONObject(i)));
        StudyPlan plan = new StudyPlan(source.getString("id"), source.getString("title"),
            Kind.valueOf(source.getString("kind")), source.getString("due"),
            Partner.parse(source.optString("partner", Partner.SQUIRREL.name())), source.getString("previous"),
            source.getLong("created"), books);
        plan.archived = source.getLong("archived");
        plan.selected = source.getString("selected");
        plan.sessionGoal = StudyNotebook.validateSessionGoal(source.getInt("sessionGoal"));
        plan.dailyGoal = StudyNotebook.validateDailyGoal(source.getInt("dailyGoal"));
        plan.remindDeadlines = source.optBoolean("remindDeadlines");
        String rt = StudyWork.Task.validateTime(source.optString("remindTime", "18:00"));
        plan.remindTime = rt.isEmpty() ? "18:00" : rt;
        if (plan.book(plan.selected) == null || plan.archived < 0)
            throw new IllegalArgumentException("无效的计划存档");
        return plan;
    }
}
