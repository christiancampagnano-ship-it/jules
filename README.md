# Ubuntu Mic & Audio Fixer

Native GTK4, Libadwaita, and PipeWire application & background service tailored specifically for Ubuntu 24.04 LTS (GNOME 46+).

---

## Features

- **🎙️ Microphone Status & VU Meter:** Monitors ALSA capture states and provides a real-time input level meter.
- **⚡ One-Click Audio Stack Reset:** Safely restarts PipeWire and WirePlumber services cleanly via Systemd user units (`systemctl --user restart pipewire pipewire-pulse wireplumber`).
- **🎧 Bluetooth Profile Engine:** Automatically switches connected Bluetooth headsets from A2DP (high quality audio) to HFP/HSP (FastStream/mSBC) when active call streams are detected (Google Meet, Zoom, WebRTC) and reverts back when calls end.
- **🌐 Snap Permission Manager:** Detects Firefox and Chromium Snaps and connects `audio-record` permissions (`snap connect firefox:audio-record`).
- **🔊 Loopback Audio Test:** Built-in 5-second microphone recording and playback test using `arecord` and `aplay`.
- **📌 GNOME System Tray:** Integrated Ayatana AppIndicator (`gi.repository.AyatanaAppIndicator3`) top bar tray icon with quick actions.

---

## File Structure

```
ubuntu-mic-fixer/
├── README.md
├── build_deb.sh
├── debian/
│   └── control
├── systemd/
│   └── ubuntu-mic-fixer.service
├── assets/
│   ├── ubuntu-mic-fixer.desktop
│   └── ubuntu-mic-fixer.svg
└── src/
    ├── main.py
    ├── engine/
    │   ├── __init__.py
    │   ├── alsa.py
    │   ├── pipewire.py
    │   └── snap.py
    └── gui/
        ├── __init__.py
        ├── indicator.py
        └── window.py
```

---

## How to Build the `.deb` Package

Make sure build tools are executable and run `build_deb.sh`:

```bash
chmod +x build_deb.sh
./build_deb.sh
```

This generates `ubuntu-mic-fixer.deb` in the current directory.

---

## How to Install and Enable

### 1. Install Dependencies & Package

```bash
sudo apt update
sudo apt install -y python3-gi gir1.2-gtk-4.0 gir1.2-adw-1 gir1.2-ayatanaappindicator3-0.1 pipewire wireplumber alsa-utils snapd

sudo dpkg -i ubuntu-mic-fixer.deb
sudo apt-get install -f # Resolve any missing dependencies
```

### 2. Enable & Start Background Service

```bash
systemctl --user daemon-reload
systemctl --user enable --now ubuntu-mic-fixer.service
```

### 3. Launch GUI Application

You can launch "Ubuntu Mic & Audio Fixer" from the Ubuntu Application Grid, or via terminal:

```bash
ubuntu-mic-fixer
```
