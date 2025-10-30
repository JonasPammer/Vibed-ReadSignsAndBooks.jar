# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Project Structure (Non-Obvious)
- **Project root is `Read Books Executable/`** - not the repository root
- Built JAR auto-copies to project root via [`build.gradle`](build.gradle:109-115) `jar.doLast` task
- Tests output to `build/test-worlds/` (gitignored) instead of system temp for inspection

## Build Commands
```bash
# From "Read Books Executable/" directory
gradle build          # Creates fat JAR with all dependencies
gradle test          # Runs Spock tests with real Minecraft world data
gradle run --args="--world /path/to/world"  # Run with custom world path
```

## Test-Specific Requirements
- Test worlds MUST be named: `WORLDNAME-BOOKCOUNT-SIGNCOUNT` in [`src/test/resources/`](src/test/resources/)
- Example: `1_21_10-44-3` = world with 44 books and 3 signs
- Tests manipulate `System.getProperty('user.dir')` to simulate working directory changes
- [`Main.runExtraction()`](src/main/groovy/Main.groovy:79) resets all static state - safe for multiple test calls
- Test discovery scans for folders matching `-\d+-\d+$` pattern

## Runtime Requirements (Critical)
- Application expects world folders relative to `baseDirectory` (defaults to `user.dir`):
  - `region/` - required (region files)
  - `playerdata/` - optional (player inventories)
  - `entities/` - optional (1.17+ only)
- Logback config reloaded at runtime via [`reloadLogbackConfiguration()`](src/main/groovy/Main.groovy:131-141) using `LOG_FILE` system property
- Fat JAR requires `-Xmx10G` for large worlds (see [`build.gradle`](build.gradle:83-87))

## Code Conventions (Non-Standard)
- Groovy linting disabled for most rules (see [`.groovylintrc.json`](.groovylintrc.json)) - minimal enforcement
- Static fields used extensively for state management in [`Main`](src/main/groovy/Main.groovy) class
- NBT helper methods in [`Main`](src/main/groovy/Main.groovy:1440-1635) wrap Querz library with null-safe defaults
- Progress bars use ASCII style (not Unicode) for Windows compatibility

## Testing Gotchas
- Spock tests use `gradle test` (JUnit Platform runner)
- Test worlds must contain actual Minecraft NBT/MCA files
- Book count includes duplicates (saved to `.duplicates/` folder)
- Sign count is by location, not unique text content