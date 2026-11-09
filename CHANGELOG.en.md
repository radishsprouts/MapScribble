# Changelog

English version of [CHANGELOG.md](CHANGELOG.md). The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The CurseForge changelog shows this file's section first, followed by the Japanese one.

## [0.3.0] - 2026-11-10

### Added

- The pencil button at the top right of the full-screen map in Xaero's World Map, FTB Chunks and JourneyMap 5.10 (when the official JourneyMap plugin is not active) can now be moved. Drag it with the left mouse button, or Shift+click to pick it up and click again to place it. Right-click returns it to the default position, and Esc (or right-click while holding it) cancels the move. The position is remembered per map mod in `config/mapscribble-ui.json` (`toggleButtons`), as the distance from the nearest screen edges. The buttons that the official JourneyMap plugin places in its right-hand column are not affected
- The pencil button's tooltip now explains how to move it (and how to reset it once moved), in all 10 languages

### Changed

- The pencil button now toggles drawing mode when the mouse button is released instead of when it is pressed, since a press alone cannot tell a click from a drag. A simple click works as before

### Fixed

- In Xaero's World Map on Minecraft 1.20.1 and others, the pencil button covered the map's direction overlay (N/E/S/W) and could not be moved
