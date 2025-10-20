# Gradle Quick Start Guide

This project has been converted to use Gradle for modern, standardized build management.

## Why Gradle?

- **Standard Build Tool**: Industry-standard build automation for Java projects
- **Dependency Management**: Automatic handling of libraries and dependencies
- **IDE Integration**: Works seamlessly with IntelliJ IDEA, Eclipse, VS Code, etc.
- **Reproducible Builds**: Gradle Wrapper ensures consistent builds across environments
- **Task Automation**: Easy to run, test, and package the application

## Quick Commands

### Build the Project
```bash
gradle build
```
or with wrapper:
```bash
./gradlew build        # Linux/Mac
gradlew.bat build      # Windows
```

### Run the Application
```bash
gradle run
```

### Clean Build Artifacts
```bash
gradle clean
```

### Build JAR Only
```bash
gradle jar
```

### List All Available Tasks
```bash
gradle tasks
```

## What Happens During Build?

1. **Compile**: All Java source files in `src/` are compiled with UTF-8 encoding
2. **Package**: Compiled classes are packaged into `ReadSignsAndBooks.jar`
3. **Copy**: The JAR is copied to both `build/libs/` and the project root
4. **Dependencies**: The `json-20180130.jar` library is copied to `build/libs/`

## Project Configuration

The build is configured in `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'application'
}

sourceCompatibility = '1.8'
targetCompatibility = '1.8'

sourceSets {
    main {
        java {
            srcDirs = ['src']  // Source code in src/ directory
        }
    }
}

dependencies {
    implementation files('json-20180130.jar')  // Local JSON library
}

application {
    mainClass = 'Main'  // Entry point
}
```

## Gradle Wrapper

The Gradle Wrapper is included in this project, which means:
- **No Gradle installation required** - The wrapper downloads the correct Gradle version automatically
- **Consistent builds** - Everyone uses the same Gradle version
- **Version controlled** - The wrapper configuration is part of the project

### Wrapper Files
- `gradlew` - Unix/Linux/Mac wrapper script
- `gradlew.bat` - Windows wrapper script
- `gradle/wrapper/` - Wrapper configuration and JAR

### Using the Wrapper

**Windows:**
```cmd
gradlew.bat build
gradlew.bat run
gradlew.bat clean
```

**Linux/Mac:**
```bash
./gradlew build
./gradlew run
./gradlew clean
```

## IDE Integration

### IntelliJ IDEA
1. Open IntelliJ IDEA
2. File → Open
3. Select the `Read Books Executable` directory
4. IntelliJ will automatically detect the Gradle project
5. Wait for Gradle to sync
6. You can now run/debug from the IDE

### Eclipse
1. Open Eclipse
2. File → Import → Gradle → Existing Gradle Project
3. Select the `Read Books Executable` directory
4. Click Finish
5. Wait for Gradle to sync

### VS Code
1. Open VS Code
2. Install the "Gradle for Java" extension
3. Open the `Read Books Executable` folder
4. The Gradle extension will detect the project
5. Use the Gradle sidebar to run tasks

## Output Files

After building, you'll find:
- `ReadSignsAndBooks.jar` - In project root (for convenience)
- `build/libs/ReadSignsAndBooks.jar` - Official build output
- `build/libs/json-20180130.jar` - Dependency copy
- `build/classes/` - Compiled .class files

## Comparison with Old Build System

### Old Way (Custom Scripts)
```bash
# PowerShell
.\build.ps1

# Batch
build.bat

# Manual
javac -encoding UTF-8 -d bin -cp json-20180130.jar -source 8 -target 8 src/**/*.java
jar cfm ReadSignsAndBooks.jar MANIFEST.MF -C bin .
```

### New Way (Gradle)
```bash
gradle build
```

**Benefits:**
- ✅ One command for everything
- ✅ Works on all platforms (Windows, Linux, Mac)
- ✅ IDE integration out of the box
- ✅ Standardized project structure
- ✅ Easy to add dependencies
- ✅ Built-in testing support
- ✅ Reproducible builds with wrapper

## Common Tasks

### Full Clean Build
```bash
gradle clean build
```

### Run Without Building
```bash
gradle run
```

### Build and Run
```bash
gradle build run
```

### Check for Issues
```bash
gradle check
```

### Generate Distribution
```bash
gradle distZip
```
This creates a distribution ZIP in `build/distributions/` with the JAR and all dependencies.

## Troubleshooting

### "Gradle command not found"
Use the wrapper instead: `./gradlew` (Linux/Mac) or `gradlew.bat` (Windows)

### "Permission denied: ./gradlew"
Make the wrapper executable:
```bash
chmod +x gradlew
```

### Build directory locked
Close any programs that might have files open in the `build/` directory, then try again.

### Java version issues
Make sure you have Java 8 or higher:
```bash
java -version
```

## Next Steps

1. **Build the project**: `gradle build`
2. **Run it**: `gradle run` or `java -jar ReadSignsAndBooks.jar`
3. **Import into your IDE** for development
4. **Modify the code** and rebuild with `gradle build`

For more information, see the main [README.md](README.md).

