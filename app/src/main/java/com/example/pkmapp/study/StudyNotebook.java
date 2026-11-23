package com.example.pkmapp.study;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Learning data and reward transactions, independent of Android and financial stores. */
public final class StudyNotebook {

    static final int VERSION = 4;
    private static final long WATER_INTERVAL_MS = 25L * 60_000L;
    private final Map<String, StudyPlan> plans = new LinkedHashMap<>();
    private final List<Record> records = new ArrayList<>();
    private final Set<String> rewardedDays = new HashSet<>();
    private final Map<String, StudyWork.Task> tasks = new LinkedHashMap<>();
    private final Map<String, StudyWork.Review> reviews = new LinkedHashMap<>();
    private String current = StudyPlan.LEGACY_ID;
    private Session active;
    private int water;
    private long waterProgress;
    private int seeds = 1;
    private boolean planted;
    private int growth;
    private int trees;

    public StudyNotebook() {
        plans.put(current, StudyPlan.legacy());
    }

    StudyPlan currentPlan() {
        return plans.get(current);
    }

    StudyPlan plan(String id) {
        return plans.get(id);
    }

    List<StudyPlan> plans() {
        return new ArrayList<>(plans.values());
    }

    private StudyPlan requirePlan(String id) {
        StudyPlan plan = plan(id);
        if (plan == null) throw new IllegalArgumentException("找不到这个学习计划");
        return plan;
    }

    void switchPlan(String id) {
        current = requirePlan(id).id;
    }

    StudyPlan createPlan(String title, StudyPlan.Kind kind, String due, StudyPlan.Partner partner,
                         List<StudySubject> books, long now) {
        return addPlan(StudyPlan.create(title, kind, due, partner, now, books, null));
    }

    private StudyPlan addPlan(StudyPlan plan) {
        plans.put(plan.id, plan);
        current = plan.id;
        return plan;
    }

    void editPlan(String id, String title, String due, StudyPlan.Partner partner, List<StudySubject> books) {
        StudyPlan plan = requirePlan(id);
        requireEditable(plan);
        String cleanTitle = StudyPlan.required(title, 40, "计划名称");
        String cleanDue = StudyPlan.date(due);
        plan.setBooks(books);
        plan.title = cleanTitle;
        plan.due = cleanDue;
        plan.partner = partner;
    }

    private void requireEditable(StudyPlan plan) {
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        if (plan.id.equals(activePlanId())) throw new IllegalStateException("请先完成这次学习");
    }

    void archive(String id, long now) {
        StudyPlan plan = requirePlan(id);
        requireEditable(plan);
        if (now <= 0) throw new IllegalArgumentException("无效的归档时间");
        plan.archived = now;
        if (current.equals(id)) {
            for (StudyPlan candidate : plans.values()) {
                if (candidate.archived == 0) {
                    current = candidate.id;
                    break;
                }
            }
        }
    }

    StudyPlan nextRound(String id, String title, long now) {
        return nextRound(id, title, now, true, Collections.emptySet());
    }

    StudyPlan nextRound(String id, String title, long now, boolean keepDates, Set<String> carry) {
        StudyPlan plan = requirePlan(id);
        requireEditable(plan);
        List<StudySubject> books = new ArrayList<>();
        for (StudySubject subject : plan.books()) {
            books.add(keepDates ? subject : subject.edit(subject.title, subject.hint, "", subject.assessment));
        }
        StudyPlan next = StudyPlan.create(title, plan.kind, "", plan.partner, now, books, plan);
        List<StudyWork.Task> source = pendingTasks(id);
        archive(id, now);
        addPlan(next);
        for (StudyWork.Task task : source) {
            if (carry.contains(task.id)) putTask(next.id, task.subjectId, task.title, "", now, task.next);
        }
        return next;
    }

    void restore(String id) {
        StudyPlan plan = requirePlan(id);
        plan.archived = 0;
        current = plan.id;
    }

    void deletePlan(String id) {
        StudyPlan plan = requirePlan(id);
        if (id.equals(activePlanId())) throw new IllegalStateException("请先结束这段计时，再删除计划");
        for (Record record : records) {
            if (id.equals(record.planId)) reviews.remove(record.id);
        }
        records.removeIf(record -> id.equals(record.planId));
        tasks.values().removeIf(task -> id.equals(task.planId));
        plans.remove(id);
        for (StudyPlan other : plans.values()) {
            if (id.equals(other.previous)) other.previous = plan.previous;
        }
        if (id.equals(current)) selectFallback();
    }

