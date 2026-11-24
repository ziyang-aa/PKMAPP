package com.example.pkmapp.study

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pkmapp.todo.TodoStore
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class StudyUiState(
    val hasPlans: Boolean = true,
    val currentPlanId: String? = null,
    val currentPlanTitle: String = "",
    val partner: StudyPlan.Partner = StudyPlan.Partner.SQUIRREL,
    val isArchived: Boolean = false,
    val hasSession: Boolean = false,
    val currentHasSession: Boolean = false,
    val running: Boolean = false,
    val sessionId: String = "",
    val water: Int = 0,
    val seeds: Int = 0,
    val planted: Boolean = false,
    val growth: Int = 0,
    val trees: Int = 0,
    val dailyGoalMinutes: Int = 180,
    val sessionGoalMinutes: Int = 25,
    val revision: Long = 0L
) {
    companion object {
        fun from(notebook: StudyNotebook, revision: Long = 0L): StudyUiState {
            val plan = notebook.currentPlan()
            return StudyUiState(
                hasPlans = plan != null,
                currentPlanId = plan?.id,
                currentPlanTitle = plan?.title.orEmpty(),
                partner = plan?.partner ?: StudyPlan.Partner.SQUIRREL,
                isArchived = plan != null && plan.archived != 0L,
                hasSession = notebook.hasSession(),
                currentHasSession = notebook.currentHasSession(),
                running = notebook.running(),
                sessionId = notebook.sessionId(),
                water = notebook.water(),
                seeds = notebook.seeds(),
                planted = notebook.planted(),
                growth = notebook.growth(),
                trees = notebook.trees(),
                dailyGoalMinutes = plan?.dailyGoal ?: 180,
                sessionGoalMinutes = plan?.sessionGoal ?: 25,
                revision = revision
            )
        }
    }
}

data class TodoUiState(
    val remindEnabled: Boolean = true,
    val defaultRemindTime: String = "18:00",
    val overdueCount: Int = 0,
    val todayOnlyCount: Int = 0,
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val revision: Long = 0L
) {
    val todayCount: Int
        get() = overdueCount + todayOnlyCount

    val pendingCount: Int
        get() = overdueCount + todayOnlyCount + plannedCount

    companion object {
        fun from(
            pending: List<TodoStore.TodoItem>,
            completed: List<TodoStore.TodoItem>,
            remindEnabled: Boolean,
            defaultRemindTime: String,
            todayKey: String,
            revision: Long = 0L
        ): TodoUiState {
            var overdue = 0
            var today = 0
            var planned = 0
            for (item in pending) {
                when {
                    item.day.isNotEmpty() && item.day < todayKey -> overdue++
                    item.day == todayKey -> today++
                    else -> planned++
                }
            }
            return TodoUiState(
                remindEnabled = remindEnabled,
                defaultRemindTime = defaultRemindTime,
                overdueCount = overdue,
                todayOnlyCount = today,
                plannedCount = planned,
                completedCount = completed.size,
                revision = revision
            )
        }
    }
}

data class TodoFilterState(
    val tabIndex: Int = 0,
    val filterDay: String? = null
)

data class StatsScopeState(
    val planId: String? = null,
    val days: Int = 7,
    val chart: StudyChartView.Kind = StudyChartView.Kind.RING
)

class StudySharedViewModel : ViewModel() {

    private var studyRevision = 0L
    private var todoRevision = 0L
    private var pendingHistoryPlanId: String? = null

    private val _studyState = MutableStateFlow(StudyUiState())
    internal val studyState: StateFlow<StudyUiState> = _studyState.asStateFlow()

    private val _todoState = MutableStateFlow(TodoUiState())
    val todoState: StateFlow<TodoUiState> = _todoState.asStateFlow()

    private val _todoFilterState = MutableStateFlow(TodoFilterState())
    val todoFilterState: StateFlow<TodoFilterState> = _todoFilterState.asStateFlow()

    private val _statsScopeState = MutableStateFlow(StatsScopeState())
    val statsScopeState: StateFlow<StatsScopeState> = _statsScopeState.asStateFlow()

