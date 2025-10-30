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

### Content Processing
- NBT binary format parsing and traversal
- Recursive container nesting (searches inside containers within containers)
- Multi-version format compatibility (automatic detection and adaptation)
- Content-based deduplication (hashCode of pages, not reference equality)
- UTF-8 text encoding with special character support

## User Experience Goals
1. **Simplicity** - Single command execution: `java -jar ReadSignsAndBooks.jar /path/to/world`
2. **Transparency** - Progress bar showing files processed, clear error messages
3. **Reliability** - Handles corrupt data gracefully, doesn't crash on edge cases
4. **Speed** - Can process large worlds (thousands of blocks/entities) in reasonable time
5. **Flexibility** - Multiple output formats for different user needs

## Success Metrics
- Extraction accuracy: 100% of accessible books/signs extracted
- Content preservation: All text, formatting, and metadata maintained
- Version compatibility: Works with all supported Minecraft versions
- Performance: Large worlds processable with -Xmx10G memory allocation
- User satisfaction: Tool is discoverable, well-documented, easy to use
- Test coverage: Integration tests with real-world Minecraft data validate correctness
