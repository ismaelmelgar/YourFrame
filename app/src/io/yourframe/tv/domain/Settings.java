package io.yourframe.tv.domain;

/** What the viewer chose. Immutable: a change is a new Settings, compared with the old one. */
public final class Settings {
    /** Seconds between works ("manual" makes no sense in a screensaver). */
    public static final int[] EVERY = {30, 60, 300, 900};

    public enum Change { NONE, TIMING, LOOK, CATEGORY }

    public final Category category;
    public final Frame frame;
    public final Mat mat;
    public final int everySeconds;
    public final boolean info, clock;
    public final Night night;

    public Settings(Category category, Frame frame, Mat mat, int everySeconds, boolean info, boolean clock, Night night) {
        this.category = category;
        this.frame = frame;
        this.mat = mat;
        int e = 60;
        for (int s : EVERY) if (s == everySeconds) e = s;
        this.everySeconds = e;
        this.info = info;
        this.clock = clock;
        this.night = night;
    }

    public static final Settings DEFAULT = new Settings(Category.ALL, Frame.MAT_ONLY, Mat.PAPER, 60, true, true, Night.FROM_22);

    public boolean bleed() {
        return frame == Frame.BLEED;
    }

    public long everyMs() {
        return everySeconds * 1000L;
    }

    /** What a change means for the work on screen: another category, a repaint, or only the timing. */
    public Change comparedWith(Settings old) {
        if (old == null || old.category != category) return Change.CATEGORY;
        if (old.frame != frame || old.mat != mat || old.info != info || old.clock != clock) return Change.LOOK;
        if (old.everySeconds != everySeconds || old.night != night) return Change.TIMING;
        return Change.NONE;
    }
}
