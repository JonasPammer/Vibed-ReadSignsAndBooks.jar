# Technical Architecture

## System Overview
ReadSignsAndBooks.jar is a single-threaded CLI application that processes Minecraft world saves to extract written content (books and signs). The system operates in three distinct phases:

1. **Player Data Phase** - Extract books/signed books from player inventories
2. **Region Files Phase** - Scan block containers within world region files
3. **Entity Files Phase** - Extract books from entity data (minecarts, end crystals)

All extracted content is deduplicated by content hash and output to user-selected format.

## Core Components

### Main.groovy (1637 lines)
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
4. **Minecraft Datapacks** - Four complete, ready-to-use datapacks:
   - `readbooks_datapack_1_13/` - Minecraft 1.13-1.14.3 (pack_format 4)
   - `readbooks_datapack_1_14/` - Minecraft 1.14.4-1.19.4 (pack_format 4)
   - `readbooks_datapack_1_20_5/` - Minecraft 1.20.5-1.20.6 (pack_format 41)
   - `readbooks_datapack_1_21/` - Minecraft 1.21+ (pack_format 48)
   - Each datapack contains:
     - `pack.mcmeta` with appropriate pack_format
     - `data/readbooks/function/books.mcfunction` - Give commands for all books
     - `data/readbooks/function/signs.mcfunction` - Setblock commands for all signs

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

### Datapack Structure Generation
- **Decision**: Generate complete, ready-to-use Minecraft datapacks instead of standalone mcfunction files
- **Rationale**: Users can directly copy datapack folders into their Minecraft world without manual file organization
- **Implementation**:
    - `Main.DATAPACK_VERSIONS` is a single immutable list (`['1_13', '1_14', '1_20_5', '1_21']`) that drives every datapack loop. Adding a new datapack requires updating this constant, docs, and tests—no hidden arrays remain.
    - `createDatapackStructure(version)` + `getFunctionDirectoryName(version)` enforce the plural/singular `function(s)/` rule from @.kilocode/rules/memory-bank/minecraft-datapacks.md.
    - `createPackMcmeta(version, packFormat, description)` writes JSON with the correct `pack_format` and descriptive text so users (and CI) can verify they picked the right folder without opening Minecraft.
    - `getPackFormat(version)` maps version identifiers to official Minecraft pack_format numbers (4, 41, 48).
    - `getVersionDescription(version)` provides human-readable version ranges for pack.mcmeta descriptions and is also surfaced in README instructions.
- **CRITICAL: Directory Naming Change in 1.21**:
  - **Pre-1.21 (1.13-1.20.6)**: Uses `functions/` directory (PLURAL)
  - **1.21+**: Uses `function/` directory (SINGULAR)
  - This changed in Java Edition 1.21 snapshot 24w21a
  - Code automatically selects correct directory name based on version
- **Directory Structure (Pre-1.21)**:
  ```
  readbooks_datapack_VERSION/
  ├── pack.mcmeta (with pack_format: 4 or 41)
  └── data/
      └── readbooks/
          └── functions/  ← PLURAL
              ├── books.mcfunction (give commands)
              └── signs.mcfunction (setblock commands)
  ```
- **Directory Structure (1.21+)**:
  ```
  readbooks_datapack_1_21/
  ├── pack.mcmeta (with pack_format: 48)
  └── data/
      └── readbooks/
          └── function/  ← SINGULAR
              ├── books.mcfunction (give commands)
              └── signs.mcfunction (setblock commands)
  ```
- **Pack Format Mapping**:
  - 1.13-1.14.4: pack_format 4 (functions/ directory)
  - 1.20.5-1.20.6: pack_format 41 (functions/ directory)
  - 1.21+: pack_format 48 (function/ directory)
- **User Experience**: Copy entire folder to `world/datapacks/`, run `/reload`, then `/function readbooks:books` or `/function readbooks:signs`
- **Testing**: `ReadBooksIntegrationSpec` now asserts there are *exactly* four datapack folders, verifies `pack.mcmeta` `pack_format` + description strings, and inspects both `books.mcfunction` and `signs.mcfunction` contents for every supported version. These tests catch regressions such as extra folders or incorrect directory naming before release.

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
6. **CLI input only** - No GUI, API, or programmatic interface
7. **Stateful processing** - Static fields maintain extraction state

## Future Architectural Considerations

- **Modularization**: Separate NBT parsing, container handling, output formatting into modules if monolith exceeds 2000 lines
- **Parallelization**: Process multiple region/entity files concurrently (requires thread-safe deduplication)
- **Streaming NBT**: Avoid full structure load for large containers
- **Plugin System**: Allow custom output format extensions
- **API Layer**: Expose extraction as programmatic API for library usage
