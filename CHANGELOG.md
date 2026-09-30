# Changelog

All notable changes to KraftTools are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses semantic versioning.

## [1.0.0] - 2026-09-30

First public release. Fourteen instruments, all device-verified, none of them
claiming more than the hardware can deliver.

### Added

- **About screen**, reachable from the top bar of the tool grid: what the app
  will not do, exactly what it stores, every permission with its reason, and
  the honest limits of each sensor. An action in the bar rather than a
  fifteenth tile, because an about tile among fourteen instruments reads like
  an instrument.
- **Four instruments got a voice.** Vibration, light, EMF and barometer all
  draw a trace, and the shared trace component had no accessible name, so a
  screen reader announced nothing at all on those four screens. Name and
  spoken value are now required parameters of the component, so a fifth
  caller cannot ship unnamed.
- **Instrumented test suite** (2 tests) covering the class of defect the JVM
  tests cannot see: bounds, semantics and layout. A unit test has no pixels,
  so it is structurally blind to all of it.
- **Tally regression test** asserting the marks block is neither collapsed
  into a sliver nor overflows its panel, at 46 marks.
- MIT licence.

### Fixed

- **Vibration meter and barometer were dead.** Neither received a sample for
  weeks, behind a fully green suite. A composable captured a coroutine-local
  copy of a reading instead of observing it, so a `var` changed and nothing
  recomposed.
- **Every linear trace drew its scale on the bottom edge.** Gridlines were
  built as fractions (0.25, 0.5, 0.75, 1) and then fed to the axis as
  values, so all four clamped to the floor and stacked on one line.
- **Axis labels never appeared anywhere in the app.** A sentinel used to
  suppress collisions was larger than any coordinate, so the guard it fed
  always fired.
- **Six tools flashed "No accelerometer here" on every cold open.** Whether a
  sample had arrived yet was standing in for whether the sensor existed.
- **The back button was 40dp on all fourteen tools** — the control a user
  reaches for when a reading is confusing and they want out. And
  `Modifier.touchTarget()` constrained only height, so fixing it produced a
  40 x 48 target. It constrains both axes now.
- **"Save swatch" was 40dp tall** — the one button on the colour sampler that
  missed `touchTarget()`. No cold dump could have shown it, because the
  button does not exist until the camera has delivered a sample.
- **Tally layout**, rewritten three times over: a ribbon of shrinking bars,
  then a block that ran off the top of its canvas, then a collapsed sliver.
  It is now an exact search over column counts.
- **Torch ripple was clipped**, because a clip applied before a clickable
  does not clip the clickable's own ripple.
- **Copy cut across the app.** The compass alone was 77 words on one screen.
  Every screen is now under 43, and the longest line left is one that earns
  its place.
- **Splash and launcher icon.** The app was still on the stock light theme;
  the icon art is inset so a circular launcher mask cannot clip it.

### Changed

- Dark theme only, as built.
- Icons audited for appropriateness rather than presence: four were wrong, and
  the spirit level had no matching glyph in the icon set, so one was drawn.
- Permissions are asked per tool with a one-line reason, never at launch.
- Tool screens stop the instrumented test clock; the grid does not. A stopped
  clock strands a frame, and Espresso counts a pending frame as a busy app.

### Known limitations

These are properties of phone hardware, and the app states them on screen
rather than hiding them:

- Phone microphones are uncalibrated. The sound meter shows a level, not a
  decibel rating.
- The angle ruler is a MEMS sensor with about 0.5 degrees of noise. Fine for
  a miter, not for inspection.
- A compass reads magnetic north until corrected, and the correction needs a
  location fix that takes a moment to arrive.
- GPS speed lags, and indoors it stops entirely.

[1.0.0]: https://github.com/kedharsairam/krafttools/releases/tag/v1.0.0
