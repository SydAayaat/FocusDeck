#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
host="${1:-auto}"
if [[ "$host" == "--help" ]]; then
  echo "Usage: $0 [private-LAN-IP|auto|127.0.0.1]"; exit 0
fi
prefix="$HOME/.local/share/focusdeck"
mkdir -p "$prefix" "$HOME/.config/focusdeck" "$HOME/.local/bin" "$HOME/.config/systemd/user"
chmod 700 "$prefix" "$HOME/.config/focusdeck"
command -v openssl >/dev/null || { echo "Install openssl first." >&2; exit 1; }
python3 -m venv "$prefix/venv"
"$prefix/venv/bin/python" -m pip install "$ROOT/desktop"
for command_name in focusdeck focusdeck-server; do
  launcher="$HOME/.local/bin/$command_name"
  if [[ -e "$launcher" ]] && ! head -n 2 "$launcher" | grep -q 'FocusDeck managed launcher'; then
    echo "Refusing to replace existing unrelated executable: $launcher" >&2; exit 1
  fi
  printf '#!/bin/sh\n# FocusDeck managed launcher\nexec "$HOME/.local/share/focusdeck/venv/bin/%s" "$@"\n' "$command_name" > "$launcher"
  chmod 755 "$launcher"
done
"$prefix/venv/bin/focusdeck-server" --init --host "$host"
cp "$ROOT/desktop/systemd/focusdeck.service" "$HOME/.config/systemd/user/focusdeck.service"
systemctl --user daemon-reload
systemctl --user enable --now focusdeck.service
systemctl --user restart focusdeck.service
echo "FocusDeck installed. Add ~/.local/bin to PATH if needed. Run: focusdeck pairing"
