# Home eclipse artwork

The built-in image-generation tool created the app-bound grayscale bitmap `android/app/src/main/res/drawable-nodpi/home_eclipse_art.png` from Hax's approved Home reference. Two passes: generate an isolated asset, then make it fully opaque black with a bright, narrow crescent. No Python pixel editing was used. The app itself colors grayscale luminance to the selected global accent while clamping near-black pixels to black.

Final prompt: Preserve the 1536×1024 composition, complete curved sphere, upper-right moon, tilted fine orbital ellipse and blank left text region. Use opaque AMOLED black; a narrow luminous white/silver right-hand crescent fading into dark photographic surface texture; black majority; subtle directional halo; no uniformly outlined circle. Neutral highlights allow global palette tint. Keep padding and curved top/bottom. No UI, text, stars, watermark or checkerboard.

Native use: density-independent image, decoded once off the UI thread, fit fully inside the responsive combined Home date/heading/focus section, ≤1.5 dp slow ambient drift in Full only. The image shown by the generation tool is artwork, not proof of native UI rendering or phone installation.
