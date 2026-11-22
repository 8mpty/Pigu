# Privacy and permissions

Pigu is local-only by design. The details below cover storage, backup exclusions, and every declared permission.

## Data and storage

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

## Permissions

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

---

[Back to README](../README.md)
