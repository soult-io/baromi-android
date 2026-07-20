# Baromi

A personal Android app for daily wellbeing check-ins — the barometer of how you feel.

Notify N times a day (fixed times you set) to record **Mood**, **Energy**, and **Anxiety**
on a 1–5 scale, with an optional comment and quick tags. Data stays on the device; export to
CSV. The long-term payoff is a longitudinal record you can mine for patterns and share with a
clinician.

- Package: `io.soult.baromi`
- Min SDK 26 · Target SDK 34 · Kotlin 2.0 · Room
- Local-only in v1 (no network permission).

## Scale (frozen)

Every axis points the same way — **higher is the better state**:

| Axis | 1 | 5 |
|---|---|---|
| Mood | very low | very good |
| Energy | drained | energized |
| Anxiety | very anxious | very calm |

## Status

v1 is built in small modules. See `baromi-spec-v1.md` (agent workspace) for the full spec.

- **Module A — data model (Room):** in progress. Entities `Checkin`, `Tag`, `CheckinTag`;
  DAOs; frozen scale constant; construction guards on ranges and comment length.
- Modules C/D (scheduler + notifications), B (check-in screen), E (settings), F (CSV export)
  follow.

## Build & test

Built on the Android toolchain via the `android-*` pipeline. Unit tests run on the JVM under
Robolectric (Room in-memory) — no emulator required:

```
./gradlew test
```
