"""
The catalogue's rules, shared by every museum: the common categories, the quality filter and duplicates.
Tested by tools/test_artlist.py.
"""
import re
import unicodedata

CATEGORIES = ("eu", "am", "as", "is", "pr", "ph")   # European painting, American painting, Asian art, Islamic art,
                                                   # Drawings & prints, Photography (the app adds "all")
LANDSCAPE = 1.2         # wider than 6:5 (a 16:9 screen is 1.78)
MAX_RATIO = 2.6         # wider still (palm leaves, scroll strips, Muybridge sequences) is a thin band on the mat
MIN_LONG_SIDE = 1600    # the full image must be at least this big on its long side, where the museum says
NEGATIVE = re.compile(r"\b(negatives?|negativ)\b", re.I)
NOT_A_WORK = re.compile(r"\b(fragments?|fragmenter|sample book|swatch)\b", re.I)


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def dedupe_key(artist, title, date):
    """Same artist + title + date across museums (e.g. impressions of one print): kept once."""
    return "|".join((norm(artist), norm(title), norm(date)))


def rejected_by_text(title, medium):
    """Negatives (title or medium) and fragments or sample books (by the title) aren't works to hang."""
    return bool(NEGATIVE.search(title or "") or NEGATIVE.search(medium or "") or NOT_A_WORK.search(title or ""))


def quality(analysis, native=None):
    """
    None when the work may hang, else why not: not landscape after its border is cropped, too small where the museum
    gives the full size, a blank or very pale sheet (little contrast), or an almost flat image.
    """
    if analysis is None:
        return "unreadable"
    if analysis["ratio"] < LANDSCAPE:
        return "portrait"
    if analysis["ratio"] > MAX_RATIO:
        return "strip"
    if native and max(native) < MIN_LONG_SIDE:
        return "small"
    if analysis["spread"] < 12:
        return "flat"
    if analysis["mean"] > 200 and analysis["spread"] < 24:
        return "pale"
    return None


# Category mapping, one function per museum (each museum names departments its own way).

def met_category(department_id):
    return {11: "eu", 15: "eu", 1: "am", 6: "as", 14: "is", 9: "pr", 19: "ph"}.get(department_id)


ASIAN = re.compile(r"chinese|japanese|korean|indian|asian|southeast", re.I)


def cma_category(kind, department):
    d = department or ""
    if "Islamic" in d:
        return "is"
    if ASIAN.search(d):
        return "as"
    if kind == "Photograph":
        return "ph"
    if kind in ("Drawing", "Print"):
        return "pr"
    if kind == "Painting":
        if "American" in d:
            return "am"
        if "European" in d or "Medieval" in d:
            return "eu"
    return None


SMK_PRINTS = {"print", "drawing", "etching", "engraving", "copper engraving", "lithograph", "xylograph", "woodcut",
              "aquatint", "watercolour", "intaglio print", "mezzotint", "chiaroscuro woodcut", "crayon engraving"}


def smk_category(object_names):
    names = {n.lower() for n in object_names}
    if "painting" in names:
        return "eu"     # SMK's paintings are European (mostly Danish and Dutch)
    if names & SMK_PRINTS:
        return "pr"
    if "photograph" in names:
        return "ph"
    return None


def nga_category(classification, department, nationality):
    if classification == "Painting":
        if department in ("CAB", "CMC"):
            return "am" if (nationality or "").startswith("American") else "eu"
        if department.startswith(("CF", "CIS", "CNE")):
            return "eu"
        return None
    if classification in ("Drawing", "Print") and department in ("CG-E", "CG-W"):
        return "pr"
    if classification == "Photograph":
        return "ph"
    return None
