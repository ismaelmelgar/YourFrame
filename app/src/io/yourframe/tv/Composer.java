package io.yourframe.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Lays out and paints one work the way The Frame shows it (and as the approved prototype draws it): the screen is the
 * mat, the work sits in a bevelled window, and the frame is drawn round the screen's edge. Everything that doesn't
 * change while the work is on screen (mat, window, work, bevel, frame) is painted once, in the background, into one
 * screen-sized bitmap: each frame of the crossfade then costs two textured quads, which even an Android 8 TV's GPU
 * draws at 60 fps. The caption and the clock are drawn on top by ArtView (they fade and tick).
 * All sizes are the prototype's, at 1920 × 1080, times k (the screen's width / 1920).
 */
final class Composer {
    private Composer() {}

    /** Frame width at 1920 px, per Prefs.FRAMES: full bleed, mat only, black, white, oak, walnut, gold. */
    static final int[] FRAME_W = {0, 0, 34, 34, 54, 54, 64};

    /** Where everything goes, for a screen, the settings and a work's aspect ratio. */
    static final class Layout {
        final float k;
        final int w, h;
        final RectF inner = new RectF(), window = new RectF();
        float captionTop, mastLeft, mastRight, mastTop;
        final boolean bleed;

        Layout(int w, int h, Prefs.Snapshot s, float ratio) {
            this.w = w;
            this.h = h;
            k = w / 1920f;
            bleed = s.bleed();
            float fw = FRAME_W[s.frame] * k;
            inner.set(fw, fw, w - fw, h - fw);
            if (bleed) {
                float ww = w, wh = ww / ratio;
                if (wh < h) {
                    wh = h;
                    ww = wh * ratio;
                }
                window.set((w - ww) / 2, (h - wh) / 2, (w + ww) / 2, (h + wh) / 2);
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
            window.set(wx, wy, wx + ww, wy + wh);
            captionTop = wy + wh + 30 * k;
            mastLeft = inner.left + Math.round(margin * 1.2f);
            mastRight = inner.right - Math.round(margin * 1.2f);
            mastTop = inner.top + Math.round(margin * 0.55f);
        }
    }

