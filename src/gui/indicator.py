"""
Ayatana AppIndicator System Tray implementation.
Provides GNOME top bar indicator icon and quick action menu.
Supports GTK4 / AyatanaAppIndicator.
"""

import sys
import logging

logger = logging.getLogger("ubuntu-mic-fixer.indicator")

try:
    import gi
    gi.require_version('AyatanaAppIndicator3', '0.1')
    from gi.repository import AyatanaAppIndicator3 as appindicator
    from gi.repository import Gtk
    APPINDICATOR_AVAILABLE = True
except Exception as e:
    logger.warning("AyatanaAppIndicator3 not available: %s", e)
    APPINDICATOR_AVAILABLE = False


class SystemTrayIndicator:
    def __init__(self, on_show_window=None, on_reset_audio=None, on_fix_snap=None, on_quit=None):
        self.on_show_window = on_show_window
        self.on_reset_audio = on_reset_audio
        self.on_fix_snap = on_fix_snap
        self.on_quit = on_quit
        self.indicator = None

        if APPINDICATOR_AVAILABLE:
            self._init_indicator()

    def _init_indicator(self):
        try:
            self.indicator = appindicator.Indicator.new(
                "ubuntu-mic-fixer",
                "audio-input-microphone-symbolic",
                appindicator.IndicatorCategory.HARDWARE
            )
            self.indicator.set_status(appindicator.IndicatorStatus.ACTIVE)
            self.indicator.set_menu(self._build_menu())
            logger.info("Ayatana AppIndicator initialized successfully.")
        except Exception as e:
            logger.error("Failed to initialize Ayatana AppIndicator: %s", e)

    def _build_menu(self):
        # Build GTK menu structure compatible with AyatanaAppIndicator
        try:
            from gi.repository import Gtk as Gtk3
        except ImportError:
            pass

        menu = Gtk.Menu() if hasattr(Gtk, 'Menu') else None
        if not menu:
            # If Gtk4 is loaded, create menu using Gtk.PopoverMenu / Gio.Menu if needed or fallback
            return None

        item_open = Gtk.MenuItem(label="🎙️ Open Ubuntu Mic Fixer")
        item_open.connect("activate", lambda w: self.on_show_window() if self.on_show_window else None)
        menu.append(item_open)

        item_reset = Gtk.MenuItem(label="⚡ Reset System Audio")
        item_reset.connect("activate", lambda w: self.on_reset_audio() if self.on_reset_audio else None)
        menu.append(item_reset)

        item_snap = Gtk.MenuItem(label="🌐 Fix Snap Mic Permissions")
        item_snap.connect("activate", lambda w: self.on_fix_snap() if self.on_fix_snap else None)
        menu.append(item_snap)

        item_quit = Gtk.MenuItem(label="🚪 Quit")
        item_quit.connect("activate", lambda w: self.on_quit() if self.on_quit else sys.exit(0))
        menu.append(item_quit)

        menu.show_all()
        return menu
