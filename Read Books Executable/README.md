# Read Books Executable

A Gradle-based Java tool that reads Minecraft books and signs from world region files and player data.

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
- Gradle 8.x (or use the included Gradle Wrapper)

## Building with Gradle

### Using Gradle Wrapper (Recommended - No Gradle installation needed)

**Windows:**
```cmd
gradlew.bat build
```

**Linux/Mac:**
```bash
./gradlew build
```

### Using Installed Gradle

```bash
gradle build
```

This will:
1. Compile all Java source files
2. Create `ReadSignsAndBooks.jar` in both `build/libs/` and the project root
3. Copy the JSON dependency to `build/libs/`

## Running the Application

### Option 1: Using Gradle

```bash
gradle run
```

or with the wrapper:
```bash
./gradlew run
```

### Option 2: Using the JAR directly

```bash
java -jar ReadSignsAndBooks.jar
```

## Usage

1. Ensure you have the following folders in the project directory:
   - `region/` - containing your Minecraft world region files (*.mca or *.mcr)
   - `playerdata/` - containing player data files (*.dat)

2. Run the application using one of the methods above

3. The tool will create three output files:
   - `bookOutput.txt`
   - `signOutput.txt`
   - `playerdataOutput.txt`

## Gradle Tasks

- `gradle build` - Compile and build the JAR
- `gradle run` - Run the application directly
- `gradle clean` - Clean build artifacts
- `gradle jar` - Build only the JAR file
- `gradle tasks` - List all available tasks

## Project Structure

```
Read Books Executable/
├── src/                    # Source code
│   ├── Main.java          # Main application
│   ├── Anvil/             # NBT parsing for Anvil format
│   └── MCR/               # Region file handling
├── build/                  # Gradle build output (generated)
│   └── libs/              # Built JAR files
├── gradle/                 # Gradle wrapper files
├── json-20180130.jar      # JSON library dependency
├── build.gradle           # Gradle build configuration
├── settings.gradle        # Gradle settings
├── gradlew                # Gradle wrapper script (Unix)
├── gradlew.bat            # Gradle wrapper script (Windows)
└── ReadSignsAndBooks.jar  # Compiled JAR (generated, copied to root)
```

## Development

### Building from Source

The project uses Gradle for build automation. The build configuration is in `build.gradle`.

**Key configuration:**
- Source compatibility: Java 8
- Target compatibility: Java 8
- Encoding: UTF-8
- Main class: `Main`
- Dependencies: `json-20180130.jar` (local file)

### IDE Support

This project can be imported into any IDE that supports Gradle:
- **IntelliJ IDEA**: File → Open → Select the project directory
- **Eclipse**: File → Import → Gradle → Existing Gradle Project
- **VS Code**: Open folder and use the Gradle extension

## Notes

- This is an old project originally designed for older Minecraft versions
- The code uses Java 7/8 features and compiles with compatibility mode
- The special Minecraft color code character (§) has been replaced with Unicode escape sequences for better compatibility
- The tool supports both Anvil (.mca) and older MCR (.mcr) region file formats

## Troubleshooting

**Error: "Could not find or load main class Main"**
- Make sure `json-20180130.jar` is in the same directory as the JAR file
- If using Gradle, run `gradle build` to ensure everything is properly built

**Error: "No such file or directory: region"**
- Create a `region/` folder and place your Minecraft world region files in it
- Create a `playerdata/` folder for player data files

**Gradle build fails:**
- Make sure you have Java 8 or higher installed
- Try using the Gradle wrapper: `./gradlew build` instead of `gradle build`
- Run `gradle clean build` to do a fresh build

## License

This is an old community project. Use at your own risk.

