# Baromi

A personal Android app for daily wellbeing check-ins — the barometer of how you feel.

Notify N times a day (fixed times you set) to record **Mood**, **Energy**, and **Anxiety**
on a 1–5 scale, with an optional comment and quick tags. Data stays on the device; export to
CSV. The long-term payoff is a longitudinal record you can mine for patterns and share with a
clinician.

- Package: `io.soult.baromi`
- Min SDK 26 · Target SDK 36 · Kotlin · Room
- Local-only: no network permission. Your check-ins never leave the device unless you export them.

## Scale (frozen)

Every axis points the same way — **higher is the better state**:

| Axis | 1 | 5 |
|---|---|---|
| Mood | very low | very good |
| Energy | drained | energized |
| Anxiety | very anxious | very calm |

## Build & test

Unit tests run on the JVM under Robolectric (Room in-memory), no emulator required:

```
./gradlew test
```

## License

Baromi is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, version 3 of the License, or
(at your option) any later version. See [LICENSE](LICENSE).

Copyright (C) 2026 SOULT IO LTD