    private void selectFallback() {
        if (active != null) {
            current = active.planId;
            return;
        }
        for (StudyPlan plan : plans.values()) {
            if (plan.archived == 0) {
                current = plan.id;
                return;
            }
        }
        current = plans.isEmpty() ? "" : plans.values().iterator().next().id;
    }

    private StudyPlan requireCurrent() {
        StudyPlan plan = currentPlan();
        if (plan == null) throw new IllegalStateException("请先新建学习计划");
        return plan;
    }

    public StudySubject selected() {
        StudyPlan plan = requireCurrent();
        return plan.book(plan.selected);
    }

    public int sessionGoal() { return requireCurrent().sessionGoal; }

    public int dailyGoal() { return requireCurrent().dailyGoal; }

    public int water() { return water; }

    public void addWater() { water = Math.addExact(water, 5); }

    public int seeds() { return seeds; }

    public int growth() { return growth; }

    public int trees() { return trees; }

    public boolean planted() { return planted; }

    public boolean hasSession() { return active != null; }

    public boolean running() { return active != null && active.running; }

    String sessionId() { return active == null ? "" : active.id; }

    String activeTaskId() { return active == null ? "" : active.taskId; }

    StudyWork.Task task(String id) { return tasks.get(id); }

    List<StudyWork.Task> pendingTasks(String planId) {
        StudyPlan plan = requirePlan(planId);
        List<StudyWork.Task> result = new ArrayList<>();
        for (StudyWork.Task task : tasks.values()) {
            if (task.planId.equals(planId) && task.completed == 0 && plan.book(task.subjectId) != null)
                result.add(task);
        }
        result.sort((a, b) -> {
            int priority = Long.compare(b.priority, a.priority);
            if (priority != 0) return priority;
            int day = a.day.compareTo(b.day);
            return day != 0 ? day : Long.compare(a.created, b.created);
        });
        return result;
    }

    StudyWork.Task nextTask(String planId) {
        StudyWork.Task best = null;
        for (StudyWork.Task task : pendingTasks(planId)) {
            if (task.next && (best == null || task.updated >= best.updated)) best = task;
        }
        return best;
    }

    StudyWork.Task putTask(String planId, String subjectId, String title, String day, long now, boolean next) {
        return putTask(planId, subjectId, title, day, "", false, 30, now, next);
    }

    StudyWork.Task putTask(
        String planId,
        String subjectId,
        String title,
        String day,
        String dueTime,
        boolean remind,
        int remindLeadMinutes,
        long now,
        boolean next
    ) {
        StudyPlan plan = requirePlan(planId);
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        requireBook(plan, subjectId);
        StudyWork.Task task = new StudyWork.Task(UUID.randomUUID().toString(), planId, subjectId,
            title, day, now, 0, now, next, 0, dueTime, remind, remindLeadMinutes);
        tasks.put(task.id, task);
        return task;
    }

    void changeTask(String id, String title, String day, boolean done, long now) {
        StudyWork.Task old = task(id);
        if (old == null) throw new IllegalArgumentException("找不到学习事项");
        changeTask(id, title, day, old.dueTime, old.remind, old.remindLeadMinutes, done, now);
    }

