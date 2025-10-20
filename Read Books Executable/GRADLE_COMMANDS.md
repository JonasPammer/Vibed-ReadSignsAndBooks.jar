# Gradle Commands Cheat Sheet

Quick reference for common Gradle commands for this project.

## Basic Commands

### Build
```bash
gradle build                    # Build the entire project
gradle build --quiet            # Build without verbose output
gradle build --info             # Build with detailed info
./gradlew build                 # Build using wrapper (no Gradle install needed)
```

### Run
```bash
gradle run                      # Run the application
gradle run --quiet              # Run without Gradle output
./gradlew run                   # Run using wrapper
```

### Clean
```bash
gradle clean                    # Remove build directory
gradle clean build              # Clean then build
```

### JAR
```bash
gradle jar                      # Build JAR only (faster than full build)
```

## Information Commands

### List Tasks
```bash
gradle tasks                    # List main tasks
gradle tasks --all              # List all tasks including internal ones
```

### Project Info
```bash
gradle projects                 # Show project structure
gradle properties               # Show project properties
gradle dependencies             # Show dependency tree
```

### Version
```bash
gradle --version                # Show Gradle version
./gradlew --version             # Show wrapper Gradle version
```

## Gradle Wrapper Commands

The wrapper allows building without installing Gradle.

### Windows
```cmd
gradlew.bat build               # Build
gradlew.bat run                 # Run
gradlew.bat clean               # Clean
gradlew.bat tasks               # List tasks
```

### Linux/Mac
```bash
./gradlew build                 # Build
./gradlew run                   # Run
./gradlew clean                 # Clean
./gradlew tasks                 # List tasks
```

### Make Wrapper Executable (Linux/Mac)
```bash
chmod +x gradlew
```

## Advanced Commands

### Build with Options
```bash
gradle build --parallel         # Build in parallel (faster)
gradle build --offline          # Build without network access
gradle build --refresh-dependencies  # Force refresh dependencies
gradle build --rerun-tasks      # Force re-run all tasks
```

### Debugging
```bash
gradle build --debug            # Full debug output
gradle build --stacktrace       # Show stack traces on errors
gradle build --scan             # Generate build scan (online)
```

### Continuous Build
```bash
gradle build --continuous       # Rebuild on file changes
gradle -t build                 # Short form of --continuous
```

## Project-Specific Tasks

### Compile Only
```bash
gradle compileJava              # Compile Java sources only
```

### Distribution
```bash
gradle distZip                  # Create distribution ZIP
gradle distTar                  # Create distribution TAR
gradle installDist              # Install distribution locally
```

### Application
```bash
gradle startScripts             # Generate start scripts
```

## Common Workflows

### Fresh Build
```bash
gradle clean build
```

### Build and Run
```bash
gradle build run
```

### Quick Test Run
```bash
gradle run --quiet
```

### Build for Distribution
```bash
gradle clean build distZip
```

### Check Everything
```bash
gradle clean build check
```

## Troubleshooting Commands

### Clear Gradle Cache
```bash
gradle clean --refresh-dependencies
```

### Stop Gradle Daemon
```bash
gradle --stop
```

### Check Daemon Status
```bash
gradle --status
```

### Force Clean Build
```bash
gradle clean build --rerun-tasks
```

## Output Locations

After building, find outputs here:

- **JAR File**: `build/libs/ReadSignsAndBooks.jar` (also copied to project root)
- **Compiled Classes**: `build/classes/java/main/`
- **Distribution**: `build/distributions/`
- **Reports**: `build/reports/`

## Environment Variables

### Set Java Home
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-21
```

### Gradle Options
```bash
# Windows
set GRADLE_OPTS=-Xmx2g

# Linux/Mac
export GRADLE_OPTS="-Xmx2g"
```

## Configuration Files

- `build.gradle` - Main build configuration
- `settings.gradle` - Project settings
- `gradle.properties` - Gradle properties (create if needed)
- `gradle/wrapper/gradle-wrapper.properties` - Wrapper configuration

## Tips

1. **Use the wrapper** (`./gradlew`) for consistent builds across machines
2. **Use `--quiet`** to reduce output noise
3. **Use `--parallel`** for faster builds on multi-core systems
4. **Use `--offline`** when working without internet
5. **Use `--continuous`** for rapid development cycles
6. **Use `gradle --stop`** if Gradle daemon is misbehaving

## Quick Reference Table

| Task | Command | Description |
|------|---------|-------------|
| Build | `gradle build` | Compile and package |
| Run | `gradle run` | Execute the application |
| Clean | `gradle clean` | Remove build artifacts |
| JAR | `gradle jar` | Build JAR only |
| Tasks | `gradle tasks` | List available tasks |
| Help | `gradle help` | Show help |
| Version | `gradle --version` | Show Gradle version |

## For More Information

- Official Gradle Docs: https://docs.gradle.org
- Gradle User Guide: https://docs.gradle.org/current/userguide/userguide.html
- Gradle Build Language: https://docs.gradle.org/current/dsl/

## Project-Specific Notes

- This project uses Java 8 compatibility
- Source files are in `src/` (not `src/main/java/`)
- The JSON library is a local file dependency
- UTF-8 encoding is enforced for compilation
- The JAR is automatically copied to project root after build

