"""
Snap Permission Manager module.
Checks and connects audio-record interface for browser snaps (Firefox, Chromium, etc).
"""

import subprocess
import logging
from typing import List, Dict

logger = logging.getLogger("ubuntu-mic-fixer.snap")

TARGET_SNAPS = ["firefox", "chromium"]


def is_snap_installed(snap_name: str) -> bool:
    """Check if a given snap package is installed on the system."""
    try:
        res = subprocess.run(
            ["snap", "list", snap_name],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False
        )
        return res.returncode == 0
    except Exception as e:
        logger.error("Failed to list snap %s: %s", snap_name, e)
        return False


def is_audio_record_connected(snap_name: str) -> bool:
    """
    Check if audio-record plug for snap_name is connected.
    """
    try:
        res = subprocess.run(
            ["snap", "connections", snap_name],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False
        )
        if res.returncode != 0:
            return False
        for line in res.stdout.splitlines():
            if "audio-record" in line:
                # line format: Interface Plug Slot Notes
                # connected plug shows slot like :audio-record
                parts = line.split()
                if len(parts) >= 3 and parts[2] != "-":
                    return True
        return False
    except Exception as e:
        logger.error("Error checking snap connections for %s: %s", snap_name, e)
        return False


def fix_snap_permissions() -> Dict[str, str]:
    """
    Connect audio-record interface for installed target snaps.
    Returns summary result dictionary.
    """
    results = []
    for snap_name in TARGET_SNAPS:
        if not is_snap_installed(snap_name):
            results.append(f"{snap_name.capitalize()}: Not installed as Snap")
            continue

        cmd = ["snap", "connect", f"{snap_name}:audio-record"]
        try:
            res = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                results.append(f"{snap_name.capitalize()}: Audio permission connected successfully")
            else:
                err = res.stderr.strip() or "Unknown error"
                results.append(f"{snap_name.capitalize()}: Failed ({err})")
        except Exception as e:
            results.append(f"{snap_name.capitalize()}: Exception ({e})")

    return {
        "status": "completed",
        "details": "\n".join(results)
    }


def get_snap_status_summary() -> List[Dict[str, str]]:
    """
    Get current permission status for browser snaps.
    """
    status_list = []
    for snap_name in TARGET_SNAPS:
        installed = is_snap_installed(snap_name)
        connected = is_audio_record_connected(snap_name) if installed else False
        status_list.append({
            "name": snap_name,
            "installed": installed,
            "connected": connected
        })
    return status_list
