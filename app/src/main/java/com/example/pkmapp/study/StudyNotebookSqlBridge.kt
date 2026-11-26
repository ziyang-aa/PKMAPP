package com.example.pkmapp.study

import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.StudyDatabaseSnapshot
import com.example.pkmapp.core.database.StudyGardenStateEntity
import com.example.pkmapp.core.database.StudyPlanEntity
import com.example.pkmapp.core.database.StudyRecordEntity
import com.example.pkmapp.core.database.StudyReviewEntity
import com.example.pkmapp.core.database.StudySubjectEntity
import com.example.pkmapp.core.database.StudyTaskEntity
import org.json.JSONArray
import org.json.JSONObject

object StudyNotebookSqlBridge {

    @JvmStatic
    @JvmOverloads
    fun toSnapshot(
        notebook: StudyNotebook,
        ownerId: String = AccountScope.GUEST_OWNER_ID
    ): StudyDatabaseSnapshot {
        val root = JSONObject(notebook.toJson())
        val currentPlanId = root.optString("current", "")

        val plans = ArrayList<StudyPlanEntity>()
        val subjects = ArrayList<StudySubjectEntity>()
        val plansJson = root.optJSONArray("plans") ?: JSONArray()
        for (i in 0 until plansJson.length()) {
            val p = plansJson.getJSONObject(i)
            val planId = p.getString("id")
            plans.add(
                StudyPlanEntity(
                    id = planId,
                    title = p.getString("title"),
                    kind = p.getString("kind"),
                    due = p.optString("due", ""),
                    partner = p.optString("partner", "SQUIRREL"),
                    previousPlanId = p.optString("previous", ""),
                    createdAt = p.getLong("created"),
                    archivedAt = p.getLong("archived"),
                    selectedSubjectId = p.getString("selected"),
                    sessionGoal = p.getInt("sessionGoal"),
                    dailyGoal = p.getInt("dailyGoal"),
                    remindDeadlines = p.optBoolean("remindDeadlines", false),
                    remindTime = p.optString("remindTime", "18:00").ifEmpty { "18:00" },
                    isCurrent = planId == currentPlanId,
                    sortOrder = i
                )
            )
            val booksJson = p.optJSONArray("books") ?: JSONArray()
            for (j in 0 until booksJson.length()) {
                val b = booksJson.getJSONObject(j)
                subjects.add(
                    StudySubjectEntity(
                        id = b.getString("id"),
                        planId = planId,
                        title = b.getString("title"),
                        hint = b.optString("hint", ""),
                        due = b.optString("due", ""),
                        assessment = b.optString("assessment", ""),
                        color = b.getInt("color"),
                        sortOrder = j
                    )
                )
            }
        }

        val records = ArrayList<StudyRecordEntity>()
        val recordsJson = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until recordsJson.length()) {
            val r = recordsJson.getJSONObject(i)
            val sub = r.getJSONObject("subject")
            val duration = r.getLong("duration")
            records.add(
                StudyRecordEntity(
                    id = r.getString("id"),
                    planId = r.getString("plan"),
                    subjectId = sub.getString("id"),
                    subjectTitleSnapshot = sub.getString("title"),
                    subjectHintSnapshot = sub.optString("hint", ""),
                    subjectColorSnapshot = sub.getInt("color"),
                    subjectDueSnapshot = sub.optString("due", ""),
                    subjectAssessmentSnapshot = sub.optString("assessment", ""),
                    durationMillis = duration,
                    completedAt = r.getLong("completedAt"),
                    note = r.optString("note", ""),
                    manual = r.optBoolean("manual", false),
                    rewardMillis = r.optLong("rewardMillis", duration),
                    editedAt = r.optLong("editedAt", 0L),
                    adjusted = r.optBoolean("adjusted", false),
                    taskId = r.optString("task", ""),
                    sortOrder = i
                )
            )
        }

