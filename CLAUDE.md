# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ReadSignsAndBooks.jar is a CLI tool that extracts written content (books and signs) from Minecraft world save files. It processes binary NBT format data across multiple Minecraft versions (1.18, 1.20, 1.20.5+) and outputs content in multiple formats (Stendhal JSON, CSV, plain text).

**Key Context:**
- Original code shared in 2020 by Matt (/u/worldseed) in r/MinecraftDataMining Discord
- All subsequent changes are "vibe coded" with AI assistance
- Monolithic design is **intentional** - single Main.groovy file for simplicity and ease of deployment
- Single-threaded processing is **intentional** - not a limitation to fix
- Static state management is **intentional** - simplifies single-threaded execution

## Build & Test Commands

### Essential Commands
```bash
# Build project (compile + package + test)
gradle build

# Run tests only (includes 10-minute integration test with real Minecraft world)
gradle test

# Run without tests
gradle build -x test

# Run application (requires world path)
gradle run --args="--world /path/to/minecraft/world"

# Create standalone JAR (auto-copied to project root)
gradle jar

# For large worlds, use custom JVM args
gradle run "-Dorg.gradle.jvmargs=-Xmx10G -XX:+UseG1GC -XX:MaxGCPauseMillis=200" --args="--world /path/to/world"
```

### Running the JAR Directly
```bash
# Standard usage
java -jar ReadSignsAndBooks.jar --world /path/to/world

# Large worlds (recommended)
java -Xmx10G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar ReadSignsAndBooks.jar --world /path/to/world

# With options
java -jar ReadSignsAndBooks.jar --world /path/to/world --output /custom/output --remove-formatting
```

### Testing
- Integration tests use real Minecraft world data: `src/test/resources/1_21_10-44-3/`
- Test world naming convention: `{version}-{bookcount}-{signcount}` (e.g., "1_21_10-44-3" = 44 books, 3 signs)
- Expected output reference: `src/test/resources/1_21_10-44-3/SHOULDBE.txt`
- Test timeout: 10 minutes (this is normal for thorough world processing)
- Framework: Spock 2.3-groovy-4.0 (BDD-style specifications)

## Architecture & Code Structure

### Monolithic Design Philosophy
**The entire application logic is in a single file: `src/main/groovy/Main.groovy` (~1600+ lines)**

This is **intentional, not a code smell**:
- Simplifies deployment as standalone JAR
- Clear linear flow for tool-specific purpose
- Avoids over-engineering for a focused utility
- Easy to distribute and understand as single artifact

**Do not refactor into modules unless:**
- File exceeds 2000 lines
- User explicitly requests modularization
- Adding genuinely new features that warrant separation

### Three-Phase Processing Pipeline

**Phase 1: Player Data** (`extractPlayerData()`)
- Processes `playerdata/*.dat` files
- Extracts books from player inventories and ender chests
- Each player file is independent NBT compound

**Phase 2: Region Files** (`extractRegionFiles()`)
- Scans `region/*.mca` files for block containers
- Processes 17+ container types (chests, shulkers, barrels, hoppers, etc.)
- Recursive container traversal via `processContainer()` for nested structures

**Phase 3: Entity Files** (`extractEntityFiles()`)
- Processes `entities/*.mca` files
- Extracts from minecarts, item frames, boats, end crystals, etc.
- Similar recursive container handling

### Critical Architectural Patterns

**Static State Management:**
```groovy
static Set<Integer> bookHashes = [] as Set  // Content-based deduplication
static Set<String> signHashes = [] as Set
static int bookCounter = 0
static Map<String, Integer> booksByContainerType = [:]
```
- All state is static fields
- Single-threaded execution model
- State reset at start of `runExtraction()`
- Do not attempt to make this thread-safe or object-oriented without explicit request

**Recursive Container Processing:**
- `processContainer(CompoundTag container, String locationType, String location)` is the core method
- Handles nested containers (shulkers in chests, bundles in shulkers, etc.)
- Recursively traverses container hierarchies
- 17+ container types supported

**Version Compatibility Fallback:**
- Try new format (1.20.5+) first
- Catch exceptions and fallback to old format (1.18/1.20)
- Heuristic approach, not version detection from level.dat
- Format differences in NBT key names and structure

**Content Deduplication:**
- Hash-based: Uses `hashCode()` of book pages array
- Not reference-based equality
- Duplicates saved to `.duplicates/` folder with location tracking
- Intentional design to preserve discovery locations

**Streaming Output:**
- Writes incrementally during extraction
- Prevents OOM on large worlds
- O(1) output buffer regardless of world size

## Critical Implementation Details

### NBT Null-Safety
- Always use safe navigation and null checks when accessing NBT tags
- NBT structures can have missing/null fields in corrupt or partial data
- Pattern: `tag?.get('key')?.asString() ?: defaultValue`

### Dynamic Logging Configuration
```groovy
System.setProperty('LOG_FILE', logFilePath)
reloadLogbackConfiguration()
```
- Sets log file location at runtime before processing
- Allows user-specified log output location
- Configured in `src/main/resources/logback.xml`

### Progress Tracking
- Uses `me.tongfei.progressbar` library
- Real-time file count updates during extraction
- Total files counted before processing starts
- User feedback for long-running operations

### Minecraft Format Variations
**Books (versions 1.18-1.20):**
- Stored in `tag.pages` as StringTag array
- Author in `tag.author`
- Title in `tag.title`

