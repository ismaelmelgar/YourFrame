package io.yourframe.tv.domain;

/** A work in the Catalogue: its museum, its Caption data, where its picture is, its shape, and the crop that drops
 * the photo's border. */
public final class Artwork {
    /** Only landscape works hang: wider than 6:5, so they fill a 16:9 screen (the screen itself is 1.78). */
    public static final float LANDSCAPE = 1.2f;

    public final int id;            // unique in the Catalogue (the museum's own id is in `source`)
    public final String museum, source, category, title, artist, date, medium;
    public final ImageRef image;
    public final float ratio;       // width / height after the crop
    public final float[] crop;      // left, top, right, bottom as fractions of the image
    public final int color;         // average colour, 0xAARRGGBB

    public Artwork(int id, String museum, String source, String category, String title, String artist, String date,
                   String medium, ImageRef image, float ratio, float[] crop, int color) {
        this.id = id;
        this.museum = museum;
        this.source = source;
        this.category = category;
        this.title = title;
        this.artist = artist;
        this.date = date;
        this.medium = medium;
        this.image = image;
        this.ratio = ratio;
        this.crop = crop == null || crop.length != 4 ? new float[] {0, 0, 1, 1} : crop;
        this.color = color;
    }

    public boolean landscape() {
        return ratio >= LANDSCAPE;
    }

    /** The crop in pixels of an image of w × h: {left, top, right, bottom}; the whole image if the crop is degenerate. */
    public int[] cropIn(int w, int h) {
        int l = Math.round(crop[0] * w), t = Math.round(crop[1] * h), r = Math.round(crop[2] * w), b = Math.round(crop[3] * h);
        if (r - l < 16 || b - t < 16) return new int[] {0, 0, w, h};
        return new int[] {Math.max(0, l), Math.max(0, t), Math.min(w, r), Math.min(h, b)};
    }

    public float cropWidth() {
        return crop[2] - crop[0];
    }
}
