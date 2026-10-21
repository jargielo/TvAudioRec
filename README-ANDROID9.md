# AudioRelay Grabber 1.9.9 – Final configuration & icon
- Homepage: Rejestrator dźwięku and Odtwarzacz only.
- Three-dot drawer: Powrót do rejestratora, Moje nagrania, Konfiguracja nagrania, Ustawienia, Diagnostyka.
- Konfiguracja nagrania collapsible sections: Format zapisu, Częstotliwość, Jakość nagrania, Kanały (Mono/Stereo), Źródło dźwięku (four modes).
- Settings page no longer duplicates recording configuration.
- Stereo PCM is recorded as 2 channels where device supports it; captures internal stereo and mixes microphone into both channels. Bluetooth mic mono is mixed into both channels with stereo internal audio.
- Seven output formats (M4A WAV FLAC MP3 AAC 3GP OGG); FFmpegKit Audio bundled.
- Launcher: opaque black PNG for legacy and round icons, plus adaptive launcher with solid black background and neon red ring foreground; no monochrome layer, to avoid system themed white icon on supported launchers.
- Android 9: mic-only; Android 10+: MediaProjection/internal capture where supported.
- Unique package ID cyber199, so install alongside older builds to avoid signature conflicts. Remove older shortcuts when testing the new icon.
