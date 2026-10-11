#!/usr/bin/env bash
# Install the local desktop GUI; retain existing companion configuration and archives.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
prefix="$HOME/.local/share/focusdeck"
mkdir -p "$prefix" "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/scalable/apps" "$HOME/.local/share/icons"
chmod 700 "$prefix"
if [[ ! -x "$prefix/venv/bin/python" ]]; then
  python3 -m venv "$prefix/venv"
fi
"$prefix/venv/bin/python" -m pip install "$ROOT/desktop[gui]"
launcher="$HOME/.local/bin/focusdeck-control-center"
if [[ -e "$launcher" ]] && ! head -n 2 "$launcher" | grep -q 'FocusDeck managed launcher'; then
  echo "Refusing to replace an unrelated executable: $launcher" >&2; exit 1
fi
printf '#!/bin/sh\n# FocusDeck managed launcher\nexec "$HOME/.local/share/focusdeck/venv/bin/focusdeck-control-center" "$@"\n' > "$launcher"
chmod 755 "$launcher"
cp "$ROOT/desktop/src/focusdeck/control_ui/assets/app.svg" "$HOME/.local/share/icons/hicolor/scalable/apps/focusdeck.svg"
cp "$ROOT/desktop/src/focusdeck/control_ui/assets/focusdeck-logo.png" "$HOME/.local/share/icons/focusdeck-mountain.png"
# Quoting the absolute home path supports spaces without relying on PATH from KDE.
python3 - "$launcher" "$HOME/.local/share/applications/focusdeck-control-center.desktop" <<'PY'
from pathlib import Path
import sys
icon_path = Path.home() / ".local/share/icons/focusdeck-mountain.png"
executable = sys.argv[1].replace('\\', '\\\\').replace('"', '\\"').replace('`', '\\`').replace('$', '\\$').replace('%', '%%')
Path(sys.argv[2]).write_text('[Desktop Entry]\nType=Application\nName=FocusDeck Control Center\nComment=Prepare sessions, edit cards and observe your phone\nExec="'+executable+'"\nIcon='+str(icon_path)+'\nTerminal=false\nCategories=Utility;Education;\nStartupWMClass=focusdeck-control-center\n')
PY
if command -v update-desktop-database >/dev/null; then update-desktop-database "$HOME/.local/share/applications"; fi
if systemctl --user is-active --quiet focusdeck.service; then
  systemctl --user restart focusdeck.service
fi
if command -v kbuildsycoca6 >/dev/null; then kbuildsycoca6 --noincremental; fi
echo "Installed. Open FocusDeck Control Center from KDE’s launcher."
echo "The existing phone/tablet pairing and archives are retained. No Android rebuild is needed."
