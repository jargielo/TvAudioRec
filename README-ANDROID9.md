# AudioRelay Grabber 1.9.8 — Huawei P20 Lite and S24 Ultra

Recording sources: local microphone, internal playback capture, local microphone + internal playback, Bluetooth microphone + internal playback. Internal playback requires API 29+ and system consent. Bluetooth mix requires a compatible connected Bluetooth input; startup fails explicitly if no valid input is routed.
Formats: M4A, WAV, FLAC, MP3, AAC, 3GP and OGG/Opus. FFmpegKit Audio 6.0.4 and its native encoding libraries are bundled with the APK through Gradle dependency. During recording PCM WAV is written; after STOP the file is transcoded to the selected format. If transcoding fails, WAV is preserved rather than lost.
Player has the selected-file action card. Recordings library displays a pure list; tapping a file opens the player.
This binary includes third-party FFmpegKit native libraries (LGPLv3 and associated licenses). https://github.com/ffmpegkit-maintained/ffmpeg
Separate app ID cyber198 for this iterative build; old app content remains in old app-specific storage.
