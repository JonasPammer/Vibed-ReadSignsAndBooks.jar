# Block Search Feature - Implementation Plan

## Executive Summary

This plan extends ReadSignsAndBooks.jar to search for arbitrary block types in Minecraft world saves, with the primary use case being nether portal frame detection for waypoint mapping.

**Research Status**: Complete (2025-12-07)
**Estimated Complexity**: Medium-High
**Files Affected**: 4-6 files (Main.groovy, new BlockSearcher.groovy, OutputWriters.groovy, GUI.groovy, tests)

---

## Research Findings Summary

### Key Technical Insights

1. **Block Storage Architecture**
   - Blocks stored using palette + bit-packed index system
   - Path: `sections[].block_states.palette` and `sections[].block_states.data`
   - 4096 blocks per section (16×16×16), ~24 sections per Overworld chunk
   - **~10 million block lookups per region file** (worst case)

2. **Querz NBT Library Capabilities**
   - `chunk.getBlockStateAt(x, y, z)` - returns CompoundTag with block name
   - Automatic palette resolution (no manual bit-packing needed)
   - `MCAFile` implements `Iterable<Chunk>`, `Chunk` implements `Iterable<Section>`
   - `Section.getPalette()` available for fast filtering

3. **Nether Portal Detection Strategy**
   - **Two block types**: `minecraft:obsidian` (frame) + `minecraft:nether_portal` (purple blocks)
   - **Critical insight**: Frame obsidian is indistinguishable from regular obsidian in NBT
   - **Best approach**: Detect `nether_portal` blocks → infer frame location
   - Portal blocks have `axis` property: `"x"` (east-west) or `"z"` (north-south)

4. **Dimension Support**
   - Overworld: `world/region/`
   - Nether: `world/DIM-1/region/`
   - The End: `world/DIM1/region/`
   - **Current code only processes `region/`** - needs extension

---

## Architecture Decision Record

### Decision 1: Palette-First Optimization

**Problem**: Iterating all blocks is O(sections × 4096) per chunk - prohibitively slow.

**Solution**: Check section palette FIRST. If target block not in palette, skip entire section.

**Implementation**:
```groovy
ListTag<?> palette = section.getCompoundTag("block_states")?.getListTag("palette")
Set<String> paletteBlocks = palette.collect { it.getString("Name") }.toSet()

// Fast rejection: skip section if no target blocks in palette
if (!targetBlocks.any { paletteBlocks.contains(it) }) {
    continue  // Skip entire 4096-block iteration
}
```

**Expected Performance**: 90%+ sections skipped for rare blocks like portals.

**LoadFlags Optimization**: Use `LoadFlags.BLOCK_STATES` instead of `LoadFlags.RAW` to skip loading entities/tile entities when only searching for blocks. This reduces memory usage significantly.

```groovy
// Performance-optimized loading for block search
MCAFile mcaFile = MCAUtil.read(file, LoadFlags.BLOCK_STATES)
```

### Decision 2: Portal Detection via Portal Blocks (with Clustering)

**Problem**: Cannot distinguish portal frame obsidian from regular obsidian.

**Solution**: Search for `minecraft:nether_portal` blocks (the purple material), then **cluster adjacent blocks** into portal structures.

**Portal Structure Facts**:
- Minimum portal: 4×5 frame → 2×3 interior (6 portal blocks)
- Maximum portal: 23×23 frame → 21×21 interior (441 portal blocks)
- All portal blocks in same portal share the same `axis` property
- Portal blocks are always vertically oriented (no horizontal portals)

**Clustering Algorithm**:
1. Collect all `minecraft:nether_portal` blocks with coordinates
2. Group by dimension and axis (x-axis and z-axis portals are separate)
3. Use flood-fill to cluster adjacent blocks (sharing a face, not diagonal)
4. For each cluster, calculate:
   - Bottom-left corner coordinates (anchor point)
   - Portal dimensions (width × height)
   - Center coordinates (for waypoint placement)

