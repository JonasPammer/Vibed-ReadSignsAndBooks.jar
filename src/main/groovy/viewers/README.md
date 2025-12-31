# Viewers Package

Interactive JavaFX viewers for extracted Minecraft data.

## CustomNamesViewer

Displays and filters custom-named items and entities extracted from Minecraft worlds.

### Features

- **Interactive Table View**
  - Sortable columns: Name, Type, Coordinates, Dimension, Container
  - Multi-selection support
  - Context menu with copy and navigation actions

- **Advanced Filtering**
  - Text search across names and item types
  - Filter by type (Items/Entities)
  - Filter by dimension (Overworld/Nether/End)
  - Filter by category (Tools, Weapons, Armor, etc.)

- **Group-by Mode**
  - Collapse duplicate custom names
  - Show count of occurrences
  - View all locations for grouped entries

- **Detail Panel**
  - Large display of custom name with formatting
  - Full item/entity details
  - Coordinate information
  - Copy-ready teleport command
  - All locations for grouped names

- **Keyboard Shortcuts**
  - `Ctrl+C`: Copy selected name to clipboard
  - Double-click: Show detail panel
  - Right-click: Context menu

### Usage

#### Standalone Application

```bash
# Run the viewer with sample data
java -cp ReadSignsAndBooks.jar viewers.CustomNamesViewerApp

# Or specify a data file
java -cp ReadSignsAndBooks.jar viewers.CustomNamesViewerApp /path/to/custom_names.json
```

#### Embedded in GUI

```groovy
import viewers.CustomNamesViewer

// Create viewer
CustomNamesViewer viewer = new CustomNamesViewer()

// Load from JSON
File jsonFile = new File('output/custom_names.json')
viewer.loadData(jsonFile)

// Or load from CSV
File csvFile = new File('output/custom_names.csv')
viewer.loadFromCsv(csvFile)

// Add to scene
Scene scene = new Scene(viewer, 1200, 700)
stage.scene = scene
```

### Data Format

#### JSON Format

```json
[
  {
    "type": "item",
    "itemOrEntityId": "minecraft:diamond_sword",
    "customName": "§cExcalibur",
    "x": 100,
    "y": 64,
    "z": 200,
    "location": "Chest in Stronghold"
  },
  {
    "type": "entity",
    "itemOrEntityId": "minecraft:horse",
    "customName": "Lightning",
    "x": 300,
    "y": 65,
    "z": 400,
    "location": "Stable near spawn"
  }
]
```

#### CSV Format

```csv
type,itemOrEntityId,customName,x,y,z,location
item,minecraft:diamond_sword,§cExcalibur,100,64,200,Chest in Stronghold
entity,minecraft:horse,Lightning,300,65,400,Stable near spawn
```

### Item Categories

The viewer automatically categorizes items into:

- **Tools**: Pickaxes, Axes, Shovels, Hoes, Fishing Rods, Shears
- **Weapons**: Swords, Bows, Crossbows, Tridents
- **Armor**: Helmets, Chestplates, Leggings, Boots, Shields, Elytra
- **Entities**: Armor Stands, Item Frames, Minecarts, Boats, Mobs
- **Other**: Everything else

### Context Menu Actions

- **Copy Name**: Copy custom name to clipboard
- **Copy TP Command**: Copy `/tp @s X Y Z` command
- **Show All with Same Name**: Filter to show all occurrences of this name

### Dimension Detection

The viewer automatically extracts dimension from location strings:

- Contains "nether" or "dim-1" → `nether`
- Contains "end" or "dim1" → `end`
- Otherwise → `overworld`

### Styling

The viewer inherits styling from the parent application. It's designed to work with:

- AtlantaFX themes (Primer Light/Dark)
- System default JavaFX themes
- Custom CSS styling

### Future Enhancements

- [ ] Item icons in the icon column (using Minecraft texture atlas)
- [ ] World map view showing item/entity locations
- [ ] Export filtered results to new JSON/CSV
- [ ] Enchantment and lore display for items
- [ ] NBT data viewer for advanced users
- [ ] Batch TP command generation
- [ ] Integration with coordinate tracking tools

## BookViewer

A JavaFX-based Minecraft book viewer with full formatting support.

### Features

#### Two-Page Spread Layout
- Mimics in-game written book appearance
- 192x192px page dimensions (scaled for readability)
- Parchment/paper background texture (#E8D5B3)
- Brown border (#8B7355)

#### Full Minecraft Formatting Support

**Color Codes (§0-§f)**
- §0 Black (#000000), §1 Dark Blue (#0000AA), §2 Dark Green (#00AA00)
- §3 Dark Aqua (#00AAAA), §4 Dark Red (#AA0000), §5 Dark Purple (#AA00AA)
- §6 Gold (#FFAA00), §7 Gray (#AAAAAA), §8 Dark Gray (#555555)
- §9 Blue (#5555FF), §a Green (#55FF55), §b Aqua (#55FFFF)
- §c Red (#FF5555), §d Light Purple (#FF55FF), §e Yellow (#FFFF55), §f White (#FFFFFF)

**Formatting Codes**
- §l **Bold**, §o *Italic*, §n <u>Underlined</u>, §m ~~Strikethrough~~
- §k Obfuscated (animated random characters), §r Reset all formatting

#### Navigation
- **Arrow Buttons**: Click ◀ Previous / Next ▶ buttons
- **Keyboard**: Arrow keys (←/→) or A/D keys
- **Scroll Wheel**: Scroll up for previous, down for next
- **Two-page navigation**: Advances/rewinds by 2 pages (left + right)

#### Book Library Sidebar
- **Search**: Filter books by title
- **Author Filter**: Dropdown to filter by author
- **Sort Options**: Title (A-Z/Z-A), Author (A-Z), Page Count (High-Low/Low-High)
- **Stats**: Shows "X of Y books shown" after filtering

### Usage

```bash
# Using Gradle
./gradlew run -PmainClass=viewers.BookViewer

# Or compile and run JAR
./gradlew fatJar
java -cp ReadSignsAndBooks.jar viewers.BookViewer
```

## Future Viewers

Planned viewers for other extraction data:

- **SignsViewer**: View and filter signs with clickable coordinates
- **PortalsViewer**: Visualize portal structures with 3D preview
- **BlockSearchViewer**: Display block search results on world map
- **ItemDatabaseViewer**: Query and explore indexed items

## Dependencies

- JavaFX 21+ (javafx.controls, javafx.graphics)
- Groovy 4.0.24
- SLF4J 2.0.16 (logging)

## License

Same as parent project (ReadSignsAndBooks.jar).