    /** The work on its mat, in its frame, as one bitmap of w × h (art null: the empty mat, for the placeholder). */
    static Bitmap compose(Bitmap art, Layout l, Prefs.Snapshot s) {
        Bitmap out = Bitmap.createBitmap(l.w, l.h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float k = l.k;
        if (l.bleed && art != null) {
            c.drawColor(0xFF000000);
            c.drawBitmap(art, null, l.window, p);
            return out;
        }
        c.drawColor(s.matColor());
        if (art != null) {
            RectF win = l.window;
            // The mat's cut edge: a thin light line round the window, and a faint wider one (only the thin one, dimmer,
            // on the dark mats).
            p.setStyle(Paint.Style.FILL);
            if (s.darkMat()) {
                p.setColor(0x1FFFFFFF);
                c.drawRect(win.left - k, win.top - k, win.right + k, win.bottom + k, p);
            } else {
                p.setColor(0x2EFFFFFF);
                c.drawRect(win.left - 7 * k, win.top - 7 * k, win.right + 7 * k, win.bottom + 7 * k, p);
                p.setColor(0x8CFFFFFF);
                c.drawRect(win.left - k, win.top - k, win.right + k, win.bottom + k, p);
            }
            p.setColor(0xFF000000);
            c.drawBitmap(art, null, win, p);
            bevel(c, win, k);
        }
        frame(c, l, s.frame);
        return out;
    }

    /** The window's bevel, inside the work: a hairline, a soft shadow from the top left, a light edge bottom right. */
    private static void bevel(Canvas c, RectF r, float k) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        // inset 2px 3px 6px rgba(0,0,0,.28)
        band(c, p, r.left, r.top, r.right, r.top + 10 * k, true, 0.28f, 3 * k, 3 * k);
        band(c, p, r.left, r.top, r.left + 9 * k, r.bottom, false, 0.28f, 2 * k, 3 * k);
        // inset -1px -1px 2px rgba(255,255,255,.25)
        p.setShader(new LinearGradient(0, r.bottom, 0, r.bottom - 3 * k, 0x40FFFFFF, 0, Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.bottom - 3 * k, r.right, r.bottom, p);
        p.setShader(new LinearGradient(r.right, 0, r.right - 3 * k, 0, 0x40FFFFFF, 0, Shader.TileMode.CLAMP));
        c.drawRect(r.right - 3 * k, r.top, r.right, r.bottom, p);
        p.setShader(null);
        // inset 0 0 0 1px rgba(0,0,0,.12)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, k));
        p.setColor(0x1F000000);
        float hw = p.getStrokeWidth() / 2;
        c.drawRect(r.left + hw, r.top + hw, r.right - hw, r.bottom - hw, p);
    }

    /**
     * A blurred inset shadow along one edge: from (x0,y0) inwards (down when vertical, right when not), offset `off`,
     * blur sigma `sigma`, peak alpha `a`. Gaussian falloff in six stops.
     */
    private static void band(Canvas c, Paint p, float x0, float y0, float x1, float y1, boolean down, float a, float off, float sigma) {
        float len = down ? y1 - y0 : x1 - x0;
        float[] pos = new float[6];
        int[] col = new int[6];
        float[] at = {0, off * 0.5f, off, off + sigma, off + 2 * sigma, off + 3 * sigma};
        for (int i = 0; i < 6; i++) {
            pos[i] = Math.min(1, at[i] / len);
            double z = (at[i] - off) / Math.max(0.5, sigma);
            col[i] = alpha(a * (float) (1 - phi(z)), 0);
        }
        for (int i = 1; i < 6; i++) if (pos[i] <= pos[i - 1]) pos[i] = Math.min(1, pos[i - 1] + 0.0001f);
        p.setShader(down ? new LinearGradient(0, y0, 0, y0 + len, col, pos, Shader.TileMode.CLAMP)
                : new LinearGradient(x0, 0, x0 + len, 0, col, pos, Shader.TileMode.CLAMP));
        c.drawRect(x0, y0, x1, y1, p);
        p.setShader(null);
    }

    private static double phi(double z) {   // the normal CDF, close enough
        return 1 / (1 + Math.exp(-1.702 * z));
    }

    private static int alpha(float a, int rgb) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // MARK: - Frames, drawn at the screen's edge like The Frame's snap-on bezels

    private static final int BLACK = 2, WHITE = 3, OAK = 4, WALNUT = 5, GOLD = 6;

    private static void frame(Canvas c, Layout l, int frame) {
        float fw = FRAME_W[frame] * l.k, k = l.k;
        if (fw <= 0) return;
        int w = l.w, h = l.h;
        RectF in = l.inner;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        // 1. The frame's shadow on the mat (inset 0 Ypx Bpx rgba(0,0,0,A)): strong at the top, soft at the sides.
        float oy = (frame == GOLD ? 10 : frame >= OAK ? 8 : 6) * k;
        float blur = (frame == GOLD ? 22 : frame >= OAK ? 18 : 14) * k;
        float a = frame == BLACK ? 0.5f : frame == WHITE ? 0.18f : frame == OAK ? 0.35f : frame == WALNUT ? 0.45f : 0.4f;
        float sigma = blur / 2;
        band(c, p, in.left, in.top, in.right, in.top + oy + 3 * sigma, true, a, oy, sigma);
        band(c, p, in.left, in.top, in.left + 3 * sigma, in.bottom, false, a, 0, sigma);
        mirrorBand(c, p, in, a, sigma);
        // 2. The lines inside the frame's lip (inset 0 0 0 Npx), the wider one first.
        if (frame == GOLD) {
            lip(c, p, in, 9 * k, 0x8CF0D78C);
            lip(c, p, in, 4 * k, 0xFF5C4416);
        } else if (frame == OAK) {
            lip(c, p, in, 3 * k, 0x8C503719);
        } else if (frame == WALNUT) {
            lip(c, p, in, 3 * k, 0xB3140A04);
        } else if (frame == BLACK) {
            lip(c, p, in, 2 * k, 0xFF2A2A2A);
        } else {
            lip(c, p, in, Math.max(1, k), 0xFFD9D6CE);
        }

        // 3. The frame itself.
        c.save();
        c.clipOutRect(in);
        p.setStyle(Paint.Style.FILL);
        switch (frame) {
            case BLACK: p.setColor(0xFF111111); break;
            case WHITE: p.setColor(0xFFF2F0EB); break;
            case OAK: p.setShader(diagonal(w, h, new int[] {0xFFC9A46E, 0xFFA9834F, 0xFFD2B07A, 0xFF9C7645, 0xFFC29C66},
                    new float[] {0, 0.3f, 0.55f, 0.8f, 1})); break;
            case WALNUT: p.setShader(diagonal(w, h, new int[] {0xFF5A3A24, 0xFF3E2716, 0xFF6B4630, 0xFF35210F, 0xFF523420},
                    new float[] {0, 0.35f, 0.6f, 0.85f, 1})); break;
            default: p.setShader(diagonal(w, h, new int[] {0xFF8A6A2A, 0xFFE6C878, 0xFFA9822F, 0xFFF3DC95, 0xFF9A7426,
                    0xFFD9B866, 0xFF7E5E22}, new float[] {0, 0.18f, 0.35f, 0.52f, 0.7f, 0.86f, 1})); break;
        }
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Each side lit from above, like a real moulding: top lighter, bottom darker, mitred at the corners.
        Path top = side(0, 0, w, 0, w - fw, fw, fw, fw), bottom = side(0, h, w, h, w - fw, h - fw, fw, h - fw);
        Path left = side(0, 0, 0, h, fw, h - fw, fw, fw), right = side(w, 0, w, h, w - fw, h - fw, w - fw, fw);
        boolean light = frame == WHITE;
        shade(c, p, top, light ? 0x14FFFFFF : 0x1AFFFFFF);
        shade(c, p, left, light ? 0x08FFFFFF : 0x0DFFFFFF);
        shade(c, p, right, light ? 0x0A000000 : 0x14000000);
        shade(c, p, bottom, light ? 0x1A000000 : 0x2B000000);

        if (frame == OAK || frame == WALNUT) {
            grain(c, top, 0, fw, w, true, frame == OAK ? 0x000000 : 0x000000, frame == OAK ? 0.09f : 0.16f, 11, k);
            grain(c, bottom, h - fw, h, w, true, 0, frame == OAK ? 0.09f : 0.16f, 12, k);
            grain(c, left, 0, fw, h, false, 0, frame == OAK ? 0.09f : 0.16f, 13, k);
            grain(c, right, w - fw, w, h, false, 0, frame == OAK ? 0.09f : 0.16f, 14, k);
        }
        // Mouldings: a ridge (light over dark) part-way across, a light inner lip, and the mitre joints.
        if (frame >= OAK) {
            float ridge = fw * (frame == GOLD ? 0.32f : 0.38f);
            ring(c, p, ridge, w, h, frame == GOLD ? 0x80F8E7AE : 0x30FFFFFF, Math.max(1, 1.5f * k));
            ring(c, p, ridge + 2 * k, w, h, frame == GOLD ? 0x665C4416 : 0x33000000, Math.max(1, 1.5f * k));
            if (frame == GOLD) {
                ring(c, p, fw * 0.7f, w, h, 0x59FFF2C4, Math.max(1, k));
                ring(c, p, fw * 0.7f + 2 * k, w, h, 0x4D4A3510, Math.max(1, k));
            }
        }
        ring(c, p, fw - Math.max(1, k), w, h, light ? 0x18000000 : 0x38FFFFFF, Math.max(1, k));
        ring(c, p, 0.5f, w, h, light ? 0x22000000 : 0x55000000, Math.max(1, k));
        if (frame != WHITE && frame != BLACK) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, k));
            p.setColor(0x26000000);
            c.drawLine(0, 0, fw, fw, p);
            c.drawLine(w, 0, w - fw, fw, p);
            c.drawLine(0, h, fw, h - fw, p);
            c.drawLine(w, h, w - fw, h - fw, p);
        }
        c.restore();
    }

    private static void mirrorBand(Canvas c, Paint p, RectF in, float a, float sigma) {
        // Right side (same falloff as the left) and the faint bottom one (the offset pushes the shadow away from it).
        float[] pos = {0, 0.33f, 0.66f, 1};
        int[] col = {alpha(a * 0.5f, 0), alpha(a * 0.16f, 0), alpha(a * 0.03f, 0), 0};
        p.setShader(new LinearGradient(in.right, 0, in.right - 3 * sigma, 0, col, pos, Shader.TileMode.CLAMP));
        c.drawRect(in.right - 3 * sigma, in.top, in.right, in.bottom, p);
        int[] bot = {alpha(a * 0.18f, 0), alpha(a * 0.05f, 0), 0, 0};
        p.setShader(new LinearGradient(0, in.bottom, 0, in.bottom - 2 * sigma, bot, pos, Shader.TileMode.CLAMP));
        c.drawRect(in.left, in.bottom - 2 * sigma, in.right, in.bottom, p);
        p.setShader(null);
    }

    private static void lip(Canvas c, Paint p, RectF in, float width, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawRect(in.left + width / 2, in.top + width / 2, in.right - width / 2, in.bottom - width / 2, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A rectangle `inset` px in from the screen's edge (inside the frame's band), stroked. */
    private static void ring(Canvas c, Paint p, float inset, int w, int h, int color, float width) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawRect(inset, inset, w - inset, h - inset, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** The CSS 135deg gradient over the whole screen: top left to bottom right, corners at 0 and 1. */
    private static LinearGradient diagonal(int w, int h, int[] colors, float[] stops) {
        float len = (w + h) * 0.70710678f, dx = 0.70710678f * len / 2, dy = 0.70710678f * len / 2;
        return new LinearGradient(w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy, colors, stops, Shader.TileMode.CLAMP);
    }

    private static Path side(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path p = new Path();
        p.moveTo(x0, y0);
        p.lineTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.close();
        return p;
    }

    private static void shade(Canvas c, Paint p, Path side, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(side, p);
    }

    /** Wood grain: thin, slightly wavy dark lines along one side (seeded, so every work gets the same frame). */
    private static void grain(Canvas c, Path side, float from, float to, float length, boolean horizontal, int rgb,
                              float strength, int seed, float k) {
        Random r = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        c.save();
        c.clipPath(side);
        int lines = Math.round((to - from) / (2.2f * k));
        Path path = new Path();
        for (int i = 0; i < lines; i++) {
            float at = from + (to - from) * r.nextFloat();
            float amp = (0.6f + r.nextFloat() * 1.6f) * k, wave = 180 + r.nextFloat() * 520, phase = r.nextFloat() * 6.28f;
            path.reset();
            for (float s = 0; s <= length; s += 24 * k) {
                float o = at + amp * (float) Math.sin(s / (wave * k) + phase);
                if (s == 0) {
                    if (horizontal) path.moveTo(s, o); else path.moveTo(o, s);
                } else if (horizontal) path.lineTo(s, o); else path.lineTo(o, s);
            }
            p.setStrokeWidth((0.5f + r.nextFloat() * 1.3f) * k);
            p.setColor(alpha(strength * (0.3f + r.nextFloat() * 0.7f), rgb));
            if (r.nextInt(5) == 0) p.setColor(alpha(strength * 0.6f, 0xFFF0D0));   // a lighter streak now and then
            c.drawPath(path, p);
        }
        c.restore();
    }
}
