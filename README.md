# SmartVision (Android)

Offline assistive navigation prototype using CameraX + TensorFlow Lite float16 models.

## Build without Android Studio

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> If your repo root is already the Android project root, run `./gradlew assembleDebug` directly.
