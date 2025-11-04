# ReadSignsAndBooks.jar - Project Brief

## Project Identity
**Name:** ReadSignsAndBooks.jar  
**Version:** 1.0.0  
**Type:** Minecraft Data Extraction Tool  
**Language:** Groovy 4.0.24 (JVM-based)  
**Status:** Active development and maintenance

## Core Mission
Extract written content (books and signs) from Minecraft world save files, enabling archival, analysis, and preservation of player-created content across different Minecraft versions.

## Problem Solved
Server administrators and archivists need a reliable way to:
- Extract and backup books written by players
- Export signs and their text content
- Process content from different Minecraft versions (1.18, 1.20, 1.20.5+)
- Handle complex nested container structures (chests, shulkers, bundles, minecarts, etc.)
- Work with large-scale worlds without memory exhaustion

## Primary Use Cases
1. **Server Archival:** Backup important player-written books before world resets
2. **Modpack Distribution:** Extract curated content for inclusion in modpacks
3. **Community Documentation:** Preserve server history and lore
4. **Data Analysis:** Analyze player-created content patterns and themes
5. **Format Conversion:** Convert Minecraft book data to accessible text formats

## Success Criteria
- ✅ Extracts all books and signs from valid Minecraft world saves
- ✅ Supports multiple output formats (Stendhal, CSV, combined text)
- ✅ Handles 17+ container types including nested structures
- ✅ Implements content-based deduplication
- ✅ Processes large worlds efficiently with -Xmx10G capability
- ✅ Maintains backward compatibility with older Minecraft versions
- ✅ Automated CI/CD with tested JAR distribution

## Project Scope
- **Dual interface:** CLI tool + JavaFX GUI (auto-detects based on arguments)
- Single-threaded processing (intentional design for simplicity)
- NBT format parsing for Minecraft data structures
- Comprehensive integration tests with real-world test data
- Automated build and deployment via GitHub Actions

## Author/Team Context
Community-maintained open-source project with focus on reliability and version compatibility.
