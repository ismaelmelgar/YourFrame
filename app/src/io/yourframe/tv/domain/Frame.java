package io.yourframe.tv.domain;

/** The Frame drawn round the screen's edge (The Frame's snap-on bezels); width in px at 1920. */
public enum Frame {
    BLEED("bleed", 0), MAT_ONLY("none", 0), BLACK("black", 34), WHITE("white", 34), OAK("oak", 54), WALNUT("walnut", 54), GOLD("gold", 64);

    public final String id;
    public final int width;

    Frame(String id, int width) {
        this.id = id;
        this.width = width;
    }

    public static Frame byId(String id) {
        for (Frame f : values()) if (f.id.equals(id)) return f;
        return MAT_ONLY;
    }
}
