"""
PipeWire & WirePlumber Engine.
Handles:
- PipeWire real-time node monitoring
- Detecting active input streams (e.g. Google Meet / WebRTC / Browser)
- Bluetooth profile switching between A2DP and HFP/HSP
- Clean restart of PipeWire & WirePlumber systemd user units
"""

import subprocess
import re
import logging
import threading
import time

logger = logging.getLogger("ubuntu-mic-fixer.pipewire")


class PipeWireEngine:
    def __init__(self, on_input_stream_changed=None):
        self.on_input_stream_changed = on_input_stream_changed
        self._monitoring = False
        self._monitor_thread = None
        self._current_input_stream_active = False
        self._auto_bt_switch_enabled = True
        self._cached_stream_active = False

    @property
    def is_input_stream_active(self) -> bool:
        return self._cached_stream_active

    def restart_pipewire_stack(self) -> dict:
        """
        Restart PipeWire and WirePlumber services cleanly via Systemd user units.
        `systemctl --user restart pipewire pipewire-pulse wireplumber`
        """
        cmd = ["systemctl", "--user", "restart", "pipewire", "pipewire-pulse", "wireplumber"]
        try:
            res = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                return {"success": True, "message": "PipeWire & WirePlumber stack restarted successfully."}
            else:
                return {"success": False, "message": f"Restart failed: {res.stderr.strip()}"}
        except Exception as e:
            logger.error("Error restarting PipeWire stack: %s", e)
            return {"success": False, "message": f"Error: {e}"}

    def get_pipewire_nodes(self) -> list:
        """
        Get list of PipeWire nodes via `wpctl status` or `pw-cli list-objects Node`.
        """
        nodes = []
        try:
            res = subprocess.run(
                ["wpctl", "status"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                lines = res.stdout.splitlines()
                current_section = ""
                for line in lines:
                    if "Sinks:" in line:
                        current_section = "sink"
                    elif "Sources:" in line:
                        current_section = "source"
                    elif "Streams:" in line:
                        current_section = "stream"
                    elif line.strip().startswith("├") or line.strip().startswith("└") or line.strip().startswith("│"):
                        clean_line = re.sub(r'^[│├└─\s]+', '', line).strip()
                        if clean_line:
                            nodes.append({"section": current_section, "info": clean_line})
        except Exception as e:
            logger.error("Failed to run wpctl status: %s", e)
        return nodes

    def check_active_input_streams(self) -> bool:
        """
        Check if any application / browser is actively capturing audio input.
        """
        try:
            res = subprocess.run(
                ["pw-cli", "list-objects", "Node"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                output = res.stdout
                if "Stream/Input/Audio" in output or "media.role = \"Communication\"" in output:
                    return True

            status_res = subprocess.run(
                ["wpctl", "status"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if status_res.returncode == 0:
                in_streams = False
                for line in status_res.stdout.splitlines():
                    if "Streams:" in line:
                        in_streams = True
                    if in_streams and ("input" in line.lower() or "capture" in line.lower() or "google" in line.lower() or "meet" in line.lower()):
                        return True
        except Exception as e:
            logger.error("Error checking active input streams: %s", e)
        return False

    def get_bluetooth_devices(self) -> list:
        """
        Find connected Bluetooth audio devices and their current profiles via wpctl.
        """
        bt_devices = []
        try:
            res = subprocess.run(
                ["wpctl", "status"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False
            )
            if res.returncode == 0:
                for line in res.stdout.splitlines():
                    if "bluez" in line.lower() or "bluetooth" in line.lower():
                        match = re.search(r'(\d+)\.\s+(.*)', line)
                        if match:
                            node_id = match.group(1)
                            name = match.group(2).strip()
                            bt_devices.append({"id": node_id, "name": name})
        except Exception as e:
            logger.error("Error finding Bluetooth devices: %s", e)
        return bt_devices

    def set_bluetooth_profile(self, profile: str) -> dict:
        """
        Set Bluetooth profile for connected headsets.
        profile: 'a2dp' or 'hfp'/'hsp'
        """
        bt_devs = self.get_bluetooth_devices()
        if not bt_devs:
            return {"success": False, "message": "No Bluetooth audio devices detected."}

        results = []
        for dev in bt_devs:
            node_id = dev["id"]
            target_profile = profile
            if profile.lower() in ["hfp", "hsp", "call"]:
                target_profile = "headset-head-unit"
            elif profile.lower() == "a2dp":
                target_profile = "a2dp-sink"

            cmd = ["wpctl", "set-profile", str(node_id), target_profile]
            try:
                res = subprocess.run(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=False
                )
                if res.returncode == 0:
                    results.append(f"Device {dev['name']} ({node_id}) set to {target_profile}")
                else:
                    alt_cmd = ["wpctl", "set-profile", str(node_id), "1" if "headset" in target_profile else "0"]
                    alt_res = subprocess.run(alt_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    if alt_res.returncode == 0:
                        results.append(f"Device {dev['name']} set profile index")
                    else:
                        results.append(f"Failed setting profile for {dev['name']}: {res.stderr.strip()}")
            except Exception as e:
                results.append(f"Error on device {dev['name']}: {e}")

        return {"success": True, "message": "\n".join(results)}

    def start_monitoring(self):
        """Start background loop monitoring PipeWire audio streams."""
        if self._monitoring:
            return
        self._monitoring = True
        self._monitor_thread = threading.Thread(target=self._monitor_loop, daemon=True)
        self._monitor_thread.start()

    def stop_monitoring(self):
        self._monitoring = False

    def set_auto_bt_switch(self, enabled: bool):
        self._auto_bt_switch_enabled = enabled

    def _monitor_loop(self):
        while self._monitoring:
            try:
                has_active_input = self.check_active_input_streams()
                self._cached_stream_active = has_active_input

                if has_active_input != self._current_input_stream_active:
                    self._current_input_stream_active = has_active_input
                    logger.info("Input stream active state changed to: %s", has_active_input)

                    if self._auto_bt_switch_enabled:
                        if has_active_input:
                            logger.info("Call/Mic active: Auto-switching Bluetooth to HFP/HSP mode")
                            self.set_bluetooth_profile("hfp")
                        else:
                            logger.info("Call/Mic inactive: Auto-switching Bluetooth to A2DP mode")
                            self.set_bluetooth_profile("a2dp")

                    if self.on_input_stream_changed:
                        self.on_input_stream_changed(has_active_input)
            except Exception as e:
                logger.error("Error in PipeWire monitor loop: %s", e)

            time.sleep(2)
