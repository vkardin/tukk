# Tukk

Android gesture shortcuts by vkardin. Kotlin, Jetpack Compose and Material 3.
Minimum Android version: 8.0 (API 26).

## Build

Use JDK 21 and Android SDK 37. Set the SDK path in local.properties.

```
gradlew.bat :detection:test :app:assembleDebug :app:lintDebug
```

The debug APK is written to app/build/outputs/apk/debug with a versioned name.

## Features

- Separate Home, Settings and gesture editor screens.
- Shake, chop, calibrated double/triple back taps, face-down and volume-button sequences.
- Per-gesture action, sensitivity, cooldown, screen condition, proximity guard, charging pause and haptic feedback.
- Flashlight strength on supported devices, cancellable SOS, sound modes, media controls, app and camera shortcuts, recorder shortcuts and screenshot.
- Live motion test and five-tap calibration.
- Ukrainian, English and Russian. Dark, light, system and wallpaper themes.
- Quick Settings tile, foreground notification and restart after reboot or package update.

Volume sequences and screenshot require optional accessibility access. The service does not retrieve window content. Volume buttons retain their regular volume behavior.

App, recorder and camera shortcuts post a notification that opens the selected app when tapped. Sound modes require notification policy access. Screenshot requires Android 9 or later.

## Validation status

The current build is a development preview. Synthetic detector tests and Android Lint run locally. Real gesture accuracy, screen-off behavior and daily battery consumption still need measurements on each target phone before a stable release.

## Motion recordings

In debug builds, hold the ember on Home to reveal recording controls in Settings.
A recording stops after 15 seconds or when the activity leaves the foreground.
Accelerometer samples are stored in motion.csv and gyroscope samples in rotation.csv in private application storage. Both use timestampNs,x,y,z columns.

## Privacy

There is no network permission, account, analytics or remote data collection.
Gesture settings and developer recordings stay on the phone. Font licenses are included in app assets.

