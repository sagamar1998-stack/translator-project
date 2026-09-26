# Translator (Speech → Chinese) — Prototype

A minimal native Android app written in **Kotlin + Jetpack Compose** that:

1. Captures spoken English using `android.speech.SpeechRecognizer`.
2. Displays the recognized English text.
3. Hands the text to a `TranslationService` (currently a fake that returns a
   single hardcoded Chinese phrase: **`你好，我听到了你的话。`**).
4. Displays the Chinese text.
5. Speaks the Chinese text aloud via `android.speech.tts.TextToSpeech`.

It is intentionally simple — no networking, no Bluetooth, no background
services, no Whisper, no real translation API. The point is to get the
speech-recognition → Chinese-speech pipeline working end-to-end and to leave
a clean shape that real components can be slotted into later.

## Architecture (MVVM)

```
MainActivity            ← Compose entry point, hosts the single screen
  └─ TranslationScreen  ← UI; observes StateFlow<TranslationUiState>
        ↑
        │  state
        │
TranslationViewModel    ← Owns all logic, exposes immutable UI state
  ├─ SpeechRecognitionManager   (android.speech.SpeechRecognizer wrapper)
  ├─ TextToSpeechManager        (android.speech.tts.TextToSpeech wrapper)
  └─ TranslationService         (interface — currently FakeTranslationService)
```

* **`TranslationUiState`** is a single immutable data class so the UI always
  renders a coherent snapshot.
* **`StateFlow`** is used for state exposure — collected in Compose via
  `collectAsStateWithLifecycle()`.
* **Managers** wrap the framework callback APIs so the ViewModel stays free
  of Android framework boilerplate and is unit-testable.
* **`TranslationService`** is an interface so the fake can be swapped for a
  real implementation (ML Kit, on-device Whisper, cloud API, ...) without
  touching the ViewModel.

## File layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/translator/
│   ├── MainActivity.kt
│   ├── speech/
│   │   ├── SpeechRecognitionManager.kt
│   │   └── TextToSpeechManager.kt
│   ├── translation/
│   │   └── TranslationService.kt
│   ├── ui/
│   │   ├── TranslationScreen.kt
│   │   └── theme/{Theme,Color,Type}.kt
│   └── viewmodel/
│       ├── TranslationUiState.kt
│       └── TranslationViewModel.kt
└── res/
    ├── values/{strings,colors,themes}.xml
    ├── xml/{backup_rules,data_extraction_rules}.xml
    ├── drawable/ic_launcher_foreground.xml
    └── mipmap-anydpi-v26/ic_launcher{,_round}.xml
```

## Permissions

* `RECORD_AUDIO` — runtime permission, requested through Accompanist's
  `rememberPermissionState`. The ViewModel reflects three states:
  `Granted`, `Denied` (can re-ask) and `PermanentlyDenied` (must open
  system settings). The UI shows a banner with a "Open settings" button
  in the permanent-denial case.
* `INTERNET` — declared because some `SpeechRecognizer` implementations
  reach out to the network even though the wrapper itself doesn't.
* `<queries>` — required on Android 11+ so `SpeechRecognizer` and
  `TextToSpeech` can resolve the on-device implementations via implicit
  intents.

## Build & run

Requirements:

* Android Studio Koala (or any version bundling AGP 8.5+)
* JDK 17
* An Android device or emulator running API 26 (Android 8.0) or newer

Steps (Android Studio):

1. **File → Open** the project root.
2. Let Gradle sync (it will download AGP 8.5.2, Kotlin 1.9.24, Compose BOM
   2024.06, Accompanist 0.34.0).
3. Connect a device (preferably a physical one — emulators sometimes lack
   the Google speech recognizer and Chinese TTS voice).
4. Click **Run ▶**.

Steps (command line, if you have a Gradle wrapper):

```bash
./gradlew :app:installDebug
adb shell am start -n com.example.translator/.MainActivity
```

> This repo intentionally does not check in the Gradle wrapper binaries
> (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`). Generate
> them with `gradle wrapper --gradle-version 8.7` or just open the project
> in Android Studio, which will create them for you.

## Manual test (success criteria)

1. Launch the app. Grant the microphone permission when prompted.
2. Tap **Start Recording**.
3. Say: *"Hello how are you"*.
4. Expected:
   * "Recognized English" card shows what you said.
   * "Chinese Translation" card shows `你好，我听到了你的话。`.
   * The phone speaks the Chinese phrase aloud.
   * No crashes.

## Replacing the fake translator

Implement the `TranslationService` interface and inject it into the
ViewModel — there are no other touch-points to change:

```kotlin
class MyRealTranslationService : TranslationService {
    override suspend fun translateEnglishToChinese(sourceText: String): String {
        // call ML Kit / Whisper / cloud API here
    }
}
```

Then wire it into `TranslationViewModel.Factory` (currently it defaults to
`FakeTranslationService()`).
