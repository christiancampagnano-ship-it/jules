"""
ALSA Unmuter module.
Unmutes hardware capture channels and sets capture volume using amixer.
"""

import subprocess
import logging

logger = logging.getLogger("ubuntu-mic-fixer.alsa")


def unmute_capture() -> dict:
    """
    Unmute capture channels and set volume to 100% via amixer.
    Returns a status dict indicating success and message.
    """
    results = []
    success = True

    commands = [
        ["amixer", "set", "Capture", "cap"],
        ["amixer", "set", "Capture", "100%"],
        ["amixer", "set", "Master", "unmute"],
    ]

    for cmd in commands:
        try:
            res = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                results.append(f"Success: {' '.join(cmd)}")
            else:
                logger.warning("amixer command failed: %s: %s", ' '.join(cmd), res.stderr.strip())
                results.append(f"Warning: {' '.join(cmd)}: {res.stderr.strip()}")
        except Exception as e:
            logger.error("Failed to execute %s: %s", ' '.join(cmd), e)
            results.append(f"Error: {e}")
            success = False

    return {
        "success": success,
        "details": "\n".join(results) if results else "ALSA unmuted."
    }


def is_capture_muted() -> bool:
    """
    Check if capture is muted via amixer sget Capture.
    """
    try:
        res = subprocess.run(
            ["amixer", "sget", "Capture"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False
        )
        if "[off]" in res.stdout:
            return True
        return False
    except Exception as e:
        logger.error("Failed to check capture state: %s", e)
        return False
