package io.yourframe.tv.ui;

// Derived from Free My TV's DreamView (MIT License, Copyright (c) 2026 Free My TV contributors,
// https://github.com/simbasjungles/free-my-tv): the prepared/current/incoming slides, the worker thread with a
// generation counter, the crossfade and releasing bitmaps after the last frame that uses them. See NOTICE.

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;

import io.yourframe.tv.R;
import io.yourframe.tv.application.NextArtwork;
import io.yourframe.tv.domain.Artwork;
import io.yourframe.tv.domain.Exhibition;
import io.yourframe.tv.domain.Layout;
import io.yourframe.tv.domain.Night;
import io.yourframe.tv.domain.Rotation;
import io.yourframe.tv.domain.Settings;
import io.yourframe.tv.infrastructure.MetCatalogue;
import io.yourframe.tv.infrastructure.MetPictures;
import io.yourframe.tv.infrastructure.SettingsStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver's one canvas, as on Samsung's The Frame: a work from The Met on its mat (or full bleed), changing
 * every few minutes with a slow 2 s crossfade; the newspaper masthead (section · date · time over a double rule) on
 * the mat; the caption under the work, which leaves 10 s after the work arrives; dimmer at night.
 *
 * Cost per frame: one or two screen-sized bitmaps (everything static is painted into them in the background by
 * Painter) and a few lines of text. Nothing is allocated in onDraw. Frames are drawn at 60 fps only while something
 * moves (a crossfade, the caption leaving); otherwise the view sleeps until the next minute or the next change.
 * No RenderNode, layers or shaders on screen, so Android 8 to 11 TVs (Free My TV's LEGACY GPUs) take the same path.
 */
public final class ArtView extends View {
    private static final long RETRY_MS = 30_000;
    /** Night: as the prototype's black veil at 42%, applied as a colour filter (no extra full-screen layer). */
    private static final float NIGHT = Night.DIM;

    /** One work on screen: its painted bitmap and its caption, measured once. */
    private static final class Slide {
        Artwork work;             // null: the placeholder (offline or loading)
        Bitmap image;             // GPU memory
        Bitmap art;               // the work's pixels, kept only by the settings' preview (to repaint on changes)
        Layout layout;
        String who = "", what = "", meta = "", src = "";
        float whoW, whatW, metaW, srcW;
        boolean placeholder, loadingText, immediate;
        long startedAt;
    }

    private final boolean preview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Paint slidePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint black = new Paint();
    private final LightingColorFilter nightFilter;
    private Settings settings;
    private final Exhibition exhibition;
    private NextArtwork<Bitmap> next;
    private int nextW, nextH;
    private Slide current, incoming, prepared;
    private boolean running, loading, night, everShown;
    private int generation, w, h;
    private long firstAt;
    private float k = 1;
    private String hour = "", day = "", shortDay = "", section = "";
    private float hourW, dayW, shortDayW, clockW;
    private String msgLoading = "", msgOffline = "", msgOfflineText = "";
    private int ink;

    // Text: sizes from the prototype at 1920 px, times k.
    private TextPaint whoP, whatP, metaP, srcP, secP, dayP, hourP, clockP, clockDateP, msgTitleP, msgP;
    private final Paint ruleP = new Paint(), bandP = new Paint(Paint.DITHER_FLAG);
    private float whoBase, whatBase, metaBase, captionHeight;   // from the caption's top
    private float clockTimeBase, clockLineY, clockDateBase;

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            updateClock();
            invalidate();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };
    private final Runnable wake = this::invalidate;
    private final Runnable retry = this::prepareNext;

    public ArtView(Context c, boolean preview) {
        super(c);
        this.preview = preview;
        Fonts.init(c);
        int m = Math.round(255 * NIGHT);
        nightFilter = new LightingColorFilter(0xFF000000 | (m << 16) | (m << 8) | m, 0);
        black.setColor(0xFF000000);
        settings = SettingsStore.read(c);
        exhibition = SettingsStore.exhibition(c);
    }

    // MARK: - Lifecycle

    public void start() {
        if (running) return;
        running = true;
        if (worker.isShutdown()) worker = Executors.newSingleThreadExecutor();
        if (next != null) next.failed.clear();
        settings = SettingsStore.read(getContext());
        updateClock();
        handler.post(minuteTick);
        prepareNext();
        invalidate();
    }

    public void stop() {
        if (!running) return;
        running = false;
        generation++;
        loading = false;
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(wake);
        handler.removeCallbacks(retry);
        final List<Bitmap> gone = new ArrayList<>();
        for (Slide s : new Slide[] {current, incoming, prepared}) {
            if (s == null) continue;
            if (s.image != null) gone.add(s.image);
            if (s.art != null) gone.add(s.art);
        }
        current = incoming = prepared = null;
        everShown = false;
        // The bitmaps stay in GPU memory until the garbage collector runs: release them after the last frame.
        if (!gone.isEmpty()) handler.postDelayed(() -> { for (Bitmap b : gone) b.recycle(); }, 1000);
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        worker.shutdownNow();
        super.onDetachedFromWindow();
    }

    /** The settings changed (the preview): repaint the work on screen with the new look, or change category. */
    public void settingsChanged() {
        Settings old = settings;
        settings = SettingsStore.read(getContext());
        updateClock();
        if (!running) return;
        Settings.Change change = settings.comparedWith(old);
        boolean category = change == Settings.Change.CATEGORY;
        if (category || change == Settings.Change.LOOK) {
            generation++;           // whatever was loading was for the old settings
            loading = false;
            if (prepared != null) release(prepared);
            prepared = null;
            Slide on = incoming != null ? incoming : current;
            if (!category && on != null && (on.art != null || on.placeholder)) repaint(on);
            else prepareNext(true);
        }
        invalidate();
    }

    // MARK: - Preparing the next work (in the background)

    private void prepareNext() {
        prepareNext(false);
    }

    private void prepareNext(final boolean immediate) {
        handler.removeCallbacks(retry);
        if (!running || loading || prepared != null || w == 0) return;
        loading = true;
        final int gen = generation, sw = w, sh = h;
        final Settings s = settings;
        final boolean first = !everShown;
        final Set<Integer> onScreen = new HashSet<>();
        for (Slide on : new Slide[] {current, incoming}) if (on != null && on.work != null) onScreen.add(on.work.id);
        final NextArtwork<Bitmap> next = nextArtwork(sw, sh);
        worker.execute(() -> {
            // The first work of a session: one kept on the TV if there is one, else "loading" while it downloads.
            NextArtwork.Found<Bitmap> found = first ? next.kept(s.category, onScreen) : null;
            if (first && found == null) postPlaceholder(gen, s, sw, sh, true);
            if (found == null) found = next.find(s.category, onScreen);
            Slide slide = null;
            if (found != null) {
                try {
                    slide = build(found.artwork, found.picture, s, sw, sh);
                } catch (RuntimeException | OutOfMemoryError e) {
                    Log.w(MetCatalogue.TAG, "paint: " + e);
                    found.picture.recycle();
                }
                if (found.kept) Log.i(MetCatalogue.TAG, "showing a work kept on the TV: " + found.artwork.id);
            }
            final Slide ready = slide;
            handler.post(() -> {
                if (gen != generation) {
                    if (ready != null) release(ready);
                    return;
                }
                loading = false;
                if (ready == null) {
                    if ((current == null || current.placeholder) && prepared == null) postPlaceholderNow(s, false);
                    handler.postDelayed(retry, RETRY_MS);
                    return;
                }
                ready.immediate = immediate || (current != null && current.placeholder);
                if (prepared != null) release(prepared);   // a placeholder still waiting
                prepared = ready;
                invalidate();
            });
        });
    }

    /** The application service for this screen size (made again when the size changes). */
    private NextArtwork<Bitmap> nextArtwork(int sw, int sh) {
        if (next == null || nextW != sw || nextH != sh) {
            Context ctx = getContext().getApplicationContext();
            next = new NextArtwork<>(MetCatalogue.get(ctx), exhibition, new MetPictures(ctx, sw, sh), new Random());
            nextW = sw;
            nextH = sh;
        }
        return next;
    }

    private void postPlaceholder(int gen, Settings s, int sw, int sh, boolean loadingText) {
        final Slide p = placeholder(s, sw, sh, loadingText);
        handler.post(() -> {
            // Only on an empty screen, or instead of the "loading" one once it turns out there's no internet.
            boolean replaces = current == null || (current.placeholder && current.loadingText && !loadingText);
            if (gen != generation || !replaces || prepared != null) {
                release(p);
                return;
            }
            p.immediate = current != null;
            prepared = p;
            invalidate();
        });
    }

    private void postPlaceholderNow(Settings s, boolean loadingText) {
        final int gen = generation, sw = w, sh = h;
        worker.execute(() -> postPlaceholder(gen, s, sw, sh, loadingText));
    }

    private Slide placeholder(Settings s, int sw, int sh, boolean loadingText) {
        Slide p = new Slide();
        p.placeholder = true;
        p.loadingText = loadingText;
        p.layout = new Layout(sw, sh, s, 1.5f);
        p.image = gpu(Painter.paint(null, p.layout, s));
        return p;
    }

    /** Paints the work and measures its caption (worker thread: its own copies of the paints). */
    private Slide build(Artwork work, Bitmap art, Settings s, int sw, int sh) {
        Slide slide = new Slide();
        slide.work = work;
        slide.layout = new Layout(sw, sh, s, (float) art.getWidth() / art.getHeight());
        slide.image = gpu(Painter.paint(art, slide.layout, s));
        if (preview) slide.art = art;
        else art.recycle();
        caption(slide);
        return slide;
    }

    /** The preview after a change of frame, mat or what's shown: the same work, painted again. */
    private void repaint(final Slide on) {
        loading = true;
        final int gen = generation, sw = w, sh = h;
        final Settings s = settings;
        final Artwork work = on.work;
        final Bitmap art = on.art == null ? null : on.art.copy(Bitmap.Config.ARGB_8888, false);
        final boolean loadingText = on.loadingText;
        worker.execute(() -> {
            Slide slide;
            try {
                slide = art == null ? placeholder(s, sw, sh, loadingText) : build(work, art, s, sw, sh);
            } catch (RuntimeException | OutOfMemoryError e) {
                slide = null;
            }
            final Slide ready = slide;
            handler.post(() -> {
                if (gen != generation) {
                    if (ready != null) release(ready);
                    return;
                }
                loading = false;
                if (ready == null) return;
                ready.immediate = true;
                ready.startedAt = 0;
                prepared = ready;
                invalidate();
            });
        });
    }

    private void caption(Slide s) {
        TextPaint who = new TextPaint(whoP), what = new TextPaint(whatP), meta = new TextPaint(metaP), src = new TextPaint(srcP);
        Locale loc = Locale.getDefault();
        Artwork wk = s.work;
        s.who = (wk.artist.isEmpty() ? getContext().getString(R.string.unknown_artist) : wk.artist).toUpperCase(loc);
        s.who = TextUtils.ellipsize(s.who, who, 1500 * k, TextUtils.TruncateAt.END).toString();
        s.what = TextUtils.ellipsize(wk.title.isEmpty() ? getContext().getString(R.string.untitled) : wk.title,
                what, 1200 * k, TextUtils.TruncateAt.END).toString();
        s.src = getContext().getString(R.string.museum).toUpperCase(loc);
        s.srcW = src.measureText(s.src);
        StringBuilder m = new StringBuilder(wk.date);
        if (!wk.medium.isEmpty()) m.append(m.length() > 0 ? " · " : "").append(wk.medium);
        String body = TextUtils.ellipsize(m.toString(), meta, Math.max(200 * k, 1500 * k - s.srcW), TextUtils.TruncateAt.END).toString();
        s.meta = body.isEmpty() ? "" : body + "   —   ";
        s.whoW = who.measureText(s.who);
        s.whatW = what.measureText(s.what);
        s.metaW = meta.measureText(s.meta);
    }

    private static Bitmap gpu(Bitmap b) {
        Bitmap g = b.copy(Bitmap.Config.HARDWARE, false);
        if (g == null) return b;
        b.recycle();
        return g;
    }

    private void release(Slide s) {
        final Bitmap a = s.image, b = s.art;
        handler.postDelayed(() -> {
            if (a != null) a.recycle();
            if (b != null) b.recycle();
        }, 1000);
    }

    // MARK: - Timing

    private void advance(long up) {
        if (current == null) {
            if (prepared == null) return;
            current = prepared;
            prepared = null;
            current.startedAt = up;
            firstAt = up;
            shown(current);
            prepareNext();
            return;
        }
        if (incoming == null && prepared != null
                && Rotation.due(current.startedAt, up, settings.everyMs(), prepared.immediate)) {
            incoming = prepared;
            prepared = null;
            incoming.startedAt = up;
            return;
        }
        if (incoming != null && up >= incoming.startedAt + Rotation.FADE_MS) {
            Slide old = current;
            current = incoming;
            incoming = null;
            release(old);
            shown(current);
            prepareNext();
        }
    }

    private void shown(Slide s) {
        if (s.placeholder || s.work == null) return;
        everShown = true;
        final Context ctx = getContext().getApplicationContext();
        final int id = s.work.id;
        final Set<Integer> keep = new HashSet<>();
        keep.add(id);
        if (incoming != null && incoming.work != null) keep.add(incoming.work.id);
        worker.execute(() -> {
            exhibition.shown(id);
            SettingsStore.save(ctx, exhibition);
            MetPictures.prune(ctx, keep);
        });
    }

    private void updateClock() {
        long now = System.currentTimeMillis();
        Locale loc = Locale.getDefault();
        Date d = new Date(now);
        hour = new SimpleDateFormat(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", loc).format(d);
        day = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMMy"), loc).format(d).toUpperCase(loc);
        shortDay = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), loc).format(d).toUpperCase(loc);
        section = (getContext().getString(R.string.masthead) + " · "
                + getContext().getString(Names.category(settings.category))).toUpperCase(loc);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        boolean n = settings.night.dims(cal.get(java.util.Calendar.HOUR_OF_DAY));
        if (n != night) {
            night = n;
            slidePaint.setColorFilter(night ? nightFilter : null);
        }
        if (hourP != null) {
            hourW = hourP.measureText(hour);
            dayW = dayP.measureText(day);
            shortDayW = clockDateP.measureText(shortDay);
            clockW = clockP.measureText(hour);
        }
    }

    // MARK: - Size and type

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        whoP = text(Fonts.textBold, 17, 0.22f);
        whatP = text(Fonts.displayItalic, 31, 0);
        metaP = text(Fonts.text, 15, 0.08f);
        srcP = text(Fonts.text, 12.5f, 0.16f);
        secP = text(Fonts.textBold, 14, 0.26f);
        dayP = text(Fonts.text, 15, 0.2f);
        hourP = text(Fonts.display, 40, 0);
        hourP.setFontFeatureSettings("'lnum', 'tnum'");
        clockP = text(Fonts.display, 60, 0);
        clockP.setFontFeatureSettings("'lnum', 'tnum'");
        clockDateP = text(Fonts.text, 14, 0.18f);
        msgTitleP = text(Fonts.displayItalic, 40, 0);
        msgP = text(Fonts.text, 17, 0.04f);
        msgTitleP.setTextAlign(Paint.Align.CENTER);
        msgP.setTextAlign(Paint.Align.CENTER);
        clockP.setTextAlign(Paint.Align.RIGHT);
        msgLoading = getContext().getString(R.string.loading_text);
        msgOffline = getContext().getString(R.string.offline_title);
        msgOfflineText = getContext().getString(R.string.offline_text);
        ruleP.setStrokeWidth(Math.max(1, k));
        // The caption's lines, as CSS lays them out: rule, 14 px, artist, 6 px, title (line-height 1.2), 8 px, meta.
        Paint.FontMetrics fm = whoP.getFontMetrics();
        float y = Math.max(1, k) + 14 * k;
        whoBase = y - fm.ascent;
        y += fm.descent - fm.ascent + 6 * k;
        Paint.FontMetrics ft = whatP.getFontMetrics();
        float line = 31 * 1.2f * k;
        whatBase = y + (line - (ft.descent - ft.ascent)) / 2 - ft.ascent;
        y += line + 8 * k;
        Paint.FontMetrics fmm = metaP.getFontMetrics();
        metaBase = y - fmm.ascent;
        captionHeight = y + fmm.descent - fmm.ascent;
        // Full bleed: the clock top right (time, a hairline, the date) and a dark band under the caption.
        Paint.FontMetrics fc = clockP.getFontMetrics();
        clockTimeBase = 70 * k + (60 * k - (fc.descent - fc.ascent)) / 2 - fc.ascent;
        clockLineY = 70 * k + 60 * k + 10 * k;
        clockDateBase = clockLineY + 8 * k - clockDateP.getFontMetrics().ascent;
        for (Paint p : new Paint[] {clockP, clockDateP}) p.setShadowLayer(16 * k, 0, 2 * k, 0x8C000000);
        float bandTop = h - 52 * k - captionHeight - 130 * k;
        bandP.setShader(new LinearGradient(0, bandTop, 0, h, 0x00000000, 0x9E000000, Shader.TileMode.CLAMP));
        updateClock();
        // Slides painted for another size are dropped; the next one is painted for this one.
        generation++;
        loading = false;
        for (Slide s : new Slide[] {current, incoming, prepared}) if (s != null) release(s);
        current = incoming = prepared = null;
        if (running) prepareNext(true);
    }

    private TextPaint text(android.graphics.Typeface t, float size, float spacing) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(t);
        p.setTextSize(size * k);
        p.setLetterSpacing(spacing);
        return p;
    }

    // MARK: - Drawing

    @Override protected void onDraw(Canvas c) {
        if (w == 0) return;
        long up = SystemClock.uptimeMillis();
        if (running) advance(up);
        Slide cur = current;
        if (cur == null) {
            c.drawRect(0, 0, w, h, black);
            return;
        }
        float first = Rotation.ease(up - firstAt, Rotation.FIRST_MS);
        float e = incoming == null ? 0 : Rotation.ease(up - incoming.startedAt, Rotation.FADE_MS);
        if (first < 1) c.drawRect(0, 0, w, h, black);
        slidePaint.setAlpha(Math.round(255 * first));
        c.drawBitmap(cur.image, 0, 0, slidePaint);
        if (incoming != null) {
            slidePaint.setAlpha(Math.round(255 * e));
            c.drawBitmap(incoming.image, 0, 0, slidePaint);
        }

        drawSlideText(c, cur, up, first * (1 - e));
        if (incoming != null) drawSlideText(c, incoming, up, e);
        // The masthead (or, at full bleed, the clock) stays put across the crossfade: same mat, same place.
        Slide top = incoming != null && e > 0.5f ? incoming : cur;
        if (settings.clock) {
            if (top.layout.bleed) drawBleedClock(c, first);
            else drawMasthead(c, top, first);
        }

        // Next frame: at once while something moves, else when the next thing happens.
        boolean moving = first < 1 || incoming != null || captionMoving(cur, up)
                || (prepared != null && prepared.immediate);
        if (moving) {
            postInvalidateOnAnimation();
        } else if (running) {
            long next = Long.MAX_VALUE;
            if (settings.info && up < cur.startedAt + Rotation.CAPTION_MS) next = cur.startedAt + Rotation.CAPTION_MS;
            if (prepared != null) next = Math.min(next, cur.startedAt + settings.everyMs());
            handler.removeCallbacks(wake);
            if (next != Long.MAX_VALUE) handler.postAtTime(wake, next);
        }
    }

    private boolean captionMoving(Slide s, long up) {
        return settings.info && !s.placeholder && Rotation.captionFading(up - s.startedAt);
    }

    private int dim(int color, float alpha) {
        int a = Math.round(((color >>> 24) / 255f) * Math.max(0, Math.min(1, alpha)) * 255);
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        if (night) {
            r = Math.round(r * NIGHT);
            g = Math.round(g * NIGHT);
            b = Math.round(b * NIGHT);
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawSlideText(Canvas c, Slide s, long up, float alpha) {
        if (alpha <= 0.004f) return;
        Layout l = s.layout;
        int inkColor = l.bleed ? 0xFFF4F0E6 : settings.mat.ink;
        if (s.placeholder) {
            float cy = (l.window.top + l.window.bottom) / 2, cx = w / 2f;
            msgTitleP.setColor(dim(inkColor, alpha * 0.85f));
            c.drawText(s.loadingText ? msgLoading : msgOffline, cx, cy, msgTitleP);
            if (!s.loadingText) {
                msgP.setColor(dim(inkColor, alpha * 0.6f));
                c.drawText(msgOfflineText, cx, cy + 50 * k, msgP);
            }
            return;
        }
        if (!settings.info) return;
        float a = alpha * Rotation.captionAlpha(up - s.startedAt);
        if (a <= 0.004f) return;
        float top;
        if (l.bleed) {
            top = h - 52 * k - captionHeight;
            bandP.setAlpha(Math.round(255 * a));
            c.drawRect(0, top - 130 * k, w, h, bandP);
        } else {
            top = l.captionTop;
        }
        float cx = w / 2f;
        ruleP.setColor(dim(inkColor, a * 0.45f));
        c.drawLine(cx - 110 * k, top, cx + 110 * k, top, ruleP);
        whoP.setColor(dim(inkColor, a));
        c.drawText(s.who, cx - s.whoW / 2, top + whoBase, whoP);
        whatP.setColor(dim(inkColor, a));
        c.drawText(s.what, cx - s.whatW / 2, top + whatBase, whatP);
        float mx = cx - (s.metaW + s.srcW) / 2;
        metaP.setColor(dim(inkColor, a * 0.7f));
        c.drawText(s.meta, mx, top + metaBase, metaP);
        srcP.setColor(dim(inkColor, a * 0.7f));
        c.drawText(s.src, mx + s.metaW, top + metaBase, srcP);
    }

    /** Section · full date · time, over a double rule, at the top of the mat. */
    private void drawMasthead(Canvas c, Slide s, float alpha) {
        Layout l = s.layout;
        int inkColor = settings.mat.ink;
        float bottom = l.mastTop + 44 * k;
        secP.setColor(dim(inkColor, alpha * 0.8f));
        c.drawText(section, l.mastLeft, bottom - secP.descent(), secP);
        dayP.setColor(dim(inkColor, alpha * 0.75f));
        c.drawText(day, (l.mastLeft + l.mastRight) / 2 - dayW / 2, bottom - 2 * k - dayP.descent(), dayP);
        hourP.setColor(dim(inkColor, alpha));
        float lineTop = bottom - 40 * k;
        float base = lineTop + (40 * k - (hourP.descent() - hourP.ascent())) / 2 - hourP.ascent();
        c.drawText(hour, l.mastRight - hourW, base, hourP);
        // The double rule: 2 px, a 3 px gap, 1 px, its bottom 14 px under the masthead.
        ruleP.setColor(dim(inkColor, alpha * 0.4f));
        float y0 = bottom + 14 * k - 6 * k;
        float thick = Math.max(1, 2 * k), thin = Math.max(1, k);
        ruleP.setStrokeWidth(thick);
        c.drawLine(l.mastLeft, y0 + thick / 2, l.mastRight, y0 + thick / 2, ruleP);
        ruleP.setStrokeWidth(thin);
        c.drawLine(l.mastLeft, y0 + 5 * k + thin / 2, l.mastRight, y0 + 5 * k + thin / 2, ruleP);
        ruleP.setStrokeWidth(Math.max(1, k));
    }

    /** Full bleed: the time, a hairline and the date, top right, with a soft shadow. */
    private void drawBleedClock(Canvas c, float alpha) {
        float right = w - 90 * k;
        int color = 0xFFF4F0E6;
        clockP.setColor(dim(color, alpha));
        c.drawText(hour, right, clockTimeBase, clockP);
        float boxW = Math.max(clockW, shortDayW);
        ruleP.setColor(dim(color, alpha * 0.35f));
        c.drawLine(right - boxW, clockLineY, right, clockLineY, ruleP);
        clockDateP.setColor(dim(color, alpha * 0.75f));
        c.drawText(shortDay, right - shortDayW, clockDateBase, clockDateP);
    }
}
