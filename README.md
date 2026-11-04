# FPS+ 1.0

FPS+ is an Android app that uses [Shizuku](https://shizuku.rikka.app/) with ADB Shell privileges to request REDMAGIC frame-generation support for games. No root, PC connection, ads, analytics or network permission is required by the app. It is an independent project and is not affiliated with REDMAGIC, nubia or Shizuku.

[Русская инструкция](README.ru.md) · [Download FPS+ 1.0 APK](https://github.com/Floppaputhon/FPS-plus/raw/refs/heads/main/dist/FPS-plus-1.0-release.apk)

## What it does

- Reads and changes only `vendor.gpp.allgame.enable`: `1` requests the unlock and `0` is the assumed off state. Every change is checked by reading the property again.
- Separates a confirmed property value from actual frame interpolation. The app **cannot automatically verify** whether REDMAGIC generates intermediate frames; users can save their own result for each game.
- Displays up to three detected games or gaming tools for quick launch, ordered as GameHub, Winlator, DroidDeck, Roblox, Minecraft, then other detected games. The full library and manual game selection are available. Android does not classify every game reliably; add a missed game manually.
- Offers a home-screen widget, setup guide, device information, diagnostic copy, and a prompt to restore the property after a reboot when Shizuku is available. It does not change the property automatically in the background.
- Supports English, Russian, Spanish, German and Japanese. The first launch asks for a language with English preselected; the choice can be changed later.

A user reported that the command worked on a REDMAGIC 11S Pro (nubia NX809J, REDMAGIC OS 11.5.8MR1_EA, Android 16) with Shizuku running as ADB Shell UID 2000. Compatibility with other devices and firmware is not guaranteed. This release was built and unit-tested in the development environment but has not been tested on a physical device there.

## Setup

1. Install the signed APK from [the signed release APK](https://github.com/Floppaputhon/FPS-plus/raw/refs/heads/main/dist/FPS-plus-1.0-release.apk) and install [Shizuku from its official site](https://shizuku.rikka.app/download/).
2. Enable Android developer options and wireless debugging. Pair and start Shizuku using its [official guide](https://shizuku.rikka.app/guide/setup/). On Android 11 and later this can be done without a PC. Without root, Shizuku usually has to be started again after reboot.
3. Open FPS+, select a language and grant Shizuku access. Shizuku must run as ADB Shell (UID 2000). If permission was denied permanently, allow the app from Shizuku settings.
4. Enable the system unlock, wait for the read-back result, and follow the in-app guide to configure the REDMAGIC gaming panel. Check the actual result in the game yourself.

If the property was initially empty or absent, Android's supported interface here cannot distinguish or restore its absence. Setting `0` is the assumed off state, not a literal restoration of absence. Initial values `0` and `1` can be restored explicitly; other original values are not written back.

**Upgrading from the 1.4 debug build:** this release is signed with a separate release key. Android cannot install it over the debug build; uninstalling the debug build removes its locally saved settings and game marks. Future official releases must use the same release signing key.

**Release signing certificate SHA-256:** `482cb4f895f15a11f742c6c6c41eba3626c396143653367ba2ad2a2e6b3b5465`

## Build

Open the project in Android Studio with JDK 17 or newer, Android SDK Platform 36 and Build Tools 36.0.0. For a debug build run `./gradlew assembleDebug`.

For a signed release provide `FPSPLUS_KEYSTORE` (absolute path), `FPSPLUS_KEY_ALIAS`, and `FPSPLUS_KEY_PASSWORD` (the password shared by the keystore and key) as environment variables, then run `./gradlew assembleRelease`. Without these variables the release APK is unsigned. The private signing key and its password are deliberately not part of the repository. Keep both backed up securely: the same key is required to sign future updates.

Run `./gradlew testDebugUnitTest` to check prioritization, game filtering and language defaults. The app detects launchable user apps and Android-classified games locally; it does not request the all-packages permission or transmit the installed-app list.

The FPS icon and instructional diagrams are original vector/Compose graphics. The diagrams were drawn from user-provided reference images, which are not included. Third-party dependencies retain their own licenses, including AndroidX and Shizuku API (Apache 2.0).
