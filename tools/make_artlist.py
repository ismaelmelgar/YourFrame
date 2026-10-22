#!/usr/bin/env python3
"""
Builds app/assets/artworks.json: the public-domain, landscape works of The Metropolitan Museum of Art that the
screensaver shows. Run it on a computer (the TV never searches anything); the output is bundled in the APK.

    .venv/bin/python tools/make_artlist.py            # uses the cache in tools/.cache, so reruns are cheap
    .venv/bin/python tools/make_artlist.py --fresh    # ignores cached search results (objects/images stay cached)

For each category it takes the department's highlights (v1.1 search, paginated) and, when they give too few landscape
works, tops up with the department's other works that have images, capped. Every work is checked:
  - isPublicDomain with a primaryImage;
  - landscape (width / height >= 1.2) by the catalogue's measurements first (to skip portrait works without
    downloading anything), then by the web-large image after cropping its photo border;
  - the crop box that removes the black/grey border many Met photos have, as fractions 0..1 (so it applies to the
    original image too), and the average colour of the work (for a coloured mat later).
Polite to the API: sequential requests with a small delay, every response cached on disk.

Needs Python 3 and Pillow (python3 -m venv .venv && .venv/bin/pip install Pillow).
"""
import argparse
import hashlib
import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, ImageStat

API = "https://collectionapi.metmuseum.org/public/collection"
IMG_PREFIX = "https://images.metmuseum.org/CRDImages/"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CACHE = os.path.join(HERE, ".cache")
OUT = os.path.join(ROOT, "app", "assets", "artworks.json")

# Category id (used by the app) -> Met department ids. "all" is not here: the app builds it from every highlight
# except photography, as the prototype does.
CATEGORIES = [
    ("eu", [11, 15]),   # European Paintings, Robert Lehman Collection
    ("am", [1]),        # The American Wing (paintings only: see MEDIUM)
    ("as", [6]),        # Asian Art
    ("pr", [9]),        # Drawings and Prints
    ("is", [14]),       # Islamic Art
    ("ph", [19]),       # Photographs
]
# Search filters per category: the American Wing is mostly furniture and silver; the category is its paintings.
MEDIUM = {"am": "Paintings"}
LANDSCAPE = 1.2          # wider than 6:5 (a 16:9 screen is 1.78)
MIN_PER_CATEGORY = 90    # top up with non-highlights below this many landscape works
TOPUP_FETCH_CAP = 700    # at most this many extra objects looked at per department when topping up
TOPUP_KEEP_CAP = 120     # at most this many non-highlight works kept per category
DELAY = 0.08             # seconds between requests (the API allows 80/s; we stay far below)
UA = "YourFrame-artlist/1.0 (personal screensaver list builder)"

_last = [0.0]


