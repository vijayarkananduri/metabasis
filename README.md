# Metabasis

An open-source, offline-first step counter presented as a tactile hardware dashboard. Walking earns fragments; glass modules glow through five evolving stages. No streaks, accounts, analytics, or network permission.

## Features

- Base dashboard: 24-dot stage ring, cycle/pass display, fragment progress, metallic chassis and inset black-glass modules.
- Step ingestion from Android Health Connect with monotonic step totals and a carried sub-1,000-step remainder.
- 1 fragment per 1,000 steps, configurable daily goal, stage progression and a two-calendar-day minimum between evolutions.
- Traces: 12-week step grid and per-day Footprint detail.
- Tune: five bundled theme palettes, dark/light chassis, goal control, and local JSON snapshot export/restore.
- MIT licensed. No Internet permission or telemetry.

## Build and install

Open this repository in Android Studio (Giraffe or newer; JDK 17; Android SDK 35), then run the `app` configuration on Android 8.0+ (API 26). To build locally with Android command-line tools installed, run `gradle assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main`, pull request, or manual run executes **Android debug APK** in GitHub Actions. Download the `metabasis-debug-apk` artifact from that workflow run. Pushing a `v*` tag also attaches the APK to the GitHub Release.

On first launch, install or update Health Connect if prompted, then tap **Connect** and grant read access to Steps. Step data is read on-device; all app state remains local. Without Health Connect, the dashboard and settings remain usable, but automatic step ingestion is unavailable.

## Design / progression

See `themes/` for the five contributor-editable palettes. The stage cost is `goal_steps / 5` fragments. Sequence order rotates deterministically by cycle; each pass reverses before the next cycle. Progress is retained when the two-day floor blocks an evolution. See `CONTRIBUTING.md` for theme changes.

## Platform note

This repository currently builds the Android app/APK. The PRD names CMPedometer for iOS as well; native iOS support requires an Apple/Xcode signing build environment and is not produced by GitHub's Android APK workflow.
