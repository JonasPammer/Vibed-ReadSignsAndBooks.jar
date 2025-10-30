# Architect Mode Rules (Non-Obvious Only)

## Design Constraints
- **Single-file monolith**: Entire application in [`Main.groovy`](../../src/main/groovy/Main.groovy) (1637 lines)
- Static state pattern for all tracking (not OOP with instances)
- No database - all processing in-memory with streaming file output
- Fat JAR pattern with all dependencies bundled via [`zipTree`](../../build.gradle:103-105)

## Processing Architecture
- Three-phase sequential processing (cannot be parallelized):
  1. [`readPlayerData()`](../../src/main/groovy/Main.groovy:292) - player inventories/ender chests
  2. [`readSignsAndBooks()`](../../src/main/groovy/Main.groovy:356) - region files with chunks
  3. [`readEntities()`](../../src/main/groovy/Main.groovy:504) - entity files (1.17+ only)
- Each phase updates shared static counters and Sets
- Duplicate detection via `Set.add()` boolean return - not post-processing

## Recursive Container Scanning
- [`parseItem()`](../../src/main/groovy/Main.groovy:625) recursively scans nested containers
- Supports: shulker boxes → bundles → more shulker boxes (infinite nesting)
- Location tracking builds path string: `"base > shulker_box > bundle"`
- Must handle both "tag" (old) and "components" (new) formats at each recursion level

## Format Compatibility Layer
- No version detection - checks for key existence instead
- Handles 3 major Minecraft format changes transparently:
  - Chunk format (1.18): Level wrapper removal
  - Sign format (1.20): Text fields to front_text compound
  - Item format (1.20.5): tag to components migration
- Compatibility achieved via fallback chains, not version branches

## Output Streaming Pattern
- [`combinedBooksWriter`](../../src/main/groovy/Main.groovy:53) kept open entire run for streaming
- Individual files written immediately when book found
- CSV data collected in-memory, written at end
- Summary statistics accumulated during processing, not post-calculated