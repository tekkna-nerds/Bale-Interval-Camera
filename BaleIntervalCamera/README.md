## 🧐 What On Earth Is This?

Ever sat there thinking: *"Man, I wish an Android phone would just sit in a corner, snap photos without my permission every X seconds, and beam them straight into my Bale channel while I do absolutely nothing"*?

No? Well, **we did.** 

So we brewed 3 pots of coffee, locked ourselves in a room, and engineered **Bale Interval Camera**. It runs silently in the background, minds its own business, takes snapshots using CameraX, and fires them via the Bale Bot API.
# 📸 Bale Interval Camera

**An Android interval camera that sends photos to Bale.**  
Built with Kotlin, CameraX, and probably more caffeine than strictly necessary.

Because sometimes, "I'll debug it tomorrow" is just a bedtime story.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Camera](https://img.shields.io/badge/Camera-CameraX-blue)](https://developer.android.com/media/camera/camerax)
[![Messaging](https://img.shields.io/badge/Messaging-Bale-00AEEF)](https://bale.ai/)

## ☕ What is this?

Bale Interval Camera is an Android application that captures photos from the device's back camera at configurable intervals and sends them to a paired private chat through the Bale Bot API.

The goal is simple: a straightforward interface, configurable capture intervals, and a visible, user-controlled camera session.

No unnecessary complexity. Just a camera, a bot, and the occasional argument with Gradle.

## ✨ Features

- 📷 **Interval capture** — Capture photos every 3, 5, 10, 15, or 30 seconds, or once per minute.
- 💬 **Bale integration** — Send captured photos to a paired private chat.
- 🔗 **Bot pairing** — Discover and save the destination chat ID through a pairing flow.
- 🌍 **Bilingual interface** — Persian and English, with RTL/LTR layouts.
- 🎨 **Theme settings** — Light and dark theme support.
- ⚙️ **Configurable settings** — Change capture intervals and language.
- 🔔 **Foreground service** — Keep the capture session visible through Android's ongoing notification.
- 🛑 **User-controlled sessions** — Start and stop capture from the app.
- 🧹 **Temporary photo files** — Captured files are deleted after their upload attempt.

## 🧰 Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Application logic |
| Android SDK | Android platform integration |
| CameraX | Camera capture |
| Android Lifecycle | Foreground camera service lifecycle |
| Bale Bot API | Photo delivery and bot pairing |
| Gradle Kotlin DSL | Build configuration |

## 🗂️ Project Structure

```text
Bale-Interval-Camera/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/baleintervalcamera/
│   │   │   ├── MainActivity.kt
│   │   │   ├── CameraCaptureService.kt
│   │   │   ├── AppSettings.kt
│   │   │   ├── SplashActivity.kt
│   │   │   └── UiTheme.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .gitignore
```

### A quick tour

- `MainActivity.kt` — Main interface, settings, bot pairing, and user interactions.
- `CameraCaptureService.kt` — Camera lifecycle, interval scheduling, notifications, and photo uploads.
- `AppSettings.kt` — Preferences, language selection, and capture interval settings.
- `SplashActivity.kt` — Startup screen.
- `UiTheme.kt` — Shared UI styling and theme colors.

## 🚀 Getting Started

### Requirements

- Android Studio
- JDK 17
- Android SDK Platform 35
- An Android device with a compatible camera
- A Bale bot and its API token

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/Bale-Interval-Camera.git
cd Bale-Interval-Camera
```

Replace `YOUR_USERNAME` with your GitHub username.

### 2. Configure the bot token

Create or edit `local.properties` in the project root and add:

```properties
bale.botToken=YOUR_BALE_BOT_TOKEN
```

Preserve any existing `sdk.dir` setting in that file.

**Security note:** The current build configuration embeds the bot token in the APK. Do not distribute an APK containing a real private bot token. For a public production release, move bot communication to a backend that keeps the token server-side.

### 3. Build the app

Open the project in Android Studio, allow Gradle to sync, and build the debug APK.

Alternatively, on Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The debug APK should be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 🔐 Privacy & Safety

This application is designed for visible, user-initiated camera sessions.

- Camera access requires Android's permission.
- The user starts the capture session explicitly.
- An ongoing notification indicates that capture is active and provides a stop action.
- Captured images are sent to the configured Bale chat.
- Temporary image files are deleted after upload attempts.

**Use responsibly:** Obtain the consent of anyone being photographed and comply with applicable privacy laws. Never use the app for covert recording or unauthorized surveillance.

### Important technical limitations

- Upload delays can increase the effective interval between captures.
- The direct pairing flow uses Bale's `getUpdates` endpoint. Its update queue is shared at the bot level, so multiple app installations can interfere with one another.
- A public multi-user deployment should use a backend to manage pairing and photo delivery.
- Embedding a bot token in an APK cannot be made completely secure through code obfuscation alone.

## 🛠️ Troubleshooting

**Gradle refuses to cooperate?**

Check the JDK version, Android SDK installation, and internet connectivity. Sometimes the bug is yours; sometimes Gradle just needs a little space.

**Photos are not being delivered?**

Verify the bot token, complete the pairing process, check the network connection, and make sure the bot can receive messages from the destination chat.

**The capture interval seems longer than expected?**

Uploads are processed sequentially, and the app pauses new captures while an upload is in progress.

## 🗺️ Roadmap

Ideas for future improvements:

- [ ] Move bot credentials to a secure backend.
- [ ] Add automated tests for pairing and capture scheduling.
- [ ] Improve upload error reporting and retry handling.
- [ ] Add a dedicated diagnostics screen.
- [ ] Add a backend-supported multi-user pairing flow.

## 🤝 Contributing

Issues, suggestions, and pull requests are welcome.

If you find a bug, please include the steps to reproduce it, relevant logs, and your Android version.

Bonus points if your bug report contains less caffeine than the debugging session it saves.

## 📄 License

No license has been selected yet. Until a license is added, do not assume that the source code is available for unrestricted reuse, modification, or redistribution.

## ☕ Final Note

Built with Kotlin, powered by caffeine, and occasionally held together by `Log.d()`.

If it works, it's engineering.  
If it doesn't, it's a learning opportunity.

**Happy coding. And please, commit before the next coffee.**
