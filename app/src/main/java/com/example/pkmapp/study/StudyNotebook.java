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

/** Learning data and reward transactions, independent of Android and financial stores. */
public final class StudyNotebook {

    private static final long WATER_INTERVAL_MS = 25L * 60_000L;
    private final List<Record> records = new ArrayList<>();
    private final Set<String> rewardedDays = new HashSet<>();
    private StudySubject selected = StudySubject.DATA_STRUCTURES;
    private int sessionGoal = 45;
    private int dailyGoal = 180;
    private Session active;
    private int water;
    private int seeds = 1;
    private boolean planted;
    private int growth;
    private int trees;

    public StudySubject selected() {
        return selected;
    }

    public int sessionGoal() {
        return sessionGoal;
    }

    public int dailyGoal() {
        return dailyGoal;
    }

    public int water() {
        return water;
    }

    public void addWater() {
        water = Math.addExact(water, 5);
    }

    public int seeds() {
        return seeds;
    }

    public int growth() {
        return growth;
    }

    public int trees() {
        return trees;
    }

    public boolean planted() {
        return planted;
    }

    public boolean hasSession() {
        return active != null;
    }

    public boolean running() {
        return active != null && active.running;
    }

    public StudySubject activeSubject() {
        return active == null ? selected : active.subject;
    }

    public int activeGoal() {
        return active == null ? sessionGoal : active.goal;
    }

    public List<Record> records() {
        return Collections.unmodifiableList(records);
    }

    public void select(StudySubject subject) {
        if (active != null) throw new IllegalStateException("先完成这次学习，再切换科目");
        if (subject == null) throw new IllegalArgumentException("请选择学习科目");
        selected = subject;
    }

    public void setSessionGoal(int minutes) {
        if (active != null) throw new IllegalStateException("本次学习开始后不能修改目标");
        if (minutes != 25 && minutes != 45 && minutes != 60) throw new IllegalArgumentException(
            "请选择25、45或60分钟"
        );
        sessionGoal = minutes;
    }

    public void setDailyGoal(int minutes) {
        if (minutes < 10 || minutes > 720 || minutes % 5 != 0) {
            throw new IllegalArgumentException("目标为10至720分钟，按5分钟递增");
        }
        dailyGoal = minutes;
    }

    public void start(long wall, long elapsed, int boot) {
        if (active != null) throw new IllegalStateException("已有正在进行的学习");
        active = new Session(selected, sessionGoal);
        resume(wall, elapsed, boot);
    }

    public void resume(long wall, long elapsed, int boot) {
        requireSession();
        if (active.running) return;
        active.running = true;
        active.wall = wall;
        active.elapsed = elapsed;
        active.boot = boot;
    }

    public long elapsedMillis(long wall, long elapsed, int boot) {
        if (active == null) return 0;
        if (!active.running) return active.accumulated;
        // elapsedRealtime includes sleep and ignores wall-clock changes in the same boot.
        long interval =
            active.boot >= 0 && active.boot == boot && elapsed >= active.elapsed
                ? elapsed - active.elapsed
                : Math.max(0, wall - active.wall);
        return active.accumulated + interval;
    }

    public void pause(long wall, long elapsed, int boot) {
        requireSession();
        active.accumulated = elapsedMillis(wall, elapsed, boot);
        active.running = false;
    }

    public void discard() {
        active = null;
    }

