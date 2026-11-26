# Strix Music — iOS Glass / Splash / Stability Pass

## Changes
- Reworked the native bottom navigation into a fixed iOS-style glass capsule with a single animated selection indicator that slides between tabs.
- Removed the Material 3 selected-indicator overlay that previously stacked with the custom pill and made the active tab look muddy.
- Increased navbar surface opacity and clarified active icon/label contrast; the glass surface remains translucent without applying a blur to the tab contents.
- Splash GIF now uses `ContentScale.Fit`, so the complete 16:9 animation is visible instead of being aggressively cropped.
- Splash duration now follows the 3-second GIF rather than holding the screen for an extra 1.5 seconds.
- The in-app GIF splash only appears on a fresh Activity launch, not when Android restores the Activity after opening another app.
- Removed the outdated static cold-start logo layer and unified the splash background to dark Strix styling. Android 12+ still displays its brief system-owned launch screen before app content; Android does not support replacing that system screen with a full GIF.
- Replaced the unrelated stock developer headshot included in this ZIP with Strix artwork. The actual user portrait was not present in the uploaded archive.
- Reduced Coil's image memory-cache budget from 15% to 10% to reduce memory pressure when switching to camera or other large apps.
- Added `termux-push.sh` to overlay the ZIP into an existing local Git clone, preserve `.git`, validate staged whitespace, commit, and push without force-pushing.

## Validation
- All 408 XML resource files parse successfully.
- Full Android/Gradle compilation was not run in this environment. Push the source and let the repository's GitHub Actions workflow build the APK.
- Reducing image-cache pressure is a stability mitigation, not proof of the cause of an Android process exit. If Android still closes the app, a crash report/logcat is needed to distinguish an app exception from Android reclaiming the process while another app is foregrounded.
