# Sus Chunk Finder — Meteor Client Addon

A Meteor Client addon that detects suspicious chunks which may contain hidden bases using block analysis and suspicion scoring (0-100).

## Features

- **Block Detection** — Identifies valuable blocks, storage, and redstone components
- **Terrain Anomaly Detection** — Finds artificial rooms, corridors, and empty spaces
- **Suspicion Scoring** — Rates chunks from 0-100 based on multiple factors
- **Height Zone Classification** — Underground, surface, and above-Y100 zones
- **Configurable Thresholds** — All detection thresholds editable via JSON config
- **Anti-Lag System** — Rate-limited scanning (chunks/sections per tick)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Meteor Client](https://meteorclient.com/) for 1.21.11
3. Download `sus-chunk-finder-1.0.0.jar` from releases
4. Place the JAR in your `.minecraft/mods/` folder
5. Launch Minecraft with Fabric

## Usage

1. Press `G` to open Meteor menu
2. Navigate to **World** → **Sus Chunk Finder**
3. Toggle the module on
4. Configure settings as needed

## Configuration

- Module settings are stored automatically by Meteor in `modules.nbt`
- Default block thresholds are in `assets/suschunkfinder/susfinder-defaults.json`

## Building

```bash
./gradlew build
```

The JAR will be in `build/libs/sus-chunk-finder-1.0.0.jar`.

## License

MIT
