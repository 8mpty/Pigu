# Building and testing

Build Pigu from source, including the Shizuku API submodule. The submodule is documented in [API-SUBMODULE.md](../API-SUBMODULE.md).

The build expects **JDK 21**, **Android SDK 37**, and the NDK version configured in the root `build.gradle`.

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

## Tests

Run the JVM unit tests for the pure decisions:

```bash
./gradlew :manager:testDebugUnitTest
```

They cover the automation token gate and notification throttle, permission-request validation and label sanitizing, the boot claim, the wireless start route, the watchdog restart policy, the duplicate-start gate, the wireless port retry schedule, app-discovery companion counts, and the App Management filter policy. The suite currently has 53 tests. These are plain JVM tests; they do not need a device or the Shizuku service.

## Lint

Android Lint runs with:

```bash
./gradlew :manager:lintDebug
```

Lint has pre-existing findings in this fork, and `checkReleaseBuilds = false` means normal APK builds do not run it. Its report is useful for catching new issues, but it is not currently a clean gate.

---

[Back to README](../README.md)
