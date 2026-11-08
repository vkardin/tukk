# Contributing to Tukk

Thanks for helping make Tukk better! Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and ideas

Open an [issue](https://github.com/vkardin/tukk/issues/new/choose) and pick a template.
For gesture problems, include the phone model, the Android version and whether the screen was on.

## Development setup

1. Install JDK 21 and Android Studio with the Android SDK (API 37).
2. Open the project folder in Android Studio, or build from the command line:
   `./gradlew :app:assembleDebug`.

## Before you open a pull request

```sh
./gradlew test :app:lintDebug :app:assembleDebug
```

CI runs the same checks.

### Detector tests

Detectors in `detection` are plain Kotlin and take `(timestampNs, x, y, z)` samples. When you
change one, add a recording of the gesture (and of ordinary motion that must not trigger it) to
`detection/src/test/resources/recordings` and a test that replays it. Debug builds can record
sensor data, see the [README](README.md#sensor-recordings).

## Guidelines

- Battery first: register only the sensors that are needed, and nothing at all when no gesture is on.
- Every text goes into string resources in Ukrainian, English and Russian.
- Each permission is explained to the user before it is requested.
- Think twice before adding a dependency.
- One topic per pull request, with a short description of what changed and why, and the phones you tested on.

By contributing, you agree that your work is released under the [MIT License](LICENSE).
