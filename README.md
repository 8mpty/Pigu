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
- **Watchdog** that restarts the service if it crashes.
- **Start on boot**, waiting for Wi-Fi when needed.
- **Automation intents** for Tasker, MacroDroid, Automate, etc.
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

The optional **Pigu Companion** is a separate, non-launchable app that uses that old package name. It does not run a service, does not request permissions, and has no UI. Its only job is to make those hard-coded package checks succeed; Pigu still starts the service and handles permission prompts.

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

## Privacy

- No tracking, analytics, or telemetry.
- No proprietary libraries.
- No Google Play Services.
- Internet access is only used for wireless debugging connections.
- No update checker; updates are installed manually.
- Only the permissions needed for the service and wireless pairing are declared.

### Permissions

- **INTERNET:** wireless debugging and local ADB connections.
- **ACCESS_NETWORK_STATE:** waits for Wi-Fi before background starts.
- **ACCESS_LOCAL_NETWORK / USE_LOOPBACK_INTERFACE:** Android 16/17 local-network access for mDNS and loopback.
- **NEARBY_WIFI_DEVICES / CHANGE_WIFI_MULTICAST_STATE:** mDNS discovery for wireless ADB and pairing.
- **POST_NOTIFICATIONS:** pairing and service status notifications.
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
manager/build/outputs/apk/debug/pigu-v13.7.0-debug.apk
manager/build/outputs/apk/release/pigu-v13.7.0-release.apk
companion/build/outputs/apk/debug/pigu-companion-v13.7.0-debug.apk
companion/build/outputs/apk/release/pigu-companion-v13.7.0-release.apk
```

Release builds use `signing.gradle` and `signing.properties`; without them they fall back to the debug keystore. Pigu and its companion must be signed with the same certificate so Pigu can recognize the companion.

You can build both at once:

```bash
./gradlew :manager:assembleDebug :companion:assembleDebug
```

The `:manager:assembleDebug` task produces a debuggable server. Attach a debugger to `shizuku_server`; in Android Studio enable *Run/Debug configurations → Always install with package manager* so the installed server matches the latest code.

## What changed in this fork

### Added

- New identity: package `com.empty.pigu`, label **Pigu**, and a new adaptive icon.
- Redesigned home screen: service status with a stop button, a single **Pairing methods** card, and an **App & service details** card (including Pigu version, server version, Android version, ABI, pairing method, authorized apps, companion status, start-on-boot, TCP mode, and TCP port).
- A separate pairing-methods page with wireless, computer/ADB, and root cards. Root is shown but disabled when no root is detected.
- A **Developer** page containing authorized applications, terminal setup, automation intents, the companion status, and the developer guide.
- The optional **Pigu Companion** app.

### Removed

- Stealth/hidden mode and its clone/stub APK tooling.
- The in-app update checker and its APK repackaging/re-signing code.
- Translation resources and the Crowdin configuration.
- The three-dot overflow menu (Stop moved to the status card; About removed).
- Update/stealth-only dependencies and permissions.

## Credits

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — the original Shizuku app and API.
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) — the maintained fork this project is based on; start-on-boot, TCP mode, watchdog, TV/VR support, and MediaTek fixes come from that work.
- [thedjchi/Shizuku-API](https://github.com/thedjchi/Shizuku-API) — the API submodule used by this build.
- [d4rken-org/porter](https://github.com/d4rken-org/porter) — the companion-app compatibility approach and the idea of reporting which apps need it.
- All upstream contributors and translators whose work these projects build on.

## License

All code files in this project are licensed under [Apache 2.0](LICENSE). The Shizuku API submodule retains its own license.