        val tasks = ArrayList<StudyTaskEntity>()
        val tasksJson = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until tasksJson.length()) {
            val t = tasksJson.getJSONObject(i)
            tasks.add(
                StudyTaskEntity(
                    id = t.getString("id"),
                    planId = t.getString("plan"),
                    subjectId = t.getString("subject"),
                    title = t.getString("title"),
                    due = t.optString("day", ""),
                    dueTime = t.optString("dueTime", ""),
                    remind = t.optBoolean("remind", false),
                    remindLeadMinutes = t.optInt("remindLead", 30),
                    createdAt = t.getLong("created"),
                    doneAt = t.getLong("completed"),
                    updatedAt = t.getLong("updated"),
                    isNext = t.optBoolean("next", false),
                    priority = t.optLong("priority", 0L)
                )
            )
        }

        val reviews = ArrayList<StudyReviewEntity>()
        val reviewsJson = root.optJSONArray("reviews") ?: JSONArray()
        for (i in 0 until reviewsJson.length()) {
            val rv = reviewsJson.getJSONObject(i)
            reviews.add(
                StudyReviewEntity(
                    recordId = rv.getString("record"),
                    prompt = rv.getString("prompt"),
                    day = rv.getString("day"),
                    completedAt = rv.getLong("completed"),
                    updatedAt = rv.getLong("updated")
                )
            )
        }

        val active = root.optJSONObject("active")
        val rewardedDays = (root.optJSONArray("rewardedDays") ?: JSONArray()).toString()
        val garden = StudyGardenStateEntity(
            ownerId = ownerId,
            currentPlanId = currentPlanId,
            water = root.getInt("water"),
            carryMillis = root.optLong("waterProgress", 0L),
            seeds = root.getInt("seeds"),
            planted = root.getBoolean("planted"),
            growth = root.getInt("growth"),
            trees = root.getInt("trees"),
            rewardedDaysJson = rewardedDays,
            activeSessionId = active?.optString("id"),
            activePlanId = active?.optString("plan"),
            activeSubjectJson = active?.optJSONObject("subject")?.toString(),
            activeGoal = active?.optInt("goal", 25) ?: 25,
            activeTaskId = active?.optString("task", "") ?: "",
            activeAccumulated = active?.optLong("accumulated", 0L) ?: 0L,
            activeWall = active?.optLong("wall", 0L) ?: 0L,
            activeElapsed = active?.optLong("elapsed", 0L) ?: 0L,
            activeBoot = active?.optInt("boot", -1) ?: -1,
            activeRunning = active?.optBoolean("running", false) ?: false
        )

        return StudyDatabaseSnapshot(
            gardenState = garden,
            plans = plans,
            subjects = subjects,
            records = records,
            tasks = tasks,
            reviews = reviews
        )
    }

    @JvmStatic
    fun fromSnapshot(snapshot: StudyDatabaseSnapshot): StudyNotebook? {
        val garden = snapshot.gardenState ?: return null
        val subjectsByPlan = snapshot.subjects
            .sortedBy { it.sortOrder }
            .groupBy { it.planId }

        val plansArr = JSONArray()
        for (plan in snapshot.plans.sortedBy { it.sortOrder }) {
            val shelfArr = JSONArray()
            for (sub in subjectsByPlan[plan.id].orEmpty()) {
                shelfArr.put(
                    JSONObject()
                        .put("id", sub.id)
                        .put("title", sub.title)
                        .put("hint", sub.hint)
                        .put("color", sub.color)
                        .put("due", sub.due)
                        .put("assessment", sub.assessment)
                )
            }
            val planObj = JSONObject()
                .put("id", plan.id)
                .put("title", plan.title)
                .put("kind", plan.kind)
                .put("due", plan.due)
                .put("partner", plan.partner)
                .put("previous", plan.previousPlanId)
                .put("created", plan.createdAt)
                .put("archived", plan.archivedAt)
                .put("selected", plan.selectedSubjectId)
                .put("sessionGoal", plan.sessionGoal)
                .put("dailyGoal", plan.dailyGoal)
                .put("books", shelfArr)
                .put("remindDeadlines", plan.remindDeadlines)
            if (plan.remindTime.isNotEmpty() && plan.remindTime != "18:00") {
                planObj.put("remindTime", plan.remindTime)
            }
            plansArr.put(planObj)
        }

        val recordsArr = JSONArray()
        for (rec in snapshot.records.sortedBy { it.sortOrder }) {
            val subObj = JSONObject()
                .put("id", rec.subjectId)
                .put("title", rec.subjectTitleSnapshot)
                .put("hint", rec.subjectHintSnapshot)
                .put("color", rec.subjectColorSnapshot)
                .put("due", rec.subjectDueSnapshot)
                .put("assessment", rec.subjectAssessmentSnapshot)
            recordsArr.put(
                JSONObject()
                    .put("id", rec.id)
                    .put("plan", rec.planId)
                    .put("subject", subObj)
                    .put("duration", rec.durationMillis)
                    .put("completedAt", rec.completedAt)
                    .put("note", rec.note)
                    .put("manual", rec.manual)
                    .put("rewardMillis", rec.rewardMillis)
                    .put("editedAt", rec.editedAt)
                    .put("adjusted", rec.adjusted)
                    .put("task", rec.taskId)
            )
        }

        val tasksArr = JSONArray()
        for (task in snapshot.tasks) {
            val taskObj = JSONObject()
                .put("id", task.id)
                .put("plan", task.planId)
                .put("subject", task.subjectId)
                .put("title", task.title)
                .put("day", task.due)
                .put("created", task.createdAt)
                .put("completed", task.doneAt)
                .put("updated", task.updatedAt)
                .put("next", task.isNext)
                .put("priority", task.priority)
            if (task.dueTime.isNotEmpty()) taskObj.put("dueTime", task.dueTime)
            if (task.remind) taskObj.put("remind", true)
            if (task.remindLeadMinutes != 30) taskObj.put("remindLead", task.remindLeadMinutes)
            tasksArr.put(taskObj)
        }

        val reviewsArr = JSONArray()
        for (rv in snapshot.reviews) {
            reviewsArr.put(
                JSONObject()
                    .put("record", rv.recordId)
                    .put("prompt", rv.prompt)
                    .put("day", rv.day)
                    .put("completed", rv.completedAt)
                    .put("updated", rv.updatedAt)
            )
        }

        val root = JSONObject()
            .put("version", StudyNotebook.VERSION)
            .put("current", garden.currentPlanId)
            .put("water", garden.water)
            .put("waterProgress", garden.carryMillis)
            .put("seeds", garden.seeds)
            .put("planted", garden.planted)
            .put("growth", garden.growth)
            .put("trees", garden.trees)
            .put("plans", plansArr)
            .put("records", recordsArr)
            .put("rewardedDays", JSONArray(garden.rewardedDaysJson.ifEmpty { "[]" }))
            .put("tasks", tasksArr)
            .put("reviews", reviewsArr)

        if (!garden.activeSessionId.isNullOrEmpty() &&
            !garden.activePlanId.isNullOrEmpty() &&
            !garden.activeSubjectJson.isNullOrEmpty()
        ) {
            root.put(
                "active",
                JSONObject()
                    .put("id", garden.activeSessionId)
                    .put("plan", garden.activePlanId)
                    .put("subject", JSONObject(garden.activeSubjectJson))
                    .put("goal", garden.activeGoal)
                    .put("accumulated", garden.activeAccumulated)
                    .put("running", garden.activeRunning)
                    .put("wall", garden.activeWall)
                    .put("elapsed", garden.activeElapsed)
                    .put("boot", garden.activeBoot)
                    .put("task", garden.activeTaskId)
            )
        }

        return StudyNotebook.fromJson(root.toString())
    }
}
