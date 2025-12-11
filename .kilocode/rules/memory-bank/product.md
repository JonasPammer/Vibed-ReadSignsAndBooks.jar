# Product & User Context

## Problem Statement
Minecraft world saves contain rich player-created content (written books and signs) that is difficult to access and preserve. These written items are stored in binary NBT format across multiple region and entity files, making manual extraction impractical. Server administrators face the challenge of:
- Losing important player lore and documentation during world resets
- Inability to backup written content for archival purposes
- Lack of tools to analyze or convert Minecraft text to accessible formats
- Complexity of handling multi-version world data (1.18, 1.20, 1.20.5+)

## Target Users
1. **Server Administrators** - Need to archive worlds before reset/updates
2. **Community Archivists** - Preserve server history and player-created lore
3. **Modpack Creators** - Extract curated books/signs for distribution
4. **Data Analysts** - Study player behavior and content patterns
5. **Content Preservation Specialists** - Maintain digital archives of gaming communities

## How the Product Solves the Problem
ReadSignsAndBooks.jar provides a straightforward CLI tool that:
- Scans a Minecraft world directory and extracts all book and sign content automatically
- Supports multiple output formats (Stendhal JSON, CSV, combined text) for different use cases
- Handles nested containers (chests within shulkers, minecarts with contents, etc.) transparently
- Works across Minecraft versions 1.18, 1.20, and 1.20.5+ automatically
- Processes large worlds efficiently without memory exhaustion
- Deduplicates identical book content (same text = same entry, regardless of copies)
- Provides clear progress feedback during extraction

## Key Features & Capabilities

### Input Processing
- Reads Minecraft world save directories (.minecraft/saves/worldname)
- Supports player inventory data (playerdata/*.dat files)
- Scans region files for block containers
- Extracts entity data (minecarts, end crystals with books)
- Handles all Minecraft container types (chests, barrels, shulkers, bundles, hoppers, dispensers, droppers, furnaces, brewing stands, smokers, blast furnaces, etc.)

### Output Formats
- **Stendhal JSON** - Structured format preserving all metadata (author, title, pages)
- **CSV** - Tabular format for spreadsheet analysis
- **Combined Text** - Plain text with book/sign separation for quick reading
- **Minecraft Commands - Books** - Four version-specific mcfunction files (1.13+, 1.14+, 1.20.5+, 1.21+) containing `/give` commands to recreate all books in-game
  - Each command includes title, author, and all pages with proper escaping
  - Can be used in command blocks, datapacks, or direct execution
  - Preserves formatting codes and Unicode characters
- **Minecraft Commands - Signs** - Five version-specific mcfunction files (1.13+, 1.14+, 1.20+, 1.20.5+, 1.21+) containing `/setblock` commands to place all signs
  - **Interactive clickable signs**: Click first line → displays original world coordinates → click coordinates to teleport
  - Preserves all four lines of text with proper escaping
  - Signs placed in organized grid pattern with relative coordinates
  - Deduplicates identical signs (same text stacked vertically)
- **Custom Names CSV** - Tabular format with named items/entities and their coordinates
- **Custom Names TXT** - Human-readable grouped report organized by item/entity type
- **Custom Names JSON** - Structured format with full metadata for programmatic processing

### Content Processing
- NBT binary format parsing and traversal
- Recursive container nesting (searches inside containers within containers)
- Multi-version format compatibility (automatic detection and adaptation)
- Content-based deduplication (hashCode of pages, not reference equality)
- UTF-8 text encoding with special character support
- Custom name extraction from items and entities with coordinate tracking

## User Experience Goals
1. **Simplicity** - Single command execution: `java -jar ReadSignsAndBooks.jar /path/to/world`
2. **Transparency** - Progress bar showing files processed, clear error messages
3. **Reliability** - Handles corrupt data gracefully, doesn't crash on edge cases
4. **Speed** - Can process large worlds (thousands of blocks/entities) in reasonable time
5. **Flexibility** - Multiple output formats for different user needs

## CLI Arguments
- `<world>` - Required: Path to Minecraft world save directory
- `--output-format` - Output format (stendhal, csv, text)
- `--output-file` - Output file path (defaults to stdout)
- `--log-file` - Custom log file location
- `--extract-custom-names` - Enable custom name extraction from items and entities
- `--index-limit N` - Maximum blocks per type to store in index (default: 5000, 0 for unlimited)
- `--index-query BLOCK_TYPE` - Query existing database for block locations (e.g., "nether_portal")
- `--index-list` - Display summary of all indexed block types from existing database
- `--index-dimension` - Filter query results by dimension (overworld, nether, end)

## Success Metrics
- Extraction accuracy: 100% of accessible books/signs extracted
- Content preservation: All text, formatting, and metadata maintained
- Version compatibility: Works with all supported Minecraft versions
- Performance: Large worlds processable with -Xmx10G memory allocation
- User satisfaction: Tool is discoverable, well-documented, easy to use
- Test coverage: Integration tests with real-world Minecraft data validate correctness
