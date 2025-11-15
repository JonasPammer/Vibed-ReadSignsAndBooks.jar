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

```bash
# 1. Build the project (downloads all dependencies to ~/.gradle/caches)
./gradlew build

# 2. Copy dependencies from Gradle cache to repository
cp -r ~/.gradle/caches/modules-2/files-2.1/* gradle/repository/

# 3. Commit and push
git add gradle/repository/
git commit -m "feat: vendor all Gradle dependencies for offline builds"
git push
```

### Using Vendored Dependencies

Once vendored dependencies are committed, apply the offline configuration in `build.gradle`:

```groovy
apply from: '.claude/offline-repositories.gradle'
```

### Updating Dependencies

When build.gradle changes:

```bash
# On local machine
./gradlew build --refresh-dependencies
cp -r ~/.gradle/caches/modules-2/files-2.1/* gradle/repository/
git add gradle/repository/
git commit -m "chore: update vendored dependencies"
git push
```

### How It Works

- **offline-repositories.gradle** - Configures Gradle to check `gradle/repository/` first
- Gradle uses standard cache structure, just committed to the repo
- No custom scripts needed - just copy the cache and commit it
