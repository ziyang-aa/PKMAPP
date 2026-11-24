package com.example.pkmapp.study

import com.example.pkmapp.borrowing.BorrowingDirection
import com.example.pkmapp.borrowing.BorrowingListAdapter
import com.example.pkmapp.borrowing.BorrowingListItem
import com.example.pkmapp.borrowing.BorrowingRecord
import com.example.pkmapp.exchange.ExchangeCurrencyListBuilder
import com.example.pkmapp.exchange.ExchangeViewModel
import com.example.pkmapp.todo.TodoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class Phase5ListModelsTest {

    @Test
    fun todoListBuilder_partitionsTodayPlannedDoneAndSingleDayCleanly() {
        val today = "2026-11-24"
        val tomorrow = "2026-11-25"
        val yesterday = "2026-11-23"
        val overdue = TodoStore.TodoItem("t1", "补交实验报告", "第3章", "2026-11-22", "18:00", true, 30, 1L, 0L)
        val todayItem = TodoStore.TodoItem("t2", "背单词", "", today, "21:00", true, 15, 2L, 0L)
        val futureItem = TodoStore.TodoItem("t3", "模拟考试", "", tomorrow, "", false, 30, 3L, 0L)
        val anytimeItem = TodoStore.TodoItem("t4", "整理错题本", "", "", "", false, 30, 4L, 0L)
        val doneCal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 24, 14, 30, 0) }
        val doneItem = TodoStore.TodoItem("t5", "晨读", "", today, "", false, 30, 5L, doneCal.timeInMillis)

        val todayModel = StudyTodoListBuilder.build(
            pendingList = listOf(overdue, todayItem, futureItem, anytimeItem),
            doneList = listOf(doneItem),
            defaultRemindTime = "20:00",
            todoFilter = 0,
            todoFilterDay = null,
            todayKey = today,
            tomorrowKey = tomorrow,
            yesterdayKey = yesterday
        )
        assertEquals(2, todayModel.todayCount)
        assertEquals(2, todayModel.plannedCount)
        assertEquals(1, todayModel.doneCount)
        assertEquals(today, todayModel.quickAddDay)
        assertEquals(4, todayModel.items.size)
        assertTrue(todayModel.items[0] is StudyTodoListItem.GroupHeader)
        assertTrue((todayModel.items[0] as StudyTodoListItem.GroupHeader).overdue)
        assertEquals("t1", (todayModel.items[1] as StudyTodoListItem.TodoEntry).itemId)

        val plannedModel = StudyTodoListBuilder.build(
            pendingList = listOf(overdue, todayItem, futureItem, anytimeItem),
            doneList = listOf(doneItem),
            defaultRemindTime = "20:00",
            todoFilter = 1,
            todoFilterDay = null,
            todayKey = today,
            tomorrowKey = tomorrow,
            yesterdayKey = yesterday
        )
        assertEquals("", plannedModel.quickAddDay)
        assertEquals(4, plannedModel.items.size)
        assertEquals("t3", (plannedModel.items[1] as StudyTodoListItem.TodoEntry).itemId)
        assertEquals("t4", (plannedModel.items[3] as StudyTodoListItem.TodoEntry).itemId)

        val doneModel = StudyTodoListBuilder.build(
            pendingList = listOf(overdue, todayItem),
            doneList = listOf(doneItem),
            defaultRemindTime = "20:00",
            todoFilter = 2,
            todoFilterDay = null,
            todayKey = today,
            tomorrowKey = tomorrow,
            yesterdayKey = yesterday
        )
        assertNull(doneModel.quickAddDay)
        assertEquals(2, doneModel.items.size)
        assertTrue((doneModel.items[1] as StudyTodoListItem.TodoEntry).checked)

        val editedToday = TodoStore.TodoItem("t2", "背核心词汇", "", today, "21:00", true, 15, 2L, 0L)
        val editedModel = StudyTodoListBuilder.build(
            pendingList = listOf(overdue, editedToday),
            doneList = listOf(doneItem),
            defaultRemindTime = "20:00",
            todoFilter = 0,
            todoFilterDay = null,
            todayKey = today,
            tomorrowKey = tomorrow,
            yesterdayKey = yesterday
        )
        assertNotEquals(todayModel.items[3], editedModel.items[3])
    }

    @Test
    fun studyRecordListAdapter_filtersBySubjectAndQueryAndDetectsEdits() {
        val notebook = StudyNotebook()
        val planId = notebook.currentPlan().id
        notebook.start(1_700_000_000_000L, 1_000L, 1, "", "")
        val r1 = notebook.finish(
            1_700_000_000_000L + 35 * 60_000L,
            1_000L + 35 * 60_000L,
            1,
            "图论第 4 题",
            StudySubject.DATA_STRUCTURES.id,
            0L,
            false,
            ""
        ).record
        val r2 = notebook.addRecord(
            planId, StudySubject.ENGLISH.id, 40 * 60_000L, 1_700_001_000_000L, "阅读真题"
        )

        val allItems = StudyRecordListAdapter.buildItems(listOf(r1, r2), null, "", null)
        assertEquals(2, allItems.size)
        assertTrue(allItems[1].subtitle.contains("手动补记"))

        val filtered = StudyRecordListAdapter.buildItems(
            listOf(r1, r2), StudySubject.DATA_STRUCTURES, "图论", null
        )
        assertEquals(1, filtered.size)
        assertEquals("35分钟", filtered[0].durationText)

        val r1Edited = notebook.editRecord(
            r1.id,
            StudySubject.DATA_STRUCTURES.id,
            33 * 60_000L,
            r1.completedAt,
            "图论第 4 题",
            1_700_002_000_000L
        )
        val editedList = StudyRecordListAdapter.buildItems(
            listOf(r1Edited), StudySubject.DATA_STRUCTURES, "图论", null
        )
        assertNotEquals(filtered[0], editedList[0])
        assertTrue(editedList[0].subtitle.contains("时长已修正"))
    }

    @Test
    fun borrowingListAdapter_groupsByDateAndMarksInnerHairlines() {
        val ts = 1_732_435_200_000L
        val r1 = BorrowingRecord("b1", "default", BorrowingDirection.LEND, 12500L, ts, "小林", "午饭垫付", null)
        val r2 = BorrowingRecord("b2", "default", BorrowingDirection.BORROW, 5000L, ts, "阿远", "", null)

        val items = BorrowingListAdapter.buildItems(listOf(r1, r2))
        assertEquals(3, items.size)
        assertTrue(items[0] is BorrowingListItem.DateHeader)
        assertTrue((items[0] as BorrowingListItem.DateHeader).firstGroup)

        val firstEntry = items[1] as BorrowingListItem.Entry
        val secondEntry = items[2] as BorrowingListItem.Entry
        assertFalse(firstEntry.showTopHairline)
        assertTrue(secondEntry.showTopHairline)
    }

    @Test
    fun exchangeCurrencyListBuilder_filtersByCodeOrChineseName() {
        val byCode = ExchangeCurrencyListBuilder.filterCurrencies(ExchangeViewModel.CURRENCIES, "jpy", "JPY")
        assertEquals(1, byCode.size)
        assertEquals("JPY", byCode[0].code)
        assertTrue(byCode[0].selected)

        val byName = ExchangeCurrencyListBuilder.filterCurrencies(ExchangeViewModel.CURRENCIES, "欧元", "CNY")
        assertEquals(1, byName.size)
        assertEquals("EUR", byName[0].code)
        assertFalse(byName[0].selected)
    }
}
