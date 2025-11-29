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
- ✅ **Clickable Signs Feature** (PR #8, Issue #4) - Interactive sign mcfunction generation
  - Players can click signs to see original world coordinates and teleport back to source location
  - Implemented for all Minecraft versions (1.13, 1.14, 1.20.5, 1.21)
  - Nested clickEvent structure: Sign → /tellraw → /tp command
  - Version-specific JSON escaping (triple-escape for 1.13/1.14, double-escape for 1.20+)
  - Signs written to `data/readbooks/function/signs.mcfunction` in each datapack
  - Comprehensive Minecraft wiki documentation in code comments
  - All integration tests passing
- ✅ **Complete Datapack Structure Generation** (PR #9, Issue #5) - Ready-to-use Minecraft datapacks
  - Generate 4 complete datapacks instead of standalone mcfunction files:
    - `readbooks_datapack_1_13/` - Minecraft 1.13-1.14.3 (pack_format 4)
    - `readbooks_datapack_1_14/` - Minecraft 1.14.4-1.19.4 (pack_format 4)
    - `readbooks_datapack_1_20_5/` - Minecraft 1.20.5-1.20.6 (pack_format 41)
    - `readbooks_datapack_1_21/` - Minecraft 1.21+ (pack_format 48)
  - Each datapack contains proper directory structure with pack.mcmeta
  - Version-aware directory naming: pre-1.21 uses `functions/` (plural), 1.21+ uses `function/` (singular)
  - Users can directly copy datapack folders into world/datapacks/ and use immediately
  - Comprehensive technical reference added: `.kilocode/rules/memory-bank/minecraft-datapacks.md`
  - All integration tests passing with datapack structure validation

## Current Focus / Active Areas
- **Maintenance Mode**: Both major features (clickable signs and datapack structure) complete and merged
- **Version Monitoring**: Tracking Minecraft version updates for format changes
- **Testing**: All integration tests passing with expanded coverage for new features
- **Documentation**: Complete technical reference for Minecraft datapacks maintained

## Known Issues or Limitations
- **Monolithic Design**: All code in single Main.groovy file (1637 lines) - refactoring deferred
- **Single-threaded**: Processing is sequential, not parallelized (intentional for simplicity)
- **Java 21 Requirement**: Uses modern Java features, not backward compatible to Java 8/11
- **Memory Intensive**: Requires -Xmx10G for large worlds, not suitable for minimal memory environments
- **Format Version Fallback**: Relies on format detection heuristics for version compatibility

## Next Planned Work
- Monitor for Minecraft 1.22+ format changes and adapt parsing logic
- Gather community feedback on clickable signs and datapack generation features
- Consider optional refactoring if codebase grows significantly beyond current size
- Evaluate performance improvements for ultra-large worlds based on user feedback

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