    public Completion finish(long wall, long elapsed, int boot, String note) {
        requireSession();
        long duration = elapsedMillis(wall, elapsed, boot);
        if (duration < 1_000) throw new IllegalStateException("先专注一小会儿，再保存这段学习");
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > 120) throw new IllegalArgumentException("手记不能超过120个字");
        long before = totalMillis();
        Record record = new Record(
            UUID.randomUUID().toString(),
            active.subject,
            duration,
            wall,
            cleanNote
        );
        records.add(record);
        int earned = (int) ((before + duration) / WATER_INTERVAL_MS - before / WATER_INTERVAL_MS);
        water += earned;
        String day = dayKey(wall);
        int seed = 0;
        if (totalForDay(wall) >= dailyGoal * 60_000L && rewardedDays.add(day)) {
            seeds++;
            seed = 1;
        }
        active = null;
        return new Completion(record, earned, seed);
    }

    public void plant() {
        if (planted) throw new IllegalStateException("这棵小树还在成长中");
        if (seeds < 1) throw new IllegalStateException("达成每日学习目标，就能获得一颗新种子");
        seeds--;
        planted = true;
        growth = 0;
    }

    /** The complete transaction is saved before the UI starts its animation. */
    public boolean waterTree() {
        if (!planted) throw new IllegalStateException("先种下一颗种子，再来浇水吧");
        if (water < 1) throw new IllegalStateException("累计学习25分钟，就能获得1滴水");
        water--;
        growth++;
        if (growth < StudyGrowth.REQUIRED_WATER) return false;
        trees++;
        growth = 0;
        planted = false;
        return true;
    }

    public long totalMillis() {
        long sum = 0;
        for (Record record : records) sum += record.durationMillis;
        return sum;
    }

    public long totalForDay(long time) {
        String day = dayKey(time);
        long sum = 0;
        for (Record record : records)
            if (day.equals(dayKey(record.completedAt))) sum += record.durationMillis;
        return sum;
    }

    public List<Record> between(long start, long end) {
        List<Record> result = new ArrayList<>();
        for (Record record : records)
            if (record.completedAt >= start && record.completedAt < end) result.add(record);
        result.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
        return result;
    }

    public static String dayKey(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(time);
    }

    private void requireSession() {
        if (active == null) throw new IllegalStateException("请先开始一段学习");
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject()
                .put("version", 2)
                .put("selected", selected.name())
                .put("sessionGoal", sessionGoal)
                .put("dailyGoal", dailyGoal)
                .put("water", water)
                .put("seeds", seeds)
                .put("planted", planted)
                .put("growth", growth)
                .put("trees", trees);
            JSONArray list = new JSONArray();
            for (Record r : records)
                list.put(
                    new JSONObject()
                        .put("id", r.id)
                        .put("subject", r.subject.name())
                        .put("duration", r.durationMillis)
                        .put("completedAt", r.completedAt)
                        .put("note", r.note)
                );
            root.put("records", list).put(
                "rewardedDays",
                new JSONArray(new ArrayList<>(rewardedDays))
            );
            if (active != null) root.put(
                "active",
                new JSONObject()
                    .put("subject", active.subject.name())
                    .put("goal", active.goal)
                    .put("accumulated", active.accumulated)
                    .put("running", active.running)
                    .put("wall", active.wall)
                    .put("elapsed", active.elapsed)
                    .put("boot", active.boot)
            );
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("无法保存学习数据", e);
        }
    }

    public static StudyNotebook fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            int version = root.getInt("version");
            if (version != 1 && version != 2) throw new IllegalArgumentException(
                "不支持的学习存档版本"
            );
            StudyNotebook n = new StudyNotebook();
            n.selected = StudySubject.valueOf(root.getString("selected"));
            n.setSessionGoal(root.getInt("sessionGoal"));
            n.setDailyGoal(root.getInt("dailyGoal"));
            n.water = root.getInt("water");
            n.seeds = root.getInt("seeds");
            n.trees = root.getInt("trees");
            n.growth = root.getInt("growth");
            n.planted = root.getBoolean("planted");
            if (
                n.water < 0 ||
                n.seeds < 0 ||
                n.trees < 0 ||
                n.growth < 0 ||
                n.growth >= (version == 1 ? 5 : StudyGrowth.REQUIRED_WATER) ||
                (!n.planted && n.growth != 0)
            ) throw new IllegalArgumentException("无效的林地数据");
            if (version == 1) n.growth = StudyGrowth.milestone(n.growth);
            Set<String> ids = new HashSet<>();
            JSONArray records = root.getJSONArray("records");
            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                Record record = new Record(
                    r.getString("id"),
                    StudySubject.valueOf(r.getString("subject")),
                    r.getLong("duration"),
                    r.getLong("completedAt"),
                    r.getString("note")
                );
                if (!ids.add(record.id)) throw new IllegalArgumentException("重复的学习记录");
                n.records.add(record);
            }
            JSONArray days = root.getJSONArray("rewardedDays");
            for (int i = 0; i < days.length(); i++) n.rewardedDays.add(days.getString(i));
            JSONObject s = root.optJSONObject("active");
            if (s != null) {
                int goal = s.getInt("goal");
                if (goal != 25 && goal != 45 && goal != 60) throw new IllegalArgumentException(
                    "无效的计时目标"
                );
                n.active = new Session(StudySubject.valueOf(s.getString("subject")), goal);
                n.active.accumulated = s.getLong("accumulated");
                n.active.running = s.getBoolean("running");
                n.active.wall = s.getLong("wall");
                n.active.elapsed = s.getLong("elapsed");
                n.active.boot = s.getInt("boot");
                if (n.active.accumulated < 0 || n.active.elapsed < 0 || n.active.wall < 0) {
                    throw new IllegalArgumentException("无效的计时存档");
                }
            }
            return n;
        } catch (JSONException e) {
            throw new IllegalArgumentException("学习存档无法读取", e);
        }
    }

    private static final class Session {

        final StudySubject subject;
        final int goal;
        long accumulated, wall, elapsed;
        int boot;
        boolean running;

        Session(StudySubject subject, int goal) {
            this.subject = subject;
            this.goal = goal;
        }
    }

    public static final class Record {

        public final String id;
        public final StudySubject subject;
        public final long durationMillis;
        public final long completedAt;
        public final String note;

        public Record(
            String id,
            StudySubject subject,
            long durationMillis,
            long completedAt,
            String note
        ) {
            if (
                id == null ||
                id.trim().isEmpty() ||
                subject == null ||
                durationMillis < 1_000 ||
                completedAt <= 0 ||
                note == null ||
                note.length() > 120
            ) throw new IllegalArgumentException("无效的学习记录");
            this.id = id;
            this.subject = subject;
            this.durationMillis = durationMillis;
            this.completedAt = completedAt;
            this.note = note;
        }
    }

    public static final class Completion {

        public final Record record;
        public final int water;
        public final int seeds;

        Completion(Record record, int water, int seeds) {
            this.record = record;
            this.water = water;
            this.seeds = seeds;
        }
    }
}
