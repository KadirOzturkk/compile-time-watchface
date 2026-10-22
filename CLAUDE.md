# Compile Time

A Wear OS watch face in the Watch Face Format (WFF). The APK is resource-only (`android:hasCode="false"`);
the whole face is one `watchface.xml` that Kotlin code in `buildSrc` writes at build time.

## Commands

```sh
./gradlew assembleRelease                  # both flavors -> watchface/build/outputs/apk/<flavor>/release/
./gradlew generateWearOs6ReleaseWatchFace  # just the XML -> watchface/build/generated/res/<task>/raw/watchface.xml
bash scripts/validate.sh                   # after assembleRelease: WFF schema, Play memory footprint, no dex
ANDROID_SERIAL=emulator-5554 bash scripts/emulator-smoke.sh <apk> <screenshot-dir>
```

Run `./gradlew clean` after touching flavors or variants; stale APKs from removed variants stay in `build/`.
`validate.sh` builds Google's validator jars from the commit pinned in the script into `build/wff-tools/<commit>/`
on first run (about a minute). Google's release downloads are rebuilt in place, so they cannot be pinned.

## Layout

| Path | What |
|---|---|
| `buildSrc/src/main/kotlin/GenerateWatchFace.kt` | Gradle task: writes `raw/watchface.xml` for one format version |
| `buildSrc/src/main/kotlin/watchface/WatchFaceDocument.kt` | Document root: live and ambient layers, ticks, state conditions, ambient fades |
| `.../watchface/States.kt` | The nine states, their texts and scene rotation, and `STATE_CODE` that picks one |
| `.../watchface/Scenes.kt` | The laptop scene and the idle scenes (coffee, rubber duck, pondering, stroll) with ambient twins |
| `.../watchface/Clawd.kt` | Pixel art: sprite poses, laptop eyes, spark, heart, props |
| `.../watchface/Markup.kt` | XML helpers; `Palette.kt` holds colours, fonts and data-source expressions |
| `watchface/build.gradle.kts` | Flavors, signing, and the `androidComponents` hook that adds the generated res dir |
| `watchface/src/main/` | Manifest, fonts, strings, picker preview, `watch_face_info.xml` |
| `scripts/` | `validate.sh` (Google's validators), `emulator-smoke.sh` (install, activate, screenshot) |
| `docs/images/` | README media, captured on emulators |

## Flavors

| Flavor | WFF | minSdk | versionCode | Difference |
|---|---|---|---|---|
| `wearOs6` | 4 | 36 | `release * 10 + 6` | Ambient mode cross-fades (Variant `duration`/`startOffset`/`interpolation`) |
| `wearOs4` | 1 | 33 | `release * 10 + 4` | Instant ambient switch; the whole live layer also hides with one parent Variant |

The format version comes from the flavor's `wffVersion` manifest placeholder; the generator reads the same
placeholder, so it is set in one place. One APK cannot serve both: a Wear OS 4 runtime rejects a face that
declares version 4 and shows its fallback face, even with version-qualified `raw-v36/` resources. Bump `release`
and `versionName` in `watchface/build.gradle.kts` for every published version; the release workflow refuses a tag
that does not match `versionName`.

## Hazards

- `wff-validator.jar` exits 0 on an invalid file unless it gets `--stop-on-fail`.
- Expressions are not schema-checked: the XSD types them as free strings, so a typo passes validation and fails
  only on a watch. Smoke-test on an emulator after changing one.
- Group, PartDraw and PartText names must be unique. The Wear OS 4 runtime silently drops a whole Condition branch
  that repeats a name used by a sibling branch (seen with the work state's two laptop slots).
- Wear OS 4 and Samsung (Galaxy Watch7, Wear OS 6) do not apply the ambient Variant to elements a Condition
  creates while ambient, so they showed at full brightness in always-on mode. The `states` group around the
  Conditions carries its own ambient fade for that. Google's runtime on Wear OS 6 and 7 handles it either way.
- A runtime draws a new Condition branch's first frame before applying any alpha of that branch (attribute,
  Transform or Variant), so a fade inside the branch flashes. Samsung's runtime also ignores a Transform on a Group
  when it uses `[SECOND_MILLISECOND]` (a `[SECOND]` one works). Hence the minute switch is hidden by
  `minute_curtain`, one long-lived black PartDraw above the Conditions whose alpha ramps around the minute.
- A Transform and a Variant on the same attribute of one element conflict; keep them on different elements.
- On the Wear OS 4 runtime a recreated branch keeps its previous scene until the next minute tick, and after many
  backwards clock jumps a scene can stay empty until the face reloads. Neither shows on a real clock; when
  capturing, reinstall the face and let minutes tick naturally.
- The runtime logs `DWF:StringUtil: elements(0) and specifiers(n) have different size` once when a branch is
  created; it is transient. `DWF:ResourceManager ... Path is empty or null` appears for any face with a
  ColorConfiguration. Both are noise.
- `isShrinkResources` must stay false: fonts and drawables referenced only from `watchface.xml` look unused.
  R8 (`isMinifyEnabled`) stays on in release so the APK carries no dex; debug builds keep a harmless dex.
- Release builds shorten resource paths (`res/li.xml`); read the face XML from `build/generated`, not the APK.
- `ACCENT_OPTIONS` names need matching `accent_<name>` strings, and `STATES` order must match `STATE_CODE`.
- The runtime keeps a user's accent across `adb install -r`; uninstall to see a new `defaultValue`.

## Emulators

- Use one adb (`/opt/homebrew/bin/adb`); mixing it with the SDK's adb dropped a device.
- Launch AVDs from bash, not zsh: zsh does not word-split `$args`, which broke earlier launch commands.
- Google's runtimes (Wear OS 6 and 7) deliver no heart rate, so the face shows `-- bpm`. The Wear OS 4 emulator
  feeds a synthetic heart rate that climbs from 60 to 150 bpm in 5 bpm steps per second, then wraps; `adb root` works there,
  so `adb shell date MMDDhhmmYYYY.ss` sets the clock for testing states.
- Emulators cover Google's runtime only. Samsung's runtime needs a Galaxy Watch.
- A Galaxy Watch off its charger suspends a second after the screen goes dark, and wireless adb times out about
  15 seconds later; on the charger, stay-awake keeps it up, but always-on mode shows the charging screen instead.
  Its always-on frames come back black from `screencap` (the display is offloaded), so check those by eye.

## Conventions

- Commits: conventional subject line only, no body, no `Co-Authored-By`.
- Comments: one line, and only for a hazard or a contract the code cannot show. No XML comments in resources.
- README voice: warm and a little playful, built around Clawd; emoji only where they mean something, no
  marketing words, nothing that reads as generated.
- Never commit `keystore.properties`, `*.jks` or `local.properties` (all gitignored).

## Releasing

Push a `v*` tag. `.github/workflows/release.yml` restores the signing key from the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets, builds, validates and publishes both APKs plus
`SHA256SUMS`, with the certificate fingerprint in the notes. Every release must use the same key, or installed
copies can no longer update.
