# Validation — 2026-10-21

Target: Xiaomi MiTV-AXFU1, Android TV 14 (API 34). Android customization only.

## Completed

- Plain Java suite: **29 checks passed**, JDK 17.
- Isolated Android instrumentation APK on the Xiaomi: **38 checks passed**, then **2 persistence checks passed after force-stop**. The tests used their own in-memory provider and preferences, never system Watch Next writes. The runner removed the test package afterward.
- `build.sh` completed; APK signature and manifest verified. Package `io.freemytv`, versionCode 2, versionName `1.1-continue`, min API 28 / target 33, compile API 34. No TMDB key configured.
- Installed and updated over the previous build with the same permanent signing key. History permission remained granted after update.
- System permission explanation/dialog displayed on TV. Actual history showed **8 entries: 5 Ivi, 3 Kinopoisk**, with four landscape cards visible, artwork, progress and additional cards reachable horizontally.
- Light-theme screen inspected. Selected card survived a return from Ivi. Long OK opened the hide menu; hiding reduced the row from 8 to 7 entries with nearest-card focus. The individual restore action restored the material; the hidden list was then empty. Source settings correctly listed Ivi and Kinopoisk, both enabled.
- User confirmed that Ivi opened the correct material and resumed from the saved position (Ivi 26.10.2 from the preceding diagnostic).
- Temporary diagnostic and isolated test APKs were removed from the TV after the checks.
- No changes to HomeMode, Watchdog, HomeData, Recs, screensaver implementations, LG or Tizen code. The original Google TV home remained the default during preview.

## Kinopoisk result

Kinopoisk 2.270.8 publishes `intent://seasons?...#Intent;scheme=kpatv;end` with `filmId` and `episodeId`. All three observed entries use that route. The launcher forwards the supplied URI and extras to `ru.kinopoisk.tv`. It opens the series' episode chooser and selects the published episode; it does not start playback automatically. The user confirmed that the correct episode is selected. Playback position after pressing OK on that episode is still awaiting confirmation. No unsupported playback/seek parameters were added.

## Not yet accepted

- Fresh playback progress after returning from the real cinema apps: the provider supports reads and observer updates, but the latest diagnostic snapshot had not yet changed its recorded positions. No freshness guarantee is inferred.
- Real-TV source-exclusion toggling, restore-all UI, dark theme and enabled TalkBack (filtering/restore-all persistence are covered by code and isolated tests).
- Final switch to Free My TV, return through Original, and watchdog recovery. Their implementations are preserved, but unchanged code is not evidence of a completed live recovery test.
- TMDB-configured background/screensaver behavior; this build intentionally has no API key. The existing weather widget remains configured through upstream Home Settings; city defaults were not customized.
- Zona playback/Watch Next behavior is explicitly outside acceptance.

The feature implementation and APK are available; full device acceptance remains open for the items above. Do not interpret the isolated permission/error fixtures as revoking real cinema permissions or provoking errors in the system provider.
