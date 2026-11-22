# Companion app

The optional companion is a package-identity shim, not a replacement for Pigu.

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

## Limitations

The companion is a package identity stub, not a full permission replacement:

- Apps that call `getPermissionInfo("moe.shizuku.manager.permission.API_V23").packageName` still see `com.empty.pigu`, because Pigu owns that permission. Making the companion own it would make the companion mandatory, as [Porter](https://github.com/d4rken-org/porter) does.
- Apps that verify the official Shizuku signing certificate will not accept Pigu or the companion.
- Apps that only check `getPackageInfo("moe.shizuku.privileged.api")` or use `Shizuku.pingBinder()` work with the companion.

## Android 17 debugging settings

Android 17 QPR3 and later can require `MANAGE_DEBUGGING` for USB and wireless debugging settings. That is a `signature|privileged` permission, so Pigu cannot hold it even with `WRITE_SECURE_SETTINGS`.

On those builds, Pigu treats these writes as best-effort:

- **Auto-disable USB debugging** and **Keep wireless debugging on** may not take effect automatically.
- Pigu can still start and use the service when the required debugging mode is already enabled.
- Settings shows a **Developer options** shortcut so the user can enable or disable USB/wireless debugging manually.
- Background-start failures show a notification with the same shortcut.

Older Android versions keep the previous automatic behavior.

---

[Back to README](../README.md)
