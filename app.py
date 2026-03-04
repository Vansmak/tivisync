from flask import Flask, request, redirect, send_file, jsonify
import os
import re
import logging
from datetime import datetime
from pathlib import Path

app = Flask(__name__)

# Config
BACKUP_DIR = "/backups"  # Mount your USBShare folder here
TIVIMATE_PACKAGE = "ar.tvplayer.tv"

# In-memory log of device syncs
sync_log = {}

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("TiviSync")


def get_tivimate_version_from_query():
    """Get TiViMate version passed as query param from the device."""
    return request.args.get("version", "")


def find_newest_tmb(version_prefix):
    """Find the newest .tmb file matching the given version prefix."""
    backup_path = Path(BACKUP_DIR)
    if not backup_path.exists():
        logger.error(f"Backup directory {BACKUP_DIR} not found")
        return None

    # Match files like TiViMate_backup_20260227_144700_v5208.tmb
    pattern = re.compile(r"TiviMate_backup_\d{8}_\d{6}_v" + re.escape(version_prefix) + r".*\.tmb", re.IGNORECASE)

    matches = [f for f in backup_path.iterdir() if f.is_file() and pattern.match(f.name)]

    if not matches:
        # Try broader match just on version prefix
        broad_pattern = re.compile(r".*v" + re.escape(version_prefix) + r".*\.tmb", re.IGNORECASE)
        matches = [f for f in backup_path.iterdir() if f.is_file() and broad_pattern.match(f.name)]

    if not matches:
        return None

    # Sort by filename descending (timestamp in name = newest last alphabetically)
    matches.sort(key=lambda f: f.name, reverse=True)
    return matches[0]


def get_version_prefix(version_string):
    """Convert '5.2.0' to '520' for filename matching."""
    if not version_string:
        return ""
    # Remove dots and take first 3 digits
    digits = version_string.replace(".", "")
    return digits[:3]


@app.route("/sync")
def sync():
    """
    Main sync endpoint called by TV Quick Actions.
    
    Query params:
      - version: TiViMate version string e.g. 5.2.0
      - last: filename of last backup this device restored (optional)
    
    Returns:
      - The .tmb file if a newer one exists
      - Redirect to launch TiViMate if already up to date
    """
    device_ip = request.remote_addr
    version = request.args.get("version", "")
    last_restored = request.args.get("last", "")

    logger.info(f"Sync request from {device_ip} | version={version} | last={last_restored}")

    if not version:
        logger.warning(f"No version provided from {device_ip}")
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    version_prefix = get_version_prefix(version)
    newest = find_newest_tmb(version_prefix)

    if newest is None:
        logger.warning(f"No matching backup found for version prefix {version_prefix}")
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    # Check if device already has this backup
    if last_restored and last_restored == newest.name:
        logger.info(f"{device_ip} already up to date ({newest.name}), launching TiViMate")
        sync_log[device_ip] = {
            "status": "up_to_date",
            "file": newest.name,
            "time": datetime.now().isoformat()
        }
        return redirect(f"intent:#Intent;package={TIVIMATE_PACKAGE};end", 302)

    # Serve the newest backup file
    logger.info(f"Serving {newest.name} to {device_ip}")
    sync_log[device_ip] = {
        "status": "synced",
        "file": newest.name,
        "time": datetime.now().isoformat()
    }

    return send_file(
        newest,
        as_attachment=True,
        download_name=newest.name,
        mimetype="application/octet-stream"
    )


@app.route("/status")
def status():
    """Simple status endpoint showing which devices have synced."""
    return jsonify({
        "backup_dir": BACKUP_DIR,
        "devices": sync_log
    })


@app.route("/latest")
def latest():
    """Returns just the filename of the newest backup for a given version."""
    version = request.args.get("version", "")
    version_prefix = get_version_prefix(version)
    newest = find_newest_tmb(version_prefix)
    if newest:
        return jsonify({"newest": newest.name})
    return jsonify({"newest": None}), 404


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5005, debug=False)
