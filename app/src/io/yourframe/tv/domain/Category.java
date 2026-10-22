package io.yourframe.tv.domain;

/** What the Screensaver shows: a department of The Met, or All (every highlight except photography, as the prototype). */
public enum Category {
    ALL("all"), EUROPEAN("eu"), AMERICAN("am"), ASIAN("as"), PRINTS("pr"), ISLAMIC("is"), PHOTOGRAPHY("ph");

    public final String id;

    Category(String id) {
        this.id = id;
    }

    public boolean includes(Artwork a) {
        return this == ALL ? a.highlight && !PHOTOGRAPHY.id.equals(a.category) : id.equals(a.category);
    }

    public static Category byId(String id) {
        for (Category c : values()) if (c.id.equals(id)) return c;
        return ALL;
    }
}
