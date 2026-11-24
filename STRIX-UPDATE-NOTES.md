# Strix Music UI / Branding Update

Based on the user-supplied `Strix-Music-main (1).zip` source tree. Existing Gradle wrapper, Android application configuration, product flavors, and GitHub Actions build workflow were retained.

## Updated
- Replaced adaptive, static, density-specific launcher artwork and notification fallback icon with a Strix waveform mark on a burgundy background.
- Updated cold-start splash background and Android 12+ splash icon to Strix branding.
- Updated in-app logo assets used by the home header, onboarding, player placeholders, and About screen.
- Updated visible home and About wordmarks to `Strix Music`.
- Set the default accent to Strix Burgundy and added Midnight Plum, Rosewood, Deep Ocean, and Forest palette presets. Existing custom color picker remains available.
- Restyled the bottom navigation as a floating translucent glass-like capsule with a soft outline and shadow.
- Added subtle size/layout animation to settings rows and retained the existing Look & Feel live preview and Crossfade tab transitions.
- Updated About/developer metadata and repository links to Muhammad Bohana (Han) / `mbohana2817`, including the supplied Al-Hidayah Cirebon, TKJ 2 description.
- Kept the upstream open-source license and contributor notices intact.

## Validation
- XML resources parsed successfully.
- Launcher image assets were regenerated for all Android density buckets.
- Full Gradle build was not run in this environment because `services.gradle.org` could not be reached. Run the retained GitHub Actions workflow to compile and produce the APK.

## Additional personalization pass (v3)
- Added a persisted app-wide background atmosphere selector in Look & Feel: Strix Burgundy, Midnight Plum, Deep Ocean, Forest, and AMOLED.
- The selected background updates Material 3 page and container surfaces throughout the app; choosing a colored preset disables Pure Black, while AMOLED enables it.
- The Look & Feel phone preview now follows the selected app background.
- Added spring-based scale animation to bottom-navigation selection for a softer, more responsive transition.
- Existing mini-player background controls remain separate from the app-wide background selector.
