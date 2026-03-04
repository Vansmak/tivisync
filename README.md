# TiviSync

**Automatic TiViMate backup sync for Android TV devices on your local network.**

TiviSync is a personal tool that lets you keep TiViMate in sync across multiple Android TV devices with a single tap. It consists of a lightweight Flask server that runs on your home server and a small Android app you sideload onto each TV device.

---

## How It Works

1. You make a backup in TiViMate on your main device — it saves a `.tmb` file to your network share
2. On any other device, tap the TiviSync app
3. TiviSync checks your server for a newer backup
4. If one exists, it downloads it and opens TiViMate's restore dialog automatically
5. If you're already up to date, nothing happens

---

## Requirements

- A home server or NAS running Docker (Linux recommended)
- A network share (SMB/NFS) where TiViMate saves its backups
- Android TV devices with sideloading enabled
- TiViMate installed on each device

---

## Part 1: Flask Server Setup

The server reads your backup folder and serves the newest `.tmb` file to your devices.

### Option A: Docker Hub (easiest)

```bash
docker run -d \
  --name tivisync \
  --restart unless-stopped \
  -p 5005:5005 \
  -v /path/to/your/backups:/backups:ro \
  vansmak/tivisync
```

Replace `/path/to/your/backups` with the local path where TiViMate saves backups on your server.

### Option B: Docker Compose

Create a `docker-compose.yml`:

```yaml
version: "3.8"
services:
  tivisync:
    image: vansmak/tivisync
    container_name: tivisync
    restart: unless-stopped
    ports:
      - "5005:5005"
    volumes:
      - /path/to/your/backups:/backups:ro
```

Then run:
```bash
docker compose up -d
```

### Verify it's running

Open a browser and go to:
```
http://YOUR_SERVER_IP:5005/latest?version=5.2.0
```

You should see a JSON response with the newest backup filename.

---

## Part 2: Android TV Setup

### Step 1: Enable Unknown Sources / Sideloading

On your Android TV device:
1. Go to **Settings → Device Preferences → Security & Restrictions**
2. Enable **Unknown Sources** (or enable it specifically for the app you'll use to install)
3. On some devices this is under **Settings → Privacy → Security**

### Step 2: Enable ADB Debugging (optional but recommended for troubleshooting)

1. Go to **Settings → Device Preferences → About**
2. Scroll to **Build** and press OK 7 times to enable Developer Options
3. Go to **Settings → Device Preferences → Developer Options**
4. Enable **USB Debugging** and **Network Debugging** (ADB over network)

### Step 3: Download the APK

Download the latest `app-debug.apk` from the [GitHub Actions Artifacts](https://github.com/vansmak/tivisync/actions):

1. Go to the **Actions** tab in this repo
2. Click the most recent successful workflow run
3. Scroll to **Artifacts** at the bottom
4. Download **TiviSync-debug** and unzip it

### Step 4: Sideload the APK

**Using Downloader app (recommended):**
1. Install [Downloader by AFTVnews](https://www.aftvnews.com/downloader/) from the Play Store on your TV
2. Open Downloader and enter the direct download URL for the APK
3. Follow the prompts to install

**Using CX File Explorer:**
1. Install CX File Explorer from the Play Store
2. Navigate to the APK file on your network share or USB drive
3. Tap the APK to install

**Using ADB from your computer:**
```bash
adb connect YOUR_TV_IP:5555
adb install app-debug.apk
```

### Step 5: First Run Setup

1. Launch **TiviSync** from your app list
2. A setup screen will appear asking for your server URL
3. Enter: `http://YOUR_SERVER_IP:5005`
4. Tap **Save**
5. TiviSync will immediately check for a backup and show TiViMate's restore dialog if one is found

After the first run, setup is complete. Every subsequent launch just does the sync silently.

---

## Usage

- Launch TiviSync from your app list or add it to a quick launch shortcut
- If a newer backup exists on the server → TiViMate restore dialog appears
- If already up to date → nothing happens (app closes silently)
- To make a new backup: use TiViMate's built-in backup function on your main device, saving to your network share

---

## Building From Source

If you prefer to build the APK yourself:

1. Fork this repo
2. Go to **Actions → Build TiviSync APK → Run workflow**
3. Download the APK from the Artifacts section of the completed run

No Android Studio or local build tools required.

---

## Troubleshooting

**App flashes and disappears on launch:**
- Make sure ADB debugging is enabled and check logcat for errors
- Verify your server URL was saved correctly (uninstall and reinstall to re-run setup)

**TiViMate restore dialog doesn't appear:**
- Check that your Flask server is running: `docker logs tivisync`
- Verify the backup folder path is correct and contains `.tmb` files
- Make sure the TiViMate version prefix in the filename matches (e.g. `v5208` for version 5.2.x)

**"Cleartext HTTP not permitted" error:**
- Make sure you're using the latest APK build — earlier builds had this issue

---

## Security & Disclaimer

> ⚠️ **Use at your own risk.**

This tool is designed for **personal use on your own local network only.**

- The APK uses plain HTTP (not HTTPS) to communicate with your local server
- No authentication is implemented — anyone on your network can access the sync endpoint
- The APK is a debug build and is **not signed for Play Store distribution**
- This app will never appear on the Google Play Store
- You can review the complete source code in this repository

This is a personal hobby project. It is not affiliated with TiViMate or any streaming service. The author provides no warranty and accepts no responsibility for data loss or any other issues arising from use of this software.

---

## License

MIT — do whatever you want with it, just don't blame me if something breaks.