    fun syncFromNotebook(notebook: StudyNotebook) {
        studyRevision++
        _studyState.value = StudyUiState.from(notebook, studyRevision)
    }

    fun syncFromTodos(
        pending: List<TodoStore.TodoItem>,
        completed: List<TodoStore.TodoItem>,
        remindEnabled: Boolean,
        defaultRemindTime: String,
        todayKey: String = TodoStore.dayKey(System.currentTimeMillis())
    ) {
        todoRevision++
        _todoState.value = TodoUiState.from(
            pending = pending,
            completed = completed,
            remindEnabled = remindEnabled,
            defaultRemindTime = defaultRemindTime,
            todayKey = todayKey,
            revision = todoRevision
        )
    }

    fun syncFromStores(context: Context) {
        val appContext = context.applicationContext
        val studyStore = StudyStore.get(appContext)
        val todoStore = TodoStore.get(appContext)
        syncFromNotebook(studyStore.notebook())
        syncFromTodos(
            pending = todoStore.pending(),
            completed = todoStore.completed(),
            remindEnabled = todoStore.remindEnabled(),
            defaultRemindTime = todoStore.defaultRemindTime()
        )
    }

    fun requestPlanHistory(planId: String) {
        pendingHistoryPlanId = planId
        _statsScopeState.update { it.copy(planId = planId, days = 0) }
    }

    fun consumePendingHistoryPlan(): String? {
        val pending = pendingHistoryPlanId
        pendingHistoryPlanId = null
        return pending
    }

    fun updateTodoTab(tabIndex: Int) {
        _todoFilterState.value = TodoFilterState(tabIndex = tabIndex.coerceIn(0, 2), filterDay = null)
    }

    fun updateTodoFilterDay(dayKey: String?) {
        val normalized = dayKey?.takeIf { it.isNotEmpty() }
        _todoFilterState.update { it.copy(filterDay = normalized) }
    }

    fun shiftTodoFilterDay(deltaDays: Int, fallbackTodayKey: String = TodoStore.dayKey(System.currentTimeMillis())) {
        val base = _todoFilterState.value.filterDay?.takeIf { it.isNotEmpty() } ?: fallbackTodayKey
        val cal = TodoStore.parseDay(base)
        cal.add(Calendar.DATE, deltaDays)
        val shifted = TodoStore.dayKey(cal.timeInMillis)
        _todoFilterState.update { it.copy(filterDay = shifted) }
    }

    fun updateStatsScope(planId: String?, days: Int) {
        _statsScopeState.update { it.copy(planId = planId, days = days) }
    }

    fun updateStatsChart(chart: StudyChartView.Kind) {
        _statsScopeState.update { it.copy(chart = chart) }
    }

    fun observeOverview(owner: LifecycleOwner, onChanged: Runnable) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(studyState, todoState) { study, todo ->
                    study.revision to todo.revision
                }.collect {
                    onChanged.run()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun get(activity: FragmentActivity): StudySharedViewModel =
            ViewModelProvider(activity)[StudySharedViewModel::class.java]

        fun computeStudyStreak(
            records: List<StudyNotebook.Record>?,
            todayKey: String = StudyNotebook.dayKey(System.currentTimeMillis())
        ): Int {
            if (records.isNullOrEmpty()) return 0
            val activeDays = HashSet<String>(records.size)
            for (record in records) {
                activeDays.add(StudyNotebook.dayKey(record.completedAt))
            }
            val cursor = StudyFormat.day(todayKey)
            if (!activeDays.contains(StudyNotebook.dayKey(cursor.timeInMillis))) {
                cursor.add(Calendar.DATE, -1)
                if (!activeDays.contains(StudyNotebook.dayKey(cursor.timeInMillis))) return 0
            }
            var streak = 0
            while (activeDays.contains(StudyNotebook.dayKey(cursor.timeInMillis))) {
                streak++
                cursor.add(Calendar.DATE, -1)
            }
            return streak
        }
    }
}
