# Technology Stack & Setup

## Language & Runtime
- **Language:** Groovy 4.0.24
- **JVM Version:** Java 21 (required, not backward compatible)
- **Build System:** Gradle 8.14.2
- **Gradle Wrapper:** Included in repository

## Build-Time Plugins
- **JavaFX Plugin** 0.1.0 - Manages JavaFX dependencies and modules
- **Badass Runtime Plugin** 1.13.1 - jpackage wrapper for native distribution
- **Dependency License Report** 3.0.1 - Auto-generates third-party license reports
  - Plugin: `com.github.jk1.dependency-license-report`
  - Outputs to: `src/main/resources/licenses/THIRD-PARTY-LICENSES.txt`
  - Runs automatically before `processResources` task
  - Generates human-readable text report of all runtime dependency licenses
  - Report embedded in JAR for GUI display via Help menu

## Core Dependencies

### NBT Processing
- **Querz NBT** 6.1 - Binary NBT format parsing and traversal
  - Handles Minecraft world data structures
  - Supports both old and new NBT formats
  - Used for reading leveldata and container items

### CLI Framework
- **Picocli** 4.7.7 - Command-line interface generation
  - Declarative CLI argument parsing
  - Auto-generated help messages
  - Integrated with Main.groovy entry point

### Logging
- **SLF4J** 2.0.16 - Logging facade
- **Logback** 1.5.12 - Logging implementation
  - Configured in `src/main/resources/logback.xml`
  - Runtime configuration via LOG_FILE system property
  - Dynamic log file redirection support

### Utilities
- **Tongfei ProgressBar** - Progress tracking during extraction
  - Real-time file processing display
  - Provides user feedback on long-running operations
- **Freva ASCII Table** - Tabular data formatting
- **Apache Commons IO** 2.15.1 - File utilities
- **Apache Commons Lang3** 3.14.0 - General utilities

### Testing
- **Spock** 2.3-groovy-4.0 - Groovy testing framework
  - BDD-style test specifications
  - Used for integration testing
  - Real Minecraft world test data included

## Development Environment Setup

