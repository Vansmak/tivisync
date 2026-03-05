# TiviSync

**Semi-Automatic TiViMate backup sync for Android TV devices on your local network.**
[![Buy Me A Coffee](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/vansmak)


TiviSync is a personal tool that lets you keep TiViMate in sync across multiple Android TV devices with a single tap. It consists of a lightweight Flask server that runs on your home server and a small Android app you sideload onto each TV device.

---

## How It Works

1. Each device has its own subfolder on your network share (e.g. `usbshare/office`, `usbshare/familyroom`)
2. TiViMate on each device saves its backups into that device's subfolder
3. On any device, tap the TiviSync app
4. TiviSync asks the server for the newest `.tmb` file across **all other device subfolders**
5. If a newer backup exists, it downloads it and opens TiViMate's restore dialog automatically
6. If you're already up to date, nothing happens

---

## Requirements

- A home server or NAS running Docker (Linux recommended)
- A network share (SMB/NFS) where TiViMate saves its backups
- Android TV devices with sideloading enabled
- TiViMate installed on each device

---

## Folder Structure

TiviSync uses a **per-device subfolder** layout so each device has its own backup location and the server can find the newest backup from any *other* device.

```
/mnt/usbshare/          ← parent share folder (mounted into Docker)
├── office/             ← backups from the office TV
│   └── v5208_2024-01-15_21-30-00.tmb
├── familyroom/         ← backups from the family room TV
│   └── v5208_2024-01-14_18-00-00.tmb
└── bedroom/            ← backups from the bedroom TV
    └── v5208_2024-01-13_09-15-00.tmb
```

- Each device's TiViMate backup destination must be its own subfolder on the share
- The subfolder name is the **device name** used in the TiviSync URL (e.g. `office`, `familyroom`)
- When a device syncs, the server scans **all other subfolders** and serves the newest `.tmb` file found — so every device always gets the most recent backup from the rest of the fleet

---

## Part 1: Flask Server Setup

The server mounts the **parent share folder** (not a device subfolder) and discovers backups across all subfolders automatically.

### Option A: Docker Hub (easiest)

```bash
docker run -d \
  --name tivisync \
  --restart unless-stopped \
  -p 5005:5005 \
  -v /mnt/usbshare:/backups:ro \
  vansmak/tivisync
```

Replace `/mnt/usbshare` with the local path of your parent share folder on the server (the folder that *contains* the per-device subfolders).

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
      - /mnt/usbshare:/backups:ro
```

Then run:
```bash
docker compose up -d
```

### Verify it's running

Open a browser and go to:
```
http://YOUR_SERVER_IP:5005/office/latest?version=5.2.0
```

You should see a JSON response with the newest backup filename found across all subfolders *other than* `office`.

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

### Step 4b: Configure TiViMate Backup Destination

Before running TiviSync, make sure TiViMate on this device is set to back up to its own subfolder on your network share:

1. In TiViMate go to **Settings → General → Backup**
2. Set the backup path to the device's subfolder — e.g. `\\YOUR_NAS\usbshare\office` for the office TV
3. Take at least one manual backup so the subfolder and a `.tmb` file exist

### Step 5: First Run Setup

1. Launch **TiviSync** from your app list
2. A setup screen will appear asking for your server URL
3. Enter the full URL **including your device's subfolder name**:
   ```
   http://YOUR_SERVER_IP:5005/office
   ```
   Use the name that matches the subfolder you configured in TiViMate (e.g. `office`, `familyroom`, `bedroom`)
4. Tap **Save**
5. TiviSync will immediately check for a newer backup from any other device and show TiViMate's restore dialog if one is found

> **Important:** The device name in the URL must exactly match the subfolder name on the share. The server uses this name to exclude the current device's own backups when searching for the newest file.

After the first run, setup is complete. Every subsequent launch just does the sync silently.

---

## Usage

- Launch TiviSync from your app list or add it to a quick launch shortcut
- If a newer backup exists on the server → TiViMate restore dialog appears
- If already up to date → nothing happens (app closes silently)
- To make a new backup: use TiViMate's built-in backup function on your main device, saving to your network share

---

## Building From Source

If you prefer to build the APKs yourself:

1. Fork this repo
2. Go to **Actions → Build TiviSync & ProjSync APKs → Run workflow**
3. Download the APKs from the Artifacts section of the completed run (**TiviSync-debug** and **ProjSync-debug**)

No Android Studio or local build tools required.

---

## ProjSync — Projectivy Launcher Sync

**ProjSync** is a companion app that syncs [Projectivy Launcher](https://www.projectivy.co.uk/) `.plbackup` files across your Android TV devices — using the **same Flask server** and the same per-device subfolder layout as TiviSync.

### How ProjSync Works

1. Each device has its own subfolder on your network share (same subfolders used by TiviSync)
2. Projectivy Launcher saves its backups (`.plbackup` files) into that device's subfolder
3. Tap the ProjSync app on any device
4. ProjSync asks the server for the newest `.plbackup` across all subfolders
5. If a newer backup exists, it downloads it and opens an **"Open with" dialog** — select **"Restore backup"** in Projectivy
6. If you're already up to date, the app closes silently

### Folder Structure

ProjSync uses the same per-device subfolder layout. You can mix `.tmb` and `.plbackup` files in the same subfolders:

```
/mnt/usbshare/
├── office/
│   ├── TiviMate_backup_20240115_213000_v520.tmb
│   └── projectivy_backup_20240115.plbackup
├── familyroom/
│   ├── TiviMate_backup_20240114_180000_v520.tmb
│   └── projectivy_backup_20240114.plbackup
└── bedroom/
    └── projectivy_backup_20240113.plbackup
```

### ProjSync Setup

**Step 1: Configure Projectivy backup destination**

In Projectivy Launcher, set the backup path to the device's subfolder on your network share. Take at least one manual backup so the subfolder and a `.plbackup` file exist.

**Step 2: Download and sideload the APK**

From the [GitHub Actions Artifacts](https://github.com/vansmak/tivisync/actions), download **ProjSync-debug** from the same workflow run as TiviSync-debug. Sideload it onto each Android TV device.

**Step 3: First run setup**

Launch **ProjSync** from your app list. The setup screen will ask for the server URL — use the same format as TiviSync (just the device name, no extra path suffix):

```
http://YOUR_SERVER_IP:5005/office
```

Use the same device subfolder name you use for TiviSync (e.g. `office`, `familyroom`, `bedroom`). Tap **Save**.

ProjSync automatically appends `&type=plbackup` to the request so the server knows to search for `.plbackup` files instead of `.tmb` files.

**Step 4: Restoring a backup**

When ProjSync finds a newer `.plbackup`, it will download it and show an **"Open with" dialog**. Select **Restore backup** in Projectivy Launcher to apply it.

> **Note:** ProjSync does not launch Projectivy directly. The "Open with" dialog is intentional — it lets Projectivy handle the restore. If you're already up to date, the app closes silently with no dialog.

### Server endpoint

Both TiviSync and ProjSync use the same `/<device>` route on the Flask server. The `type=` query parameter (`tmb` or `plbackup`) tells the server which file type to search for. No additional server configuration is needed.

---

## Troubleshooting

**App flashes and disappears on launch:**
- Make sure ADB debugging is enabled and check logcat for errors
- Verify your server URL was saved correctly (uninstall and reinstall to re-run setup)

**TiViMate restore dialog doesn't appear:**
- Check that your Flask server is running: `docker logs tivisync`
- Verify the parent share folder is mounted correctly and each device subfolder contains `.tmb` files
- Confirm the device name in your TiviSync URL matches the subfolder name exactly (case-sensitive)
- Make sure at least one *other* device has a backup — the server searches all subfolders except the requesting device's own
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
