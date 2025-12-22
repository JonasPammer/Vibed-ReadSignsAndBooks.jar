# Minecraft Resources - Authoritative Links

**Purpose**: Curated high-quality links for Minecraft-related research. Use these FIRST before searching.

## Official Wiki (minecraft.wiki) - PRIMARY SOURCE

### NBT & Data Formats
- **NBT Format**: https://minecraft.wiki/w/NBT_format
  - Binary tag specification, SNBT conversion, all 13 tag types
- **Entity Format**: https://minecraft.wiki/w/Entity_format
  - Entity NBT structure, DataVersion, Position arrays
- **Chunk Format**: https://minecraft.wiki/w/Chunk_format
  - Region file structure, chunk data layout
- **Region File Format**: https://minecraft.wiki/w/Region_file_format
  - .mca file structure, compression, chunk locations

### Commands & Syntax
- **Commands Overview**: https://minecraft.wiki/w/Commands
- **Give Command**: https://minecraft.wiki/w/Commands/give
- **Setblock Command**: https://minecraft.wiki/w/Commands/setblock
- **Tellraw Command**: https://minecraft.wiki/w/Commands/tellraw
- **Raw JSON Text Format**: https://minecraft.wiki/w/Raw_JSON_text_format
  - clickEvent, hoverEvent, text components

### Books & Signs
- **Written Book**: https://minecraft.wiki/w/Written_Book
  - NBT structure: title, author, pages, generation
  - Command format changes across versions
- **Book and Quill**: https://minecraft.wiki/w/Book_and_Quill
  - writable_book_content component (1.20.5+)
- **Sign**: https://minecraft.wiki/w/Sign
  - Block data, front_text/back_text (1.20+), Text1-Text4 (legacy)

### Datapacks
- **Data Pack**: https://minecraft.wiki/w/Data_pack
  - Directory structure, namespace rules, load order
- **Pack Format**: https://minecraft.wiki/w/Pack_format
  - **CRITICAL**: Complete pack_format version table
  - Directory naming change in 1.21 (functions → function)
- **Function Files**: https://minecraft.wiki/w/Function_(Java_Edition)
  - .mcfunction syntax, no command length limit
- **Tutorial: Creating a Data Pack**: https://minecraft.wiki/w/Tutorial:Creating_a_data_pack

### Version-Specific Changes
- **Java Edition 24w21a**: https://minecraft.wiki/w/Java_Edition_24w21a
  - Directory rename: functions/ → function/ (1.21+)
- **Template:Data pack format**: https://minecraft.wiki/w/Template:Data_pack_format
  - Detailed changelog of command/format changes per version

## Technical Wiki (wiki.vg)

- **NBT Specification**: https://wiki.vg/NBT
  - Alternative technical documentation
- **Protocol**: https://wiki.vg/Protocol
  - Network protocol (useful for understanding data structures)

## Command Generators (gamergeeks.net) - EXCELLENT TOOLS

- **Give Command Generator**: https://www.gamergeeks.net/apps/minecraft/give-command-generator
  - Enchantments, custom names, lore, attributes
- **Setblock Generator**: https://www.gamergeeks.net/apps/minecraft/setblock-command-generator
  - Block states, NBT data for placed blocks
- **Tellraw Generator**: https://www.gamergeeks.net/apps/minecraft/tellraw-command-generator
  - JSON text with clickEvent, hoverEvent
- **Raw JSON Text Generator**: https://www.gamergeeks.net/apps/minecraft/raw-json-text-format-generator
  - Visual editor for JSON text components
- **Knowledge Books**: https://www.gamergeeks.net/apps/minecraft/give-command-generator/knowledge-books
- **Enchanted Books**: https://www.gamergeeks.net/apps/minecraft/give-command-generator/enchanted-books
- **Chests/Shulkers**: https://www.gamergeeks.net/apps/minecraft/give-command-generator/chests-shulkers
  - Prefilled container generation

## Datapack Conventions (mc-datapacks.github.io)

- **Namespace Conventions**: https://mc-datapacks.github.io/en/conventions/namespace.html
  - Valid characters: lowercase a-z, 0-9, underscore, hyphen, period
  - **FORBIDDEN**: Uppercase letters, spaces, special characters

## Command Format Quick Reference

### Written Book Commands by Version

**1.13 (NBT with JSON pages)**:
```
/give @p written_book{title:"Title",author:"Author",pages:['{"text":"Page 1"}']}
```

**1.14 (Different JSON wrapping)**:
```
/give @p written_book{title:"Title",author:"Author",pages:['["Page 1"]']}
```

