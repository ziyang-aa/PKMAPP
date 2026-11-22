package com.example.pkmapp.study;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class StudyNotebookTest {

    private static final long DAY = 1_800_000_000_000L;
    private static final long MINUTE = 60_000L;

    private StudyNotebook.Completion session(StudyNotebook n, long at, long duration) {
        n.start(at - duration, 1_000, 8);
        return n.finish(at, 1_000 + duration, 8, "复习二叉树 <&>");
    }

    @Test
    public void pauseExcludesRestAndSameBootIgnoresWallClockChanges() {
        StudyNotebook n = new StudyNotebook();
        n.start(DAY, 1000, 8);
        assertEquals(30_000, n.elapsedMillis(DAY - 60_000, 31_000, 8));
        n.pause(DAY + 30_000, 31_000, 8);
        assertEquals(30_000, n.elapsedMillis(DAY + 9_000_000, 9_001_000, 8));
        n.resume(DAY + 9_000_000, 9_001_000, 8);
        assertEquals(45_000, n.elapsedMillis(DAY + 12_000_000, 9_016_000, 8));
    }

    @Test
    public void runningAndPausedSessionsSurviveSerialization() {
        StudyNotebook n = new StudyNotebook();
        n.select(StudySubject.ENGLISH);
        n.setSessionGoal(60);
        n.start(DAY, 100, 2);
        StudyNotebook restored = StudyNotebook.fromJson(n.toJson());
        assertTrue(restored.running());
        assertEquals(StudySubject.ENGLISH, restored.activeSubject());
        assertEquals(60, restored.activeGoal());
        assertEquals(10_000, restored.elapsedMillis(DAY + 10_000, 10_100, 2));
        restored.pause(DAY + 10_000, 10_100, 2);
        restored = StudyNotebook.fromJson(restored.toJson());
        assertFalse(restored.running());
        assertEquals(10_000, restored.elapsedMillis(DAY + 200_000, 200_100, 2));
    }

    @Test
    public void deviceRebootUsesPersistedWallAnchorWithoutLosingAccumulatedTime() {
        StudyNotebook n = new StudyNotebook();
        n.start(DAY, 900_000, 4);
        n.pause(DAY + 10_000, 910_000, 4);
        n.resume(DAY + 20_000, 920_000, 4);
        n = StudyNotebook.fromJson(n.toJson());
        assertEquals(130_000, n.elapsedMillis(DAY + 140_000, 12_000, 5));
    }

    @Test
    public void shortSessionsAccumulateWaterAndFinishCannotBeRepeated() {
        StudyNotebook n = new StudyNotebook();
        assertEquals(0, session(n, DAY, 10 * MINUTE).water);
        assertEquals(1, session(n, DAY + 20 * MINUTE, 15 * MINUTE).water);
        assertEquals(1, n.water());
        assertEquals(2, n.records().size());
        try {
            n.finish(DAY + 22 * MINUTE, 99_000, 8, "");
            fail();
        } catch (IllegalStateException expected) {
            assertEquals(2, n.records().size());
        }
        assertEquals(25 * MINUTE, n.totalMillis());
    }

    @Test
    public void dailySeedIsGrantedOnceEvenWhenGoalChangesAndAgainNextDay() {
        StudyNotebook n = new StudyNotebook();
        n.setDailyGoal(25);
        assertEquals(1, session(n, DAY, 25 * MINUTE).seeds);
        n.setDailyGoal(10);
        assertEquals(0, session(n, DAY + 20 * MINUTE, 10 * MINUTE).seeds);
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(DAY);
        next.add(Calendar.DATE, 1);
        assertEquals(1, session(n, next.getTimeInMillis(), 10 * MINUTE).seeds);
        assertEquals(3, n.seeds());
    }

    @Test
    public void wateringConsumesResourcesOnceAndMatureTreeSurvivesReload() {
        StudyNotebook n = new StudyNotebook();
        session(n, DAY, 125 * MINUTE);
        assertEquals(5, n.water());
        n.plant();
        assertEquals(0, n.seeds());
        for (int i = 0; i < 19; i++) n.addWater();
        for (int i = 0; i < StudyGrowth.REQUIRED_WATER - 1; i++) assertFalse(n.waterTree());
        assertTrue(n.waterTree());
        n = StudyNotebook.fromJson(n.toJson());
        assertEquals(1, n.trees());
        assertEquals(0, n.water());
        assertFalse(n.planted());
        assertEquals(0, n.growth());
        try {
            n.waterTree();
            fail();
        } catch (IllegalStateException expected) {
            assertEquals(0, n.water());
        }
        try {
            n.plant();
            fail();
        } catch (IllegalStateException expected) {
            assertFalse(n.planted());
        }
    }

    @Test
    public void legacyGardenKeepsItsVisualStageAndMigratesOnlyOnce() throws Exception {
        for (int oldGrowth = 0; oldGrowth < 5; oldGrowth++) {
            JSONObject legacy = legacyNotebook(1, oldGrowth);
            StudyNotebook migrated = StudyNotebook.fromJson(legacy.toString());
            assertEquals(StudyGrowth.milestone(oldGrowth), migrated.growth());
            assertEquals(oldGrowth, StudyGrowth.stage(migrated.growth()));
            assertTrue(migrated.planted());
            assertTrue(migrated.running());
            assertEquals(migrated.growth(), StudyNotebook.fromJson(migrated.toJson()).growth());
        }
    }

    private JSONObject legacyNotebook(int version, int growth) throws Exception {
        return new JSONObject()
            .put("version", version).put("selected", "DATA_STRUCTURES")
            .put("sessionGoal", 45).put("dailyGoal", 180)
            .put("water", 4).put("seeds", 0).put("trees", 2)
            .put("planted", true).put("growth", growth)
            .put("rewardedDays", new JSONArray().put(StudyNotebook.dayKey(DAY)))
            .put("records", new JSONArray().put(new JSONObject()
                .put("id", "saved-before-plans").put("subject", "OPERATING_SYSTEMS")
                .put("duration", 12_345).put("completedAt", DAY).put("note", "旧版手记")))
            .put("active", new JSONObject()
                .put("subject", "NETWORKS").put("goal", 60).put("accumulated", 5000)
                .put("running", true).put("wall", DAY).put("elapsed", 0).put("boot", 1));
    }

    @Test
    public void migratesVersionTwoIncludingActiveTimerAndGrove() throws Exception {
        StudyNotebook n = StudyNotebook.fromJson(legacyNotebook(2, 47).toString());
        assertEquals("408 考研", n.currentPlan().title);
        assertEquals(7, n.currentPlan().books().size());
        assertEquals(StudySubject.DATA_STRUCTURES, n.selected());
        assertEquals(StudySubject.NETWORKS, n.activeSubject());
        assertEquals(60, n.activeGoal());
        assertEquals(4, n.water());
        assertEquals(2, n.trees());
        assertEquals(47, n.growth());
        assertEquals("saved-before-plans", n.records().get(0).id);
        assertEquals("旧版手记", n.records().get(0).note);
        n = StudyNotebook.fromJson(n.toJson());
        assertEquals(1, n.plans().size());
        assertEquals(1, n.records().size());
        assertEquals(7000, n.elapsedMillis(DAY + 2000, 2000, 1));
    }

    @Test
    public void switchingAndArchivingKeepSessionsAndIndependentGoals() {
        StudyNotebook n = new StudyNotebook();
        StudyPlan first = n.currentPlan();
        n.setSessionGoal(60);
        n.setDailyGoal(90);
        n.start(DAY, 1000, 8);
        StudySubject database = StudyCatalog.book("数据库原理", "", 0);
        StudyPlan finals = n.createPlan("大三上期末", StudyPlan.Kind.FINALS, "2027-01-15",
            StudyPlan.Partner.TREECKO, Collections.singletonList(database), DAY + 1000);
        assertFalse(n.currentHasSession());
        assertEquals(45, n.sessionGoal());
        assertEquals(database, n.selected());
        assertThrows(IllegalStateException.class, () -> n.start(DAY + 1000, 2000, 8));
        assertThrows(IllegalStateException.class, () -> n.archive(first.id, DAY + 1000));
        n.finish(DAY + 10 * MINUTE, 1000 + 10 * MINUTE, 8, "一轮手记");
        assertEquals(first.id, n.records().get(0).planId);
        session(n, DAY + 25 * MINUTE, 15 * MINUTE);
        StudyPlan second = n.nextRound(first.id, "408 二轮", DAY + 30 * MINUTE);
        StudyNotebook restored = StudyNotebook.fromJson(n.toJson());
        assertEquals(first.id, restored.currentPlan().previous);
        assertTrue(restored.plan(first.id).archived > 0);
        assertEquals(60, restored.sessionGoal());
        assertEquals(90, restored.dailyGoal());
        assertEquals(0, restored.forPlan(second.id).size());
        assertEquals("一轮手记", restored.forPlan(first.id).get(0).note);
        assertEquals(15 * MINUTE, restored.forPlan(finals.id).get(0).durationMillis);
        assertEquals(25 * MINUTE, restored.totalMillis());
        assertEquals(1, restored.water());
        restored.restore(first.id);
        assertEquals(0, restored.currentPlan().archived);
    }

    @Test
    public void removedAndRenamedBooksKeepTheirHistoricalLabels() {
        StudyNotebook n = new StudyNotebook();
        StudySubject old = StudyCatalog.book("实验课", "", 0);
        StudySubject second = StudyCatalog.book("实验课", "", 1);
        StudyPlan plan = n.createPlan("实验周", StudyPlan.Kind.CUSTOM, "",
            StudyPlan.Partner.SQUIRREL, Arrays.asList(old, second), DAY);
        session(n, DAY + MINUTE, MINUTE);
        n.editPlan(plan.id, plan.title, "", plan.partner, Collections.singletonList(
            second.edit("模拟实验", "", "2027-01-15", "上机")));
        session(n, DAY + 2 * MINUTE, MINUTE);
        n = StudyNotebook.fromJson(n.toJson());
        assertEquals("实验课", n.records().get(0).subject.title);
        assertEquals("模拟实验", n.selected().title);
        assertNotEquals(n.records().get(0).subject, n.records().get(1).subject);
        assertEquals("上机", n.selected().assessment);
    }

    @Test
    public void backupKeepsGroveAndMergesByIdWithoutResurrectingCompletedTimer() {
        StudyNotebook source = new StudyNotebook();
        source.addWater();
        source.plant();
        source.waterTree();
        session(source, DAY + MINUTE, MINUTE);
        source.start(DAY + 2 * MINUTE, 0, 8);
        StudyNotebook portable = StudyNotebook.fromJson(source.portableJson(DAY + 3 * MINUTE, MINUTE, 8));
        assertTrue(source.running());
        StudyNotebook target = new StudyNotebook();
        assertEquals(1, target.merge(portable, true, DAY + 20 * MINUTE, 900, 8));
        assertFalse(target.running());
        assertTrue(target.hasSession());
        assertEquals(MINUTE, target.elapsedMillis(DAY + 25 * MINUTE, 900, 8));
        assertEquals(1, target.growth());
        assertEquals(4, target.water());
        target.finish(DAY + 25 * MINUTE, 900, 8, "");
        assertEquals(0, target.merge(portable, false, DAY + 26 * MINUTE, 1000, 8));
        assertFalse(target.hasSession());
        assertEquals(2, target.records().size());
    }

    @Test
    public void backupWithTwoDifferentPendingTimersDoesNotPartiallyApply() {
        StudyNotebook local = new StudyNotebook();
        StudyNotebook remote = new StudyNotebook();
        local.start(DAY, 1000, 8);
        session(remote, DAY + MINUTE, MINUTE);
        remote.start(DAY + 2 * MINUTE, 1000, 8);
        StudyNotebook portable = StudyNotebook.fromJson(remote.portableJson(DAY + 3 * MINUTE, 61_000, 8));
        String previous = local.toJson();
        assertThrows(IllegalStateException.class, () -> local.merge(portable, true, DAY + MINUTE, 2000, 8));
        assertEquals(previous, local.toJson());
    }

    @Test
    public void deletingAnArchivedRoundKeepsOtherRecordsAndAccumulatedRewards() {
        StudyNotebook n = new StudyNotebook();
        StudyPlan first = n.currentPlan();
        session(n, DAY, 35 * MINUTE);
        StudyPlan middle = n.nextRound(first.id, "二轮", DAY + MINUTE);
        session(n, DAY + 6 * MINUTE, 5 * MINUTE);
        StudyPlan latest = n.nextRound(middle.id, "三轮", DAY + 7 * MINUTE);
        n.start(DAY + 8 * MINUTE, 1000, 8);
        assertThrows(IllegalStateException.class, () -> n.deletePlan(latest.id));
        n.deletePlan(middle.id);
        StudyNotebook reloaded = StudyNotebook.fromJson(n.toJson());
        assertEquals(first.id, reloaded.currentPlan().previous);
        assertEquals(latest.id, reloaded.activePlanId());
        assertEquals(1, reloaded.records().size());
        assertEquals(35 * MINUTE, reloaded.totalMillis());
        assertEquals(1, reloaded.water());
        assertEquals(1, reloaded.finish(DAY + 18 * MINUTE, 1000 + 10 * MINUTE, 8, "").water);
        assertEquals(45 * MINUTE, reloaded.totalMillis());
    }

    @Test
    public void deletingTheLastPlanPersistsAnEmptyNotebookAndCanRestoreABackup() throws Exception {
        StudyNotebook original = new StudyNotebook();
        session(original, DAY, 15 * MINUTE);
        JSONObject oldFormat = new JSONObject(original.toJson());
        oldFormat.remove("waterProgress");
        original = StudyNotebook.fromJson(oldFormat.toString());
        String backup = original.toJson();
        original.plant();
        original.deletePlan(StudyPlan.LEGACY_ID);
        StudyNotebook reloaded = StudyNotebook.fromJson(original.toJson());
        assertNull(reloaded.currentPlan());
        assertTrue(reloaded.records().isEmpty());
        assertTrue(reloaded.planted());
        assertThrows(IllegalStateException.class, () -> reloaded.start(DAY, 1000, 8));
        assertEquals(1, reloaded.merge(StudyNotebook.fromJson(backup), false, DAY, 1000, 8));
        assertTrue(reloaded.planted());
        assertEquals(StudyPlan.LEGACY_ID, reloaded.currentPlan().id);
        assertEquals(1, session(reloaded, DAY + 10 * MINUTE, 10 * MINUTE).water);
    }

    @Test
    public void wateringAdvancesOnePointAndReloadsAcrossMilestones() {
        StudyNotebook n = new StudyNotebook();
        n.plant();
        for (int i = 0; i < 20; i++) n.addWater();
        for (int growth = 1; growth < StudyGrowth.REQUIRED_WATER; growth++) {
            assertFalse(n.waterTree());
            n = StudyNotebook.fromJson(n.toJson());
            assertEquals(growth, n.growth());
            assertEquals(StudyGrowth.REQUIRED_WATER - growth, n.water());
            assertTrue(n.planted());
            assertEquals(0, n.trees());
        }
        assertEquals(0, StudyGrowth.stage(14));
        assertEquals(1, StudyGrowth.stage(15));
        assertEquals(2, StudyGrowth.stage(35));
        assertEquals(3, StudyGrowth.stage(60));
        assertEquals(4, StudyGrowth.stage(85));
        assertEquals(5, StudyGrowth.stage(100));
        assertTrue(n.waterTree());
        assertFalse(n.planted());
        assertEquals(1, n.trees());
    }

    @Test
    public void subjectAndGoalStayLockedUntilSessionIsCompleted() {
        StudyNotebook n = new StudyNotebook();
        n.start(DAY, 0, 1);
        try {
            n.select(StudySubject.NETWORKS);
            fail();
        } catch (IllegalStateException expected) {
            assertEquals(StudySubject.DATA_STRUCTURES, n.activeSubject());
        }
        try {
            n.setSessionGoal(25);
            fail();
        } catch (IllegalStateException expected) {
            assertEquals(45, n.activeGoal());
        }
        n.discard();
        n.select(StudySubject.NETWORKS);
        assertEquals(StudySubject.NETWORKS, n.selected());
    }

    @Test
    public void recordsPreserveSubjectExactDurationNotesAndRangeBoundaries() {
        StudyNotebook n = new StudyNotebook();
        n.select(StudySubject.OPERATING_SYSTEMS);
        session(n, DAY, 12_345);
        StudyNotebook restored = StudyNotebook.fromJson(n.toJson());
        StudyNotebook.Record r = restored.records().get(0);
        assertEquals(StudySubject.OPERATING_SYSTEMS, r.subject);
        assertEquals(12_345, r.durationMillis);
        assertEquals("复习二叉树 <&>", r.note);
        assertEquals(1, restored.between(DAY, DAY + 1).size());
        assertEquals(0, restored.between(DAY - 1, DAY).size());
        assertEquals("2小时30分钟", StudyFormat.duration(150 * MINUTE));
    }

    @Test
    public void malformedAndUnknownVersionDataAreRejected() {
        for (String data : new String[] {
            "{",
            "{\"version\":2}",
            new StudyNotebook().toJson().replace("\"water\":0", "\"water\":-1"),
        }) {
            try {
                StudyNotebook.fromJson(data);
                fail(data);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }
}
