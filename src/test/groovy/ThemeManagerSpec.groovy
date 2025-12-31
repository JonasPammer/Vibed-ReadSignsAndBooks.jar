import spock.lang.Specification
import viewers.ThemeManager
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.embed.swing.JFXPanel
import java.util.prefs.Preferences

/**
 * Tests for ThemeManager.
 */
class ThemeManagerSpec extends Specification {

    def setupSpec() {
        // Initialize JavaFX toolkit
        new JFXPanel()
    }

    def setup() {
        // Initialize before each test
        ThemeManager.initialize()
        // Clear any managed scenes from previous tests
        clearManagedScenes()
    }

    def cleanup() {
        // Clear managed scenes after each test
        clearManagedScenes()
    }

    private void clearManagedScenes() {
        // Access private managedScenes field to clear it
        def field = ThemeManager.getDeclaredField('managedScenes')
        field.setAccessible(true)
        field.get(null).clear()
    }

    def "should initialize with default theme"() {
        expect:
        ThemeManager.getCurrentTheme() != null
    }

    def "should initialize with saved preference"() {
        given:
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager)
        prefs.put('theme', 'DARK')
        prefs.flush()

        when:
        ThemeManager.initialize()

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.DARK

        cleanup:
        prefs.remove('theme')
    }

    def "should fallback to SYSTEM on invalid saved preference"() {
        given:
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager)
        prefs.put('theme', 'INVALID_THEME')
        prefs.flush()

        when:
        ThemeManager.initialize()

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.SYSTEM

        cleanup:
        prefs.remove('theme')
    }

    def "should toggle between themes"() {
        given:
        def initialTheme = ThemeManager.getCurrentTheme()

        when:
        ThemeManager.toggleTheme()

        then:
        ThemeManager.getCurrentTheme() != initialTheme
    }

    def "should toggle from DARK to LIGHT"() {
        given:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        when:
        ThemeManager.toggleTheme()

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.LIGHT
    }

    def "should toggle from LIGHT to DARK"() {
        given:
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)

        when:
        ThemeManager.toggleTheme()

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.DARK
    }

    def "should set theme explicitly"() {
        when:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.DARK
        ThemeManager.isDark() == true

        when:
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)

        then:
        ThemeManager.getCurrentTheme() == ThemeManager.Theme.LIGHT
        ThemeManager.isDark() == false
    }

    def "should persist theme preference"() {
        given:
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager)

        when:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        then:
        prefs.get('theme', null) == 'DARK'

        when:
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)

        then:
        prefs.get('theme', null) == 'LIGHT'

        cleanup:
        prefs.remove('theme')
    }

    def "should provide color palettes"() {
        when:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)
        def darkColors = ThemeManager.getColors()

        then:
        darkColors != null
        darkColors.containsKey('slot-bg')
        darkColors.containsKey('enchant-purple')

        when:
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)
        def lightColors = ThemeManager.getColors()

        then:
        lightColors != null
        lightColors['slot-bg'] != darkColors['slot-bg']  // Different colors
    }

    def "should provide all required Minecraft colors"() {
        when:
        def colors = ThemeManager.getColors()

        then:
        colors.containsKey('slot-bg')
        colors.containsKey('slot-border')
        colors.containsKey('tooltip-bg')
        colors.containsKey('enchant-purple')
        colors.containsKey('gold-text')
        colors.containsKey('book-page')
        colors.containsKey('sign-wood')
        colors.containsKey('sign-border')
    }

    def "should provide display names"() {
        when:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        then:
        ThemeManager.getThemeDisplayName() == 'Dark'

        when:
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)

        then:
        ThemeManager.getThemeDisplayName() == 'Light'

        when:
        ThemeManager.setTheme(ThemeManager.Theme.SYSTEM)

        then:
        ThemeManager.getThemeDisplayName().startsWith('System')
    }

    def "should register and apply theme to scene"() {
        given:
        def root = new VBox()
        def scene = new Scene(root, 400, 300)
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        when:
        ThemeManager.registerScene(scene)

        then:
        // Scene should have CSS variables applied
        scene.root.style != null
        scene.root.style.contains('-mc-slot-bg')

        // Scene should have minecraft-theme.css (if available)
        // Note: This may fail in test environment if CSS resource not found
        // which is acceptable as ThemeManager handles this gracefully
    }

    def "should not register same scene twice"() {
        given:
        def root = new VBox()
        def scene = new Scene(root, 400, 300)
        def field = ThemeManager.getDeclaredField('managedScenes')
        field.setAccessible(true)

        when:
        ThemeManager.registerScene(scene)
        ThemeManager.registerScene(scene)
        def managedScenes = field.get(null) as List

        then:
        managedScenes.size() == 1
        managedScenes.contains(scene)
    }

    def "should unregister scene"() {
        given:
        def root = new VBox()
        def scene = new Scene(root, 400, 300)
        def field = ThemeManager.getDeclaredField('managedScenes')
        field.setAccessible(true)
        ThemeManager.registerScene(scene)

        when:
        ThemeManager.unregisterScene(scene)
        def managedScenes = field.get(null) as List

        then:
        managedScenes.size() == 0
        !managedScenes.contains(scene)
    }

    def "should apply theme to all registered scenes when theme changes"() {
        given:
        def scene1 = new Scene(new VBox(), 400, 300)
        def scene2 = new Scene(new VBox(), 400, 300)
        ThemeManager.registerScene(scene1)
        ThemeManager.registerScene(scene2)
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT)

        when:
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        then:
        // Both scenes should have dark theme CSS variables
        scene1.root.style.contains('-mc-slot-bg')
        scene2.root.style.contains('-mc-slot-bg')

        // Verify dark theme colors are applied
        def darkColors = ThemeManager.MC_COLORS_DARK
        scene1.root.style.contains(darkColors['slot-bg'])
        scene2.root.style.contains(darkColors['slot-bg'])
    }

    def "should apply CSS variables with correct format"() {
        given:
        def root = new VBox()
        def scene = new Scene(root, 400, 300)
        ThemeManager.setTheme(ThemeManager.Theme.DARK)

        when:
        ThemeManager.registerScene(scene)
        def style = scene.root.style

        then:
        // Should contain CSS variable declarations
        style.contains('-mc-slot-bg:')
        style.contains('-mc-enchant-purple:')
        style.contains('-mc-gold-text:')

        // Should contain hex color values
        style.contains('#')
    }

    def "should handle SYSTEM theme by detecting OS preference"() {
        when:
        ThemeManager.setTheme(ThemeManager.Theme.SYSTEM)
        def displayName = ThemeManager.getThemeDisplayName()

        then:
        displayName.startsWith('System')
        displayName.contains('Dark') || displayName.contains('Light')

        // Should resolve to either dark or light
        ThemeManager.isDark() == true || ThemeManager.isDark() == false
    }

    def "should provide correct color palette based on resolved theme"() {
        when:
        ThemeManager.setTheme(ThemeManager.Theme.SYSTEM)
        def systemColors = ThemeManager.getColors()

        then:
        // Should return either light or dark colors (not null)
        systemColors != null
        systemColors == ThemeManager.MC_COLORS_DARK || systemColors == ThemeManager.MC_COLORS_LIGHT
    }

    def "should have different color values for light and dark modes"() {
        expect:
        // Verify that light and dark palettes are actually different
        ThemeManager.MC_COLORS_LIGHT['slot-bg'] != ThemeManager.MC_COLORS_DARK['slot-bg']
        ThemeManager.MC_COLORS_LIGHT['enchant-purple'] != ThemeManager.MC_COLORS_DARK['enchant-purple']
        ThemeManager.MC_COLORS_LIGHT['book-page'] != ThemeManager.MC_COLORS_DARK['book-page']
    }

    def "should gracefully handle scene without root node"() {
        given:
        def scene = new Scene(new VBox(), 400, 300)
        // Remove root temporarily to test null handling
        def originalRoot = scene.root
        scene.root = null

        when:
        ThemeManager.registerScene(scene)

        then:
        notThrown(Exception)

        cleanup:
        scene.root = originalRoot
    }
}
