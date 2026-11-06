# Outdoor Run: Full-Screen Map, Km Markers, Split Highlight and GPX Export

Date: 2026-11-05. Plan: `docs/superpowers/plans/2026-11-05-outdoor-run-map-and-gpx.md`.

## Goal

A finished GPS workout's route is a 170 dp card on its summary. Make it useful for looking back at a run: open it full screen, see where each kilometre ended, pick a kilometre and see where it was, and take the run out of the app as a GPX file.

## Scope

In: full-screen map, km markers, split picker with highlight, GPX export from the Route card.
Out, for now: Strava upload, Health Connect `ExerciseRoute`, miles, any change to the live map, uploading anywhere.

## Behaviour

**Where.** The Route card (`RouteCard`), shown on the just-finished summary and a past workout's page for any run whose route is kept and `Route.mappable`. Nothing changes for workouts without a route.

**Full-screen map.**
- Tapping the card's map opens the map over the whole screen; system Back or Close returns to the page.
- The route is drawn as on the card, each stretch in its heart-rate zone colour, with the start dot and the end dot, fitted to the screen on open.
- Pan and zoom are free. After a pan, a Fit route pill appears and refits the whole route.
- A Route only toggle swaps tiles for a plain black background, as on the live map. In that mode the app makes no tile requests.
- Rotation and process death keep the map open, with the same Route only choice and the same selected split.

**Km markers.** A numbered circle at the end of each whole kilometre (1, 2, 3 ...), on the route. A run under 1 km has none. They show with or without map tiles.

**Split picker.** A row of chips along the bottom edge, one per kilometre and one for the last part when the Splits list shows one ("Km 3 · 5:42", "Last 430 m"). The chips and their times are the same rows as the summary's Splits list. Tapping a chip draws that stretch with a white casing under its zone colour and zooms to it; tapping it again clears it and refits the whole route. A run with no rows shows no picker. The OpenStreetMap credit stays visible above the picker.

**GPX export.**
- An Export pill beside the Route card's title opens the system share sheet with a `.gpx` file named `OpenStrap_<workout start>.gpx`.
- GPX 1.1, one track named for the sport; points are the smoothed track the app draws and measures along, each with time, elevation when the fix had one, and heart rate (`gpxtpx:hr`) when a reading lies within 30 s.
- A pause starts a new track segment, so the file has no line across a pause or a gap.
- Coordinates are decimal with a dot in every locale; names are escaped so the file is always well-formed XML.
- If the file can't be written, the user is told; nothing is shared.

## Constraints

- No new network use, no new permissions, no new FileProvider paths (`res/xml/share_paths.xml` already exposes `cache/share/`). The route still leaves the phone only when the user picks an app in the share sheet; `docs/privacy.md` says so.
- All user-facing text lives in `res/values/strings_glow_summary.xml` (`HardcodedEnglishTest`, `StringsParityTest`); the suite also runs with `-PtestLocale=de-DE`.
- Splits stay per kilometre, as everywhere in the app.
- `WorkoutMap`'s existing callers (the live map) behave exactly as before.
- Full screen is a dialog over the page, not a new navigation destination.

## Decisions and why

- **Marker images, not text labels.** A text label on the map needs a font download from the tile host, which would break Route only's promise of no requests. Markers are small bitmaps added to the style, so they work offline. A style change (toggling Route only) drops them, so they are added again whenever the style is ready.
- **`Route.legs` as the one source of kilometres.** The markers, the highlight and the Splits list all come from the same cut of the same smoothed track (`splits` becomes a view of `legs`), so the three can't disagree.
- **The smoothed track in GPX, not raw fixes.** The file matches the distance and line the app shows. Elevation is GPS altitude as recorded (WGS84 ellipsoid); services that care re-derive it.
- **Share sheet, not a save dialog.** Consistent with sharing the story card, and it reaches Strava's and others' import without OpenStrap knowing about them.

## Known limits

- A highlighted kilometre that spans a pause is drawn as one line, so the gap shows as a straight segment.
- Tapping a marker on the map does nothing; the picker is the way to select.

## Done when

1. On a device, a past GPS run opens full screen from the Route card, with a numbered marker at each kilometre and a picker whose chips match the Splits list.
2. Selecting and clearing a split highlights and refits as described; Route only keeps markers and highlight and makes no tile requests.
3. A run under 1 km has no markers and no whole-km chips.
4. Exporting opens the share sheet; the GPX opens in a viewer with the right line, times and heart rate, and Strava's file upload accepts it.
5. `./gradlew :app:testDebugUnitTest :app:lintDebug detektMain ktlintCheck` is green, also with `-PtestLocale=de-DE`.
