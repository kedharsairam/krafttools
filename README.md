# KraftTools

Phone instruments, done properly. Fourteen tools that tell the truth about their hardware — and say so when they can't.

Spirit level, compass, torch + strobe, vibration meter, tally + stopwatch, QR scanner, WiFi analyzer, light meter, metal + EMF, sound meter, color picker, angle ruler, speedometer, barometer.

No accounts. No ads. No tracking. No background work of any kind — sensors run only while their screen is open. Permissions are asked per tool, each with its reason, and every reading carries its honest limits (un calibrated mic, MEMS noise floor, GPS lag).

## Project layout

- `app/` — Android app (Kotlin + Compose, no Room, no network)
- Sensors via framework APIs only; CameraX for QR/color; Glance widget (manual refresh)

## Status

v0.2 — all 14 tools live and device-verified. Icon pending.
