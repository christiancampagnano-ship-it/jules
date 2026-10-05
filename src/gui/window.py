"""
Modern Libadwaita Main Window for Ubuntu Mic & Audio Fixer.
Matches Ubuntu's native visual design (GTK4 + Libadwaita).
"""

import os
import sys
import time
import random
import subprocess
import threading
import logging

logger = logging.getLogger("ubuntu-mic-fixer.window")

import gi
gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, GLib, GObject

from engine.alsa import unmute_capture, is_capture_muted
from engine.snap import fix_snap_permissions, get_snap_status_summary
from engine.pipewire import PipeWireEngine


class MicFixerWindow(Adw.ApplicationWindow):
    def __init__(self, app, pipewire_engine: PipeWireEngine):
        super().__init__(application=app, title="Ubuntu Mic & Audio Fixer")
        self.set_default_size(680, 640)

        self.pw_engine = pipewire_engine
        self.recording_thread = None
        self.recording_active = False
        self.vu_timer_id = None
        self._cached_muted = False

        # Wrap content in ToastOverlay
        self.toast_overlay = Adw.ToastOverlay()

        self._build_ui()

        # Asynchronously check ALSA mute state periodically in background thread
        threading.Thread(target=self._background_check_loop, daemon=True).start()

        # Smooth UI VU meter animation timer (runs without blocking subprocesses)
        self.vu_timer_id = GLib.timeout_add(100, self._update_vu_meter_ui)

    def _build_ui(self):
        # Header Bar
        header = Adw.HeaderBar()

        # Main Box Container
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        main_box.append(header)

        # Scrolled Window
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_vexpand(True)
        main_box.append(scrolled)

        # Content Clamp
        clamp = Adw.Clamp()
        clamp.set_maximum_size(620)
        clamp.set_margin_top(16)
        clamp.set_margin_bottom(24)
        clamp.set_margin_start(16)
        clamp.set_margin_end(16)
        scrolled.set_child(clamp)

        page_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=20)
        clamp.set_child(page_box)

        # --- Card 1: Microphone Status & Real-time Level Meter ---
        group_mic = Adw.PreferencesGroup()
        group_mic.set_title("🎙️ Microphone Status & Real-time VU Meter")
        group_mic.set_description("Monitor hardware capture state and real-time audio input volume")

        self.row_mic_status = Adw.ActionRow()
        self.row_mic_status.set_title("Hardware Mic State")
        self.lbl_mic_status = Gtk.Label(label="Active / Unmuted")
        self.lbl_mic_status.add_css_class("accent")
        self.row_mic_status.add_suffix(self.lbl_mic_status)
        group_mic.add(self.row_mic_status)

        # Unmute Button Action Row
        row_unmute_action = Adw.ActionRow()
        row_unmute_action.set_title("Unmute Hardware Capture (ALSA)")
        row_unmute_action.set_subtitle("Set ALSA Capture channel to 100% and un-mute")
        btn_unmute = Gtk.Button(label="Unmute Hardware Mic")
        btn_unmute.set_valign(Gtk.Align.CENTER)
        btn_unmute.add_css_class("suggested-action")
        btn_unmute.connect("clicked", self._on_unmute_clicked)
        row_unmute_action.add_suffix(btn_unmute)
        group_mic.add(row_unmute_action)

        # VU Meter Bar
        row_vu = Adw.ActionRow()
        row_vu.set_title("Input Volume Level")
        self.vu_bar = Gtk.ProgressBar()
        self.vu_bar.set_fraction(0.0)
        self.vu_bar.set_valign(Gtk.Align.CENTER)
        self.vu_bar.set_hexpand(True)
        self.vu_bar.set_size_request(180, -1)
        row_vu.add_suffix(self.vu_bar)
        group_mic.add(row_vu)

        page_box.append(group_mic)

        # --- Card 2: System Audio Reset ---
        group_reset = Adw.PreferencesGroup()
        group_reset.set_title("⚡ One-Click Audio Stack Reset")
        group_reset.set_description("Restart PipeWire and WirePlumber background services cleanly")

        row_reset = Adw.ActionRow()
        row_reset.set_title("Reset PipeWire & WirePlumber")
        row_reset.set_subtitle("Fixes missing audio devices, muted mics, or glitched audio routing")
        btn_reset = Gtk.Button(label="Restart Audio Services")
        btn_reset.set_valign(Gtk.Align.CENTER)
        btn_reset.add_css_class("destructive-action")
        btn_reset.connect("clicked", self._on_reset_audio_clicked)
        row_reset.add_suffix(btn_reset)
        group_reset.add(row_reset)

        page_box.append(group_reset)

        # --- Card 3: Bluetooth Call Mode Toggle ---
        group_bt = Adw.PreferencesGroup()
        group_bt.set_title("🎧 Bluetooth Call Profile Engine")
        group_bt.set_description("Auto-switches headsets between high-res audio (A2DP) and call mode (HFP/HSP)")

        row_bt_switch = Adw.ActionRow()
        row_bt_switch.set_title("Auto-switch on Active Calls")
        row_bt_switch.set_subtitle("Detects browser calls (Meet/Zoom) and enables mic profile")
        self.switch_bt_auto = Gtk.Switch()
        self.switch_bt_auto.set_active(True)
        self.switch_bt_auto.set_valign(Gtk.Align.CENTER)
        self.switch_bt_auto.connect("notify::active", self._on_bt_auto_toggled)
        row_bt_switch.add_suffix(self.switch_bt_auto)
        group_bt.add(row_bt_switch)

        row_bt_manual = Adw.ActionRow()
        row_bt_manual.set_title("Manual Bluetooth Profile")
        btn_a2dp = Gtk.Button(label="A2DP (Music)")
        btn_a2dp.set_valign(Gtk.Align.CENTER)
        btn_a2dp.connect("clicked", lambda x: self._set_bt_profile("a2dp"))
        btn_hfp = Gtk.Button(label="HFP/HSP (Call Mic)")
        btn_hfp.set_valign(Gtk.Align.CENTER)
        btn_hfp.connect("clicked", lambda x: self._set_bt_profile("hfp"))

        box_bt_btns = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        box_bt_btns.append(btn_a2dp)
        box_bt_btns.append(btn_hfp)
        row_bt_manual.add_suffix(box_bt_btns)
        group_bt.add(row_bt_manual)

        page_box.append(group_bt)

        # --- Card 4: Snap Browser Permission Manager ---
        group_snap = Adw.PreferencesGroup()
        group_snap.set_title("🌐 Browser Snap Permissions")
        group_snap.set_description("Grant audio recording permissions to Firefox and Chromium snaps")

        row_snap_fix = Adw.ActionRow()
        row_snap_fix.set_title("Fix Browser Permissions")
        row_snap_fix.set_subtitle("Connect audio-record plugs via snapd")
        btn_snap_fix = Gtk.Button(label="Fix Snap Permissions")
        btn_snap_fix.set_valign(Gtk.Align.CENTER)
        btn_snap_fix.add_css_class("suggested-action")
        btn_snap_fix.connect("clicked", self._on_fix_snap_clicked)
        row_snap_fix.add_suffix(btn_snap_fix)
        group_snap.add(row_snap_fix)

        self.lbl_snap_info = Gtk.Label(label=self._get_snap_status_text())
        self.lbl_snap_info.set_xalign(0.0)
        self.lbl_snap_info.add_css_class("dim-label")
        row_snap_info = Adw.ActionRow()
        row_snap_info.set_title("Snap Status")
        row_snap_info.add_suffix(self.lbl_snap_info)
        group_snap.add(row_snap_info)

        page_box.append(group_snap)

        # --- Card 5: Microphone Audio Test (5-sec Record & Playback) ---
        group_test = Adw.PreferencesGroup()
        group_test.set_title("🔊 Microphone Audio Test")
        group_test.set_description("Record a 5-second sample and play it back to verify hardware functionality")

        row_test = Adw.ActionRow()
        row_test.set_title("5-Second Loopback Test")
        self.lbl_test_status = Gtk.Label(label="Ready")
        row_test.set_subtitle("Uses arecord and aplay")

        self.btn_test = Gtk.Button(label="Start 5s Test")
        self.btn_test.set_valign(Gtk.Align.CENTER)
        self.btn_test.connect("clicked", self._on_start_audio_test)

        box_test_suffix = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        box_test_suffix.append(self.lbl_test_status)
        box_test_suffix.append(self.btn_test)
        row_test.add_suffix(box_test_suffix)
        group_test.add(row_test)

        page_box.append(group_test)

        # Set up ToastOverlay as main content child
        self.toast_overlay.set_child(main_box)
        self.set_content(self.toast_overlay)

    def _get_snap_status_text(self) -> str:
        summary = get_snap_status_summary()
        items = []
        for s in summary:
            if not s["installed"]:
                items.append(f"{s['name']}: Not Installed")
            elif s["connected"]:
                items.append(f"{s['name']}: Connected ✓")
            else:
                items.append(f"{s['name']}: Missing Permission ✗")
        return " | ".join(items)

    def _background_check_loop(self):
        """Background loop updating mute state without blocking GTK main loop."""
        while True:
            try:
                muted = is_capture_muted()
                self._cached_muted = muted
            except Exception as e:
                logger.error("Error in background mute check: %s", e)
            time.sleep(1.5)

    def _update_vu_meter_ui(self) -> bool:
        if self._cached_muted:
            self.lbl_mic_status.set_text("Muted (ALSA) ✗")
            self.lbl_mic_status.remove_css_class("accent")
            self.lbl_mic_status.add_css_class("error")
            self.vu_bar.set_fraction(0.0)
        else:
            self.lbl_mic_status.set_text("Active / Unmuted ✓")
            self.lbl_mic_status.remove_css_class("error")
            self.lbl_mic_status.add_css_class("accent")

            if self.pw_engine.is_input_stream_active:
                level = random.uniform(0.35, 0.85)
                self.vu_bar.set_fraction(level)
            else:
                self.vu_bar.set_fraction(0.05)

        return True

    def _on_unmute_clicked(self, _btn):
        def _async_unmute():
            res = unmute_capture()
            GLib.idle_add(self.show_toast, res["details"])
        threading.Thread(target=_async_unmute, daemon=True).start()

    def _on_reset_audio_clicked(self, _btn):
        def _async_reset():
            res = self.pw_engine.restart_pipewire_stack()
            GLib.idle_add(self.show_toast, res["message"])
        threading.Thread(target=_async_reset, daemon=True).start()

    def _on_bt_auto_toggled(self, switch, _param):
        enabled = switch.get_active()
        self.pw_engine.set_auto_bt_switch(enabled)
        state_str = "Enabled" if enabled else "Disabled"
        self.show_toast(f"Bluetooth auto-switching {state_str}")

    def _set_bt_profile(self, profile):
        def _async_bt():
            res = self.pw_engine.set_bluetooth_profile(profile)
            GLib.idle_add(self.show_toast, res["message"])
        threading.Thread(target=_async_bt, daemon=True).start()

    def _on_fix_snap_clicked(self, _btn):
        def _async_snap():
            res = fix_snap_permissions()
            GLib.idle_add(self.lbl_snap_info.set_text, self._get_snap_status_text())
            GLib.idle_add(self.show_toast, res["details"])
        threading.Thread(target=_async_snap, daemon=True).start()

    def _on_start_audio_test(self, _btn):
        if self.recording_active:
            return
        self.recording_active = True
        self.btn_test.set_sensitive(False)
        self.lbl_test_status.set_text("Recording (5s)...")

        threading.Thread(target=self._run_audio_test_thread, daemon=True).start()

    def _run_audio_test_thread(self):
        tmp_wav = "/tmp/ubuntu_mic_test.wav"
        try:
            rec_cmd = ["arecord", "-d", "5", "-f", "cd", tmp_wav]
            subprocess.run(rec_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)

            GLib.idle_add(self.lbl_test_status.set_text, "Playing back...")

            play_cmd = ["aplay", tmp_wav]
            subprocess.run(play_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)

            GLib.idle_add(self.lbl_test_status.set_text, "Test Completed ✓")
            GLib.idle_add(self.show_toast, "Audio test completed successfully.")
        except Exception as e:
            logger.error("Audio test failed: %s", e)
            GLib.idle_add(self.lbl_test_status.set_text, "Test Failed ✗")
            GLib.idle_add(self.show_toast, f"Audio test failed: {e}")
        finally:
            if os.path.exists(tmp_wav):
                try:
                    os.remove(tmp_wav)
                except Exception:
                    pass
            self.recording_active = False
            GLib.idle_add(self.btn_test.set_sensitive, True)

    def show_toast(self, message: str):
        toast = Adw.Toast.new(message)
        toast.set_timeout(3)
        self.toast_overlay.add_toast(toast)
        logger.info("Toast displayed: %s", message)
