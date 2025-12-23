# GUI Implementation

## Overview
ReadSignsAndBooks.jar includes a modern JavaFX GUI that auto-launches when the JAR is double-clicked (no command-line arguments). The GUI provides a user-friendly interface for the same extraction functionality available via CLI.

## Architecture

### Smart Launch Detection
**Location:** `src/main/groovy/Main.groovy`

```groovy
static void main(String[] args) {
    if (shouldUseGui(args)) {
        Application.launch(GUI, args)
    } else {
        runCli(args)
    }
}

static boolean shouldUseGui(String[] args) {
    return args.length == 0 || args.contains('--gui') || args.contains('-g')
}
```

**Behavior:**
- No arguments → GUI mode
- `--gui` or `-g` flag → Force GUI mode
- Any other arguments → CLI mode

### GUI Class Structure
**Location:** `src/main/groovy/GUI.groovy`

**Framework:** Pure JavaFX 21 (no GroovyFX - it's unmaintained and Java 8-only)

**Static Fields:**
- `worldPathField` - TextField for world directory path
- `outputPathField` - TextField for output folder path
- `logArea` - TextArea showing live Logback output
- `statusLabel` - Label showing extraction status
- `worldDir` - Selected world directory File
- `outputFolder` - Selected output folder File
- `actualOutputFolder` - Computed output folder for "Open" button

### Custom Application Icon
**Location:** `src/main/resources/icons/`
**Integration:** GUI.groovy lines 45-53

**Icon Files:**
- `icon.svg` - Animated SVG with book, sign, scanner beam, and Galactic Alphabet text (8,662 bytes)
- `icon-512.png` - High-resolution PNG for GUI window icon (44,521 bytes)
- `icon.ico` - Multi-resolution Windows icon for EXE packaging (122,625 bytes)

**Implementation:**
```groovy
try {
    def iconStream = getClass().getResourceAsStream('/icons/icon-512.png')
    if (iconStream) {
        stage.icons.add(new javafx.scene.image.Image(iconStream))
    }
} catch (Exception e) {
    // Silently fail if icon cannot be loaded - not critical
}
```

**Easter Eggs in SVG:**
- "vibe" (⍊╎ʖᒷ) - Vibe coded attribution
- "matt" (ᒲᔑℸ ̣ ℸ ̣) - Original author /u/worldseed
- "sign" (ᓭ╎⊣リ) - Signs extraction feature

### Key Components

#### 1. Menu Bar
- **Help Menu:**
  - "View on GitHub" - Opens project repository in browser
  - "About" - Shows version, attribution (Matt /u/worldseed, Querz NBT, Claude 4.5), and legal disclaimers
  - "Third-Party Licenses" - Opens dialog displaying all dependency licenses (italic monospace font)

#### 2. Path Configuration
- **World Directory:** Optional - uses current working directory if not set (same as CLI `-w`)
- **Output Folder:** Optional - shows dynamic prompt text with default path (same as CLI `-o`)

#### 3. Item Index Database Section (Visual Group with Dependencies)
Bordered panel with header "Item Index Database":
- **Build Item Index:** Checkbox (main toggle) - passes `--index-items` flag to CLI
  - Tooltip: "Creates items.db in output folder. Query later with: --item-query"
  - **Dependent options** (indented, disabled until main checkbox checked):
    - **Skip Common Items:** Checkbox (default: checked) - passes `--skip-common-items false` when unchecked
      - Tooltip: "Excludes bulk materials to reduce database size"
    - **Item Limit:** Spinner (default: 1000) - passes `--item-limit N` to CLI
      - Tooltip: "Maximum items per type to store."

#### 5. Block Search Section (Visual Group with Dependencies)
Bordered panel with header "Block Search (skipped if nothing selected)":
- **Find Portals:** Checkbox - passes `--find-portals` flag to CLI
  - Tooltip: "Scans for portal blocks and groups them into portal structures"
- **Search Blocks:** Text field - passes `--search-blocks TYPE1,TYPE2` to CLI
  - Tooltip: "Comma-separated list of block IDs to find"
- **Dependent options** (indented, disabled until portals checkbox or search field has content):
  - **Block Limit:** Spinner (default: 5000) - passes `--index-limit N` to CLI
    - Tooltip: "Maximum blocks per type to record."
  - **Dimension Checkboxes:** Overworld/Nether/End (all checked by default) - passes `--search-dimensions` to CLI
- **Output:** All 3 formats (CSV, JSON, TXT) generated automatically (no dropdown)

#### 6. Execution Bar (Horizontal)
Combines primary action with execution options:
- **Extract** (Green, bold) - Runs extraction in background thread
- **Track & Suppress Failing Regions:** Checkbox - passes `--track-failed-regions` flag to CLI
  - Tooltip: "If a region file fails repeatedly, suppress future errors"
  - Located next to Extract button since it's execution behavior, not extraction config
- **[Vertical Separator]**
- **Open Output Folder** - Opens output directory in file explorer
- **Clear Log** - Clears the log TextArea
- **Exit** - Closes application

#### 7. Extraction Log
**Real-time Logback integration** - Shows live logging output from CLI execution with rolling buffer to prevent UI freeze.

### Live Logging Implementation

#### Custom Logback Appender
**Location:** `src/main/groovy/GuiLogAppender.groovy`

Batching appender that buffers log messages and flushes to the GUI periodically via `Platform.runLater`.

#### GUI Freeze Fixes (GitHub Issue #12)

**Problem 1 (Original):** TextArea.appendText() has O(n) performance. With large worlds generating 166K+ log messages, the TextArea would accumulate megabytes of text, causing each append to trigger expensive layout recalculation.

**Solution 1:** Rolling buffer limits TextArea content to 80KB in GUI.groovy:
```groovy
final int MAX_LOG_CHARS = 80000  // ~80KB of log text

GuiLogAppender.setLogHandler { message ->
    def currentLength = logArea.text.length()
    def newLength = currentLength + message.length()

    if (newLength > MAX_LOG_CHARS) {
        // Remove oldest ~20% of text to make room
        def trimAmount = (int)(MAX_LOG_CHARS * 0.2) + message.length()
        logArea.deleteText(0, Math.min(trimAmount, currentLength))
    }
    logArea.appendText(message)
}
```

**Problem 2 (Recurrence):** Each log message triggered a separate `Platform.runLater()` call. During *.mca processing with thousands of messages/second, the FX event queue flooded with pending updates, starving user input events. Symptoms: GUI only updated when resizing window, otherwise frozen but extraction continued normally.

**Solution 2:** Simple time-based batching in GuiLogAppender:
```groovy
private static StringBuilder buffer = new StringBuilder()
private static long lastFlush = 0
private static final long FLUSH_INTERVAL = 100  // ms

void append(ILoggingEvent event) {
    synchronized (buffer) {
        buffer.append(event.formattedMessage).append('\n')
        if (System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL) {
            def text = buffer.toString()
            buffer.setLength(0)
            lastFlush = System.currentTimeMillis()
            Platform.runLater { logHandler(text) }
        }
    }
}
```

**Result:** Reduces Platform.runLater calls from thousands/second to ~10/second. GUI stays responsive throughout extraction.

### Command-Line Argument Parsing (Picocli Integration)

**Location:** `GUI.groovy` lines 183-217

GUI mode now parses command-line arguments using Picocli, reusing Main's `@Option` definitions to avoid duplication:

```groovy
void parseGuiArguments() {
    def args = getParameters().getRaw() as String[]

    // Parse args into Main's static fields using Picocli
    new picocli.CommandLine(new Main()).parseArgs(args)

    // Apply parsed values to GUI controls
    if (Main.customWorldDirectory) { worldDir = new File(...) }
    if (Main.customOutputDirectory) { outputFolder = new File(...) }
}
```

**Supported GUI Arguments:**
- `-w, --world <path>` - Pre-set world directory
- `-o, --output <path>` - Pre-set output directory
- `--start` - Auto-start extraction after 3-second countdown

### Auto-Start Feature (`--start` flag)

**Location:** `GUI.groovy` lines 223-245

Enables hands-free extraction for testing and batch workflows:

```groovy
void handleAutoStart() {
    logArea.appendText("Auto-start enabled. Beginning extraction in 3 seconds...\n")

    int[] countdown = [3]
    def countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), { event ->
        countdown[0]--
        statusLabel.text = countdown[0] > 0 ?
            "Auto-starting in ${countdown[0]}..." : "Starting extraction..."
    }))
    countdownTimer.cycleCount = 3
    countdownTimer.onFinished = { runExtraction() }

    statusLabel.text = "Auto-starting in 3..."
    countdownTimer.play()
}
```

**Usage Example:**
```bash
java -jar ReadSignsAndBooks.jar --gui -w "C:\path\to\world" --start
```

### Extraction Flow

```groovy
void runExtraction() {
    Thread.start {
        def args = []
        if (worldDir) args += ['-w', worldDir.absolutePath]
        if (outputFolder) args += ['-o', outputFolder.absolutePath]
        // Always extract custom names (no toggle needed)
        args += ['--extract-custom-names']

        Main.runCli(args as String[])  // Direct CLI call, not Main.main()

        Platform.runLater {
            statusLabel.text = "Complete! ${Main.bookHashes.size()} books..."
            showAlert('Success', "Extraction complete!...")
        }
    }
}
```

**Important:** Calls `Main.runCli()` directly to avoid double JavaFX Application launch error.

## Window Behavior

### Sizing & Layout
- **Initial size:** 950×750
- **Minimum size:** 900×650 (prevents UI breaking)
- **Resizable:** Yes

### Responsive Design
- **Text fields (world/output paths):** Grow horizontally with window
  ```groovy
  HBox.setHgrow(worldPathField, Priority.ALWAYS)
  ```
- **Log area:** Grows vertically to fill available space
  ```groovy
  VBox.setVgrow(logArea, Priority.ALWAYS)
  ```

### Dynamic Prompts
Output folder field shows computed default path:
```groovy
void updateOutputFolderPrompt() {
    def dateStamp = new SimpleDateFormat('yyyy-MM-dd').format(new Date())
    def defaultPath = worldDir ?
        new File(worldDir, "ReadBooks${File.separator}${dateStamp}") :
        new File(System.getProperty('user.dir'), "ReadBooks${File.separator}${dateStamp}")

    outputPathField.promptText = "Optional: Custom output folder (default: ${defaultPath.absolutePath})"
}
```

## CLI Alignment

The GUI is designed to **perfectly mirror CLI behavior**:

| Scenario | GUI | CLI |
|----------|-----|-----|
| No world selected | Uses CWD | `java -jar app.jar` (no `-w`) uses CWD |
| World selected | Passes `-w /path` | `java -jar app.jar -w /path` |
| No output selected | Uses default | `java -jar app.jar` (no `-o`) uses default |
| Output selected | Passes `-o /path` | `java -jar app.jar -o /path` |
| Track failed regions checkbox | Passes `--track-failed-regions` | `java -jar app.jar --track-failed-regions` |
| Custom names | Always passes `--extract-custom-names` | Always extracted (no toggle) |
| Item index checkbox | Passes `--index-items` | `java -jar app.jar --index-items` |
| Skip common items checkbox | Passes `--skip-common-items false` when unchecked | `java -jar app.jar --skip-common-items false` |
| Item limit spinner | Passes `--item-limit N` | `java -jar app.jar --item-limit 500` |
| Search blocks field | Passes `--search-blocks TYPES` | `java -jar app.jar --search-blocks diamond_ore,spawner` |
| Block index limit spinner | Passes `--index-limit N` | `java -jar app.jar --index-limit 10000` |
| Find portals checkbox | Passes `--find-portals` | `java -jar app.jar --find-portals` |
| Block output | All 3 formats (CSV, JSON, TXT) | All 3 formats automatically (no `--block-output-format`) |

**Design Philosophy:** GUI only passes arguments that are explicitly set by user. CLI handles all defaults and validation.

## Build Configuration

### Gradle Dependencies
**Location:** `build.gradle`

```gradle
plugins {
    id 'org.openjfx.javafxplugin' version '0.1.0'
}

javafx {
    version = '21.0.5'
    modules = ['javafx.controls', 'javafx.fxml', 'javafx.graphics', 'javafx.web']
}
```

**Note:** JavaFX `web` module required by underlying JavaFX dependencies.

### JAR Manifest
```gradle
jar {
    manifest {
        attributes('Main-Class': 'Main')  // Smart launcher with GUI detection
    }
}
```

## Testing

### Manual Testing
1. **GUI Launch:** `java -jar ReadSignsAndBooks.jar` or double-click JAR
2. **CLI Test:** `java -jar ReadSignsAndBooks.jar --help` (should show CLI help, not GUI)
3. **Force GUI:** `java -jar ReadSignsAndBooks.jar --gui`

### Integration Tests
Existing Spock tests (`ReadBooksIntegrationSpec.groovy`) still test CLI functionality. GUI does not interfere with tests as they pass arguments (triggering CLI mode).

## Legal & Attribution

### About Dialog
Full attribution and legal disclaimers visible in Help → About dialog:
- Original code: Matt (/u/worldseed) from r/MinecraftDataMining Discord (2020)
- NBT library: Querz NBT Library
- Current implementation: Vibe coded with Claude 4.5
- **Legal Disclaimers:**
  - NOT affiliated with Mojang Studios or Microsoft Corporation
  - Provided "AS IS" without warranty
  - No liability for world corruption or data loss
  - Users advised to backup worlds before processing

### Third-Party Licenses Dialog
**Location:** Help → Third-Party Licenses (bottom of menu)

**Implementation:**
- `showLicensesDialog()` method in `GUI.groovy`
- Opens 800x600 dialog window with scrollable license text
- Displays auto-generated license report from all runtime dependencies
- Styled in italic Courier New font for readability
- License file: `src/main/resources/licenses/THIRD-PARTY-LICENSES.txt`
- Auto-generated during build via `com.github.jk1.dependency-license-report` plugin v3.0.1
- Embedded in JAR resources, excluded from git (auto-generated on each build)

**Fallback Behavior:**
If license file not found, displays manual list of major dependencies with known licenses.

## Future Considerations

### Current Limitations
- Single-threaded extraction (intentional design, matches CLI)
- No progress bar in GUI (shows text progress from CLI logs)
- No cancel/abort button (would require threading refactor)

### Potential Enhancements
- Add cancel button (requires safely accounted-for interruptible CLI execution)
- Integration Tests

### Not Planned
- GroovyFX migration (library is unmaintained, Java 8-only)
- Separate GUI-specific extraction logic (violates DRY, creates maintenance burden)