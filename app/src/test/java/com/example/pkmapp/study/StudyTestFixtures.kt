package com.example.pkmapp.study

internal object StudyTestFixtures {
    fun buildRichNotebookJson(): String {
        val notebook = StudyNotebook()
        val now = 1_763_900_000_000L

        // Complete a focus session on round 1 ("408 考研") and schedule a review + task
        notebook.start(now, 1_000L, 5)
        val completion = notebook.finish(
            now + 50 * 60_000L,
            1_000L + 50 * 60_000L,
            5,
            "红黑树旋转推导"
        )
        notebook.changeReview(completion.record.id, "手写红黑树删除修复", "2026-11-28", false, now + 60_000L)
        val carryTask = notebook.putTask(
            StudyPlan.LEGACY_ID,
            StudySubject.ENGLISH.id,
            "精读真题阅读 Text 2",
            "2026-11-25",
            "19:30",
            true,
            60,
            now + 70_000L,
            true
        )

        // Archive round 1 and open round 2 ("408 二轮") carrying the pending task
        val round2 = notebook.nextRound(
            StudyPlan.LEGACY_ID,
            "408 二轮",
            now + 100_000L,
            true,
            setOf(carryTask.id)
        )
        notebook.switchPlan(round2.id)
        notebook.select(StudySubject.OPERATING_SYSTEMS)

        // Plant seed, water once, and start an active running session on round 2
        notebook.plant()
        notebook.waterTree()
        notebook.start(now + 200_000L, 500_000L, 5)
        return notebook.toJson()
    }
}
