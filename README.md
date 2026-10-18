# Training Milo 🐶

**Training Milo** is an Android application designed for dog owners training dogs with anxious attachment and separation anxiety to become comfortable being alone in the house.

The app combines **randomized departure interval alerts** (telling the owner when to step outside) with an **adaptive "Time Alone" countdown timer** that automatically scales target durations based on the dog's success or distress.

---

## ✨ Key Features

### 1. ⏱️ Randomized Departure Intervals
- **Active Time Window**: Set your daily training window (e.g., `08:00` to `16:30`).
- **Randomized Intervals**: Define a minimum and maximum interval range (e.g., 40–80 minutes). The app schedules alarms at randomized intervals within the window to prevent your dog from anticipating departure patterns.
- **Repeat Days**: Select specific days of the week or leave unselected for manual single-day training.
- **Reliable Alarms**: Scheduled using Android's `AlarmManager.setExactAndAllowWhileIdle` and automatically rescheduled upon device reboot (`BOOT_COMPLETED`).
- **Next Departure Countdown**: Dedicated prominent section displaying the upcoming departure time and real-time countdown (e.g., `14:35` / `in 42 minutes`) under the status card while the alarm is active.
- **Clean Active Dashboard**: Automatically hides blocked configuration settings while active to reduce clutter, with an expandable **View Settings** toggle to inspect them read-only.
- **Custom Alerts**: Configurable alarm ringtone (system ringtone picker) and vibration patterns.

### 2. ⏳ Adaptive "Time Alone" Training Timer
- **Trigger Anywhere**: Start an alone-time session on demand with the **Alone T** button, or accept an automated interval departure alarm to seamlessly transition into countdown mode.
- **Background Persistence**: The timer runs in a foreground service backed by a `PARTIAL_WAKE_LOCK` to ensure it continues ticking accurately even when the phone is locked.
- **Almost Done Warning Notification**: Configurable warning notification ("Almost Done") with a short vibration when approaching the end of alone time (default 1 minute; set to 00:00 to disable).
- **Smart Auto-Adjustment**:
  - **Success!**: When the timer completes without distress, a 10-second Congratulations screen appears (defaulting to Success). The app automatically increases the target duration by your configured adjustment percentage (e.g., +10%).
  - **Failed**: If your dog whines, barks, or shows distress before the timer ends, tapping **Failed** stops the session early, records the exact elapsed time, and decreases the target duration by the configured percentage (e.g., -10%).
  - Target duration automatically clamps between 1 second and 4 hours (`14,400s`).
- **Completion Alerts**: Vibrating alert notifies you when the alone-time session ends.

### 3. 📊 History & Progress Tracking
- **Session Logging**: Every alarm event and alone-time session is recorded with timestamp, outcome (`SUCCESS`, `FAILED`, `DISMISSED`), target duration, and actual elapsed time.
- **Clear & Minimalist**: Built-in history viewer with itemized cards, entry count, and a clear-all dialog.
- **App Version Display**: Embedded directly in the History screen header for quick build verification.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: Modern [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/) and dynamic system theming.
- **Architecture**: MVVM with reactive unidirectional data flow using Kotlin `StateFlow` and `Flow`.
- **Database & Storage**:
  - [Room Database 2.8.4](https://developer.android.com/training/data-storage/room) for persistent training history.
  - [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) for application settings and configuration.
- **Background Execution**:
  - Android `AlarmManager` with `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`.
  - Android Foreground Services (`specialUse` subtype for Android 14/15+).
  - `BroadcastReceiver` for boot persistence (`BootReceiver`).
- **Language & Toolchain**: Kotlin `2.1.0`, Java 21 toolchain, Gradle `9.4.0`, Android Gradle Plugin `9.1.0`.

---

## 📁 Project Structure

```
app/src/main/java/com/example/intervalalarm/
├── IntervalAlarmApp.kt         # Application class & notification channels
├── MainActivity.kt             # Navigation & single-activity entry point
├── alarm/
│   └── AlarmScheduler.kt       # Randomized trigger calculation & AlarmManager
├── data/
│   ├── AlarmHistoryDb.kt       # Room entities, DAO, and database
│   └── AlarmPreferences.kt     # DataStore configuration & percentage math
├── model/
│   └── AlarmConfig.kt          # Domain data model for alarm & timer settings
├── receiver/
│   ├── AlarmReceiver.kt        # Broadcast receiver triggering AlarmFiringService
│   └── BootReceiver.kt         # Boot completed receiver to restore schedules
├── service/
│   ├── AlarmFiringService.kt   # Foreground service handling sound & vibration
│   └── TimerService.kt         # Foreground service running alone-time countdown
└── ui/
    ├── AlarmViewModel.kt       # Shared ViewModel managing state & database
    ├── screens/
    │   ├── HomeScreen.kt       # Main control dashboard & schedule settings
    │   ├── TimerScreen.kt      # Active countdown & Congratulations decision screen
    │   └── HistoryScreen.kt    # Training history list with version display
    └── theme/
        └── Theme.kt            # Material 3 dynamic color theme
```

---

## 🚀 Building & Running

### Prerequisites
- **Android Studio** (Ladybug / Meerkat or later recommended)
- **JDK 21**
- **Android Device or Emulator** running **Android 15+** (`minSdk = 35`)

### Gradle Commands

- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
  The generated APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

- **Compile Kotlin**:
  ```bash
  ./gradlew compileDebugKotlin
  ```

---

## 🏷️ Versioning & Release Workflow

This project adheres to [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`).

Whenever the app version is bumped:
1. Update `versionName` (and increment `versionCode`) in `app/build.gradle.kts`.
2. Commit the changes and create an annotated Git tag:
   ```bash
   git tag -a v<version> -m "Release v<version>"
   ```
3. Push both the commit and tag to GitHub:
   ```bash
   git push origin main
   git push origin v<version>
   ```

---

## 📄 License

Private repository for personal training use.
