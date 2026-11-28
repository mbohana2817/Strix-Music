# Strix Music — Railway + Fatal Playback Fix

Patch for the current Strix Music source.

## Included
- `app/src/main/kotlin/com/blazify/music/playback/MusicService.kt`
  - Safely rebuilds ExoPlayer when AudioTrackPlaybackParams changes: the old player is no longer released before the replacement is successfully created/prepared.
  - A MediaController initialization failure no longer stops an actively playing service.
  - More defensive service shutdown cleanup.
  - Keeps the playback service sticky while audio is actively playing.
- `app/src/main/kotlin/com/blazify/music/listentogether/ListenTogetherServers.kt`
  - Default Listen Together server changed to the current Strix Railway WebSocket:
    `wss://strix-live-server-production.up.railway.app/ws`

## Apply in Termux

```bash
cd ~/Strix-Music
unzip -o /sdcard/Download/Strix-Music-Railway-FatalPlayback-Fix.zip -d .
chmod +x ./gradlew
git diff --check
git status
git add app/src/main/kotlin/com/blazify/music/playback/MusicService.kt app/src/main/kotlin/com/blazify/music/listentogether/ListenTogetherServers.kt
git commit -m "Fix fatal playback service crash and update Railway Together server"
git push origin main
```

Build remains on GitHub Actions as usual.
