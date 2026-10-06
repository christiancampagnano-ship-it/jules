"""
Unit tests for Ubuntu Mic & Audio Fixer engine modules.
"""

import unittest
from unittest.mock import patch, MagicMock
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../src')))

from engine.alsa import unmute_capture, is_capture_muted
from engine.snap import is_snap_installed, is_audio_record_connected, fix_snap_permissions, get_snap_status_summary
from engine.pipewire import PipeWireEngine


class TestAlsaEngine(unittest.TestCase):
    @patch('subprocess.run')
    def test_unmute_capture_success(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        res = unmute_capture()
        self.assertTrue(res["success"])
        self.assertIn("Success", res["details"])

    @patch('subprocess.run')
    def test_is_capture_muted(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="Capture 0 [off]", stderr="")
        self.assertTrue(is_capture_muted())

        mock_run.return_value = MagicMock(returncode=0, stdout="Capture 80% [on]", stderr="")
        self.assertFalse(is_capture_muted())


class TestSnapEngine(unittest.TestCase):
    @patch('subprocess.run')
    def test_is_snap_installed(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        self.assertTrue(is_snap_installed("firefox"))

        mock_run.return_value = MagicMock(returncode=1)
        self.assertFalse(is_snap_installed("nonexistent"))

    @patch('subprocess.run')
    def test_fix_snap_permissions(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        res = fix_snap_permissions()
        self.assertEqual(res["status"], "completed")


class TestPipeWireEngine(unittest.TestCase):
    @patch('subprocess.run')
    def test_restart_pipewire_stack(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        engine = PipeWireEngine()
        res = engine.restart_pipewire_stack()
        self.assertTrue(res["success"])

    @patch('subprocess.run')
    def test_check_active_input_streams(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="media.class = \"Stream/Input/Audio\"", stderr="")
        engine = PipeWireEngine()
        self.assertTrue(engine.check_active_input_streams())


if __name__ == '__main__':
    unittest.main()
