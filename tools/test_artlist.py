#!/usr/bin/env python3
"""Tests for the catalogue rules (category mapping, quality and landscape filter, duplicates). Exits 1 on failure."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlist import rules  # noqa: E402

checks = 0


def check(ok, what):
    global checks
    checks += 1
    if not ok:
        print("FAIL: " + what)
        sys.exit(1)


def a(ratio=1.5, mean=120, spread=50):
    return {"ratio": ratio, "mean": mean, "spread": spread}


# Category mapping, per museum.
check(rules.met_category(11) == "eu" and rules.met_category(19) == "ph" and rules.met_category(3) is None, "Met")
check(rules.cma_category("Painting", "American Painting and Sculpture") == "am", "CMA American painting")
check(rules.cma_category("Painting", "Modern European Painting and Sculpture") == "eu", "CMA European painting")
check(rules.cma_category("Print", "Japanese Art") == "as", "CMA: a Japanese print is Asian art")
check(rules.cma_category("Painting", "Islamic Art") == "is", "CMA Islamic")
check(rules.cma_category("Drawing", "Drawings") == "pr" and rules.cma_category("Photograph", "Photography") == "ph", "CMA prints, photos")
check(rules.cma_category("Painting", "Egyptian and Ancient Near Eastern Art") is None, "CMA: unmapped departments are left out")
check(rules.smk_category(["Painting"]) == "eu" and rules.smk_category(["Etching", "Print"]) == "pr", "SMK")
check(rules.smk_category(["Statue"]) is None, "SMK: sculpture is left out")
check(rules.nga_category("Painting", "CAB", "American") == "am", "NGA American painting")
check(rules.nga_category("Painting", "CAB", "British") == "eu", "NGA British painting is European")
check(rules.nga_category("Painting", "CIS-R", "Italian") == "eu", "NGA Italian painting")
check(rules.nga_category("Print", "CG-E", "German") == "pr" and rules.nga_category("Photograph", "CPH", "") == "ph", "NGA")
check(rules.nga_category("Index of American Design", "CG-W", "") is None, "NGA: design renderings are left out")
check(set(rules.CATEGORIES) == {"eu", "am", "as", "is", "pr", "ph"}, "the six common categories")

# Quality and landscape.
check(rules.quality(a()) is None, "a good landscape work hangs")
check(rules.quality(a(ratio=1.19)) == "portrait" and rules.quality(a(ratio=1.2)) is None, "landscape from 1.2")
check(rules.quality(a(), (1400, 1000)) == "small" and rules.quality(a(), (1600, 1000)) is None, "1600 px long side")
check(rules.quality(a(), None) is None, "unknown size (the Met) isn't rejected")
check(rules.quality(a(mean=230, spread=15)) == "pale", "a pale sketch on a white sheet")
check(rules.quality(a(mean=120, spread=8)) == "flat", "an almost flat image")
check(rules.quality(a(mean=230, spread=40)) is None, "a light but contrasted drawing hangs")
check(rules.quality(None) == "unreadable", "unreadable image")
check(rules.rejected_by_text("[The Oriel Window]", "Paper negative"), "negatives")
check(rules.rejected_by_text("Fragment of a Carpet", "") and rules.rejected_by_text("Textile fragments", ""), "fragments")
check(not rules.rejected_by_text("The Harvesters", "Oil on wood"), "an ordinary painting")
check(not rules.rejected_by_text("A night scene (recto); Calligraphy (verso)", "Fragment of a handscroll, ink"),
      "a recto with its verso noted, or a medium mentioning a fragment, still hangs")

# Duplicates across museums.
check(rules.dedupe_key("Albrecht Dürer", "Melencolia I", "1514") == rules.dedupe_key("Albrecht Durer", "Melencolia I.", "1514"),
      "same print, two museums, accents and punctuation aside")
check(rules.dedupe_key("Dürer", "Melencolia I", "1514") != rules.dedupe_key("Dürer", "Melencolia I", "1515"), "another date")

print("Artlist rules: %d checks passed" % checks)
