# Developing YourFrame

It's a plain Java app (Android 8+, no libraries, no Gradle), built the same way as
[Free My TV](https://github.com/simbasjungles/free-my-tv).

## Build

Needs a JDK 17 and the Android SDK command-line tools (build-tools 34, platform android-34).

```bash
./build.sh          # → YourFrame.apk, signed with yourframe.keystore (created the first time; keep it, don't commit it)
```

The app's name and package id live at the top of `build.sh` (`APP_NAME`, `APP_ID`) and in
`app/res/values/strings.xml` (`app_name`).

The list of works (`app/assets/artworks.json`) is generated on a computer and bundled in the APK:

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python tools/make_artlist.py      # API responses and images are cached in tools/.cache (ignored by git)
```

One command, reproducible and resumable (a rerun only fetches what isn't cached). Each museum is an adapter in
`tools/artlist/` (`met.py`, `cma.py`, `smk.py`, `nga.py`) that yields its free works mapped to the common categories;
`tools/artlist/rules.py` holds what every work must pass, tested by `tools/test_artlist.py` (run by `build.sh` too):

- public domain or CC0, checked on each work's own licence field;
- landscape (≥ 1.2) after cropping the black or grey border of the museum's photo (the crop is stored as fractions,
  so it applies to the full image), and at least 1600 px on the long side where the museum gives the size;
- not a negative, a fragment or a blank or very pale sheet (measured on a small copy, which also gives the colour);
- not a duplicate of another museum's (same artist, title and date: prints often exist in several impressions).

The list is balanced: at most 450 works per museum and category. Each work stores its museum, caption, picture (a
path under the museum's image server, or its IIIF service with the full width), ratio, crop and colour.
`tools/make_icons.py` draws the launcher banner and icon.

## Architecture

Domain-driven, in four packages under `app/src/io/yourframe/tv`; dependencies point inward:

- `domain`: plain Java, no Android. Artwork, Museum, ImageRef (fixed URL or IIIF), Catalogue, Category, Exhibition (random order without repeating the
  last 50), Frame, Mat, Night, Settings, Layout (the prototype's geometry: mat, window, masthead, caption, full bleed)
  and Rotation (crossfade and caption timing).
- `application`: NextArtwork, which finds the next artwork and its picture (downloaded, or kept on the TV when
  offline). Its only port, `Pictures`, keeps Android out of it.
- `infrastructure`: CatalogueFile (`assets/artworks.json`, every museum), MuseumPictures (IIIF at the screen's width
  or the museum's full image over HTTP, decode, disk cache of 12),
  SettingsStore (SharedPreferences), Adb and ScreensaverActivation (the TV's own ADB).
- `ui`: ArtView (the one canvas), Painter (mat, window, bevel and procedural frames), Fonts, Names.

The museums plug in at the Catalogue: each museum's adapter is in the data tool (`tools/artlist/`) and writes the same
rows (museum, id, category, caption, picture, ratio, crop, colour); the app reads them all with one adapter,
CatalogueFile, and a work's ImageRef says whether its picture is a fixed URL or a IIIF service.

The Android entry points (`ArtDream`, `SettingsActivity`, `PreviewActivity`) stay in `io.yourframe.tv` so the
screensaver's component name, which TVs store in their settings, never changes. `build.sh` compiles `domain` and
`application` without `android.jar` and runs `app/test/.../DomainTest.java` on the JVM before building the APK.

Screenshots from the Google TV 14 emulator are in `docs/screenshots/`.
