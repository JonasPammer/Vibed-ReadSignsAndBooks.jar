# GlobalSearch - Quick Start Guide

## 5-Minute Integration

### Step 1: Import
```groovy
import viewers.GlobalSearch
```

### Step 2: Create
```groovy
GlobalSearch search = new GlobalSearch(model, { result ->
    navigateToResult(result)
})
```

### Step 3: Add to UI
```groovy
// Option A: Sidebar
borderPane.left = search

// Option B: Dialog
Button searchButton = new Button('Search')
searchButton.onAction = { showSearchDialog(search) }
```

### Step 4: Handle Results
```groovy
void navigateToResult(GlobalSearch.SearchResult result) {
    switch (result.type) {
        case 'book':
            contentTabs.selectionModel.select(0)
            bookViewer.selectBook(result.data)
            break
        case 'sign':
            contentTabs.selectionModel.select(1)
            signViewer.selectSign(result.data)
            break
        // ... etc
    }
}
```

## Try the Demo

```bash
./gradlew run -PmainClass=viewers.GlobalSearchDemo
```

## Quick Reference

### Search Queries to Try
```
diamond    → Finds books, signs, items
steve      → Finds books by author
excalibur  → Finds custom named items
nether     → Finds portals, signs
spawn      → Finds signs
```

### Keyboard Shortcuts
```
Ctrl+F     → Focus search
Esc        → Clear search
↓          → Move to results
Enter      → Select result
```

### Result Types
```
📚 BOOKS         → From all_books_stendhal.json
🪧 SIGNS         → From all_signs.csv
🏷️ CUSTOM NAMES  → From custom_names.json
⚔️ ITEMS         → From items.db (if exists)
🚪 PORTALS       → From portals.json
```

### Relevance Scores
```
100  → Exact match      ("diamond" = "Diamond")
80   → Starts with      ("dia" in "Diamond Mine")
60   → Whole word       ("mine" in "Diamond mine")
50   → Contains         ("mond" in "Diamond")
10   → Fuzzy            ("dmnd" in "Diamond")
```

## Example Usage

### Basic Search
```groovy
GlobalSearch search = new GlobalSearch(model, { result ->
    println "Found: ${result.displayText}"
})

search.searchField.text = "diamond"
// Results appear after 300ms debounce
```

### Programmatic Control
```groovy
search.focusSearch()         // Ctrl+F equivalent
search.clearSearch()         // Esc equivalent
search.updateModel(newModel) // After reloading data
```

### Access Result Data
```groovy
GlobalSearch search = new GlobalSearch(model, { result ->
    Map data = result.data

    if (result.type == 'book') {
        println "Title: ${data.title}"
        println "Author: ${data.author}"
        println "Pages: ${data.pages.size()}"
    }

    if (result.type == 'sign') {
        println "Lines: ${data.line1} | ${data.line2}"
        println "Location: (${data.x}, ${data.y}, ${data.z})"
    }
})
```

## Common Patterns

### Sidebar Integration
```groovy
HBox content = new HBox()
content.children.addAll(
    search,                        // Left sidebar
    new Separator(Orientation.VERTICAL),
    mainContentArea                // Right content
)
HBox.setHgrow(mainContentArea, Priority.ALWAYS)
```

### Floating Window
```groovy
Stage searchWindow = new Stage()
searchWindow.title = 'Global Search'
searchWindow.scene = new Scene(search, 500, 600)
searchWindow.show()
search.focusSearch()
```

### Menu Integration
```groovy
MenuBar menuBar = new MenuBar()
Menu searchMenu = new Menu('Search')
MenuItem globalSearchItem = new MenuItem('Global Search...')
globalSearchItem.accelerator = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)
globalSearchItem.onAction = { search.focusSearch() }
searchMenu.items.add(globalSearchItem)
menuBar.menus.add(searchMenu)
```

## Troubleshooting

### No results appear
- Check minimum query length (2 characters)
- Verify model has data loaded
- Check search matches case-insensitively

### Slow search
- Large datasets (>1000 items) may take 200-500ms
- Debounce ensures UI stays responsive
- Results limited to 50 for performance

### Callback not firing
- Ensure result type != 'header'
- Check selection before callback
- Verify callback closure is not null

### Database not searched
- Verify model.itemDatabase is not null
- Check ItemDatabase.openForQuery() succeeded
- Database search limited to 10 results

## Configuration

Edit constants in `GlobalSearch.groovy`:

```groovy
private static final int MAX_RESULTS = 50          // Result limit
private static final int MIN_QUERY_LENGTH = 2      // Min chars
private static final long SEARCH_DEBOUNCE_MS = 300 // Debounce delay
```

## Full Documentation

- **Component API**: `src/main/groovy/viewers/GLOBAL_SEARCH_README.md`
- **Integration Guide**: `GLOBAL_SEARCH_INTEGRATION.md`
- **Implementation Details**: `GLOBAL_SEARCH_SUMMARY.md`

## Support

For issues or questions:
1. Check the comprehensive README
2. Run the demo for examples
3. Review integration guide for patterns
4. Inspect test suite for usage examples

---

**Status**: Production Ready ✅
**Version**: 1.0
**Last Updated**: 2025-12-23