**Books (versions 1.20.5+):**
- Different NBT structure for pages
- JSON text components instead of plain strings
- Requires JSON parsing for formatted text

**Signs:**
- Each sign face has 4 text lines
- JSON text components with formatting
- Empty lines stored as '{"text":""}'

## Common Development Tasks

### Adding Support for New Container Type
1. Identify container's block ID in NBT (e.g., `minecraft:chest`)
2. Add to container type detection in `extractRegionFiles()` or `extractEntityFiles()`
3. Call `processContainer()` with appropriate NBT tag
4. Update `booksByContainerType` tracking
5. Add test case with sample container in test world

### Supporting New Minecraft Version
1. Test extraction with new version world
2. Check for NBT structure changes in books/signs
3. Update fallback parsing logic if needed
4. Add version-specific handling in try-catch blocks
5. Update test world to newer version if necessary
6. Document version compatibility in README

### Debugging Extraction Issues
1. Check logs in `ReadBooks/{date}/logs.txt`
2. Enable debug logging by modifying `logback.xml`
3. Add strategic `LOGGER.debug()` statements in Main.groovy
4. Run with small test world first
5. Verify NBT structure with external NBT viewer (e.g., NBTExplorer)

### Memory Optimization
- Default recommendation: `-Xmx10G`
- For ultra-large worlds: `-Xmx16G`
- If OOM occurs: Increase heap size, not code changes
- Memory profile is O(unique_books) for deduplication set
- No streaming NBT parsing (loads full structure per container)

## CI/CD & Deployment

### GitHub Actions Workflow
**File:** `.github/workflows/ci.yml`

**Jobs:**
1. `build-and-test` - Builds project and runs integration tests
2. `create-release` - Creates GitHub release with JAR artifact (master branch only)

**Automatic Release Process:**
- Every commit to master triggers release creation
- Tag format: `release-{short-sha}` (e.g., `release-b422d2b4`)
- Automatically keeps only 5 most recent releases
- JAR uploaded as release asset
- Uses `softprops/action-gh-release@v2` (actively maintained, 5.2k stars)
- Old release cleanup via `actions/github-script@v7`

**Download Link:**
- Latest release always available at: `https://github.com/{owner}/{repo}/releases/latest/download/ReadSignsAndBooks.jar`
- Never needs updating in documentation

### JAR Distribution
- JAR is **not committed to repository** (removed as of recent refactor)
- Releases are the sole distribution method
- Fat JAR includes all dependencies (~11MB)
- Main-Class manifest set to `Main`

## Key Dependencies

**Critical Libraries:**
- **Querz NBT 6.1** - NBT parsing (version pinned, do not change without testing)
- **Picocli 4.7.7** - CLI argument parsing
- **Groovy 4.0.24** - Language runtime
- **Spock 2.3-groovy-4.0** - Testing framework

**Version Constraints:**
- Java 21+ required (uses modern language features)
- Querz < 6.0 incompatible (NBT format changes)
- Groovy 4.0+ required for Spock compatibility

## Important Files & Locations

```
src/main/groovy/Main.groovy          # All application logic (1600+ lines)
src/main/resources/logback.xml       # Logging configuration (dynamic LOG_FILE property)
src/test/groovy/ReadBooksIntegrationSpec.groovy  # Spock integration tests
src/test/resources/1_21_10-44-3/     # Real Minecraft test world (44 books, 3 signs)
build.gradle                          # Dependencies, JAR configuration
.github/workflows/ci.yml              # CI/CD pipeline
.kilocode/rules/memory-bank/          # Detailed architecture documentation
```

## Development Principles

1. **Preserve monolithic structure** - Don't split Main.groovy unless file exceeds 2000 lines
2. **Maintain static state model** - Don't refactor to OOP without explicit request
3. **Keep single-threaded** - Don't add parallelization (intentional design)
4. **Format version fallback** - Always try new format first, catch and fallback
5. **Streaming output** - Write incrementally, never buffer entire output
6. **Real-world testing** - Always test with actual Minecraft world data
7. **Memory over speed** - Accept high memory usage for simplicity (user can allocate more)

## Working with Memory Bank

The `.kilocode/rules/memory-bank/` directory contains detailed documentation:
- `brief.md` - Project mission and use cases
- `architecture.md` - Deep technical architecture details
- `tech.md` - Technology stack and setup
- `product.md` - User context and problem statement

**Always consult these files when:**
- Making architectural decisions
- Understanding design rationale
- Adding major features
- Refactoring code structure

## Minecraft World Structure

**Required directories in world folder:**
- `region/` - Block containers (*.mca region files)
- `playerdata/` - Player inventory data (*.dat files)
- `entities/` - Entity data (*.mca files, Minecraft 1.17+)
- `level.dat` - World metadata (version detection)

**Output structure:**
```
ReadBooks/{YYYY-MM-DD}/
├── books/                    # Individual Stendhal JSON files
│   ├── Title_(3)_by_Author~location~coords.stendhal
│   └── .duplicates/          # Duplicate books with location tracking
├── all_books.txt             # Combined text with #region markers
├── all_signs.txt             # One sign per line with coordinates
├── all_books.csv             # CSV export with metadata
├── all_signs.csv             # CSV export with coordinates
├── summary.txt               # Processing statistics and breakdown
└── logs.txt                  # Debug logs
```
