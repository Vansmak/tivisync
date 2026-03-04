# TiviSync

Automatically prompts TiViMate to restore from the newest backup on your network share when a new backup is detected.

## How It Works

1. On launch (triggered by TV Quick Actions Pro on device wake), TiviSync:
   - Gets the installed TiViMate version
   - Connects to your SMB share at 192.168.254.205/USBShare
   - Finds the newest .tmb file matching your TiViMate version
   - Compares it to the last file it prompted you to restore
   - If it's new, copies it locally and fires the TiViMate restore intent
   - TiViMate shows its normal restore confirmation dialog
   - If no new backup, exits silently

## Build Instructions

1. Open this folder in Android Studio
2. Let Gradle sync
3. Build > Generate Signed APK (or just Build > Build APK for testing)
4. Sideload the APK on each Android TV device

## TV Quick Actions Pro Setup

1. Go to Trigger Actions & Macros
2. Open "Turning on the device"
3. Add Action > ADB, HTTP, Intent, Tap > App
4. Select TiviSync
5. Done

## Dependencies

- jcifs-ng 2.1.10 (SMB2/3 support for modern Windows shares)
- androidx.core

## Notes

- Credentials are hardcoded for personal use (your SMB share)
- Last restored filename is stored in SharedPreferences per device
- Version matching uses TiViMate's versionName to filter backup files
- The restore confirmation dialog is TiViMate's own UI — TiviSync shows nothing itself
