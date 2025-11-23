# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md
@.kilocode/rules/memory-bank/gui.md
@.kilocode/rules/memory-bank/minecraft-datapacks.md

## CRITICAL: Minecraft Datapack Directory Naming

**WARNING**: Minecraft 1.21 snapshot 24w21a changed directory naming from PLURAL to SINGULAR.

Pre-1.21 versions (1.13-1.20.6): Use `functions/` (plural)
1.21+ versions: Use `function/` (singular)

**ALWAYS** use version-specific directory naming in `createDatapackStructure()`:
```groovy
String functionDirName = (version == '1_21') ? 'function' : 'functions'
```

**DO NOT** hardcode `function/` for all versions - this will break pre-1.21 datapacks!

See @.kilocode/rules/memory-bank/minecraft-datapacks.md for complete technical reference.

## Datapack Guardrails

- Always consult @.kilocode/rules/memory-bank/minecraft-datapacks.md before touching datapack code. That document is the single source of truth for pack formats, directory names, and command syntax.
- Only generate and test the versions listed in `Main.DATAPACK_VERSIONS` (`1_13`, `1_14`, `1_20_5`, `1_21`). Adding/removing versions requires updating the constant, integration tests, and this file together.
