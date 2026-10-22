package io.yourframe.tv.domain;

/**
 * Where an Artwork's picture is: a fixed URL (the museum's full image, with a smaller one to fall back on), or a IIIF
 * image service, which is asked for exactly the width the screen needs (crisp at 1080p without an 8 MB download).
 */
public final class ImageRef {
    public final boolean iiif;
    public final String url;        // fixed: the full image; IIIF: the service's base URL
    public final String small;      // fixed: a smaller copy (null if none)
    public final int width;         // the full image's width in px, 0 when unknown

    private ImageRef(boolean iiif, String url, String small, int width) {
        this.iiif = iiif;
        this.url = url;
        this.small = small;
        this.width = width;
    }

    public static ImageRef fixed(String url, String small) {
        return new ImageRef(false, url, small, 0);
    }

    public static ImageRef iiif(String base, int width) {
        return new ImageRef(true, base, null, width);
    }

    /**
     * The picture for a work that must be `targetWidth` px wide on screen once `cropWidth` (0..1) of it is kept: IIIF
     * asks for just that width (never more than the full image); a fixed URL is what it is.
     */
    public String url(int targetWidth, float cropWidth) {
        if (!iiif) return url;
        int w = (int) Math.ceil(targetWidth / Math.max(0.05f, Math.min(1, cropWidth)));
        if (width > 0) w = Math.min(w, width);
        return url + "/full/" + w + ",/0/default.jpg";
    }

    /** A smaller picture to try when the first one fails (null if there's none). */
    public String fallback() {
        return iiif ? url + "/full/!1280,1280/0/default.jpg" : small;
    }
}
