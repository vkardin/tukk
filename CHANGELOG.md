# Changelog

## 0.4.0

- Flashlight and SOS now use the default torch strength instead of the lowest level on Android 13 and later.
- SOS keeps blinking with the screen off.
- The chop gesture works with the phone held upright, and a single swing no longer triggers it.
- Sensors are turned off when no gesture can fire in the current screen state, and screen-off events are batched.
- Changing a gesture's action no longer turns the gesture off.
- Silent and vibrate modes switch back to normal sound on a repeated gesture.
- Apps, recorder and camera open directly when accessibility access is enabled.
- Added a pause during calls and three vibration strengths for each gesture.
- Background work settings show the battery optimization state and steps for Xiaomi, Samsung, Huawei and OnePlus.
- The notification has Pause and Settings buttons; Stop SOS appears only while SOS is on.
- System bar icons follow the app theme, and the ember shows its paused, active and flash states.
- Quick Settings tile shows whether gestures are active.
- Split into modules with Hilt and DataStore; existing settings are migrated.
- Release builds are minified.

## 0.3.0

- Added dedicated Home and Settings pages with gesture cards and individual editors.
- Added chop, face-down, calibrated double/triple back taps and volume-button sequences.
- Added per-gesture action assignment and conditions.
- Added SOS cancellation, torch strength, sound modes, media controls, app and recorder shortcuts, camera notifications and screenshot.
- Added optional accessibility disclosure, gesture testing and five-tap calibration.
- Added adaptive and monochrome launcher icons.
- Added gyroscope recording alongside accelerometer data.
- Preserved existing shake settings during migration.
- Added detector regression tests. Physical accuracy and battery measurements remain pending.

## 0.2.0

- Added adjustable shake sensitivity and cooldown.
- Added screen, proximity and charging conditions with optional haptic feedback.
- Added dark, light, system and wallpaper themes, with Sora and Inter fonts.
- Added a Quick Settings tile and background settings guidance.
- Fixed access to the debug motion recorder.

## 0.1.0

- Added shake-to-toggle flashlight with a foreground gesture service.
- Added a localized home screen, pause control, and debug motion recording.

