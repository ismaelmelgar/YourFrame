#!/usr/bin/env python3
"""
Builds app/assets/artworks.json, YourFrame's catalogue: public-domain, landscape works from several museums' open
collections, in common categories, balanced per museum and category. One command, reproducible and resumable
(every API response and analysed image is cached in tools/.cache, so a rerun only fetches what's missing):

    .venv/bin/python tools/make_artlist.py                 # every museum
    .venv/bin/python tools/make_artlist.py --museums nga   # some (the others are left out of the output)

Each museum is an adapter in tools/artlist/ (met, cma, smk, nga) that yields its free works with the common
category; the rules every work must pass (landscape after cropping the photo's border, big enough, not a negative,
fragment or blank sheet, not a duplicate of another museum's) are in tools/artlist/rules.py, tested by
tools/test_artlist.py. Needs Python 3 and Pillow (python3 -m venv .venv && .venv/bin/pip install Pillow).
"""
import argparse
import collections
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlist import cma, common, met, nga, rules, smk  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "assets", "artworks.json")
# IIIF museums first: a work held by two museums is kept from the one that serves it at the screen's size.
MUSEUMS = [nga, smk, cma, met]
CELL_CAP = 450      # works kept per museum and category
CELL_LOOKS = 2500   # small images analysed per museum and category, at most (bounds the run)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--museums", default=",".join(m.ID for m in MUSEUMS))
    ap.add_argument("--cap", type=int, default=CELL_CAP)
    args = ap.parse_args()
    chosen = [m for m in MUSEUMS if m.ID in args.museums.split(",")]

    works, seen = [], set()
    report = collections.OrderedDict()
    for index, museum in enumerate(chosen):
        kept, looked, why = collections.Counter(), collections.Counter(), collections.Counter()
        full = lambda cat: kept[cat] >= args.cap or looked[cat] >= CELL_LOOKS
        started = time.time()
        for c in museum.candidates(full):
            cat = c["cat"]
            if full(cat):
                continue
            if rules.rejected_by_text(c["title"], c["medium"]):
                why["text"] += 1
                continue
            key = rules.dedupe_key(c["artist"], c["title"], c["date"])
            if key in seen:
                why["duplicate"] += 1
                continue
            if c["native"] and max(c["native"]) < rules.MIN_LONG_SIDE:
                why["small"] += 1
                continue
            data = common.fetch(c["thumb"], binary=True)
            looked[cat] += 1
            a = common.analyse(data) if data else None
            reason = rules.quality(a, c["native"])
            if reason:
                why[reason] += 1
                continue
            seen.add(key)
            kept[cat] += 1
            box = [round(v, 4) for v in a["box"]]
            crop = box if any(abs(v - d) > 0.001 for v, d in zip(box, (0, 0, 1, 1))) else 0
            width = c["native"][0] if c["native"] else 0
            works.append([index, c["id"], cat, c["title"].strip(), c["artist"].strip(), c["date"].strip(),
                          c["medium"].strip(), round(a["ratio"], 3), c["img"], width, crop, a["color"][1:]])
            if sum(kept.values()) % 100 == 0:
                print("  %s: %d kept (%s) %.0f s" % (museum.ID, sum(kept.values()), dict(kept), time.time() - started),
                      file=sys.stderr, flush=True)
        report[museum.ID] = (kept, why)
        print("%s: kept %s; left out %s" % (museum.ID, dict(kept), dict(why)), file=sys.stderr, flush=True)

    doc = {"v": 2, "generated": time.strftime("%Y-%m-%d"),
           "museums": [{"id": m.ID, "name": m.NAME, "short": m.SHORT, "license": m.LICENSE, "kind": m.KIND, "prefix": m.PREFIX}
                       for m in chosen],
           # [museum, id, category, title, artist, date, medium, ratio, image, width, crop, colour]
           "works": works}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print("Wrote %s: %d works (%d KB)" % (os.path.relpath(OUT, ROOT), len(works), os.path.getsize(OUT) // 1024))
    cats = collections.Counter()
    for m, (kept, _) in report.items():
        cats.update(kept)
        print("  %-4s %5d  %s" % (m, sum(kept.values()), " ".join("%s=%d" % kv for kv in sorted(kept.items()))))
    print("  per category: " + " ".join("%s=%d" % (c, cats[c]) for c in rules.CATEGORIES))


if __name__ == "__main__":
    main()
