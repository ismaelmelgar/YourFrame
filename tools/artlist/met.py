"""The Metropolitan Museum of Art: v1.1 search per department (highlights first, then the rest, in a fixed random
order), /v1/objects/{id} for each; isPublicDomain with a primaryImage only (Open Access, CC0). Fixed image URLs
(the original; web-large for the analysis), no IIIF."""
import random

from . import common, rules

ID = "met"
NAME = "The Metropolitan Museum of Art"
SHORT = "The Met"
LICENSE = "Open Access, CC0 1.0"
KIND = "url"
PREFIX = "https://images.metmuseum.org/CRDImages/"
API = "https://collectionapi.metmuseum.org/public/collection"
# Category -> (department, medium filter). The American Wing is mostly furniture and silver: its paintings only.
SEARCHES = {"eu": [(11, None), (15, "Paintings")], "am": [(1, "Paintings")], "as": [(6, None)],
            "is": [(14, None)], "pr": [(9, None)], "ph": [(19, None)]}
MAX_IDS = 8000          # per search: Drawings and Prints alone has 100,000 objects with images
MAX_OBJECTS = 3000      # objects looked up per category: most Met photographs aren't public domain


def _search(dept, medium, highlight):
    ids, offset, limit = [], 0, 500
    while offset < MAX_IDS:
        q = "%s/v1.1/search?%shasImages=true&departmentId=%d%s&q=*&limit=%d&offset=%d" % (
            API, "isHighlight=true&" if highlight else "", dept, "&medium=" + medium if medium else "", limit, offset)
        page = (common.fetch(q) or {}).get("objectIDs") or []
        ids += page
        offset += limit
        if len(page) < limit:
            break
    return ids


def _measured_ratio(o):
    for m in o.get("measurements") or []:
        e = m.get("elementMeasurements") or {}
        if e.get("Width") and e.get("Height"):
            return e["Width"] / e["Height"]
    return None


def candidates(full):
    for cat, searches in SEARCHES.items():
        ids, seen = [], set()
        for dept, medium in searches:
            hl = _search(dept, medium, True)
            rest = _search(dept, medium, False)
            random.Random(dept).shuffle(rest)
            for i in hl + rest:
                if i not in seen:
                    seen.add(i)
                    ids.append(i)
        for n, oid in enumerate(ids):
            if full(cat) or n >= MAX_OBJECTS:
                break
            o = common.fetch("%s/v1/objects/%d" % (API, oid))
            if not o or not o.get("isPublicDomain") or not o.get("primaryImage"):
                continue
            r = _measured_ratio(o)
            if r is not None and r < rules.LANDSCAPE * 0.92:    # measurements include mounts: a little slack
                continue
            orig = o["primaryImage"]
            yield {"cat": cat, "id": str(o["objectID"]), "title": o.get("title") or "",
                   "artist": o.get("artistDisplayName") or o.get("culture") or "", "date": o.get("objectDate") or "",
                   "medium": o.get("medium") or "", "img": orig[len(PREFIX):] if orig.startswith(PREFIX) else orig,
                   "thumb": o.get("primaryImageSmall") or orig, "native": None}
