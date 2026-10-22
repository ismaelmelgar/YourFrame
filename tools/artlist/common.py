"""Polite, cached HTTP (sequential, small delay, every response kept in tools/.cache) and the small-image analysis."""
import hashlib
import io
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, ImageStat

CACHE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".cache")
UA = "YourFrame-artlist/2.0 (personal screensaver list builder; one request at a time)"
DELAY = 0.08
_last = [0.0]


def fetch(url, binary=False, headers=None, retries=3, fresh=False):
    """GET with an on-disk cache keyed by URL. Returns bytes (binary) or parsed JSON; None on 404/410 or failure."""
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.sha1(url.encode()).hexdigest()
    path = os.path.join(CACHE, key + (".bin" if binary else ".json"))
    miss = path + ".404"
    if not fresh:
        if os.path.exists(miss):
            return None
        if os.path.exists(path):
            with open(path, "rb") as f:
                data = f.read()
            try:
                return data if binary else json.loads(data)
            except json.JSONDecodeError:
                pass
    safe = urllib.parse.quote(url, safe=":/?&=%#+,;@!$'()*~[]")
    for attempt in range(retries):
        wait = DELAY - (time.time() - _last[0])
        if wait > 0:
            time.sleep(wait)
        _last[0] = time.time()
        try:
            h = {"User-Agent": UA}
            h.update(headers or {})
            with urllib.request.urlopen(urllib.request.Request(safe, headers=h), timeout=60) as r:
                data = r.read()
            if not binary:
                json.loads(data)                   # don't cache a broken page
            with open(path + ".tmp", "wb") as f:
                f.write(data)
            os.replace(path + ".tmp", path)
            return data if binary else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code in (404, 410):
                open(miss, "w").close()
                return None
            time.sleep(2 + attempt * 5)
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError, OSError):
            time.sleep(2 + attempt * 3)
    return None


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def crop_box(img):
    """
    Fractions (left, top, right, bottom) without the photo's border: lines scanned from each edge while they are
    near-uniform and either dark (the black or dark-grey surround of many museum photos) or neutral grey/white close
    to the outermost line (a studio backdrop). Coloured or textured lines (paper, paint) stop the scan; at most 25%.
    """
    small = img.convert("RGB")
    small.thumbnail((400, 400))
    w, h = small.size
    px = small.load()

    def line(i, horizontal):
        return [px[x, i] for x in range(w)] if horizontal else [px[i, y] for y in range(h)]

    def stats(pixels):
        n = len(pixels)
        r = sum(p[0] for p in pixels) / n
        g = sum(p[1] for p in pixels) / n
        b = sum(p[2] for p in pixels) / n
        ls = [lum(p) for p in pixels]
        m = sum(ls) / n
        sd = (sum((x - m) ** 2 for x in ls) / n) ** 0.5
        return m, sd, max(r, g, b) - min(r, g, b)

    def is_border(pixels, ref):
        n = len(pixels)
        core = pixels[int(n * 0.04): max(int(n * 0.96), int(n * 0.04) + 1)]
        m, sd, sat = stats(core)
        if sd > 9:
            return False
        if m < 70:
            return True
        return sat < 14 and ref is not None and abs(m - ref) < 14

    def scan(count, horizontal, order):
        ref, cut = None, 0
        for i in order:
            pixels = line(i, horizontal)
            if ref is None:
                m0, _, sat0 = stats(pixels)
                ref = m0 if sat0 < 14 else None
            if not is_border(pixels, ref):
                break
            cut += 1
            if cut > count * 0.25:
                return 0
        return cut

    top, bottom = scan(h, True, range(h)), scan(h, True, range(h - 1, -1, -1))
    left, right = scan(w, False, range(w)), scan(w, False, range(w - 1, -1, -1))
    pad = lambda c: c + 1 if c else 0
    return (pad(left) / w, pad(top) / h, 1 - pad(right) / w, 1 - pad(bottom) / h)


def analyse(data):
    """The small image's crop, average colour, and luminance mean / spread inside the crop; None if unreadable."""
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception:
        return None
    box = crop_box(img)
    w, h = img.size
    c = img.convert("RGB").crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    c.thumbnail((96, 96))
    st = ImageStat.Stat(c.convert("L"))
    r, g, b = [int(round(x)) for x in ImageStat.Stat(c).mean[:3]]
    ratio = (box[2] - box[0]) * w / max(1.0, (box[3] - box[1]) * h)
    return {"box": box, "color": "#%02x%02x%02x" % (r, g, b), "mean": st.mean[0], "spread": st.stddev[0],
            "ratio": ratio, "size": (w, h)}
