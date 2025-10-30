# Vibed-ReadSignsAndBooks.jar

Full attribution go to the source code shared on 2020 by `Matt (/u/worldseed)` in the `r/MinecraftDataMining` 's Discord server.
All changes are just vibe coded with help of Claude 4.5.

## Features

This tool scans Minecraft world files and extracts:

- **Written books** (with title and author)
- **Writable books** (book and quill)
- **Signs** with text (all variants: regular, hanging, wall signs)
- **Books in containers:** chests, barrels, shulker boxes, bundles, item frames, minecarts, boats, and more
- **Books in containers of containers** (e.g., inside bundles in frames, inside shulker boxes in chests)
- **Books in player inventories** and ender chests
- **Duplicate tracking:** Saves duplicate books to `.duplicates/` folder instead of skipping them

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
    - `books/` - directory containing individual Stendhal format files for each unique book
        - Each book is saved as: `Title_(PageCount)_by_Author~location~coords.stendhal`
        - Example: `My_Book_(3)_by_Joe~minecraft_chest~-10_65_20.stendhal`
        - Stendhal format preserves Minecraft formatting codes (§ codes)
    - `books/.duplicates/` - directory containing duplicate books (same content, different locations)
    - `all_signs.txt` - all signs found in the world, one per line
        - Example: `Chunk [31, 31]	(-2 75 -5)		Line 1! ⚠ Line 2! ☀`
    - `all_books.txt` - all books in Stendhal format, separated by `#region` and `#endregion` markers for VSCode folding
    - `all_books.csv` - CSV export of all books with metadata
    - `all_signs.csv` - CSV export of all signs with metadata
    - `logs.txt` - program debug logs
    - `summary.txt`
        - Breakdown by container type (chests, shulker boxes, villagers, etc.)
        - Breakdown by location type (block entities, entities, players)
        - Processing time and performance metrics

## Testing

The integration test uses real minecraft world(s).

```bash
gradle test
```