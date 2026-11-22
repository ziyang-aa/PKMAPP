package com.example.pkmapp.study;

import org.json.JSONException;
import org.json.JSONObject;

final class StudyWork {
    private StudyWork() {}

    static final class Task {
        final String id, planId, subjectId, title, day, dueTime;
        final long created, completed, updated, priority;
        final boolean next, remind;
        final int remindLeadMinutes;

        Task(String id, String planId, String subjectId, String title, String day, long created,
             long completed, long updated, boolean next) {
            this(id, planId, subjectId, title, day, created, completed, updated, next, 0, "", false, 30);
        }

        Task(String id, String planId, String subjectId, String title, String day, long created,
             long completed, long updated, boolean next, long priority) {
            this(id, planId, subjectId, title, day, created, completed, updated, next, priority, "", false, 30);
        }

        Task(String id, String planId, String subjectId, String title, String day, long created,
             long completed, long updated, boolean next, long priority,
             String dueTime, boolean remind, int remindLeadMinutes) {
            this.id = StudyPlan.required(id, 120, "任务标识");
            this.planId = StudyPlan.required(planId, 120, "所属计划");
            this.subjectId = StudyPlan.required(subjectId, 120, "所属科目");
            this.title = StudyPlan.required(title, 120, "学习事项");
            this.day = StudyPlan.date(day);
            this.created = created;
            this.completed = completed;
            this.updated = updated;
            this.next = next;
            this.priority = priority;
            this.dueTime = validateTime(dueTime);
            this.remind = remind;
            this.remindLeadMinutes = remindLeadMinutes <= 0 ? 30 : Math.min(remindLeadMinutes, 10080);
        }

        static String validateTime(String value) {
            if (value == null) return "";
            String clean = value.trim().replace('：', ':');
            if (clean.isEmpty()) return "";
            if (!clean.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]")) {
                throw new IllegalArgumentException("时间格式为 HH:mm，例如 18:00");
            }
            String[] parts = clean.split(":");
            return String.format(java.util.Locale.ROOT, "%02d:%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        long deadlineAt() {
            if (day.isEmpty()) return 0;
            java.util.Calendar cal = StudyFormat.day(day);
            if (!dueTime.isEmpty()) {
                String[] parts = dueTime.split(":");
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
            } else {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                cal.set(java.util.Calendar.MINUTE, 59);
            }
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }

        long reminderAt(String defaultRemindTime) {
            if (completed != 0 || !remind || day.isEmpty()) return 0;
            if (!dueTime.isEmpty()) {
                return deadlineAt() - remindLeadMinutes * 60_000L;
            }
            java.util.Calendar cal = StudyFormat.day(day);
            if (remindLeadMinutes >= 1440) {
                cal.add(java.util.Calendar.DATE, -1);
            }
            String hhmm = (defaultRemindTime == null || defaultRemindTime.isEmpty()) ? "18:00" : defaultRemindTime;
            String[] parts = hhmm.split(":");
            cal.set(java.util.Calendar.HOUR_OF_DAY, parts.length == 2 ? Integer.parseInt(parts[0]) : 18);
            cal.set(java.util.Calendar.MINUTE, parts.length == 2 ? Integer.parseInt(parts[1]) : 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }

        String leadLabel() {
            if (remindLeadMinutes >= 1440) return "前一天傍晚";
            if (remindLeadMinutes >= 60 && remindLeadMinutes % 60 == 0)
                return "提前" + (remindLeadMinutes / 60) + "小时";
            return "提前" + remindLeadMinutes + "分钟";
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject().put("id", id).put("plan", planId).put("subject", subjectId)
                .put("title", title).put("day", day).put("created", created)
                .put("completed", completed).put("updated", updated).put("next", next).put("priority", priority);
            if (!dueTime.isEmpty()) obj.put("dueTime", dueTime);
            if (remind) obj.put("remind", true);
            if (remindLeadMinutes != 30) obj.put("remindLead", remindLeadMinutes);
            return obj;
        }

        static Task fromJson(JSONObject json) throws JSONException {
            return new Task(json.getString("id"), json.getString("plan"), json.getString("subject"),
                json.getString("title"), json.getString("day"), json.getLong("created"),
                json.getLong("completed"), json.getLong("updated"), json.getBoolean("next"),
                json.optLong("priority"), json.optString("dueTime", ""),
                json.optBoolean("remind", false), json.optInt("remindLead", 30));
        }
    }

    static final class Review {
        final String recordId, prompt, day;
        final long completed, updated;

        Review(String recordId, String prompt, String day, long completed, long updated) {
            this.recordId = StudyPlan.required(recordId, 120, "原记录");
            this.prompt = StudyPlan.required(prompt, 120, "回顾问题");
            this.day = StudyPlan.date(day);
            if (this.day.isEmpty()) throw new IllegalArgumentException("请选择回顾日期");
            this.completed = completed;
            this.updated = updated;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("record", recordId).put("prompt", prompt).put("day", day)
                .put("completed", completed).put("updated", updated);
        }

        static Review fromJson(JSONObject json) throws JSONException {
            return new Review(json.getString("record"), json.getString("prompt"), json.getString("day"),
                json.getLong("completed"), json.getLong("updated"));
        }
    }
}
