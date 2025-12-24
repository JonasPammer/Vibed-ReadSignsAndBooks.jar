# Global Search Integration Guide

This document shows how to integrate the GlobalSearch component into the OutputViewer.

## Integration Steps

### 1. Add GlobalSearch to OutputViewer

In `OutputViewer.groovy`, add the GlobalSearch component:

```groovy
import viewers.GlobalSearch

class OutputViewer extends Stage {

    OutputViewerModel model
    TabPane contentTabs
    TreeView<FileTreeItem> folderTree
    Label statusBar
    Button loadButton
    TextField folderField
    GlobalSearch globalSearch  // Add this field

    OutputViewer() {
        // ... existing code ...

        // Build UI
        BorderPane root = new BorderPane()

        // Top: Toolbar with folder selection
        root.top = createToolbar()

        // Center: Main content area with search sidebar
        HBox centerContent = new HBox()

        // Create global search component
        globalSearch = new GlobalSearch(model, { result ->
            navigateToResult(result)
        })
        globalSearch.prefWidth = 350
        globalSearch.minWidth = 300
        globalSearch.maxWidth = 500

        // Create content area
        SplitPane mainContent = new SplitPane()
        mainContent.dividerPositions = [0.25] as double[]

        // Left: Folder tree (existing)
        folderTree = createFolderTree()
        VBox leftPane = new VBox(new Label('Output Files:').with { it.style = '-fx-font-weight: bold;'; it }, folderTree)
        leftPane.padding = new Insets(10)
        VBox.setVgrow(folderTree, Priority.ALWAYS)

        // Right: Content tabs (existing)
        contentTabs = createContentTabs()

        mainContent.items.addAll(leftPane, contentTabs)

        // Combine search + main content
        centerContent.children.addAll(globalSearch, new Separator(Orientation.VERTICAL), mainContent)
        HBox.setHgrow(mainContent, Priority.ALWAYS)

        root.center = centerContent

        // ... rest of existing code ...
    }

    /**
     * Navigate to a search result.
     */
    private void navigateToResult(GlobalSearch.SearchResult result) {
        switch (result.type) {
            case 'book':
                navigateToBook(result.data)
                break
            case 'sign':
                navigateToSign(result.data)
                break
            case 'custom_name':
                navigateToCustomName(result.data)
                break
            case 'item':
                navigateToItem(result.data)
                break
            case 'portal':
                navigateToPortal(result.data)
                break
        }
    }

    private void navigateToBook(Map book) {
        // Switch to Books tab
        contentTabs.selectionModel.select(0)

        // TODO: Implement BookViewer integration
        // If BookViewer is a custom component, call:
        // bookViewer.selectBook(book)

        LOGGER.info("Navigating to book: ${book.title}")
    }

    private void navigateToSign(Map sign) {
        // Switch to Signs tab
        contentTabs.selectionModel.select(1)

        // TODO: Implement SignViewer integration
        // If SignViewer is a custom component, call:
        // signViewer.selectSign(sign)

        LOGGER.info("Navigating to sign at (${sign.x}, ${sign.y}, ${sign.z})")
    }

    private void navigateToCustomName(Map customName) {
        // Switch to appropriate tab based on type
        contentTabs.selectionModel.select(2) // Items or Custom Names tab

        LOGGER.info("Navigating to custom name: ${customName.name}")
    }

    private void navigateToItem(Map item) {
        // Switch to Items tab
        contentTabs.selectionModel.select(2)

        // TODO: Implement ItemViewer integration

        LOGGER.info("Navigating to item: ${item.item_id}")
    }

    private void navigateToPortal(Map portal) {
        // Switch to Portals tab
        contentTabs.selectionModel.select(4)

        // TODO: Implement PortalViewer integration
        // If PortalViewer is a custom component, call:
        // portalViewer.selectPortal(portal)

        LOGGER.info("Navigating to portal #${portal.portal_id}")
    }

    /**
     * Update UI after loading data.
     */
    private void updateUI() {
        // Update folder tree
        updateFolderTree()

        // Update global search model
        globalSearch.updateModel(model)

        // Update tabs (existing code)
        updateBooksTab()
        updateSignsTab()
        updateItemsTab()
        updateBlocksTab()
        updatePortalsTab()
        updateStatisticsTab()
    }
}
```

### 2. Alternative: Floating Search Window

If you prefer a floating search window instead of a sidebar:

```groovy
class OutputViewer extends Stage {

    Stage searchWindow
    GlobalSearch globalSearch

    OutputViewer() {
        // ... existing code ...

        // Create menu bar
        MenuBar menuBar = new MenuBar()
        Menu searchMenu = new Menu('Search')
        MenuItem globalSearchItem = new MenuItem('Global Search...')
        globalSearchItem.accelerator = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)
        globalSearchItem.onAction = { event -> showGlobalSearch() }
        searchMenu.items.add(globalSearchItem)
        menuBar.menus.add(searchMenu)

        root.top = new VBox(menuBar, createToolbar())
    }

    private void showGlobalSearch() {
        if (!searchWindow) {
            searchWindow = new Stage()
            searchWindow.title = 'Global Search'
            searchWindow.initOwner(this)

            globalSearch = new GlobalSearch(model, { result ->
                navigateToResult(result)
            })

            Scene searchScene = new Scene(globalSearch, 500, 600)
            searchWindow.scene = searchScene

            // Close handler
            searchWindow.onCloseRequest = { event ->
                searchWindow.hide()
                event.consume()
            }
        }

        if (!searchWindow.showing) {
            searchWindow.show()
            globalSearch.focusSearch()
        } else {
            searchWindow.toFront()
            globalSearch.focusSearch()
        }
    }
}
```

