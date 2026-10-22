package io.yourframe.tv.domain;

/** The Mat (passepartout): the screen's colour round the work, and the ink printed on it. */
public enum Mat {
    PAPER("paper", 0xFFF1EDE4, 0xFF1E1C19), IVORY("ivory", 0xFFE9DFC9, 0xFF2A241A), STONE("stone", 0xFFBDB8AE, 0xFF1E1C19),
    SAGE("sage", 0xFFA9B3A0, 0xFF1D221A), NAVY("navy", 0xFF1F2A3A, 0xFFEDE7DA), BLACK("black", 0xFF151515, 0xFFE9E4D8);

    public final String id;
    public final int color, ink;

    Mat(String id, int color, int ink) {
        this.id = id;
        this.color = color;
        this.ink = ink;
    }

    public boolean dark() {
        return this == NAVY || this == BLACK;
    }

    public static Mat byId(String id) {
        for (Mat m : values()) if (m.id.equals(id)) return m;
        return PAPER;
    }
}
