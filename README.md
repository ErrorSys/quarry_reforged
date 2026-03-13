# Quarry Reforged

A BuildCraft-style quarry mod for Fabric, designed for Minecraft `1.20.1` with Tech Reborn power integration.

## Features
- Marker-defined quarry areas with live laser preview.
- Invalid marker layouts display helper beams in all cardinal directions (including up/down).
- Automated frame build and frame removal routines.
- Multi-phase mining flow:
  - Frame setup/build
  - Top laser cube mining
  - Gantry/tool-head mining
  - Rediscovery pass for reappearing blocks
- Configurable speed/energy/size limits.
- Upgrade system:
  - Speed
  - Fortune I/II/III
  - Silk Touch
  - Chunkloading
  - Void Overflow

## Requirements
- Minecraft `1.20.1`
- Java `17+`
- Fabric Loader `0.14+`
- Fabric API
- Tech Reborn

## Installation
1. Install Fabric Loader for Minecraft `1.20.1`.
2. Install Fabric API.
3. Install Tech Reborn.
4. Place `quarry_reforged` in your `mods` folder.

## Quick Start
1. Place quarry block.
2. Place quarry markers for the target area, one behind the quarry, 1 in the X direction, 1 in the Z direction, and optionally one in the Y direction.
3. Right-click a defining marker to validate and activate area preview.
4. Right-click again to clear active preview/helper render.
5. Press `Start` in the quarry GUI.

`Start` requires an active marker preview on the origin marker behind the quarry.

## Marker Rules
- The origin marker is the X / Z intersection marker (the one behind the quarry when starting).
- Two base-axis markers define X and Z extent (loose Y allowed for discovery).
- Optional top marker (same X / Z as origin) sets top Y for larger initial frame volume.
- If no top marker exists, default height of 5 blocks is used.
- Smallest valid prism is selected if multiple markers co-exist on the same X / Y line.
- Preview fails if the prism intersects another active preview.
- Invalid layouts are reported to console logs.

## Configuration
Config file: `config/quarry_reforged.json`

Main options:
- `maxQuarrySize`
- `blacklist`
- `energyCapacity`
- `maxEnergyInput`
- `energyPerFrame`
- `energyPerBlock`
- `hardnessEnergyScale`
- `minBlocksPerSecond`
- `maxBlocksPerSecond`
- `enableChunkloadingUpgrade`
- `chunkloadingUpgradeRadius`
- `chunkTicketLevel`
- `rediscoveryScanIntervalTicks`
- `autoFrameRepair`
- `frameRebuildScanInterval`
- `debug` (enables `/quarry debug ...` command tree)
- `enableStateDebugLogging`

## Commands
Operator-only debug root (requires `debug=true`): `/quarry debug`

Examples:
- `/quarry debug viz preview on <pos>`
- `/quarry debug viz preview off <pos>`
- `/quarry debug viz interp on <pos>`
- `/quarry debug viz interp off <pos>`
- `/quarry debug diag rediscovery on <pos>`
- `/quarry debug diag gantrytrace on <pos>`
- `/quarry debug diag rendertrace on <pos>`
- `/quarry debug io autoexportlog on`

## Development
Build with Gradle:
- Windows: `gradlew.bat build`
- Unix/macOS: `./gradlew build`

## License
MIT (see `LICENSE`).
