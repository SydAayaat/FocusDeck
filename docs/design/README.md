# Supplied design references

`minimal-dial.png` and `session-report.png` are the user's supplied visual references. They guided the thin dial, light typography, restrained controls and report layout. They are concept renderings, **not screenshots of the built Android app**. Actual Compose rendering still requires device/emulator verification.

The 1.13 page references are `approved-home-eclipse.png`, `approved-study-history-gold.png` and `approved-session-complete-gold.png`. They are the exact images supplied by Hax for this update; they are design inputs, not app screenshots. See UPDATE_1.13.0.md for implementation and verification limits.

The 1.13 Home correction bundles the generated monochrome `android/app/src/main/res/drawable-nodpi/home_eclipse_art.png` bitmap. The native renderer applies the current global accent and preserves black pixels. It is real app artwork, not a screenshot of the app. Prompt/method provenance is in ECLIPSE_ARTWORK.md.
