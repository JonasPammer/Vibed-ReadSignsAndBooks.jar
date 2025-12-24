# ThemeManager - Centralized Theme Management

**Location:** `src/main/groovy/viewers/ThemeManager.groovy`
**Created:** 2025-12-23
**Purpose:** Centralized dark/light mode management for all JavaFX windows

## Overview

ThemeManager provides centralized theme management for all JavaFX applications and viewers in the project. It handles:

- **Auto-detection** of system dark/light mode preferences
- **Manual toggle** between dark and light themes
- **Persistent storage** of user theme preference
- **Minecraft color palette** overlays on top of AtlantaFX base themes
- **Multi-scene management** - updates all registered windows simultaneously

## Architecture

### Theme Modes

```groovy
enum Theme {
    LIGHT,   // Always light mode
    DARK,    // Always dark mode
    SYSTEM   // Follow system preference (default)
}
```

### Color Palettes

ThemeManager provides two Minecraft-themed color palettes:

**Light Mode Colors:**
```groovy
MC_COLORS_LIGHT = [
    'slot-bg': '#8b8b8b',
    'slot-border': '#373737',
    'tooltip-bg': '#100010f0',
    'enchant-purple': '#8040ff',
    'gold-text': '#ffaa00',
    'book-page': '#E8D5B3',
    'sign-wood': '#8B7355',
    'sign-border': '#5D4E37'
]
```

**Dark Mode Colors:**
```groovy
MC_COLORS_DARK = [
    'slot-bg': '#373737',
    'slot-border': '#1d1d1d',
    'tooltip-bg': '#100010f0',
    'enchant-purple': '#b080ff',
    'gold-text': '#ffcc00',
    'book-page': '#C4B599',
    'sign-wood': '#6B5A47',
    'sign-border': '#4A3D2E'
]
```

These colors are applied as CSS variables to the scene root: `-mc-slot-bg`, `-mc-enchant-purple`, etc.

## Usage

### 1. Initialize (Once per Application)

```groovy
class MyApplication extends Application {
    void start(Stage stage) {
        // Initialize ThemeManager (loads saved preference)
        ThemeManager.initialize()

        // ... build UI ...

        Scene scene = new Scene(root, 800, 600)

        // Register scene for theme updates
        ThemeManager.registerScene(scene)

        // Cleanup on close
        stage.onCloseRequest = { event ->
            ThemeManager.unregisterScene(scene)
        }

        stage.show()
    }
}
```

### 2. Add Theme Toggle Button

```groovy
Button themeToggleButton = new Button()
updateThemeButtonText(themeToggleButton)
themeToggleButton.onAction = { event ->
    ThemeManager.toggleTheme()
    updateThemeButtonText(themeToggleButton)
}

void updateThemeButtonText(Button button) {
    String icon = ThemeManager.isDark() ? '☀' : '☾'
    button.text = "${icon} ${ThemeManager.getThemeDisplayName()}"
}
```

### 3. Use Minecraft Theme Colors in CSS

**In CSS file:**
```css
.my-element {
    -fx-background-color: var(-mc-slot-bg, #373737);
    -fx-text-fill: var(-mc-enchant-purple, #8040ff);
}
```

**In inline styles:**
```groovy
label.style = '-fx-text-fill: -mc-gold-text; -fx-font-weight: bold;'
```

### 4. Programmatic Theme Control

```groovy
// Set theme explicitly
ThemeManager.setTheme(ThemeManager.Theme.DARK)
ThemeManager.setTheme(ThemeManager.Theme.LIGHT)
ThemeManager.setTheme(ThemeManager.Theme.SYSTEM)

// Toggle between themes
ThemeManager.toggleTheme()

// Query current state
boolean isDark = ThemeManager.isDark()
Theme currentTheme = ThemeManager.getCurrentTheme()
String displayName = ThemeManager.getThemeDisplayName()

// Get current color palette
Map<String, String> colors = ThemeManager.getColors()
String slotBg = colors['slot-bg']
```

## Integration with Existing Code

### OutputViewer Integration

**Before:**
```groovy
class OutputViewer extends Stage {
    OutputViewer() {
        // ... setup ...
        applyTheme()  // Old method using OsThemeDetector directly

        Scene scene = new Scene(root, 1200, 800)
        this.scene = scene
    }

    private void applyTheme() {
        OsThemeDetector detector = OsThemeDetector.detector
        if (detector.dark) {
            Application.userAgentStylesheet = new PrimerDark().userAgentStylesheet
        } else {
            Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
        }
    }
}
```

**After:**
```groovy
import viewers.ThemeManager

class OutputViewer extends Stage {
    OutputViewer() {
        // ... setup ...
        ThemeManager.initialize()  // Load saved preference

        Scene scene = new Scene(root, 1200, 800)
        this.scene = scene

        ThemeManager.registerScene(scene)  // Auto-apply theme

        onCloseRequest = { event ->
            ThemeManager.unregisterScene(scene)
            model?.close()
        }
    }
}
```

### Adding Theme Toggle to Toolbar

```groovy
private HBox createToolbar() {
    // ... existing buttons ...

    Button themeToggleButton = new Button()
    updateThemeButtonText(themeToggleButton)
    themeToggleButton.tooltip = new Tooltip('Toggle dark/light theme')
    themeToggleButton.onAction = { event ->
        ThemeManager.toggleTheme()
        updateThemeButtonText(themeToggleButton)
    }

    toolbar.children.addAll(
        label, folderField, browseButton, loadButton, refreshButton,
        new Separator(),
        themeToggleButton
    )
}

private void updateThemeButtonText(Button button) {
    String icon = ThemeManager.isDark() ? '☀' : '☾'
    button.text = "${icon} ${ThemeManager.getThemeDisplayName()}"
}
```

