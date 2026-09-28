# WeatherNot (Android)

The Android companion to the [WeatherNot](../weathernot) web app: sign in,
see your events and their go / no-go verdicts, dig into an event's forecast,
criteria and alerts, create events, and manage your notification settings.
It talks to the same API as the web app (`weathernot/src/weathernot/api`),
the calls mirror the web client (`frontend/src/api/client.ts`), and the
wording and unit rules are ported from the web app so both say the same
thing.

Built with Kotlin and Jetpack Compose (Material 3), Ktor for HTTP,
kotlinx.serialization, and osmdroid for maps.

## What's in it

| Screen | What it does |
|---|---|
| Sign in | Passwordless email sign-in (the same request-then-verify the web app does), then the one-time "what kinds of events do you plan?" question |
| Events | Upcoming / Past, a "Needs attention" group for forecast Watch / No-go events, search, type filter, series rows. Per-event pause / resume (with Undo), mark cancelled, duplicate, delete. Pull to refresh; refreshes itself while a check is running |
| Event | Verdict panel with "check now", hour-by-hour forecast grid, map, parts of the event, criteria grouped by part (verdict, trend, check history, edit, pause, remove with Undo), suggestions, alert history with notes, per-event notifications (decision deadline, email / text / improvements overrides, extra recipients), share links (copy, Android share sheet, revoke, organizer message), edit details |
| New event | Three steps like the web app on a phone: what & when, where (place search, or coordinates), what to watch (suggested criteria with reasons, adjust any, add your own) |
| Series | Dates with verdicts, cancel / restore a date, the criteria every date watches |
| Profile | Units, event types, email / improvements / digest / quiet hours, phone number and texts, sign out |

`weathernot://event/<id>` and `weathernot://event/<id>/criterion/<id>`
(the links the backend already puts in alerts) open the event, with that
criterion expanded and scrolled into view.

Not here yet: push notifications (the API has no device-token registration
yet), creating a series or importing a schedule, and editing an event's
parts. Those stay on the web app for now.

## Repo layout

```
weathernotkit/   Kotlin/JVM module: API models, ApiClient, and the web app's
                 formatting rules (units, criterion wording, event times,
                 events-list grouping). Builds and tests with just the JVM
                 toolchain -- no Android SDK.
app/             The Compose app: screens, view models, theme, map.
scripts/         setup.sh (SDK + emulator), build-and-run.sh (build, install, launch).
gradle/libs.versions.toml   Every dependency version.
```

## Setup

```bash
./scripts/setup.sh          # installs the SDK pieces and creates a 'WeatherNot_Test' emulator
emulator -avd WeatherNot_Test &
./scripts/build-and-run.sh  # builds, installs and launches on the first attached emulator/device
```

Gradle needs a JDK from 17 to 21. `build-and-run.sh` finds one (Homebrew
`openjdk@21` / `openjdk@17`) when the system default is newer; for other
Gradle commands, set `JAVA_HOME` yourself, e.g.
`export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home`.

### Running against the API

The app talks to `http://10.0.2.2:8000`, which is the emulator's name for
your machine's `localhost:8000`. Run the weathernot API there: the
rancher-desktop deployment (`k8s/scripts/deploy-backend.sh` in the
weathernot repo) or `scripts/dev-api.sh`.

Share links point at the web app's status page, `http://localhost:5173`
by default. Both URLs are `buildConfigField`s in `app/build.gradle.kts`.
A physical device can't reach `10.0.2.2`, so point `API_BASE_URL` at your
machine's LAN IP instead. `usesCleartextTraffic` is on for this local
setup; turn it off before a build talks to a deployed HTTPS API.

Sign in with any email: with no email provider wired up, the API hands the
magic-link token straight back and the app verifies it at once, the same
shortcut the web app takes.

## Tests

```bash
./gradlew :weathernotkit:test
```

Covers decoding real API payloads (including enum values this build
doesn't know, which fall back instead of failing the whole response),
the request bodies (snake_case, and explicit `null`s where the API
treats null as "clear it"), 401 handling, unit conversion, criterion
wording, event-time formatting, and the events-list grouping.

## Design

Colors come from the web app's tokens (`frontend/src/index.css`): the blue
header gradient, the accent, and the go / caution / hold decision colors.
Verdicts always show icon + words + color, never color alone. The web app
is light only; the app also has a dark palette in the same hues and follows
the system setting.

Maps use osmdroid (OpenStreetMap tiles), so no Maps API key or billing
account is needed. The map doesn't pan inside the scrolling event page;
tapping it opens the spot in the phone's maps app.

## Differences from the web app

- A criterion's margin ("6°F past the threshold") converts a temperature
  difference by scale only. The web app's `formatMargin` converts it as an
  absolute temperature, so a 3.2 °C margin reads "38°F past the threshold"
  there.