### 3. Keyboard Shortcut Setup

The GlobalSearch component automatically sets up Ctrl+F when added to a scene. To ensure it works:

1. Add GlobalSearch to scene graph
2. The component's `setupKeyboardShortcuts()` registers Ctrl+F
3. Pressing Ctrl+F focuses the search field

### 4. Custom Navigation Logic

For more advanced navigation with highlighting:

```groovy
private void navigateToBook(Map book) {
    // Switch to Books tab
    Tab booksTab = contentTabs.tabs[0]
    contentTabs.selectionModel.select(booksTab)

    // If using custom BookViewer component
    if (booksTab.content instanceof BookViewer) {
        BookViewer bookViewer = booksTab.content as BookViewer

        // Find book in viewer's list
        int bookIndex = bookViewer.filteredBooks.indexOf(book)
        if (bookIndex >= 0) {
            // Select in list
            bookViewer.bookListView.selectionModel.select(bookIndex)

            // Scroll to selected item
            bookViewer.bookListView.scrollTo(bookIndex)

            // Display book
            bookViewer.displayCurrentPages()
        }
    }
}

private void navigateToSign(Map sign) {
    // Switch to Signs tab
    Tab signsTab = contentTabs.tabs[1]
    contentTabs.selectionModel.select(signsTab)

    // If using custom SignViewer component
    if (signsTab.content instanceof SignViewer) {
        SignViewer signViewer = signsTab.content as SignViewer

        // Highlight sign in viewer
        signViewer.highlightSign(sign)

        // Center map on sign coordinates
        if (signViewer.hasMapView()) {
            signViewer.centerMapOn(sign.x as int, sign.z as int)
        }
    }
}
```

## Testing Integration

Create a test to verify integration:

```groovy
def "should integrate global search into OutputViewer"() {
    given:
    OutputViewer viewer = new OutputViewer()
    viewer.model.loadFromFolder(new File('test/output'))

    when:
    Platform.runLater {
        viewer.globalSearch.searchField.text = "diamond"
    }
    Thread.sleep(500)

    and:
    Platform.runLater {
        viewer.globalSearch.resultsList.selectionModel.selectFirst()
        viewer.globalSearch.handleResultSelection()
    }
    Thread.sleep(100)

    then:
    // Verify navigation occurred
    viewer.contentTabs.selectionModel.selectedIndex == 0 // Books tab
}
```

## UI Layout Examples

### Sidebar Layout (Recommended)
```
┌────────────────────────────────────────────────────────┐
│  [Output Folder] [Browse] [Load] [Refresh]            │
├──────────────┬─────────────────────────────────────────┤
│ GLOBAL       │  ┌──────────┬──────────────────────┐   │
│ SEARCH       │  │ Files    │ Books | Signs | ... │   │
│              │  │          │                      │   │
│ 🔍 [Search   │  │ output/  │                      │   │
│    all...  ] │  │ ├books   │                      │   │
│              │  │ ├signs   │                      │   │
│ 📚 BOOKS (3) │  │ ├items.db│                      │   │
│ "Diamond..." │  │ └portals │                      │   │
│ "My Diary"   │  │          │                      │   │
│              │  │          │                      │   │
│ 🪧 SIGNS (1) │  │          │                      │   │
│ "Spawn..."   │  └──────────┴──────────────────────┘   │
│              │                                         │
└──────────────┴─────────────────────────────────────────┘
```

### Floating Window Layout
```
Main Window:                    Search Window:
┌───────────────────┐          ┌─────────────────┐
│ File Edit Search  │          │ Global Search   │
│ ─────────────────│          ├─────────────────┤
│ [Folder] [Load]  │          │ 🔍 [Search...  ]│
│ ┌───┬───────────┐│          │                 │
│ │   │ Books     ││          │ 📚 BOOKS (3)    │
│ │   │           ││          │ "Diamond Mine"  │
│ │   │           ││          │ "My Diary"      │
│ │   │           ││          │                 │
│ └───┴───────────┘│          │ 🪧 SIGNS (1)    │
└───────────────────┘          │ "Spawn Point"   │
                               └─────────────────┘
```

## Performance Considerations

1. **Debouncing**: Search triggers 300ms after last keystroke
2. **Result Limit**: Maximum 50 results to prevent UI freeze
3. **Background Search**: Search runs in separate thread
4. **Lazy Loading**: Only search active data (don't load databases until needed)

## Future Enhancements

1. **Advanced Filters**: Add checkboxes to include/exclude specific types
2. **Search History**: Remember recent searches
3. **Regex Support**: Allow regex patterns in search
4. **Export Results**: Export search results to CSV/JSON
5. **Saved Searches**: Save frequently used search queries
