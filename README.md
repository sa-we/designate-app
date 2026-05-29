# DESIGNATE App

**DESIGNATE** stands for *"Disruption im Internet: Mehr Souveränität gegenüber Deceptive Technologies"*
(Disruption on the Internet: More Sovereignty over Deceptive Technologies).

This Android application was developed as part of an interdisciplinary research project
at [TU Dresden](https://tu-dresden.de/mn/psychologie/iaosp/applied-cognition),
funded by the TUDisc programme. The project investigates the effectiveness of dark patterns
in digital interfaces and explores technical, psychological, and legal countermeasures.

---

## About the Study

Participants use DESIGNATE as a browser replacement for Facebook, Instagram, X (Twitter),
and YouTube over a **14-day period**. The app records touchscreen interactions and collects
responses to short questionnaires (EMA — Ecological Momentary Assessment) to measure
stress levels and emotional responses in everyday situations.

**Privacy:** No personal data is collected. Login credentials, viewed content, and
messages are never recorded or transmitted.

**Contact:** Sascha Weber - [Applied Cognition Lab, TU Dresden](https://tu-dresden.de/mn/psychologie/iaosp/applied-cognition)

---

## Requirements

- Android 8.0 (Oreo, API 26) or newer
- Compatible with custom Android systems (LineageOS, GrapheneOS, EMUI)
- Internet connection for data upload

---

## Installation

1. Download the latest APK from the [Releases](../../releases) page.
2. On your Android device, enable installation from unknown sources.
3. Install the APK and launch the app.
4. Follow the in-app setup wizard, which guides you through:
   - Granting **Usage Access** (to detect active social media apps)
   - Granting **Overlay Permission** (to display the touch-logging layer)
   - Enabling **Notifications** (for questionnaire reminders)
   - Disabling **Battery Optimization** for this app (to ensure it keeps running)
   - Entering your personal **User ID**
   - Logging in to your social media accounts

---

## How It Works

```
┌─────────────────────────────────────────────────┐
│  SocialMediaMonitoringService (Foreground)       │
│  Detects active social media app every 5 sec     │
└───────────────┬─────────────────────────────────┘
                │ triggers
     ┌──────────▼──────────┐     ┌──────────────────┐
     │  WebViewActivity     │────▶│  TouchLogger      │
     │  (browser overlay)   │     │  (records taps)   │
     └──────────┬──────────┘     └──────────────────┘
                │ every ~60 min (max 5×/day)
     ┌──────────▼──────────┐     ┌──────────────────┐
     │ QuestionnaireActivity│     │  NextCloudUploader│
     │ (EMA questionnaire)  │────▶│  (upload CSV)     │
     └─────────────────────┘     └──────────────────┘
```

- The monitoring service runs continuously in the background and detects when a social app is opened.
- When a supported social media app is opened, the app overlays a transparent WebView that logs touch coordinates and timing.
- Up to **5 short questionnaires** per day are triggered after social media sessions.
- Collected data is automatically uploaded to a secure Nextcloud server.

---

## Permissions

| Permission | Purpose |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is currently in use |
| `SYSTEM_ALERT_WINDOW` | Display overlay above other apps |
| `POST_NOTIFICATIONS` | Send questionnaire reminders |
| `FOREGROUND_SERVICE` | Keep monitoring service alive |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after device reboot |
| `INTERNET` | Upload collected data |

---

## Project Structure

```
app/src/main/java/com/ingpsy/designate/
├── MainActivity.kt                 # Setup wizard and main screen
├── SocialMediaMonitoringService.kt # Background monitoring service
├── WebViewActivity.kt              # Overlay browser for social media
├── FullscreenWebViewActivity.kt    # Fullscreen WebView variant
├── DesignateWebViewClient.kt       # Custom WebView client
├── TouchLogger.kt                  # Records touch events to CSV
├── QuestionnaireActivity.kt        # EMA questionnaire screen
├── QuestionnaireLogger.kt          # Logs questionnaire responses
├── NextCloudUploader.kt            # Uploads data via Nextcloud API
├── BootReceiver.kt                 # Restarts service after reboot
├── AppLogger.kt                    # General app event logging
├── Config.kt                       # Central configuration constants
└── HelperFunctions.kt              # Utility functions
```

---

## Build

Before building, add the Nextcloud credentials to your `local.properties` file
(this file is excluded from version control):

```properties
nextcloud.url=YOUR_DATASHARE
nextcloud.username=YOUR_USERNAME
nextcloud.password=YOUR_PASSWORD
```

Then run the build:

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 16)
- **Language:** Kotlin
- **Build system:** Gradle (Kotlin DSL)

---

## Research Project

DESIGNATE is an interdisciplinary project involving the departments of
**Computer Science**, **Psychology**, and **Law** at TU Dresden.

The four research questions are:
1. How effective are dark patterns in manipulating user behaviour?
2. Can targeted interventions raise user awareness of subtle manipulation?
3. What software-based countermeasures are feasible?
4. Is existing regulation sufficient, or are additional legal frameworks needed?

Funded by the **TUDisc programme** of TU Dresden. Project start: April 2022.

---

## License

This software was developed for academic research purposes at TU Dresden.
