# FPS+ 1.0

First public release, with the features from the 1.4 test build.

- Requests the REDMAGIC system frame-generation unlock through Shizuku using ADB Shell, without root. Each change is checked by reading the property again.
- Actual interpolation is not verified automatically. The app offers an in-game setup guide and per-game user confirmation.
- Includes a widget, game discovery and quick launch, recovery prompts, diagnostics, and English, Russian, Spanish, German and Japanese localization.

A user reported that the underlying command worked on a REDMAGIC 11S Pro with REDMAGIC OS 11.5.8MR1_EA and Android 16. Other devices and firmware may behave differently. The release APK was built and unit-tested, but this build has not been tested on a physical phone in the build environment.

**Installation:** install and start [Shizuku](https://shizuku.rikka.app/download/) with wireless debugging, then grant FPS+ access. This release uses a different signing key from the 1.4 debug APK. Android cannot update directly from the debug APK; uninstalling it removes its locally stored settings and game marks.

The SHA-256 checksum is provided in `FPS-plus-1.0-SHA256SUMS.txt`.

**Update:** Version 1.0.1 fixes a possible repeat widget action after the activity is recreated. Please use the current APK linked in the repository README.
