# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md
@.kilocode/rules/memory-bank/gui.md
@.kilocode/rules/memory-bank/minecraft-datapacks.md
@.kilocode/rules/memory-bank/minecraft-resources.md
@.kilocode/rules/memory-bank/nbt-litematica-formats.md

## CRITICAL: Minecraft Datapack Directory Naming

**WARNING**: Minecraft 1.21 snapshot 24w21a changed directory naming from PLURAL to SINGULAR.

Pre-1.21 versions (1.13-1.20.6): Use `functions/` (plural)
1.21+ versions: Use `function/` (singular)

**ALWAYS** use version-specific directory naming in `setupDatapackStructure()`:
```groovy
String functionDirName = (version == '1_21') ? 'function' : 'functions'
```

**DO NOT** hardcode `function/` for all versions - this will break pre-1.21 datapacks!

See @.kilocode/rules/memory-bank/minecraft-datapacks.md for complete technical reference.

## Interactive Output Viewer (NEW)

The project includes a comprehensive **Interactive Output Viewer** GUI for browsing extracted data.

### Key Components
- `OutputViewer.groovy` - Main viewer window with tabbed interface
- `OutputViewerModel.groovy` - Data layer loading JSON/CSV/SQLite from output folders

### Integrated Viewer Components
| Tab | Component | Features |
|-----|-----------|----------|
| Books | `BookViewer` | Two-page spread, Minecraft formatting codes (§), search/filter/sort |
| Signs | Built-in ListView | Search by text/dimension/coordinates, dimension filter |
| Items | TextArea (fallback) | Shows item database summary |
| Blocks | `BlockGridViewer` | Visual grid with category/dimension filters |
| Portals | TextArea | Portal coordinates and dimensions |
| Map | Placeholder | Coming soon |
| Statistics | `StatsDashboard` | KPI cards, charts, interactive filtering |

### Theme System
`viewers/ThemeManager.groovy` provides Light/Dark/System theme support:
```groovy
ThemeManager.initialize()
ThemeManager.registerScene(scene)  // Applies and tracks theme
stage.onCloseRequest = { ThemeManager.unregisterScene(scene) }  // Cleanup
```

### Block Search Wildcard
Use `--search-blocks "*"` or check "Index ALL blocks" in GUI to scan all block types.

### Testing BookViewer
BookViewer tests require calling `initializeUI()` after instantiation:
```groovy
viewer = new BookViewer()
viewer.initializeUI()  // Required to initialize UI components
```