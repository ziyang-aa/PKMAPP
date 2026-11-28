package com.example.pkmapp.study

import java.util.Locale

/**
 * Pure search matcher for Study records across notes, subjects, plans, and linked tasks.
 */
object StudyRecordSearchFilter {

    @JvmStatic
    fun matches(
        record: StudyNotebook.Record,
        selection: StudySubject?,
        rawQuery: String,
        planTitle: String? = null,
        taskTitle: String? = null
    ): Boolean {
        if (selection != null && record.subject != selection) {
            return false
        }
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return true
        }
        if (record.note.lowercase(Locale.ROOT).contains(query)) return true
        if (record.subject.title.lowercase(Locale.ROOT).contains(query)) return true
        if (!planTitle.isNullOrBlank() && planTitle.lowercase(Locale.ROOT).contains(query)) return true
        if (!taskTitle.isNullOrBlank() && taskTitle.lowercase(Locale.ROOT).contains(query)) return true
        return false
    }
}
