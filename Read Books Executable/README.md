# Read Books Executable

A Gradle-based Java tool that reads Minecraft books and signs from world region files and player data.

## What This Does

This tool scans Minecraft world files and extracts:
- Written books (with title and author)
- Writable books (book and quill)
- Signs with text
- Books in chests, shulker boxes, item frames, entities, and player inventories

## Building with Gradle

```cmd
gradlew.bat build
```

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

