from flask import Flask, request, redirect, send_file, jsonify
import os
import re
import logging
from datetime import datetime
from pathlib import Path

app = Flask(__name__)

# Config
BACKUP_DIR = "/backups"  # Mount your USBShare folder here; each device has its own subfolder
TIVIMATE_PACKAGE = "ar.tvplayer.tv"

# In-memory log of device syncs
sync_log = {}

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("TiviSync")


def get_version_prefix(version_string):
    """Convert '5.2.0' to '520' for filename matching."""
    if not version_string:
        return ""
    digits = version_string.replace(".", "")
    return digits[:3]


def find_newest_tmb(version_prefix):
    """
    Find the newest .tmb file matching the given version prefix across ALL
    device subfolders under BACKUP_DIR.
    """
    backup_path = Path(BACKUP_DIR)
    if not backup_path.exists():
        logger.error(f"Backup directory {BACKUP_DIR} not found")
        return None

    pattern = re.compile(
        r"TiviMate_backup_\d{8}_\d{6}_v" + re.escape(version_prefix) + r".*\.tmb",
        re.IGNORECASE,
    )
    broad_pattern = re.compile(r".*v" + re.escape(version_prefix) + r".*\.tmb", re.IGNORECASE)

    matches = []
    for subfolder in backup_path.iterdir():
        if not subfolder.is_dir():
            continue
        for f in subfolder.iterdir():
            if not f.is_file():
                continue
            if pattern.match(f.name) or broad_pattern.match(f.name):
                matches.append(f)

    if not matches:
        return None

    matches.sort(key=lambda f: f.name, reverse=True)
    return matches[0]


@app.route("/<device>")
def sync(device):
    """
    Main sync endpoint called by TV Quick Actions.

    URL path:
      - device: identifies the requesting device (e.g. 'office', 'familyroom')

    Query params:
      - version: TiViMate version string e.g. 5.2.0
      - last: filename of last backup this device restored (optional)

    Returns:
      - The .tmb file if a newer one exists in another device's subfolder
      - Redirect to launch TiViMate if already up to date
    """
    device_ip = request.remote_addr
    version = request.args.get("version", "")
    last_restored = request.args.get("last", "")

    logger.info(f"Sync request from {device_ip} (device={device}) | version={version} | last={last_restored}")

    if not version:
        logger.warning(f"No version provided from {device_ip} (device={device})")
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    version_prefix = get_version_prefix(version)
    newest = find_newest_tmb(version_prefix)

    if newest is None:
        logger.warning(f"No matching backup found for version prefix {version_prefix}")
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    # The newest file lives in this device's own folder — it made that backup
    if newest.parent.name.lower() == device.lower():
        logger.info(f"{device_ip} ({device}) owns the newest backup ({newest.name}), launching TiViMate")
        sync_log[device] = {
            "ip": device_ip,
            "status": "up_to_date",
            "file": newest.name,
            "time": datetime.now().isoformat(),
        }
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    # Newest is from another device — check if this device already restored it
    if last_restored and last_restored == newest.name:
        logger.info(f"{device_ip} ({device}) already up to date ({newest.name}), launching TiViMate")
        sync_log[device] = {
            "ip": device_ip,
            "status": "up_to_date",
            "file": newest.name,
            "time": datetime.now().isoformat(),
        }
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    logger.info(f"Serving {newest.name} to {device_ip} ({device})")
    sync_log[device] = {
        "ip": device_ip,
        "status": "synced",
        "file": newest.name,
        "time": datetime.now().isoformat(),
    }

    return send_file(
        newest,
        as_attachment=True,
        download_name=newest.name,
        mimetype="application/octet-stream",
    )


@app.route("/status")
def status():
    """Simple status endpoint showing which devices have synced."""
    return jsonify({
        "backup_dir": BACKUP_DIR,
        "devices": sync_log,
    })


@app.route("/latest")
def latest():
    """Returns the filename of the newest backup for a given version across all subfolders."""
    version = request.args.get("version", "")
    version_prefix = get_version_prefix(version)
    newest = find_newest_tmb(version_prefix)
    if newest:
        return jsonify({"newest": newest.name})
    return jsonify({"newest": None}), 404


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5005, debug=False)
