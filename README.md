# YourFrame

An art screensaver for Android TV and Google TV in the style of Samsung's The Frame: public-domain works from four
museums (The Metropolitan Museum of Art, the Cleveland Museum of Art, Statens Museum for Kunst and the National
Gallery of Art, Washington), on a mat or full bleed, in a frame drawn at the screen's edge, with a newspaper-style
masthead and caption.

- Only landscape works (width / height ≥ 1.2), so they fill a 16:9 screen.
- Categories, the same across museums: All (everything but photography), European paintings, American paintings,
  Asian art, Drawings & prints, Islamic art, Photography. Museums: each one on or off (one always stays on).
- IIIF where the museum has it (SMK, NGA): the TV asks for exactly its screen's width, crisp at 1080p.
- Frames: full bleed, mat only, thin black, white, oak, walnut, gold. Mats: paper, ivory, stone, sage, navy, black.
- A slow 2 s crossfade every 30 s, 1, 5 or 15 min; the caption leaves 10 s after a work arrives; dimmer at night.
- Type: Playfair Display and Libre Caslon Text (bundled, SIL OFL).
- Works offline: the last 12 works are kept on the TV.
- English and Spanish, following the TV's language.

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

## Install and activate

Download `YourFrame.apk` from the [latest release](https://github.com/ismaelmelgar/YourFrame/releases/latest)
(or build it, below), then:

```bash
adb connect <TV-IP>:5555          # accept "Allow USB debugging?" on the TV
adb install -r YourFrame.apk
```

Open **YourFrame** from the apps row to choose the category, frame, mat and the rest; the preview on the right shows
each change. Then **Set as screensaver**:

- **Android TV** (e.g. Mi Box, Shield): it opens the system's screensaver settings; pick YourFrame there.
- **Google TV** (Chromecast with Google TV, Google TV Streamer, TVs with Google TV): Google TV doesn't list
  third-party screensavers. YourFrame sets itself through the TV's own debugging connection, the way Free My TV does:
  turn on *Developer options → USB debugging* (Settings → System → About → tap *Android TV OS build* 7 times), then
  *Set as screensaver*; the TV asks once *Allow USB debugging?* (tick *Always allow*). From a computer the same thing is

  ```bash
  adb shell settings put secure screensaver_components io.yourframe.tv/.ArtDream
  ```

The TV still decides when the screensaver starts and when the screen turns off; YourFrame doesn't change those times.
To try it right away, use *Preview* in the app (on classic Android TV also
`adb shell am start -n com.android.systemui/.Somnambulator`; Google TV doesn't have it).

## Credits

Images and data, all public domain; YourFrame isn't affiliated with or endorsed by any of these museums, and every
caption names the work's museum:

- [The Metropolitan Museum of Art](https://www.metmuseum.org/about-the-met/policies-and-documents/open-access): Open
  Access, CC0 1.0 (works with `isPublicDomain`).
- [The Cleveland Museum of Art](https://www.clevelandart.org/open-access): Open Access, CC0 1.0 (works whose
  `share_license_status` is CC0).
- [Statens Museum for Kunst, National Gallery of Denmark](https://www.smk.dk/en/article/smk-api/): public domain works,
  Public Domain Mark 1.0; images through SMK's IIIF server.
- [National Gallery of Art, Washington](https://github.com/NationalGalleryOfArt/opendata): open data CC0; open access
  images (`openaccess = 1`) CC0, through NGA's IIIF server.
- Fonts: Playfair Display and Libre Caslon Text, SIL Open Font License 1.1 (`app/assets/fonts`).
- Built on [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT): `build.sh`, the ADB client and the
  screensaver's structure. See `NOTICE`.

## License

MIT (`LICENSE`). Third-party code, fonts and images keep their own terms: see Credits and `NOTICE`.
