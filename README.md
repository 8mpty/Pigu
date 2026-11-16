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

- **Three ways to start the service:** wireless debugging (including Android 11+ pairing), a computer via `adb`, or root.
- **Start / stop control** from the home screen, with a confirmation dialog when stopping.
- **TCP mode** (after an ADB-over-Wi-Fi start), so the service can be restarted without Wi-Fi.
- **Option to keep wireless debugging on** after connecting, with a warning about the security trade-off.
- **Watchdog** that restarts the service if it crashes.
- **Start on boot**, waiting for Wi-Fi when needed.
- **Hardened permission prompt** that validates the caller, blocks obscured taps, discloses shared UIDs, and ignores taps for 500 ms after it appears.
- **Automation intents** for Tasker, MacroDroid, Automate, etc.
- **Copy diagnostics** on the Developer page for privacy-conscious bug reports.
- **Android TV and VR support**, including D-Pad navigation and pairing without notifications.
- **Android 17 / SDK 37 compatibility** inherited from the base fork.
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
- **System installer** — writes the bundled APK to Pigu's cache and opens Android's package installer, so the install is confirmed by the system.

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

On those builds, Pigu treats these writes as best-effort:

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
- The diagnostics snapshot contains app, device, and service state only. It never includes the auth token, Wi-Fi names, IP addresses, or app lists.
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

Release builds use `signing.gradle` and `signing.properties`; without them they fall back to the debug keystore. Pigu and its companion must be signed with the same certificate so Pigu can recognize the companion.

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

They cover the automation token gate and notification throttle, permission-request validation and label sanitizing, the boot claim, and the wireless start route. These are plain JVM tests; they do not need a device or the Shizuku service.

## What changed in this fork

### Added

- New identity: package `com.empty.pigu`, label **Pigu**, and a new adaptive icon.
- Redesigned home screen: service status with a stop button, a single **Pairing methods** card, and an **App & service details** card (including Pigu version, server version, Android version, ABI, pairing method, authorized apps, companion status, start-on-boot, TCP mode, and TCP port).
- A separate pairing-methods page with wireless, computer/ADB, and root cards. Root is shown but disabled when no root is detected.
- A **Developer** page containing authorized applications, terminal setup, automation intents, the companion status, a **Copy diagnostics** card, and the developer guide.
- The optional **Pigu Companion** app.

### Phase 0 hardening

The first hardening pass changes decisions around the existing architecture rather than adding a new platform.

**Permission prompt**

- Validates that the request has a positive UID and PID, that `ApplicationInfo.uid` matches the requesting UID, and that the package name is well formed. Invalid requests are denied without showing a prompt. Android's package list is used for shared-UID disclosure but is not treated as proof of ownership, because package visibility can hide a valid caller from it.
- Shows the package name and any other packages sharing the UID, so a shared UID cannot hide behind a friendly label.
- Uses only the first line of the app label, removes bidi and control characters, bounds it to 500 characters, and HTML-escapes it before rendering.
- Blocks touches Android reports as obscured, hides non-system overlays on Android 12+, and disables both answers for 500 ms after the dialog appears. This is a small deliberate delay; it prevents a tap already travelling from answering a prompt the user has not seen.

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

### Removed

- Stealth/hidden mode and its clone/stub APK tooling.
- The in-app update checker and its APK repackaging/re-signing code.
- Translation resources and the Crowdin configuration.
- The three-dot overflow menu (Stop moved to the status card; About removed).
- Update/stealth-only dependencies and permissions.

## Credits

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — the original Shizuku app and API.
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) — the maintained fork this project is based on; start-on-boot, TCP mode, watchdog, TV/VR support, and MediaTek fixes come from that work.
- [8mpty/Pigu-API](https://github.com/8mpty/Pigu-API) — the API submodule used by this build; it is an unmodified base of thedjchi/Shizuku-API.
- [d4rken-org/porter](https://github.com/d4rken-org/porter) — the companion-app compatibility approach and the idea of reporting which apps need it.
- All upstream contributors and translators whose work these projects build on.

## License

All code files in this project are licensed under [Apache 2.0](LICENSE). The Shizuku API submodule retains its own license.
