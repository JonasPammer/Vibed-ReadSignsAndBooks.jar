# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md
@.kilocode/rules/memory-bank/gui.md
@.kilocode/rules/memory-bank/context.md

## Memory Bank System

This repository uses the Kilo Code Memory Bank system for persistent context across Claude Code sessions. The files referenced above contain comprehensive documentation about:

- **brief.md** - Project identity, mission, and scope
- **architecture.md** - Technical architecture, design decisions, and system components
- **tech.md** - Technology stack, development setup, and build commands
- **product.md** - Product context, user needs, and feature capabilities
- **gui.md** - JavaFX GUI implementation and behavior
- **context.md** - Current state, recent work, and AI commit conventions

Always read ALL memory bank files at the start of every task. See `.kilocode/rules/memory-bank-instructions.md` for the complete Memory Bank workflow.

## Quick Start for Development

```bash
# Build and test
gradle build

# Run tests (10-minute timeout)
gradle test

# Run GUI mode
java -jar ReadSignsAndBooks.jar

# Run CLI mode
gradle run --args="--world /path/to/minecraft/world"

# Large worlds need more memory
gradle run "-Dorg.gradle.jvmargs=-Xmx10G" --args="--world /path/to/world"
```

**CLI Options:**
- `-w, --world <path>` - World directory (default: current directory)
- `-o, --output <path>` - Output directory (default: `ReadBooks/YYYY-MM-DD/`)
- `--remove-formatting` - Strip Minecraft formatting codes

**Test output inspection:** `build/test-worlds/*/ReadBooks/` (gitignored, persistent)

## Important Conventions

**AI Commit Format (REQUIRED):**
```
<type>(ai): <description> [skip ci]
```

Examples:
- `feat(ai): add new container support [skip ci]`
- `fix(ai): correct NBT parsing [skip ci]`
- `docs(ai): update memory bank [skip ci]`

See context.md for complete AI commit convention details.
