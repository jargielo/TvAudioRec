# PureCapture v2.0.1
- Creator: n2s.
- Compatible install minimum Android 9 (API 28), no declared maximum; runtime features may differ by device and Android versions.
- Main tabs: Recorder and Player. Overflow drawer: Return to recorder, My recordings, Recording configuration, Settings, Diagnostics.
- All configuration parameters display as vertical inline expanding sections: audio source, format, sample rate, bitrate, channels, output directory.
- File storage via Android Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE) with persisted URI permission. Finished recordings are copied to the chosen folder and kept in the local My Recordings library for playback, favorites, and deletion. A failed external copy never deletes the local recording.
- "Newest", "Name", "Favorites", "Trash" are fixed equal-width buttons in one row, without horizontal scrolling.
- Drawer/logo uses cyberpunk_foreground transparent PNG: red neon ring/dot only, no black logo tile. Installed launcher icon retains opaque black plus adaptive dark background.
- Formats: WAV / M4A / FLAC / MP3 / AAC / 3GP / OGG. Encoders from FFmpegKit Audio.
- Source: microphone; internal; mic + internal; Bluetooth mic + internal. Android 9 supports mic only. Internal audio capture requires Android 10+ and source cooperation.
- Mono and stereo depending on hardware capabilities.
- Package: pl.n2s.purecapture.

## Changes in v2.0.1
- Configuration accordion automatically closes after choosing a value; the active choice is visible in the collapsed summary.
- Return-to-recorder drawer item has neither a leading red dot nor trailing chevron.
- Default public output is /storage/emulated/0/Music/PureCapture. Android 9 requests WRITE_EXTERNAL_STORAGE, Android 10+ uses MediaStore with RELATIVE_PATH=Music/PureCapture. A locally indexed library copy is retained for app playback, favorites and recovery if public output fails. Custom SAF folder takes precedence.

## v2.0.2
- Player: filename, live signal visualizer (Android Visualizer attached to MediaPlayer session), only four actions PLAY, PAUSE, STOP, MORE. Remaining functions including favorites, rename, share, trash and recordings library live in MORE.
- Recorder meter: increased sensitivity, 90 ms redraw; flat line during silence, moving bars for measured input.
- 3-dot navigation offers Systemowy/Dark/Light theme. Dark AMOLED defaults unchanged. Light is a true reversed UI palette. A system theme follows night-mode configuration. Neon circle and dot remain transparent in header and drawer.
- Requires RECORD_AUDIO and MODIFY_AUDIO_SETTINGS for output Visualizer, which might still be restricted by some OEM media pipelines.


## v2.0.3 – automatic latest recording selection
- The recorder publishes a completed file only after WAV finalization, successful FFmpeg transcode, or WAV fallback if conversion failed.
- The player automatically selects the newly saved recording without starting playback. On returning to the player, the filename is already displayed.
- The last completed file path and timestamp are persisted, so a process restart restores the last recording. If it is gone, the most recent available local recording is used.
- The recorder and player continue using the same local app library even when the recording is also copied to the public Music/PureCapture directory.

The player does not auto-start the file; press ODTWÓRZ to begin playback.

## v2.0.4 – neon playback panel prototype
- Player window matches the approved visual style within the app's Dark, Light, and System themes; default Dark remains AMOLED black.
- Only filename, real playback signal meter, red seek bar with elapsed / total playback times, and 4 equal icon-only red neon tiles (Play, Pause, Stop, More).
- More contains file actions, favorites, rename, share, trash, and My Recordings. Icons drawn as vector canvas shapes with accessibility labels, not emoji or text pretending to be icons.
- A new recording remains auto-selected after conversion as in v2.0.3; timer and seek are functional when MediaPlayer supports the file.
