# Debug Mode Rules (Non-Obvious Only)

## Log Configuration
- Log file path set via `System.setProperty('LOG_FILE', ...)` at runtime in [`Main.groovy:93`](../../src/main/groovy/Main.groovy:93)
- Logback config reloaded dynamically via [`reloadLogbackConfiguration()`](../../src/main/groovy/Main.groovy:131) after setting LOG_FILE
- Logs go to `{outputFolder}/logs.txt`, NOT standard location
- Summary written to separate `summary.txt` file

## Debug Output Locations
- Progress bars output to console (not captured in logs)
- Test output goes to `build/test-worlds/` (gitignored for inspection)
- Each test world gets its own subdirectory with full output structure

## Silent Failures
- Missing world folders (playerdata, entities) logged as warnings, not errors
- Empty pages/signs are skipped without error (just counted)
- NBT read failures caught and logged as debug, processing continues
- Duplicate detection via Set.add() return value - no explicit logging

## Test Debugging
- Tests change `System.getProperty('user.dir')` - restored in finally block at [`ReadBooksIntegrationSpec.groovy:489`](../../src/test/groovy/ReadBooksIntegrationSpec.groovy:489)
- Static state must be reset or tests contaminate each other
- Test world discovery scans `build/resources/test` via classloader reflection