    void changeTask(
        String id,
        String title,
        String day,
        String dueTime,
        boolean remind,
        int remindLeadMinutes,
        boolean done,
        long now
    ) {
        StudyWork.Task old = task(id);
        if (old == null) throw new IllegalArgumentException("找不到学习事项");
        if (requirePlan(old.planId).archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        long completed = done ? (old.completed == 0 ? now : old.completed) : 0;
        tasks.put(id, new StudyWork.Task(id, old.planId, old.subjectId, title, day, old.created,
            completed, now, old.next, old.day.equals(day) ? old.priority : 0,
            dueTime, remind, remindLeadMinutes));
    }

    void deleteTask(String id) {
        StudyWork.Task old = task(id);
        if (old == null) return;
        if (requirePlan(old.planId).archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        tasks.remove(id);
    }

    void prioritizeTask(String id, long now) {
        StudyWork.Task old = task(id);
        if (old == null || old.completed != 0) throw new IllegalArgumentException("找不到待做事项");
        if (requirePlan(old.planId).archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        tasks.put(id, new StudyWork.Task(id, old.planId, old.subjectId, old.title, dayKey(now), old.created,
            0, now, old.next, now, old.dueTime, old.remind, old.remindLeadMinutes));
    }

    List<StudyWork.Task> completedTasks(String planId, long start, long end) {
        List<StudyWork.Task> result = new ArrayList<>();
        for (StudyWork.Task task : tasks.values())
            if ((planId == null || task.planId.equals(planId)) && task.completed >= start
                && task.completed < end && task.completed > 0) result.add(task);
        result.sort((a, b) -> Long.compare(b.completed, a.completed));
        return result;
    }

    Record record(String id) {
        for (Record record : records) if (record.id.equals(id)) return record;
        return null;
    }

    StudyWork.Review review(String recordId) { return reviews.get(recordId); }

    List<StudyWork.Review> pendingReviews(String planId) {
        List<StudyWork.Review> result = new ArrayList<>();
        for (Record record : records) {
            StudyWork.Review review = reviews.get(record.id);
            if (review != null && review.completed == 0 && (planId == null || record.planId.equals(planId)))
                result.add(review);
        }
        result.sort((a, b) -> a.day.compareTo(b.day));
        return result;
    }

    List<StudyWork.Review> completedReviews(String planId, long start, long end) {
        List<StudyWork.Review> result = new ArrayList<>();
        for (Record record : records) {
            StudyWork.Review review = reviews.get(record.id);
            if (review != null && review.completed > 0 && review.completed >= start && review.completed < end
                && (planId == null || record.planId.equals(planId))) result.add(review);
        }
        result.sort((a, b) -> Long.compare(b.completed, a.completed));
        return result;
    }

    void changeReview(String recordId, String prompt, String day, boolean done, long now) {
        if (record(recordId) == null) throw new IllegalArgumentException("找不到原学习记录");
        reviews.put(recordId, new StudyWork.Review(recordId, prompt, day, done ? now : 0, now));
    }

    void setDeadline(String planId, String subjectId, String day) {
        StudyPlan plan = requirePlan(planId);
        requireEditable(plan);
        StudySubject subject = requireBook(plan, subjectId);
        List<StudySubject> books = new ArrayList<>(plan.books());
        books.set(books.indexOf(subject), subject.edit(subject.title, subject.hint, day, subject.assessment));
        plan.setBooks(books);
    }

    void remindDeadlines(String planId, boolean enabled) {
        StudyPlan plan = requirePlan(planId);
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        plan.remindDeadlines = enabled;
    }

    void setRemindTime(String planId, String time) {
        StudyPlan plan = requirePlan(planId);
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        String clean = StudyWork.Task.validateTime(time);
        if (clean.isEmpty()) throw new IllegalArgumentException("请填写提醒时间，例如 18:00");
        plan.remindTime = clean;
    }

    private StudySubject requireBook(StudyPlan plan, String subjectId) {
        StudySubject subject = plan.book(subjectId);
        if (subject == null) throw new IllegalArgumentException("找不到计划里的科目");
        return subject;
    }

    String activePlanId() { return active == null ? null : active.planId; }

    boolean currentHasSession() { return current.equals(activePlanId()); }

    public StudySubject activeSubject() {
        return active == null ? selected() : active.subject;
    }

    public int activeGoal() {
        return active == null ? sessionGoal() : active.goal;
    }

    public List<Record> records() { return Collections.unmodifiableList(records); }

    public void select(StudySubject subject) {
        StudyPlan plan = requireCurrent();
        requireEditable(plan);
        if (subject == null || plan.book(subject.id) == null)
            throw new IllegalArgumentException("请选择这个计划的学习科目");
        plan.selected = subject.id;
    }

    public void setSessionGoal(int minutes) {
        StudyPlan plan = requireCurrent();
        requireEditable(plan);
        plan.sessionGoal = validateSessionGoal(minutes);
    }

    static int validateSessionGoal(int minutes) {
        if (minutes < 5 || minutes > 360)
            throw new IllegalArgumentException("单次专注时长为5至360分钟");
        return minutes;
    }

    public void setDailyGoal(int minutes) {
        StudyPlan plan = requireCurrent();
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        plan.dailyGoal = validateDailyGoal(minutes);
    }

    static int validateDailyGoal(int minutes) {
        if (minutes < 10 || minutes > 720 || minutes % 5 != 0)
            throw new IllegalArgumentException("目标为10至720分钟，按5分钟递增");
        return minutes;
    }

    public void start(long wall, long elapsed, int boot) {
        start(wall, elapsed, boot, "", "");
    }

    void start(long wall, long elapsed, int boot, String taskId, String goal) {
        if (active != null) throw new IllegalStateException("已有一段学习计时，请先保存");
        StudyPlan plan = requireCurrent();
        if (plan.archived != 0) throw new IllegalStateException("请先恢复已归档的计划");
        StudyWork.Task task = null;
        if (!taskId.isEmpty()) {
            task = task(taskId);
            if (task == null || !task.planId.equals(current) || task.completed != 0)
                throw new IllegalArgumentException("这个学习事项已不可用");
            select(requireBook(plan, task.subjectId));
            tasks.put(task.id, new StudyWork.Task(task.id, task.planId, task.subjectId,
                task.title, task.day, task.created, 0, wall, true, task.priority,
                task.dueTime, task.remind, task.remindLeadMinutes));
        } else if (!goal.trim().isEmpty()) {
            task = putTask(current, selected().id, goal, "", wall, true);
        }
        active = new Session(UUID.randomUUID().toString(), current, selected(), sessionGoal(),
            task == null ? "" : task.id);
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
        long interval = active.boot >= 0 && active.boot == boot && elapsed >= active.elapsed
            ? elapsed - active.elapsed : Math.max(0, wall - active.wall);
        return active.accumulated + interval;
    }

    public void pause(long wall, long elapsed, int boot) {
        requireSession();
        active.accumulated = elapsedMillis(wall, elapsed, boot);
        active.running = false;
    }

    public void discard() { active = null; }

    public Completion finish(long wall, long elapsed, int boot, String note) {
        return finish(wall, elapsed, boot, note, null, 0, false, "");
    }

    Completion finish(long wall, long elapsed, int boot, String note, String subjectId,
                      long correctedMillis, boolean done, String next) {
        requireSession();
        long measured = elapsedMillis(wall, elapsed, boot);
        if (measured < 1_000) throw new IllegalStateException("先专注一小会儿，再保存这段学习");
        long duration = correctedMillis == 0 ? measured : correctedMillis;
        long eligible = Math.min(duration, measured);
        StudyPlan plan = requirePlan(active.planId);
        StudySubject subject = subjectId == null || subjectId.equals(active.subject.id)
            ? active.subject : requireBook(plan, subjectId);
        String cleanNote = StudyPlan.optional(note == null ? "" : note, 120, "手记");
        String step = StudyPlan.optional(next, 120, "下一步");
        long progress = Math.addExact(waterProgress, eligible);
        int earned = Math.toIntExact(progress / WATER_INTERVAL_MS);
        int newWater = Math.addExact(water, earned);
        Record record = new Record(active.id, active.planId, subject, duration, wall, cleanNote,
            false, eligible, 0, duration != measured, active.taskId);
        records.add(record);
        StudyWork.Task task = task(active.taskId);
        if (task != null && (done || !step.isEmpty())) {
            tasks.put(task.id, new StudyWork.Task(task.id, task.planId, task.subjectId, task.title,
                task.day, task.created, done && task.completed == 0 ? wall : task.completed,
                wall, step.isEmpty() && task.next, task.priority,
                task.dueTime, task.remind, task.remindLeadMinutes));
        }
        if (!step.isEmpty()) putTask(plan.id, subject.id, step, "", wall, true);
        water = newWater;
        waterProgress = progress % WATER_INTERVAL_MS;
        String day = dayKey(wall);
        int seed = 0;
        if (rewardTimeForDay(active.planId, wall) >= plan.dailyGoal * 60_000L
            && rewardedDays.add(day)) {
            seeds++;
            seed = 1;
        }
        active = null;
        return new Completion(record, earned, seed);
    }

    Record addRecord(String planId, String subjectId, long duration, long completedAt, String note) {
        StudySubject subject = requireBook(requirePlan(planId), subjectId);
        Record record = new Record(UUID.randomUUID().toString(), planId, subject, duration, completedAt,
            StudyPlan.optional(note, 120, "手记"), true, 0, 0, false, "");
        records.add(record);
        return record;
    }

    Record editRecord(String id, String subjectId, long duration, long completedAt, String note, long now) {
        Record old = record(id);
        if (old == null) throw new IllegalArgumentException("找不到学习记录");
        StudySubject subject = old.subject.id.equals(subjectId) ? old.subject
            : requireBook(requirePlan(old.planId), subjectId);
        if (!old.manual && completedAt != old.completedAt) throw new IllegalArgumentException("计时记录的完成日期保持原值");
        Record updated = new Record(id, old.planId, subject, duration, completedAt,
            StudyPlan.optional(note, 120, "手记"), old.manual, old.rewardMillis, now,
            old.adjusted || duration != old.durationMillis, old.taskId);
        records.set(records.indexOf(old), updated);
        return updated;
    }

    private long rewardTimeForDay(String planId, long now) {
        String day = dayKey(now);
        long total = 0;
        for (Record record : records) {
            if (record.planId.equals(planId) && day.equals(dayKey(record.completedAt))) total += record.rewardMillis;
        }
        return total;
    }

    public static final int SEED_WATER_COST = 6;

    public void plant() {
        if (planted) throw new IllegalStateException("这棵小树还在成长中");
        if (seeds < 1) throw new IllegalStateException("达成每日学习目标，就能获得一颗新种子");
        seeds--;
        planted = true;
        growth = 0;
    }

    public void exchangeWaterForSeed() {
        if (planted) throw new IllegalStateException("这棵小树还在成长中");
        if (water < SEED_WATER_COST) {
            throw new IllegalStateException("累计满 " + SEED_WATER_COST + " 滴水即可兑换一颗新种子");
        }
        water -= SEED_WATER_COST;
        seeds++;
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
        for (Record record : records) sum = Math.addExact(sum, record.durationMillis);
        return sum;
    }

    long totalForDay(String planId, long time) {
        String day = dayKey(time);
        long sum = 0;
        for (Record record : records)
            if (record.planId.equals(planId) && day.equals(dayKey(record.completedAt)))
                sum += record.durationMillis;
        return sum;
    }

    List<Record> forPlan(String planId) {
        return between(planId, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public List<Record> between(long start, long end) {
        return between(null, start, end);
    }

    List<Record> between(String planId, long start, long end) {
        List<Record> result = new ArrayList<>();
        for (Record record : records)
            if ((planId == null || record.planId.equals(planId))
                && record.completedAt >= start && record.completedAt < end) result.add(record);
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
            JSONObject root = new JSONObject().put("version", VERSION).put("current", current)
                .put("water", water).put("waterProgress", waterProgress).put("seeds", seeds).put("planted", planted)
                .put("growth", growth).put("trees", trees);
            JSONArray catalog = new JSONArray();
            for (StudyPlan plan : plans.values()) catalog.put(plan.toJson());
            JSONArray list = new JSONArray();
            for (Record record : records) list.put(record.toJson());
            JSONArray taskList = new JSONArray();
            for (StudyWork.Task task : tasks.values()) taskList.put(task.toJson());
            JSONArray reviewList = new JSONArray();
            for (StudyWork.Review review : reviews.values()) reviewList.put(review.toJson());
            root.put("plans", catalog).put("records", list)
                .put("rewardedDays", new JSONArray(new ArrayList<>(rewardedDays)))
                .put("tasks", taskList).put("reviews", reviewList);
            if (active != null) root.put("active", active.toJson());
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("无法保存学习数据", e);
        }
    }

    public static StudyNotebook fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            int version = root.getInt("version");
            if (version < 1 || version > VERSION) throw new IllegalArgumentException("不支持的学习存档版本");
            boolean legacy = version < 3;
            StudyNotebook notebook = new StudyNotebook();
            if (legacy) notebook.readLegacyPlan(root);
            else notebook.readPlans(root);
            notebook.water = root.getInt("water");
            notebook.seeds = root.getInt("seeds");
            notebook.trees = root.getInt("trees");
            notebook.growth = root.getInt("growth");
            notebook.planted = root.getBoolean("planted");
            if (notebook.water < 0 || notebook.seeds < 0 || notebook.trees < 0 || notebook.growth < 0
                || notebook.growth >= (version == 1 ? 5 : StudyGrowth.REQUIRED_WATER)
                || (!notebook.planted && notebook.growth != 0)) {
                throw new IllegalArgumentException("无效的林地数据");
            }
            if (version == 1) notebook.growth = StudyGrowth.milestone(notebook.growth);
            Set<String> ids = new HashSet<>();
            JSONArray list = root.getJSONArray("records");
            for (int i = 0; i < list.length(); i++) {
                Record record = Record.fromJson(list.getJSONObject(i), legacy);
                notebook.requirePlan(record.planId);
                if (!ids.add(record.id)) throw new IllegalArgumentException("重复的学习记录");
                notebook.records.add(record);
            }
            JSONArray taskList = root.optJSONArray("tasks");
            if (taskList != null) for (int i = 0; i < taskList.length(); i++) {
                StudyWork.Task task = StudyWork.Task.fromJson(taskList.getJSONObject(i));
                notebook.requirePlan(task.planId);
                if (notebook.tasks.put(task.id, task) != null)
                    throw new IllegalArgumentException("重复的学习事项");
            }
            JSONArray reviewList = root.optJSONArray("reviews");
            if (reviewList != null) for (int i = 0; i < reviewList.length(); i++) {
                StudyWork.Review review = StudyWork.Review.fromJson(reviewList.getJSONObject(i));
                if (!ids.contains(review.recordId) || notebook.reviews.put(review.recordId, review) != null)
                    throw new IllegalArgumentException("无效的回顾事项");
            }
            long total = notebook.totalMillis();
            notebook.waterProgress = root.has("waterProgress") ? root.getLong("waterProgress") : total % WATER_INTERVAL_MS;
            if (notebook.waterProgress < 0 || notebook.waterProgress >= WATER_INTERVAL_MS)
                throw new IllegalArgumentException("无效的水滴累计时长");
            JSONArray days = root.getJSONArray("rewardedDays");
            for (int i = 0; i < days.length(); i++) {
                String day = StudyPlan.date(days.getString(i));
                if (day.isEmpty()) throw new IllegalArgumentException("无效的奖励日期");
                notebook.rewardedDays.add(day);
            }
            if (root.has("active")) {
                notebook.active = Session.fromJson(root.getJSONObject("active"), legacy);
                notebook.validateActive(ids);
            }
            return notebook;
        } catch (JSONException | ArithmeticException e) {
            throw new IllegalArgumentException("学习存档无法读取", e);
        }
    }

    private void readLegacyPlan(JSONObject root) throws JSONException {
        currentPlan().selected = StudySubject.legacy(root.getString("selected")).id;
        setSessionGoal(root.getInt("sessionGoal"));
        setDailyGoal(root.getInt("dailyGoal"));
    }

    private void readPlans(JSONObject root) throws JSONException {
        plans.clear();
        JSONArray list = root.getJSONArray("plans");
        for (int i = 0; i < list.length(); i++) {
            StudyPlan plan = StudyPlan.fromJson(list.getJSONObject(i));
            if (plans.put(plan.id, plan) != null) throw new IllegalArgumentException("重复的学习计划");
        }
        String selected = root.getString("current");
        if (plans.isEmpty() && selected.isEmpty()) current = "";
        else switchPlan(selected);
        for (StudyPlan plan : plans.values()) {
            Set<String> path = new HashSet<>();
            for (StudyPlan round = plan; !round.previous.isEmpty(); round = requirePlan(round.previous))
                if (!path.add(round.id)) throw new IllegalArgumentException("无效的计划轮次关联");
        }
    }

    private void validateActive(Set<String> finishedIds) {
        StudyPlan plan = requirePlan(active.planId);
        if (plan.archived != 0 || plan.book(active.subject.id) == null || finishedIds.contains(active.id))
            throw new IllegalArgumentException("计时归属与学习计划或记录不一致");
    }

    String portableJson(long wall, long elapsed, int boot) {
        StudyNotebook copy = fromJson(toJson());
        if (copy.hasSession()) copy.pause(wall, elapsed, boot);
        return copy.toJson();
    }

    /** Keep unique records from both copies; the user chooses which settings and grove to keep. */
    int merge(StudyNotebook backup, boolean preferBackup, long wall, long elapsed, int boot) {
        Map<String, Record> merged = new LinkedHashMap<>();
        for (Record record : records) merged.put(record.id, record);
        int added = 0;
        for (Record record : backup.records) {
            Record local = merged.putIfAbsent(record.id, record);
            if (local == null) added++;
            else {
                if (!local.sameContents(record) && (!local.sameOrigin(record)
                    || (local.editedAt == 0 && record.editedAt == 0)))
                    throw new IllegalArgumentException("同一条学习记录的内容不同，无法合并此备份");
                if (record.editedAt > local.editedAt || (record.editedAt == local.editedAt && preferBackup))
                    merged.put(record.id, record);
            }
        }
        StudyNotebook candidate = fromJson(toJson());
        for (StudyPlan plan : backup.plans.values()) {
            if (preferBackup || !candidate.plans.containsKey(plan.id)) {
                StudyPlan local = candidate.plan(plan.id);
                if (local != null && (local.created != plan.created || local.kind != plan.kind))
                    throw new IllegalArgumentException("同一个学习计划的来源不同，无法合并此备份");
                candidate.plans.put(plan.id, plan);
            }
        }
        candidate.records.clear();
        candidate.records.addAll(merged.values());
        for (StudyWork.Task task : backup.tasks.values()) {
            StudyWork.Task local = candidate.task(task.id);
            if (local == null || task.updated > local.updated || (task.updated == local.updated && preferBackup))
                candidate.tasks.put(task.id, task);
        }
        for (StudyWork.Review review : backup.reviews.values()) {
            StudyWork.Review local = candidate.review(review.recordId);
            if (local == null || review.updated > local.updated || (review.updated == local.updated && preferBackup))
                candidate.reviews.put(review.recordId, review);
        }
        candidate.rewardedDays.addAll(backup.rewardedDays);
        if (preferBackup) {
            if (!backup.current.isEmpty()) candidate.current = backup.current;
            candidate.water = backup.water;
            candidate.waterProgress = backup.waterProgress;
            candidate.seeds = backup.seeds;
            candidate.growth = backup.growth;
            candidate.trees = backup.trees;
            candidate.planted = backup.planted;
        }
        Session local = candidate.active;
        Session incoming = backup.active;
        if (local != null && merged.containsKey(local.id)) local = null;
        if (incoming != null && merged.containsKey(incoming.id)) incoming = null;
        if (local != null && incoming != null && !local.id.equals(incoming.id))
            throw new IllegalStateException("本机与备份各有一段未完成计时，请先保存本机这一段");
        if (incoming != null) {
            if (incoming.running)
                throw new IllegalArgumentException("请先在原设备导出学习备份，再恢复未完成计时");
            if (local != null && (!local.planId.equals(incoming.planId) || !local.subject.equals(incoming.subject)))
                throw new IllegalArgumentException("同一段计时的科目或计划不同，无法合并此备份");
            if (local == null || incoming.accumulated > elapsedMillis(wall, elapsed, boot))
                local = incoming;
        }
        candidate.active = local;
        if (candidate.currentPlan() == null) candidate.selectFallback();
        StudyNotebook validated = fromJson(candidate.toJson());
        plans.clear();
        plans.putAll(validated.plans);
        current = validated.current;
        records.clear();
        records.addAll(validated.records);
        tasks.clear();
        tasks.putAll(validated.tasks);
        reviews.clear();
        reviews.putAll(validated.reviews);
        rewardedDays.clear();
        rewardedDays.addAll(validated.rewardedDays);
        active = validated.active;
        water = validated.water;
        waterProgress = validated.waterProgress;
        seeds = validated.seeds;
        growth = validated.growth;
        trees = validated.trees;
        planted = validated.planted;
        return added;
    }

    private static final class Session {
        final String id;
        final String planId;
        final StudySubject subject;
        final int goal;
        final String taskId;
        long accumulated, wall, elapsed;
        int boot;
        boolean running;

        Session(String id, String planId, StudySubject subject, int goal, String taskId) {
            this.id = StudyPlan.required(id, 120, "计时标识");
            this.planId = planId;
            this.subject = subject;
            this.goal = validateSessionGoal(goal);
            this.taskId = StudyPlan.optional(taskId, 120, "任务标识");
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("plan", planId).put("subject", subject.toJson())
                .put("goal", goal).put("accumulated", accumulated).put("running", running)
                .put("wall", wall).put("elapsed", elapsed).put("boot", boot).put("task", taskId);
        }

        static Session fromJson(JSONObject json, boolean legacy) throws JSONException {
            long wall = json.getLong("wall");
            long elapsed = json.getLong("elapsed");
            String id = legacy ? UUID.nameUUIDFromBytes(("study:" + wall + ":" + elapsed + ":"
                + json.getString("subject")).getBytes(StandardCharsets.UTF_8)).toString()
                : json.getString("id");
            Session session = new Session(id, legacy ? StudyPlan.LEGACY_ID : json.getString("plan"),
                legacy ? StudySubject.legacy(json.getString("subject"))
                    : StudySubject.fromJson(json.getJSONObject("subject")), json.getInt("goal"),
                json.optString("task"));
            session.accumulated = json.getLong("accumulated");
            session.running = json.getBoolean("running");
            session.wall = wall;
            session.elapsed = elapsed;
            session.boot = json.getInt("boot");
            if (session.accumulated < 0 || elapsed < 0 || wall < 0)
                throw new IllegalArgumentException("无效的计时存档");
            return session;
        }
    }

    public static final class Record {
        public final String id;
        public final String planId;
        public final StudySubject subject;
        public final long durationMillis;
        public final long completedAt;
        public final String note;
        final boolean manual, adjusted;
        final long rewardMillis, editedAt;
        final String taskId;

        Record(String id, String planId, StudySubject subject, long durationMillis, long completedAt, String note) {
            this(id, planId, subject, durationMillis, completedAt, note, false, durationMillis, 0, false, "");
        }

        private Record(String id, String planId, StudySubject subject, long durationMillis,
                       long completedAt, String note, boolean manual, long rewardMillis, long editedAt,
                       boolean adjusted, String taskId) {
            this.id = StudyPlan.required(id, 120, "记录标识");
            this.planId = StudyPlan.required(planId, 120, "记录所属计划");
            if (subject == null || durationMillis < 1_000 || completedAt <= 0
                || note == null || note.length() > 120 || rewardMillis < 0 || editedAt < 0
                || (manual && rewardMillis != 0)) throw new IllegalArgumentException("无效的学习记录");
            this.subject = subject;
            this.durationMillis = durationMillis;
            this.completedAt = completedAt;
            this.note = note;
            this.manual = manual;
            this.rewardMillis = rewardMillis;
            this.editedAt = editedAt;
            this.adjusted = adjusted;
            this.taskId = StudyPlan.optional(taskId, 120, "任务标识");
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("plan", planId).put("subject", subject.toJson())
                .put("duration", durationMillis).put("completedAt", completedAt).put("note", note)
                .put("manual", manual).put("rewardMillis", rewardMillis).put("editedAt", editedAt)
                .put("adjusted", adjusted).put("task", taskId);
        }

        static Record fromJson(JSONObject json, boolean legacy) throws JSONException {
            long duration = json.getLong("duration");
            return new Record(json.getString("id"), legacy ? StudyPlan.LEGACY_ID : json.getString("plan"),
                legacy ? StudySubject.legacy(json.getString("subject"))
                    : StudySubject.fromJson(json.getJSONObject("subject")),
                duration, json.getLong("completedAt"), json.getString("note"), json.optBoolean("manual"),
                json.optLong("rewardMillis", duration), json.optLong("editedAt"), json.optBoolean("adjusted"),
                json.optString("task"));
        }

        boolean sameContents(Record other) {
            return sameOrigin(other) && subject.equals(other.subject)
                && durationMillis == other.durationMillis && completedAt == other.completedAt
                && note.equals(other.note) && adjusted == other.adjusted;
        }

        boolean sameOrigin(Record other) {
            return planId.equals(other.planId) && manual == other.manual && rewardMillis == other.rewardMillis
                && taskId.equals(other.taskId) && (manual || completedAt == other.completedAt);
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
