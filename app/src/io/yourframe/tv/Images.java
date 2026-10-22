package io.yourframe.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Log;

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
 * The works' images: the ORIGINAL downloaded in the background, decoded already cropped (the photo's border off) and
 * reduced to what the screen can show (inSampleSize, then halving steps, as Free My TV's Backdrop does), and the last
 * 12 kept on disk as JPEGs so the screensaver still works without internet. Runs on a worker thread only.
 */
final class Images {
    private Images() {}

    private static final int KEEP = 12;
    private static final long MAX_BYTES = 90L * 1024 * 1024;   // some originals are huge scans: then web-large
    private static final Object LOCK = new Object();

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "art");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static File cached(Context c, int id) {
        File f = new File(dir(c), id + ".jpg");
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /** Ids of the works on disk, the most recently used first. */
    static List<Integer> cachedIds(Context c) {
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

    /** The size a work is kept at for a screen of w × h: wide enough to cover it at full bleed, never upscaled. */
    static int[] target(float ratio, int w, int h) {
        int tw = Math.round(Math.max(w, h * ratio));
        return new int[] {tw, Math.round(tw / ratio)};
    }

    /**
     * The work's image at most w × h-screen size: from disk if it's there, else downloaded (when `online`). Throws when
     * neither works.
     */
    static Bitmap load(Context c, Works.Work work, int w, int h, boolean online) throws IOException {
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
                download(work.original, tmp);
            } catch (IOException e) {
                Log.w(Works.TAG, "original of " + work.id + ": " + e.getMessage() + "; trying web-large");
                original = false;
                download(work.small, tmp);
            }
            Bitmap b = decode(tmp, work.crop, work.ratio, w, h);
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
    static Bitmap decode(File f, float[] crop, float ratio, int w, int h) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        int sw = bounds.outWidth, sh = bounds.outHeight;
        if (sw <= 0 || sh <= 0) throw new IOException("unreadable image");
        Rect r = new Rect(Math.round(crop[0] * sw), Math.round(crop[1] * sh), Math.round(crop[2] * sw), Math.round(crop[3] * sh));
        if (r.width() < 16 || r.height() < 16) r.set(0, 0, sw, sh);
        float real = (float) r.width() / r.height();
        int[] t = target(real, w, h);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (r.width() / (opts.inSampleSize * 2) >= t[0] && r.height() / (opts.inSampleSize * 2) >= t[1]) opts.inSampleSize *= 2;
        Bitmap src = null;
        try {
            BitmapRegionDecoder rd = BitmapRegionDecoder.newInstance(f.getPath(), false);
            try {
                src = rd.decodeRegion(r, opts);
            } finally {
                rd.recycle();
            }
        } catch (IOException | RuntimeException e) {
            Log.w(Works.TAG, "region decoder: " + e.getMessage());
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
        int[] t = target((float) b.getWidth() / b.getHeight(), w, h);
        return shrink(b, t[0], t[1]);
    }

    /** Down to tw × th, halving while it can (a true average, no aliasing); never scaled up. */
    static Bitmap shrink(Bitmap b, int tw, int th) {
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
    static void prune(Context c, Collection<Integer> keep) {
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
