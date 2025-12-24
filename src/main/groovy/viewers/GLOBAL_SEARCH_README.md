# GlobalSearch Component

A unified search component that searches across all extracted Minecraft data types with intelligent relevance ranking and grouped results.

## Features

### Core Functionality
- **Unified Search**: Single search bar for books, signs, items, custom names, and portals
- **Relevance Ranking**: Exact match > Starts with > Contains > Fuzzy
- **Grouped Results**: Results organized by type with emoji icons
- **Real-time Search**: Debounced search-as-you-type (300ms delay)
- **Performance Optimized**: Maximum 50 results to prevent UI freeze
- **Keyboard Navigation**: Full keyboard support with shortcuts

### Search Capabilities

#### Books
- Search by title (highest priority)
- Search by author (medium priority)
- Search in page content (lower priority)

#### Signs
- Search all four lines of text
- Shows coordinates and dimension

#### Custom Names
- Search by custom name
- Search by entity/item type
- Search by item ID

#### Items (Database)
- Search by item ID
- Shows count, location, custom name
- Limited to top 10 matches for performance

#### Portals
- Search by dimension
- Search by portal ID
- Shows block count and center coordinates

### Relevance Scoring

Results are scored based on match quality:

| Match Type | Score | Example |
|------------|-------|---------|
| Exact match | 100 | Query: "diamond" → "Diamond" |
| Starts with | 80 | Query: "dia" → "Diamond Mining" |
| Whole word | 60 | Query: "mining" → "Diamond mining guide" |
| Contains | 50 | Query: "min" → "Diamond mining guide" |
| Fuzzy match | 10 | Query: "dmnd" → "Diamond" (all chars in order) |

### UI Components

```
┌──────────────────────────────────────┐
│ Global Search                        │
├──────────────────────────────────────┤
│ 🔍 [Search all data...            ] │
├──────────────────────────────────────┤
│ 📚 BOOKS (3 results)                 │
│   Diamond Mining Guide               │
│   by Steve | 4 pages | chest at...  │
│                                      │
│   My Diary - Day 12                  │
│   by Alex | 3 pages | player inv... │
├──────────────────────────────────────┤
│ 🪧 SIGNS (1 result)                  │
│   Diamond Mine | Level -59 | ...    │
│   at (100, -59, 200) in overworld   │
├──────────────────────────────────────┤
│ ⚔️ ITEMS (2 results)                 │
│   minecraft:diamond_sword            │
│   Count: 1 | Name: Excalibur | ...  │
└──────────────────────────────────────┘
│ 3 results found                      │
└──────────────────────────────────────┘
```

## Usage

### Basic Integration

```groovy
import viewers.GlobalSearch

// Create model with data
OutputViewerModel model = new OutputViewerModel()
model.loadFromFolder(new File("output"))

// Create search component with callback
GlobalSearch search = new GlobalSearch(model, { result ->
    // Handle result selection
    navigateToResult(result)
})

// Add to layout
VBox container = new VBox()
container.children.add(search)
```

### Handling Search Results

```groovy
GlobalSearch search = new GlobalSearch(model, { result ->
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
})
```

### Programmatic Control

```groovy
// Focus search field
search.focusSearch()

// Clear search
search.clearSearch()

// Update model (after reloading data)
search.updateModel(newModel)
```

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+F | Focus search field |
| Esc | Clear search |
| ↓ | Move to results list |
| ↑ | Move back to search field |
| Enter | Select highlighted result |
| Arrow keys | Navigate results |

## SearchResult Object

The callback receives a `SearchResult` object with:

```groovy
class SearchResult {
    String type         // 'book', 'sign', 'custom_name', 'item', 'portal'
    Object data         // Original data map
    String displayText  // Main display text
    String subtitle     // Secondary info text
    int score           // Relevance score (0-100)
}
```

### Example Usage

```groovy
void handleResult(GlobalSearch.SearchResult result) {
    println "Type: ${result.type}"
    println "Display: ${result.displayText}"
    println "Subtitle: ${result.subtitle}"
    println "Score: ${result.score}"

    // Access original data
    Map originalData = result.data
    if (result.type == 'book') {
        println "Title: ${originalData.title}"
        println "Author: ${originalData.author}"
        println "Pages: ${originalData.pages.size()}"
    }
}
```

## Configuration

### Constants (in GlobalSearch.groovy)

```groovy
private static final int MAX_RESULTS = 50
private static final int MIN_QUERY_LENGTH = 2
private static final long SEARCH_DEBOUNCE_MS = 300
```

Adjust these for different performance/UX trade-offs:

- `MAX_RESULTS`: Increase for more comprehensive results (may slow UI)
- `MIN_QUERY_LENGTH`: Decrease to search with single character (more noise)
- `SEARCH_DEBOUNCE_MS`: Decrease for instant search (more CPU usage)

## Demo Application

Run the standalone demo to explore all features:

```bash
./gradlew run -PmainClass=viewers.GlobalSearchDemo
```

The demo includes:
- Sample data across all types
- Interactive result selection dialogs
- Example search queries
- Full keyboard navigation

## Testing

Run the test suite:

```bash
./gradlew test --tests GlobalSearchSpec
```

Test coverage includes:
- Search by title, author, content
- Relevance ranking
- Result grouping
- Minimum query length
- Result limiting
- Clear functionality
- Callback invocation

## Architecture

### Thread Safety

- Search runs in background thread
- UI updates via `Platform.runLater()`
- Thread-safe debouncing

### Performance Optimizations

1. **Debouncing**: 300ms delay prevents excessive searches
2. **Result Limiting**: Maximum 50 results prevents UI freeze
3. **Background Search**: Doesn't block UI thread
4. **Lazy Database Queries**: Only searches database if available
5. **Score-based Sorting**: Results sorted once after collection

### Memory Footprint

- Search component: ~1 MB
- Result cache: ~50 results × ~500 bytes = 25 KB
- Total: < 2 MB (negligible)

## Integration Examples

See `GLOBAL_SEARCH_INTEGRATION.md` for complete integration examples:

- Sidebar layout (recommended)
- Floating window layout
- Custom navigation logic
- Advanced filtering

## Known Limitations

1. **Database Search**: Item database search limited to 10 results for performance
2. **No Regex**: Search is literal text matching (case-insensitive)
3. **No Wildcards**: Future feature
4. **Single Language**: English UI only (localizable)

## Future Enhancements

1. **Advanced Filters**: Checkboxes to include/exclude types
2. **Search History**: Remember recent searches
3. **Regex Support**: Allow pattern matching
4. **Export Results**: Save search results to file
5. **Saved Searches**: Bookmark frequently used queries
6. **Batch Operations**: Select multiple results for bulk actions

## Dependencies

- JavaFX 21+ (controls, layout)
- OutputViewerModel (data access)
- ItemDatabase (optional, for item search)
- BlockDatabase (optional, future feature)
- SLF4J (logging)

## Files

| File | Purpose |
|------|---------|
| `GlobalSearch.groovy` | Main component implementation |
| `GlobalSearchSpec.groovy` | Spock test suite |
| `GlobalSearchDemo.groovy` | Standalone demo application |
| `GLOBAL_SEARCH_README.md` | This documentation |
| `GLOBAL_SEARCH_INTEGRATION.md` | Integration guide |

## License

Same as parent project (ReadSignsAndBooks.jar).

## Author

Created as part of the Output Viewer feature set for ReadSignsAndBooks.jar.
