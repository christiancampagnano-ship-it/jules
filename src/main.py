#!/usr/bin/env python3
"""
Ubuntu Mic & Audio Fixer Main Entrypoint.
Supports both GUI mode (GTK4 + Libadwaita + System Tray) and background daemon mode (--daemon).
"""

import sys
import os
import argparse
import signal
import time
import logging

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("ubuntu-mic-fixer")

# Ensure src path is in sys.path when running directly
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from engine.pipewire import PipeWireEngine
from engine.alsa import unmute_capture
from engine.snap import fix_snap_permissions


def run_daemon_mode():
    """Run as background daemon service without GUI."""
    logger.info("Starting Ubuntu Mic & Audio Fixer Daemon...")

    # Initial system fixes on daemon startup
    logger.info("Unmuting ALSA hardware capture channels...")
    unmute_capture()

    logger.info("Checking & fixing Snap audio permissions for Firefox and Chromium...")
    fix_snap_permissions()

    # Start PipeWire engine monitoring loop
    pw_engine = PipeWireEngine()
    pw_engine.start_monitoring()

    logger.info("Background daemon is active and monitoring PipeWire audio streams.")

    def signal_handler(sig, frame):
        logger.info("Shutdown signal received. Stopping daemon...")
        pw_engine.stop_monitoring()
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    while True:
        time.sleep(1)


def run_gui_mode():
    """Run full GTK4 / Libadwaita GUI application with System Tray."""
    logger.info("Starting Ubuntu Mic & Audio Fixer GUI...")

    import gi
    gi.require_version('Gtk', '4.0')
    gi.require_version('Adw', '1')
    from gi.repository import Gtk, Adw, GLib

    from gui.window import MicFixerWindow
    from gui.indicator import SystemTrayIndicator

    pw_engine = PipeWireEngine()
    pw_engine.start_monitoring()

    class MicFixerApplication(Adw.Application):
        def __init__(self):
            super().__init__(application_id="com.ubuntu.MicFixer", flags=0)
            self.window = None
            self.indicator = None

        def do_activate(self):
            if not self.window:
                self.window = MicFixerWindow(self, pw_engine)
            self.window.present()

            if not self.indicator:
                self.indicator = SystemTrayIndicator(
                    on_show_window=self.show_window,
                    on_reset_audio=lambda: pw_engine.restart_pipewire_stack(),
                    on_fix_snap=lambda: fix_snap_permissions(),
                    on_quit=self.quit_app
                )

        def show_window(self):
            if self.window:
                self.window.present()

        def quit_app(self):
            pw_engine.stop_monitoring()
            self.quit()

    app = MicFixerApplication()
    return app.run(sys.argv)


def main():
    parser = argparse.ArgumentParser(description="Ubuntu Mic & Audio Fixer")
    parser.add_argument("--daemon", action="store_true", help="Run in background daemon mode")
    args = parser.parse_args()

    if args.daemon:
        run_daemon_mode()
    else:
        run_gui_mode()


if __name__ == "__main__":
    main()
