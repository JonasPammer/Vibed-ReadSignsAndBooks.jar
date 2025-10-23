# Read Books Executable

A Gradle-based Java tool that reads Minecraft books and signs from world region files and player data.

## What This Does

This tool scans Minecraft world files and extracts:

- **Written books** (with title and author)
- **Writable books** (book and quill)
- **Signs** with text (all variants: regular, hanging, wall signs)
- **Books in containers:** chests, barrels, shulker boxes, bundles, item frames, minecarts, boats, and more
- **Books in containers of containers** (e.g., inside bundles in frames, inside shulker boxes in chests)
- **Books in player inventories** and ender chests
- **Books in mob inventories:** villagers, zombies, skeletons, piglins, and other mobs that can hold items

### Key Features

- **Comprehensive container support:** Detects books in 24+ container types including nested containers (bundles in
  chests, etc.)
- **Mob inventory support:** Extracts books from villagers, zombies, skeletons, piglins, and other mobs with inventories
- **Duplicate tracking:** Saves duplicate books to `.duplicates/` folder instead of skipping them
- **Sign location tracking:** Counts all physical signs by location, not just unique text content
- **Summary statistics:** Detailed breakdown of books by container type and location, with performance metrics
- **Command-line arguments:** Customize world directory, output directory, and enable/disable specific extraction types
- **Version compatibility:** Supports Minecraft 1.13+ through 1.21+ (including 1.20.5+ item components)
- **Detailed logging:** DEBUG-level logs show every block entity, sign, and book found

## Running

Although the jar is also committed, here's how to build from source
(if gradle is installed, otherwise use gradlew.bat/gradlew):

```cmd
gradle build
```

Run directly with Gradle:

```bash
gradle run --args="--world /path/to/world"
```

To use custom JVM arguments for large worlds:

```bash
gradle run "-Dorg.gradle.jvmargs=-Xmx10G -XX:+UseG1GC -XX:MaxGCPauseMillis=200" --args="--world /path/to/world"
```

Or with built jar:

```bash
java -Xmx10G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar ReadSignsAndBooks.jar --world /path/to/world
```

## Usage

1. Ensure you have the following folders in the executable's directory (or specify with `--world`):
    - `region/` - containing your Minecraft world region files (*.mca or *.mcr)
    - `playerdata/` - containing player data files (*.dat)
    - `entities/` - containing entity files (*.mca) for Minecraft 1.17+

2. Run the application using one of the methods above

3. The tool will create output in `ReadBooks/YYYY-MM-DD/` (or custom path with `--output`):
    - `books/` - directory containing individual text files for each unique book
        - Each book is saved as: `NNN_written_title_by_author.txt` or `NNN_writable_book.txt`
        - Books are numbered sequentially (001, 002, 003, etc.)
    - `books/.duplicates/` - directory containing duplicate books (same content, different locations)
        - Uses the same naming convention as the main books folder
        - Allows you to see all instances of the same book across your world
    - `signs.txt` - all signs found in the world
        - Organized by region file
        - Shows chunk coordinates, block coordinates, and sign text
        - Supports all sign variants (oak, birch, spruce, etc.) and hanging signs
        - **Signs are counted by location:** Multiple signs with identical text are listed separately
        - Example: 3 signs with same text at different coordinates = 3 entries in output
    - `logs.txt` - program debug logs
    - `summary.txt`
        - Shows total books/signs found
        - Breakdown by container type (chests, shulker boxes, villagers, etc.)
        - Breakdown by location type (block entities, entities, players)
        - Processing time and performance metrics

## Testing

Integration tests are available using Spock Framework:

```bash
gradle test
```

### Test World Setup

To add test worlds: Place Minecraft world in `src/test/resources/WORLDNAME-BOOKCOUNT-SIGNCOUNT/`

- Example: `src/test/resources/1_21_10-44-3/` (Minecraft 1.21.10 world with 44 books and 3 signs)
- `WORLDNAME`: Any descriptive name (e.g., `1_21_10`, `vanilla_1_18`, `modded_world`)
- `BOOKCOUNT`: Total number of books including duplicates (main folder + .duplicates folder)
- `SIGNCOUNT`: Total number of physical signs (counted by location, not unique text)

**Note:** Signs with identical text but different locations are counted separately.

### Test Output Locations

When tests run, they create directories and output files in the project's `build/` folder (gitignored):

**Test World Directories:**

- Location: `build/test-worlds/<worldname>/`
- Structure: Each test world is copied here with its output
- Cleanup: **NOT automatically deleted** - persists for inspection
- Example: `build/test-worlds/1_21_10-44-3/`

**Test Report:**  `build/reports/tests/test/index.html`
