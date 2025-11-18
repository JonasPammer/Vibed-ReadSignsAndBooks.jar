# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference

### Essential Commands

**Build and Package:**
```bash
gradle build              # Compile, test, and package JAR
gradle clean build        # Clean build from scratch
```

**Testing:**
```bash
gradle test               # Run all integration tests (10-minute timeout)
```

**Running Locally:**
```bash
# GUI mode (no arguments)
java -jar ReadSignsAndBooks.jar

# CLI mode with world path
gradle run --args="--world /path/to/minecraft/world"

# With custom JVM args for large worlds
gradle run "-Dorg.gradle.jvmargs=-Xmx10G -XX:+UseG1GC -XX:MaxGCPauseMillis=200" --args="--world /path/to/world"

# Direct JAR execution
java -Xmx10G -jar ReadSignsAndBooks.jar --world /path/to/world
```

**CLI Options:**
- `-w, --world <path>` - Minecraft world directory (default: current working directory)
- `-o, --output <path>` - Custom output directory (default: `ReadBooks/YYYY-MM-DD/`)
- `--remove-formatting` - Strip Minecraft formatting codes (§ codes) from output

**Linting:**
```bash
# GroovyLint configuration exists in .groovylintrc.json
# Most strict rules disabled to accommodate monolithic design
```

### Architecture Quick Facts

**Monolithic Design:**
- Main.groovy: ~2431 lines (all application logic)
- GUI.groovy: ~474 lines (JavaFX interface)
- GuiLogAppender.groovy: ~31 lines (live logging bridge)
- Intentionally single-file for deployment simplicity

**Smart Launch Detection:**
```groovy
// Main.main() auto-detects GUI vs CLI mode
if (shouldUseGui(args)) {
    Application.launch(GUI, args)  // No args → GUI
} else {
    runCli(args)                   // Has args → CLI
}
```

**Key Technologies:**
- **Language:** Groovy 4.0.24 on Java 21
- **NBT Parsing:** Querz NBT 6.1 (Minecraft data structures)
- **CLI Framework:** Picocli 4.7.7
- **GUI Framework:** JavaFX 21 (no GroovyFX - it's unmaintained)
- **Testing:** Spock 2.3-groovy-4.0 with real Minecraft world data

**Processing Pipeline:**
1. Player inventories (`playerdata/*.dat`)
2. Block containers in regions (`region/*.mca`)
3. Entity data (`entities/*.mca`)

All extracted content is deduplicated by hash and written in streaming fashion.

### Output Formats Generated

The tool creates `ReadBooks/YYYY-MM-DD/` with:
- `books/` - Individual `.stendhal` files per book
- `books/.duplicates/` - Duplicate books (same content, different locations)
- `all_books.txt` - Combined Stendhal format with VSCode folding markers
- `all_signs.txt` - All signs, one per line with coordinates
- `all_books.csv` / `all_signs.csv` - Tabular exports
- `all_books-1_13.mcfunction` - Minecraft 1.13+ give commands
- `all_books-1_14.mcfunction` - Minecraft 1.14+ give commands
- `all_books-1_20_5.mcfunction` - Minecraft 1.20.5+ give commands
- `all_books-1_21.mcfunction` - Minecraft 1.21+ give commands
- `all_signs-1_13.mcfunction` through `all_signs-1_21.mcfunction` - Sign placement commands
- `shulker_boxes-1_13.mcfunction` through `shulker_boxes-1_21.mcfunction` - Books organized in color-coded shulker boxes by author
- `summary.txt` - Processing metrics and breakdown
- `logs.txt` - Debug logs

### Testing Structure

**Integration Tests:**
- Location: `src/test/groovy/ReadBooksIntegrationSpec.groovy`
- Test data: `src/test/resources/1_21_10-44-3/` (real Minecraft 1.21 world)
- Naming convention: `{version}-{bookcount}-{signcount}/`
- Output inspection: `build/test-worlds/*/ReadBooks/` (gitignored, persistent)

**Test Execution:**
Tests run Main.runCli() directly (not Main.main() to avoid GUI launch issues).

### AI Commit Convention

**All AI-generated commits MUST use the "(ai)" scope:**

```
<type>(ai): <description> [skip ci]
```

Examples:
- `feat(ai): add new container type support [skip ci]`
- `fix(ai): correct NBT parsing for version X [skip ci]`
- `chore(ai): update memory bank [skip ci]`

This enables clear audit trails of AI-assisted changes.

### Known Constraints

- **Java 21+ only** - Uses modern features, not backward compatible
- **Large memory** - Requires `-Xmx10G` for large worlds
- **Single-threaded** - Intentional design for simplicity
- **Monolithic** - Refactoring deferred unless >2000 lines (currently ~2431)

---

## Detailed Documentation (Memory Bank)

For comprehensive architectural details, design decisions, and implementation specifics, see:

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md
@.kilocode/rules/memory-bank/gui.md
@.kilocode/rules/memory-bank/context.md

**Memory Bank Usage:**
The Memory Bank system provides persistent context across Claude Code sessions. Always read ALL memory bank files at task start. Update context.md when completing significant work. See `.kilocode/rules/memory-bank-instructions.md` for full details.
