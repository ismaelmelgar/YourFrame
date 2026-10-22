package io.yourframe.tv.ui;

import io.yourframe.tv.R;
import io.yourframe.tv.domain.Category;
import io.yourframe.tv.domain.Frame;
import io.yourframe.tv.domain.Mat;
import io.yourframe.tv.domain.Night;

/** The words the viewer sees for each domain value (English and Spanish in res/values*). */
public final class Names {
    private Names() {}

    private static final int[] CATEGORIES = {R.string.cat_all, R.string.cat_eu, R.string.cat_am, R.string.cat_as,
            R.string.cat_pr, R.string.cat_is, R.string.cat_ph};
    private static final int[] FRAMES = {R.string.frame_bleed, R.string.frame_none, R.string.frame_black,
            R.string.frame_white, R.string.frame_oak, R.string.frame_walnut, R.string.frame_gold};
    private static final int[] MATS = {R.string.mat_paper, R.string.mat_ivory, R.string.mat_stone, R.string.mat_sage,
            R.string.mat_navy, R.string.mat_black};
    private static final int[] NIGHTS = {R.string.night_off, R.string.night_22, R.string.night_23, R.string.night_always};

    public static int category(Category c) { return CATEGORIES[c.ordinal()]; }
    public static int frame(Frame f) { return FRAMES[f.ordinal()]; }
    public static int mat(Mat m) { return MATS[m.ordinal()]; }
    public static int night(Night n) { return NIGHTS[n.ordinal()]; }
}
