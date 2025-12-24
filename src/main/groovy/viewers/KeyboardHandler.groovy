package viewers

import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.control.Alert
import javafx.scene.control.TextArea
import javafx.scene.text.Font

/**
 * Keyboard shortcut handler for the Output Viewer.
 * Manages global shortcuts and viewer-specific shortcuts.
 */
class KeyboardHandler {

    OutputViewer viewer
    Map<KeyCodeCombination, Runnable> globalShortcuts = [:]
    Map<String, Map<KeyCodeCombination, Runnable>> viewerShortcuts = [:]

    /**
     * Initialize keyboard shortcuts and attach to scene.
     */
    void initialize(OutputViewer viewer) {
        this.viewer = viewer
        setupGlobalShortcuts()
        setupViewerShortcuts()
        attachToScene(viewer.stage.scene)
    }

    /**
     * Set up global shortcuts that work across all tabs.
     */
    private void setupGlobalShortcuts() {
        // Focus search
        globalShortcuts[new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)] = {
            viewer.globalSearch.searchField.requestFocus()
        }

        // Tab switching (Ctrl+1 through Ctrl+7)
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(0) // Books
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(1) // Signs
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(2) // Items
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(3) // Blocks
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(4) // Portals
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT6, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(5) // Map
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.CONTROL_DOWN)] = {
            selectTabByIndex(6) // Statistics
        }

        // Export current view
        globalShortcuts[new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN)] = {
            viewer.exportCurrentView()
        }

        // Add bookmark (if location selected)
        globalShortcuts[new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN)] = {
            viewer.addBookmarkFromSelection()
        }

        // Clear search / close popups
        globalShortcuts[new KeyCodeCombination(KeyCode.ESCAPE)] = {
            viewer.clearSearchAndPopups()
        }

        // Show help (F1 and ?)
        globalShortcuts[new KeyCodeCombination(KeyCode.F1)] = {
            showShortcutHelp()
        }
        globalShortcuts[new KeyCodeCombination(KeyCode.SLASH, KeyCombination.SHIFT_DOWN)] = {
            // Shift+/ produces '?' on US keyboards
            showShortcutHelp()
        }
    }

    /**
     * Set up viewer-specific shortcuts for each tab.
     */
    private void setupViewerShortcuts() {
        // Book viewer shortcuts
        viewerShortcuts['Books'] = [
            (new KeyCodeCombination(KeyCode.LEFT)): {
                viewer.bookViewer?.previousPage()
            },
            (new KeyCodeCombination(KeyCode.RIGHT)): {
                viewer.bookViewer?.nextPage()
            },
            (new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN)): {
                viewer.bookViewer?.showGoToPageDialog()
            },
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.bookViewer?.copyTeleportCommand()
            },
            (new KeyCodeCombination(KeyCode.M)): {
                viewer.bookViewer?.showOnMap()
            }
        ]

        // Sign viewer shortcuts
        viewerShortcuts['Signs'] = [
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.signViewer?.copySelectedTeleport()
            },
            (new KeyCodeCombination(KeyCode.M)): {
                viewer.signViewer?.showSelectedOnMap()
            },
            (new KeyCodeCombination(KeyCode.R)): {
                viewer.signViewer?.showSelectedDetails()
            }
        ]

        // Item grid shortcuts
        viewerShortcuts['Items'] = [
            (new KeyCodeCombination(KeyCode.UP)): {
                viewer.itemGrid?.navigateUp()
            },
            (new KeyCodeCombination(KeyCode.DOWN)): {
                viewer.itemGrid?.navigateDown()
            },
            (new KeyCodeCombination(KeyCode.LEFT)): {
                viewer.itemGrid?.navigateLeft()
            },
            (new KeyCodeCombination(KeyCode.RIGHT)): {
                viewer.itemGrid?.navigateRight()
            },
            (new KeyCodeCombination(KeyCode.ENTER)): {
                viewer.itemGrid?.selectCurrent()
            },
            (new KeyCodeCombination(KeyCode.R)): {
                viewer.itemGrid?.showSelectedDetails()
            },
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.itemGrid?.copySelectedTeleport()
            },
            (new KeyCodeCombination(KeyCode.M)): {
                viewer.itemGrid?.showSelectedOnMap()
            }
        ]

        // Block grid shortcuts (same as item grid)
        viewerShortcuts['Blocks'] = [
            (new KeyCodeCombination(KeyCode.UP)): {
                viewer.blockGrid?.navigateUp()
            },
            (new KeyCodeCombination(KeyCode.DOWN)): {
                viewer.blockGrid?.navigateDown()
            },
            (new KeyCodeCombination(KeyCode.LEFT)): {
                viewer.blockGrid?.navigateLeft()
            },
            (new KeyCodeCombination(KeyCode.RIGHT)): {
                viewer.blockGrid?.navigateRight()
            },
            (new KeyCodeCombination(KeyCode.ENTER)): {
                viewer.blockGrid?.selectCurrent()
            },
            (new KeyCodeCombination(KeyCode.R)): {
                viewer.blockGrid?.showSelectedDetails()
            },
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.blockGrid?.copySelectedTeleport()
            },
            (new KeyCodeCombination(KeyCode.M)): {
                viewer.blockGrid?.showSelectedOnMap()
            }
        ]

        // Portal viewer shortcuts
        viewerShortcuts['Portals'] = [
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.portalViewer?.copySelectedTeleport()
            },
            (new KeyCodeCombination(KeyCode.M)): {
                viewer.portalViewer?.showSelectedOnMap()
            },
            (new KeyCodeCombination(KeyCode.R)): {
                viewer.portalViewer?.showSelectedDetails()
            }
        ]

        // Map viewer shortcuts
        viewerShortcuts['Map'] = [
            (new KeyCodeCombination(KeyCode.PLUS)): {
                viewer.mapViewer?.zoomIn()
            },
            (new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHIFT_DOWN)): {
                // Shift+= produces '+' on US keyboards
                viewer.mapViewer?.zoomIn()
            },
            (new KeyCodeCombination(KeyCode.MINUS)): {
                viewer.mapViewer?.zoomOut()
            },
            (new KeyCodeCombination(KeyCode.UP)): {
                viewer.mapViewer?.panUp()
            },
            (new KeyCodeCombination(KeyCode.DOWN)): {
                viewer.mapViewer?.panDown()
            },
            (new KeyCodeCombination(KeyCode.LEFT)): {
                viewer.mapViewer?.panLeft()
            },
            (new KeyCodeCombination(KeyCode.RIGHT)): {
                viewer.mapViewer?.panRight()
            },
            (new KeyCodeCombination(KeyCode.SPACE)): {
                viewer.mapViewer?.resetView()
            },
            (new KeyCodeCombination(KeyCode.F)): {
                viewer.mapViewer?.toggleFullscreen()
            }
        ]

        // Statistics viewer shortcuts
        viewerShortcuts['Statistics'] = [
            (new KeyCodeCombination(KeyCode.C)): {
                viewer.statsViewer?.copyCurrentStats()
            }
        ]
    }

    /**
     * Attach keyboard event handlers to the scene.
     */
    private void attachToScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, { event ->
            // Check if we're in a text input field (don't intercept typing)
            if (event.target instanceof javafx.scene.control.TextInputControl) {
                // Allow Escape and F1 even in text fields
                if (event.code == KeyCode.ESCAPE || event.code == KeyCode.F1) {
                    // Continue to global shortcut handling
                } else {
                    return // Let text field handle the key
                }
            }

            // Check global shortcuts first
            def matchedGlobal = globalShortcuts.find { combo, action ->
                combo.match(event)
            }

            if (matchedGlobal) {
                matchedGlobal.value.run()
                event.consume()
                return
            }

            // Check viewer-specific shortcuts
            String currentTab = viewer.contentTabs.selectionModel.selectedItem?.text
            if (currentTab && viewerShortcuts.containsKey(currentTab)) {
                def viewerKeys = viewerShortcuts[currentTab]
                def matchedViewer = viewerKeys.find { combo, action ->
                    combo.match(event)
                }

                if (matchedViewer) {
                    matchedViewer.value.run()
                    event.consume()
                }
            }
        })
    }

    /**
     * Select tab by index (0-based).
     */
    private void selectTabByIndex(int index) {
        if (index >= 0 && index < viewer.contentTabs.tabs.size()) {
            viewer.contentTabs.selectionModel.select(index)
        }
    }

    /**
     * Show keyboard shortcut help dialog.
     */
    void showShortcutHelp() {
        String help = """KEYBOARD SHORTCUTS

Global Shortcuts:
  Ctrl+F          Focus global search
  Ctrl+1          Switch to Books tab
  Ctrl+2          Switch to Signs tab
  Ctrl+3          Switch to Items tab
  Ctrl+4          Switch to Blocks tab
  Ctrl+5          Switch to Portals tab
  Ctrl+6          Switch to Map tab
  Ctrl+7          Switch to Statistics tab
  Ctrl+E          Export current view
  Ctrl+B          Add bookmark (if location selected)
  Escape          Clear search / close popups
  F1 or ?         Show this help

Book Viewer:
  ← / →           Previous / next page
  Ctrl+G          Go to page dialog
  C               Copy teleport command
  M               Show on map

Sign Viewer:
  C               Copy selected teleport command
  M               Show selected on map
  R               Show selected details

Item/Block Grid:
  Arrow keys      Navigate grid
  Enter           Select current item
  R               Show details
  C               Copy teleport command
  M               Show on map

Portal Viewer:
  C               Copy selected teleport command
  M               Show selected on map
  R               Show selected details

Map Viewer:
  + / -           Zoom in / out
  Arrow keys      Pan view
  Space           Reset view to default
  F               Toggle fullscreen mode

Statistics Viewer:
  C               Copy current statistics
"""

        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = "Keyboard Shortcuts"
        alert.headerText = "Output Viewer Keyboard Shortcuts"

        TextArea textArea = new TextArea(help)
        textArea.editable = false
        textArea.font = Font.font("Consolas", 12)
        textArea.prefRowCount = 35
        textArea.prefColumnCount = 60

        alert.dialogPane.content = textArea
        alert.dialogPane.prefWidth = 650

        // Apply icon if available
        try {
            def iconStream = getClass().getResourceAsStream('/icons/icon-512.png')
            if (iconStream) {
                alert.dialogPane.scene.window.icons.add(new javafx.scene.image.Image(iconStream))
            }
        } catch (Exception e) {
            // Silently fail if icon cannot be loaded
        }

        alert.showAndWait()
    }
}
