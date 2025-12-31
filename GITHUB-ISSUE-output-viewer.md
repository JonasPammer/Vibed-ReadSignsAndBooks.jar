# Feature Request: Interactive Output Viewer GUI

## Summary

Add a rich graphical viewer accessible from `Help → Output Viewer` (or dedicated toolbar button) that provides Minecraft-themed visualization of all extracted data. This goes far beyond a file explorer—it's a purpose-built data exploration interface.

## Motivation

Currently, users must navigate output folders manually and use external tools (text editors, SQLite browsers, spreadsheet apps) to explore extracted data. A built-in viewer would:
- Provide immediate visual feedback after extraction
- Enable powerful search/filter capabilities without SQL knowledge
- Display data in Minecraft-authentic styling for immersion
- Allow interactive exploration of spatial data (portals, blocks, signs on maps)

---

## Feature Breakdown

### Phase 1: Core Data Viewers (MVP)

#### 1.1 JEI-Style Item Grid Viewer (Primary Item View)

Inspired by [Just Enough Items](https://modrinth.com/mod/jei), [REI](https://modrinth.com/mod/rei), and [NEI](https://www.curseforge.com/minecraft/mc-mods/notenoughitems) - the gold standard for Minecraft item browsing.

**Visual Design:**
```
┌─────────────────────────────────────────────────────────────────────────┐
│ [🔍 Search items...]                    [⚗️ Enchanted] [📛 Named] [⚙️] │
├─────────────────────────────────────────────────────────────────────────┤
│ [All] [Tools] [Weapons] [Armor] [Blocks] [Materials] [Food] [Misc]     │
├─────────────────────────────────────────────────────────────────────────┤
│ ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐         │
│ │▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│         │
│ │ ⚔️ │ ⛏️ │ 🪓 │ 🏹 │ 🛡️ │ 💎 │ 🥇 │ ⚗️ │ 📦 │ 🪣 │ 🎣 │ 🗡️ │  ▲      │
│ │ 1  │ 1  │ 1  │ 64 │ 1  │ 64 │ 32 │ 16 │ 27 │ 1  │ 1  │ 1  │  │      │
│ │▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│  │      │
│ ├────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤  ║      │
│ │▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│  ║      │
│ │ 🧱 │ 🪨 │ 🌳 │ 🌲 │ 🍎 │ 🥕 │ 🥔 │ 🍞 │ 🥩 │ 🍖 │ 🐟 │ 🥧 │  ║      │
│ │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │ 64 │  ▼      │
│ │▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│▓▓▓▓│         │
│ └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘         │
├─────────────────────────────────────────────────────────────────────────┤
│ 📊 Showing 156 items (12 enchanted, 8 named) │ Page 1 of 13 │ [◀][▶]  │
└─────────────────────────────────────────────────────────────────────────┘
```

**Slot Cell Rendering:**
Each item displayed in a "slot" mimicking vanilla inventory:
```
┌──────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  ← Dark gray (#373737) slot background
│ ▓▓┌──────────┐▓▓ │
│ ▓▓│  [ICON]  │▓▓ │  ← 32x32 scaled item texture (from 16x16 source)
│ ▓▓│          │▓▓ │
│ ▓▓└──────────┘▓▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓64▓▓ │  ← Stack count (white text, bottom-right, with shadow)
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
└──────────────────┘
```

**Special Rendering Effects:**
- **Enchanted items**: Animated purple shimmer overlay (like in-game glint)
- **Named items**: Gold border highlight
- **Damaged items**: Durability bar (green→yellow→red gradient)
- **Hover state**: Lighter slot background (#5a5a5a) + slight scale (1.05x)

**Hover Tooltip (JEI-style):**
```
┌─────────────────────────────────────┐
│ ⚔️ Diamond Sword                    │  ← Item name (white, or gold if renamed)
│ ═══════════════════════════════════ │
│ "Excalibur"                         │  ← Custom name (gold, italic)
│ ─────────────────────────────────── │
│ Sharpness V                         │  ← Enchantments (blue text)
│ Unbreaking III                      │
│ Mending                             │
│ Fire Aspect II                      │
│ ─────────────────────────────────── │
│ "Forged in dragon fire"             │  ← Lore line 1 (purple, italic)
│ "by the ancient smiths"             │  ← Lore line 2
│ ─────────────────────────────────── │
│ Durability: 1561/1561               │  ← Damage info
│ ═══════════════════════════════════ │
│ 📍 Chest at (-100, 64, 200)         │  ← Location info (gray)
│ 🌍 Overworld                        │  ← Dimension
│ 👤 Player: Steve                    │  ← If in player inventory
│ ─────────────────────────────────── │
│ [R] Details  [C] Copy TP  [M] Map   │  ← Keyboard hints
└─────────────────────────────────────┘
```

**Virtual Scrolling (Critical for Performance):**
- Use [Flowless](https://github.com/FXMisc/Flowless) library for efficient rendering
- Only render visible cells (~100-200 at a time)
- Smooth scrolling with momentum
- Handles 100,000+ items without lag

**Search & Filtering:**
- **Real-time search**: Filter as you type (debounced 150ms)
- **Search syntax** (JEI-inspired):
  - `diamond` - Name contains "diamond"
  - `@steve` - Found by player "steve"
  - `#enchanted` - Has any enchantment
  - `#sharpness` - Has specific enchantment
  - `$named` - Has custom name
  - `^chest` - In container type "chest"
  - `~overworld` - In overworld dimension
  - `>100` - Stack count > 100
  - Combinable: `diamond #sharpness ~nether`

**Category Tabs:**
| Tab | Filter Logic |
|-----|--------------|
| All | No filter |
| Tools | `*_pickaxe, *_axe, *_shovel, *_hoe, shears, flint_and_steel` |
| Weapons | `*_sword, bow, crossbow, trident` |
| Armor | `*_helmet, *_chestplate, *_leggings, *_boots, shield` |
| Blocks | ID contains no `_` after `minecraft:` (heuristic) |
| Materials | `diamond, emerald, gold_ingot, iron_ingot, *_nugget, etc.` |
| Food | `edible` tag or known food items |
| Misc | Everything else |

**Keyboard Shortcuts:**
| Key | Action |
|-----|--------|
| `R` | Show item details panel |
| `C` | Copy teleport command to clipboard |
| `M` | Show item location on map (if map loaded) |
| `E` | Export selected items to CSV |
| `Ctrl+F` | Focus search bar |
| `Esc` | Clear search / close tooltip |
| `↑↓←→` | Navigate grid |
| `Enter` | Select/activate item |
| `Page Up/Down` | Scroll by page |

**Detail Panel (Right-Click or Press R):**
```
┌─────────────────────────────────────────────────────────────┐
│                    DIAMOND SWORD                            │
│                    ═══════════════                          │
│                                                             │
│    ┌──────────────┐                                        │
│    │              │     Custom Name: "Excalibur"           │
│    │   [LARGE     │     ──────────────────────────         │
│    │    ICON]     │     Enchantments:                      │
│    │   128x128    │       • Sharpness V                    │
│    │              │       • Unbreaking III                 │
│    └──────────────┘       • Mending                        │
│                           • Fire Aspect II                  │
│    Durability:                                              │
│    ████████████████░░ 1500/1561                            │
│                                                             │
│    ─────────────────────────────────────────────────────── │
│    LOCATIONS (3 found)                                     │
│    ┌────────────────────────────────────────────────────┐  │
│    │ 📍 Chest at -100, 64, 200 (Overworld)    [TP] [Map]│  │
│    │ 📍 Shulker at 500, 70, -300 (End)        [TP] [Map]│  │
│    │ 📍 Player: Steve                         [─] [─]   │  │
│    └────────────────────────────────────────────────────┘  │
│                                                             │
│    [Show Container Contents]  [Copy All TPs]  [Export]     │
└─────────────────────────────────────────────────────────────┘
```

**Implementation Architecture:**

```groovy
class JEIStyleItemGrid extends VirtualFlow<ItemSlot> {
    private FilteredList<ItemEntry> filteredItems
    private StringProperty searchQuery = new SimpleStringProperty("")
    private ObjectProperty<ItemCategory> category = new SimpleObjectProperty(ALL)

    // Virtual scrolling - only creates visible cells
    @Override
    protected ItemSlot createCell(int index) {
        return new ItemSlot(filteredItems.get(index))
    }
}

class ItemSlot extends StackPane {
    private static final int SLOT_SIZE = 48  // 32px icon + 8px padding each side
    private ImageView iconView
    private Label countLabel
    private Rectangle glintOverlay  // Animated for enchanted items
    private Rectangle hoverHighlight

    void render(ItemEntry item) {
        iconView.image = IconManager.getIcon(item.itemId)
        countLabel.text = item.count > 1 ? item.count.toString() : ""
        glintOverlay.visible = item.hasEnchantments()

        if (item.hasEnchantments()) {
            startGlintAnimation()
        }
    }

    private void startGlintAnimation() {
        // Purple diagonal sweep animation
        def gradient = new LinearGradient(...)
        def timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glintOverlay.opacity, 0)),
            new KeyFrame(Duration.millis(500), new KeyValue(glintOverlay.opacity, 0.3)),
            new KeyFrame(Duration.seconds(1), new KeyValue(glintOverlay.opacity, 0))
        )
        timeline.cycleCount = Animation.INDEFINITE
        timeline.play()
    }
}

class ItemTooltip extends Popup {
    void show(ItemEntry item, Node anchor) {
        // Build tooltip content dynamically
        def vbox = new VBox(4)
        vbox.styleClass.add("minecraft-tooltip")

        // Item name (gold if renamed)
        def nameLabel = new Label(item.displayName)
        nameLabel.styleClass.add(item.customName ? "renamed-item" : "item-name")
        vbox.children.add(nameLabel)

        // Custom name
        if (item.customName) {
            vbox.children.add(new Label("\"${item.customName}\"").tap {
                styleClass.add("custom-name-italic")
            })
        }

        // Enchantments
        if (item.enchantments) {
            vbox.children.add(new Separator())
            item.enchantments.each { ench, level ->
                vbox.children.add(new Label("${ench} ${toRoman(level)}").tap {
                    styleClass.add("enchantment-text")
                })
            }
        }

        // ... lore, location, etc.
    }
}
```

**CSS Styling (Minecraft Theme):**

```css
.item-slot {
    -fx-background-color: #373737;
    -fx-border-color: #1d1d1d #5a5a5a #5a5a5a #1d1d1d;
    -fx-border-width: 2;
    -fx-padding: 4;
}

.item-slot:hover {
    -fx-background-color: #4a4a4a;
    -fx-scale-x: 1.05;
    -fx-scale-y: 1.05;
}

.item-count {
    -fx-font-family: "Minecraft", "Consolas", monospace;
    -fx-font-size: 14px;
    -fx-text-fill: white;
    -fx-effect: dropshadow(gaussian, #3f3f3f, 1, 1, 1, 1);
}

.minecraft-tooltip {
    -fx-background-color: #100010f0;
    -fx-border-color: #5000ff #28007f #28007f #5000ff;
    -fx-border-width: 2;
    -fx-padding: 6;
    -fx-font-family: "Minecraft", "Consolas", monospace;
}

.item-name { -fx-text-fill: white; }
.renamed-item { -fx-text-fill: #ffff55; }
.custom-name-italic { -fx-text-fill: #ffaa00; -fx-font-style: italic; }
.enchantment-text { -fx-text-fill: #aaaaff; }
.lore-text { -fx-text-fill: #aa00aa; -fx-font-style: italic; }
.location-text { -fx-text-fill: #aaaaaa; }
```

---

#### 1.2 Block Grid Viewer (Same JEI Style)

Uses identical grid UI as Item Viewer but for blocks:
- Block textures rendered isometrically (pre-computed or top-face only)
- Hover shows: block type, properties (facing, waterlogged, etc.), coordinates
- Category tabs: Ores, Storage, Redstone, Natural, Manufactured
- Special highlight for searched blocks (`--search-blocks` results)

**Block-Specific Features:**
- Property badges (e.g., "facing:east" chip on block)
- Cluster indicator (if multiple of same block nearby)
- "Show all of type" button to see distribution

---

#### 1.3 Table View Alternative (Classic Mode)

For users who prefer traditional data tables:

**Item Index TableView** (`item_index.db`)
- Columns: Icon, Item ID, Count, Dimension, Coordinates, Container, Custom Name, Enchantments
- All columns sortable/filterable
- Multi-select with Shift/Ctrl
- Right-click context menu: Copy TP, Show on Map, Export Selection
- Toggle between Grid View and Table View with button

**Block Index TableView** (`block_index.db`)
- Columns: Icon, Block Type, Dimension, X/Y/Z, Properties, Region File
- Quick filters: block type autocomplete, dimension selector, coordinate range
- "Copy TP command" button for selected row
- Group-by view: collapse by block type showing count per dimension

**Portal Viewer** (`portals.json`)
- Card layout showing each portal with:
  - Dimension badge (colored: green=overworld, red=nether, purple=end)
  - Size visualization (proportional rectangle)
  - One-click "Copy TP to center" button
  - Linked portal detection (if coordinates match 8:1 ratio)

#### 1.2 Book Viewer (Minecraft Look & Feel)

**Design Goals:**
- Mimic the in-game written book interface
- Two-page spread with page flip animation
- Preserve Minecraft formatting codes (§ colors, bold, italic)

**Implementation:**
- Custom JavaFX component with Canvas rendering
- Page navigation: arrow buttons, keyboard (←/→), scroll wheel
- Toolbar: search within book, jump to page, font size adjustment
- Book list sidebar: filter by author, title search, sort by page count

**Rendering Details:**
- Use "Minecraft" font family (Minecraftia or similar open font)
- Support all formatting codes: §0-§f (colors), §l (bold), §o (italic), §n (underline), §m (strikethrough), §k (obfuscated/random)
- Background texture: parchment/paper similar to game

#### 1.3 Sign Viewer

- Grid of sign previews (render as sign blocks)
- Click to expand: show all 4 lines, original coordinates, TP command
- Filter: text search, dimension, coordinate range
- Sort: by content, by location, by dimension

---

### Phase 2: Interactive Map View

#### 2.1 World Map Overlay

**Concept:** Load a pre-rendered world map image (from uNmINeD, Dynmap export, or Chunky) and overlay extracted data as interactive markers.

**Map Source Options:**
1. **User provides image**: File picker for PNG/JPG exported from external tools
2. **Auto-detect**: Check for `world_map.png` in output folder or world folder
3. **Future**: Integrate basic top-down rendering (stretch goal)

**Map Features:**
- Pan and zoom (ScrollPane with viewport transform)
- Coordinate display (mouse position → Minecraft coords)
- Marker clustering at low zoom levels

**Marker Types:**
| Data Type | Marker Icon | Click Action |
|-----------|-------------|--------------|
| Signs | 🪧 Sign icon | Show sign content popup |
| Portals | 🟣 Purple ring | Show portal details, linked portal |
| Named Items | ⭐ Star | Show item details |
| Custom Blocks | Block-colored dot | Show block properties |

**Marker Popups:**
- Sign: render actual sign text in Minecraft style
- Portal: dimension, size, TP commands for both sides
- Item: name, enchantments, container type

#### 2.2 Coordinate Jumping

- "Jump to coordinates" dialog (X, Y, Z input)
- Auto-center map and highlight location
- Coordinate bookmark system (save favorite locations)

---

### Phase 3: Enhanced Features

#### 3.1 Item Icons (Minecraft Textures)

**Legal Approach - ProgrammerArt Resource Pack:**
- [ProgrammerArt](https://github.com/deathcap/ProgrammerArt) is CC-BY 4.0 licensed
- Contains all item/block textures in 16x16 format
- Bundle subset of icons needed (tools, armor, common blocks)
- ~500KB for comprehensive coverage

**Alternative - Generated Icons:**
- Programmatically generate simple colored squares for blocks
- Use Unicode symbols for items (⚔️ sword, 🛡️ shield, etc.)
- Fallback to text-only if textures unavailable

**Implementation:**
- `IconManager` class loads textures from JAR resources
- Cache frequently used icons
- Lazy-load on first display

#### 3.2 Statistics Dashboard

- Pie charts: books by author, items by type, blocks by dimension
- Bar charts: enchantment frequency, container distribution
- Timeline: if multiple extractions exist, show trends
- Use JavaFX Charts API (built-in)

#### 3.3 Cross-Reference Views

- "Where is this author's other books?" - click author → see all books
- "What else is in this container?" - click container coords → show all items
- "Books near this portal" - spatial proximity queries

#### 3.4 Search Across All Data

- Global search bar searching: books, signs, custom names, item names
- Results grouped by type with relevance ranking
- Click result → navigate to appropriate viewer

---

## Technical Implementation

### Architecture

```
OutputViewer (Main Window)
├── MenuBar
│   └── View → [Books | Signs | Items | Blocks | Portals | Map | Stats]
├── NavigationSidebar
│   ├── TreeView of output folder structure
│   └── Quick access buttons per data type
├── ContentArea (TabPane or StackPane)
│   ├── BookViewer
│   ├── SignViewer
│   ├── ItemGridViewer
│   ├── BlockGridViewer
│   ├── PortalViewer
│   ├── MapViewer
│   └── StatsDashboard
└── StatusBar
    └── "Loaded: 44 books, 156 items, 3 portals"
```

### Dependencies

**Already Available:**
- JavaFX 21 (TableView, Canvas, Charts, ScrollPane)
- SQLite via existing database classes
- JSON parsing via Groovy's JsonSlurper

**New Dependencies (Required for JEI-style grid):**
- [Flowless](https://github.com/FXMisc/Flowless) - Efficient virtual scrolling for large item lists
  - Maven: `org.fxmisc.flowless:flowless:0.7.3`
  - Enables handling 100,000+ items without performance degradation
  - Only renders visible cells (typically ~100-200 at a time)

**Optional Dependencies:**
- [ControlsFX](https://controlsfx.github.io/) - Enhanced TableView filtering for classic mode
- [mapjfx](https://codeberg.org/sothawo/mapjfx) - If integrating real map tiles later
- [Monocraft Font](https://github.com/IdreesInc/Monocraft) - Open source Minecraft-style font (OFL license)

### Data Loading Strategy

```groovy
class OutputViewerModel {
    // Lazy-loaded, cached
    private List<Book> books
    private List<Sign> signs
    private ItemDatabase itemDb
    private BlockDatabase blockDb
    private List<Portal> portals

    void loadFromFolder(File outputFolder) {
        // Detect available data files
        // Load metadata only, defer full content
    }
}
```

### Book Renderer (Canvas-based)

```groovy
class MinecraftBookRenderer {
    static final int PAGE_WIDTH = 180
    static final int PAGE_HEIGHT = 160
    static final String BOOK_FONT = "Minecraftia" // or fallback

    void renderPage(GraphicsContext gc, String pageContent, int pageNum) {
        // Draw background texture
        // Parse formatting codes
        // Render text with appropriate colors/styles
    }

    void renderSpread(Canvas canvas, Book book, int leftPage) {
        // Render two facing pages
        // Add page numbers, navigation hints
    }
}
```

### Map Overlay Architecture

```groovy
class WorldMapViewer extends ScrollPane {
    private ImageView mapImage
    private Pane markerLayer  // Overlay for clickable markers
    private double scale = 1.0
    private Point2D worldOrigin  // Map image (0,0) in world coords

    void loadMap(File imageFile, MapMetadata metadata) {
        // metadata contains: scale (blocks per pixel), origin coords
    }

    Point2D worldToImage(int x, int z) {
        // Convert Minecraft coords to image pixels
    }

    void addMarker(WorldMarker marker) {
        // Create positioned node on markerLayer
    }
}
```

---

## User Experience Flow

### Entry Points

1. **Menu Bar**: `View → Output Viewer` (opens last extraction)
2. **Post-Extraction**: "Open in Viewer" button in completion dialog
3. **Auto-open**: `--viewer` CLI flag to auto-open after extraction
4. **Standalone**: `java -jar ReadSignsAndBooks.jar --viewer /path/to/output`

### First-Time Experience

1. User completes extraction
2. Dialog: "Extraction complete! 44 books, 3 signs, 156 items found. [Open Viewer] [Close]"
3. Viewer opens with Books tab active (most common use case)
4. Sidebar shows available data types with counts
5. Quick tutorial tooltips highlight key features

---

## Asset Licensing Strategy

### Fonts
- **Minecraftia** by Andrew Tyler - Free for personal use
- **Monocraft** - Open source Minecraft-style font (OFL license)
- Fallback: System monospace font

### Textures (Item Icons)
| Source | License | Usage |
|--------|---------|-------|
| [ProgrammerArt](https://github.com/deathcap/ProgrammerArt) | CC-BY 4.0 | Full item/block icons |
| [Pixel Perfection](https://www.planetminecraft.com/texture-pack/pixel-perfection-chorus-edit/) | Open license | Alternative pack |
| Self-generated | N/A | Colored squares fallback |

### Book/Sign Textures
- Create original textures inspired by (but not copying) Minecraft
- Use procedural generation for parchment effect
- Solid color alternatives if needed

---

## Implementation Phases

### MVP (Phase 1) - ~40 hours
- [ ] Book viewer with formatting support
- [ ] Sign grid viewer
- [ ] Basic item/block database grids with filtering
- [ ] Portal list view
- [ ] Menu integration and folder loading

### Enhanced (Phase 2) - ~30 hours
- [ ] Map overlay with markers (user-provided image)
- [ ] Item icons from bundled resource pack
- [ ] Cross-reference navigation
- [ ] Global search

### Polish (Phase 3) - ~20 hours
- [ ] Statistics dashboard
- [ ] Page flip animations
- [ ] Coordinate bookmarks
- [ ] Export/print functionality

---

## Open Questions for Discussion

1. **Scope creep prevention**: Should we start with read-only viewing, or also allow editing/annotation?
2. **Standalone vs integrated**: Should viewer be a separate window or a new tab in existing GUI?
3. **Map format support**: What format should map metadata use? (JSON with origin/scale? Embedded in filename?)
4. **Persistence**: Should viewer remember last opened folder, zoom level, selected filters?
5. **Multi-extraction**: Should viewer support comparing multiple extraction runs?

---

## Related Issues

- #12 - GUI freeze fix (viewer must also use batched updates)
- #18 - Litematica export (viewer could show schematic preview)

---

## References & Inspiration

### JEI/REI Style Item Browsers (Primary Inspiration)
- [Just Enough Items (JEI)](https://modrinth.com/mod/jei) - The gold standard for Minecraft item browsing
- [Roughly Enough Items (REI)](https://modrinth.com/mod/rei) - Modern alternative with similar UI
- [Not Enough Items (NEI)](https://www.curseforge.com/minecraft/mc-mods/notenoughitems) - Classic predecessor
- [JEI GitHub](https://github.com/mezz/JustEnoughItems) - Source code for implementation reference

### JavaFX Virtual Scrolling
- [Flowless Library](https://github.com/FXMisc/Flowless) - **Essential** for handling thousands of items
- [Flowless Documentation](https://github.com/FXMisc/Flowless#features) - API and usage examples

### JavaFX Resources
- [TableView Sorting & Filtering](https://code.makery.ch/blog/javafx-8-tableview-sorting-filtering/)
- [ControlsFX FilteredTableView](https://controlsfx.github.io/javadoc/11.1.0/org.controlsfx.controls/org/controlsfx/control/tableview2/FilteredTableView.html)
- [Zoomable/Pannable ImageView Gist](https://gist.github.com/james-d/ce5ec1fd44ce6c64e81a)
- [Page Flip Animation Gist](https://gist.github.com/skrb/1c62b77ef7ddb3c7adf4)
- [TilePane vs FlowPane](https://www.oreilly.com/library/view/mastering-javafx-10/9781788293822/1957ce89-d51b-47c1-9146-1af3b2b3cd99.xhtml)

### Map Tools (for user-generated map images)
- [uNmINeD](https://unmined.net/) - Offline Minecraft world mapper
- [Dynmap](https://www.spigotmc.org/resources/dynmap%C2%AE.274/) - Web-based live map
- [ChunkyMap](https://github.com/leMaik/ChunkyMap) - Photorealistic renders

### Resource Packs (CC-licensed textures)
- [ProgrammerArt (CC-BY 4.0)](https://github.com/deathcap/ProgrammerArt)
- [Minecraft Forum: Open License Packs](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/resource-packs/resource-pack-discussion/1256229-list-of-open-license-resource-packs)
- [Public Domain Textures Thread](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/resource-packs/resource-pack-discussion/2981321-free-to-use-public-domain-textures-for-mods)

---

## Labels
`enhancement`, `gui`, `phase-2`, `good-first-issue` (for individual subcomponents)
