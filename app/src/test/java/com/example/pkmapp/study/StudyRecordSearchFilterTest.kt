package com.example.pkmapp.study

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRecordSearchFilterTest {

    private fun sampleRecord(): StudyNotebook.Record {
        val notebook = StudyNotebook()
        val startWall = 1_760_000_000_000L
        notebook.start(startWall, 1_000L, 7)
        notebook.finish(startWall + 45 * 60_000L, 1_000L + 45 * 60_000L, 7, "复习平衡二叉树旋转逻辑")
        return notebook.forPlan(null).first()
    }

    @Test
    fun matchesAcrossNoteSubjectPlanAndTaskTitle() {
        val record = sampleRecord()
        assertTrue(StudyRecordSearchFilter.matches(record, null, "旋转"))
        assertTrue(StudyRecordSearchFilter.matches(record, null, record.subject.title))
        assertTrue(StudyRecordSearchFilter.matches(record, null, "考研", planTitle = "408 考研"))
        assertTrue(StudyRecordSearchFilter.matches(record, null, "真题", taskTitle = "完成2024真题"))
        assertFalse(StudyRecordSearchFilter.matches(record, null, "微积分", planTitle = "408 考研"))
    }

    @Test
    fun respectsSubjectSelectionFilter() {
        val record = sampleRecord()
        val other = StudySubject.NETWORKS
        assertTrue(StudyRecordSearchFilter.matches(record, record.subject, ""))
        assertFalse(StudyRecordSearchFilter.matches(record, other, "旋转"))
    }
}
