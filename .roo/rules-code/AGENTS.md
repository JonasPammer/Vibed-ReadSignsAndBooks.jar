# Code Mode Rules (Non-Obvious Only)

## Critical NBT Handling
- Querz NBT library returns null for missing keys - MUST wrap with helper methods in [`Main.groovy:1440-1635`](../../src/main/groovy/Main.groovy:1440)
- [`getCompoundTagList()`](../../src/main/groovy/Main.groovy:1458) returns empty list for null/missing keys, not null
- Book pages can be either StringTag list (pre-1.20.5) OR CompoundTag list (1.20.5+) - check both formats

## State Management Gotcha
- All static fields in [`Main`](../../src/main/groovy/Main.groovy:40-53) MUST be reset at start of [`runExtraction()`](../../src/main/groovy/Main.groovy:79-84)
- Missing reset causes test failures when running multiple tests
- [`bookHashes`](../../src/main/groovy/Main.groovy:43) and [`signHashes`](../../src/main/groovy/Main.groovy:44) use `.add()` return value for duplicate detection

## File Writing Pattern
- [`combinedBooksWriter`](../../src/main/groovy/Main.groovy:53) MUST be flushed after each book via `.flush()` for streaming output
- Duplicate books go to `.duplicates/` subfolder, not skipped
- Filename format: `Title_(PageCount)_by_Author~location~coords.stendhal` - tilde separators required

## Minecraft Format Version Detection
- Check for `'tag'` key first (pre-1.20.5), then `'components'` key (1.20.5+)
- Chunk format: try `'block_entities'` first (1.18+), fallback to `'TileEntities'` (pre-1.18)
- Entity location: `'entities'` folder exists only in 1.17+, otherwise in chunk data