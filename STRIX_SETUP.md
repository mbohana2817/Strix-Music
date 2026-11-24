# Strix Music — first rebrand build

This fork is being rebranded from Blazify. The first pass changes the Android application ID to `com.strix.music`, the displayed app name to `Strix Music`, and the adaptive launcher icon to a purple waveform mark. The original Kotlin namespace (`com.blazify.music`) is intentionally retained for this first build so that existing source imports, generated classes, Room schema references, and app internals are not mass-renamed before the app is confirmed to build.

## Build an APK with GitHub Actions

1. Push this project to your GitHub repository's `main` or `master` branch.
2. Open the repository's **Actions** tab.
3. Select **Build Strix Music APK**.
4. Choose **Run workflow** (or wait for a push-triggered run).
5. When the run finishes successfully, open the run and download the `Strix-Music-debug-apk` artifact.
6. Extract the artifact ZIP and install the APK on an Android device (Android 8.0 / API 26 or newer).

The workflow builds the `fossDebug` variant. This is a test APK, not a Play Store release. It uses a debug signing key and is intended for installation/testing. A future public release should use a private release keystore stored as GitHub Actions secrets. Never commit a keystore or its passwords.

## Notes

- The app's Kotlin/Gradle namespace remains `com.blazify.music` intentionally for now; the installed package/application ID is `com.strix.music`.
- Existing Blazify-specific integrations, attribution, and upstream links have not all been rebranded in this first pass. Review them before publishing.
- Preserve the upstream GPL-3.0 license and notices. If you distribute a modified binary, comply with GPL-3.0 source and notice requirements.
- The cloud workflow may need adjustment if the upstream project updates its compile SDK, Android Gradle Plugin, or build tools.
