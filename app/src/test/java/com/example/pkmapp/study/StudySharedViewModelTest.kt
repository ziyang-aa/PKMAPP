package com.example.pkmapp.study

import com.example.pkmapp.todo.TodoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySharedViewModelTest {

    @Test
    fun syncFromNotebook_projectsPlanSessionAndGardenState() = runTest {
        val vm = StudySharedViewModel()
        val notebook = StudyNotebook()
        vm.syncFromNotebook(notebook)

        val initial = vm.studyState.first()
        assertTrue(initial.hasPlans)
        assertEquals("408 考研", initial.currentPlanTitle)
        assertFalse(initial.hasSession)
        assertFalse(initial.running)
        assertEquals(1L, initial.revision)

        val startWall = 1_760_000_000_000L
        notebook.start(startWall, 1_000L, 7)
        vm.syncFromNotebook(notebook)
        val active = vm.studyState.value
        assertTrue(active.hasSession)
        assertTrue(active.currentHasSession)
        assertTrue(active.running)
        assertEquals(2L, active.revision)

        notebook.pause(startWall + 30 * 60_000L, 1_000L + 30 * 60_000L, 7)
        vm.syncFromNotebook(notebook)
        assertFalse(vm.studyState.value.running)

        notebook.finish(startWall + 30 * 60_000L, 1_000L + 30 * 60_000L, 7, "复习红黑树")
        vm.syncFromNotebook(notebook)
        val finished = vm.studyState.value
        assertFalse(finished.hasSession)
        assertEquals(1, finished.water)
    }

    @Test
    fun syncFromTodos_partitionsOverdueTodayPlannedAndCompleted() {
        val vm = StudySharedViewModel()
        val today = "2026-11-24"
        val pending = listOf(
            TodoStore.TodoItem("1", "逾期事项", "", "2026-11-22", "", false, 30, 1L, 0L),
            TodoStore.TodoItem("2", "今日事项", "", "2026-11-24", "18:00", true, 30, 2L, 0L),
            TodoStore.TodoItem("3", "未来事项", "", "2026-11-26", "", false, 30, 3L, 0L),
            TodoStore.TodoItem("4", "随时可做", "", "", "", false, 30, 4L, 0L)
        )
        val completed = listOf(
            TodoStore.TodoItem("5", "已完成事项", "", "2026-11-24", "", false, 30, 5L, 100L)
        )
        vm.syncFromTodos(pending, completed, remindEnabled = true, defaultRemindTime = "19:30", todayKey = today)

        val state = vm.todoState.value
        assertTrue(state.remindEnabled)
        assertEquals("19:30", state.defaultRemindTime)
        assertEquals(1, state.overdueCount)
        assertEquals(1, state.todayOnlyCount)
        assertEquals(2, state.todayCount)
        assertEquals(2, state.plannedCount)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun requestPlanHistory_consumesOnceAndUpdatesStatsScope() {
        val vm = StudySharedViewModel()
        assertNull(vm.consumePendingHistoryPlan())

        vm.requestPlanHistory("legacy-408")
        assertEquals("legacy-408", vm.statsScopeState.value.planId)
        assertEquals(0, vm.statsScopeState.value.days)
        assertEquals("legacy-408", vm.consumePendingHistoryPlan())
        assertNull(vm.consumePendingHistoryPlan())
    }

    @Test
    fun todoFilterState_tabSelectionClearsDayFilterAndShiftDayMovesCalendar() {
        val vm = StudySharedViewModel()
        vm.updateTodoFilterDay("2026-11-24")
        assertEquals("2026-11-24", vm.todoFilterState.value.filterDay)

        vm.shiftTodoFilterDay(1, "2026-11-24")
        assertEquals("2026-11-25", vm.todoFilterState.value.filterDay)

        vm.updateTodoTab(2)
        assertEquals(2, vm.todoFilterState.value.tabIndex)
        assertNull(vm.todoFilterState.value.filterDay)
    }

    @Test
    fun computeStudyStreak_countsConsecutiveDaysFromTodayOrYesterday() {
        val notebook = StudyNotebook()
        val day1 = StudyFormat.day("2026-11-22").timeInMillis + 3_600_000L
        val day2 = StudyFormat.day("2026-11-23").timeInMillis + 3_600_000L
        val day3 = StudyFormat.day("2026-11-24").timeInMillis + 3_600_000L

        notebook.start(day1, 1_000L, 1)
        notebook.finish(day1 + 25 * 60_000L, 1_000L + 25 * 60_000L, 1, "d1")
        notebook.start(day2, 2_000L, 1)
        notebook.finish(day2 + 25 * 60_000L, 2_000L + 25 * 60_000L, 1, "d2")

        assertEquals(2, StudySharedViewModel.computeStudyStreak(notebook.records(), "2026-11-24"))
        assertEquals(0, StudySharedViewModel.computeStudyStreak(notebook.records(), "2026-11-25"))

        notebook.start(day3, 3_000L, 1)
        notebook.finish(day3 + 25 * 60_000L, 3_000L + 25 * 60_000L, 1, "d3")
        assertEquals(3, StudySharedViewModel.computeStudyStreak(notebook.records(), "2026-11-24"))
    }

    @Test
    fun syncFromNotebook_handlesEmptyPlanStateWithoutThrowing() {
        val vm = StudySharedViewModel()
        val notebook = StudyNotebook()
        for (plan in notebook.plans().toList()) {
            notebook.deletePlan(plan.id)
        }
        vm.syncFromNotebook(notebook)

        val state = vm.studyState.value
        assertFalse(state.hasPlans)
        assertNull(state.currentPlanId)
        assertEquals("", state.currentPlanTitle)
        assertEquals(180, state.dailyGoalMinutes)
        assertEquals(25, state.sessionGoalMinutes)
    }
}
