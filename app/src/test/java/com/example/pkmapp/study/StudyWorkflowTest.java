package com.example.pkmapp.study;

import static org.junit.Assert.*;

import java.util.Collections;
import org.json.JSONObject;
import org.junit.Test;

public final class StudyWorkflowTest {
    private static final long DAY = StudyFormat.day("2026-11-19").getTimeInMillis();
    private static final long MINUTE = 60_000;

    @Test
    public void correctedTimeAndNextStepSurviveReload() {
        StudyNotebook book = new StudyNotebook();
        book.start(DAY, 1000, 5, "", "图论第 1–8 题");
        StudyWork.Task first = book.task(book.activeTaskId());
        book = StudyNotebook.fromJson(book.toJson());
        assertEquals(first.id, book.activeTaskId());
        StudyNotebook.Completion saved = book.finish(DAY + 60 * MINUTE, 1000 + 60 * MINUTE,
            5, "做完前四题", StudySubject.DATA_STRUCTURES.id, 35 * MINUTE, false, "接着做第 5 题");
        assertEquals(35 * MINUTE, saved.record.durationMillis);
        assertEquals(1, saved.water);
        assertTrue(saved.record.adjusted);
        assertEquals(0, book.task(first.id).completed);
        book = StudyNotebook.fromJson(book.toJson());
        StudyWork.Task next = book.nextTask(book.currentPlan().id);
        assertEquals("接着做第 5 题", next.title);
        book.start(DAY + 86_400_000, 500, 6, next.id, "");
        book.finish(DAY + 86_401_000, 1500, 6, "", null, 0, true, "");
        assertNull(book.nextTask(book.currentPlan().id));
        assertEquals(1, book.completedTasks(book.currentPlan().id, DAY, DAY + 2 * 86_400_000).size());
    }

    @Test
    public void manualAndEditedTimeDoNotCountTowardsWaterOrSeed() {
        StudyNotebook book = new StudyNotebook();
        book.setDailyGoal(30);
        String plan = book.currentPlan().id;
        StudyNotebook.Record manual = book.addRecord(plan, StudySubject.ENGLISH.id, 40 * MINUTE, DAY, "纸笔练习");
        book.start(DAY, 1000, 5);
        StudyNotebook.Completion timed = book.finish(DAY + MINUTE, 1000 + MINUTE, 5, "", null,
            50 * MINUTE, false, "");
        assertEquals(0, timed.seeds);
        assertEquals(0, timed.water);
        book.editRecord(timed.record.id, StudySubject.ENGLISH.id, 35 * MINUTE,
            timed.record.completedAt, "改正时长", DAY + 2 * MINUTE);
        assertEquals(75 * MINUTE, book.totalForDay(plan, DAY));
        assertEquals(0, book.water());
        assertEquals(1, book.seeds());
        assertTrue(StudyNotebook.fromJson(book.toJson()).record(manual.id).manual);
        StudyNotebook backupBeforeEdit = StudyNotebook.fromJson(book.toJson());
        book.editRecord(timed.record.id, StudySubject.ENGLISH.id, 30 * MINUTE,
            timed.record.completedAt, "旧手记已修正", DAY + 3 * MINUTE);
        backupBeforeEdit.merge(book, false, DAY + 4 * MINUTE, 1000, 5);
        assertEquals(30 * MINUTE, backupBeforeEdit.record(timed.record.id).durationMillis);
        assertEquals(2, backupBeforeEdit.records().size());
    }

    @Test
    public void carryOnlySelectedTasksAndKeepOldReviewSource() {
        StudyNotebook book = new StudyNotebook();
        StudyPlan old = book.currentPlan();
        book.setDeadline(old.id, StudySubject.ENGLISH.id, "2026-11-23");
        StudyWork.Task keep = book.putTask(old.id, StudySubject.ENGLISH.id, "英语真题", "2026-11-20", DAY, false);
        StudyWork.Task other = book.putTask(old.id, StudySubject.DATA_STRUCTURES.id, "写实验", "", DAY, false);
        book.prioritizeTask(other.id, DAY + 2000);
        assertEquals(other.id, book.pendingTasks(old.id).get(0).id);
        StudyNotebook.Record source = book.addRecord(old.id, StudySubject.ENGLISH.id, MINUTE, DAY, "旧轮手记");
        book.changeReview(source.id, "回想阅读题", "2026-11-20", false, DAY);
        StudyPlan next = book.nextRound(old.id, "二轮", DAY + MINUTE, false, Collections.singleton(keep.id));
        book = StudyNotebook.fromJson(book.toJson());
        assertEquals("2026-11-23", book.plan(old.id).book(StudySubject.ENGLISH.id).due);
        assertEquals("", book.plan(next.id).book(StudySubject.ENGLISH.id).due);
        assertEquals(2, book.pendingTasks(old.id).size());
        assertEquals(1, book.pendingTasks(next.id).size());
        assertEquals("", book.pendingTasks(next.id).get(0).day);
        assertEquals("英语真题", book.pendingTasks(next.id).get(0).title);
        assertTrue(book.records().get(0).planId.equals(old.id));
        book.changeReview(source.id, "回想阅读题", "2026-11-20", true, DAY + 2 * MINUTE);
        assertTrue(book.pendingReviews(old.id).isEmpty());
        assertEquals(source.id, book.completedReviews(old.id, DAY, DAY + 3 * MINUTE).get(0).recordId);
        assertEquals("旧轮手记", book.record(source.id).note);
    }

    @Test
    public void originalVersionThreeKeepsCustomPlanAndRewardHistory() throws Exception {
        StudyNotebook book = new StudyNotebook();
        StudyPlan custom = book.createPlan("图论复习", StudyPlan.Kind.CUSTOM, "",
            book.currentPlan().partner, Collections.singletonList(StudySubject.DATA_STRUCTURES), DAY);
        book.start(DAY, 1000, 5);
        StudyNotebook.Record old = book.finish(DAY + 40 * MINUTE, 1000 + 40 * MINUTE, 5, "旧记录").record;
        JSONObject versionThree = new JSONObject(book.toJson()).put("version", 3);
        JSONObject record = versionThree.getJSONArray("records").getJSONObject(0);
        for (String key : new String[] { "manual", "rewardMillis", "editedAt", "adjusted", "task" }) record.remove(key);
        versionThree.remove("tasks");
        versionThree.remove("reviews");
        StudyNotebook restored = StudyNotebook.fromJson(versionThree.toString());
        assertEquals(custom.id, restored.currentPlan().id);
        assertEquals(custom.id, restored.record(old.id).planId);
        assertEquals("旧记录", restored.record(old.id).note);
        assertEquals(40 * MINUTE, restored.record(old.id).rewardMillis);
        assertEquals(1, restored.water());
    }
}