**Adjacency Definition** (for clustering):
- Same axis value
- Coordinates differ by exactly 1 on ONE axis only:
  - For `axis=z` portals: adjacent on X or Y
  - For `axis=x` portals: adjacent on Z or Y

**Output**: One row per portal structure, not per block

### Decision 3: New Utility Class (BlockSearcher.groovy)

**Rationale**:
- Main.groovy already 1812 lines - avoid bloat
- Block searching is conceptually separate from sign/book extraction
- Enables independent testing and future extension
- Consistent with recent modularization (NbtUtils, TextUtils, etc.)

### Decision 4: Dimension Parameter in Output

**Requirement**: Each block location must include dimension for waypoint mapping.

**CSV Format**:
```csv
block_type,dimension,x,y,z,properties
minecraft:nether_portal,overworld,123,64,-456,axis=z
minecraft:nether_portal,nether,15,8,-57,axis=x
```

---

## CLI Interface Design

### New Command-Line Options

```groovy
@Option(names = ['--search-blocks'],
        description = 'Search for specific block types (comma-separated, e.g., "obsidian,nether_portal")',
        split = ',')
static List<String> searchBlocks = []

@Option(names = ['--find-portals'],
        description = 'Find all nether portals with intelligent clustering (outputs one entry per portal structure)',
        defaultValue = 'false')
static boolean findPortals = false

@Option(names = ['--dimensions'],
        description = 'Dimensions to search (default: all). Options: overworld,nether,end',
        split = ',',
        defaultValue = 'overworld,nether,end')
static List<String> dimensions = ['overworld', 'nether', 'end']

@Option(names = ['--block-output-format'],
        description = 'Output format for block search (csv, json, txt)',
        defaultValue = 'csv')
static String blockOutputFormat = 'csv'
```

### Usage Examples

```bash
# Find all nether portals in all dimensions
java -jar ReadSignsAndBooks.jar -w /path/to/world --find-portals

# Search for specific blocks
java -jar ReadSignsAndBooks.jar -w /path/to/world --search-blocks obsidian,nether_portal

# Search only nether dimension
java -jar ReadSignsAndBooks.jar -w /path/to/world --find-portals --dimensions nether

# Combined with existing extraction
java -jar ReadSignsAndBooks.jar -w /path/to/world --find-portals --extract-custom-names
```

---

## CSV Output Format Specification

### Format A: Generic Block Search (`--search-blocks`)

**File**: `blocks.csv`

```csv
block_type,dimension,x,y,z,properties,region_file
minecraft:nether_portal,overworld,123,64,-456,axis=z,r.0.-1.mca
minecraft:nether_portal,overworld,123,65,-456,axis=z,r.0.-1.mca
minecraft:obsidian,overworld,120,64,-456,,r.0.-1.mca
```

**Columns**:

| Column | Type | Description |
|--------|------|-------------|
| `block_type` | String | Full block ID (e.g., `minecraft:nether_portal`) |
| `dimension` | String | `overworld`, `nether`, or `end` |
| `x` | Integer | World X coordinate |
| `y` | Integer | World Y coordinate |
| `z` | Integer | World Z coordinate |
| `properties` | String | Block state properties (e.g., `axis=z`), empty if none |
| `region_file` | String | Source region file name |

---

### Format B: Portal Detection (`--find-portals`)

**File**: `portals.csv`

```csv
portal_id,dimension,x,y,z,width,height,axis,block_count,center_x,center_y,center_z
1,overworld,123,64,-456,2,3,z,6,123.5,65.5,-456
2,nether,15,8,-57,2,3,x,6,15,9.5,-57.5
3,overworld,500,70,200,4,5,z,20,501.5,72.5,200
```

**Columns**:

