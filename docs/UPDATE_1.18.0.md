# Desktop 1.18.0 · complete text input and session output

The Control Center covers the existing daily CLI workflows through the GUI. Readable indented GPT text now accepts every session/achievement contract field: conditional tiers, reward text/alternatives, independent session gates, classification/scoring, comparisons, criteria/weights, daily objective replacements, progress SET/increment, actual events, icons and lifecycle operations. Four headings route drafts: CARD, SESSION, UPDATE, BATCH. Original shorthand and canonical JSON remain compatible. See [input reference and workflow mapping](GPT_CONTROL_CENTER_INPUT.md).

Session Builder edits full conditions and scoring through schema-derived named-field forms, including nested objects, optional fields, arrays and tier/alternative ordering. Card Builder’s Prepare update covers all nine operations, card selection and validated editing; Add measured progress opens a selected-card increment. Canonical upserts retain receipt IDs instead of becoming new definition commands. Delivery journals preserve exact payloads/ACKed steps; editing a previously used receipt into different content is rejected. Batches apply in order and are not transactions. Send & start is an explicit fresh launch; Send and Start prepared remain available.

Overview shows synced completed/ended session report actions. Analytics offers the same actions for selected attempts: View output, Copy for GPT, Save .txt. The visible selectable report has readable/full JSON toggles with native clipboard and .txt/.json save. Copies use the existing complete report generator and archived object, work offline and honor deletion tombstones on every click. Selecting older reports switches to All history when needed rather than silently selecting another session. No manual IDs are required. Analytics also exposes selected-session JSON/CSV export and confirmed single/all synchronized deletion; Settings exports all history/rebuilds CSV. Devices exposes disconnected-phone pairing recovery and restart of the already-installed companion.

The phone remains timer/achievement authority; tablet viewers stay read-only. No timer, reward, measurement, archive, signing, pairing or Android code changes. Android stays 1.17.0, versionCode 21. The earlier +5-minute removal stays in place; historical extension evidence remains readable. Administrative setup/signing/build/OS configuration are outside the daily planner. No cloud model/API key is used. The new parser uses bounded safe PyYAML with JSON-like scalars; duplicate keys, tags, aliases, merges, malformed/oversized drafts reject instead of being guessed.

## Install on the existing PC

Quit the old Control Center fully with Ctrl+Q or tray Quit first; closing only hides it. Extract the whole FocusDeck-v1.18.0.zip into the parent of your existing FocusDeck project, overwriting source, then run the existing desktop installer once:

```bash
cd "$HOME/Projects/FocusDeck"
bash scripts/setup-control-center.sh
```

It updates the existing desktop environment/launcher and restarts an already-active companion. Open FocusDeck Control Center from KDE’s launcher. Preserve existing configuration/tokens/certificates, phone/tablet data and signing identity. No uninstall, re-pairing, Android build or APK is needed. The updated GPT source pack’s README_SETUP.md lists the Sources to replace and instruction text to paste; retain your edited reward catalog and subject materials.

## Verification and limits

- Full desktop suite: 225 collected, 224 passed, one existing host AF_UNIX skip. Nineteen new contract/workflow regressions cover existing session/achievement fixture round-trips, full reward preservation, all nine operations, unsafe/ambiguous YAML rejection, ordered retries, exact report selection/copies, deleted-report suppression, atomic UTF-8 saving and source protection, service restart and explicit start routing.
- Battery-tool fixtures: seven passed.
- Actual Qt WebEngine/WebChannel offscreen: 102 responsive page/period/sidebar layouts at 420–1672 px, zero horizontal overflow/JavaScript errors; 28 workflow assertions passed. New checks cover reward paste/form editing without policy loss, batch redirects/receipt retention, icon variant selection, visible/copyable text and JSON reports, older-session selection and Overview final-report actions.
- Python/JavaScript syntax, wheel build/install and bundled source/assets byte comparison passed. Installed-wheel native preview rendered.
- Physical KDE/Wayland/Krohnkite/tray, native chooser/clipboard in the target desktop session and live phone/tablet LAN execution remain unexecuted. Existing transport tests exercise ACK contracts locally. Native offscreen checks do not establish measured FPS or real battery performance.
- No APK assembled, target installation, app reset, re-pairing or remote push performed.

Evidence: docs/verification/control-center-1.18/. Full source and GPT pack exclude private keys, APK/build outputs and personal credentials.
