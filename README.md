# TiviSync Flask Server

Serves the newest TiViMate backup to Android TV devices on demand.

## Setup

1. Edit `docker-compose.yml` and update the volume path to your USBShare folder
2. Run: `docker compose up -d`

## Endpoints

- `GET /sync?version=5.2.0&last=TiviMate_backup_20260206_175552_v5208.tmb`
  - Serves newest .tmb if newer than `last`, otherwise redirects to launch TiViMate
- `GET /latest?version=5.2.0` — Returns newest backup filename as JSON
- `GET /status` — Shows which device IPs have synced and when

## TV Quick Actions Setup

Add an HTTP Request action pointing to:
```
http://192.168.254.205:5005/sync?version=5.2.0&last=LAST_RESTORED_FILENAME
```

The `last` param is optional but lets the server know if you're already up to date.

## Flow

- Tap shortcut on TV
- Flask checks if newer .tmb exists for your TiViMate version
- If newer → downloads file → TiViMate restore dialog appears
- If up to date → TiViMate launches directly, no restore prompt
