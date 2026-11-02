# CodeAque Launcher for Android handhelds (RG3566 and similar)

Android stays the system underneath. CodeAque is just the **home screen** on top: the same charcoal,
neon-cyan look and white CODEAQUE logo as the Handheld Edition, with big tiles that open the apps you
already have.

> **0.9.4:** My Games (installed games with their icons, installed emulators, your own game folders), All Apps, true full screen.
> Checked on an Android 11 emulator only; not yet on a real K56 / Android 14 device.

> **Untested on a real RG3566.** It was checked on an Android 11 emulator (1280x720 and 640x480)
> with a keyboard standing in for the pad.

## Install

1. On the handheld: *Settings > Security* (or *Apps > Special access*) > allow **Install unknown apps**
   for your file manager or browser.
2. Download `codeaque-launcher.apk` from the v0.8.0 release (or copy it over on a USB stick / SD card) and open it. Tap **Install**.
3. Press the **Home** button. Android asks which home app to use: pick **CodeAque**, then **Always**.
   (Later: CodeAque > Settings > Home app to switch back.)

From a PC with adb instead: `adb install codeaque-launcher.apk`.

## Using it

| Pad | Touch | Does |
|---|---|---|
| D-pad / left stick | tap | move |
| L1 / R1 | tap a category | change category |
| A | tap the selected tile | open |
| B | | back to the category row |
| Y (or Select) | | Settings |

* Tiles open the installed app. If it is missing, the tile says **GET** and A opens its Play Store page.
* Cloud tiles (Xbox Cloud Gaming, GeForce NOW, Amazon Luna, Boosteroid) say **WEB** when their app is
  missing: they open the official site in your browser instead.
* **Retro** opens RetroArch (and PPSSPP, DuckStation, Dolphin, Mupen64Plus FZ, Flycast, DraStic if installed).
  No BIOS, ROMs, games or keys are included — bring your own.
* **Apps > All apps** lists every installed app by name.
* **Settings > Theme** cycles the looks: **Neon** (the default: charcoal, white logo, cyan), **Power Up** (orange and
  blue, a glowing energy aura and a rising power-up sound) and **Catch** (red, white and yellow, a
  turning sunburst with confetti stars, a spinning star on the selected tile and a bright chime).
  All art and sounds are original (made by `tools/make_powerup_sfx.py` and `tools/make_catch_sfx.py`).
* **Settings > Wallpaper** picks a picture from the device as the background (it is darkened a little
  so the text stays readable). Choose Wallpaper again to go back to the theme background.
* **Settings > Effects**: Auto (30 fps waves, the default), On (60 fps) or Off (still background, saves battery).

## Updating

Settings > Update checks the latest GitHub release, downloads `codeaque-launcher.apk`, checks it against `SHA256SUMS.txt` and then hands it to Android's installer. You confirm there, and you may need to allow "Install unknown apps" for CodeAque once. Versions 0.9.1 and older can't update themselves, so install 0.9.2 by hand once.

## Christmas theme

Settings > Theme > Christmas shows falling snow and snow-capped tiles. From 1 December the launcher suggests it once a year.

## Build it yourself

Needs JDK 17+ and the Android SDK (platform 34, build-tools 34):

```
cd android
ANDROID_HOME=/path/to/sdk ./build.sh        # -> android/dist/codeaque-launcher.apk
./build.sh test                             # plain-Java checks of the tile list
```

Android Studio / Gradle (`gradle assembleDebug`) works too. Release builds are signed with a
debug-style key unless you set `CODEAQUE_KEYSTORE`, `CODEAQUE_KS_PASS` and `CODEAQUE_KS_ALIAS`.

## What is untested

- The full self-update install prompt hasn't been run end to end: the only release so far is 0.9.2 itself. The version check was tested on the emulator.


* The wallpaper picker (Android's file chooser) was not clicked through on the emulator; the themes were.
* The theme sounds were generated and checked as files, not heard on the device speaker.

* Real RG3566 hardware: its pad's key codes, the 5in screen, speed, battery reading, and whether its
  Android build lets you change the home app (some vendor builds hide that choice).
* The package names of some apps (Amazon Luna, Boosteroid, DraStic) may differ by region or store.
* Because the APK is signed with a debug-style key, updating needs the same key; uninstall first if Android refuses.
