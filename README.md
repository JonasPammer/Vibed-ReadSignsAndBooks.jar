# Vibed-ReadSignsAndBooks.jar

Full attribution go to the source code shared on 2020 by `Matt (/u/worldseed)` in the `r/MinecraftDataMining` 's Discord server.
All changes are just vibe coded with help of Claude 4.5.

## Download

**[Download Latest ReadSignsAndBooks.jar](https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/releases/latest/download/ReadSignsAndBooks.jar)**

## Features

This tool scans Minecraft world files and extracts:

- **Written books** (with title and author)
- **Writable books** (book and quill)
- **Signs** with text (all variants: regular, hanging, wall signs)
- **Books in containers:** chests, barrels, shulker boxes, bundles, item frames, minecarts, boats, and more
- **Books in containers of containers** (e.g., inside bundles in frames, inside shulker boxes in chests)
- **Books in player inventories** and ender chests
- **Duplicate tracking:** Saves duplicate books to `.duplicates/` folder instead of skipping them

## Usage

### Quick Start (The Easy Way)

1. **[Download ReadSignsAndBooks.jar](https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/releases/latest/download/ReadSignsAndBooks.jar)**

2. **Find your Minecraft world folder:**
   - Windows: `%appdata%\.minecraft\saves\YourWorldName`
   - Mac: `~/Library/Application Support/minecraft/saves/YourWorldName`
   - Linux: `~/.minecraft/saves/YourWorldName`

3. **Run the tool:**
   ```bash
   java -jar ReadSignsAndBooks.jar --world "C:\Users\YourName\AppData\Roaming\.minecraft\saves\YourWorldName"
   ```
   
   Or simply drag and drop your world folder into the same directory as the JAR and run:
   ```bash
   java -jar ReadSignsAndBooks.jar --world YourWorldName
   ```

4. **Find your results** in the newly created `ReadBooks/` folder!

### What You'll Get

The tool creates output in `ReadBooks/YYYY-MM-DD/`:
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

## Development

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

### Testing

The integration test uses real minecraft world(s).

```bash
gradle test
```
