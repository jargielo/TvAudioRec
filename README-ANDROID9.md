# AudioRelay Grabber 1.9.5 — Cyberpunk raster icon (Android 9 Pie)
- **Fix verified at packaging:** bitmap PNG for launcher and in-app logo, rendered from the neon circle visual on a completely opaque black background.
- Old white-outline vector assets and the Android adaptive icon XML are deleted so EMUI 9 cannot select them as fallbacks.
- Includes mipmap-mdpi through mipmap-xxxhdpi launcher PNG assets.
- User interface: a left-side sliding menu accessible via the three vertical dots; pages: Recording, My recordings, Player, Settings, Diagnostics.
- Internal recording source remains visible but unsupported on Android 9.
- Huawei P20 Lite / EMUI 9.1; minSdk 28; Android 9 microphone M4A/WAV recording.
- **Separate app ID for this build:** pl.n2s.grabberpie.cyber195. This avoids Android debug-key signature mismatch with earlier builds and prevents accidental overwriting of prior recordings. Earlier recordings can be imported with the file picker.
- Product display name **Grabber Cyberpunk** so it is easily distinguished from older versions.
