"""Qt system-tray window lifecycle; imported only by the optional desktop GUI."""
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QMainWindow, QMenu, QSystemTrayIcon


class TrayWindow(QMainWindow):
    def __init__(self, icon, *, tray_enabled=True):
        super().__init__()
        self.setWindowIcon(icon)
        self.tray = None
        if tray_enabled:
            self.tray = QSystemTrayIcon(icon, self)
            self.tray.setToolTip('FocusDeck Control Center')
            self.tray_menu = QMenu(self)
            self.open_action = self.tray_menu.addAction('Open FocusDeck', self.restore)
            self.hide_action = self.tray_menu.addAction('Hide window', self.hide)
            self.tray_menu.addSeparator()
            self.quit_action = self.tray_menu.addAction('Quit FocusDeck', self.quit)
            self.tray.setContextMenu(self.tray_menu)
            self.tray.activated.connect(self._activated)
            # Qt registers the icon if the tray becomes available after startup.
            self.tray.show()

    def _activated(self, reason):
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.DoubleClick):
            self.restore()

    def restore(self):
        # hide()/show() preserves maximized state and compositor-managed geometry.
        if self.isMinimized():
            self.setWindowState(self.windowState() & ~Qt.WindowState.WindowMinimized)
        self.show()
        self.raise_()
        self.activateWindow()  # Wayland focus remains subject to compositor policy.

    def quit(self):
        if self.tray is not None:
            self.tray.hide()
        QApplication.instance().quit()

    def closeEvent(self, event):
        if self.tray is not None and self.tray.isVisible() and QSystemTrayIcon.isSystemTrayAvailable():
            event.ignore()
            self.hide()
        else:
            # Do not leave an unreachable hidden process when there is no tray.
            event.accept()
            self.quit()
