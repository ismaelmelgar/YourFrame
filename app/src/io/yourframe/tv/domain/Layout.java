package io.yourframe.tv.domain;

/**
 * Where everything goes on a screen of w × h, as the approved prototype lays it out at 1920 × 1080 (sizes times k):
 * the Frame at the edge, the Mat inside it, the work's window as large as the Mat allows with room for the Masthead
 * above and the Caption below; at full bleed, the work covers the screen.
 */
public final class Layout {
    public final float k;
    public final int w, h;
    public final boolean bleed;
    public final Box inner, window;
    public final float captionTop, mastLeft, mastRight, mastTop;

    public Layout(int w, int h, Settings s, float ratio) {
        this.w = w;
        this.h = h;
        k = w / 1920f;
        bleed = s.bleed();
        float fw = s.frame.width * k;
        inner = new Box(fw, fw, w - fw, h - fw);
        if (bleed) {
            float ww = w, wh = ww / ratio;
            if (wh < h) {
                wh = h;
                ww = wh * ratio;
            }
            window = new Box((w - ww) / 2, (h - wh) / 2, (w + ww) / 2, (h + wh) / 2);
            captionTop = mastLeft = mastRight = mastTop = 0;
            return;
        }
        float margin = Math.round(inner.height() * 0.075f);
        float head = s.clock ? 92 * k : 0, capH = s.info ? 132 * k : 0;
        float maxW = inner.width() - 2 * margin, maxH = inner.height() - 2 * margin - capH - head;
        float ww = maxW, wh = ww / ratio;
        if (wh > maxH) {
            wh = maxH;
            ww = wh * ratio;
        }
        ww = Math.round(ww);
        wh = Math.round(wh);
        float wx = Math.round(inner.left + (inner.width() - ww) / 2);
        float wy = Math.round(inner.top + margin + head + (maxH - wh) / 2);
        window = new Box(wx, wy, wx + ww, wy + wh);
        captionTop = wy + wh + 30 * k;
        mastLeft = inner.left + Math.round(margin * 1.2f);
        mastRight = inner.right - Math.round(margin * 1.2f);
        mastTop = inner.top + Math.round(margin * 0.55f);
    }

    /** The size an artwork is kept at for a w × h screen: wide enough to cover it at full bleed. {width, height} */
    public static int[] keptSize(float ratio, int w, int h) {
        int tw = Math.round(Math.max(w, h * ratio));
        return new int[] {tw, Math.round(tw / ratio)};
    }

    /** The largest power-of-two reduction of a src × src image that stays at least target-sized. */
    public static int sampleSize(int srcW, int srcH, int targetW, int targetH) {
        int s = 1;
        while (srcW / (s * 2) >= targetW && srcH / (s * 2) >= targetH) s *= 2;
        return s;
    }
}
