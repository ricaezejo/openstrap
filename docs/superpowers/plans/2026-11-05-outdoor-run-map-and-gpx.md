# Outdoor Run: Full-Screen Map, Km Markers, Split Highlight and GPX Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a finished GPS workout's Route card, open the map full screen with a numbered marker at each kilometre and a split picker that highlights one kilometre on the map, and export the run as a GPX file.

**Architecture:** All of it hangs off the existing `RouteCard` (`ui/glow/SummarySections.kt`), which the just-finished summary and a past workout's page both render. The full-screen map is a `Dialog` hosting the existing `WorkoutMap`, so no navigation changes. Km markers are bitmaps drawn with `android.graphics` and added to the map style as images, so they need no font download and work in route-only mode with no network. Split highlight reuses the existing split rows (`SummaryCopy.splitRows`) and a new `Route.legs` that returns each kilometre's points. GPX is a pure-Kotlin writer over the already cleaned `Route.track`, handed to the system share sheet through the existing `share/` FileProvider path. Two stacked branches, made with git-spice.

**Tech Stack:** Kotlin, Jetpack Compose, MapLibre (`WorkoutMap`), Room entities already in place, JUnit4 + Robolectric (`ComposeRules`).

**Spec:** `docs/superpowers/specs/2026-11-05-outdoor-run-map-and-gpx-design.md`. Strava upload and a Health Connect `ExerciseRoute` are out of scope for now (see the end).

## Global Constraints

- User-facing text goes in `app/src/main/res/values/strings_glow_summary.xml`, never in Kotlin literals (`i18n/HardcodedEnglishTest`). Each string has an XML comment saying where it shows.
- `i18n/StringsParityTest` must stay green; CI also runs `./gradlew :app:testDebugUnitTest -PtestLocale=de-DE`.
- No new network use: export is a local file handed to the share sheet; `docs/privacy.md` says routes never leave the phone.
- No new permissions and no new FileProvider paths: `res/xml/share_paths.xml` already exposes `cache/share/`.
- Commit messages are `type: summary` with no ticket key. End each with the Co-Authored-By line from the session.
- Branches: git-spice only (`gs branch create`, `gs stack submit` as drafts). No worktrees, no plain `git checkout -b`.
- Verification commands: `./gradlew :app:testDebugUnitTest --tests '<class>'`; before submitting also `./gradlew :app:lintDebug detektMain ktlintCheck`.

## Review Focus

- A run under 1 km has no km markers, and its "Last N m" chip is still selectable and highlights the whole route (Task 2).
- A run with pauses or a tunnel gap exports as separate `<trkseg>`s, not one line across the gap (Task 3).
- A device in a decimal-comma locale (de-DE) still writes `51.5000000`, not `51,5000000` (Task 3).
- Rotating the phone or process death with the full-screen map open keeps it open, with the same route-only choice and the same selected split (Tasks 1 and 2).
- Switching Route only on and off with a split selected keeps the km markers and the highlight, since the new style drops the images added to the old one (Task 2).
- A workout name with `&`, `<`, `"` produces well-formed XML, and no heart rate or altitude still gives a valid GPX (Task 3, tested there).

---

## Branch 1: `feat/fullscreen-route-map`

Start with `gs branch create feat/fullscreen-route-map` from `main`.

### Task 1: Full-screen route map

**Files:**
- Create: `app/src/main/kotlin/org/openstrap/app/ui/glow/FullScreenRouteMap.kt`
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowWorkoutSummary.kt:328-339` (`RouteMap`)
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/SummarySections.kt:268-277` (`RouteCard`)
- Modify: `app/src/main/res/values/strings_glow_summary.xml` (after `glow_summary_climb`)
- Test: `app/src/test/kotlin/org/openstrap/app/ui/glow/FullScreenRouteMapTest.kt`

