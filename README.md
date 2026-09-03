# PING

A tiny native Android app that pings a host. Real ICMP, no root, no ads, no trackers.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:installDebug         # install on a connected device
./gradlew spotlessCheck lint testDebugUnitTest assembleDebug   # full local check
```

Requires a JDK 17+ (Android Studio's bundled JBR works). The Android SDK path is
read from `local.properties` or `$ANDROID_HOME`.

## Release

1. Create an upload keystore (once):
   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 4096 \
     -validity 10000 -alias upload
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in.
3. `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

## Privacy & compliance

PING collects nothing. See [PRIVACY.md](PRIVACY.md).
Play Store / Android developer-verification checklist: [docs/COMPLIANCE.md](docs/COMPLIANCE.md).

## License

GPL-3.0-or-later. Copyright (C) 2026 Lysak.

## Credits

Visual design, theme tokens, the Inter font bundling and the wave animation are
adapted from [metiq](https://github.com/metiq-xyz/android-app) (also GPL-3.0).
