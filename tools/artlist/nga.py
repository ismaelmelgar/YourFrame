"""National Gallery of Art, Washington: its open data on GitHub (NationalGalleryOfArt/opendata, CC0), primary
images with openaccess=1 only. IIIF: the app asks for exactly the screen's size."""
import csv
import os
import random
import urllib.request

from . import common, rules

ID = "nga"
NAME = "National Gallery of Art, Washington"
SHORT = "NGA"
LICENSE = "Open Access, CC0 1.0"
KIND = "iiif"
PREFIX = "https://api.nga.gov/iiif/"
DATA = "https://raw.githubusercontent.com/NationalGalleryOfArt/opendata/main/data/"
FILES = ("objects.csv", "published_images.csv", "objects_constituents.csv", "constituents.csv")


def _table(name):
    d = os.path.join(common.CACHE, "nga")
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, name)
    if not os.path.exists(path):
        req = urllib.request.Request(DATA + name, headers={"User-Agent": common.UA})
        with urllib.request.urlopen(req, timeout=600) as r, open(path + ".tmp", "wb") as f:
            f.write(r.read())
        os.replace(path + ".tmp", path)
    csv.field_size_limit(1 << 24)
    return csv.DictReader(open(path, encoding="utf-8", newline=""))


def candidates(full):
    images = {}
    for r in _table("published_images.csv"):
        if r["viewtype"] == "primary" and r["openaccess"] == "1":
            images[r["depictstmsobjectid"]] = r
    nationality = {r["constituentid"]: r["nationality"] for r in _table("constituents.csv")}
    artist_of = {}
    for r in _table("objects_constituents.csv"):
        if r["roletype"] == "artist" and r["objectid"] not in artist_of:
            artist_of[r["objectid"]] = nationality.get(r["constituentid"], "")
    objects = [r for r in _table("objects.csv") if r["objectid"] in images]
    random.Random(7).shuffle(objects)
    for o in objects:
        im = images[o["objectid"]]
        cat = rules.nga_category(o["classification"], o["departmentabbr"], artist_of.get(o["objectid"]))
        if not cat or full(cat):
            continue
        try:
            native = (int(im["width"]), int(im["height"]))
        except ValueError:
            continue
        if native[0] < native[1] * rules.LANDSCAPE * 0.95:
            continue
        base = im["iiifurl"]
        yield {"cat": cat, "id": o["objectid"], "title": o["title"], "artist": o["attribution"],
               "date": o["displaydate"], "medium": o["medium"],
               "img": base[len(PREFIX):] if base.startswith(PREFIX) else base,
               "thumb": base + "/full/!400,400/0/default.jpg", "native": native}
