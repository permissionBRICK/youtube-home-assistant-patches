# YouTube Home Assistant button

A custom ReVanced patch that adds a TV button in the bottom player controls. Tap it to pause YouTube on your phone and send the current video and playback position to a Home Assistant webhook. Your existing automation handles the receiver, TV inputs and SmartTube.

For **ReVanced Manager 2.6.0** and **YouTube 20.40.45**. The repository distributes a patch, not a YouTube APK.

## Install

1. In Manager, open **Patches**, tap the edit button, then **+**, and choose **Enter URL**.
2. Paste this patch feed URL:

   ```text
   https://github.com/permissionBRICK/youtube-home-assistant-patches/releases/latest/download/patches.json
   ```

3. Patch the original YouTube **20.40.45 APK** with your usual patches and **Add Home Assistant TV button** from this source. Install the result through Manager, using the same Manager signing key as your existing installation.
4. Open a video and tap the TV button in the player controls. Paste the **complete webhook URL from your existing browser userscript**, then save. There is no separate Home Assistant API token or entity setting.
5. Tap the TV button again to send. **Long press** it to open Home Assistant settings later.

The URL above is a JSON feed in [Manager's documented format](https://github.com/ReVanced/revanced-manager/blob/main/docs/2_3_managing_patches.md), not the repository URL. A local `.rvp` download is also available in Releases.

## What it sends

```json
{
  "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=95s",
  "video_id": "dQw4w9WgXcQ",
  "position": 95,
  "title": "",
  "source_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

The payload matches the browser userscript's fields. `title` is empty because this patch does not read the video title. Positions below five seconds start at the beginning. Playlist parameters are omitted so SmartTube opens the selected video instead of a playlist.

Configure the existing Home Assistant webhook trigger to accept POST JSON. An HTTPS or local HTTP URL can include a reverse-proxy prefix, followed by `/api/webhook/<id>`. The phone must be able to reach that URL. No automation changes are needed if the browser script already works.

The phone pauses before sending and stays paused if the request fails. A success message means Home Assistant accepted the HTTP request; it does not confirm playback on the TV. A timeout may occur after the automation started, so the patch does not retry automatically. Redirects are rejected; use the final webhook URL.

The webhook URL is stored encrypted with Android Keystore in the app's private, excluded-from-backup storage. It is not written to logs or built into the patch. Use HTTPS when connecting over an untrusted network. The patch permits HTTP in the app's network configuration for local Home Assistant instances; certificate verification remains enabled for HTTPS.

The button is for the regular video player, including fullscreen. Shorts and background playback do not have this button. The patch checks the APK's player methods and layout and fails if they do not match.

## Build

Requires JDK 21, Python 3, and Android SDK platform `android-36`. Set `ANDROID_HOME` to your SDK directory.

```sh
python3 scripts/build.py
```

Output: `build/libs/youtube-home-assistant.rvp`. The build downloads the official ReVanced CLI 6.0.0 and Google R8 9.0.32, verifies their pinned SHA-256 checksums, and uses their public build interfaces. No GitHub Packages token is required.

Run the network and playback-state checks locally:

```sh
scripts/test.sh
```

Tagged releases build the `.rvp` and publish the Manager JSON feed through GitHub Actions. The workflow only builds and publishes artifacts.

## Credits and license

GPL-3.0-only. Player method fingerprints are adapted from [ReVanced Patches v6.1.0](https://gitlab.com/ReVanced/revanced-patches/-/tree/v6.1.0). The Gradle wrapper comes from the [ReVanced patches template](https://github.com/ReVanced/revanced-patches-template). See [NOTICE](NOTICE) and [LICENSE](LICENSE).
