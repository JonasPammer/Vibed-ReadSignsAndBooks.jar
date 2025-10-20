# Build Notes

## Quick Build

Just run one of these:
- **PowerShell**: `.\build.ps1`
- **Batch**: `build.bat`

The JAR file `ReadSignsAndBooks.jar` will be created in the current directory.

## What Was Fixed

The original source code had encoding issues with Minecraft color codes. The special character § (U+00A7) was corrupted in the source file. This has been fixed by:

1. Replacing the corrupted characters with Unicode escape sequences (`\u00A7`)
2. Adding `-encoding UTF-8` flag to the javac compiler command
3. Using Java 8 compatibility mode (`-source 8 -target 8`) for maximum compatibility

## Changes Made

### Modified Files:
- `src/Main.java` - Line 29: Fixed color code character encoding

### New Files Created:
- `MANIFEST.MF` - JAR manifest specifying Main class and classpath
- `build.ps1` - PowerShell build script
- `build.bat` - Windows batch build script
- `README.md` - User documentation
- `BUILD_NOTES.md` - This file

## Build Process

The build scripts perform these steps:

1. **Clean**: Remove old compiled classes from `bin/` directory
2. **Compile**: Compile all `.java` files from `src/` to `bin/`
   - Uses UTF-8 encoding
   - Includes `json-20180130.jar` in classpath
   - Targets Java 8 for compatibility
3. **Package**: Create JAR file with manifest
   - Includes all compiled classes
   - References `json-20180130.jar` in classpath

## Dependencies

- **json-20180130.jar** - JSON parsing library (included)
  - Must be in the same directory as the JAR when running
  - Automatically included in classpath via MANIFEST.MF

## Compatibility

- **Source**: Java 7/8 code
- **Compiled**: Java 8 bytecode (compatible with Java 8+)
- **Tested**: Java 21 (OpenJDK)
- **Minecraft**: Supports Anvil (.mca) and MCR (.mcr) region formats

## Original Project

This appears to be an old Eclipse project from around 2018 (based on the JSON library version). The original developer created a tool to extract books and signs from Minecraft world files, which is useful for:

- Backing up written content
- Searching for specific books or signs
- Documenting server content
- Recovering lost text

The project structure suggests it was developed in Eclipse IDE with Java 7.