| Column | Type | Description |
|--------|------|-------------|
| `portal_id` | Integer | Unique portal identifier (per extraction run) |
| `dimension` | String | `overworld`, `nether`, or `end` |
| `x` | Integer | Bottom-left corner X (anchor point) |
| `y` | Integer | Bottom-left corner Y (lowest block) |
| `z` | Integer | Bottom-left corner Z (anchor point) |
| `width` | Integer | Portal width in blocks (horizontal) |
| `height` | Integer | Portal height in blocks (vertical) |
| `axis` | String | Portal orientation: `x` (east-west) or `z` (north-south) |
| `block_count` | Integer | Total portal blocks in this structure |
| `center_x` | Float | Center X coordinate (for waypoint placement) |
| `center_y` | Float | Center Y coordinate (vertical midpoint) |
| `center_z` | Float | Center Z coordinate (for waypoint placement) |

**Notes**:
- One row per portal structure (not per block)
- `width × height` should equal `block_count` for rectangular portals
- Irregular portal shapes (from mods) may have different block counts
- Center coordinates are ideal for waypoint marker placement

---

### JSON Output Format (Both Modes)

**Generic Block Search**:
```json
{
  "blocks": [
    {
      "type": "minecraft:nether_portal",
      "dimension": "overworld",
      "coordinates": {"x": 123, "y": 64, "z": -456},
      "properties": {"axis": "z"},
      "region": "r.0.-1.mca"
    }
  ],
  "summary": {
    "total_blocks": 15,
    "by_type": {"minecraft:nether_portal": 10, "minecraft:obsidian": 5},
    "by_dimension": {"overworld": 8, "nether": 7}
  }
}
```

**Portal Detection**:
```json
{
  "portals": [
    {
      "id": 1,
      "dimension": "overworld",
      "anchor": {"x": 123, "y": 64, "z": -456},
      "center": {"x": 123.5, "y": 65.5, "z": -456},
      "size": {"width": 2, "height": 3},
      "axis": "z",
      "block_count": 6
    }
  ],
  "summary": {
    "total_portals": 3,
    "by_dimension": {"overworld": 2, "nether": 1}
  }
}
```

---

## Implementation Plan

### Phase 1: Core Infrastructure (BlockSearcher.groovy)

**New File**: `src/main/groovy/BlockSearcher.groovy`

**Responsibilities**:
- Iterate region files across dimensions
- Palette-first optimization for fast section skipping
- Block coordinate calculation with dimension tracking
- Deduplication by coordinate hash

**Key Methods**:
```groovy
class BlockSearcher {
    // Main entry point
    static List<BlockLocation> searchBlocks(
        String worldPath,
        Set<String> targetBlocks,
        List<String> dimensions
    )

    // Process single region file
    static List<BlockLocation> processRegionFile(
        File regionFile,
        Set<String> targetBlocks,
        String dimension
    )

    // Process single chunk with palette optimization
    static List<BlockLocation> processChunk(
        Chunk chunk,
        Set<String> targetBlocks,
        String dimension,
        String regionFileName
    )

    // Helper: Check if palette contains any target blocks
    static boolean paletteContainsTargets(
        ListTag<?> palette,
        Set<String> targetBlocks
    )

    // Data class for results
    static class BlockLocation {
        String blockType
        String dimension
        int x, y, z
        Map<String, String> properties
        String regionFile
    }
}
```

### Phase 1.5: Portal Clustering (PortalDetector.groovy)

**New File**: `src/main/groovy/PortalDetector.groovy`

**Responsibilities**:
- Cluster adjacent nether_portal blocks into portal structures
- Calculate portal dimensions (width × height)
- Compute center coordinates for waypoint placement
- Handle portals spanning chunk boundaries

**Key Methods**:
```groovy
class PortalDetector {
    // Main entry point - converts raw blocks to portal structures
    static List<Portal> detectPortals(List<BlockLocation> portalBlocks)

    // Cluster adjacent blocks using flood-fill algorithm
    static List<Set<BlockLocation>> clusterAdjacentBlocks(
        List<BlockLocation> blocks,
        String dimension,
        String axis
    )

    // Check if two blocks are adjacent (share a face)
    static boolean areAdjacent(BlockLocation a, BlockLocation b)

    // Calculate portal properties from block cluster
    static Portal createPortalFromCluster(
        Set<BlockLocation> cluster,
        int portalId
    )

    // Data class for portal structure
    static class Portal {
        int id
        String dimension
        int anchorX, anchorY, anchorZ  // Bottom-left corner
        int width, height
        String axis
        int blockCount
        double centerX, centerY, centerZ

        // Convenience method for CSV output
        String toCsvRow()
    }
}
```

