# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md

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

### Testing Details
- Integration tests use real Minecraft world data: `src/test/resources/1_21_10-44-3/`
- Test world naming convention: `{version}-{bookcount}-{signcount}` (e.g., "1_21_10-44-3" = 44 books, 3 signs)
- Expected output reference: `src/test/resources/1_21_10-44-3/SHOULDBE.txt`
- Test timeout: 10 minutes (this is normal for thorough world processing)
- Framework: Spock 2.3-groovy-4.0 (BDD-style specifications)

## Critical Implementation Notes

### Intentional Design Decisions
The following are **intentional design choices**, not code smells to fix:

1. **Monolithic structure** - Single `Main.groovy` file (~1600+ lines)
   - Do not refactor into modules unless file exceeds 2000 lines or user explicitly requests it
   
2. **Static state management** - All application state in static fields
   - Do not attempt to make thread-safe or convert to OOP without explicit request
   
3. **Single-threaded processing** - Sequential file processing
   - Do not add parallelization (intentional simplicity)

### Key Architectural Patterns

**Three-Phase Processing:**
1. Player Data (`extractPlayerData()`) - Processes `playerdata/*.dat`
2. Region Files (`extractRegionFiles()`) - Scans `region/*.mca` for containers
3. Entity Files (`extractEntityFiles()`) - Processes `entities/*.mca`

**Recursive Container Processing:**
- Core method: `processContainer(CompoundTag container, String locationType, String location)`
- Handles 17+ container types with nested structures (shulkers in chests, bundles in shulkers, etc.)

**Version Compatibility Fallback:**
- Try new format (1.20.5+) first
- Catch exceptions and fallback to old format (1.18/1.20)
- Format differences handled via try-catch, not version detection

**Content Deduplication:**
- Hash-based using `hashCode()` of book pages array
- Duplicates saved to `.duplicates/` folder with location tracking

### NBT Null-Safety Pattern
Always use safe navigation when accessing NBT tags:
```groovy
tag?.get('key')?.asString() ?: defaultValue
```

## CI/CD & Releases

### GitHub Actions Workflow
- **File:** `.github/workflows/ci.yml`
- **Triggers:** Every commit to master
- **Jobs:**
  1. `build-and-test` - Builds and runs integration tests
  2. `create-release` - Creates GitHub release with JAR (master only)

### Automatic Releases
- Tag format: `release-{short-sha}` (e.g., `release-b422d2b4`)
- Keeps only 5 most recent releases (older ones auto-deleted)
- JAR uploaded as release asset (~11MB fat JAR)
- Uses `softprops/action-gh-release@v2` + `actions/github-script@v7`

### Distribution
- JAR is **not committed to repository** (releases only)
- Latest download link: `https://github.com/{owner}/{repo}/releases/latest/download/ReadSignsAndBooks.jar`
- This link never needs updating in documentation

## Common Development Tasks

### Adding New Container Type Support
1. Identify container's block ID in NBT (e.g., `minecraft:chest`)
2. Add to container type detection in `extractRegionFiles()` or `extractEntityFiles()`
3. Call `processContainer()` with appropriate NBT tag
4. Update `booksByContainerType` tracking
5. Add test case with sample container

### Supporting New Minecraft Version
1. Test extraction with new version world
2. Check for NBT structure changes in books/signs
3. Update fallback parsing logic if needed
4. Add version-specific handling in try-catch blocks
5. Update test world if necessary
6. Document version compatibility in README

### Memory Optimization
- Default: `-Xmx10G` for large worlds
- Ultra-large: `-Xmx16G+`
- If OOM occurs: Increase heap size, not code changes
- Memory profile is O(unique_books) for deduplication

## Key Files

```
src/main/groovy/Main.groovy                      # All application logic (1600+ lines)
src/main/resources/logback.xml                   # Logging config (dynamic LOG_FILE)
src/test/groovy/ReadBooksIntegrationSpec.groovy  # Spock integration tests
src/test/resources/1_21_10-44-3/                 # Real Minecraft test world
build.gradle                                      # Dependencies, JAR config
.github/workflows/ci.yml                          # CI/CD pipeline
```

## Critical Dependencies

- **Querz NBT 6.1** - NBT parsing (version pinned, do not change without testing)
- **Picocli 4.7.7** - CLI argument parsing
- **Groovy 4.0.24** - Language runtime
- **Spock 2.3-groovy-4.0** - Testing framework
- **Java 21+** - Required (uses modern language features)
