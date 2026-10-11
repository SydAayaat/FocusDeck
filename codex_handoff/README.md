# FocusDeck Codex Implementation Handoff

This directory is an **implementation specification** plus untouched locked screenshots, not a replacement application or a Figma export.

## Fastest use

1. Extract this directory to your full local FocusDeck repository root as `codex_handoff/` (so it becomes `FocusDeck/codex_handoff/CODEX_START_HERE.md`). The repository itself must contain the existing source code, not just README.md.
2. Open Codex from the local FocusDeck repository root.
3. Paste:

   > Implement the native FocusDeck Android UI from `codex_handoff/CODEX_START_HERE.md`. Read all seven untouched image references and `DESIGN_UPDATED.md`. Preserve all existing functionality, data, sync and timer behavior. Operate autonomously in small, safely checkpointed stages M0–M8. Build debug as needed for automated ADB screenshot QA and use `codex_handoff/tools/compare_ui.py` plus visual inspection to correct deviations. Don't stop at a plan and don't redesign the screenshots; implement and verify. Keep me informed with short milestone status reports; ask only about genuine access/safety blockers. Do not push or merge code without separate permission.

That is the complete kickoff; the long task contract lives in `CODEX_START_HERE.md`.

## Reference map

| File | Meaning |
| --- | --- |
| `references/home.jpeg` | Home (normal) |
| `references/home_!.jpeg` | Home with popup (same screen, another state) |
| `references/achivements.jpeg` | Achievements, normal mode |
| `references/day.jpeg` | Analytics / Day |
| `references/weekl.jpeg` | Analytics / Week |
| `references/month.jpeg` | Analytics / Month |
| `references/year.jpeg` | Analytics / Year |

These files are copied unmodified from the user's locked references; SHA-256 is recorded in `REFERENCE_SHA256.json`.

## No automated visual capture?

The comparison script **does not capture screenshots**. Codex should use the actual running Android application via ADB/Compose UI tests, then compare. If device/debug automation isn't possible, it must say `NOT RUN` and never claim the UI was visually verified.
