"""The Cleveland Museum of Art Open Access API: cc0=1 and has_image=1, each work's share_license_status checked
(CC0). No IIIF: the "print" JPEG (about 3400 px) on screen, the "web" one (900 px) for the analysis."""
from . import common, rules

ID = "cma"
NAME = "The Cleveland Museum of Art"
SHORT = "Cleveland"
LICENSE = "Open Access, CC0 1.0"
KIND = "url"
PREFIX = "https://openaccess-cdn.clevelandart.org/"
API = "https://openaccess-api.clevelandart.org/api/artworks/"
TYPES = ("Painting", "Drawing", "Print", "Photograph")
FIELDS = "id,title,creation_date,creators,technique,department,type,images,share_license_status,culture"


def _artist(w):
    for c in w.get("creators") or []:
        d = c.get("description") or ""
        return d.split(" (")[0].strip()
    culture = w.get("culture") or []
    return culture[0] if culture else ""


def candidates(full):
    for kind in TYPES:
        skip = 0
        while True:
            page = common.fetch("%s?cc0=1&has_image=1&type=%s&limit=1000&skip=%d&fields=%s" % (API, kind, skip, FIELDS))
            data = (page or {}).get("data") or []
            for w in data:
                cat = rules.cma_category(kind, w.get("department"))
                if not cat or full(cat) or w.get("share_license_status") != "CC0":
                    continue
                imgs = w.get("images") or {}
                pr, web = imgs.get("print") or {}, imgs.get("web") or {}
                if not pr.get("url") or not web.get("url"):
                    continue
                try:
                    native = (int(pr["width"]), int(pr["height"]))
                except (KeyError, TypeError, ValueError):
                    continue
                if native[0] < native[1] * rules.LANDSCAPE * 0.95:
                    continue
                url = pr["url"]
                yield {"cat": cat, "id": str(w["id"]), "title": w.get("title") or "", "artist": _artist(w),
                       "date": w.get("creation_date") or "", "medium": (w.get("technique") or "").capitalize(),
                       "img": url[len(PREFIX):] if url.startswith(PREFIX) else url, "thumb": web["url"],
                       "native": native}
            if len(data) < 1000:
                break
            skip += 1000
