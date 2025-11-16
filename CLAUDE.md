# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.kilocode/rules/memory-bank/brief.md
@.kilocode/rules/memory-bank/architecture.md
@.kilocode/rules/memory-bank/tech.md
@.kilocode/rules/memory-bank/product.md
@.kilocode/rules/memory-bank/gui.md

## Vendoring Gradle Dependencies for Claude Code Web

Due to Java HTTP client incompatibility with Claude Code Web's JWT proxy authentication, this project vendors all Gradle dependencies into the repository for offline builds.

### Setup (Run on Your Local Machine)

**Prerequisites on Windows:**
```bash
# Enable Git long paths support (required for Windows only)
git config core.longpaths true
```

**Build and vendor dependencies:**
```bash
# 1. Build the project (downloads all dependencies to ~/.gradle/caches)
./gradlew build

# 2. Copy dependencies from Gradle cache to repository
# On Windows:
powershell -Command "Copy-Item -Path \"$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\*\" -Destination \"gradle\repository\" -Recurse -Force"
# On Linux/macOS:
# cp -r ~/.gradle/caches/modules-2/files-2.1/* gradle/repository/

# 3. Commit the raw directory structure (Git handles deduplication)
git add gradle/repository/
git commit -m "feat: vendor all Gradle dependencies for offline builds"
git push
```

### Using Vendored Dependencies

The `.claude/scripts/install_pkgs.sh` script automatically applies offline configuration to `build.gradle` when running in Claude Code Web.

No manual intervention needed in CCW environment.

### Updating Dependencies

When build.gradle changes:

```bash
# On local machine
./gradlew build --refresh-dependencies

# Copy updated dependencies
# On Windows:
powershell -Command "Copy-Item -Path \"$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\*\" -Destination \"gradle\repository\" -Recurse -Force"
# On Linux/macOS:
# cp -r ~/.gradle/caches/modules-2/files-2.1/* gradle/repository/

# Commit changes (Git only stores changed files, very efficient)
git add gradle/repository/
git commit -m "chore: update vendored dependencies"
git push
```

### How It Works

- **gradle/repository/** - All Gradle dependencies committed as raw directory structure
- **Git deduplication** - Git efficiently handles binary files, only storing changed JARs on updates
- **.claude/scripts/install_pkgs.sh** - Applies offline configuration in CCW
- **offline-repositories.gradle** - Configures Gradle to check `gradle/repository/` first
- **core.longpaths** - Windows Git setting enables paths >260 characters
