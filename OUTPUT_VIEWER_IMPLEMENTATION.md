# Output Viewer Implementation

## Overview

The Output Viewer is a new JavaFX-based GUI component for browsing and analyzing extracted Minecraft data. It provides a tabbed interface for viewing books, signs, items, blocks, portals, and statistics from extraction output folders.

## Files Created

### 1. `src/main/groovy/OutputViewerModel.groovy` (371 lines)

**Purpose**: Data model that loads and holds all extracted data.

**Key Features**:
- Loads JSON files: `all_books_stendhal.json`, `custom_names.json`, `portals.json`, `block_results.json`
- Parses CSV files: `all_signs.csv`
- Opens SQLite databases: `items.db`, `blocks.db`
- Provides metadata summary with counts of all loaded data
- Custom CSV parser that handles quoted fields and escaped quotes
- Implements `close()` to properly cleanup database connections

**Key Methods**:
- `loadFromFolder(File folder)` - Main entry point, loads all data
- `getSummaryText()` - Returns human-readable summary string
- `parseCsv(File file)` - CSV parsing with proper quote handling
- Private load methods for each data type

### 2. `src/main/groovy/OutputViewer.groovy` (531 lines)

**Purpose**: Main viewer window with tabbed interface.

**Key Features**:
- **Toolbar**: Folder selection, browse button, load/refresh buttons
- **Sidebar**: TreeView showing output folder file structure
- **Main Content**: TabPane with 7 tabs:
  - Books - Shows loaded books with title, author, pages, location
  - Signs - Shows sign text and coordinates
  - Items - Item database summary and top 20 item types
  - Blocks - Block database summary with all block types
  - Portals - Portal locations and dimensions
  - Map - Placeholder for future interactive map feature
  - Statistics - Complete data summary
- **Status Bar**: Shows data load summary
- **Theming**: Uses AtlantaFX PrimerDark/PrimerLight based on system preference
- **Icon**: Uses same 512px icon as main GUI

**Architecture**:
```
OutputViewer (Stage)
├── Toolbar (HBox)
│   ├── Folder selection field
│   ├── Browse button
│   └── Load/Refresh buttons
├── SplitPane (25/75 split)
│   ├── Sidebar TreeView (file structure)
│   └── TabPane (content tabs)
└── StatusBar (Label)
```

**Key Methods**:
- `loadFolder()` - Loads data in background thread
- `updateUI()` - Updates all tabs after loading
- `updateFolderTree()` - Populates file tree recursively
- Update methods for each tab type
- `showError()` / `showInfo()` - Alert dialogs

### 3. GUI Integration (`GUI.groovy` modifications)

**Changes**:
1. Added "Output Viewer" menu item in Help menu (between GitHub and About)
2. Added `showOutputViewer()` method that:
   - Creates new OutputViewer instance
   - Pre-populates folder field if extraction was run
   - Shows the viewer window
   - Error handling with logging

## Usage Flow

1. User clicks Help → Output Viewer
2. Output Viewer window opens
3. User browses to an output folder (e.g., `ReadBooks/2025-12-23`)
4. User clicks "Load"
5. Background thread loads all data:
   - JSON files parsed
   - CSV files parsed
   - SQLite databases opened
   - File tree populated
6. All tabs update with loaded data
7. Status bar shows summary: "Loaded: X books, Y signs, Z portals..."

## Data Sources

The viewer reads these files from the output folder:

| File | Format | Tab | Description |
|------|--------|-----|-------------|
| `all_books_stendhal.json` | JSON | Books | All extracted books with metadata |
| `all_signs.csv` | CSV | Signs | All signs with coordinates |
| `custom_names.json` | JSON | Statistics | Items/entities with custom names |
| `portals.json` | JSON | Portals | Portal structures and coordinates |
| `block_results.json` | JSON | Statistics | Block search results |
| `items.db` | SQLite | Items | Item database with full query capability |
| `blocks.db` | SQLite | Blocks | Block database with coordinates |

## Current Limitations (Expected)

1. **Placeholder Tabs**: Map tab is placeholder (future feature)
2. **Simple Display**: Books/Signs show first 10 items + count
3. **No Filtering**: No search/filter UI yet (databases support it)
4. **Read-Only**: No editing or export from viewer (view-only)
5. **No Auto-Refresh**: Must click Refresh to reload after new extraction

## Future Enhancements (Not Implemented)

These are intentionally left for future development:

1. **Advanced Book Viewer**: Full book reader with page navigation
2. **Sign Map View**: Interactive 2D/3D map showing sign locations
3. **Item Query Builder**: GUI for constructing complex item database queries
4. **Block Visualizer**: 3D visualization of portal structures
5. **Export Features**: Export filtered results to CSV/JSON
6. **Search/Filter**: Global search across all data types
7. **Custom Queries**: SQL query interface for power users

## Testing

Manual testing steps:

1. Run extraction with various options enabled
2. Open Output Viewer from Help menu
3. Browse to output folder
4. Click Load
5. Verify all tabs populate correctly
6. Check status bar shows correct counts
7. Verify file tree shows folder structure
8. Test with folders missing some files (should handle gracefully)

## Integration Points

### With ItemDatabase.groovy
- Uses `ItemDatabase.openForQuery(File)` static factory
- Calls `getSummary()` for item type listing
- Accesses metadata counts

### With BlockDatabase.groovy
- Uses `BlockDatabase.openForQuery(File)` static factory
- Calls `getSummary()` for block type listing
- Accesses metadata counts

### With GUI.groovy
- Menu integration in `setupMenuBar()`
- Pre-populates output folder from `actualOutputFolder` field
- Uses same theming system (AtlantaFX)
- Consistent error handling patterns

## Error Handling

The implementation includes robust error handling:

1. **File Not Found**: Gracefully handles missing JSON/CSV files (logs warning, continues)
2. **Invalid JSON**: Catches parse errors, logs warning
3. **Database Errors**: Safely handles missing/corrupted databases
4. **Invalid Folder**: Shows error dialog if folder doesn't exist
5. **Load Failures**: Background thread catches all exceptions, shows error dialog

## Performance Considerations

1. **Background Loading**: All I/O happens in background thread to keep UI responsive
2. **Lazy Database Queries**: Databases only queried when needed (not pre-loaded)
3. **Limited Display**: Only shows first 10 books/signs to avoid UI freeze
4. **Streaming Potential**: Database classes support streaming for future enhancement

## Code Quality

- **Logging**: SLF4J logger for debugging
- **Resource Cleanup**: `close()` methods on databases
- **Null Safety**: Groovy safe navigation (`?.`) throughout
- **Consistent Style**: Matches existing GUI.groovy patterns
- **Documentation**: Comprehensive JavaDoc-style comments

## Build Verification

```
> Task :compileGroovy
BUILD SUCCESSFUL in 10s
```

All files compile successfully with no errors or warnings.

## Memory Bank Integration

The implementation references these memory bank files for patterns:
- `gui.md` - GUI architecture and theming patterns
- `architecture.md` - Database usage patterns
- `tech.md` - Groovy and JavaFX dependencies

---

**Status**: ✅ COMPLETE - Base framework implemented and ready for testing
**Compilation**: ✅ SUCCESSFUL
**Integration**: ✅ Menu item added to Help menu
**Testing**: ⏳ Manual testing required