**1.20.5+ (Component-based with minecraft: prefix)**:
```
/give @p written_book[minecraft:written_book_content={title:"Title",author:"Author",pages:["Page 1"]}]
```

**1.21+ (Component-based, no minecraft: prefix required)**:
```
/give @p written_book[written_book_content={title:"Title",author:"Author",pages:["Page 1"]}]
```

### Sign Commands by Version

**1.13-1.14 (Text1-Text4)**:
```
/setblock ~ ~ ~ oak_sign{Text1:'{"text":"Line 1"}',Text2:'{"text":"Line 2"}'}
```

**1.20+ (front_text/back_text)**:
```
/setblock ~ ~ ~ oak_sign{front_text:{messages:['{"text":"Line 1"}','{"text":"Line 2"}','{"text":""}','{"text":""}']}}
```

### Pack Format Table (Most Common)

| pack_format | Minecraft Version | Function Directory |
|-------------|-------------------|-------------------|
| 4 | 1.13 – 1.14.4 | `functions/` |
| 15 | 1.20 – 1.20.1 | `functions/` |
| 41 | 1.20.5 – 1.20.6 | `functions/` |
| 48 | 1.21 – 1.21.1 | `function/` |

**Full table**: See minecraft-datapacks.md or https://minecraft.wiki/w/Pack_format

## Structure Files & Schematics

### Official Documentation
- **Structure File Format**: https://minecraft.wiki/w/Structure_file
  - Vanilla .nbt structure format, size limits (48x48x48)
- **Block Entity Format**: https://minecraft.wiki/w/Block_entity_format
  - Sign NBT (front_text, back_text), Lectern NBT (Book compound)
- **Structure Block**: https://minecraft.wiki/w/Structure_Block
  - In-game structure loading/saving

### Schematic Format Tools & Libraries
- **SchemConvert**: https://github.com/PiTheGuy/SchemConvert
  - **RECOMMENDED**: Java 21 tool supporting .nbt, .schem, .litematic, .bp
  - Reference implementation for format conversion
  - **Local clone**: `/tmp/SchemConvert` (for analysis)
- **Litematica Mod**: https://github.com/maruohon/litematica
  - Original mod for .litematic format
- **Sakura-Ryoko Litematica Fork**: https://github.com/sakura-ryoko/litematica
  - Updated fork for 1.20.5+ compatibility
- **Litemapy**: https://github.com/SmylerMC/litemapy
  - Python library for .litematic files
  - Docs: https://litemapy.readthedocs.io/en/latest/litematics.html

### Format Converters
- **Lite2Edit**: https://github.com/GoldenDelicios/Lite2Edit
  - Converts Litematics to WorldEdit schematics
- **litematic-converter**: https://github.com/paxxxw/litematic-converter
  - Python tool for .bp and .schem conversion
- **ObjToSchematic**: https://github.com/LucasDower/ObjToSchematic
  - 3D models to .schematic, .litematic, .schem, .nbt

### Format Quick Reference

| Format | Extension | Max Size | Library |
|--------|-----------|----------|---------|
| Vanilla Structure | `.nbt` | 48x48x48 | Querz NBT 6.1 |
| Litematica | `.litematic` | Unlimited | Querz NBT 6.1 |
| Sponge Schematic | `.schem` | Unlimited | Querz NBT 6.1 |

**Detailed format docs**: See @nbt-litematica-formats.md

## Related Projects (Reference Implementations)

- **Text2Book**: https://github.com/TheWilley/Text2Book
  - Minecraft book converter (referenced in project)
- **MinecraftBookConverter**: https://github.com/ADP424/MinecraftBookConverter
  - Another book converter implementation
- **Querz NBT Library**: https://github.com/Querz/NBT
  - Java NBT parsing library used by this project

## NBT Tools

- **NBTExplorer**: GUI tool for viewing/editing NBT files
- **NBT Studio**: Successor to NBTExplorer with Bedrock support
- **webNBT**: Online NBT viewer/editor

## Search Tips for Future Agents

1. **For command syntax**: Start with minecraft.wiki/w/Commands/[command_name]
2. **For NBT structure**: Start with minecraft.wiki/w/[Item_or_Block]
3. **For version changes**: Check minecraft.wiki/w/Java_Edition_[version]
4. **For generators**: Use gamergeeks.net tools to verify command format
5. **For pack_format**: ALWAYS check minecraft.wiki/w/Pack_format before generating datapacks

## Last Verified

- **Date**: 2025-12-22
- **Minecraft Versions**: 1.13 through 1.21.4
- **All links tested**: Via Tavily search during research session
- **SchemConvert cloned**: For NBT/Litematica format analysis
