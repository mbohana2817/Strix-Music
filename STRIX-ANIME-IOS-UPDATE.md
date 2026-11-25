# Strix Music — Anime iOS Glass Update

## Changes
- Reworked the native Compose bottom navigation to use a single animated iOS-style glass selection surface; disabled the overlapping default Material pill.
- The existing **Glass Theme · iOS** switch in Theme settings now controls the translucent navigation surface and the selected indicator together.
- Replaced old waveform/Blaze logo references and launcher artwork with the current Strix anime logo.
- Removed the first-install onboarding/welcome overlay.
- The anime splash GIF is shown on each cold app launch. It keeps its original 16:9 ratio with `ContentScale.Fit`, so the whole animation remains visible without center-cropping.
- Added a refined progress bar and “CONNECTING TO STRIX MUSIC” loading copy.
- Disabled Coil memory caching for the short-lived splash GIF to reduce retained image memory.
- Developer portrait now uses the configured GitHub profile avatar URL in both About and Account settings.
- Wired the project-level coffee QR and WhatsApp channel QR into Android drawable resources; added a WhatsApp channel QR dialog in About.

## Notes
- The developer portrait is loaded from `https://github.com/mbohana2817.png`; an internet connection is required for the live avatar. The bundled fallback is the generic person icon, not an unrelated portrait.
- The “Connecting” line is a branded loading message; it does not claim that the app has a dedicated Strix backend.
- This source update has not been compiled into an APK in this environment. Build it with the repository's GitHub Actions workflow after pushing.