**Clustering Algorithm (Flood-Fill)**:
```groovy
static List<Set<BlockLocation>> clusterAdjacentBlocks(List<BlockLocation> blocks, String dimension, String axis) {
    // Filter blocks by dimension and axis
    def filtered = blocks.findAll { it.dimension == dimension && it.properties?.axis == axis }

    // Build coordinate lookup map for O(1) adjacency checks
    Map<String, BlockLocation> coordMap = [:]
    filtered.each { coordMap["${it.x},${it.y},${it.z}"] = it }

    Set<BlockLocation> visited = [] as Set
    List<Set<BlockLocation>> clusters = []

    filtered.each { block ->
        if (visited.contains(block)) return

        // Flood-fill from this block
        Set<BlockLocation> cluster = [] as Set
        Queue<BlockLocation> queue = new LinkedList<>()
        queue.add(block)

        while (!queue.isEmpty()) {
            BlockLocation current = queue.poll()
            if (visited.contains(current)) continue

            visited.add(current)
            cluster.add(current)

            // Check all 4 adjacent positions (not diagonal)
            // For axis=z portals: vary X and Y
            // For axis=x portals: vary Z and Y
            getAdjacentCoords(current, axis).each { coord ->
                BlockLocation neighbor = coordMap[coord]
                if (neighbor && !visited.contains(neighbor)) {
                    queue.add(neighbor)
                }
            }
        }

        if (!cluster.isEmpty()) {
            clusters.add(cluster)
        }
    }

    return clusters
}

static List<String> getAdjacentCoords(BlockLocation block, String axis) {
    int x = block.x, y = block.y, z = block.z

    if (axis == 'z') {
        // Portal faces north-south, varies on X and Y
        return [
            "${x-1},${y},${z}", "${x+1},${y},${z}",  // left/right
            "${x},${y-1},${z}", "${x},${y+1},${z}"   // up/down
        ]
    } else {
        // Portal faces east-west, varies on Z and Y
        return [
            "${x},${y},${z-1}", "${x},${y},${z+1}",  // left/right
            "${x},${y-1},${z}", "${x},${y+1},${z}"   // up/down
        ]
    }
}
```

### Phase 2: Dimension Support

**Modify**: `Main.groovy`

**Changes**:
1. Add new CLI options (see CLI Interface Design above)
2. Add dimension iteration in extraction flow
3. Call BlockSearcher from main extraction method

**Dimension Folder Mapping**:
```groovy
static Map<String, String> DIMENSION_FOLDERS = [
    'overworld': 'region',
    'nether': 'DIM-1/region',
    'end': 'DIM1/region'
]
```

### Phase 3: Output Integration

**Modify**: `OutputWriters.groovy`

**New Methods**:
```groovy
static void writeBlocksCSV(String outputFolder, List<BlockLocation> blocks)
static void writeBlocksJSON(String outputFolder, List<BlockLocation> blocks)
static void writeBlocksTXT(String outputFolder, List<BlockLocation> blocks)
```

### Phase 4: GUI Integration

**Modify**: `GUI.groovy`

**Changes**:
1. Add "Find Portals" checkbox
2. Add "Search Blocks" text field (advanced)
3. Add dimension checkboxes (Overworld, Nether, End)
4. Update extraction flow to include block search

### Phase 5: Testing

**New Test Cases** in `ReadBooksIntegrationSpec.groovy`:
1. Test portal detection in test world (if portals exist)
2. Test dimension filtering
3. Test CSV output format
4. Test palette optimization (performance)
5. Test empty results handling

---

## Detailed Task Breakdown

