"""SMK, the National Gallery of Denmark (api.smk.dk): public_domain and has_image, each work's rights checked
(Public Domain Mark 1.0). IIIF: the app asks for exactly the screen's size."""
from . import common, rules

ID = "smk"
NAME = "Statens Museum for Kunst"
SHORT = "SMK"
LICENSE = "Public Domain Mark 1.0"
KIND = "iiif"
PREFIX = "https://iip.smk.dk/iiif/jp2/"
API = "https://api.smk.dk/api/v1/art/search/"
FREE = ("https://creativecommons.org/publicdomain/mark/1.0/", "https://creativecommons.org/publicdomain/zero/1.0/")


def _title(it):
    titles = it.get("titles") or []
    for t in titles:
        if t.get("language") == "engelsk":
            return t.get("title") or ""
    return (titles[0].get("title") or "") if titles else ""


def _artist(it):
    for p in it.get("production") or []:
        if p.get("creator_role"):        # "after", "workshop of"...: the original artist comes first
            continue
        fore, sur = p.get("creator_forename"), p.get("creator_surname")
        return ("%s %s" % (fore, sur)).strip() if fore and sur else (p.get("creator") or "")
    return ""


def _date(it):
    for d in it.get("production_date") or []:
        return d.get("period") or ""
    return ""


def candidates(full):
    offset, rows = 0, 1000
    while True:
        page = common.fetch("%s?keys=*&filters=[public_domain:true],[has_image:true],[image_orientation:landscape]"
                            "&offset=%d&rows=%d&lang=en" % (API, offset, rows))
        items = (page or {}).get("items") or []
        for it in items:
            cat = rules.smk_category([n.get("name", "") for n in it.get("object_names") or []])
            base = it.get("image_iiif_id") or ""
            if not cat or full(cat) or not it.get("public_domain") or it.get("rights") not in FREE or not base:
                continue
            try:
                native = (int(it["image_width"]), int(it["image_height"]))
            except (KeyError, TypeError, ValueError):
                continue
            if native[0] < native[1] * rules.LANDSCAPE * 0.95:
                continue
            yield {"cat": cat, "id": it.get("object_number") or it.get("id"), "title": _title(it),
                   "artist": _artist(it), "date": _date(it), "medium": ", ".join(it.get("techniques") or []),
                   "img": base[len(PREFIX):] if base.startswith(PREFIX) else base,
                   "thumb": base + "/full/!400,400/0/default.jpg", "native": native}
        if len(items) < rows:
            break
        offset += rows
