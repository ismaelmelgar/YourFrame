# YourFrame

An art screensaver for Android TV and Google TV in the style of Samsung's The Frame: public-domain works from
The Metropolitan Museum of Art, on a mat or full bleed, in a frame drawn at the screen's edge, with a newspaper-style
masthead and caption.

- Only landscape works (width / height ≥ 1.2), so they fill a 16:9 screen.
- Categories: All (highlights), European paintings, American paintings, Asian art, Drawings & prints, Islamic art,
  Photography.
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

It asks the Met's collection API (v1.1 search, then `/v1/objects/{id}`) for each department's highlights, tops up with
other works when a category has too few landscape ones, and for each work stores its caption, image URLs, the crop
that removes the black or grey border around many Met photos (as fractions, so it applies to the original), and its
average colour. `tools/make_icons.py` draws the launcher banner and icon.

## Install and activate

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
To try it right away: `adb shell am start -n com.android.systemui/.Somnambulator`, or *Preview* in the app.

## Credits

- Images and data: [The Metropolitan Museum of Art Open Access](https://www.metmuseum.org/about-the-met/policies-and-documents/open-access),
  public domain (CC0). YourFrame is not affiliated with The Met.
- Fonts: Playfair Display and Libre Caslon Text, SIL Open Font License 1.1 (`app/assets/fonts`).
- Built on [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT): `build.sh`, the ADB client and the
  screensaver's structure. See `NOTICE`.
