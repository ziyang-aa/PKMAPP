package com.example.pkmapp.study;

import static org.junit.Assert.*;

import java.util.Calendar;
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
            StudyNotebook original = new StudyNotebook();
            original.plant();
            original.start(DAY, 0, 1);
            org.json.JSONObject legacy = new org.json.JSONObject(original.toJson());
            legacy.put("version", 1).put("growth", oldGrowth);
            StudyNotebook migrated = StudyNotebook.fromJson(legacy.toString());
            assertEquals(StudyGrowth.milestone(oldGrowth), migrated.growth());
            assertEquals(oldGrowth, StudyGrowth.stage(migrated.growth()));
            assertTrue(migrated.planted());
            assertTrue(migrated.running());
            assertEquals(migrated.growth(), StudyNotebook.fromJson(migrated.toJson()).growth());
        }
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