**Interfaces:**
- Consumes: `WorkoutMap(stretches, routeOnly, modifier, here, fitPadding, creditBottom, hereColor, follow, onPanned)` (`ui/glow/WorkoutMap.kt`); `Route.byZone(points, hr, maxHr, zones)`; `LocalHeartZones`; `IconPill(label, onClick, modifier, icon, ...)` (`PageParts.kt:690`); `PhoneSummary`.
- Produces:
  - `@Composable internal fun rememberZoneStretches(summary: PhoneSummary): List<Pair<List<Route.Point>, Int>>`, extracted from `RouteMap` so both maps colour the line the same way.
  - `@Composable internal fun FullScreenRouteMap(summary: PhoneSummary, onClose: () -> Unit)`: a `Dialog` (`usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false`, `onDismissRequest = onClose`) around the content below.
  - `@Composable internal fun FullScreenRouteMapContent(summary: PhoneSummary, onClose: () -> Unit)`: the map and its pills, without the `Dialog`, so tests need no window.
  - `RouteMap(summary, modifier, onOpen: (() -> Unit)? = null)`: the card's map becomes tappable when `onOpen` is given.

- [ ] **Step 1: Write the failing test** in `FullScreenRouteMapTest` (Robolectric, `ComposeRules`, content wrapped in `CompositionLocalProvider(LocalInspectionMode provides true)` so `WorkoutMap` draws its black stand-in; see `LiveLandscapeLayoutTest` for the rule and `@Config`). Build a `PhoneSummary` with a mappable route (the `appWorkout` helper in `org.openstrap.app.testing`; a run east of 40 points as in `RouteTest.run`). Tests, with strings read from `R.string`:
  - `closing calls onClose`: click the node with text `R.string.glow_summary_map_close`; assert a counter is 1.
  - `route only can be switched on and back`: click `glow_summary_map_route_only`, assert the node with text `glow_summary_map_show_map` exists, click it, assert the first label is back.
  - `fit route shows only after the map was panned`: assert `glow_summary_map_fit` does not exist initially. (Panning cannot be driven in inspection mode; this pins the initial state only.)
  - `the summary's route card map opens it`: render `RouteMap(summary, onOpen = { opened++ })`, click the node with content description `glow_summary_map_open`, assert `opened == 1`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.ui.glow.FullScreenRouteMapTest'`
Expected: FAIL, compilation error (`FullScreenRouteMapContent` and the strings undefined).

