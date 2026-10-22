package io.yourframe.tv.infrastructure;

// The decode-at-screen-size approach (inSampleSize, then halving steps) comes from Free My TV's Backdrop
// (MIT License, Copyright (c) 2026 Free My TV contributors). See NOTICE.

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Log;

import io.yourframe.tv.application.NextArtwork;
import io.yourframe.tv.domain.Artwork;
import io.yourframe.tv.domain.Layout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The works' pictures for one screen size: from IIIF at exactly the width needed, or the museum's full image,
 * downloaded in the background, decoded already cropped (the photo's border off) and
 * reduced to what the screen can show (inSampleSize, then halving steps, as Free My TV's Backdrop does), and the last
 * 12 kept on disk as JPEGs so the screensaver still works without internet. Runs on a worker thread only.
 */
public final class MuseumPictures implements NextArtwork.Pictures<Bitmap> {
    private final Context context;
    private final int w, h;

    /** For a screen of w × h. */
    public MuseumPictures(Context c, int w, int h) {
        context = c.getApplicationContext();
        this.w = w;
        this.h = h;
    }

    @Override public List<Integer> kept() {
        return cachedIds(context);
    }

    @Override public Bitmap load(Artwork a, boolean online) throws IOException {
        try {
            return load(context, a, w, h, online);
        } catch (OutOfMemoryError e) {
            throw new IOException("out of memory decoding " + a.id);
        }
    }

    private static final int KEEP = 12;
    private static final long MAX_BYTES = 90L * 1024 * 1024;   // some originals are huge scans: then web-large
    private static final Object LOCK = new Object();

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "art");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static File cached(Context c, int id) {
        File f = new File(dir(c), id + ".jpg");
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /** Ids of the works on disk, the most recently used first. */
    private static List<Integer> cachedIds(Context c) {
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        List<Integer> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            try {
                out.add(Integer.parseInt(f.getName().substring(0, f.getName().length() - 4)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /**
     * The work's image at most w × h-screen size: from disk if it's there, else downloaded (when `online`). Throws when
     * neither works.
     */
    private static Bitmap load(Context c, Artwork work, int w, int h, boolean online) throws IOException {
        File f = cached(c, work.id);
        if (f != null) {
            Bitmap b = decodeCached(f, w, h);
            if (b != null) {
                f.setLastModified(System.currentTimeMillis());
                return b;
            }
            f.delete();
        }
        if (!online) throw new IOException("not cached");
        File tmp = new File(c.getCacheDir(), "download-" + Thread.currentThread().getId() + ".jpg");
        boolean original = true;
        try {
            try {
                download(work.image.url(Layout.keptSize(work.ratio, w, h)[0], work.cropWidth()), tmp);
            } catch (IOException e) {
                String small = work.image.fallback();
                if (small == null) throw e;
                Log.w(CatalogueFile.TAG, work.museum + " " + work.source + ": " + e.getMessage() + "; trying a smaller one");
                original = false;
                download(small, tmp);
            }
            Bitmap b = decode(tmp, work, w, h);
            if (original) save(c, work.id, b);
            return b;
        } finally {
            tmp.delete();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url.replace(" ", "%20")).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(30_000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "YourFrame/1.0 (Android TV screensaver)");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long length = con.getContentLengthLong();
            if (length > MAX_BYTES) throw new IOException("too large (" + (length >> 20) + " MB)");
            long total = 0;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("too large");
                    if (Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
                    out.write(buf, 0, n);
                }
            }
        } finally {
            con.disconnect();
        }
    }

    /** Only the cropped region, decoded at the smallest power-of-two reduction still larger than the target. */
    private static Bitmap decode(File f, Artwork work, int w, int h) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        int sw = bounds.outWidth, sh = bounds.outHeight;
        if (sw <= 0 || sh <= 0) throw new IOException("unreadable image");
        int[] k = work.cropIn(sw, sh);
        Rect r = new Rect(k[0], k[1], k[2], k[3]);
        int[] t = Layout.keptSize((float) r.width() / r.height(), w, h);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Layout.sampleSize(r.width(), r.height(), t[0], t[1]);
        Bitmap src = null;
        try {
            BitmapRegionDecoder rd = BitmapRegionDecoder.newInstance(f.getPath(), false);
            try {
                src = rd.decodeRegion(r, opts);
            } finally {
                rd.recycle();
            }
        } catch (IOException | RuntimeException e) {
            Log.w(CatalogueFile.TAG, "region decoder: " + e.getMessage());
        }
        if (src == null) {
            Bitmap whole = BitmapFactory.decodeFile(f.getPath(), opts);
            if (whole == null) throw new IOException("unreadable image");
            int s = opts.inSampleSize;
            Rect rs = new Rect(r.left / s, r.top / s, Math.min(whole.getWidth(), r.right / s), Math.min(whole.getHeight(), r.bottom / s));
            src = Bitmap.createBitmap(whole, rs.left, rs.top, rs.width(), rs.height());
            if (src != whole) whole.recycle();
        }
        return shrink(src, t[0], t[1]);
    }

    private static Bitmap decodeCached(File f, int w, int h) {
        Bitmap b = BitmapFactory.decodeFile(f.getPath());
        if (b == null) return null;
        int[] t = Layout.keptSize((float) b.getWidth() / b.getHeight(), w, h);
        return shrink(b, t[0], t[1]);
    }

    /** Down to tw × th, halving while it can (a true average, no aliasing); never scaled up. */
    private static Bitmap shrink(Bitmap b, int tw, int th) {
        if (b.getWidth() <= tw || b.getHeight() <= th) return b;
        while (b.getWidth() / 2 >= tw && b.getHeight() / 2 >= th) {
            Bitmap half = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            b.recycle();
            b = half;
        }
        if (b.getWidth() == tw && b.getHeight() == th) return b;
        Bitmap out = Bitmap.createScaledBitmap(b, tw, th, true);
        if (out != b) b.recycle();
        return out;
    }

    private static void save(Context c, int id, Bitmap b) {
        synchronized (LOCK) {
            File dir = dir(c), tmp = new File(dir, id + ".tmp"), f = new File(dir, id + ".jpg");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.getFD().sync();
            } catch (IOException e) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(f)) tmp.delete();
        }
    }

    /** Keeps the 12 most recently used works on disk (and whatever is on screen now). */
    public static void prune(Context c, Collection<Integer> keep) {
        synchronized (LOCK) {
            List<Integer> ids = cachedIds(c);
            int kept = 0;
            for (int id : ids) {
                if (keep.contains(id)) continue;
                if (++kept > KEEP) new File(dir(c), id + ".jpg").delete();
            }
        }
    }
}
