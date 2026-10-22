package io.yourframe.tv.domain;

/** Night dimming, by the clock (TVs have no light sensor): the Screensaver dims to 58%, as the prototype's veil. */
public enum Night {
    OFF("off"), FROM_22("22"), FROM_23("23"), ALWAYS("always");

    public static final float DIM = 0.58f;
    public final String id;

    Night(String id) {
        this.id = id;
    }

    public boolean dims(int hour) {
        switch (this) {
            case FROM_22: return hour >= 22 || hour < 7;
            case FROM_23: return hour >= 23 || hour < 8;
            case ALWAYS: return true;
            default: return false;
        }
    }

    public static Night byId(String id) {
        for (Night n : values()) if (n.id.equals(id)) return n;
        return FROM_22;
    }
}
