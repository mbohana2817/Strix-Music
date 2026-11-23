# Strix Music — first rebrand build

This fork is being rebranded from Blazify. The first pass changes the Android application ID to `com.strix.music`, the displayed app name to `Strix Music`, and the adaptive launcher icon to a Strix waveform mark with a burgundy/pink palette. The original Kotlin namespace (`com.blazify.music`) is intentionally retained for this first build so that existing source imports, generated classes, Room schema references, and app internals are not mass-renamed before the app is confirmed to build.

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
- Internal Kotlin package names and some upstream service integrations remain unchanged to avoid breaking playback, updater, and Listen Together services. The About screen, home branding, GitHub profile/repository links, launcher/splash artwork, and theme palette are branded for Strix Music. Configure your own release assets and update feed before redirecting the in-app updater.
- Preserve the upstream GPL-3.0 license and notices. If you distribute a modified binary, comply with GPL-3.0 source and notice requirements.
- The cloud workflow may need adjustment if the upstream project updates its compile SDK, Android Gradle Plugin, or build tools.


## Strix UI update

- Home/header branding uses the Strix Music wordmark and waveform mark.
- The bottom navigation uses a translucent, rounded glass-style dock with animated selection.
- The default accent is Strix Burgundy; Theme settings include Strix Burgundy, Midnight Plum, and Rosewood palettes alongside the existing color options.
- About identifies Muhammad Bohana (Han), Alumni Siswa Al-Hidayah Kota Cirebon, TKJ 2, and links to the `mbohana2817/Strix-Music` GitHub profile/repository.