## Preference Persistence

ThemeManager automatically saves and restores the user's theme preference using Java Preferences API:

**Storage location:**
- Windows: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\viewers\ThemeManager`
- macOS: `~/Library/Preferences/com.apple.java.util.prefs.plist`
- Linux: `~/.java/.userPrefs/viewers/ThemeManager/prefs.xml`

**Preference key:** `theme` (values: "LIGHT", "DARK", "SYSTEM")

## CSS Variable System

ThemeManager applies CSS variables as inline styles on the scene root node:

```groovy
scene.root.style = "-mc-slot-bg: #373737; -mc-enchant-purple: #b080ff; ..."
```

This allows CSS files to reference these variables:

```css
.item-slot {
    -fx-background-color: var(-mc-slot-bg, #373737);  /* Fallback value */
}
```

**Available CSS Variables:**
- `-mc-slot-bg` - Item slot background
- `-mc-slot-border` - Item slot border
- `-mc-tooltip-bg` - Tooltip background
- `-mc-enchant-purple` - Enchantment purple color
- `-mc-gold-text` - Golden text color
- `-mc-book-page` - Book page background
- `-mc-sign-wood` - Sign wood color
- `-mc-sign-border` - Sign border color

## Demo Application

**Location:** `src/main/groovy/viewers/ThemeManagerDemo.groovy`

Run with:
```bash
java -cp ReadSignsAndBooks.jar viewers.ThemeManagerDemo
```

Features:
- Toggle button with icon (☀/☾)
- Force Dark/Light buttons
- Follow System button
- Live Minecraft-themed element previews

## Testing

**Location:** `src/test/groovy/ThemeManagerSpec.groovy`

Run tests:
```bash
./gradlew test --tests ThemeManagerSpec
```

Tests verify:
- Initialization with default theme
- Theme toggling
- Explicit theme setting
- Color palette switching
- Display name generation

## Design Decisions

### Why Centralized?

**Before ThemeManager**, each application/viewer had duplicate theme code:
- OutputViewer.applyTheme()
- GUI.applyTheme()
- SignViewer.applyTheme()
- CustomNamesViewerApp.applyBasicStyling()

**Problem:** Code duplication, inconsistent behavior, no theme persistence.

**After ThemeManager:** Single source of truth for theme management.

### Why CSS Variables?

JavaFX CSS supports `var()` syntax (similar to web CSS). This allows:
- Dynamic color changes without reloading stylesheets
- Single CSS file works for both dark/light modes
- Fallback values for graceful degradation

### Why Three Theme Modes?

1. **SYSTEM**: Respects user's OS preference (default)
2. **LIGHT**: User prefers light mode regardless of OS
3. **DARK**: User prefers dark mode regardless of OS

This matches modern application behavior (Discord, Slack, VS Code).

## Future Enhancements

### Planned
- Custom theme colors (user-configurable palette)
- Accent color selection (purple, blue, green variants)
- High contrast mode support
- Theme preview before applying

### Not Planned
- Multiple color scheme presets (vanilla, modded, custom)
  - *Reason: Adds complexity without clear user benefit*
- Per-window theme override
  - *Reason: Breaks consistency across application*

## Migration Guide

To migrate existing applications to ThemeManager:

1. **Add import:**
   ```groovy
   import viewers.ThemeManager
   ```

2. **Replace applyTheme() call:**
   ```groovy
   // OLD
   applyTheme()

   // NEW
   ThemeManager.initialize()
   ```

3. **Register scene:**
   ```groovy
   Scene scene = new Scene(root, width, height)
   ThemeManager.registerScene(scene)
   ```

4. **Add cleanup:**
   ```groovy
   onCloseRequest = { event ->
       ThemeManager.unregisterScene(scene)
       // ... existing cleanup ...
   }
   ```

5. **Remove old applyTheme() method** (no longer needed)

6. **Optional: Add theme toggle button** (see examples above)

## Troubleshooting

### Theme doesn't change when toggled

**Cause:** Scene not registered with ThemeManager.

**Fix:** Call `ThemeManager.registerScene(scene)` after creating scene.

### CSS variables not working

**Cause:** CSS file loaded before scene registered.

**Fix:** ThemeManager automatically adds minecraft-theme.css when registering scene. Remove manual stylesheet addition.

### Theme preference not persisted

**Cause:** Java Preferences API permissions issue.

**Fix:** Check file permissions on preference storage location (see Preference Persistence section).

### "System" theme doesn't match OS

**Cause:** OsThemeDetector library doesn't support your OS/version.

**Fix:** Use explicit DARK or LIGHT theme instead of SYSTEM.

## Dependencies

- **AtlantaFX**: Base theme library (PrimerDark, PrimerLight)
- **jSystemThemeDetector**: OS theme detection (OsThemeDetector)
- **JavaFX**: Scene, Application, CSS support
- **Java Preferences API**: Theme preference persistence

## References

- AtlantaFX: https://github.com/mkpaz/atlantafx
- jSystemThemeDetector: https://github.com/Dansoftowner/jSystemThemeDetector
- JavaFX CSS: https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html
