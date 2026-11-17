# MiMacro Launcher

An external launcher for Xiaomi's system app MiMacro (`com.xiaomi.macro`).

## What is this

MiMacro is Xiaomi's game macro app, but it has no Activity and no launcher
entry — it can only be launched by the game booster. On unsupported models,
the game booster doesn't show the macro entry at all.

This small app works around that limitation: it sends the same explicit
Intent to MiMacro's `MainService` that the game booster does, bringing up
the macro UI.

## Features

- **Open game and start service**: opens the configured game first, then
  starts the MiMacro service
- **Start MiMacro service only**: launches the service without opening a game
- **Fill in Honor of Kings package name**: one-tap fill of
  `com.tencent.tmgp.sgame`
- **Landscape mode**: forces the launcher into landscape (for landscape
  games, turn on landscape mode first, then start); the choice is remembered
- **Floating window launcher** (toggleable): a draggable floating button
  stays on top of the game; tap it to start the macro, long-press to stop
- **Stop service**, **open overlay permission for MiMacro**,
  **re-check install status**

## Usage

1. Install MiMacro (`com.xiaomi.macro`, version 2.2.7.S works) on the phone
   first
2. Install this launcher (download the APK from Releases, or build it
   yourself)
3. Enter the game package name and tap "Open game and start service"
4. On first use, tap "Open overlay permission for MiMacro" to grant the
   permission

## Build

### Manual build (recommended, no dependency downloads needed)

Requires JDK 21 and Android SDK (platform-35, build-tools 35.0.0):

```sh
export JAVA_HOME=<your JDK 21 directory>
export ANDROID_HOME=<your Android SDK directory>
./build-manual.sh
```

Output: `build/mimacro-launcher-debug.apk` (debug-signed, installable
directly).

### Gradle build

```sh
./build.sh
```

Requires network access to download AGP 8.13.2 and dependencies.

## Version history

- v1.3.0: landscape mode toggle, toggleable floating window launcher
- v1.2.0: open the game first, then start the macro service
- v1.1.0: open game feature, Honor of Kings package shortcut
- v1.0.0: initial release, start / stop the MiMacro service

## Disclaimer

Unofficial tool, not affiliated with Xiaomi. MiMacro is Xiaomi's system app;
this project only launches it from outside and does not modify or repackage
MiMacro itself.
