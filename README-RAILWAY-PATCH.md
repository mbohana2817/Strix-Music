# Strix Music — Railway Live Server patch

This patch connects the existing Listen Together client to the deployed Strix Live Server.

Changed:
- Listen Together default WebSocket:
  `wss://strix-live-server-production.up.railway.app/ws`
- Updater checks the Strix Live Server manifest first:
  `https://strix-live-server-production.up.railway.app/api/update/latest`
- Existing GitHub/backup updater remains as fallback.

The server manifest must expose a newer `version` and a public HTTPS APK `url` before an update can be offered.

Install by extracting this ZIP into the Strix-Music project root.
