# B13 — Nusantara & Utility UI (Data Integrity & State Honesty)

Scope: `NUS-01..12`, `CORE-12..16` (BMKG quake/weather, prayer times, area/postal lookup, AI weather,
currency converter, dictionary), the Nusantara hub, and the shared `UtilityComponents` used by them.

## 1. Findings (verified in source)

| # | Finding | File |
|---|---|---|
| 1 | BMKG slots without `t`/`hu` were coerced to `0`, so the UI displayed **0 °C / 0 %** for data the provider never sent — fabricated weather values. | `core/nusantara/BmkgCuacaApi.kt`, `feature/nusantara/WeatherScreen.kt` |
| 2 | The forecast list was silently truncated to 24 slots while the header claimed that truncated count as the total. | `feature/nusantara/WeatherScreen.kt` |
| 3 | After a failed refresh, quake/weather/prayer screens kept the previous payload and only showed an error card, so cached session data looked identical to current data. | `EarthquakeScreen`, `WeatherScreen`, `PrayerTimesScreen`, `NusantaraHubScreen` |
| 4 | A failed area search discarded results, and typing a new keyword kept the old results with a count that looked like it matched the new input. | `feature/area/AreaLookupViewModel.kt`, `AreaLookupScreen.kt` |
| 5 | The prayer freshness note used the device date instead of the date the provider returned for the schedule. | `feature/prayer/PrayerTimesScreen.kt` |

Attribution was already correct and is preserved: BMKG (`data.bmkg.go.id`, `api.bmkg.go.id`), emsifa,
`alamat.thecloudalert.com` (MIT), `api.myquran.com` (Quran API ID v2), Frankfurter reference rates, and
Wiktionary / dictionaryapi.dev (CC BY-SA) via the AI gateway. No provider text or licence notice changed.

## 2. Changes

- `WeatherSlot.temperatureC` / `humidityPercent` are now nullable and keep `null` when BMKG omits the
  value. `WeatherSlotRow` renders `—` for a missing temperature and `weather_humidity_unknown` for a
  missing humidity; nothing is invented.
- The forecast renders every received slot and the header reports the real count (no `take(24)`).
- `EarthquakeUiState`, `WeatherUiState`, `PrayerTimesUiState`, `NusantaraHubUiState` and
  `AreaLookupUiState` gained `isStale`. A failed refresh that keeps previous data sets it, a success
  clears it, and each screen shows `utility_stale_data_notice` so retained session data is labelled.
- `AreaLookupUiState` gained `resultKeyword`, the keyword that actually produced the visible rows. The
  result header names that keyword (`area_results_for`), and when the input differs the screen shows
  `utility_query_changed_notice` with a direct Search action instead of presenting old rows as matches.
- Prayer freshness now uses the schedule's own `dateLabel`/`isoDate` (device date only as fallback).

## 3. Verification

Unit + Robolectric coverage added/updated (CI job `android`):

- `NusantaraTest.weatherSlotKeepsMissingNumbersAsNullInsteadOfZero` — BMKG payload with `t`/`hu` = null
  parses to `null`, not `0`.
- `NusantaraAndUtilitiesUiTest.prayerTimesShowsPresetsScheduleHighlightAndErrorRetry` — a failed refresh
  keeps the schedule, sets `isStale`, renders the stale notice, and a successful retry clears it.
- `NusantaraAndUtilitiesUiTest.areaLookupShowsPromptEmptyClearAndCopyPostalCode` — results are labelled
  with the searched keyword and typing a new keyword raises the “results are still for …” notice while
  the previous rows stay visible.

Commands run by CI (no local Android SDK/JDK/Gradle used):

```bash
gradle assembleDebug testDebugUnitTest lintDebug assembleRelease
java -jar ktlint-cli.jar "app/src/**/*.kt"
```
