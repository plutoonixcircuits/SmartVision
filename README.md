# SmartVision (Android, Offline)

SmartVision is an offline assistive navigation app built with CameraX, TensorFlow Lite, IMU sensing, object tracking, and Android accessibility APIs.

## Offline Guarantee

- No network API usage is implemented.
- No internet permission is declared in `AndroidManifest.xml`.
- All inference models run from local `app/src/main/assets`.

## Build without Android Studio

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> If this repository root already contains `settings.gradle`, run `./gradlew assembleDebug` directly.
