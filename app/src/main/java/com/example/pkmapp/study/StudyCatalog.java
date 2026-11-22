package com.example.pkmapp.study;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Referenced admissions examples and course pools. Books are copied into each plan. */
final class StudyCatalog {
    enum Track {
        HUMANITIES("文科", "语言、历史、法学和经管"),
        SCIENCE("理科", "数学、物理、工科和医学");

        final String title;
        final String description;

        Track(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static final int[] COLORS = {
        0xffb99b65, 0xff8ca378, 0xffba8a70, 0xff62826b, 0xff8ba5af, 0xffa498ad, 0xffcaab83
    };
    private static final String UCAS = "https://admission.ucas.edu.cn/ShowArticle/Article/4c7e0e9f-2311-47a0-8f12-b0ec992078ac/7d50f7f5-0125-4410-93a3-2b2b6917f0dd";
    private static final String SJTU = "https://yzb.sjtu.edu.cn/post/3772";
    private static final String NAU = "https://gs.nau.edu.cn/2026/0920/c4397a161925/page.htm";
    private static final String CDU = "https://yjsc.cdu.edu.cn/info/1037/8308.htm";
    private static final String CUFE = "https://soa.cufe.edu.cn/info/1040/8501.htm";
    private static final String POLITICS = "101|思想政治理论";
    private static final String ENGLISH_1 = "201|英语一";
    private static final String ENGLISH_2 = "204|英语二";
    private static final String MATH_1 = "301|数学一";
    private static final String MATH_2 = "302|数学二";
    private static final String[] COMPUTER = {
        "408|数据结构", "408|计算机组成原理", "408|操作系统", "408|计算机网络"
    };

    static final List<Group> GROUPS = Collections.unmodifiableList(Arrays.asList(
        graduate("软件工程学硕", "国科大 · 0835", UCAS, with408(ENGLISH_1, MATH_1)),
        graduate("计算机技术专硕", "国科大 · 085404", UCAS, with408(ENGLISH_2, MATH_2)),
        graduate("通信工程", "国科大 · 085402", UCAS, POLITICS, ENGLISH_2, MATH_2, "859|信号与系统"),
        graduate("控制工程", "国科大 · 085406", UCAS, POLITICS, ENGLISH_2, MATH_2, "857|自动控制理论"),
        graduate("机械工程", "上海交通大学 · 080200 · 机械工程方向", SJTU, POLITICS, ENGLISH_1, MATH_1, "813|机械设计原理与基础"),
        graduate("金融专硕", "南京审计大学 · 025100", NAU, POLITICS, ENGLISH_2, "396|经济类综合能力", "431|金融学综合"),
        graduate("应用统计", "南京审计大学 · 025200", NAU, POLITICS, ENGLISH_2, "303|数学三", "432|统计学"),
        graduate("法律硕士（法学）", "上海交通大学 · 035102", SJTU, POLITICS, ENGLISH_1, "397|法律硕士专业基础（法学）", "497|法律硕士综合（法学）"),
        graduate("法律硕士（非法学）", "上海交通大学 · 035101", SJTU, POLITICS, ENGLISH_1, "398|法律硕士专业基础（非法学）", "498|法律硕士综合（非法学）"),
        graduate("教育专硕", "成都大学 · 0451", "https://sfxy.cdu.edu.cn/info/1021/14021.htm", POLITICS, ENGLISH_2, "333|教育综合", "教育研究方法"),
        graduate("临床医学专硕", "成都大学 · 1051", CDU, POLITICS, ENGLISH_1, "306|临床医学综合能力（西医）"),
        graduate("会计专硕", "中央财经大学 · 125300", CUFE, "199|管理类综合能力", ENGLISH_2),
        graduate("审计专硕", "中央财经大学 · 125700", CUFE, "199|管理类综合能力", ENGLISH_2),
        graduate("英语笔译", "成都大学 · 055101", CDU, POLITICS, "211|翻译硕士（英语）", "357|翻译基础（英语）", "448|汉语写作与百科知识"),
        new Group(StudyPlan.Kind.GRADUATE, null, "数学分科与备选语种", "复习拆分 · 按院校和试卷勾选",
            "https://yz.chsi.com.cn/kyzx/jybzc/202609/20260924/2293971511.html",
            "高等数学", "线性代数", "概率论与数理统计", "202|俄语", "203|日语"),
        finals(Track.HUMANITIES, "汉语言文学", "https://chinese.sysu.edu.cn/article/1927",
            "文学概论", "中国古代文学史", "二十世纪中国文学史", "现代汉语", "古代汉语", "外国文学史", "比较文学概论"),
        finals(Track.HUMANITIES, "英语", "https://zhujiang.tjufe.edu.cn/rwxy/2026/0417/c498a30103/page.htm",
            "综合英语", "英语视听说", "英语写作", "语言学导论", "英语文学导论", "跨文化交际"),
        finals(Track.HUMANITIES, "历史学",
            "https://wenxxy.lsnu.edu.cn/__local/B/75/6E/515D72390F809AEFB1EF6C953EF_D74DFB4D_575BD.pdf",
            "中国古代史", "中国近代史", "世界古代史", "世界近代史", "世界现代史", "史学概论"),
        finals(Track.HUMANITIES, "法学", "https://law.gdufe.edu.cn/2026/0402/c8287a236910/page.htm",
            "法理学", "刑法学", "民法学", "行政法与行政诉讼法学", "民事诉讼法学"),
        finals(Track.HUMANITIES, "会计学", "https://jwc.ncu.edu.cn/jingjiguanlixueyuan2024banbenkezhuanyepeiyangfangan.pdf",
            "微观经济学", "宏观经济学", "财务管理", "中级财务会计", "统计学", "审计学"),
        finals(Track.SCIENCE, "计算机科学与技术",
            "https://www.sjzc.edu.cn/wlxxjsxy/columns/e2b56ed7-e5d6-4d80-866b-ed48b912b75a/202610/29/7defd600-9a9a-481e-b2d4-fba13bbb6853.html",
            "数据结构", "数据库原理", "计算机组成原理", "操作系统", "计算机网络"),
        finals(Track.SCIENCE, "通信工程",
            "https://ei.hust.edu.cn/__local/F/E8/A2/75D297EABF8A4016C4BF612B544_4FBB88E8_D1E5B.pdf?e=.pdf",
            "信号与线性系统", "数字电路与逻辑设计", "通信电子线路", "通信原理", "数字信号处理"),
        finals(Track.SCIENCE, "机械设计制造及其自动化", "https://jw.yyu.edu.cn/p48/pyfa/20250606/15730.html",
            "机械制图", "理论力学", "材料力学", "机械原理", "机械设计"),
        finals(Track.SCIENCE, "土木工程", "https://civil.cqust.edu.cn/info/1057/1546.htm",
            "材料力学", "结构力学", "混凝土结构原理", "土力学与基础工程", "钢结构原理"),
        finals(Track.SCIENCE, "数学与应用数学", "https://math.lcu.edu.cn/docs/20250310174600380732.pdf",
            "数学分析", "高等代数", "常微分方程", "概率论与数理统计", "复变函数论", "数值分析"),
        finals(Track.SCIENCE, "物理学", "https://www.csust.edu.cn/wdxy/info/1497/11521.htm",
            "数学物理方法", "力学", "热学", "理论力学", "电动力学", "量子力学"),
        finals(Track.SCIENCE, "临床医学", "https://medicine.jhun.edu.cn/2024/0906/c8170a202580/page.psp",
            "系统解剖学", "生理学", "病理学", "药理学", "诊断学", "内科学", "外科学")
    ));

    static StudySubject book(String title, String code, int position) {
        return StudySubject.create(title, code, COLORS[position % COLORS.length]);
    }

    private static String[] with408(String english, String math) {
        String[] books = { POLITICS, english, math, COMPUTER[0], COMPUTER[1], COMPUTER[2], COMPUTER[3] };
        return books;
    }

    private static Group graduate(String name, String school, String source, String... entries) {
        return new Group(StudyPlan.Kind.GRADUATE, null, name, "2027 初试 · " + school, source, entries);
    }

    private static Group finals(Track track, String name, String source, String... entries) {
        return new Group(StudyPlan.Kind.FINALS, track, name, track.title, source, entries);
    }

    static final class Group {
        final StudyPlan.Kind kind;
        final Track track;
        final String title;
        final String detail;
        final String source;
        final List<String> entries;

        Group(StudyPlan.Kind kind, Track track, String title, String detail, String source, String... entries) {
            this.kind = kind;
            this.track = track;
            this.title = title;
            this.detail = detail;
            this.source = source;
            this.entries = Collections.unmodifiableList(Arrays.asList(entries));
        }
    }
}