### Task 1: Create BlockSearcher.groovy (Est. ~300 lines)
- [ ] Create BlockLocation data class with equals/hashCode
- [ ] Implement paletteContainsTargets() helper
- [ ] Implement processChunk() with palette optimization
- [ ] Implement processRegionFile() with chunk iteration
- [ ] Implement searchBlocks() main method with dimension support
- [ ] Add progress bar support
- [ ] Add error handling for corrupt regions

### Task 2: Create PortalDetector.groovy (Est. ~200 lines)
- [ ] Create Portal data class with toCsvRow() method
- [ ] Implement clusterAdjacentBlocks() flood-fill algorithm
- [ ] Implement getAdjacentCoords() helper for axis-aware adjacency
- [ ] Implement createPortalFromCluster() to calculate dimensions/center
- [ ] Implement detectPortals() main method
- [ ] Handle edge case: portals spanning chunk boundaries
- [ ] Add unit tests for clustering logic

### Task 3: Add CLI Options to Main.groovy (~50 lines)
- [ ] Add @Option for --search-blocks (generic block search)
- [ ] Add @Option for --find-portals (intelligent portal detection)
- [ ] Add @Option for --dimensions
- [ ] Add @Option for --block-output-format
- [ ] Add resetState() updates for new fields

### Task 4: Integrate in Extraction Flow (~50 lines)
- [ ] Call BlockSearcher from runExtraction()
- [ ] For --find-portals: search for nether_portal → pass to PortalDetector
- [ ] For --search-blocks: output raw block locations
- [ ] Coordinate with existing extraction phases

### Task 5: Implement Output Writers (~150 lines)
- [ ] writeBlocksCSV() for generic block search
- [ ] writePortalsCSV() for portal detection (different format)
- [ ] writePortalsJSON() with structured format
- [ ] writeBlocksTXT() for human-readable output
- [ ] Add portal summary to printSummaryStatistics()

### Task 6: GUI Integration (~80 lines)
- [ ] Add "Find Portals" checkbox
- [ ] Add dimension checkboxes (Overworld, Nether, End)
- [ ] Update runExtraction() to pass new flags
- [ ] Update parseGuiArguments() for new options

### Task 7: Testing (~150 lines)
- [ ] Test basic block search functionality
- [ ] Test portal clustering (2×3 blocks → 1 portal)
- [ ] Test multi-portal clustering (separate structures)
- [ ] Test cross-dimension portal detection
- [ ] Test CSV output format validation
- [ ] Test edge case: irregular portal shapes

---

## Performance Considerations

### Expected Performance

**Worst Case** (all sections contain target blocks):
- 32×32 chunks per region × ~24 sections × 4096 blocks = 100+ million lookups per region
- **Mitigated by**: Palette-first optimization

**Typical Case** (rare blocks like portals):
- Palette check: O(1) per section
- 95%+ sections skipped
- Only iterate blocks in sections containing targets

### Memory Profile

- Streaming output (no full result buffering)
- Single region file in memory at a time
- Deduplication set scales with unique locations (typically small)

### Optimization Strategies

1. **Palette-First**: Skip sections where target not in palette
2. **Early Termination**: Option to stop after N results (for preview)
3. **Parallel Processing**: Future enhancement - process regions in parallel

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Performance too slow for large worlds | Medium | High | Palette optimization, progress bar, --dimensions filter |
| Querz NBT API changes | Low | Medium | Pin library version, defensive coding |
| Test world lacks portals | Medium | Low | Create test world with known portals, or mock tests |
| GUI complexity increase | Low | Medium | Start with simple checkbox, defer advanced options |

---

## Success Criteria

1. **Functional**: Accurately locate all nether portals in test world
2. **Performance**: Large worlds processable in reasonable time (<5 min for typical server)
3. **Usability**: CSV output directly importable to mapping tools
4. **Maintainability**: Clean separation in BlockSearcher.groovy
5. **Backward Compatible**: No impact on existing sign/book extraction

---

## Future Enhancements (Out of Scope)

1. **Portal Frame Reconstruction**: Scan adjacent obsidian to find full frame
2. **Portal Pairing**: Match overworld portals to nether destinations
3. **Block Search Patterns**: Regex or wildcard block matching
4. **Spatial Queries**: Find blocks within radius of coordinates
5. **Map Integration**: Direct export to Dynmap/BlueMap markers

