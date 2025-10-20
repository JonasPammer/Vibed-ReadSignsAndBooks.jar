# Read Books Executable - Build Instructions

This project reads Minecraft books and signs from world region files and player data.

## What This Does

This tool scans Minecraft world files and extracts:
- Written books (with title and author)
- Writable books (book and quill)
- Signs with text
- Books in chests, shulker boxes, item frames, entities, and player inventories

The output is saved to text files:
- `bookOutput.txt` - All books found
- `signOutput.txt` - All signs found
- `playerdataOutput.txt` - Books from player inventories

## Prerequisites

- Java 8 or higher (tested with Java 21)
- The `json-20180130.jar` library (included in this directory)

## Building the JAR

### Option 1: Using PowerShell (Recommended for Windows)

```powershell
.\build.ps1
```

### Option 2: Using Batch File

```cmd
build.bat
```

### Option 3: Manual Build

```bash
# Clean and create bin directory
mkdir bin

# Compile Java files
javac -encoding UTF-8 -d bin -cp json-20180130.jar -source 8 -target 8 src/**/*.java

# Create JAR file
jar cfm ReadSignsAndBooks.jar MANIFEST.MF -C bin .
```

## Running the Tool

1. Place the `ReadSignsAndBooks.jar` file in a directory with:
   - `region/` folder - containing your Minecraft world region files (*.mca or *.mcr)
   - `playerdata/` folder - containing player data files (*.dat)
   - `json-20180130.jar` - the JSON library dependency

2. Run the JAR:
   ```bash
   java -jar ReadSignsAndBooks.jar
   ```

3. The tool will create three output files:
   - `bookOutput.txt`
   - `signOutput.txt`
   - `playerdataOutput.txt`

## Project Structure

```
Read Books Executable/
├── src/                    # Source code
│   ├── Main.java          # Main application
│   ├── Anvil/             # NBT parsing for Anvil format
│   └── MCR/               # Region file handling
├── bin/                    # Compiled classes (generated)
├── json-20180130.jar      # JSON library dependency
├── MANIFEST.MF            # JAR manifest file
├── build.ps1              # PowerShell build script
├── build.bat              # Batch build script
└── ReadSignsAndBooks.jar  # Compiled JAR (generated)
```

## Notes

- This is an old project originally designed for older Minecraft versions
- The code uses Java 7/8 features and compiles with compatibility mode
- The special Minecraft color code character (§) has been replaced with Unicode escape sequences for better compatibility
- The tool supports both Anvil (.mca) and older MCR (.mcr) region file formats

## Troubleshooting

**Error: "Could not find or load main class Main"**
- Make sure `json-20180130.jar` is in the same directory as the JAR file

**Error: "No such file or directory: region"**
- Create a `region/` folder and place your Minecraft world region files in it
- Create a `playerdata/` folder for player data files

**Build fails with encoding errors:**
- Make sure you're using the provided build scripts which specify UTF-8 encoding
- The source code has been updated to use Unicode escape sequences for special characters

## License

This is an old community project. Use at your own risk.