def fetch(url, binary=False, retries=3):
    """GET with an on-disk cache (keyed by URL). Returns bytes/obj, or None on 404."""
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.sha1(url.encode()).hexdigest()
    path = os.path.join(CACHE, key + (".bin" if binary else ".json"))
    miss = path + ".404"
    if os.path.exists(miss):
        return None
    if os.path.exists(path):
        with open(path, "rb") as f:
            data = f.read()
        return data if binary else json.loads(data)
    for attempt in range(retries):
        wait = DELAY - (time.time() - _last[0])
        if wait > 0:
            time.sleep(wait)
        _last[0] = time.time()
        try:
            safe = urllib.parse.quote(url, safe=":/?&=%#+,;@!$'()*~")
            req = urllib.request.Request(safe, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            with open(path + ".tmp", "wb") as f:
                f.write(data)
            os.replace(path + ".tmp", path)
            return data if binary else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                open(miss, "w").close()
                return None
            if e.code in (403, 429, 500, 502, 503):
                time.sleep(2 + attempt * 5)
                continue
            raise
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
            time.sleep(2 + attempt * 3)
    return None


def search(dept, highlight, fresh, medium=None):
    """Every object id of a department search (v1.1 is paginated with offset/limit)."""
    ids, offset, limit = [], 0, 500
    while True:
        q = "%s/v1.1/search?%shasImages=true&departmentId=%d%s&q=*&limit=%d&offset=%d" % (
            API, "isHighlight=true&" if highlight else "", dept,
            "&medium=" + medium if medium else "", limit, offset)
        if fresh:
            p = os.path.join(CACHE, hashlib.sha1(q.encode()).hexdigest() + ".json")
            if os.path.exists(p):
                os.remove(p)
        j = fetch(q) or {}
        page = j.get("objectIDs") or []
        ids += page
        offset += limit
        if not page or offset >= (j.get("total") or 0) or (not highlight and offset >= 2 * TOPUP_FETCH_CAP):
            break
    return ids


def measured_ratio(o):
    for m in o.get("measurements") or []:
        e = m.get("elementMeasurements") or {}
        if e.get("Width") and e.get("Height"):
            return e["Width"] / e["Height"]
    return None


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def crop_box(img):
    """
    Fractions (left, top, right, bottom) without the photo's border: lines scanned from each edge while they are
    near-uniform and either dark (the black or dark-grey surround of many Met photos) or neutral grey/white close to
    the outermost line's colour (a studio background). Coloured or textured lines (paper, paint) stop the scan.
    At most 25% per side.
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
        sat = max(r, g, b) - min(r, g, b)
        return (r, g, b), m, sd, sat

    def is_border(pixels, ref):
        # Ignore 4% at both ends of the line: the corners are border on both sides and drag the stats.
        n = len(pixels)
        core = pixels[int(n * 0.04): max(int(n * 0.96), int(n * 0.04) + 1)]
        col, m, sd, sat = stats(core)
        if sd > 9:
            return False
        if m < 70:
            return True                                 # dark surround
        if sat < 14 and ref is not None:                # neutral grey or white backdrop, same as the outer line
            return abs(m - ref) < 14
        return False

    def scan(count, horizontal, order):
        ref = None
        cut = 0
        for i in order:
            pixels = line(i, horizontal)
            if ref is None:
                _, m0, sd0, sat0 = stats(pixels)
                ref = m0 if sat0 < 14 else None
            if not is_border(pixels, ref):
                break
            cut += 1
            if cut > count * 0.25:
                return 0                                # a quarter of uniform lines: not a border, leave it
        return cut

    top = scan(h, True, range(h))
    bottom = scan(h, True, range(h - 1, -1, -1))
    left = scan(w, False, range(w))
    right = scan(w, False, range(w - 1, -1, -1))
    # One more line inwards when something was cut: the border's soft edge.
    pad = lambda c: c + 1 if c else 0
    return (pad(left) / w, pad(top) / h, 1 - pad(right) / w, 1 - pad(bottom) / h)


def average_color(img, box):
    w, h = img.size
    c = img.convert("RGB").crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    c.thumbnail((64, 64))
    r, g, b = [int(round(x)) for x in ImageStat.Stat(c).mean[:3]]
    return "#%02x%02x%02x" % (r, g, b)


def short_url(url):
    return url[len(IMG_PREFIX):] if url.startswith(IMG_PREFIX) else url


def examine(oid, cat, highlight, stats):
    o = fetch("%s/v1/objects/%d" % (API, oid))
    if not o or not o.get("isPublicDomain") or not o.get("primaryImage"):
        stats["not_pd"] += 1
        return None
    r = measured_ratio(o)
    if r is not None and r < LANDSCAPE * 0.92:          # a little slack: measurements include mounts and sheets
        stats["portrait"] += 1
        return None
    small_url = o.get("primaryImageSmall") or o["primaryImage"]
    data = fetch(small_url, binary=True)
    if not data:
        stats["no_image"] += 1
        return None
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception:
        stats["no_image"] += 1
        return None
    box = crop_box(img)
    w, h = img.size
    ratio = (box[2] - box[0]) * w / max(1.0, (box[3] - box[1]) * h)
    if ratio < LANDSCAPE:
        stats["portrait"] += 1
        return None
    orig = short_url(o["primaryImage"])
    small = short_url(small_url)
    work = {
        "id": o["objectID"],
        "c": cat,
        "t": (o.get("title") or "").strip(),
        "a": (o.get("artistDisplayName") or o.get("culture") or "").strip(),
        "d": (o.get("objectDate") or "").strip(),
        "m": (o.get("medium") or "").strip(),
        "o": orig,
        "r": round(ratio, 3),
        "col": average_color(img, box),
    }
    if small != orig.replace("/original/", "/web-large/"):
        work["s"] = small
    if any(abs(v - d) > 0.001 for v, d in zip(box, (0, 0, 1, 1))):
        work["k"] = [round(v, 4) for v in box]
    if highlight:
        work["h"] = 1
    stats["kept"] += 1
    return work


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fresh", action="store_true", help="refresh the search results (objects and images stay cached)")
    ap.add_argument("--min", type=int, default=MIN_PER_CATEGORY)
    args = ap.parse_args()

    works, seen = [], set()
    summary = []
    for cat, depts in CATEGORIES:
        stats = {"kept": 0, "not_pd": 0, "portrait": 0, "no_image": 0}
        kept_hl = 0
        for d in depts:
            for oid in search(d, True, args.fresh, MEDIUM.get(cat)):
                if oid in seen:
                    continue
                seen.add(oid)
                w = examine(oid, cat, True, stats)
                if w:
                    works.append(w)
                    kept_hl += 1
            print("  %s dept %d highlights: %s" % (cat, d, stats), file=sys.stderr)
        extra = 0
        if kept_hl < args.min:
            for d in depts:
                looked = 0
                for oid in search(d, False, args.fresh, MEDIUM.get(cat)):
                    if kept_hl + extra >= args.min or extra >= TOPUP_KEEP_CAP or looked >= TOPUP_FETCH_CAP:
                        break
                    if oid in seen:
                        continue
                    seen.add(oid)
                    looked += 1
                    w = examine(oid, cat, False, stats)
                    if w:
                        works.append(w)
                        extra += 1
                print("  %s dept %d top-up: looked at %d, kept %d" % (cat, d, looked, extra), file=sys.stderr)
        summary.append((cat, kept_hl, extra))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = {"v": 1, "img": IMG_PREFIX, "generated": time.strftime("%Y-%m-%d"), "works": works}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print("Wrote %s: %d works (%d KB)" % (os.path.relpath(OUT, ROOT), len(works), os.path.getsize(OUT) // 1024))
    for cat, hl, extra in summary:
        print("  %-3s %4d highlights + %4d others = %4d" % (cat, hl, extra, hl + extra))
    all_count = sum(1 for w in works if w.get("h") and w["c"] != "ph")
    print("  all %4d (highlights, no photography)" % all_count)


if __name__ == "__main__":
    main()
