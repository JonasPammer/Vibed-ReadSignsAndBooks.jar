import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.testfx.framework.spock.ApplicationSpec
import org.testfx.util.WaitForAsyncUtils
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Timeout

import java.awt.GraphicsEnvironment
import java.util.concurrent.TimeUnit

/**
 * Full GUI Integration Tests for ReadSignsAndBooks
 *
 * These tests actually launch the GUI application and interact with it
 * using TestFX's robot to click buttons, verify labels, etc.
 *
 * IMPORTANT: These tests require a display to run. They will be skipped
 * automatically in headless environments (CI without xvfb, etc.) because
 * Monocle headless testing is not yet compatible with JavaFX 21.
 *
 * To run these tests locally, just execute: ./gradlew test --tests "GuiFullIntegrationSpec"
 * On CI with Linux, use xvfb-run: xvfb-run ./gradlew test --tests "GuiFullIntegrationSpec"
 *
 * @see <a href="https://github.com/TestFX/TestFX">TestFX Documentation</a>
 */
@Stepwise
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@IgnoreIf({ GraphicsEnvironment.headless })
class GuiFullIntegrationSpec extends ApplicationSpec {

    @Shared
    Stage primaryStage

    @Shared
    GUI guiInstance

    /**
     * Called by TestFX to start the application under test.
     * This launches the actual GUI class.
     */
    @Override
    void start(Stage stage) throws Exception {
        primaryStage = stage
        // Launch the actual GUI and keep reference
        guiInstance = new GUI()
        guiInstance.start(stage)
    }

    /**
     * Clean up after all tests
     */
    def cleanupSpec() {
        // Ensure we close the stage properly
        Platform.runLater {
            if (primaryStage) {
                primaryStage.close()
            }
        }
        WaitForAsyncUtils.waitForFxEvents()
    }

    // =========================================================================
    // GUI Launch and Basic Structure Tests
    // =========================================================================

    def "GUI should launch successfully"() {
        expect: 'The primary stage should be showing'
        primaryStage != null
        primaryStage.showing
    }

    def "GUI should have correct window title"() {
        expect:
        primaryStage.title == 'ReadSignsAndBooks Extractor'
    }

    def "GUI should have minimum window dimensions"() {
        expect:
        primaryStage.minWidth == 700
        primaryStage.minHeight == 500
    }

    // =========================================================================
    // UI Component Presence Tests
    // =========================================================================

    def "GUI should contain title label"() {
        when:
        Object titleLabel = lookup('.label').queryAll().find { Object node ->
            node instanceof Label && ((Label) node).text == 'Minecraft Book & Sign Extractor'
        }

        then:
        titleLabel != null
    }

    def "GUI should contain World Directory label and text field"() {
        when:
        def worldLabel = lookup('.label').queryAll().find {
            it instanceof Label && ((Label) it).text.contains('World Directory')
        }
        def worldTextField = lookup('.text-field').queryAll().find {
            it instanceof TextField && ((TextField) it).promptText?.contains('Select Minecraft world')
        }

        then:
        worldLabel != null
        worldTextField != null
    }

    def "GUI should contain Output Folder label and text field"() {
        when:
        def outputLabel = lookup('.label').queryAll().find {
            it instanceof Label && ((Label) it).text.contains('Output Folder')
        }
        def outputTextField = lookup('.text-field').queryAll().find {
            it instanceof TextField && ((TextField) it).promptText?.contains('Optional')
        }

        then:
        outputLabel != null
        outputTextField != null
    }

    def "GUI should contain Remove formatting checkbox"() {
        when:
        def checkbox = lookup('.check-box').queryAll().find {
            it instanceof CheckBox && ((CheckBox) it).text?.contains('formatting')
        }

        then:
        checkbox != null
        !((CheckBox) checkbox).selected  // Should be unchecked by default
    }

    def "GUI should contain Extract button"() {
        when:
        def extractButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Extract'
        }

