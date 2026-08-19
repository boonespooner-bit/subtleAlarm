# Subtle Alarm

A quiet, beautiful Android alarm that wakes *you* — and lets your partner keep sleeping.

Instead of a jarring buzzer, Subtle Alarm wakes you with soft synthesized bell
tones that fade in over minutes, a warm sunrise-like glow on the screen, and an
optional gentle vibration. Dismissing it is instant and silent: press the power
button, either volume button, or the on-screen dismiss button.

## Features

- **Three bell tones, synthesized in-app** (no audio files):
  - *Dawn* — a low singing bowl, one slow strike every nine seconds
  - *Aurora* — two distant hand-bell notes, a gentle fifth apart
  - *Breeze* — a slow music-box arpeggio
- **Long fade-in** — 30 seconds to 5 minutes; the sound starts near-silent and
  swells so gradually the room barely notices
- **Sunrise glow** — the wake screen brightens from deep night to warm first
  light in step with the sound
- **Gentle vibration** (optional) — a soft, low-amplitude heartbeat pattern
- **Breathing light** (optional) — the camera flash LED climbs from off to just
  5% of full torch strength over five seconds, then fades back to off over five
  more, so it reads as a slow dim ember rather than a flash (true fading on
  Android 13+ devices with torch strength control; a brief soft pulse on devices
  whose torch is only on/off)
- **One-touch dismissal** — power button, volume up/down, the on-screen button,
  or a tap anywhere on the wake screen
- **Repeat days**, per-alarm volume, minimalist dusk-palette UI
- **Zero dependencies** — pure Android framework Java; the APK is ~50 KB
- Auto-silences after 15 minutes; reschedules itself after reboot and time changes

## Building

### With Android Studio / Gradle (normal path)

Open the project in Android Studio (or run `./gradlew assembleDebug`) with an
Android SDK for API 34 installed. There are no library dependencies.

### Without the Android SDK (offline path)

This repo includes `scripts/build-offline.sh`, which builds an installable APK
using only Ubuntu-packaged Android tools plus a framework jar for the compile
classpath:

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner android-framework-res zip
curl -LO https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
scripts/build-offline.sh ./android-all-14-robolectric-10818077.jar
# → build-offline/SubtleAlarm.apk
```

## Versioning

`version.properties` is the single source of truth for `versionCode` /
`versionName`; the Gradle build and the offline script both read it, and every
change bumps it. Each released build is committed as
`apk/SubtleAlarm-v<version>.apk`, all signed with the same committed debug
keystore (`scripts/debug.keystore`) so upgrades install cleanly with
`adb install -r`.

## Installing on your device

1. Copy the APK from `apk/` to your phone (or `adb install -r apk/SubtleAlarm-v*.apk`).
2. Allow installing from unknown sources when prompted (the APK is debug-signed).
3. On first use, allow notifications when asked — the alarm uses a full-screen
   notification to open the wake screen over the lock screen.
4. Battery optimization: the app uses `setAlarmClock`, which is exempt from Doze,
   so no whitelist changes should be needed.

## How dismissal works

- **On-screen button / tap anywhere / volume keys** — handled directly by the
  wake screen. The wake screen reserves space for the dismiss button before the
  clock and honors window insets, so the button stays visible and reachable on
  short windows, foldables (including under the large-screen taskbar), and
  split-screen.
- **Power button** — Android doesn't deliver power-key presses to apps; instead,
  the alarm listens for the screen turning off (which is what the power button
  does while the alarm is showing) and stops immediately.

## Design notes

Palette: deep night blues (`#070B18` → `#131C38`) with candlelight gold
(`#E9C48E`). Typeface: system Roboto thin/light. The glow view renders layered
radial gradients animated at a slow breathing rhythm on the home screen and as
a rising warm dome on the wake screen.
