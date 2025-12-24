package viewers

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage

/**
 * Demo application to showcase ThemeManager functionality.
 *
 * Usage: java -cp ReadSignsAndBooks.jar viewers.ThemeManagerDemo
 */
class ThemeManagerDemo extends Application {

    @Override
    void start(Stage primaryStage) {
        primaryStage.title = 'ThemeManager Demo'

        // Initialize ThemeManager
        ThemeManager.initialize()

        // Create UI
        VBox root = new VBox(20)
        root.padding = new Insets(40)
        root.alignment = Pos.CENTER

        Label titleLabel = new Label('ThemeManager Demo')
        titleLabel.style = '-fx-font-size: 24px; -fx-font-weight: bold;'

        Label infoLabel = new Label('Current theme: ')
        Label themeLabel = new Label(ThemeManager.getThemeDisplayName())
        themeLabel.style = '-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -mc-enchant-purple;'

        Button toggleButton = new Button()
        updateButtonText(toggleButton)
        toggleButton.onAction = { event ->
            ThemeManager.toggleTheme()
            updateButtonText(toggleButton)
            themeLabel.text = ThemeManager.getThemeDisplayName()
        }

        Button darkButton = new Button('Force Dark')
        darkButton.onAction = { event ->
            ThemeManager.setTheme(ThemeManager.Theme.DARK)
            updateButtonText(toggleButton)
            themeLabel.text = ThemeManager.getThemeDisplayName()
        }

        Button lightButton = new Button('Force Light')
        lightButton.onAction = { event ->
            ThemeManager.setTheme(ThemeManager.Theme.LIGHT)
            updateButtonText(toggleButton)
            themeLabel.text = ThemeManager.getThemeDisplayName()
        }

        Button systemButton = new Button('Follow System')
        systemButton.onAction = { event ->
            ThemeManager.setTheme(ThemeManager.Theme.SYSTEM)
            updateButtonText(toggleButton)
            themeLabel.text = ThemeManager.getThemeDisplayName()
        }

        // Add Minecraft-styled elements to showcase theme colors
        Label minecraftLabel = new Label('Minecraft-themed elements:')
        minecraftLabel.style = '-fx-font-size: 14px; -fx-font-weight: bold;'

        Label slotDemo = new Label('Item Slot')
        slotDemo.styleClass.add('item-slot')

        Label enchantDemo = new Label('Enchanted Item')
        enchantDemo.style = '-fx-text-fill: -mc-enchant-purple; -fx-font-weight: bold;'

        Label goldDemo = new Label('Golden Text')
        goldDemo.style = '-fx-text-fill: -mc-gold-text; -fx-font-weight: bold;'

        root.children.addAll(
            titleLabel,
            infoLabel,
            themeLabel,
            toggleButton,
            darkButton,
            lightButton,
            systemButton,
            minecraftLabel,
            slotDemo,
            enchantDemo,
            goldDemo
        )

        // Create and register scene
        Scene scene = new Scene(root, 500, 600)
        primaryStage.scene = scene

        ThemeManager.registerScene(scene)

        // Unregister on close
        primaryStage.onCloseRequest = { event ->
            ThemeManager.unregisterScene(scene)
        }

        primaryStage.show()
    }

    private void updateButtonText(Button button) {
        String icon = ThemeManager.isDark() ? '☀' : '☾'
        button.text = "${icon} Toggle Theme"
    }

    static void main(String[] args) {
        launch(args)
    }
}
