# Changelog

English version of [CHANGELOG.md](CHANGELOG.md). The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The CurseForge changelog shows this file's section first, followed by the Japanese one.

## [0.4.0] - 2026-11-24

Notes can now be kept separately for each backend server behind a proxy such as Velocity. Your existing notes are not removed.

### Added

- Per-server notes behind a proxy (Velocity and the like). When the world seed hash of the same dimension changes within one connection to the same address, that address is split automatically. Each backend server gets its own "notebook", and notes are saved into the notebook of the server you are on (`mapscribble/servers/<address>/nb-…/`, recorded in `notebooks.json` in the same folder). Ordinary servers and singleplayer behave as before. To keep servers that change the seed on every join from creating notebooks endlessly, at most 8 notebooks are created automatically per address
- A menu button on the tool band. Its "Notebooks…" item opens the notebook screen (multiplayer only; in singleplayer it is greyed out and reads "This world only")
- The notebook screen: turn the split on or off for the address (you can also split by hand), switch notebooks by clicking one in the list (with the list focused, Up / Down moves the cursor and Enter switches; focus, cursor and any name being typed are kept when you resize the window, change the GUI scale or come back from the confirmation screen; on an address that is not split, notebooks are shown as greyed-out rows you cannot click; an empty name reads "(no name)", and automatic names are "Notebook N"), rename a notebook (duplicate names are detected ignoring case and full-width / half-width forms), and create a new one. There is no way to delete a notebook
- Moving pre-split notes into the notebook in use, with "Move all here (N)…" (only the pre-split notes of the current dimension) and "Move visible" on the notebook screen (a confirmation screen is shown). A move can be undone from the screen or with Ctrl+Z on the map. Before the first move, the original file is kept once in the same folder as `<file name>.before-split.bak`
- Pre-split notes look the same on the map; only when you hover over one with the eraser or edit tool, it says "Pre-split note (shown on every server)"
- New notices on the hint line below the band ("Notebook: …", "Notes are now split by server", "Notes moved: …", "Moved notes put back", "Move failed", "Imported 0.1.0 notes as notebooks", and so on)
- Importing 0.1.0 notes: when one address has two or more 0.1.0 folders (under JourneyMap's `data/mp/`), each folder is imported as its own notebook

### Changed

- The tool band layout was finalised. The top row gained the menu button, and the bottom row changes with the selected tool (outline: line width, fill opacity, color; line: line width, color; text: text size, color; eraser and edit have no settings and show "No settings for this tool"). The fill opacity of outlines can now be chosen from the band. The band is a little wider (a position saved far to the right in an older version is pulled back so it does not go off screen)
- Rarely used features such as the notebook location are gathered in the menu, so the band is not filled with buttons
- The undo / redo history is now also discarded when you move to another backend server behind a proxy, and the "Ctrl+Z to undo" notice that is showing disappears with it
- The new 0.4.0 texts are available in all 10 languages (German, Spanish, French, Korean, Brazilian Portuguese, Russian, Simplified and Traditional Chinese were added)

### Fixed

- Starting a drag from the margin of the tool band could open the line-note screen. A drag that starts on the band no longer starts drawing a line after you leave the band
- The hint line below the band (button descriptions and notices) was not being drawn in 0.2.0 and 0.3.0. It is back, with a dark background so it stays readable on bright terrain, and it appears above the band when you move the band near the bottom edge of the screen
- After moving the band, the clickable area of the buttons could be off from where the buttons were drawn
- On closing the game, the last changes (within about 1 second of editing) could fail to be saved. Pending notes are now written out when the game closes
- Opening the notebook screen while notebooks were still loading left it stuck on "Loading…" with no rows, even after loading finished. The rows now appear as soon as loading finishes, without any input
- Notes drawn in 0.1.0 were not carried over in 0.2.0 and later. 0.1.0 saved them in JourneyMap's per-world folder (`journeymap/data/{mp,sp}/<folder>/addon-data/mapscribble/`), but the carry-over looked in a different place. The old files are left untouched

### About your existing notes

- Notes that were in the address folder before the split (those drawn in 0.2.0 and 0.3.0) are not deleted, moved or renamed. After the split they still show on every server and can still be edited (edits are written to the original file)
- If you go back to 0.3.0, the notes in the address folder are still shown. Notes drawn in notebooks, and notes moved into notebooks, are not visible in 0.3.0 (the files stay on disk)

### Known limitations

- Two servers with the same seed cannot be told apart, so they are not split automatically (switch by hand on the notebook screen)
- When a server hides the seed and the value cannot be read, the address is not split automatically (notes are written to the same place as before)

## [0.3.0] - 2026-11-10

### Added

- The pencil button at the top right of the full-screen map in Xaero's World Map, FTB Chunks and JourneyMap 5.10 (when the official JourneyMap plugin is not active) can now be moved. Drag it with the left mouse button, or Shift+click to pick it up and click again to place it. Right-click returns it to the default position, and Esc (or right-click while holding it) cancels the move. The position is remembered per map mod in `config/mapscribble-ui.json` (`toggleButtons`), as the distance from the nearest screen edges. The buttons that the official JourneyMap plugin places in its right-hand column are not affected
- The pencil button's tooltip now explains how to move it (and how to reset it once moved), in all 10 languages

### Changed

- The pencil button now toggles drawing mode when the mouse button is released instead of when it is pressed, since a press alone cannot tell a click from a drag. A simple click works as before

### Fixed

- In Xaero's World Map on Minecraft 1.20.1 and others, the pencil button covered the map's direction overlay (N/E/S/W) and could not be moved
