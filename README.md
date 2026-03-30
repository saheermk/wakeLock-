<div align="center">
  <h1>No Sleep ☀️</h1>
  <p>A beautifully simple Android app to force your screen to stay awake.</p>
</div>

<br />

## 📱 What is No Sleep?

**No Sleep** is a minimalist, elegant Android utility designed to keep your device's screen on globally, without relying on battery-draining CPU WakeLocks. Whether you're referencing a recipe, reading long documentation, or just need your screen to stay exactly as it is, No Sleep has you covered.

Built with **Jetpack Compose** and a focus on premium UI/UX, the app features an easy-to-access **Quick Settings Tile**, allowing you to toggle the screen state directly from your notification panel—just like Wi-Fi or Bluetooth.

## ✨ Features

- **Quick Settings Tile**: Toggle No Sleep instantly from your notification shade without opening the app.
- **Battery Friendly**: Uses a lightweight `WindowManager` overlay (`FLAG_KEEP_SCREEN_ON`) instead of heavy CPU `PowerManager.WakeLock`s to conserve battery and CPU.
- **Modern UI**: Designed meticulously with Jetpack Compose featuring smooth dynamic animations, gradients, and professional vector icons.
- **State Syncing**: The Quick Settings tile and the app's main dashboard stay perfectly in sync in real time.
- **CI/CD Built-in**: Configured with GitHub Actions to automatically build and release APKs upon version tagging.

## 🛠 Installation

You can download the latest APK directly from the [Releases](https://github.com/saheermk/no-sleep/releases) page.

### 💡 Quick Tip

Once installed, pull down your Android Quick Settings (Notification Panel), tap the **Edit** icon (pencil), and drag the **No Sleep** tile into your active tiles for instant access!

## ⚙️ Permissions

To ensure the OS does not put the device to sleep, No Sleep requires:

- **Display over other apps (`SYSTEM_ALERT_WINDOW`)**: Required to draw an invisible 0x0 pixel overlay that actively forces Android's display manager to stay awake natively.
- **Foreground Service**: Ensures the screen lock doesn't get killed by the Android system in the background.
- **Notifications**: Shows a persistent notification while the wake lock is active, so you don't accidentally leave your screen on forever.

## 💻 Tech Stack

- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34 (Android 14)

## 🏗 Building from Source

If you'd like to build the project yourself, ensure you have the Android SDK and JDK 17 installed.

```bash
# Clone the repository
git clone https://github.com/saheermk/no-sleep.git
cd no-sleep

# Build the debug APK
./gradlew assembleDebug

# The APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

## 👨‍💻 Developer

**Developed by Saheermk**  
_Creative Developer blending design and engineering to create immersive apps._

- 🌐 **[Website & Portfolio](https://saheermk.pages.dev)**
- 💼 **[LinkedIn](https://in.linkedin.com/in/saheermk)**
- 💻 **[GitHub](https://github.com/saheermk/)**

<br />

---

## 🚀 How to Release a New Version

To build and publish a new version of No Sleep to the GitHub Releases page:

1. **Tag the version** (e.g., `v1.1.0`):

   ```bash
   git tag v1.1.0
   ```

2. **Push the tag to GitHub**:
   ```bash
   git push origin v1.1.0
   ```

The **GitHub CI/CD** workflow will automatically build the APKs and create a new Release for you!
