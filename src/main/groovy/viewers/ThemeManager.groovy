package viewers

import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import com.jthemedetecor.OsThemeDetector
import javafx.application.Application
import javafx.scene.Scene

import java.util.prefs.Preferences

/**
 * Centralized theme management for all JavaFX applications and viewers.
 * Handles dark/light mode switching, system theme detection, and Minecraft-themed styling.
 *
 * Features:
 * - Auto-detect system dark/light mode
 * - Manual toggle between dark/light themes
 * - Persistent theme preference storage
 * - Minecraft color palette overlays
 * - Manages multiple scenes/windows
 */
class ThemeManager {

    /**
     * Theme options.
     */
    enum Theme {
        LIGHT,   // Always light
        DARK,    // Always dark
        SYSTEM   // Follow system preference
    }

    /**
     * Current theme setting.
     */
    private static Theme currentTheme = Theme.SYSTEM

    /**
     * All registered scenes that should receive theme updates.
     */
    private static List<Scene> managedScenes = []

    /**
     * Minecraft color palette for light mode.
     */
    static final Map<String, String> MC_COLORS_LIGHT = [
        'slot-bg': '#8b8b8b',
        'slot-border': '#373737',
        'tooltip-bg': '#100010f0',
        'enchant-purple': '#8040ff',
        'gold-text': '#ffaa00',
        'book-page': '#E8D5B3',
        'sign-wood': '#8B7355',
        'sign-border': '#5D4E37'
    ]

    /**
     * Minecraft color palette for dark mode.
     */
    static final Map<String, String> MC_COLORS_DARK = [
        'slot-bg': '#373737',
        'slot-border': '#1d1d1d',
        'tooltip-bg': '#100010f0',
        'enchant-purple': '#b080ff',
        'gold-text': '#ffcc00',
        'book-page': '#C4B599',
        'sign-wood': '#6B5A47',
        'sign-border': '#4A3D2E'
    ]

    /**
     * Initialize theme manager and load saved preference.
     */
    static void initialize() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ThemeManager)
            String savedTheme = prefs.get('theme', 'SYSTEM')
            currentTheme = Theme.valueOf(savedTheme)
        } catch (Exception e) {
            // If loading fails, default to SYSTEM
            currentTheme = Theme.SYSTEM
        }
    }

    /**
     * Register a scene to receive theme updates.
     * Immediately applies the current theme to the scene.
     *
     * @param scene Scene to manage
     */
    static void registerScene(Scene scene) {
        if (!managedScenes.contains(scene)) {
            managedScenes << scene
        }
        applyTheme(scene)
    }

    /**
     * Unregister a scene (e.g., when window closes).
     *
     * @param scene Scene to unregister
     */
    static void unregisterScene(Scene scene) {
        managedScenes.remove(scene)
    }

    /**
     * Set the theme preference and apply to all registered scenes.
     *
     * @param theme Theme to apply
     */
    static void setTheme(Theme theme) {
        currentTheme = theme

        // Save preference
        try {
            Preferences prefs = Preferences.userNodeForPackage(ThemeManager)
            prefs.put('theme', theme.name())
            prefs.flush()
        } catch (Exception e) {
            // Silently ignore save errors
        }

        // Apply to all registered scenes
        managedScenes.each { scene ->
            applyTheme(scene)
        }
    }

    /**
     * Toggle between dark and light themes.
     * If currently SYSTEM, switches to opposite of current system theme.
     */
    static void toggleTheme() {
        if (currentTheme == Theme.SYSTEM) {
            // If SYSTEM, toggle to opposite of current system preference
            boolean systemIsDark = detectSystemTheme()
            setTheme(systemIsDark ? Theme.LIGHT : Theme.DARK)
        } else if (currentTheme == Theme.DARK) {
            setTheme(Theme.LIGHT)
        } else {
            setTheme(Theme.DARK)
        }
    }

    /**
     * Get current theme setting.
     *
     * @return Current theme
     */
    static Theme getCurrentTheme() {
        return currentTheme
    }

    /**
     * Check if currently using dark theme (after resolving SYSTEM).
     *
     * @return true if dark theme is active
     */
    static boolean isDark() {
        return shouldUseDarkTheme()
    }

    /**
     * Get current Minecraft color palette based on active theme.
     *
     * @return Color map
     */
    static Map<String, String> getColors() {
        return isDark() ? MC_COLORS_DARK : MC_COLORS_LIGHT
    }

    /**
     * Apply theme to a specific scene.
     *
     * @param scene Scene to apply theme to
     */
    private static void applyTheme(Scene scene) {
        boolean isDark = shouldUseDarkTheme()

        // Apply AtlantaFX base theme
        try {
            if (isDark) {
                Application.userAgentStylesheet = new PrimerDark().userAgentStylesheet
            } else {
                Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
            }
        } catch (Exception e) {
            // Fallback to light if theme application fails
            Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
        }

        // Add Minecraft theme CSS if not already present
        try {
            URL mcCssUrl = ThemeManager.class.getResource('/css/minecraft-theme.css')
            if (mcCssUrl) {
                String mcCss = mcCssUrl.toExternalForm()
                if (!scene.stylesheets.contains(mcCss)) {
                    scene.stylesheets.add(mcCss)
                }
            }
        } catch (Exception e) {
            // Silently ignore if CSS not found
        }

        // Apply dynamic CSS variables to root node
        if (scene.root) {
            Map<String, String> colors = isDark ? MC_COLORS_DARK : MC_COLORS_LIGHT
            String cssVars = colors.collect { k, v -> "-mc-$k: $v" }.join('; ')
            scene.root.style = cssVars
        }
    }

    /**
     * Determine if dark theme should be used based on current setting.
     *
     * @return true if dark theme should be used
     */
    private static boolean shouldUseDarkTheme() {
        switch (currentTheme) {
            case Theme.DARK:
                return true
            case Theme.LIGHT:
                return false
            case Theme.SYSTEM:
                return detectSystemTheme()
            default:
                return false
        }
    }

    /**
     * Detect system theme preference.
     *
     * @return true if system is in dark mode
     */
    private static boolean detectSystemTheme() {
        try {
            OsThemeDetector detector = OsThemeDetector.detector
            return detector.dark
        } catch (Exception e) {
            // Default to light if detection fails
            return false
        }
    }

    /**
     * Get human-readable theme name for UI display.
     *
     * @return Theme name (e.g., "Light", "Dark", "System (Dark)")
     */
    static String getThemeDisplayName() {
        switch (currentTheme) {
            case Theme.LIGHT:
                return 'Light'
            case Theme.DARK:
                return 'Dark'
            case Theme.SYSTEM:
                boolean systemIsDark = detectSystemTheme()
                return "System (${systemIsDark ? 'Dark' : 'Light'})"
            default:
                return 'Unknown'
        }
    }
}
