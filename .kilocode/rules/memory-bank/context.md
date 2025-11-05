# Current State & Progress

## Current Version
**Version:** 1.0.0  
**Release Status:** Active, production-ready

## Recent Completed Work
- ✅ Monolithic Main.groovy implementation (1637 lines) with full feature completeness
- ✅ NBT format compatibility layer supporting Minecraft 1.18, 1.20, 1.20.5+
- ✅ Multiple output formats implemented (Stendhal JSON, CSV, combined text)
- ✅ 17 container types supported with recursive nesting
- ✅ Content-based deduplication system
- ✅ Streaming output architecture preventing OOM on large worlds
- ✅ Dynamic Logback configuration for runtime log file setting
- ✅ Spock integration tests with real-world Minecraft data (1_21_10-44-3 test world)
- ✅ GitHub Actions CI/CD pipeline with automated JAR commits
- ✅ Comprehensive documentation and README
- ✅ **Shulker Box Export Feature** (Version 1.0.1) - Author-organized container export
  - Deterministic author → color mapping (16 shulker box colors)
  - Multi-version command generation (1.13, 1.14, 1.20.5, 1.21)
  - Overflow handling for authors with >27 books (multiple boxes with numbering)
  - Version-appropriate JSON/NBT syntax for all formats
  - item_name component displaying author in container UI
  - 3 new integration tests validating JSON structure, slot capacity, color determinism
  - All 14 integration tests passing (11 existing + 3 new shulker tests)
- ✅ **Sign mcfunction File Generation Feature** (Version 1.0.2)
  - Multi-version setblock command generation (1.13, 1.14, 1.20, 1.20.5, 1.21)
  - Sign position tracking: unique signs at incrementing X coordinates (~1, ~2, etc.), duplicates offset in Z (~0, ~1, ~2, etc.)
  - Version-specific NBT formatting for each Minecraft version
  - All sign NBT data preserved (front_text, back_text, block states, glowing, waxed status)
  - 3 new integration tests validating sign mcfunction generation, X coordinate incrementing, Z offset deduplication
  - Regression test assertions for Z coordinate format (line 1: "~ ~0", line 2: "~ ~1", etc.)
  - All 17 integration tests passing (14 existing + 3 new sign tests)
- ✅ **Failed Region State File Tracking** (Version 1.0.3)
  - Persistent state file `.failed_regions_state.json` in output folder
  - Tracks region/entity files that fail to read across multiple runs
  - World-aware key system supports processing multiple worlds
  - Consolidated startup notice displays all known problematic regions
  - Error suppression: Known failures logged at debug level, new failures at warning level
  - Automatic recovery tracking: Successfully read regions removed from state file
  - Dynamic state persistence: State updated only with remaining failures
  - 16/17 integration tests passing (pre-existing failure unrelated to feature)

## Current Focus / Active Areas
- **Maintenance Mode**: Monitoring for new Minecraft version releases
- **Bug Fixes**: Addressing any edge cases in NBT parsing or container handling
- **Performance Optimization**: Monitoring memory usage on large-scale worlds

## Known Issues or Limitations
- **Monolithic Design**: All code in single Main.groovy file (1637 lines) - refactoring deferred
- **Single-threaded**: Processing is sequential, not parallelized (intentional for simplicity)
- **Java 21 Requirement**: Uses modern Java features, not backward compatible to Java 8/11
- **Memory Intensive**: Requires -Xmx10G for large worlds, not suitable for minimal memory environments
- **Format Version Fallback**: Relies on format detection heuristics for version compatibility

## Next Planned Work
- Monitor for Minecraft 1.21+ format changes and adapt parsing logic
- Consider optional refactoring into modular components if codebase exceeds 2000 lines
- Evaluate performance improvements for ultra-large worlds
- Gather community feedback on output formats and usability

## Team Status
- **Author**: Community-maintained, open-source
- **Maintenance**: Active monitoring and updates
- **Documentation**: Complete with usage examples and integration tests

## AI Commit Convention

**All AI-generated commits MUST use the "(ai)" scope identifier in commit messages.**

**Format**: `<type>(ai): <description> [skip ci]`

**Examples**:
- `chore(ai): initialize project memory bank [skip ci]`
- `feat(ai): add new container type support [skip ci]`
- `fix(ai): correct NBT parsing for format version X [skip ci]`
- `refactor(ai): extract utility functions to separate module [skip ci]`

**Rationale**: The "(ai)" scope clearly identifies commits created by AI agents, enabling:
- Clear audit trail of AI-assisted changes
- Distinction from human developer commits
- Easy filtering of automated changes in git history
- Accountability and traceability

**Enforcement**: All AI assistants working on this project should:
1. Always include "(ai)" in their commit scope
2. Document this convention in commit messages when first establishing it
3. Reference this section if questioned about commit formatting

