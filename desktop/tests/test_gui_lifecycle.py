"""Launcher activation and real Qt close/hide/restore lifecycle regressions."""
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from focusdeck.gui_instance import GuiInstance


class InstanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.private = Path(self.temp.name) / 'control-center'
        self.instances = []

    def instance(self):
        value = GuiInstance(self.private)
        self.instances.append(value)
        return value

    def tearDown(self):
        for value in self.instances: value.close()
        self.temp.cleanup()

    def test_secondary_launcher_activates_existing_owner_once(self):
        primary = self.instance(); secondary = self.instance()
        self.assertTrue(primary.primary); self.assertFalse(secondary.primary)
        secondary.activate()
        self.assertTrue(primary.poll()); self.assertFalse(primary.poll())
        self.assertEqual(primary.activation.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.private.stat().st_mode & 0o777, 0o700)
        secondary.activate(); self.assertTrue(primary.poll())

    def test_startup_request_survives_until_first_poll(self):
        primary = self.instance()
        self.instance().activate()
        # GUI construction can finish after the launcher has sent its request.
        self.assertTrue(primary.poll())

    def test_restart_ignores_old_request_and_releases_lock(self):
        primary = self.instance(); self.instance().activate(); primary.close()
        replacement = self.instance()
        self.assertTrue(replacement.primary); self.assertFalse(replacement.poll())

    def test_corrupt_or_symlink_mailbox_cannot_activate(self):
        primary = self.instance(); primary.activation.write_text('not json')
        self.assertFalse(primary.poll())
        primary.activation.unlink(); primary.activation.symlink_to('/dev/null')
        self.assertFalse(primary.poll())


QT_AVAILABLE = importlib.util.find_spec('PySide6') is not None and bool(
    os.environ.get('QT_QPA_PLATFORM') or os.environ.get('DISPLAY') or os.environ.get('WAYLAND_DISPLAY'))

@unittest.skipUnless(QT_AVAILABLE, 'Qt GUI extra/display unavailable; use QT_QPA_PLATFORM=offscreen for native lifecycle checks')
class TrayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from PySide6.QtWidgets import QApplication
        cls.app = QApplication.instance() or QApplication([])
        cls.app.setQuitOnLastWindowClosed(False)

    def setUp(self):
        from PySide6.QtGui import QIcon
        from focusdeck.tray_window import TrayWindow
        self.available = patch('focusdeck.tray_window.QSystemTrayIcon.isSystemTrayAvailable', return_value=True)
        self.available.start()
        self.quit_mock = patch.object(self.app, 'quit')
        self.quit_call = self.quit_mock.start()
        self.window = TrayWindow(QIcon())
        self.window.resize(800, 600); self.window.show(); self.app.processEvents()

    def tearDown(self):
        self.window.tray.hide(); self.window.hide(); self.window.deleteLater()
        self.app.processEvents(); self.quit_mock.stop(); self.available.stop()

    def test_close_hides_without_quitting_and_restores_same_window(self):
        self.assertFalse(self.window.close())
        self.assertFalse(self.window.isVisible()); self.quit_call.assert_not_called()
        self.assertTrue(self.window.tray.isVisible())
        self.window.open_action.trigger(); self.app.processEvents()
        self.assertTrue(self.window.isVisible()); self.assertEqual(self.window.size().width(), 800)

    def test_maximized_state_survives_close_and_restore(self):
        self.window.showMaximized(); self.app.processEvents()
        self.window.close(); self.window.restore(); self.app.processEvents()
        self.assertTrue(self.window.isMaximized())

    def test_minimized_window_is_visible_after_restore(self):
        self.window.showMinimized(); self.app.processEvents()
        self.window.restore(); self.app.processEvents()
        self.assertTrue(self.window.isVisible()); self.assertFalse(self.window.isMinimized())

    def test_tray_click_restores_but_context_menu_does_not(self):
        from PySide6.QtWidgets import QSystemTrayIcon
        self.window.hide()
        self.window.tray.activated.emit(QSystemTrayIcon.ActivationReason.Context)
        self.assertFalse(self.window.isVisible())
        self.window.tray.activated.emit(QSystemTrayIcon.ActivationReason.Trigger)
        self.assertTrue(self.window.isVisible())

    def test_explicit_quit_exits_and_removes_tray_icon(self):
        self.window.quit_action.trigger()
        self.quit_call.assert_called_once(); self.assertFalse(self.window.tray.isVisible())

    def test_missing_tray_exits_instead_of_hiding_unreachable_process(self):
        with patch('focusdeck.tray_window.QSystemTrayIcon.isSystemTrayAvailable', return_value=False):
            self.assertTrue(self.window.close())
        self.quit_call.assert_called_once()

    def test_demo_close_exits_without_background_tray(self):
        from PySide6.QtGui import QIcon
        from focusdeck.tray_window import TrayWindow
        demo = TrayWindow(QIcon(), tray_enabled=False)
        demo.show(); self.assertTrue(demo.close())
        self.quit_call.assert_called_once(); self.assertIsNone(demo.tray)
        demo.deleteLater()
