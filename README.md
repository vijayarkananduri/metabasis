# Metabasis

Metabasis is an offline-first Android pedometer styled as a compact, tactile hardware dashboard. The v1 instrument uses a rectangular machined chassis, black-glass modules, a central `mb` monogram and five separated LED strips. The default logo is monochrome; users can switch it to their selected theme palette from Tune without changing the locked journey.

The repository root also contains the static marketing page (`index.html`), detailed user guide (`metabasis-guide.html`), update page (`metabasis-updates.html`) and shared Alive UI-inspired stylesheet.

## Step counting (no account or health-data connection)

Metabasis reads the phone's own Android sensors directly; it does **not** require Health Connect, Google Fit, a paired device, a login or an internet connection. The app prefers Android `TYPE_STEP_COUNTER` (hardware cumulative counter), then tries `TYPE_STEP_DETECTOR`, then a clearly marked approximate accelerometer fallback if needed. A foreground service keeps the sensor listener registered while the app is not open; a boot/package-update receiver attempts to resume it after restart when Android allows it. All recorded totals and settings stay in app-private local storage.

On Android 10 and newer, grant **Physical activity** when prompted. That is Android's runtime permission for step sensors; it is not a connection to another app. Android 13 and newer may also ask to show the ongoing counter notification. Metabasis uses the first hardware-counter reading on first setup as a baseline, then records subsequent changes. This avoids incorrectly counting earlier steps accumulated since boot as today's steps. Therefore, new installations start counting from the first sensor baseline; they cannot reconstruct steps from before activation without an external historical provider.

Device sensor behavior varies. The hardware counter is preferred but firmware can produce false or missed detections; no open-source project reviewed publishes a defensible universal accuracy percentage. The accelerometer fallback is less reliable and is explicitly labelled approximate. A persistent counter notification indicates that local tracking is active. Force-stopping the app, denied permissions, a missing sensor, or aggressive manufacturer battery controls can interrupt background collection; the service attempts restart where platform rules permit.

## App features

- **Base:** hardware-inspired step circle, 24-dot stage progress ring, cycle/pass display, fragment meter, live sensor state and a two-calendar-day minimum between evolutions.
- **Traces:** 12-week daily step heatmap with selected-day detail and evolution entries.
- **Tune:** five LED palettes, system/light/dark chassis, goal adjustment with a confirmation, and PRD-schema JSON import/export through Android's file picker.
- One fragment per 1,000 newly recorded steps; stage cost is `goal_steps / 5` fragments. The ledger retains the remainder and date history locally.
- No accounts, analytics, Health Connect dependency, `INTERNET` permission or advertising SDK.

## Build and install

Open this repository in Android Studio (JDK 17; Android SDK 35), then run the `app` configuration on Android 8.0+ (API 26). With Android command-line tools and Gradle 8.10.2 installed, build locally with `gradle assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main`, pull request, or manual run executes **Android debug APK** in GitHub Actions. Download the `metabasis-debug-apk` artifact from the workflow run. Pushing a `v*` tag also attaches the debug APK to the GitHub Release.

The v1 workflow intentionally produces an unsigned debug APK for testing. Use the `metabasis-debug-apk` artifact from the latest Actions run, or open the GitHub Release created from a `v*` tag.

## Design notes and open-source review

See [`docs/step-counter-design.md`](docs/step-counter-design.md) for a source-cited review of five open-source Android pedometers and why Metabasis favors a registered hardware-counter listener over Health Connect ingestion. Theme files are in `themes/`; contributor guidance is in [`CONTRIBUTING.md`](CONTRIBUTING.md).

The PRD also names CMPedometer for iOS. This repository currently builds the Android app; iOS requires an Apple/Xcode build and signing environment and is not produced by the Android workflow. The Android sensor accuracy and background behavior should be validated on real target phones before making product-level accuracy guarantees.
