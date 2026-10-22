package io.yourframe.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** The settings, shared by the settings screen and the screensaver (same process, same file). */
final class Prefs {
    private Prefs() {}

    static final String FILE = "settings";

    static final String[] CATEGORIES = {"all", "eu", "am", "as", "pr", "is", "ph"};
    static final int[] CATEGORY_NAMES = {R.string.cat_all, R.string.cat_eu, R.string.cat_am, R.string.cat_as,
            R.string.cat_pr, R.string.cat_is, R.string.cat_ph};
    static final String[] FRAMES = {"bleed", "none", "black", "white", "oak", "walnut", "gold"};
    static final int[] FRAME_NAMES = {R.string.frame_bleed, R.string.frame_none, R.string.frame_black,
            R.string.frame_white, R.string.frame_oak, R.string.frame_walnut, R.string.frame_gold};
    static final String[] MATS = {"paper", "ivory", "stone", "sage", "navy", "black"};
    static final int[] MAT_NAMES = {R.string.mat_paper, R.string.mat_ivory, R.string.mat_stone, R.string.mat_sage,
            R.string.mat_navy, R.string.mat_black};
    /** Mat colour and the ink printed on it (from the prototype). */
    static final int[] MAT_COLORS = {0xFFF1EDE4, 0xFFE9DFC9, 0xFFBDB8AE, 0xFFA9B3A0, 0xFF1F2A3A, 0xFF151515};
    static final int[] MAT_INKS = {0xFF1E1C19, 0xFF2A241A, 0xFF1E1C19, 0xFF1D221A, 0xFFEDE7DA, 0xFFE9E4D8};
    /** Seconds between works ("manual" makes no sense in a screensaver). */
    static final int[] EVERY = {30, 60, 300, 900};
    static final String[] NIGHTS = {"off", "22", "23", "always"};
    static final int[] NIGHT_NAMES = {R.string.night_off, R.string.night_22, R.string.night_23, R.string.night_always};

    /** One reading of the settings: the view keeps it and compares it with the next. */
    static final class Snapshot {
        final int category, frame, mat, every, night;
        final boolean info, clock;

        Snapshot(SharedPreferences p) {
            category = index(CATEGORIES, p.getString("category", "all"));
            frame = index(FRAMES, p.getString("frame", "none"));
            mat = index(MATS, p.getString("mat", "paper"));
            int e = p.getInt("every", 60), ei = 1;
            for (int i = 0; i < EVERY.length; i++) if (EVERY[i] == e) ei = i;
            every = ei;
            night = index(NIGHTS, p.getString("night", "22"));
            info = p.getBoolean("info", true);
            clock = p.getBoolean("clock", true);
        }

        String categoryId() { return CATEGORIES[category]; }
        String frameId() { return FRAMES[frame]; }
        boolean bleed() { return frame == 0; }
        int matColor() { return MAT_COLORS[mat]; }
        int ink() { return MAT_INKS[mat]; }
        boolean darkMat() { return mat >= 4; }
        long everyMs() { return EVERY[every] * 1000L; }

        /** Whether the screen dims now (by the clock; the TV has no light sensor). */
        boolean nightNow(long now) {
            if (night == 0) return false;
            if (night == 3) return true;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            int h = c.get(Calendar.HOUR_OF_DAY);
            return night == 1 ? (h >= 22 || h < 7) : (h >= 23 || h < 8);
        }

        /** What changes the drawing of a work (not the timing). */
        boolean sameLook(Snapshot o) {
            return o != null && o.frame == frame && o.mat == mat && o.info == info && o.clock == clock;
        }
    }

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Snapshot read(Context c) {
        return new Snapshot(get(c));
    }

    static int index(String[] list, String id) {
        for (int i = 0; i < list.length; i++) if (list[i].equals(id)) return i;
        return 0;
    }
}
