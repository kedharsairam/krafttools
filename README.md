# KraftTools

Fourteen phone instruments, and what each one can honestly measure.

Spirit level, compass, torch + strobe, vibration meter, tally + stopwatch, QR scanner, WiFi analyzer, light meter, metal + EMF, sound meter, colour picker, angle ruler, speedometer, barometer.

No accounts. No ads. No analytics. **No internet permission** — the manifest has no `INTERNET` entry, so the app has no way to send anything anywhere even if it wanted to. A sensor runs only while its screen is open. Every reading carries the limits of the hardware behind it, printed on the screen rather than buried in a help page.

<p align="center">
  <a href="https://github.com/kedharsairam/krafttools/releases/latest"><img src="https://img.shields.io/github/v/release/kedharsairam/krafttools?style=for-the-badge&label=Download" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License">
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue?style=for-the-badge" alt="Android 8.0 and newer">
</p>

---

## The idea

Most instrument apps show a number and let you believe it. This one tells you what the number is worth.

- A phone microphone is uncalibrated, so the sound meter shows a level and **says so**, rather than inventing a decibel rating.
- The angle ruler has about half a degree of noise, so it calls itself phone-grade and stops there.
- A compass reads magnetic north. Correcting it to true north needs a location fix, so the app **tells you that** instead of quietly showing a wrong heading.
- The barometer is good to about ten centimetres, and says the number depends on the sea-level reference you set.

An app that admits its limits is more useful than one that hides them, and it is also the only version of this that is worth maintaining.

## What it will not do

- **No readings recorded.** A trace lives in memory and is gone when you leave.
- **No background work.** Every sensor is registered when its screen opens and unregistered when it closes.
- **No accounts, no ads, no analytics, no crash reporting.**
- **Nothing written to disk** except three values, all from the barometer: its last reading, its trend, and when you took it.

## Permissions

Each is asked for by the tool that needs it, with a one-line reason, and never at launch.

| Permission | Asked by | Why |
| --- | --- | --- |
| Camera | torch, QR, light, colour | The hardware lives behind the camera API |
| Microphone | sound meter | The only way to read a sound level |
| Location | compass, WiFi, speed | True north, network names, ground speed |
| Vibration | haptics, tally | A tick you can feel |

---

<details>
<summary><strong>Tests — 329 unit and 2 instrumented</strong></summary>

```sh
./gradlew :app:testDebugUnitTest          # 329 JVM tests
./gradlew :app:connectedDebugAndroidTest  # 2 instrumented, needs a device
```

The unit suite includes source lints, so a claim in a string that stops matching the code fails the build rather than the reader.

The instrumented pair exists because a JVM test has no pixels, no bounds and no semantics tree, and is therefore blind to layout. It has already earned its place: it found a 40dp back button on all fourteen screens, a `touchTarget()` modifier that constrained one axis, four instruments that announced nothing to a screen reader, and a 40dp button that does not exist until the camera has delivered a sample.

The instrumented tests read a live-sensor app, which is never idle. The clock is stopped for that reason, and a stopped clock strands a frame — so the grid, the one screen with no sensor and no animation, is the one place the clock is allowed to run. That constraint is written down in the test file, because it looks arbitrary otherwise.

</details>

<details>
<summary><strong>Build from source</strong></summary>

```sh
git clone git@github.com:kedharsairam/krafttools.git
cd krafttools
./gradlew :app:assembleDebug
```

JDK 17, Android SDK with `compileSdk 37` and `minSdk 26`. Kotlin, Jetpack Compose, no network layer, no database, no dependency injection framework. Sensors come from the framework APIs; the QR and colour tools use CameraX.

The release APK is signed with a debug key on purpose. There is no release keystore for this project, so if you install it, you are testing a build the author also built — not one that came from a pipeline you cannot inspect.

</details>

<details>
<summary><strong>Project layout</strong></summary>

```
app/src/main/java/com/krafttools/app/
  ui/          one file per tool, plus the shared frame, sensors and maths
  data/        the barometer's three stored values
app/src/test/          329 JVM tests, including source lints
app/src/androidTest/   the instrumented sweep and the tally regression
```

</details>

## Support

If you enjoy KraftTools, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>

## Licence

MIT. See [LICENSE](LICENSE).
