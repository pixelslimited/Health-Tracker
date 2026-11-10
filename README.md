# Health Tracker

Android app by Pixels Limited (Kotlin, Jetpack Compose).

## Features
- Steps with daily and weekly goals, streaks, home-screen widget, live notification summary
- Heart rate from the phone camera (approximate) and from wearables through Health Connect
- Blood pressure, glucose and HbA1c logging with out-of-range alerts; automatic sync of device readings from Health Connect
- Sleep tracker (bed/wake timer, manual entry, wearable import), water, weight/BMI, meals and carbs
- Diabetes risk check (FINDRISC), medication and measurement reminders, emergency contact
- PDF and CSV export, multiple profiles, app lock, encrypted on-device storage
- English, Hausa and Arabic
- In-app updater (GitHub Releases)

The app does not diagnose or treat any condition. A phone cannot measure blood pressure or glucose by itself; those readings come from a cuff, glucometer or synced device.

## Releases
Every push to `main` builds the app. When these repository secrets exist, the build is signed and published as a release that installed apps update to:
`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
