# Android Dashboard

A customizable Android dashboard with digital clock, notification display, and embedded web apps.

## Features

- **Digital Clock** - Large customizable clock with date display
  - 12/24 hour format
  - Multiple color options (white, blue, purple, pink, green, orange)
  - Animated background (solid, gradient, animated gradient, particles)
  - Music playback controls with Now Playing info

- **Notifications Panel** (swipe right)
  - View device notifications
  - Tap to open source app
  - Swipe to dismiss

- **App Launcher** (swipe left)
  - Apple Music
  - Apple Podcasts
  - Google Calendar
  - Google Tasks
  - Google Keep
  - YouTube Music

- **Embedded Web Apps**
  - Fullscreen WebView (no browser chrome)
  - Back button to return to clock
  - Apps stay running in background (music keeps playing)
  - Auto-hides Apple Music "Get on Google Play" banner

## Building with GitHub Actions

1. Push this project to a GitHub repository
2. Go to the **Actions** tab
3. Wait for the build to complete
4. Download `app-debug.apk` from the artifacts

## Installing

1. Download the APK to your Android device
2. Open the APK to install (enable "Install from unknown sources" if prompted)
3. Grant **Notification Access** when prompted (required for notifications feature)
4. Enjoy your dashboard!

## First-Time Setup

On first launch, the app will ask for notification access permission. This is optional but required to display notifications on the dashboard.

## Controls

- **Swipe left** → App Launcher
- **Swipe right** → Notifications
- **Tap clock** → Settings panel
- **Back button** (in web apps) → Return to clock

## Screen Wake Lock

The app keeps the screen awake while on the clock screen, perfect for using as a bedside clock or desk dashboard.
