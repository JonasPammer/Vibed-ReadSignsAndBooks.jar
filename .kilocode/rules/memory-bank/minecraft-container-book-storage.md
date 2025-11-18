# Minecraft Container Types and Book Storage - Complete Reference

**Created:** 2025-11-18
**Sources:** Minecraft Wiki, extensive gameplay research
**Coverage:** All container types that can store written books (Java Edition 1.13-1.21+)

## Overview

This document catalogs every Minecraft container type capable of storing written books, their NBT structures, nesting capabilities, and extraction patterns. Critical for implementing comprehensive book extraction tools.

---

## Container Classification System

### Storage Categories

**Category 1: Block Entity Containers**
- Physical blocks with block entity data
- Stored in chunk NBT under "block_entities" (1.18+) or "TileEntities" (pre-1.18)
- Access pattern: Region file → chunk → block_entities

**Category 2: Entity Containers**
- Mobile entities with inventory
- Stored in entity files (*.mca in entities/ folder)
- Access pattern: Entity file → entities list → entity data

**Category 3: Player Containers**
- Player inventory and ender chest
- Stored in playerdata/*.dat files
- Access pattern: Player data file → Inventory/EnderItems

**Category 4: Nested Containers**
- Items that themselves contain items (shulker boxes, bundles)
- Can appear in any of the above categories
- Requires recursive descent

---

## Block Entity Containers (Category 1)

### Standard Chests

**Block IDs:**
- `minecraft:chest` (regular chest)
- `minecraft:trapped_chest` (trapped chest)

**NBT Structure (Pre-1.20.5):**
```snbt
{
  id: "minecraft:chest",
  x: 100,
  y: 64,
  z: 200,
  Items: [
    {
      Slot: 0b,
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ]
}
```

**NBT Structure (1.20.5+):**
```snbt
{
  id: "minecraft:chest",
  x: 100,
  y: 64,
  z: 200,
  Items: [
    {
      Slot: 0b,
      id: "minecraft:written_book",
      count: 1,
      components: {...}
    }
  ]
}
```

**Slots:** 27 (3 rows × 9 columns), numbered 0-26
**Nesting:** Can contain shulker boxes, bundles

### Barrels

**Block ID:** `minecraft:barrel`

**Structure:** Identical to chest (27 slots)

**NBT:** Same "Items" list format as chests

### Shulker Boxes (All 17 Colors)

**Block IDs:**
- `minecraft:shulker_box` (purple, undyed)
- `minecraft:white_shulker_box`
- `minecraft:orange_shulker_box`
- `minecraft:magenta_shulker_box`
- `minecraft:light_blue_shulker_box`
- `minecraft:yellow_shulker_box`
- `minecraft:lime_shulker_box`
- `minecraft:pink_shulker_box`
- `minecraft:gray_shulker_box`
- `minecraft:light_gray_shulker_box`
- `minecraft:cyan_shulker_box`
- `minecraft:purple_shulker_box`
- `minecraft:blue_shulker_box`
- `minecraft:brown_shulker_box`
- `minecraft:green_shulker_box`
- `minecraft:red_shulker_box`
- `minecraft:black_shulker_box`

**Slots:** 27 (same as chest)

**Nesting:** **CAN contain other shulker boxes** (unlimited recursion possible)

**NBT:** Same "Items" list, but items can themselves be shulker boxes

**Extraction Pattern:**
```java
// Must check if item is shulker box and recurse
for (CompoundTag item : items) {
    String itemId = item.getString("id");
    if (itemId.contains("shulker_box")) {
        // Recurse into nested shulker
        processNestedShulker(item);
    }
}
```

### Hoppers

**Block ID:** `minecraft:hopper`

**Slots:** 5 (single row)

**NBT:** Standard "Items" list

### Dispensers and Droppers

**Block IDs:**
- `minecraft:dispenser`
- `minecraft:dropper`

**Slots:** 9 (3×3 grid)

**NBT:** Standard "Items" list

### Furnaces and Variants

**Block IDs:**
- `minecraft:furnace`
- `minecraft:blast_furnace`
- `minecraft:smoker`

**Slots:** 3 total
- Slot 0: Input
- Slot 1: Fuel
- Slot 2: Output

**NBT:** Standard "Items" list

**Note:** Books typically in output slot (crafted result)

### Brewing Stands

**Block ID:** `minecraft:brewing_stand`

**Slots:** 5 total
- Slots 0-2: Potion bottles
- Slot 3: Potion ingredient
- Slot 4: Fuel (blaze powder)

**NBT:** Standard "Items" list

**Note:** Unlikely to contain books, but technically possible via commands

### Lecterns

**Block ID:** `minecraft:lectern`

**Capacity:** 1 book only (written book or book & quill)

**NBT Structure:**
```snbt
{
  id: "minecraft:lectern",
  x: 100,
  y: 64,
  z: 200,
  Book: {
    id: "minecraft:written_book",
    Count: 1b,
    tag: {...}
  },
  Page: 0  // Current page being viewed
}
```

**Key Difference:** Uses "Book" field, NOT "Items" list

**Hopper Interaction:** None (hoppers cannot insert/remove from lecterns)

### Chiseled Bookshelves

**Block ID:** `minecraft:chiseled_bookshelf`

**Introduced:** 1.19.3 (22w42a)

**Capacity:** 6 books (slots 0-5)

**Accepted Items:**
- Written books
- Books and quills
- Enchanted books
- Knowledge books

**NBT Structure:**
```snbt
{
  id: "minecraft:chiseled_bookshelf",
  x: 100,
  y: 64,
  z: 200,
  Items: [
    {
      Slot: 0b,
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ],
  last_interacted_slot: 0  // Last slot clicked (-1 if never interacted)
}
```

**Hopper Interaction:** YES (can insert/remove via hoppers)

**Redstone:** Comparator outputs signal 1-6 based on last_interacted_slot

### Decorated Pots

**Block ID:** `minecraft:decorated_pot`

**Introduced:** 1.20 (23w07a)

**Capacity:** 1 item stack

**NBT:** Uses standard "Items" list (single slot)

### Copper Chests and Variants

**Block IDs:**
- `minecraft:copper_chest` (exposed copper chest)
- `minecraft:exposed_copper_chest`
- `minecraft:weathered_copper_chest`
- `minecraft:oxidized_copper_chest`
- Waxed variants of above

**Introduced:** 1.21 (24w33a)

**Slots:** 27 (same as regular chest)

**NBT:** Standard "Items" list

**Oxidation:** Does not affect NBT structure

---

## Entity Containers (Category 2)

### Minecarts with Chests

**Entity ID:** `minecraft:chest_minecart`

**Storage Location:** Entity files (entities/*.mca)

**NBT Structure:**
```snbt
{
  id: "minecraft:chest_minecart",
  Pos: [100.5d, 64.0d, 200.5d],
  Items: [
    {
      Slot: 0b,
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ]
}
```

**Slots:** 27 (same as chest)

### Minecarts with Hoppers

**Entity ID:** `minecraft:hopper_minecart`

**Slots:** 5 (same as hopper block)

**NBT:** Standard "Items" list in entity data

### Boats with Chests

**Entity ID:** `minecraft:chest_boat`

**Introduced:** 1.19 (22w12a)

**Wood Types:**
- oak_chest_boat
- spruce_chest_boat
- birch_chest_boat
- jungle_chest_boat
- acacia_chest_boat
- dark_oak_chest_boat
- mangrove_chest_boat
- bamboo_chest_raft
- cherry_chest_boat

**Slots:** 27

**NBT:** Standard "Items" list in entity data

### Item Frames

**Entity ID:** `minecraft:item_frame`

**Capacity:** 1 item

**NBT Structure:**
```snbt
{
  id: "minecraft:item_frame",
  Pos: [100.5d, 64.0d, 200.5d],
  Item: {
    id: "minecraft:written_book",
    Count: 1b,
    tag: {...}
  },
  ItemRotation: 0b,  // 0-7 (rotation in 45° increments)
  Fixed: 0b          // If true: indestructible, immovable
}
```

**Key Difference:** Uses "Item" field (singular), NOT "Items" list

### Glow Item Frames

**Entity ID:** `minecraft:glow_item_frame`

**Structure:** Identical to regular item frame

**Visual:** Glowing border effect (cosmetic, doesn't affect NBT)

---

## Player Containers (Category 3)

### Player Inventory

**File:** `playerdata/<UUID>.dat`

**NBT Structure:**
```snbt
{
  Inventory: [
    {
      Slot: 0b,  // -106b to 35b (various inventory sections)
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ]
}
```

**Slot Ranges:**
- -106: Off-hand
- 0-8: Hotbar
- 9-35: Main inventory
- 100-103: Armor slots
- -1 to -999: Crafting grid, cursor (transient)

### Ender Chest (Player-Specific)

**NBT Field:** `EnderItems` (in player data)

**Structure:**
```snbt
{
  EnderItems: [
    {
      Slot: 0b,  // 0-26 (27 slots)
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ]
}
```

**Note:** NOT stored in world files, stored per-player

**Slots:** 27 (same as chest)

---

## Nested Container Items (Category 4)

### Shulker Boxes (as items)

**Pre-1.20.5 Format:**
```snbt
{
  id: "minecraft:purple_shulker_box",
  Count: 1b,
  tag: {
    BlockEntityTag: {
      Items: [
        {
          Slot: 0b,
          id: "minecraft:written_book",
          Count: 1b,
          tag: {...}
        }
      ]
    }
  }
}
```

**1.20.5+ Format:**
```snbt
{
  id: "minecraft:purple_shulker_box",
  count: 1,
  components: {
    minecraft:container: [
      {
        slot: 0,
        item: {
          id: "minecraft:written_book",
          count: 1,
          components: {...}
        }
      }
    ]
  }
}
```

**Recursive Nesting:**
```java
void processContainer(CompoundTag container) {
    ListTag<?> items = getItems(container);
    for (CompoundTag item : items) {
        if (item.getString("id").contains("shulker_box")) {
            // Recurse into nested shulker
            CompoundTag nestedContainer = extractNestedContainer(item);
            processContainer(nestedContainer); // RECURSIVE CALL
        } else if (isBook(item)) {
            extractBook(item);
        }
    }
}
```

### Bundles

**Introduced:** 1.17 (experimental), 1.21.4 (fully released)

**Pre-1.20.5 Format:**
```snbt
{
  id: "minecraft:bundle",
  Count: 1b,
  tag: {
    Items: [
      {
        id: "minecraft:written_book",
        Count: 1b,
        tag: {...}
      }
    ]
  }
}
```

**1.20.5+ Format:**
```snbt
{
  id: "minecraft:bundle",
  count: 1,
  components: {
    minecraft:bundle_contents: [
      {
        id: "minecraft:written_book",
        count: 1,
        components: {...}
      }
    ]
  }
}
```

**Key Differences from Shulker Boxes:**
- **No slot numbers:** Items stored as direct list
- **Weight system:** Each item has weight (64 / max_stack_size)
- **Max capacity:** 64 weight units (e.g., 64 books or 16 unstackable items)
- **Recursive:** Bundles can contain other bundles

**Nesting Extraction:**
```java
if (item.getString("id").equals("minecraft:bundle")) {
    // 1.20.5+ format
    if (hasKey(item, "components")) {
        CompoundTag components = getCompoundTag(item, "components");
        ListTag<?> bundleContents = getListTag(components, "minecraft:bundle_contents");
        // Process each item in bundle (no slot field)
        bundleContents.each { processItem(it) }
    }
    // Pre-1.20.5 format
    else if (hasKey(item, "tag")) {
        CompoundTag tag = getCompoundTag(item, "tag");
        ListTag<?> items = getListTag(tag, "Items");
        items.each { processItem(it) }
    }
}
```

---

## Container Type Detection Patterns

### Block Entity Identification

**Pattern 1: Direct ID Check**
```java
String blockId = blockEntity.getString("id");
switch (blockId) {
    case "minecraft:chest":
    case "minecraft:trapped_chest":
    case "minecraft:barrel":
        return processStandardContainer(blockEntity);

    case "minecraft:lectern":
        return processLectern(blockEntity); // Special case: Book field

    default:
        if (blockId.contains("shulker_box")) {
            return processStandardContainer(blockEntity);
        }
}
```

**Pattern 2: Capability Check**
```java
boolean hasStandardInventory = blockEntity.containsKey("Items");
boolean hasLecternBook = blockEntity.containsKey("Book");

if (hasStandardInventory) {
    return processItemsList(blockEntity.getListTag("Items"));
} else if (hasLecternBook) {
    return processSingleBook(blockEntity.getCompoundTag("Book"));
}
```

### Entity Identification

```java
String entityId = entity.getString("id");

if (entityId.equals("minecraft:chest_minecart") ||
    entityId.equals("minecraft:hopper_minecart") ||
    entityId.contains("chest_boat")) {
    return processItemsList(entity.getListTag("Items"));
}

if (entityId.equals("minecraft:item_frame") ||
    entityId.equals("minecraft:glow_item_frame")) {
    return processSingleItem(entity.getCompoundTag("Item"));
}
```

### Nested Container Detection

```java
boolean isNestedContainer(CompoundTag item) {
    String id = item.getString("id");

    // Shulker boxes (all colors)
    if (id.contains("shulker_box")) {
        return true;
    }

    // Bundles
    if (id.equals("minecraft:bundle")) {
        return true;
    }

    // Future-proof: check for container components
    if (item.containsKey("components")) {
        CompoundTag components = item.getCompoundTag("components");
        return components.containsKey("minecraft:container") ||
               components.containsKey("minecraft:bundle_contents");
    }

    return false;
}
```

---

## Version-Specific Changes

### Minecraft 1.18 (21w43a)

**Change:** Chunk NBT restructuring
- `TileEntities` → `block_entities`
- `Level` wrapper removed

**Impact:** Must check both field names for compatibility

### Minecraft 1.19.3 (22w42a)

**Added:** Chiseled bookshelves
- New container type with 6 slots
- Hopper-compatible

### Minecraft 1.20 (23w07a)

**Added:** Decorated pots
- Single-slot container
- Stores any item type

### Minecraft 1.20.5 (24w09a)

**Major Change:** Data components system
- `tag.BlockEntityTag.Items` → `components.minecraft:container`
- `tag.Items` → `components.minecraft:bundle_contents`
- Slot fields: `Slot` → `slot`, `Count` → `count`

### Minecraft 1.21 (24w33a)

**Added:** Copper chests (all oxidation levels)
- Standard 27-slot containers
- Multiple variants based on oxidation

**Bundles:** Fully released (no longer experimental)

---

## Edge Cases and Special Scenarios

### Empty Containers

**Problem:** Empty "Items" lists vs missing "Items" field

**Solution:**
```java
ListTag<?> items = blockEntity.containsKey("Items")
    ? blockEntity.getListTag("Items")
    : new ListTag<>(CompoundTag.class);

if (items.size() == 0) {
    return; // Skip empty containers
}
```

### Corrupted Slot Numbers

**Problem:** Invalid slot numbers (negative, exceeding container size)

**Solution:**
```java
int slot = item.getByte("Slot");
if (slot < 0 || slot >= maxSlots) {
    LOGGER.warn("Invalid slot number: {}", slot);
    // Still process item, just note corruption
}
```

### Missing Item Count

**Problem:** Pre-1.20.5 uses `Count` (byte), 1.20.5+ uses `count` (int)

**Solution:**
```java
int count = item.containsKey("count")
    ? item.getInt("count")
    : (item.containsKey("Count") ? item.getByte("Count") : 1);
```

### Deeply Nested Shulker Boxes

**Problem:** Shulker A → Shulker B → Shulker C → ... → Book

**Solution:** Implement depth limiting
```java
void processContainer(CompoundTag container, int depth) {
    if (depth > 20) {
        LOGGER.error("Max nesting depth exceeded");
        return;
    }

    for (CompoundTag item : getItems(container)) {
        if (isNestedContainer(item)) {
            processContainer(extractNested(item), depth + 1);
        }
    }
}
```

### Bundles Inside Shulker Boxes Inside Bundles

**Problem:** Mixed container types, each with different NBT structures

**Solution:** Unified extraction abstraction
```java
List<CompoundTag> extractContents(CompoundTag item) {
    String id = item.getString("id");

    // Shulker boxes
    if (id.contains("shulker_box")) {
        return extractShulkerContents(item);
    }

    // Bundles
    if (id.equals("minecraft:bundle")) {
        return extractBundleContents(item);
    }

    return Collections.emptyList();
}
```

### Fixed Item Frames

**Problem:** Item frames with `Fixed: 1b` cannot be broken

**Solution:** No special handling needed (NBT extraction identical)

---

## Performance Considerations

### Recursive Descent Overhead

**Issue:** Deep nesting (20+ levels) causes stack overflow

**Mitigation:**
- Depth limiting (max 512 to match NBT spec)
- Iterative approach with explicit stack

**Iterative Pattern:**
```java
Stack<ContainerContext> toProcess = new Stack<>();
toProcess.push(new ContainerContext(rootContainer, 0));

while (!toProcess.isEmpty()) {
    ContainerContext ctx = toProcess.pop();

    for (CompoundTag item : getItems(ctx.container)) {
        if (isNestedContainer(item) && ctx.depth < MAX_DEPTH) {
            toProcess.push(new ContainerContext(extractNested(item), ctx.depth + 1));
        } else if (isBook(item)) {
            extractBook(item);
        }
    }
}
```

### Large Container Counts

**Issue:** Worlds with 10,000+ chests slow down extraction

**Mitigation:**
- Stream processing (process one chunk at a time)
- Progress reporting
- Memory-efficient data structures

---

## Testing Checklist

### Container Coverage Test

Ensure extraction handles:
- [ ] Regular chests (both chest and trapped_chest)
- [ ] Barrels
- [ ] All 17 shulker box colors
- [ ] Hoppers
- [ ] Dispensers and droppers
- [ ] Furnaces (all 3 types)
- [ ] Brewing stands
- [ ] Lecterns (special "Book" field)
- [ ] Chiseled bookshelves (6 slots)
- [ ] Decorated pots
- [ ] Copper chests (all oxidation levels)
- [ ] Minecarts (chest and hopper variants)
- [ ] Boats with chests (all wood types)
- [ ] Item frames (regular and glow)
- [ ] Player inventory
- [ ] Ender chest (player-specific)
- [ ] Bundles (as nested items)
- [ ] Shulker boxes (as nested items)

### Nesting Test Cases

- [ ] Shulker box inside shulker box (2 levels)
- [ ] Shulker box inside shulker box inside shulker box (3+ levels)
- [ ] Bundle inside shulker box
- [ ] Shulker box inside bundle
- [ ] Bundle inside bundle
- [ ] Book directly in container
- [ ] Book in shulker in chest
- [ ] Book in bundle in shulker in chest

### Version Compatibility Tests

- [ ] Pre-1.20.5 format (tag.BlockEntityTag)
- [ ] 1.20.5+ format (components.minecraft:container)
- [ ] Mixed format (old and new in same world)
- [ ] Missing optional fields (generation, resolved)
- [ ] Pre-1.18 chunk format (Level.TileEntities)
- [ ] 1.18+ chunk format (block_entities)

---

## References

- Minecraft Wiki - Block Entity Format: https://minecraft.wiki/w/Chunk_format#Block_entity_format
- Minecraft Wiki - Entity Format: https://minecraft.wiki/w/Entity_format
- Minecraft Wiki - Player Data: https://minecraft.wiki/w/Player.dat_format
- Minecraft Wiki - Item Format: https://minecraft.wiki/w/Item_format
- Minecraft Wiki - Data Components: https://minecraft.wiki/w/Data_component_format

**Document Version:** 1.0
**Last Updated:** 2025-11-18
**Minecraft Coverage:** Java Edition 1.13 through 1.21+
