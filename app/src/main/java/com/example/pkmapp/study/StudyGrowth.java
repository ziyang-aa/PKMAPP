package com.example.pkmapp.study;

/** Watering milestones shared by storage, the tree animation and the progress trail. */
public final class StudyGrowth {

    public static final int REQUIRED_WATER = 100;
    public static final int STAGE_COUNT = 6;
    private static final int[] MILESTONES = { 0, 15, 35, 60, 85, REQUIRED_WATER };
    private static final String[] NAMES = {
        "刚安家的种子",
        "探出头的小芽",
        "舒展叶片的树苗",
        "向着阳光的小树",
        "快长大的小树",
        "枝繁叶茂的伙伴",
    };

    private StudyGrowth() {}

    public static int milestone(int stage) {
        return MILESTONES[stage];
    }

    public static int stage(int growth) {
        int stage = 0;
        while (stage < STAGE_COUNT - 1 && growth >= MILESTONES[stage + 1]) stage++;
        return stage;
    }

    public static String name(int growth) {
        return NAMES[stage(growth)];
    }
}
