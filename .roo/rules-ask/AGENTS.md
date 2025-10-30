# Ask Mode Rules (Non-Obvious Only)

## Project Organization (Counterintuitive)
- **Actual project root is `Read Books Executable/`**, not repository root
- All Gradle commands must run from `Read Books Executable/` directory
- Single monolithic [`Main.groovy`](../../src/main/groovy/Main.groovy) file (1637 lines) contains entire application
- No separate classes for book/sign parsing - all in Main class

## Minecraft Data Format Evolution
- Application handles THREE different format changes:
  1. Chunk format 21w43a/1.18: removed "Level" wrapper, renamed keys
  2. Sign format 1.20: changed from Text1-4 to front_text/back_text
  3. Item format 1.20.5: changed from "tag" to "components"
- Version detection done by checking key existence, not version numbers

## NBT Library Usage
- Uses Querz NBT library (com.github.Querz:NBT:6.1) from JitPack
- Library provides MCA file reading, not just NBT parsing
- Custom helpers wrap library for null safety and format compatibility

## Test World Structure
- Test worlds are ACTUAL Minecraft save files (region/, playerdata/, entities/)
- Folder naming convention encodes expected output: `WORLDNAME-BOOKCOUNT-SIGNCOUNT`
- Example: `1_21_10-44-3` expects 44 books and 3 signs
- Book count includes duplicates saved to `.duplicates/` folder