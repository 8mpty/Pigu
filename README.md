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

- **Start your way:** wireless debugging (including Android 11+ pairing), a computer via `adb`, or root. A wireless-debugging pairing appears as **Pigu** in Android's Paired devices list.
- **Simple home controls:** start/stop the service with a confirmation, see the service state, authorized-app count, and collapsible Pigu/Shizuku/device details. There is no pause or Quick Settings tile in 2.0.0.
- **Access control:** a hardened permission prompt, App Management with search/filter/sort and per-app details, a local access log, “revoke all app grants,” and a persistent root-mode warning.
- **Automation:** intents for Tasker, MacroDroid, Automate, etc.; TCP mode; and a direct local-TCP start from Pairing methods.
- **Reliability:** a two-layer watchdog, bounded restart recovery, start-on-boot, and guards against duplicate or accidental starts.
- **Diagnostics:** Service Doctor, copyable diagnostics, and the last Starter transcript for bug reports.
- **Compatibility:** Android 7.0+, TV/VR and D-pad support, Android 17 / SDK 37 compatibility, one local-network permission gate, and an English-only UI.
- **Optional companion:** for apps that hard-code the official Shizuku package name.

[Full feature list →](docs/FEATURES.md)

## Requirements

- Android 7.0 or newer.
- **Root mode:** a rooted device.
- **Wireless debugging:** Android 11+ and most Android TVs.
- **PC mode:** any device with `adb` access.
- **Start on boot:** available with wireless debugging or root mode.

## Quick start

1. Enable **Developer options → Wireless debugging** on the device.
2. Open Pigu → **Start service** → **Start via Wireless debugging**.
3. Pair once with the code from Android, then tap **Start**.

**Other start methods**

- **PC:** Pigu → **Start service** → **Start by connecting to a computer** → **View command**, then run the command with `adb`.
- **Root:** Pigu → **Start service** → **Root** (only shown when root is available; confirm the root warning once).

To stop, use **Stop** on the home card. To restart, stop and then start again. Pigu 2.0.0 has no global pause and no Quick Settings tile; stopping the service is the way to cut access.

## Versioning

Pigu uses its own semantic version, independent of the Shizuku API/server version:

- **Pigu app version:** `2.0.0` — the value under **App & service details → Pigu version**.
- **Shizuku API/server version:** `13.6` — the value on the service card and under **Shizuku version**.
- **Version code:** `major * 10000 + minor * 100 + patch` (`2.0.0` → `20000`). Minor and patch stay in `0..99`.
- A `-Pbeta` build appends `-beta` to the displayed version name.

Bump the three numbers in the root `build.gradle` (`piguVersionMajor`, `piguVersionMinor`, `piguVersionPatch`). The API submodule and the `13.6` server compatibility number are not changed by a Pigu release.

## Privacy at a glance

- No tracking, analytics, telemetry, proprietary libraries, or Google Play Services.
- Internet access is only used for wireless debugging connections.
- The access log, automation token, and Starter transcript stay on the device and are excluded from backup.
- Diagnostics never include the auth token, Wi-Fi names, IP addresses, app lists, or the Starter transcript.
- Only the permissions needed for the service and pairing are declared.

[Privacy and permissions →](docs/PRIVACY.md)

## Build

```bash
git clone --recurse-submodules https://github.com/8mpty/Pigu.git
cd Pigu
./gradlew :manager:assembleDebug
```

The optional companion builds with `./gradlew :companion:assembleDebug`. See [Building and testing](docs/BUILDING.md) for release signing, the debug server, tests, and the API submodule.

## Documentation

| Document | Contents |
| --- | --- |
| [Features](docs/FEATURES.md) | The complete feature list and behavior details. |
| [Companion app](docs/COMPANION.md) | Install paths, limitations, and Android 17 debugging settings. |
| [Privacy and permissions](docs/PRIVACY.md) | What is stored, what is excluded from backup, and every declared permission. |
| [Building and testing](docs/BUILDING.md) | Build commands, release signing, the debug server, and JVM tests. |
| [Fork history](docs/HISTORY.md) | The detailed change history and the phases behind the current feature set. |
| [API submodule](API-SUBMODULE.md) | Pigu-API pinning and submodule maintenance notes. |
| [Security policy](SECURITY.md) | The inherited Shizuku security policy and reporting guidance. |

## Credits and inspirations

### Base projects

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — the original Shizuku app and API.
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) — the maintained fork this project is based on; start-on-boot, TCP mode, watchdog, TV/VR support, and MediaTek fixes come from that work.
- [8mpty/Pigu-API](https://github.com/8mpty/Pigu-API) — the API submodule used by this build; it is an unmodified base of thedjchi/Shizuku-API.
- [d4rken-org/porter](https://github.com/d4rken-org/porter) — the companion-app compatibility approach, permission-security discipline, and app-management ideas.

### Inspirations

- [ShizukuPlus](https://github.com/thejaustin/ShizukuPlus) — Service Doctor checklist, Quick Settings/TCP/OTG patterns, and setup UX ideas.
- [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) — watchdog probe/breaker, start status, and wireless port-hunt ideas.
- [Nightzuku](https://github.com/kerneldroid/Nightzuku) — permission-dialog hardening, signature pinning, and server-watchdog ideas.
- [Shevery](https://github.com/HmnDev-Tech/shevery) — starter supervision and server-hang detection ideas.
- [Shizako](https://github.com/cr1437/Shizako) — local access-log ideas.

All upstream contributors and translators whose work these projects build on are gratefully acknowledged.

## License

All code files in this project are licensed under [Apache 2.0](LICENSE). The Shizuku API submodule retains its own license.
