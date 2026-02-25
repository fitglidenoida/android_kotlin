# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

FitGlide is an Android-native health & fitness tracking app (Kotlin / Jetpack Compose). The backend is a remote Strapi CMS at `https://admin.fitglide.in/api/` — it is **not** part of this repository.

### System dependencies (pre-installed in the VM snapshot)

- **JDK 17** (`/usr/lib/jvm/java-17-openjdk-amd64`) — required by the Gradle `jvmToolchain(17)` directive.
- **Android SDK** at `/opt/android-sdk` with platform `android-35` and build-tools `35.0.0`.
- Environment variables `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `JAVA_HOME` are set in `~/.bashrc`.
- `local.properties` (gitignored) must contain `sdk.dir=/opt/android-sdk`. The update script creates it automatically.

### Common Gradle commands

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew assembleDebug` |
| Run unit tests | `./gradlew testDebugUnitTest` |
| Run lint | `./gradlew lintDebug` |
| Clean build | `./gradlew clean` |

All commands should be run from the repository root (`/workspace`).

### Non-obvious caveats

- **Lint has a pre-existing error**: `MissingDefaultResource` for `fitglide_logo_tweaked.png` in `drawable-night` with no base-folder declaration. This causes `lintDebug` to return exit code 1. The lint baseline file is at `app/lint-baseline.xml`.
- **No Android emulator**: The cloud VM has no KVM/hardware acceleration, so running an Android emulator for instrumented tests or GUI testing is not feasible. Use `testDebugUnitTest` for local JVM tests only.
- **Gradle daemon**: Use `--no-daemon` in CI-like environments to avoid orphaned daemon processes. In interactive sessions the daemon is fine.
- **First build downloads Gradle 8.10.2** (~200 MB) and all dependencies. Subsequent builds use the Gradle cache under `~/.gradle/`.
