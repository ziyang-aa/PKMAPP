package com.example.pkmapp.study;

public enum StudySubject {
    ENGLISH("英语", "单词 · 阅读", 0xffb99b65),
    CALCULUS("高等数学", "微积分", 0xff8ca378),
    LINEAR_ALGEBRA("线性代数", "矩阵 · 向量", 0xffba8a70),
    DATA_STRUCTURES("数据结构", "408 / 01", 0xff62826b),
    COMPUTER_ORGANIZATION("计算机组成原理", "408 / 02", 0xff8ba5af),
    OPERATING_SYSTEMS("操作系统", "408 / 03", 0xffa498ad),
    NETWORKS("计算机网络", "408 / 04", 0xffcaab83);

    public final String title;
    public final String hint;
    public final int color;

    StudySubject(String title, String hint, int color) {
        this.title = title;
        this.hint = hint;
        this.color = color;
    }
}
