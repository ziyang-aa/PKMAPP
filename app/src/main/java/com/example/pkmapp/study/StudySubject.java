package com.example.pkmapp.study;

import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

public final class StudySubject {
    public static final StudySubject ENGLISH = new StudySubject("ENGLISH", "英语", "单词 · 阅读", 0xffb99b65);
    public static final StudySubject CALCULUS = new StudySubject("CALCULUS", "高等数学", "微积分", 0xff8ca378);
    public static final StudySubject LINEAR_ALGEBRA = new StudySubject("LINEAR_ALGEBRA", "线性代数", "矩阵 · 向量", 0xffba8a70);
    public static final StudySubject DATA_STRUCTURES = new StudySubject("DATA_STRUCTURES", "数据结构", "408 / 01", 0xff62826b);
    public static final StudySubject COMPUTER_ORGANIZATION = new StudySubject("COMPUTER_ORGANIZATION", "计算机组成原理", "408 / 02", 0xff8ba5af);
    public static final StudySubject OPERATING_SYSTEMS = new StudySubject("OPERATING_SYSTEMS", "操作系统", "408 / 03", 0xffa498ad);
    public static final StudySubject NETWORKS = new StudySubject("NETWORKS", "计算机网络", "408 / 04", 0xffcaab83);

    public final String id;
    public final String title;
    public final String hint;
    public final int color;
    public final String due;
    public final String assessment;

    private StudySubject(String id, String title, String hint, int color) {
        this(id, title, hint, color, "", "");
    }

    private StudySubject(String id, String title, String hint, int color, String due, String assessment) {
        this.id = StudyPlan.required(id, 120, "科目标识");
        this.title = StudyPlan.required(title, 40, "科目名称");
        this.hint = StudyPlan.optional(hint, 40, "科目代码");
        this.color = color | 0xff000000;
        this.due = StudyPlan.date(due);
        this.assessment = StudyPlan.optional(assessment, 40, "考核方式");
    }

    static StudySubject create(String title, String code, int color) {
        return new StudySubject(UUID.randomUUID().toString(), title, code, color);
    }

    StudySubject edit(String title, String code, String due, String assessment) {
        return new StudySubject(id, title, code, color, due, assessment);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("hint", hint)
            .put("color", color).put("due", due).put("assessment", assessment);
    }

    static StudySubject fromJson(JSONObject json) throws JSONException {
        return new StudySubject(json.getString("id"), json.getString("title"), json.getString("hint"),
            json.getInt("color"), json.getString("due"), json.getString("assessment"));
    }

    public static StudySubject[] values() {
        return new StudySubject[] {
            ENGLISH, CALCULUS, LINEAR_ALGEBRA, DATA_STRUCTURES,
            COMPUTER_ORGANIZATION, OPERATING_SYSTEMS, NETWORKS
        };
    }

    static StudySubject legacy(String id) {
        for (StudySubject subject : values()) if (subject.id.equals(id)) return subject;
        throw new IllegalArgumentException("无法识别旧版科目");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StudySubject && id.equals(((StudySubject) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