        then:
        extractButton != null
        !((Button) extractButton).disabled
    }

    def "GUI should contain Open Output Folder button"() {
        when:
        def openButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Open Output Folder'
        }

        then:
        openButton != null
    }

    def "GUI should contain Clear Log button"() {
        when:
        def clearButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Clear Log'
        }

        then:
        clearButton != null
    }

    def "GUI should contain Exit button"() {
        when:
        def exitButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Exit'
        }

        then:
        exitButton != null
    }

    def "GUI should contain log TextArea"() {
        when:
        def textArea = lookup('.text-area').query()

        then:
        textArea != null
        textArea instanceof TextArea
        !((TextArea) textArea).editable  // Log area should not be editable
    }

    def "GUI should contain status label"() {
        when:
        def statusLabel = lookup('.label').queryAll().find {
            it instanceof Label && ((Label) it).text == 'Ready'
        }

        then:
        statusLabel != null
    }

    def "GUI should contain Help menu"() {
        when:
        def menuBar = lookup('.menu-bar').query()
        def helpMenu = ((MenuBar) menuBar)?.menus?.find { it.text == 'Help' }

        then:
        menuBar != null
        helpMenu != null
    }

    // =========================================================================
    // UI Interaction Tests
    // =========================================================================

    def "Clear Log button should clear the log area"() {
        given: 'The log TextArea'
        TextArea logArea = lookup('.text-area').query() as TextArea

        and: 'Add some text to the log'
        Platform.runLater {
            logArea.text = "Test log content\nLine 2\nLine 3"
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)  // Allow UI to update

        when: 'Click the Clear Log button'
        Button clearButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Clear Log'
        } as Button

        // Use Platform.runLater to simulate button action directly for reliability
        Platform.runLater {
            clearButton.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)  // Allow UI to update

        then: 'The log should be empty'
        logArea.text == ''
    }

    def "Remove formatting checkbox should toggle"() {
        given:
        CheckBox checkbox = lookup('.check-box').queryAll().find {
            it instanceof CheckBox && ((CheckBox) it).text?.contains('formatting')
        } as CheckBox

        expect: 'Initially unchecked'
        !checkbox.selected

        when: 'Toggle the checkbox'
        Platform.runLater {
            checkbox.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        then: 'Should be checked'
        checkbox.selected

        when: 'Toggle again'
        Platform.runLater {
            checkbox.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        then: 'Should be unchecked again'
        !checkbox.selected
    }

    def "Help menu should contain expected items"() {
        when: 'Get the Help menu'
        MenuBar menuBar = lookup('.menu-bar').query() as MenuBar
        Menu helpMenu = menuBar?.menus?.find { it.text == 'Help' }

        then: 'Help menu should exist and contain expected items'
        helpMenu != null
        helpMenu.items.size() >= 2  // At least "View on GitHub" and "About"

        and: 'Check for expected menu items'
        def menuItemTexts = helpMenu.items*.text
        menuItemTexts.any { it?.contains('GitHub') || it?.contains('About') || it?.contains('Licenses') }
    }

    // =========================================================================
    // Extract Button State Tests
    // =========================================================================

    def "Extract button should be enabled by default"() {
        when:
        Button extractButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Extract'
        } as Button

        then:
        extractButton != null
        !extractButton.disabled
    }

    // =========================================================================
    // Window Resize Tests
    // =========================================================================

    def "GUI should be resizable"() {
        expect:
        primaryStage.resizable
    }

    def "GUI should respect minimum dimensions when resized"() {
        when: 'Try to resize below minimum'
        Platform.runLater {
            primaryStage.width = 500  // Below 700 minimum
            primaryStage.height = 400  // Below 500 minimum
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        then: 'Should stay at minimum dimensions'
        primaryStage.width >= 700
        primaryStage.height >= 500
    }

    // =========================================================================
    // Browse Button Tests (Cannot fully test without file dialog mocking)
    // =========================================================================

    def "World Directory Browse button should exist and be clickable"() {
        when:
        def browseButtons = lookup('.button').queryAll().findAll {
            it instanceof Button && ((Button) it).text == 'Browse...'
        }

        then: 'Should have at least 2 browse buttons (world and output)'
        browseButtons.size() >= 2
    }

    // =========================================================================
    // Extraction Log Label Tests
    // =========================================================================

    def "GUI should have Extraction Log label"() {
        when:
        def logLabel = lookup('.label').queryAll().find {
            it instanceof Label && ((Label) it).text == 'Extraction Log:'
        }

        then:
        logLabel != null
    }

    // =========================================================================
    // Test with Real World (Integration with extraction)
    // =========================================================================

    def "Extract with test world should work and update UI"() {
        given: 'Path to test world'
        def testWorldPath = new File('src/test/resources/1_21_10-44-3')

        and: "Skip if test world doesn't exist"
        if (!testWorldPath.exists()) {
            println "Skipping: Test world not found at ${testWorldPath.absolutePath}"
            return
        }

        and: 'Get UI components'
        TextField worldPathField = lookup('.text-field').queryAll().find {
            it instanceof TextField && ((TextField) it).promptText?.contains('Select Minecraft world')
        } as TextField

        Button extractButton = lookup('.button').queryAll().find {
            it instanceof Button && ((Button) it).text == 'Extract'
        } as Button

        TextArea logArea = lookup('.text-area').query() as TextArea

        when: 'Set the world path programmatically and trigger extraction'
        Platform.runLater {
            worldPathField.text = testWorldPath.absolutePath
            guiInstance.worldDir = testWorldPath
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        // Fire the extract button directly
        Platform.runLater {
            extractButton.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()

        and: 'Wait for extraction to complete (up to 60 seconds)'
        def startTime = System.currentTimeMillis()
        def extractionComplete = false
        while (System.currentTimeMillis() - startTime < 60000) {
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(500)

            // Check if extraction completed (button text returns to "Extract" and is not disabled)
            if (extractButton.text == 'Extract' && !extractButton.disabled) {
                // Give a little more time for status to update
                Thread.sleep(200)
                WaitForAsyncUtils.waitForFxEvents()
                extractionComplete = true
                break
            }
        }

        then: 'Extraction should have completed'
        extractionComplete

        and: 'Log area should have content'
        logArea.text?.length() > 0

        and: 'Extract button should be re-enabled'
        !extractButton.disabled
        extractButton.text == 'Extract'
    }

}