- [ ] **Step 3: Add the strings** to `strings_glow_summary.xml`, each with a comment: `glow_summary_map_open` ("Open map full screen", content description of the card's map), `glow_summary_map_close` ("Close"), `glow_summary_map_route_only` ("Route only", same wording as `workout_live_map_route_only`), `glow_summary_map_show_map` ("Show map"), `glow_summary_map_fit` ("Fit route").

- [ ] **Step 4: Implement `rememberZoneStretches`, `FullScreenRouteMapContent` and `FullScreenRouteMap`** in `FullScreenRouteMap.kt`. Content is a black `Box(fillMaxSize)` with `WorkoutMap(stretches, routeOnly, Modifier.fillMaxSize(), follow = follow, fitPadding = PaddingValues(horizontal = 24.dp, vertical = 96.dp), onPanned = { follow = false })`. `routeOnly` is `rememberSaveable` (default false, matching the card). `follow` is `remember` (true). Pills, inside `WindowInsets.safeDrawing` padding: Close top-end, Route only / Show map top-start, and Fit route top-centre only while `!follow` (tap sets `follow = true`). The bottom edge is left free for Task 2's split picker. Reuse `IconPill`; no new styling.

- [ ] **Step 5: Wire the card.** `RouteMap` takes `onOpen`; when non-null its `Box` gets `Modifier.clickable(onClickLabel = open label, onClick = onOpen)` and a content description from `glow_summary_map_open`. `RouteCard` holds `var full by rememberSaveable { mutableStateOf(false) }`, passes `onOpen = { full = true }`, and shows `FullScreenRouteMap(summary) { full = false }` while `full`. `RouteMap`'s other caller keeps passing no `onOpen`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.ui.glow.FullScreenRouteMapTest' --tests 'org.openstrap.app.i18n.*'`
Expected: PASS.

- [ ] **Step 7: Check on a device.** Per `CLAUDE.md`, check no workout is recording first. Install the debug build, open a past GPS run, tap the route card map. Expect: full screen with the route fitted, drag to pan makes Fit route appear and tapping it refits, Route only shows a plain black map, system Back closes, rotating keeps it open.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: open the route map full screen from a workout's summary"
```

### Task 2: Km markers and split highlight

Still on `feat/fullscreen-route-map`. Splits are per kilometre everywhere in the app (`Km 3`), not per mile, so markers and the picker are too.

**Files:**
- Modify: `app/src/main/kotlin/org/openstrap/app/data/Route.kt` (`splits`, around line 305)
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/WorkoutMap.kt`
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowWorkoutSummary.kt:398-410` (`SplitsList`)
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/FullScreenRouteMap.kt` (from Task 1)
- Create: `app/src/main/kotlin/org/openstrap/app/ui/glow/KmMarkers.kt`
- Create: `app/src/main/kotlin/org/openstrap/app/ui/glow/SplitPicker.kt`
- Test: `app/src/test/kotlin/org/openstrap/app/data/RouteTest.kt` (add), `app/src/test/kotlin/org/openstrap/app/ui/glow/KmMarkersTest.kt`, `app/src/test/kotlin/org/openstrap/app/ui/glow/FullScreenRouteMapTest.kt` (add)

**Interfaces:**
- Consumes: `Route.splits`, `Route.Split(index, endT, seconds, meters)`, `SummaryCopy.splitRows(splits, readings, firstT, totalMeters): List<SplitRow>` (`SplitRow(name, time, averageBpm, seconds, kind)`), `workout_summary_split_km` and `workout_summary_split_last` strings, Task 1's `FullScreenRouteMapContent`.
- Produces:
  - `Route.Leg(val split: Split, val points: List<Point>)` and `Route.legs(points: List<Point>, pauses: List<LongRange> = emptyList(), km: Double = 1000.0): List<Leg>`: the same kilometres as `splits`, each with the smoothed track points it covers, a leg's first point being the previous leg's last. `splits` becomes `legs(...).map { it.split }`, so the two cannot disagree.
  - `internal fun splitRowsFor(summary: PhoneSummary, route: List<Route.Point>): List<SplitRow>` in `GlowWorkoutSummary.kt`, extracted from `SplitsList` (the `SummaryCopy.splitRows(Route.splits(route, summary.pauses), summary.history, route.firstOrNull()?.t, summary.distanceMeters?.takeIf { !summary.distanceFromSteps })` call) so the list and the picker build identical rows. Row `i` is leg `i`.
  - `internal fun kmMarkerFeatures(markers: List<Pair<Int, Route.Point>>): String`: GeoJSON FeatureCollection, one Point feature per marker (lon, lat order), property `icon` = `"km-<n>"`.
  - `internal fun kmMarkerBitmap(km: Int, density: Float): Bitmap`: a 22 dp circle, dark fill (`0xFF121316`, as the start dot), white 2 dp ring, the number centred in bold white, text scaled down so 3 digits fit.
  - `internal fun highlightGeoJson(points: List<Route.Point>): String`: an empty FeatureCollection under 2 points, else one LineString feature.
  - `WorkoutMap(..., kmMarkers: List<Pair<Int, Route.Point>> = emptyList(), highlight: List<Route.Point> = emptyList())`: both default to nothing, so the live map is unchanged.
  - `@Composable internal fun SplitPicker(rows: List<SplitRow>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier)`: a `LazyRow` of chips, name over time, the selected one lit white; each chip has `Modifier.semantics { selected = ... }` and `selectable`-style click (tap an unselected chip selects it, tap the selected one clears it).

- [ ] **Step 1: Write the failing tests.**
  - In `RouteTest` (helpers `east`, `run` already there): `legs cut the track at each kilometre` with `run(1200)` (about 3.6 km at 3 m/s): 4 legs; legs 0 to 2 each measure 1000 m ± 15 m summed with `Route.meters`; `legs[i].points.last() == legs[i + 1].points.first()`. `legs and splits agree`: `Route.legs(run(1200)).map { it.split } == Route.splits(run(1200))`. `a run under a kilometre is one part leg`: `Route.legs(run(100))` has one leg with `split.meters < 1000.0`. `a pause stays out of a leg's metres`: pauses `500L..600L` on `run(1200)`: the leg containing it measures 1000 m ± 15 m of unpaused track and the whole-run total is about 100 s × 3 m/s shorter than the unpaused run's.
  - `KmMarkersTest` (Robolectric, `@GraphicsMode(NATIVE)`): `features carry the km as an icon name` (markers `1 to p1, 2 to p2` parse with `org.json.JSONObject`: 2 features, `properties.icon` is `km-1` and `km-2`, `coordinates[0]` is the lon); `a marker bitmap is square, sized by density, and drawn` (`kmMarkerBitmap(7, 2f)`: width equals height equals 44, and the centre pixel is not fully transparent); `three digits still fit` (`kmMarkerBitmap(123, 2f)` does not throw and is the same size); `highlight needs two points` (`highlightGeoJson(emptyList())` has no features, a two-point list has one feature of type `LineString`).
  - In `FullScreenRouteMapTest`: `the picker lists each kilometre and the last part` (route of 3.5 km: nodes with text `getString(R.string.workout_summary_split_km, 1)` to `3` exist, so does a node starting with the `workout_summary_split_last` text prefix); `tapping a split selects it and tapping it again clears it` (`assertIsSelected()` after the first click on the "Km 2" chip's node, `assertIsNotSelected()` after the second); `a run under a kilometre has no km chips` (route of 400 m: no node with the `workout_summary_split_km` text for 1).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.data.RouteTest' --tests 'org.openstrap.app.ui.glow.KmMarkersTest' --tests 'org.openstrap.app.ui.glow.FullScreenRouteMapTest'`
Expected: FAIL, compilation errors (`Route.legs`, `kmMarkerFeatures` and friends undefined).

- [ ] **Step 3: Implement `Route.Leg` and `Route.legs`**, moving the body of `splits` into it (the loop over `track(points).zipWithNext()`, skipping paused pairs, closing a leg at `meters >= km`); keep a `current` list of points, seed it with the leg's start point, add each unpaused `b`, and start the next leg's list from the closing point. Make `splits` delegate. Run `RouteTest` alone: the existing splits tests must still pass unchanged.

- [ ] **Step 4: Implement `splitRowsFor`** and use it in `SplitsList` (behaviour unchanged; `SplitsListTest` or the summary tests that exist must stay green).

- [ ] **Step 5: Implement `KmMarkers.kt`** (`kmMarkerFeatures`, `kmMarkerBitmap` with `Canvas`, `Paint(ANTI_ALIAS_FLAG)`, `Paint.Align.CENTER`) and `highlightGeoJson` (next to the existing private `geoJson` in `WorkoutMap.kt`, reusing `coord`).

- [ ] **Step 6: Extend `WorkoutMap`.** In the style-ready callback add a `GeoJsonSource` `KM_SRC` with a `SymbolLayer` `KM_LAYER` (`iconImage(Expression.get("icon"))`, `iconAllowOverlap(true)`, `iconIgnorePlacement(true)`), and a `GeoJsonSource` `HIGHLIGHT_SRC` with a white `LineLayer` (width 10, round caps) added with `addLayerBelow(..., LINE)` so the zone-coloured line sits on top of a white casing. A `LaunchedEffect(style, kmMarkers)` does `style.addImage("km-$n", kmMarkerBitmap(n, density))` for each marker then `setGeoJson(kmMarkerFeatures(kmMarkers))`; it must run again whenever `style` changes, because switching Route only calls `setStyle` and the new style has none of the old images. A `LaunchedEffect(style, highlight)` sets `highlightGeoJson(highlight)`. In the existing camera effect: when `follow` and `highlight.size >= 2`, fit the highlight's bounds (same padding) instead of the whole route.

- [ ] **Step 7: Implement `SplitPicker`** (`SplitPicker.kt`). Chip label is `row.name`, second line `row.time`; reuse `GlowType` text styles and the pill colours of `IconPill`; no new theme values.

- [ ] **Step 8: Wire `FullScreenRouteMapContent`.** `val legs = remember(summary) { Route.legs(summary.route, summary.pauses) }`, `val rows = remember(summary) { splitRowsFor(summary, summary.route) }`, `var selected by rememberSaveable { mutableStateOf<Int?>(null) }`. Pass `kmMarkers = legs.filter { it.split.meters >= 1000.0 }.map { it.split.index + 1 to it.points.last() }` and `highlight = selected?.let { legs.getOrNull(it)?.points }.orEmpty()` to `WorkoutMap`. Place `SplitPicker(rows, selected, onSelect = { selected = it; follow = true })` along the bottom inside `WindowInsets.safeDrawing`; show it only when `rows` is not empty; pass `creditBottom` equal to the picker's height so the OpenStreetMap credit stays visible above it.

- [ ] **Step 9: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.data.*' --tests 'org.openstrap.app.ui.glow.*' --tests 'org.openstrap.app.i18n.*'`
Expected: PASS.

- [ ] **Step 10: Check on a device.** Open a past run longer than 3 km. Expect: numbered circles at each km along the line; tapping "Km 2" in the picker draws it with a white casing and zooms to it, tapping it again returns to the whole route; Route only keeps markers and highlight and makes no tile requests; a run under 1 km has no markers and only a "Last N m" chip; rotating keeps the selection; the OpenStreetMap credit is visible above the picker.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: km markers and split highlight on the full-screen route map"
```

---

## Branch 2: `feat/gpx-export`

Start with `gs branch create feat/gpx-export` (stacked on branch 1; the Export pill sits in the same `RouteCard`).

### Task 3: GPX writer

**Files:**
- Create: `app/src/main/kotlin/org/openstrap/app/export/GpxWriter.kt`
- Modify: `app/src/main/kotlin/org/openstrap/app/data/Route.kt:411` (`private const val MAX_HR_GAP_S` becomes `const val`, so the writer matches heart rate the same way `byZone` does)
- Test: `app/src/test/kotlin/org/openstrap/app/export/GpxWriterTest.kt`

**Interfaces:**
- Consumes: `Route.track(points): List<Point>`, `Route.MAX_HR_GAP_S` (30 s), `Route.Point`.
- Produces: `object GpxWriter { fun write(name: String, points: List<Route.Point>, heartRate: List<Pair<Long, Int>>, pauses: List<LongRange> = emptyList()): String }`. `heartRate` is (epoch seconds, bpm) ascending, as `PhoneSummary.history`. `pauses` is `PhoneSummary.pauses`.

Output format (the only decisions the tests do not pin): GPX 1.1, `creator="OpenStrap"`, namespaces `http://www.topografix.com/GPX/1/1` and `gpxtpx` = `http://www.garmin.com/xmlschemas/TrackPointExtension/v1`; one `<trk>` with `<name>`; `<time>` ISO-8601 UTC with `Z` and whole seconds. Points come from `Route.track(points)`, the same smoothed line the map draws. A new `<trkseg>` starts between two consecutive points when a pause overlaps them (`it.first < b.t && it.last > a.t`, as `Route.paused`).

- [ ] **Step 1: Write the failing tests** in `GpxWriterTest`. Parse output with `DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }` so each test also proves it is well-formed. Use a 100 s run east at 3 m/s with 1 fix a second (copy the `east`/`run` helpers from `RouteTest`) so `Route.track` keeps points. Tests:
  - `a run is one segment of timed points`: root `gpx` has `version` `1.1`, one `trk`, one `trkseg`, more than 50 `trkpt`, first `time` equals the ISO string for the first kept point.
  - `a pause splits the track in two segments`: pause `40L..60L` (offset by the run's start) gives 2 `trkseg`.
  - `heart rate rides along when a reading is near`: readings every 5 s from the start give `gpxtpx:hr` under each point's `extensions`; a point more than 30 s from any reading has no `extensions`.
  - `altitude appears only when the fix has one`: points with `altitude = 12.3` have `<ele>12.3</ele>`; points without have no `ele`.
  - `a name with markup is escaped`: name `Run & "Hill" <1>` parses back with `getTextContent() == name`.
  - `coordinates use a dot whatever the locale`: `Locale.setDefault(Locale.GERMANY)` in the test (restore in `finally`); the document contains `lat="0.0000000"` shaped values with no comma in any `lat`/`lon` attribute.
  - `no route is a valid document with no points`: empty input parses and has zero `trkpt`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.export.GpxWriterTest'`
Expected: FAIL, compilation error (`GpxWriter` undefined).

- [ ] **Step 3: Implement `GpxWriter.write`** with `StringBuilder`, `String.format(Locale.ROOT, "%.7f", ...)` for coordinates (as `WorkoutMap.coord`), `%.1f` for elevation, `java.time.Instant.ofEpochSecond(t).toString()` for times, and a small private `escape` for `& < > "`. Nearest heart-rate reading by binary search on the times, as in `Route.byZone`'s `zoneAt`. Add a one-line KDoc noting that `ele` is GPS altitude as recorded (WGS84 ellipsoid, not sea level) and that Strava and similar services re-derive elevation.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.export.GpxWriterTest' --tests 'org.openstrap.app.data.RouteTest'` and again with `-PtestLocale=de-DE`.
Expected: PASS both.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: write a workout's route as GPX with heart rate"
```

### Task 4: Export action and share

**Files:**
- Create: `app/src/main/kotlin/org/openstrap/app/export/GpxShare.kt`
- Modify: `app/src/main/kotlin/org/openstrap/app/ui/glow/SummarySections.kt:268-277` (`RouteCard`)
- Modify: `app/src/main/res/values/strings_glow_summary.xml`
- Modify: `docs/privacy.md` (one line under the workout map entry)
- Test: `app/src/test/kotlin/org/openstrap/app/export/GpxShareTest.kt`

**Interfaces:**
- Consumes: `GpxWriter.write(name, points, heartRate, pauses)`; `StoryFiles.AUTHORITY_SUFFIX` (`.share`); `EdgeTitle(title, note, ..., action: (@Composable () -> Unit)?)`; `IconPill`; `AppIcons.DownloadSimple`; `PhoneSummary.route`, `.history`, `.pauses`; `analysis.sportName`, `analysis.workout.start`.
- Produces: `internal object GpxShare { fun fileName(workoutStart: Long): String; fun forSharing(context: Context, fileName: String, gpx: String): Uri?; fun send(context: Context, uri: Uri) }`. `fileName` returns `"OpenStrap_$workoutStart.gpx"` (same scheme as the PNG). `forSharing` writes to `File(context.cacheDir, "share")`, overwriting, and returns the FileProvider URI (null on failure, logged like `StoryFiles.forSharing`). `send` starts `Intent.createChooser` over `ACTION_SEND` with type `application/gpx+xml`, `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION` and a `ClipData`, as `ShareScreen` does for the PNG.

- [ ] **Step 1: Write the failing tests** in `GpxShareTest` (Robolectric): `fileName names the file after the workout` (`fileName(1792000000)` equals `"OpenStrap_1792000000.gpx"`); `forSharing writes the text and returns a content uri` (read it back through `context.contentResolver.openInputStream`, equals the input; authority is `context.packageName + ".share"`); `a second export replaces the first` (two writes with the same name, read back the second).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.export.GpxShareTest'`
Expected: FAIL, compilation error.

- [ ] **Step 3: Implement `GpxShare`.** File write on the caller's dispatcher; the composable calls it on `Dispatchers.IO`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'org.openstrap.app.export.GpxShareTest'`
Expected: PASS.

- [ ] **Step 5: Add the Export pill.** In `RouteCard`, pass `action = { IconPill(stringResource(R.string.glow_summary_export), onClick = ..., icon = AppIcons.DownloadSimple, height = 36.dp) }` to `EdgeTitle`. The click launches in `rememberCoroutineScope()`: build `GpxWriter.write(a.sportName, summary.route, summary.history, summary.pauses)` and call `GpxShare.forSharing` on `Dispatchers.IO`, then `GpxShare.send(context, uri)` on the main thread. If `forSharing` returns null, show the string `glow_summary_export_failed` ("Couldn't export the route") with the same toast or snackbar mechanism `ShareScreen` uses for a failed save (read its `saved` handling at `ShareScreen.kt:175` and match it). Add both strings with comments.

- [ ] **Step 6: Update `docs/privacy.md`.** One sentence: Export route hands a GPX file to the app you pick in the share sheet; OpenStrap itself sends nothing.

- [ ] **Step 7: Run the whole guard set**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug detektMain ktlintCheck`
Expected: PASS. `HardcodedEnglishTest` and `StringsParityTest` are the likeliest to object to new strings.

- [ ] **Step 8: Check on a device.** Open a past GPS run, tap Export, share to Files or Drive, open the `.gpx` in a viewer (for example gpx.studio) and check the line, times and heart rate. Share to the Strava app and to Strava's web "upload activity" and confirm it accepts the file.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: export a workout's route as GPX from the summary"
```

Then `gs stack submit` (drafts).

---

## Out of scope, in order of likely next

Both parked by request.

1. **Strava upload** (own spec and plan). Blocked on the credentials decision: user-supplied Strava API app (matches the no-cloud stance) or a hosted token proxy. It reuses `GpxWriter` as its upload payload.
2. **Health Connect route.** `ExerciseSessionRecord` can carry an `ExerciseRoute`, but it needs a separate route permission with its own consent flow; worth its own task once the file export is in.
