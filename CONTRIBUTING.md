# Contributing to PULSE

PULSE is maintained by [The Allsparks](https://github.com/The-Allsparks) (FTC Team 36117).

## Setup

```powershell
git clone https://github.com/The-Allsparks/PULSE.git
cd PULSE
.\gradlew.bat check
```

Keep `allsparks-contracts` as a sibling checkout so `includeBuild` substitutes the input SPI.

## Rules

1. `pulse-core` must not import the FTC SDK, Android, FORGE, or private TeamCode.
2. Libraries consume `allsparks-contracts`, not `org.allsparks.pulse`.
3. Do not add output arbitration, command scheduling, pathing, or vision in v1.
4. Do not access Hub hardware from a background thread.
5. Do not commit secrets or student PII.

Coding agents: read [AGENTS.md](AGENTS.md) and [docs/architecture.md](docs/architecture.md).

## Line endings

LF in the repository (see `.gitattributes`).

## License

MIT ([LICENSE](LICENSE)). No CLA.