### Prerequisites
- Java Development Kit 21 or later
- Git (for version control)
- **SDKMAN** recommended for Java version management (https://sdkman.io)
- No additional tools required (Gradle wrapper handles builds)

### IMPORTANT: Java Version Management
**This project requires Java 21.** If using SDKMAN, always prefix gradle/java commands with `sdk env;` to ensure correct Java version:

```bash
# CORRECT: Use sdk env to load .sdkmanrc configuration
sdk env; ./gradlew build
sdk env; ./gradlew test
sdk env; java -jar ReadSignsAndBooks.jar

# WRONG: Running without sdk env may use wrong Java version
./gradlew build  # ❌ May fail with UnsupportedClassVersionError
```

**Why**: The project uses JavaFX 21 which requires Java 21 class files (version 65.0). Running with older Java versions will fail with `UnsupportedClassVersionError`.

**Setup**:
```bash
# Install Java 21 via SDKMAN
sdk install java 21.0.9-tem

# Use Java 21 for current shell
sdk use java 21.0.9-tem

# Or always use sdk env; prefix
sdk env; ./gradlew build
```

### Local Development
```bash
# Clone repository
git clone <repo-url>
cd Vibed-ReadSignsAndBooks.jar

# Verify Java version (should be 21+)
sdk env; java -version

# Build project
sdk env; ./gradlew build

# Run tests
sdk env; ./gradlew test

# Create executable JAR
sdk env; ./gradlew fatJar

# Run application
sdk env; java -Xmx10G -jar ReadSignsAndBooks.jar /path/to/minecraft/world
```

## Build System Configuration

### Gradle Files
- **build.gradle** - Main build configuration
  - Dependency declarations
  - Task definitions (build, test, fatJar, createWindowsZip)
  - JAR manifest configuration
  - jpackage configuration for Windows distribution
- **settings.gradle** - Project settings
- **gradle/wrapper/** - Gradle wrapper for reproducible builds

### Key Build Tasks
- `./gradlew build` - Compile, package, and run tests
- `./gradlew test` - Run integration tests only
- `./gradlew fatJar` - Create standalone JAR with all dependencies
- `./gradlew jpackageImage` - Create native distribution with bundled JRE
- `./gradlew createWindowsZip` - Package Windows distribution as ZIP
- `./gradlew clean` - Remove build artifacts
- `./gradlew dependencies` - Show dependency tree

### JAR Configuration
- **Manifest:** Main-Class set to Main class entry point
- **Fat JAR:** All dependencies included in single JAR
- **Auto-location:** JAR auto-copied to project root after build
- **Size:** Approximately 15-20MB with all dependencies

### Windows Distribution Configuration
**Location:** `build.gradle` lines 173-223

```gradle
runtime {
    options = ['--strip-debug', '--compress', '2', '--no-header-files', '--no-man-pages']
    
    modules = [
        'java.base', 'java.desktop', 'java.logging', 'java.management',
        'java.naming', 'java.prefs', 'java.sql', 'java.xml', 'jdk.unsupported'
    ]
    
    jpackage {
        imageName = 'ReadSignsAndBooks'
        imageOptions = ['--icon', 'src/main/resources/icons/icon.ico']
        
        jvmArgs = [
            '-Xmx10G',
            '-XX:+UseG1GC',
            '-XX:MaxGCPauseMillis=200'
        ]
    }
}

task createWindowsZip {
    dependsOn 'jpackageImage'
    description = 'Creates Windows distribution ZIP for releases'
    group = 'distribution'
    
    doLast {
        def jpackageDir = file("$buildDir/build/jpackage/ReadSignsAndBooks")
        def zipFile = file("ReadSignsAndBooks-Windows.zip")
        
        ant.zip(destfile: zipFile.absolutePath, basedir: jpackageDir.absolutePath)
    }
}
```

**Distribution Contents:**
- `ReadSignsAndBooks.exe` - Launcher executable with custom icon
- `app/` - Application JAR and dependencies
- `runtime/` - Bundled Java 21 runtime environment

**User Experience:** Extract ZIP → Double-click EXE (no Java installation required)

## Runtime Configuration

### Memory Requirements
- **Minimum:** -Xmx2G (small worlds)
- **Recommended:** -Xmx10G (large worlds, 100K+ blocks)
- **Ultra-large:** -Xmx16G+ (massive server worlds)

### JVM Arguments Example
```bash
java -Xmx10G -jar ReadSignsAndBooks.jar /path/to/world --output-format stendhal
```

### Logging Configuration
- **Default:** Logs to console + `logs/readbooks.log`
- **Dynamic:** Set via `-DLOG_FILE=/path/to/logfile.log`
- **Format:** Timestamp | Level | Thread | Logger | Message

### CLI Arguments
- `<world>` - Required: Path to Minecraft world save directory
- `--output-format` - Output format (stendhal, csv, text)
- `--output-file` - Output file path (defaults to stdout)
- `--log-file` - Custom log file location

## Technical Constraints & Requirements

### Java/JVM Constraints
- **Java 21+ only** - Uses Record types, sealed classes, modern API features
- **No Java 8/11 support** - Not backward compatible
- **Large heap allocation** - Requires OS support for -Xmx10G

### NBT Format Constraints
- Depends on Querz 6.1 exact version for compatibility
- Format changes may require version updates
- Minecraft version detection relies on heuristic parsing

### Minecraft Version Support
- **Minimum:** Minecraft 1.18 (oldest supported)
- **Current:** 1.18, 1.20, 1.20.5, 1.21
- **Future:** Requires parsing updates for new major versions

### Minecraft Datapack pack_format Quick Reference

**CRITICAL**: pack_format numbers must match Minecraft version for datapacks to load properly.

| pack_format | Minecraft Versions | Directory Name | Our Datapacks |
|-------------|-------------------|----------------|---------------|
| 4 | 1.13 – 1.14.4 | `functions/` | ✅ readbooks_datapack_1_13, readbooks_datapack_1_14 |
| 5 | 1.15 – 1.16.1 | `functions/` | ❌ Not generated |
| 6-40 | 1.16.2 – 1.20.4 | `functions/` | ❌ Not generated |
| 41 | 1.20.5 – 1.20.6 | `functions/` | ✅ readbooks_datapack_1_20_5 |
| 42-47 | Snapshots/Pre-releases | `function/` or `functions/` | ❌ Not generated |
| 48 | 1.21 – 1.21.1 | `function/` | ✅ readbooks_datapack_1_21 |

**Directory Naming Change**: Minecraft 1.21 snapshot 24w21a changed directory names from plural (`functions/`) to singular (`function/`). See @.kilocode/rules/memory-bank/minecraft-datapacks.md for complete table and details.

**Implementation**: `getPackFormat(version)` method maps version strings to pack_format numbers. `setupDatapackStructure(version)` uses version-specific directory naming.

**Source**: https://minecraft.wiki/w/Pack_format (verified 2025-11-18)

### File System Constraints
- Cannot process encrypted/compressed world saves
- Requires read access to playerdata/, region/, entities/ folders
- World must be validly structured (level.dat required)

### Processing Constraints
- **Single-threaded** - No parallelization (intentional)
- **Sequential processing** - Process one file at a time
- **In-memory deduplication** - HashSet of extracted content
- **No streaming NBT** - Full structure load per container

## Performance Tuning

### Optimization Options
- Increase `-Xmx` if OOM errors occur
- Use SSD for world data (faster NBT parsing)
- Redirect logs to faster storage
- Disable progress bar with `--quiet` flag (if implemented)

### Known Performance Characteristics
- Processing speed: ~100 files/second (region/entity files)
- Memory peak: Variable with world size, typically 2-8GB
- Deduplication overhead: Linear with unique content count
- Output writing: Streaming (constant buffer size)

## GitHub Actions CI/CD

### Workflow Configuration
**Location:** `.github/workflows/ci.yml`

### Pipeline Steps
1. **Build and Test** (Ubuntu) - Compile with Gradle, run integration tests
2. **Build Windows ZIP** (Windows) - Create jpackage distribution and ZIP
3. **Create Release** - Upload both JAR and Windows ZIP to GitHub releases

### Dual Distribution System
**JAR Distribution:**
- Cross-platform compatibility
- Requires Java 21+ installation
- Smaller download (~15-20MB)

**Windows ZIP Distribution:**
- Windows-only (EXE + bundled JRE)
- No Java installation required
- Larger download (~150MB with JRE)
- Industry-standard approach (used by IntelliJ IDEA, NetBeans)

### Triggers
- Push to main branch
- Pull requests to main branch

### Artifacts
- JAR auto-committed to repository
- Windows ZIP created for releases
- Both available in GitHub releases section

## Dependency Version Management

### Version Pinning Strategy
- All versions explicitly specified in build.gradle
- No dynamic version ranges (e.g., no "1.0+")
- Ensures reproducible builds across environments

### Dependency Update Process
1. Check for compatible updates
2. Test locally with new versions
3. Update build.gradle
4. Run full test suite
5. Commit version update

### Known Incompatibilities
- Querz < 6.0 - Incompatible NBT format
- Java < 21 - Uses modern language features
- Spock < 2.3 - Incompatible with Groovy 4.0

## Testing Framework

### Test Configuration
- **Framework:** Spock 2.3-groovy-4.0
- **Location:** `src/test/groovy/ReadBooksIntegrationSpec.groovy`
- **Test Data:** Real Minecraft world (1_21_10-44-3)
- **Timeout:** 10 minutes per test suite

### Test World Specifications
- Format: Minecraft 1.21.10
- Location: `src/test/resources/1_21_10-44-3/`
- Contents: 44 books, 3 signs
- Naming: `{version}-{bookcount}-{signcount}`

### Test Execution
```bash
./gradlew test
```

### Expected Output Reference
- File: `src/test/resources/1_21_10-44-3/SHOULDBE.txt`
- Format: Expected extraction output for validation

## Documentation & Resources

### Project Documentation
- **README.adoc** - Usage guide and overview
- **Architecture.md** - System design and decisions
- **This file** - Technical setup and constraints

### External Resources
- Querz Library: NBT parsing documentation
- Picocli: CLI framework documentation
- Groovy: Groovy language documentation
- Spock: Testing framework documentation

## Common Development Tasks

### Adding New Container Type Support
1. Identify container's block ID or entity type
2. Add to container type detection in Main.groovy
3. Update recursive container processing if nesting supported
4. Add test case with sample data
5. Update documentation

### Supporting New Minecraft Version
1. Extract format version from level.dat
2. Analyze NBT structure changes
3. Update version detection logic
4. Add fallback parsing if needed
5. Test with real world data from new version
6. Update documentation with version number

### Performance Debugging
1. Profile with `-XX:+FlightRecorder` JVM flags
2. Monitor memory with JProfiler or JMeter
3. Profile NBT parsing bottleneck
4. Optimize hot paths with benchmarking
5. Update memory recommendations if needed

## Deployment Considerations

### Distribution Methods
- **GitHub Releases** - Automated dual-asset releases (JAR + Windows ZIP)
- **JAR Distribution** - Cross-platform, requires Java 21+
- **Windows ZIP Distribution** - Self-contained with bundled JRE
- **Repository** - Latest JAR committed to main branch (deprecated, use releases)
- **Maven Central** - Not currently published

### End-User Requirements

**JAR Distribution:**
- Java 21 runtime (download from adoptopenjdk.net or oracle.com)
- 10GB minimum available RAM (for large worlds)
- Read access to Minecraft world directory

**Windows ZIP Distribution:**
- Windows operating system
- No Java installation required (bundled)
- 10GB minimum available RAM (for large worlds)
- Read access to Minecraft world directory

### Distribution Checklist
- [ ] Build succeeds locally
- [ ] All tests pass (10-minute runtime acceptable)
- [ ] JAR created and tested
- [ ] Windows ZIP created and tested
- [ ] Version number updated in build.gradle
- [ ] Commit to main triggers CI/CD
- [ ] GitHub Actions workflow completes successfully
- [ ] Both JAR and Windows ZIP uploaded to releases