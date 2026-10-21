# Validation — 2026-10-21

Target: Xiaomi MiTV-AXFU1, Android TV 14 (API 34). Android customization only.

## Completed

- Plain Java suite: **29 checks passed**, JDK 17.
- Isolated Android instrumentation APK on the Xiaomi: **56 checks passed**, then **2 persistence checks passed after force-stop**. This includes 18 checks for the Kinopoisk playback URI rule. The tests used their own in-memory provider and preferences, never system Watch Next writes or real cinema launches. The runner removed the test package afterward.
- `build.sh` completed; APK signature and manifest verified. Current package `io.freemytv`, versionCode 4, versionName `1.3-continue`, min API 28 / target 33, compile API 34. No TMDB key configured.
- Installed and updated over the previous build with the same permanent signing key. History permission remained granted after update.
- System permission explanation/dialog displayed on TV. Actual history showed **8 entries: 5 Ivi, 3 Kinopoisk**, with four landscape cards visible, artwork, progress and additional cards reachable horizontally.
- Light-theme screen inspected. Selected card survived a return from Ivi. Long OK opened the hide menu; hiding reduced the row from 8 to 7 entries with nearest-card focus. The individual restore action restored the material; the hidden list was then empty. Source settings correctly listed Ivi and Kinopoisk, both enabled.
- User confirmed that Ivi opened the correct material and resumed from the saved position (Ivi 26.10.2 from the preceding diagnostic).
- Temporary diagnostic and isolated test APKs were removed from the TV after the checks.
- No changes to HomeMode, Watchdog, Recs, screensaver implementations, LG or Tizen code. The original Google TV home remained the default during preview.

## Weather follow-up

The user confirmed city search with the oblast finds Svetly. On the TV, geocoding succeeded while Open-Meteo forecast connections timed out at both 6 and 20 seconds; the same hostname resolved to the same address on the computer, where forecasts were reachable. Version 1.3 adds a MET Norway fallback in HomeData through WeatherLoader. **15 weather regression checks passed**, as did the existing **29 Java / 56 Android / 2 persistence checks**. An additional live check through the production loader fetched a valid forecast for Svetly on the TV. After installation, the user confirmed the weather appeared; visual inspection showed temperature, six future hours, the 24-hour range, sunrise/sunset and source attribution. Temporary weather-diagnostic and instrumentation packages were removed. See [weather.md](weather.md) for transport, cache, attribution and range semantics.

## Kinopoisk result

Kinopoisk 2.270.8 publishes `intent://seasons?...#Intent;scheme=kpatv;end` with `filmId` and `episodeId`. All three observed entries use that route. The unmodified link opens the episode chooser with the correct episode selected, as confirmed by the user.

The installed app's handler explicitly supports `action=play` on that same route and dispatches it to its episode player with the supplied film/episode IDs. A direct device test with the existing episode link plus this action opened the player; the user confirmed both the correct episode and saved playback position without the episode list. Version 1.2 now applies this narrow compatibility rule to Kinopoisk episode links. It preserves explicit actions and incomplete/ambiguous links, never injects a seek position, and leaves other apps unchanged. The updated APK was installed with the same signing key; a subsequent OK on the first Kinopoisk card opened the player directly. This behavior is verified for Kinopoisk 2.270.8 and should be retested after app updates; it is not a public guarantee for future versions.

## Not yet accepted

- Fresh playback progress after returning from the real cinema apps: the provider supports reads and observer updates, but the latest diagnostic snapshot had not yet changed its recorded positions. No freshness guarantee is inferred.
- Real-TV source-exclusion toggling, restore-all UI, dark theme and enabled TalkBack (filtering/restore-all persistence are covered by code and isolated tests).
- Final switch to Free My TV, return through Original, and watchdog recovery. Their implementations are preserved, but unchanged code is not evidence of a completed live recovery test.
- TMDB-configured background/screensaver behavior; this build intentionally has no API key. Weather city defaults are now Svetly, Kaliningrad Oblast; TV selections still take precedence.
- Zona playback/Watch Next behavior is explicitly outside acceptance.

The feature implementation and APK are available; full device acceptance remains open for the items above. Do not interpret the isolated permission/error fixtures as revoking real cinema permissions or provoking errors in the system provider.
