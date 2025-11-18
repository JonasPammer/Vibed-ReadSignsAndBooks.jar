# Technical Architecture

## System Overview
ReadSignsAndBooks.jar is a single-threaded CLI application that processes Minecraft world saves to extract written content (books and signs). The system operates in three distinct phases:

1. **Player Data Phase** - Extract books/signed books from player inventories
2. **Region Files Phase** - Scan block containers within world region files
3. **Entity Files Phase** - Extract books from entity data (minecarts, end crystals)

All extracted content is deduplicated by content hash and output to user-selected format.

## Core Components

### Main.groovy (~2431 lines)
**Location:** `src/main/groovy/Main.groovy`

Single monolithic Groovy file containing all application logic. This is intentional for simplicity and ease of deployment as a standalone JAR.

**Key Static Components:**
- `main(String[] args)` - Entry point, CLI argument parsing via Picocli
- `runExtraction()` - Orchestrates three-phase extraction
- `extractPlayerData()` - Phase 1: Processes playerdata/*.dat files
- `extractRegionFiles()` - Phase 2: Scans region/*.mca files for containers
- `extractEntityFiles()` - Phase 3: Processes entities/*.mca files
- `processContainer()` - Recursive container traversal (handles nesting)
- `writeOutput()` - Formats and writes results

**Static Field Management:**
```groovy
static Set<String> extractedBooks = [] as Set  // Content-based dedup
static Set<String> extractedSigns = [] as Set
static int totalFiles = 0
static int processedFiles = 0
static ConsoleProgressBar progressBar = null
```

Intentional static state simplifies single-threaded processing without complex object threading.

## Data Processing Pipeline

### High-Level Data Flow
```
Minecraft World Files (.mca region files)
    ↓
MCAUtil.read() with LoadFlags (chunks, block entities, entities)
    ↓
NBT Tag traversal (CompoundTag, ListTag, StringTag)
    ↓
Parse books (container recursion: chest → shulker → bundle)
    ↓
Hash-based deduplication (bookHashes Set)
    ↓
Output generation (parallel writers for formats)
    ↓
ReadBooks/YYYY-MM-DD/ folder structure
```

### Detailed Processing Stages

### Input Stage
- Accept world directory path via CLI argument
- Validate directory structure (playerdata, region, entities folders)
- Count total files for progress tracking

### Processing Stage
**Format Detection & NBT Parsing:**
- Detect Minecraft version from level.dat
- Fallback strategy: Try new format (1.20.5+) → old format (1.18/1.20)
- Parse binary NBT using Querz library

**Version Compatibility Layer:**
```
1.20.5+ format: 
  - Books stored in CompoundTag with new structure
  - Page array format updated

1.18/1.20 format:
  - Legacy page storage in CompoundTag
  - Different key naming conventions
  
Fallback logic: New format attempted first, old format on failure
```

### Container Type Support (17 types)
- Chests, Barrels, Shulker Boxes, Bundles
- Hoppers, Dispensers, Droppers
- Furnaces, Smokers, Blast Furnaces
- Brewing Stands, Cauldrons
- Minecarts (all variants), End Crystals
- Other storage containers

**Recursive Nesting:**
- Shulker boxes can contain other shulkers
- Bundles can contain bundles
- All containers scanned recursively via `processContainer()` method

### Deduplication Strategy
- Content-based: `hashCode()` of book pages array
- Not reference-based equality
- Single instance per unique text content regardless of copies
- Applied to both books and signs

### Output Stage
**Streaming Architecture:**
- Write books/signs incrementally as processed
- Prevents OOM on large worlds
- No buffering of complete content in memory

**Format Writers:**
1. **Stendhal JSON** - Preserves metadata (author, title, pages, type, location)
2. **CSV** - Tabular format (type, title, author, page_count, content_preview)
3. **Combined Text** - Human-readable merged output
4. **Minecraft Commands** - Four version-specific mcfunction files:
   - `all_books-1_13.mcfunction` - Format: `give @p written_book{title:"...",author:"...",pages:['{"text":"..."}']}`
   - `all_books-1_14.mcfunction` - Format: `give @p written_book{title:"...",author:"...",pages:['["..."]']}`
   - `all_books-1_20_5.mcfunction` - Format: `give @p written_book[minecraft:written_book_content={...}]`
   - `all_books-1_21.mcfunction` - Format: `give @p written_book[written_book_content={...}]`

## Key Algorithms

### Container Recursion Algorithm
Deep inspection of nested containers handles complex storage structures:
- Recursively processes `Items` ListTag in container NBT data
- Supports arbitrary nesting depth (e.g., bundle → shulker → chest → shulker → bundle)
- Maintains location context through recursion stack
- Implemented in `processContainer()` method with CompoundTag parameter
- Handles all 17+ container types uniformly via NBT tag structure

### Hash-Based Deduplication
Content-based book deduplication using Java hashCode():
- Hash computed from book pages array content (not object reference)
- `bookHashes` Set tracks seen content hashes
- Duplicates saved to `.duplicates/` subfolder instead of being skipped
- Preserves location metadata for all copies
- Sign deduplication uses similar approach with `signHashes` Set

### Coordinate Extraction
Block entity positions extracted from NBT data:
- Block entities store position as `x`, `y`, `z` NumberTag fields
- Coordinates formatted as `x_y_z` in output filenames
- Chunk coordinates computed for region file context
- Used in metadata and output file naming

### Sign Position Tracking
Signs tracked by both content hash and physical location:
- Unique sign text: Incremented X coordinate (~1, ~2, ~3, etc.)
- Duplicate sign text: Same X coordinate, incremented Z offset (~0, ~1, ~2, etc.)
- Implemented via `signsByHash` Map tracking positions per hash
- Enables recreating sign placement patterns in mcfunction commands

### Multi-Version Command Generation
Separate command generators for Minecraft version compatibility:
- **1.13**: Legacy NBT syntax in commands with `{"text":"..."}` pages
- **1.14**: JSON text components with `["..."]` page format
- **1.20.5**: Component system using `[minecraft:written_book_content={...}]`
- **1.21**: Latest format with `[written_book_content={...}]` (no namespace prefix)
- Each generator has version-specific escaping rules (`\\n` vs `\n`)

## Important Patterns

### NBT Data Access Patterns
When working with Querz NBT library:
```groovy
// Safe nested access with null checks
CompoundTag tag = ...
if (tag.containsKey("key")) {
    Tag value = tag.get("key")
    if (value instanceof StringTag) {
        String text = ((StringTag) value).getValue()
    }
}

// ListTag iteration for container items
ListTag<CompoundTag> items = tag.getListTag("Items")
for (CompoundTag item : items) {
    // Process each item
}

// Casting NBT tag types
StringTag stringTag = (StringTag) tag.get("id")
NumberTag numberTag = (NumberTag) tag.get("Count")
CompoundTag compound = (CompoundTag) tag.get("tag")
```

Key NBT access principles:
- Always check `containsKey()` before accessing nested tags
- Cast appropriately: `StringTag`, `ListTag<?>`, `NumberTag`, `CompoundTag`
- Container items stored in `tag.Items` ListTag (note capitalization)
- Book pages in `tag.pages` or `tag.written_book_content.pages` depending on version

### Minecraft Version Handling in Code
When modifying mcfunction command generation:
- **1.13 Format**: Uses legacy NBT syntax with § formatting codes, newlines as `\\n`
  - Example: `give @p written_book{title:"...",author:"...",pages:['{"text":"..."}']}`
- **1.14 Format**: Introduced JSON text components, still uses `\\n`
  - Example: `give @p written_book{title:"...",author:"...",pages:['["..."]']}`
- **1.20.5 Format**: Component system overhaul, uses `\n` for newlines
  - Example: `give @p written_book[minecraft:written_book_content={title:"...",author:"...",pages:["..."]}]`
- **1.21 Format**: Removed namespace prefix requirement, uses `\n`
  - Example: `give @p written_book[written_book_content={title:"...",author:"...",pages:["..."]}]`

All generators must maintain backward compatibility. Test with actual Minecraft versions before deployment.

### Minecraft Formatting Codes
§ codes preserved by default for color/styling:
- `COLOR_CODES` array in Main.groovy defines all valid codes (§0-§f, §k-§r)
- Codes represent colors, bold, italic, underline, strikethrough, obfuscated, reset
- `--remove-formatting` CLI flag strips all § codes from output
- Codes must be preserved in mcfunction commands for in-game appearance
- Used in both book pages and sign text

### State Persistence Pattern
`.failed_regions_state.json` tracks problematic region files:
- Map keyed by world folder name (`worldFolderName -> Set<failedRegionFiles>`)
- Prevents retry spam on corrupted/unreadable region files across runs
- Auto-recovery tracking: Successfully read regions removed from state
- State file location: Output folder (not world folder, to avoid modifying source)
- Updated dynamically as processing completes

## Architectural Decisions & Rationale

### Monolithic Design
- **Decision**: Single Main.groovy file instead of modular classes
- **Rationale**: Simplified deployment (single JAR), easier standalone distribution, clear linear flow for tool purpose
- **Trade-off**: Not modular, harder to unit test individual components
- **Future**: Consider refactoring if exceeds 2000 lines

### Static Field State Management
- **Decision**: Use static fields for global extraction state
- **Rationale**: Single-threaded application, avoids object threading complexity
- **Alternative Considered**: Object-based state with dependency injection
- **Constraint**: Intentional design choice for simplicity

### Streaming Output
- **Decision**: Write output incrementally as content extracted
- **Rationale**: Prevents memory exhaustion on large worlds
- **Memory Profile**: O(1) output buffering regardless of world size
- **Trade-off**: Cannot reorder output after extraction completes

### Format Version Fallback
- **Decision**: Try new format first, fallback to old format on exception
- **Rationale**: Supports multiple Minecraft versions with single codebase
- **Implementation**: Try-catch wrapper around format-specific parsing
- **Limitations**: Heuristic approach, not perfect for edge cases

### NBT Null-Safety Layer
- **Decision**: Helper methods for safe nested NBT access
- **Rationale**: NBT structures can have null/missing fields
- **Implementation**: Custom getters that return Optional or default values
- **Benefit**: Prevents NullPointerException crashes on corrupt data

### Multi-Version Command Generation
- **Decision**: Generate four separate mcfunction files for different Minecraft versions (1.13+, 1.14+, 1.20.5+, 1.21+)
- **Rationale**: Minecraft command syntax changed significantly across versions; single format won't work universally
- **Implementation**:
  - `Map<String, BufferedWriter> mcfunctionWriters` maintains separate writers for each version
  - `generateBookCommand(title, author, pages, version)` generates version-specific commands via switch statement
  - `escapeForMinecraftCommand(text, version)` handles version-specific escaping (1.13/1.14 use `\\n`, 1.20.5+/1.21 use `\n`)
  - All four files written simultaneously in single pass during extraction
- **Format Differences**:
  - **1.13**: `give @p written_book{title:"...",author:"...",pages:['{"text":"..."}']}`
  - **1.14**: `give @p written_book{title:"...",author:"...",pages:['["..."]']}`
  - **1.20.5**: `give @p written_book[minecraft:written_book_content={title:"...",author:"...",pages:["..."]}]`
  - **1.21**: `give @p written_book[written_book_content={title:"...",author:"...",pages:["..."]}]` (no `minecraft:` prefix)
- **Testing**: Three dedicated integration tests verify file creation, command count, and JSON structure validity for all versions
- **Attribution**: Implementation inspired by https://github.com/TheWilley/Text2Book and https://github.com/ADP424/MinecraftBookConverter

## File Structure & Organization

```
project-root/
├── src/
│   ├── main/groovy/
│   │   └── Main.groovy (1637 lines - all application logic)
│   ├── main/resources/
│   │   └── logback.xml (Logging configuration)
│   └── test/groovy/
│       └── ReadBooksIntegrationSpec.groovy (Spock tests)
├── src/test/resources/
│   └── 1_21_10-44-3/ (Real Minecraft world test data)
│       ├── region/ (Block containers)
│       ├── entities/ (Entity data)
│       ├── playerdata/ (Player inventory)
│       └── SHOULDBE.txt (Expected output reference)
├── build.gradle (Gradle build configuration)
├── gradle/ (Gradle wrapper)
├── .github/workflows/
│   └── ci.yml (GitHub Actions CI/CD)
└── .kilocode/rules/memory-bank/ (This Memory Bank)
```

## Dynamic Logback Configuration

**Feature**: Runtime log file setting via system property

**Implementation:**
- In Main.groovy: `System.setProperty("LOG_FILE", logFilePath)`
- In logback.xml: `<property name="LOG_FILE" value="${LOG_FILE}" />`
- Enables dynamic log redirection based on user input

**Benefit**: Users can specify log output location without configuration files

## Version Compatibility Strategy

### Supported Versions
- **Minecraft 1.18** - Original supported version
- **Minecraft 1.20** - Format compatible with 1.18
- **Minecraft 1.20.5+** - New NBT format structure

### Detection Logic
1. Read level.dat from world root
2. Extract format version from NBT data
3. Select appropriate parser based on version
4. Fallback to legacy parser if primary fails

### Known Format Variations
- Page storage key names differ between versions
- CompoundTag structure varies
- Some fields optional in newer versions

## Performance Characteristics

### Memory Usage
- **Recommended**: -Xmx10G for large worlds (100K+ blocks)
- **Minimum**: -Xmx2G for small worlds
- **Streaming**: Constant memory regardless of output size

### Processing Time
- Linear with number of region/entity files
- Single-threaded: ~100 files/second typical
- Bottleneck: NBT parsing and deduplication checks

### Optimization Opportunities
- Parallelization (currently single-threaded by design)
- Streaming NBT parsing (currently full structure load)
- Deduplication via bloom filter (currently hashCode set)

## Testing Architecture

### Integration Tests
**Location:** `src/test/groovy/ReadBooksIntegrationSpec.groovy`
**Framework:** Spock 2.3-groovy-4.0
**Test Data:** Real Minecraft world (1_21_10-44-3)

**Test World Contents:**
- 44 books across various containers
- 3 signs in world blocks
- Multiple container types (chests, minecarts, etc.)
- Naming convention: `worldname-BOOKCOUNT-SIGNCOUNT`

**Key Test Pattern:**
```groovy
Main.runExtraction()  // Direct call, avoids System.exit()
// Verify extracted books/signs match SHOULDBE.txt
```

**Test Timeout:** 10 minutes (allows for thorough execution)

## Deployment Architecture

### Build System: Gradle 8.14.2
**Key Build Tasks:**
- `build` - Compile and package
- `test` - Run integration tests
- `fatJar` - Create standalone JAR with all dependencies
- Auto-copy JAR to project root for distribution

### CI/CD: GitHub Actions
**Workflow:** `.github/workflows/ci.yml`
- **Trigger**: On push to main, PR creation
- **Steps**: Build → Test → Package JAR → Auto-commit to main
- **Artifacts**: JAR committed to repository for distribution

### Deployment Profile
- **Distribution**: GitHub releases + JAR in repository
- **Runtime**: `java -jar ReadSignsAndBooks.jar <world_path> [options]`
- **Dependencies**: Java 21 runtime required

## Architectural Constraints

1. **Single-threaded** - Sequential processing, not concurrent
2. **Java 21+ only** - Uses modern language features
3. **Large memory footprint** - 10GB JVM for large worlds
4. **NBT format dependency** - Tightly coupled to Querz library (6.1)
5. **Minecraft version tracking** - Must update parsing logic for new format versions
6. **Dual interface** - JavaFX GUI for double-click usage, CLI for automation (smart detection)
7. **Stateful processing** - Static fields maintain extraction state

## Future Architectural Considerations

- **Modularization**: Consider separating NBT parsing, container handling, output formatting into modules (currently ~2431 lines, refactoring deferred for simplicity)
- **Parallelization**: Process multiple region/entity files concurrently (requires thread-safe deduplication)
- **Streaming NBT**: Avoid full structure load for large containers
- **Plugin System**: Allow custom output format extensions
- **API Layer**: Expose extraction as programmatic API for library usage