---

## Appendix A: Querz NBT API Reference (from DeepWiki)

### Key Classes and Methods

```java
// Region file handling with LoadFlags for performance
// Use BLOCK_STATES to skip entity loading
long flags = LoadFlags.BLOCK_STATES;  // Only load block data, not entities
MCAFile mcaFile = MCAUtil.read(file, flags);

// Iterate chunks in region (MCAFile implements Iterable<Chunk>)
for (Chunk chunk : mcaFile) {
    if (chunk == null) continue;
    // Process chunk...
}

// Block access at world coordinates
CompoundTag blockState = chunk.getBlockStateAt(blockX, blockY, blockZ);
String blockName = blockState.getString("Name");
CompoundTag properties = blockState.getCompoundTag("Properties");

// Section iteration (Chunk implements Iterable<Section>)
for (Section section : chunk) {
    if (section == null) continue;
    int sectionY = section.getHeight();  // Section Y coordinate

    // Iterate ALL 4096 blocks in section using blockStates() iterator
    for (CompoundTag blockState : section.blockStates()) {
        // Each blockState is a palette entry (CompoundTag)
        String name = blockState.getString("Name");
    }
}

// Direct block iteration with index (alternative approach)
for (int i = 0; i < 4096; i++) {
    CompoundTag blockState = section.getBlockStateAt(i);
    // Index formula: (y & 0xF) * 256 + (z & 0xF) * 16 + (x & 0xF)
}
```

### Coordinate Conversion Utilities

```java
// Block → Chunk (divide by 16, handles negatives correctly)
int chunkX = MCAUtil.blockToChunk(blockX);   // blockX >> 4

// Block → Region (divide by 512)
int regionX = MCAUtil.blockToRegion(blockX); // blockX >> 9

// Chunk → Region (divide by 32)
int regionX = MCAUtil.chunkToRegion(chunkX); // chunkX >> 5

// Reverse conversions (multiply)
int blockX = MCAUtil.chunkToBlock(chunkX);   // chunkX << 4
int blockX = MCAUtil.regionToBlock(regionX); // regionX << 9
int chunkX = MCAUtil.regionToChunk(regionX); // regionX << 5

// Get chunk index within region (0-1023)
int index = MCAFile.getChunkIndex(chunkX, chunkZ); // (chunkX & 31) + (chunkZ & 31) * 32
```

### LoadFlags Constants

```java
LoadFlags.BLOCK_STATES   // Block state data (palette + indices)
LoadFlags.BLOCK_LIGHTS   // Block light levels
LoadFlags.SKY_LIGHT      // Sky light levels
LoadFlags.BIOMES         // Biome data
LoadFlags.HEIGHTMAPS     // Heightmap data
LoadFlags.ENTITIES       // Entity data
LoadFlags.TILE_ENTITIES  // Block entity data (chests, signs)
LoadFlags.ALL_DATA       // Everything
LoadFlags.RAW            // Raw NBT without field parsing
```

### Block Index Calculation

```java
// Within a 16x16x16 section, blocks are indexed YZX order:
int blockIndex = (blockY & 0xF) * 256 + (blockZ & 0xF) * 16 + (blockX & 0xF);

// Reverse: index to local coordinates
int localY = blockIndex / 256;
int localZ = (blockIndex % 256) / 16;
int localX = blockIndex % 16;
```

---

## Appendix B: Minecraft Block IDs for Portals

```
minecraft:nether_portal    - Purple portal blocks (axis: x|z)
minecraft:obsidian         - Frame material (no special properties)
minecraft:crying_obsidian  - Not used in portals (for respawn anchors)
minecraft:end_portal       - End portal (for future enhancement)
minecraft:end_portal_frame - End portal frame (eye: true|false)
```

---

**Document Created**: 2025-12-07
**Author**: Claude Code with Deep Research Mode
**Status**: Ready for Review
