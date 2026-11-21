<div align="center">

<img src="manager/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="Pigu icon" />

# Pigu

Pigu gives Android apps access to system-level APIs that normally require ADB or root, through the Shizuku API.

It is a personal fork of [Shizuku](https://github.com/RikkaApps/Shizuku), based on the maintained fork by [thedjchi](https://github.com/thedjchi/Shizuku). The optional companion and the compatibility notes are inspired by [Porter](https://github.com/d4rken-org/porter).

</div>

## What is Pigu?

Pigu runs a small service as the `shell` user (through ADB or wireless debugging) or as `root`. Other apps talk to that service over Binder using the standard Shizuku API, so they can call privileged system APIs without being rooted themselves.

The app uses the package id `com.empty.pigu`, and its service is the same Shizuku-compatible server used by the upstream project.

## Features

- **Three ways to start the service:** wireless debugging (including Android 11+ pairing), a computer via `adb`, or root. A wireless-debugging pairing appears as **Pigu** in Android's Paired devices list.
- **Direct launch to the home screen:** no first-run setup pass; permissions are requested only when the relevant flow needs them.
- **Start / stop control** from the home screen: stop has a confirmation. To restart, stop and start again; there is no one-tap Restart button.
- **Long-press shortcuts** for starting the service, App Management, and the Developer page.
- **Access control settings category** for the local access log and a confirmed “revoke all app grants” action.
- **TCP mode** (after an ADB-over-Wi-Fi start), so the service can be restarted without Wi-Fi.
- **Option to keep wireless debugging on** after connecting, with a warning about the security trade-off.
- **Two-layer watchdog:** the manager restarts a dead service, and the server exits itself when its main thread hangs or a required system service disappears.
- **Recovery guards:** deliberate stops are respected, duplicate starts are suppressed, and repeated failed restarts stop instead of looping forever.
- **Start on boot**, waiting for Wi-Fi when needed.
- **Hardened permission prompt** that validates the caller, blocks obscured taps, discloses shared UIDs, and ignores taps for 500 ms after it appears.
- **Automation intents** for Tasker, MacroDroid, Automate, etc. Manual START may run over mobile data; boot and watchdog recovery still wait for an unmetered network.
- **Start over local TCP** from Pairing methods: enter a port, skip mDNS, and connect to `127.0.0.1` if adb is already listening. No Wi-Fi or local-network permission is used, but the port must already be open.
- **Copy diagnostics** and a **Service Doctor** checklist on the Developer page for privacy-conscious bug reports and common start failures.
- **Local access log** of permission prompts, grants/revokes, automation starts/stops, and companion installs. It is a 400-entry ring buffer, stored only on the device, and excluded from backup.
- **Root-mode warning** at the first root start and a persistent home banner while the service runs as root.
- **App discovery** that lists every app declaring the Shizuku permission, with a per-app hint when an older client may need the companion.
- **App Management search, filters, sorting and per-app details** (UID/user, version, client type, last request, access toggle, open app and app info).
- **Optional phantom-process control** (Android 12+) with an explicit warning and a one-tap restore of Android's default.
- **Android TV and VR support**, including D-Pad navigation and pairing without notifications.
- **Android 17 / SDK 37 compatibility** inherited from the base fork.
- **One local-network permission gate** for wireless discovery and pairing: API 36 asks for `NEARBY_WIFI_DEVICES`, API 37 asks for `ACCESS_LOCAL_NETWORK`, and the background worker posts an actionable notification instead of starting a blocked mDNS search.
- **English-only UI**, with a choice between the system locale and English for formatting.
- **Optional companion app** for apps that hard-code the official Shizuku package name.

## Requirements

- Android 7.0 or newer.
- **Root mode:** a rooted device.
- **Wireless debugging:** Android 11+ and most Android TVs.
- **PC mode:** any device with `adb` access.
- **Start on boot:** available with wireless debugging or root mode.

## Companion app

Pigu keeps its own package name, `com.empty.pigu`. Some apps only check for the official Shizuku package, `moe.shizuku.privileged.api`, and refuse to run when it is missing.

The optional **Pigu Companion** is a small, non-launchable app that uses that old package name. It does not run a service, does not request permissions, and has no UI. Its only job is to make those hard-coded package checks succeed; Pigu still starts the service and handles permission prompts.

The companion APK is bundled inside Pigu and can be installed from the Developer page. There are two install paths:

- **Install** — installs the bundled APK through the running Pigu service (or root). Nothing is downloaded.
- **System installer** — writes the bundled APK to Pigu's cache and opens Android's package installer, so the installation is confirmed by the system.

The standalone `pigu-companion-*.apk` is also still published in Releases for people who prefer to sideload it themselves.

| Situation | Result |
| --- | --- |
| Companion not installed | Apps that use the normal Shizuku API still work. Apps that hard-check for `moe.shizuku.privileged.api` may report that Shizuku is missing. |
| Companion installed | Those package checks pass, and the apps can connect to Pigu normally. |
| Official Shizuku installed | The companion cannot be installed, because Android treats both as the same app identity. Uninstall Shizuku first. |
| Another app owns the package | Pigu shows it as being owned by another app and offers a shortcut to Android's app-info page. |

The Developer page shows whether the companion is installed, its package name, and a dialog explaining the above; it also links to Android's app-info page so the companion can be uninstalled.

### Limitations

The companion is a package identity stub, not a full permission replacement:

- Apps that call `getPermissionInfo("moe.shizuku.manager.permission.API_V23").packageName` still see `com.empty.pigu`, because Pigu owns that permission. Making the companion own it would make the companion mandatory, as Porter does.
- Apps that verify the official Shizuku signing certificate will not accept Pigu or the companion.
- Apps that only check `getPackageInfo("moe.shizuku.privileged.api")` or use `Shizuku.pingBinder()` work with the companion.

### Android 17 debugging settings

Android 17 QPR3 and later can require `MANAGE_DEBUGGING` for USB and wireless debugging settings. That is a `signature|privileged` permission, so Pigu cannot hold it even with `WRITE_SECURE_SETTINGS`.

On those builds, Pigu treats this writes as best-effort:

- **Auto-disable USB debugging** and **Keep wireless debugging on** may not take effect automatically.
- Pigu can still start and use the service when the required debugging mode is already enabled.
- Settings shows a **Developer options** shortcut so the user can enable or disable USB/wireless debugging manually.
- Background-start failures show a notification with the same shortcut.

Older Android versions keep the previous automatic behavior.

## Privacy

- No tracking, analytics, or telemetry.
- No proprietary libraries.
- No Google Play Services.
- Internet access is only used for wireless debugging connections.
- No update checker; updates are installed manually.
- The automation auth token is stored in a separate `secrets` preferences file that is excluded from cloud backup and device transfer. Existing tokens are migrated without being regenerated.
- The diagnostics snapshot contains app, device, and service state only. It never includes the auth token, Wi-Fi names, IP addresses, app lists, or the Starter transcript.
- The access log is a local 400-entry ring buffer containing package names, UIDs, timestamps, and action results. It is excluded from cloud backup and device transfer, and can be cleared from the Developer page.
- The last Starter transcript is stored in the app private files directory, excluded from backup. It can contain paths and ports, is never uploaded, and is only copied when the user taps **Copy** in the Service Doctor.
- The root-warning acknowledgement is stored in the excluded `secrets` preferences file, so a restored backup does not suppress the warning on another device.
- The optional phantom-process control changes one global Android setting (`settings_enable_monitor_phantom_procs`). It is off by default, warns before changing anything, verifies the result, and restores Android's default when turned off.
- Only the permissions needed for the service, wireless pairing, and permission-prompt protection are declared.

### Permissions

- **INTERNET:** wireless debugging and local ADB connections.
- **ACCESS_NETWORK_STATE:** waits for Wi-Fi before background starts.
- **ACCESS_LOCAL_NETWORK / USE_LOOPBACK_INTERFACE:** Android 16/17 local-network access for mDNS and loopback.
- **NEARBY_WIFI_DEVICES / CHANGE_WIFI_MULTICAST_STATE:** mDNS discovery for wireless ADB and pairing.
- **POST_NOTIFICATIONS:** pairing and service status notifications.
- **HIDE_OVERLAY_WINDOWS:** lets the permission prompt hide non-system overlays on Android 12+, so a tap cannot be redirected by an app drawing over Pigu.
- **REQUEST_INSTALL_PACKAGES:** installing the optional companion through Android's system installer.
- **RECEIVE_BOOT_COMPLETED:** start on boot.
- **FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE:** keeps the watchdog and starter services alive.
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:** prevents start-on-boot and watchdog from being killed.
- **WRITE_SECURE_SETTINGS:** toggles USB/wireless debugging when starting or stopping the service.

The companion declares no permissions.

## Building

Clone with submodules, because the Shizuku API lives in a submodule:

```bash
git clone --recurse-submodules https://github.com/8mpty/Pigu.git
cd Pigu
```

Build the main app:

```bash
./gradlew :manager:assembleDebug
./gradlew :manager:assembleRelease
```

Build the optional companion:

```bash
./gradlew :companion:assembleDebug
./gradlew :companion:assembleRelease
```

Outputs:

```
manager/build/outputs/apk/debug/pigu-debug.apk
manager/build/outputs/apk/release/pigu-release.apk
companion/build/outputs/apk/debug/pigu-companion-debug.apk
companion/build/outputs/apk/release/pigu-companion-release.apk
```

Release builds use `signing.gradle` and `signing.properties`; without them, they fall back to the debug keystore. Pigu and its companion must be signed with the same certificate so Pigu can recognize the companion.

You can build both at once:

```bash
./gradlew :manager:assembleDebug :companion:assembleDebug
```

The `:manager:assembleDebug` task produces a debuggable server. Attach a debugger to `shizuku_server`; in Android Studio enable *Run/Debug configurations → Always install with package manager* so the installed server matches the latest code.

### Tests

Run the JVM unit tests for the pure decisions:

```bash
./gradlew :manager:testDebugUnitTest
```

They cover the automation token gate and notification throttle, permission-request validation and label sanitizing, the boot claim, the wireless start route, the watchdog restart policy, the duplicate-start gate, the wireless port retry schedule, app-discovery companion counts, and the App Management filter policy. The suite currently has 53 tests. These are plain JVM tests; they do not need a device or the Shizuku service.

## What changed in this fork

### Added

- New identity: package `com.empty.pigu`, label **Pigu**, and a new adaptive icon.
- Redesigned home screen: the standard **Pigu** app-bar title and a rich service card with a color-coded status emblem, **Running / Stopped / Error** label, service version/mode detail, and context-aware actions (**Stop** while running, **Start service** when stopped or errored). The card keeps the standard card surface; only the status emblem carries the state color. It also has a **Pairing methods** icon action while running; the separate Pairing methods card is hidden, leaving the service card, a compact **Apps with access** summary, and a collapsible **App & service details** card (Pigu version, server version, Android version, ABI, pairing method, authorized apps, companion status, start-on-boot, TCP mode, and TCP port).
- A separate pairing-methods page with wireless, computer/ADB, and root cards. Root is shown but disabled when no root is detected.
- A **Developer** page grouped into **Apps and access**, **Tools**, and **Diagnostics and help**, containing authorized applications, terminal setup, automation intents, the companion status, a **Copy diagnostics** card, and the developer guide.
- The optional **Pigu Companion** app.

### Phase 0 hardening

The first hardening pass changes decisions around the existing architecture rather than adding a new platform.

**Permission prompt**

- Validates that the request has a positive UID and PID, that `ApplicationInfo.uid` matches the requesting UID, and that the package name is well-formed. Invalid requests are denied without showing a prompt. Android's package list is used for shared-UID disclosure but is not treated as proof of ownership, because package visibility can hide a valid caller from it.
- Shows the package name and any other packages sharing the UID, so a shared UID cannot hide behind a friendly label.
- Uses only the first line of the app label, removes bidi and control characters, bounds it to 500 characters, and HTML-escapes it before rendering.
- Blocks touches Android reports as obscured, hides non-system overlays on Android 12+, and disables both answers for 500 ms after the dialog appears. This is a small deliberate delay; it prevents a tap already traveling from answering a prompt the user has not seen.

**Watchdog lifecycle**

- Only an explicit stop (the notification's **Turn Off** action or the Settings toggle) clears the watchdog preference.
- `onDestroy()` no longer writes `false`, so a system kill, task removal, or ordinary process death cannot silently disable the user's watchdog setting.

**Automation token**

- The token moved from `settings.xml` to a separate `secrets.xml` that is excluded from cloud backup and device transfer. An existing token is migrated once and is not regenerated.
- Missing or invalid token notifications are limited to one per five minutes. A valid token is still accepted immediately, and the rate limit is persisted across process restarts.

**Boot claim**

- A per-user, per-boot claim prevents duplicate `BOOT_COMPLETED` deliveries from starting the service twice. If `BOOT_COUNT` is unavailable, Pigu falls back to the previous start-on-every-delivery behavior.

**Diagnostics**

- The Developer page can copy a snapshot of the app version, Android build, device model/ABI, service state, permissions, debugging/TCP state, watchdog state, battery optimization, notification state, and companion state.
- The snapshot omits the auth token, Wi-Fi names, IP addresses, and app lists. It is generated locally and only copied to the clipboard when the user taps the card.

**Unit tests**

- `:manager:testDebugUnitTest` covers the token gate and notification window, permission-request validation and label sanitizing, the boot claim, and the wireless start route.

### Phase 1 reliability

The second phase makes recovery work in the cases where “the binder died” is not the whole story.

**Server watchdog**

- The server watches its own main thread with a heartbeat. If the thread is silent for 60 seconds, or a required system service (`package`, `activity`, `user`, `appops`) disappears, it logs the stuck stack and exits. When the manager watchdog is enabled, that binder death is its cue to start a fresh process.
- This covers a server that still holds a live binder but no longer answers, which binder-death monitoring cannot see.
- An intentional server exit stops the server watchdog first, so a deliberate stop is not reported as a hang.

**Manager recovery**

- The watchdog probes the binder when it starts, when the screen unlocks, and every 60 seconds. This recovers a missed binder-death callback after Pigu's own process was not alive.
- A deliberate stop is recorded. The watchdog will not restart the service until an explicit start request clears it; a transient `RUNNING` observation during shutdown does not.
- If start-on-boot is off and the server has not run in the current boot, a dead binder is not treated as a crash. A reboot therefore does not silently start a service the user did not ask for.
- Restart attempts are bounded: one attempt at a time, a 90-second cooldown, at most three attempts, and a three-minute in-flight guard. After that the watchdog posts a **Watchdog paused** notification and waits for a manual start or for the watchdog to be turned off and on again.
- A `STARTING` state older than three minutes, or a `STOPPING` state older than one minute, is re-probed instead of disabling recovery forever.
- The diagnostics snapshot also reports whether the service was stopped deliberately and whether it has run in the current boot.
- Wireless recovery waits for the user to unlock; the unlock receiver retries immediately.

**Duplicate starts**

- Boot and automation start requests share a three-minute in-flight gate. A second request is ignored while one is being handled, and the gate is released as soon as the state reaches `RUNNING`, `STOPPED`, or `CRASHED`.
- The watchdog checks that gate before starting a server of its own, so it does not race an enqueued worker whose start has not set `STARTING` yet.

**Tests**

- `:manager:testDebugUnitTest` now includes the restart-policy cooldown/limit/reset behavior and the duplicate-start gate's expiry and release behavior.

**Limits**

- The server watchdog is deliberately conservative; it kills the process only after a long silence or a missing core service. A main thread that is slow but still beating is not treated as a hang.
- If `BOOT_COUNT` is unavailable, the manager falls back to allowing recovery, so the reboot guard is best-effort.
- A server that repeatedly fails to start makes the watchdog stop retrying; the user must start it manually or toggle the watchdog.

### Phase 2 security and control

The third phase adds the controls that need more than a manager-side preference: app discovery, an explicit phantom-process opt-in, and a more patient wireless port retry.

- The manager-only `getApplications` transaction is now actually enforced as manager-only. Previously it was handled before the normal permission check, so any attached client could ask for the installed-app slice.

**App discovery and companion status**

- App discovery now includes every app that declares the Shizuku permission, not only apps that advertise `V3_SUPPORT` or already have a grant. This makes older clients visible in **App Management** before they ask for access.
- Each app row marks an older client that does not advertise `V3_SUPPORT` when the companion is missing: **May require companion app**. Modern clients are not marked, and the hint disappears once the companion is installed.
- The Developer page shows how many apps are authorized and how many older clients may need the companion; **App Management** lists every discovered app, including unauthorized ones. The companion info dialog explains that this is a heuristic, not a guarantee.

**Optional phantom-process control**

- On Android 12+, **Settings → Advanced → Prevent phantom process killing** runs the documented `settings put global settings_enable_monitor_phantom_procs false` change through the Pigu service.
- It is opt-in and shows a warning that the change is global: it affects every app, can increase battery use, can let runaway background processes continue, and does not override an OEM's own process manager.
- Every change is read back and verified. Turning the option off runs `settings delete`, restoring Android's default rather than writing a second opinion. The preference is only available while the service is running.
- The diagnostics snapshot records the cached setting, not a live binder call.

**Wireless port retry and verification**

- The fixed five-attempt, one-second retry is replaced with a seven-attempt backoff schedule (0, 0.5, 1, 2, 4, 8, and 12 seconds). A socket timeout is retried instead of being treated as fatal.
- After `tcpip:<port>`, Pigu waits briefly for Android to report the new TCP port, then verifies the port by actually connecting. If the new port never comes up, it retries the original port before failing, which covers a partial TCP-mode switch.
- The wait is still bounded. A device that accepts neither port now fails after tens of seconds instead of ten; each socket timeout can extend that, and the worker/notification path reports the failure as before.

**Tests and limits**

- `:manager:testDebugUnitTest` now also covers the port retry schedule's ordering, bounds, and invalid input, plus the discovery summary's companion-candidate count.
- The companion hint is a heuristic based on `V3_SUPPORT`. A modern client can still hard-code the official package, and a legacy client can work without the companion.
- Phantom-process control is a global platform workaround. It does not guarantee that Pigu survives an OEM battery manager, and Android may ignore or reset the setting after an update.
- The wireless retry improvements do not add an endless mDNS hunt. If both the new and original ports fail, the user still has to re-pair or rediscover.

### Phase 3 access, setup, and diagnostics

This pass is entirely manager-side; there are no server protocol changes. It adds accountability, safer app management, faster access through shortcuts, a skippable setup flow, a single local-network permission gate, and a checklist for the environmental failures that make Pigu look broken.

**Access log**

- A local ring buffer of the last **400** access events. It is stored in its own `audit_log.xml` preferences file, which is not included by either backup descriptor, so it is not uploaded by cloud backup or device transfer.
- Recorded events: permission prompt shown, allowed, and denied; single and bulk grants/revokes; automation `START`/`STOP`; companion install attempts and signature conflicts.
- The Developer page has an **Access log** card that opens a sheet with the newest events first, a **Copy** action, and a **Clear** confirmation.
- The App Management **Denied** filter shows apps that are not currently granted and whose last recorded permission decision was a denial. It is a view of the log, not a separate persistent denial state: until “remember denial” exists, an app can still ask again, and clearing the log resets the filter.
- The Service Doctor shows the log count and the number of denied events, and can open the same sheet. The log is a diagnostic aid, not a security boundary; a user with root can edit app data.

**Root-mode warning**

- The first root start shows a confirmation explaining that every authorized app can execute commands as root. The acknowledgement is stored in `secrets.xml`, which is excluded from backup, so restoring a configuration on another device does not silently suppress the warning.
- A persistent **Running as root** card appears on the home screen whenever the service UID is 0, with the same explanation.
- Background root starts (automation, start-on-boot, watchdog recovery) are blocked until the warning is acknowledged; they post a notification instead of starting silently.
- The acknowledgement resets when app data is cleared. That is deliberate, but it means root automation will wait for one in-app confirmation after a data reset.

**Safer bulk actions and app rows**

- “Grant all” and “Revoke all” now require a confirmation showing how many apps are affected. The operation runs off the main thread with a progress dialog and reports how many succeeded or failed.
- Bulk actions apply to the **currently filtered list**, not necessarily every installed app. The confirmation count makes that explicit.
- Tapping an app row opens a detail sheet; the switch is the only access toggle on the row. This avoids revoking an app by tapping anywhere on it.
- A single revoke shows an **Undo** snackbar. Undo is not offered for bulk operations, where the confirmation is the safety mechanism.
- The app list uses the granted UID set computed by the view model instead of making one binder call per row, and the select-all state is computed in memory rather than by querying every app on every bind.

**Companion signature semantics**

- `CompanionApp.isInstalled()` now means “the official package is installed **and** signed by Pigu.” Another app that owns `moe.shizuku.privileged.api` no longer suppresses the “May require companion app” hints or counts as a usable companion.
- The companion card still shows the signature conflict and offers Android’s app-info page so the conflicting package can be removed.

**App Management: search, filters, sorting, details**

- Toolbar search matches the app label or package name.
- Filter chips: **All**, **Allowed**, **Denied** (not granted and last request denied), and **Companion** (older client without a usable companion).
- Sort by name or recently used. “Recently used” uses the newest access-log entry for the app; if the log is cleared, the order falls back to name order.
- Per-app detail sheet: icon, label, package, UID/user, version, V3/legacy client type, root requirement, companion hint, last recorded request, access switch, **Open app**, and **App info**.

**Service Doctor**

- A checklist built from the same checks as diagnostics, with an action for each problem:
  - service running, deliberately stopped, or stopped
  - notifications allowed
  - battery optimization
  - local-network permission on Android 16/17
  - wireless debugging
  - developer-options restriction
  - watchdog preference, running state, and last restart time
  - phantom-process setting
  - companion status and signature conflict
  - access-log denials
  - last start result and transcript
- Actions open the relevant settings, request the local-network permission, request the battery-optimization exemption, start the service, or open the access log.
- **Copy diagnostics** is available from the Service Doctor toolbar.
- The last Starter transcript is persisted in the app’s private files directory (capped at 64 KB) instead of being lost when the Starter screen closes. The Doctor can show and copy it. The transcript is local-only and can contain paths and ports, so it is not added to the diagnostics snapshot automatically.

**Shortcuts and restart**

- Static long-press shortcuts: **Start service**, **App Management**, and **Developer page**. Start uses the last working method and falls back to wireless discovery.
- The service card has **Stop** while the service is running. To restart, stop and then use Start service; root and TCP starts are quick, while wireless starts open the Starter flow.
- Stop-then-start is the way to replace a stale server after a Pigu update. It is not a hot swap; clients lose access for the few seconds between stop and start.

**Settings access control**

- **Behavior** now contains only start-on-boot and the watchdog. The access log and **Revoke all app grants** live in a new **Access control** category. **Networking & ADB** is unchanged.
- **Revoke all app grants** lists every app that currently has a grant, shows the count in the confirmation, runs off the main thread, and records one bulk audit entry. It does not stop the service and does not revoke Pigu itself.
- The action requires a running service; without one it explains why instead of failing with a binder error. There is no bulk undo, matching the App Management bulk-action policy.

**Permissions without a first-run gate**

- Pigu opens directly to the home screen. Notifications, local-network access, and the battery-optimization exemption are requested at the point they are needed, so there is no separate setup pass to complete or skip.
- If a permission is denied, the relevant flow explains the failure and offers the system settings shortcut.

**Central local-network permission gate**

- `LocalNetworkPermission` is now the single SDK mapping and request helper: Android 17 (API 37) uses `ACCESS_LOCAL_NETWORK`, Android 16 (API 36) uses `NEARBY_WIFI_DEVICES`, and older releases need neither.
- Activity paths request the permission and continue only after a grant: wireless Start, the ADB discovery dialog, the pairing dialog, the pairing tutorial, and the Starter discovery fallback. A denial stops discovery and shows an explanation instead of opening Android's device picker loop.
- `AdbStartWorker` checks the same helper before mDNS. If the permission is missing it skips discovery, releases the start gate, and posts an actionable notification that opens **Pairing methods**; it does not retry a blocked search forever.
- `PiguDiagnostics` uses the same mapping and reports `NEARBY_WIFI_DEVICES` on API 36 instead of “not applicable”; API 35 and below report that the permission is not required.
- The activity request uses a headless fragment, so callers do not duplicate permission-result plumbing. If the requesting activity is destroyed while the system dialog is open, the original action is not resumed automatically; the next tap re-checks the permission.

**Manual start over mobile data**

- Manual and automation starts no longer inherit the old unconditional “wait for an unmetered network” rule. `AdbStartWorker` now takes an explicit `allowMetered` flag; `ManualStartReceiver` and the notification’s **Attempt now** action pass `true`, while boot and watchdog recovery keep waiting for Wi-Fi.
- This restores the behavior introduced by the earlier “intent only working on wifi” fix: a manual `START` intent may run over mobile data when no Wi-Fi network is available.
- The worker still has to resolve the wireless-debugging port. Mobile carriers can block multicast, and Android 16/17 still requires the local-network permission, so the permission gate and the Starter timeout remain unchanged. If mDNS cannot resolve, the Starter screen reports the timeout instead of silently waiting.

**Start over local TCP**

- Pairing methods now has a **Start over local TCP** card. It opens a port dialog prefilled with Pigu’s configured TCP port and skips mDNS and the local-network permission path entirely.
- The start uses `StarterActivity` with an exact-port flag, so Pigu connects to `127.0.0.1:<port>` and does not switch adbd to a different configured TCP port.
- This only works if adb is already listening on that port. Typical ways to get there: run `adb tcpip 5555` while USB-connected, or keep TCP mode from an earlier wireless start. The action does **not** open the port or make it survive a reboot.
- If nothing is listening, the normal connection dialog is replaced with a specific error naming the port and repeating the `adb tcpip` requirement. This is intentionally explicit, not an automatic fallback to discovery.
- No mDNS, no port scan, no hidden retry: one connect attempt on the exact port the user confirms. No new JVM tests; the path is an activity/UI integration around the existing `AdbStarter`.

**Permission prompt binder wait**

- `RequestPermissionActivity.waitForBinder()` no longer uses `runBlocking` on the main thread with a 5-second timeout in `onCreate`. The wait is now a suspend function inside `lifecycleScope`, so a late binder no longer freezes the UI before the permission dialog appears.
- The timeout and denial behavior are unchanged: if the server is still unavailable after 5 seconds, the request is recorded as denied and the activity finishes. If the activity is destroyed first, the coroutine is canceled.
- This is the only remaining item from the earlier main-thread binder list. `ToggleAllViewHolder` already performs bulk changes off the main thread with progress, and App Management rows already use the cached granted-UID set instead of calling `granted()` per bind.
- `checkSelfPermission()` still performs its short binder call on the main thread when the coroutine resumes. The expensive 5-second wait is no longer there, but that call has not been moved to a background dispatcher.

**UI polish for mobile and TV**

- The home app bar is back to the standard **Pigu** title. The service card carries the color and status instead.
- The service card now shows a state-colored status emblem, a normal **Running / Stopped / Error** title, and a detail line: version and `adb`/`root` mode while running, “Service is not running” when stopped, or the error state.
- While running it shows **Stop**, plus a **Pairing methods** link icon. When stopped or errored it shows a full-width **Start service** button and no link icon, because the Start button already opens the start-methods screen. The old Pairing methods card is hidden for now.
- During **Starting** and **Stopping** the card shows the transition state without action buttons.
- A compact **Apps with access** summary sits below the service card while the service is running. It shows a count such as `9 of 14 apps authorized`; tapping it opens App Management. The card is hidden while stopped, where the service card is the only action that matters. The service card uses the same surface as every other card; only its status emblem changes color.
- The **App & service details** card toggles locally in the view holder instead of going through `notifyItemChanged()`, so repeated expand/collapse taps are immediate and reliable. Its expanded state still survives service/app refreshes and rotation, and the card remains a single focusable target for TV remote navigation.
- `HomeViewModel` publishes a cheap status snapshot before the slow SELinux/permission checks. Binder callbacks are marshalled to the main thread before touching the adapter, and `onResume` reconciles a stale `STOPPED` state, so the service card updates immediately after stop/start instead of several seconds later or not at all.
- Pairing methods applies bottom/start/end insets to its scrolling content, keeping the last card reachable above the navigation bar.
- The local TCP dialog now selects the prefilled port, clears its error while editing, exposes a helper line, supports the IME Done action, and focuses the port field when opened.
- The revoke-all-grants summary now states that it has no undo and requires a running service.
- The Developer page is now split into three labelled groups instead of eight equally weighted cards: **Apps and access** (manage apps, companion, access log), **Tools** (terminal, automation intents), and **Diagnostics and help** (Service Doctor, copy diagnostics, developer guide). Section headings span both columns on the tablet/TV grid and are not focusable, so D-pad navigation still moves card-to-card.
- These changes use standard Material focusable controls, so existing mobile touch, TV D-pad, and accessibility behavior is preserved.

**Tests and limits**

- `:manager:testDebugUnitTest` now has **53** tests, including the App Management filter policy and the SDK 36/37 local-network mapping. The previous pass had 50.
- The access log is local and clearable, but it is not tamper-proof and does not record callers that never reach the permission activity.
- There is no persistent “Deny” state yet: the **Denied** filter combines “not currently granted” with “last request denied.” Adding remembered denial would change the permission flow and is a separate decision.
- Root background starts now require one UI acknowledgement. This is a deliberate security trade-off, not an oversight.
- The App Management bulk operations only touch the visible/filtered list, and undo is only offered for a single revoke.
- Runtime checks on a **Vivo V2465A (Android 16)** over wireless ADB confirmed the restored **Pigu** title, the rich service card for **Running** and **Stopped** (status icon/title, version/mode detail, Stop or Start service), the **Pairing methods** link icon visible only while running and opening the methods screen, the hidden Pairing methods card, rapid expand/collapse taps without a rebind race, the card updating within about half a second after stop/start, Developer section headings, and the two-column/tablet layout (simulated with `wm size`/`wm density`, then restored). The **Error** card, shortcuts, restart flow, sheets, chips, root banner, Service Doctor actions, and actual TV overscan/remote behavior still need dedicated checks.
- A CPH2791 (Android 16) run confirmed the **Apps with access** summary, its authorized count, and tapping it to open App Management.
- The static shortcuts embed the `com.empty.pigu` application ID, so changing the application ID requires updating `res/xml/shortcuts.xml`.
- Restarting is a manual stop-then-start, not a hot swap. If the Starter screen is canceled or the new start fails, the service stays stopped until the user starts it again.
- If notifications are denied, the worker still cannot post the local-network notification; the user must open Pigu and start from **Pairing methods**.
- Lint has the pre-existing manager errors (mostly `Instantiatable` false positives) and 4 server `NewApi` errors. The changed files add no new lint errors.
- API 35 and below do not require a local-network runtime permission. API 36 and 37 do; code degrades to a notification when a background path cannot request it.

### Removed

- The first-run onboarding activity, layout, strings, and completion flag. Pigu now opens directly to the home screen.
- Stealth/hidden mode and its clone/stub APK tooling.
- The in-app update checker and its APK repackaging/re-signing code.
- Translation resources and the Crowdin configuration.
- The three-dot overflow menu (Stop moved to the home service-actions card; About removed).
- Update/stealth-only dependencies and permissions.

## Credits

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — the original Shizuku app and API.
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) — the maintained fork this project is based on; start-on-boot, TCP mode, watchdog, TV/VR support, and MediaTek fixes come from that work.
- [8mpty/Pigu-API](https://github.com/8mpty/Pigu-API) — the API submodule used by this build; it is an unmodified base of thedjchi/Shizuku-API.
- [d4rken-org/porter](https://github.com/d4rken-org/porter) — the companion-app compatibility approach and the idea of reporting which apps need it.
- All upstream contributors and translators whose work these projects build on.

## License

All code files in this project are licensed under [Apache 2.0](LICENSE). The Shizuku API submodule retains its own license.
