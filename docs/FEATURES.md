# Features

Pigu is a personal Shizuku-compatible manager. This page lists the full feature set; the main [README](../README.md) has the short version.

- **Three ways to start the service:** wireless debugging (including Android 11+ pairing), a computer via `adb`, or root. A wireless-debugging pairing appears as **Pigu** in Android's Paired devices list.
- **Direct launch to the home screen:** no first-run setup pass; permissions are requested only when the relevant flow needs them.
- **Start / stop control** from the home screen: stop has a confirmation. To restart, stop and start again; there is no one-tap Restart button.
- **No global pause or Quick Settings tile:** stopping the service is the way to cut access; there is no one-tap pause switch.
- **Accessibility services (Developer → Tools):** a read-only list of installed services, with an opt-in keep-alive for services that are already enabled and explicitly pinned. Pigu never provides an enable/disable switch for accessibility services; use Android settings for that.
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

---

[Back to README](../README.md)
