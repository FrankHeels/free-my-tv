# Stock home shortcut

Android version 1.4 adds a home icon immediately before Settings in the top row and a labelled **Stock home** action in the bottom drawer. Both use the existing D-pad focus and accessibility labels. From a continuation card: Up, Right twice, OK. Press **Home on the physical remote** to return to Free My TV.

## Temporary visit

The shortcut resolves an exported system HOME activity, preferring the recorded original launcher, then the Android TV / Google TV launcher. It includes disabled packages in discovery. It does not choose an unknown ambiguous candidate, the setup wizard, a third-party launcher or Free My TV itself. The explicit launch uses `ACTION_MAIN` / `CATEGORY_LAUNCHER`, so the visit is an ordinary app task.

Upstream Free mode disables the stock launcher. A non-exported foreground `StockHomeActivity` in the main process asks `HomeMode` to enable it temporarily, under the same lock as mode jobs. Classic Android TV's companion recommendations service is restored if Free My TV disabled it. These operations use the TV's already authorized local ADB connection. Leaving preparation cancels the launch; errors show an explanation.

On this Xiaomi firmware, the stock HOME activity has priority 2 and Free My TV has priority 0. Merely keeping the HOME role with Free My TV does not prevent the system resolver choosing stock while it is enabled. Android caps non-privileged activity priorities at 0 ([Android 14 resolver source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/pm/resolution/ComponentResolver.java)).

During such a visit, the existing `RemoteKeyService` handles Home and explicitly opens Free My TV. It does not read screen content. The handler checks the HOME role and is inactive in Original mode or after watchdog restoration. Other keys retain their existing behavior on Xiaomi; holding Home during a visit is consumed by the same handler. The service is temporarily added alongside any existing accessibility services, preserving TalkBack and the previous accessibility-enabled state.

When `HomeActivity` resumes (including a return from stock or another app), a job restores only the packages enabled for this visit, respecting protected packages and Wi-Fi ownership. A persisted visit token prevents a delayed cleanup closing a newer visit. After cleanup, ordinary native HOME resolution again selects Free My TV and the temporary Xiaomi key service is disabled. Boot/update verification also restores the normal Free configuration after an interrupted visit. Original mode and watchdog restoration turn off Home interception and retain the factory restoration path.

The button does not change the selected mode, HOME role or screensaver. It is distinct from **Home Screen → Original**, which restores the factory configuration. Keep USB debugging and the app's local ADB authorization available for enabling and restoring the stock launcher.

## Validation

The isolated suite covers candidate selection, missing/non-exported/ambiguous activities, setup and third-party exclusion, disabled launchers, explicit launch flags, affected packages and Home-handler guards for Original/watchdog/role changes.

`tests/android/stock-home-live.py` is an opt-in Xiaomi acceptance test. Set `ADB` and `ANDROID_SERIAL`; it opens the visible shortcut and waits for a physical Home press, then checks the role, native resolver and service cleanup. Injected `adb input` keys bypass the accessibility filter on this TV and cannot validate physical Home interception. `RETURN_MODE=activity` instead checks the cleanup path using an explicit return, without claiming to test the remote button. Actual results are in [validation.md](validation.md).
