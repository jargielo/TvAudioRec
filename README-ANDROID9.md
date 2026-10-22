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

## v2.0.5 — Playback navigation (six icons)
- Player has six red neon touch tiles on one line: Previous, Play, Pause, Stop, Next, More. No text within the tiles.
- Previous opens the chronologically older completed recording; Next opens a newer recording, sorted using file last-modified time descending (with filename tiebreaker).
- At first / last recording, a toast explains that there is no newer / older recording, respectively. If no file is selected but some exist, navigation opens the newest recording.
- Switching recordings updates the filename and duration, resets the seeking control and visualizer, and starts playback of the selected file. More retains rename, favorites, sharing and trash.
- Preserves Dark AMOLED/Light/System themes and existing transparent neon logo.

## v2.0.6 — Compact player controls
- All six playback icon tiles are truly square, not elongated vertically.
- Their side is responsive to available phone width, capped at 52 dp; horizontal alignment is centered.
- Reduced corners, borders and shadows preserve the original PureCapture cyberpunk red/black styling.
- Playback behavior, visualization, seeking and theme options are unchanged.

## v2.0.7 — Drawer navigation cleanup
- Removed "Importuj nagranie z innej aplikacji" from Moje nagrania.
- In overflow (three dots): Importuj nagrania z innej aplikacji and Diagnostyka now sit directly above Ustawienia, in that order. Menu sequence: Back to recorder, My recordings, Recording configuration, Import audio, Diagnostics, Settings, Theme.
- Import triggers the same system ACTION_OPEN_DOCUMENT audio picker and existing import handler.
- All other recorder, player, theme and codec behavior unchanged.

## v2.0.8 — Import label and compact integrated recordings list
- Drawer import action is now simply "Importuj nagrania".
- Playback signal meter reduced to 64 dp high, from 124 dp.
- Inside playback window below the six icon buttons: a vertical, independently scrollable recordings list, newest recording first, oldest last. Selected item is highlighted. Tap to play it. This does not change the existing Moje nagrania library.
- Local player uses a maximum of 5 visible list items (approx 260 dp), with vertical scrolling for more.
