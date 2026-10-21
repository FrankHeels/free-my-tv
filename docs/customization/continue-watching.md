# Continue watching (Android fork)

Fork: https://github.com/FrankHeels/free-my-tv
Upstream: https://github.com/simbasjungles/free-my-tv
Branch: `feature/continue-watching`. Based on upstream `62e883b3179c8cafb7a2b09960eabfe7eea6ef1c`.

This Android-only change replaces recommendation tabs/cards with a four-card, horizontally navigable Watch Next row. The app shelf, weather, background, screensaver, Original mode and watchdog retain their existing implementations. The row works without a TMDB key. Recs still supplies background/screensaver art when configured. No private cinema APIs, Gradle or libraries are added.

## Data and identity

`ContinueWatching` queries `TvContract.WatchNextPrograms` with `READ_TV_LISTINGS`. Reads and app-label lookups run on a worker. Resume refreshes; notifications debounce for 300 ms; pause cancels/invalidates pending reads and unregisters the observer. Permission, provider error, loading and empty history are distinct states. No TV-provider records are written.

`ContinueModel` retains CONTINUE entries with unknown progress, removes known completed entries, sorts by engagement descending (missing timestamps last) and uses a deterministic key to break ties. Duplicate titles from different apps remain distinct. Focus follows material identity; removal selects the nearest surviving index. Four slots display the current window; D-pad and TalkBack can reach all entries. Long OK and TalkBack's long-click action open Hide material.

Persistent identity is package + SHA-256 of length-prefixed fields: internal provider ID if supplied; otherwise title/season/episode number/episode title; otherwise the published intent. Row IDs, positions, engagement times and artwork are excluded. With no stable identifier, two indistinguishable metadata records in the same app share a hide key; if the app renames the material, that fallback key changes. Hide persists until manual restoration, independently of the source's progress.

`ContinuePrefs` stores excluded sources, remembered source names and hidden material labels in a separate SharedPreferences file in the `:home` process. Settings supports individual restore and restore all. These changes do not recreate HomeView.

## Artwork and launch

`ContinueImages` queues only visible artwork, with two workers, a 12 MiB bitmap cache, bounded pending/miss sets, a 4 MiB encoded-image limit, timeouts and downsampling. HTTPS redirects remain HTTPS. `content://` uses the caller's existing access. Missing, inaccessible and HTTP images remain placeholders. There is no global cleartext exception.

`ContinueLaunch` parses the published intent, rejects selectors/mismatched packages/components, pins resolution to the source and checks that the handler is exported. URI grants/ClipData are not forwarded. A failed launch offers Open app only after an explicit user action. A source may publish a title detail link rather than a direct playback link; the launcher does not guess missing resume parameters.

One verified compatibility rule applies to `ru.kinopoisk.tv`: its `kpatv://seasons` URI receives `action=play` when it has exactly one nonblank `filmId` and `episodeId` and no existing `action`. The installed Kinopoisk 2.270.8 handler explicitly supports this action and opens its episode player; a live test on Xiaomi confirmed the correct episode and saved position with the user. The original IDs, encoded query parameters, fragment, intent extras and MIME type are retained. Explicit source actions, incomplete/ambiguous links, other routes and other apps are left unchanged. No playback position is injected and no private API or simulated remote key is used. This is version-dependent app behavior, not a documented cross-app contract; retest after Kinopoisk updates.

## Build and updates

Use JDK 17, Android platform 34 and build-tools 34 or newer. Older D8 from build-tools 34 cannot process some enum metadata emitted by JDK 25.

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export FREEMYTV_KEYSTORE=/private/path/freemytv.keystore
# Set FREEMYTV_KEYSTORE_PASSWORD in the environment from your private password store.
./build.sh
tests/run.sh
tests/android/build.sh
```

Back up the keystore and password separately from Git. The build script preserves the upstream defaults when the signing variables are omitted. Keep the package ID and signing certificate for upgrades, increase versionCode for releases, then install the new APK over the existing version with `adb install -r`. No automatic update mechanism is added. Upstream releases must not overwrite the custom build: fetch upstream, merge/rebase into a development branch, run the checks, then build with the same key.

## Verification

`tests/run.sh`: plain Java assertions for filters, missing fields/progress, tie order, duplicate names, material identity, hidden reinsertions and focus/window behavior.

`tests/android/build.sh` and `tests/android/run.sh`: a separate instrumentation APK (`local.codex.continuetests`) with its own in-memory provider. It never inserts records into `android.media.tv`, never launches real cinema apps and needs no TV-history permission. It verifies read/observer lifecycle, permission gates (simulated via a Context wrapper), provider errors, stale queries, durable SharedPreferences, source restrictions, bad/uninstalled handlers and bounded content artwork. Synthetic Kinopoisk links verify the playback rule, exact ID/query preservation, idempotence, explicit actions, missing/duplicate IDs and unchanged other sources/routes. The second run verifies preferences after force-stop. Set `ANDROID_SERIAL` and optionally `ADB`; uninstall the test package after testing. The runner checks PASS/FAIL output because Android instrumentation may return shell exit code zero even on failure. Build the main APK before the test APK: `build.sh` clears the shared `out/` directory.

Device acceptance additionally requires live permission UI, artwork and progress comparison, D-pad/TalkBack, hide/restore, settings, light/dark themes, actual cinema resume positions and updated provider progress. Zona is explicitly outside live acceptance. Weather/location defaults are upstream settings; adjust through existing Home Settings if needed.

Activate the home replacement only after preview checks: Settings → Home Screen → Free My TV, then test Original once. This changes launcher/screensaver and may disable the upstream promotional-app list. Do not disable or clear system packages manually. Keep the Original/watchdog code and the recorded original state available.
