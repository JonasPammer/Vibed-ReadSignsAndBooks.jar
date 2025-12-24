# Statistics Dashboard - Detailed Design Document

## Executive Summary

This document provides comprehensive research findings and implementation recommendations for adding an interactive Statistics Dashboard to the ReadSignsAndBooks.jar GUI. The dashboard will visualize extraction analytics using JavaFX's built-in charting capabilities with modern theming via AtlantaFX.

---

## 1. JavaFX Charts Research

### 1.1 Built-in Chart Capabilities

JavaFX 21 provides a rich set of built-in chart components in the `javafx.scene.chart` package:

**Available Chart Types:**
- **PieChart** - Circular charts with wedge slices
- **BarChart** - Vertical/horizontal bar charts (categorical data)
- **StackedBarChart** - Multiple data series stacked on bars
- **LineChart** - Line graphs for trends over time/categories
- **AreaChart** - Filled area under line graphs
- **StackedAreaChart** - Multiple stacked area series
- **ScatterChart** - X/Y coordinate scatter plots
- **BubbleChart** - Scatter plots with size dimension

**Key Components:**
- **CategoryAxis** - Categorical labels (e.g., "Java", "Groovy", "Books by Author")
- **NumberAxis** - Numerical values with auto-scaling
- **XYChart.Series** - Data series with name and data points
- **XYChart.Data** - Individual data points

**API Documentation:**
```java
// Example: Creating a PieChart
ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
    new PieChart.Data("Books", 150),
    new PieChart.Data("Signs", 75),
    new PieChart.Data("Items", 320)
);
PieChart chart = new PieChart(pieData);
chart.setTitle("Content Distribution");

// Example: Creating a BarChart
CategoryAxis xAxis = new CategoryAxis();
xAxis.setLabel("Author");
NumberAxis yAxis = new NumberAxis();
yAxis.setLabel("Book Count");

BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
XYChart.Series<String, Number> series = new XYChart.Series<>();
series.setName("Books by Author");
series.getData().add(new XYChart.Data<>("Alice", 25));
series.getData().add(new XYChart.Data<>("Bob", 42));
barChart.getData().add(series);
```

### 1.2 Customization Options

**Colors:**
- Default theme provides 8 chart colors: `-color-chart-1` through `-color-chart-8`
- CSS styling: `.chart-pie-label`, `.chart-bar`, `.chart-series-line`
- AtlantaFX provides modern color palettes (Primer, Nord, Dracula, Cupertino)

**Labels & Legends:**
```java
chart.setTitle("My Chart");
chart.setLegendVisible(true);
chart.setLegendSide(Side.RIGHT);  // TOP, BOTTOM, LEFT, RIGHT

// PieChart-specific
pieChart.setLabelsVisible(true);
pieChart.setLabelLineLength(10);
pieChart.setStartAngle(30);  // Rotation offset

// BarChart-specific
barChart.setBarGap(5);       // Space between bars in same category
barChart.setCategoryGap(10);  // Space between categories
```

**Animation:**
- Built-in animation on data changes
- Controlled via `chart.setAnimated(true/false)`

**Tooltips:**
- Not built-in, must be added manually via event handlers
- Implementation pattern:
```java
for (PieChart.Data data : pieChart.getData()) {
    Tooltip tooltip = new Tooltip(
        data.getName() + ": " + data.getPieValue()
    );
    Tooltip.install(data.getNode(), tooltip);
}
```

### 1.3 Performance with Large Datasets

**Observations from Research:**
- JavaFX charts are designed for **hundreds to low thousands** of data points
- Beyond 5,000+ points, performance degrades significantly
- Recommendation: Aggregate data for visualization (e.g., top 20 authors, not all 500)
- Use pagination or filtering for large datasets

**Optimization Strategies:**
1. Limit visible data points (top N, grouped "Other" category)
2. Disable animation for large datasets: `chart.setAnimated(false)`
3. Use `StackedBarChart` to consolidate series instead of separate bars
4. Consider lazy loading for drill-down views

### 1.4 Third-Party Chart Libraries

**Research Findings:**
- **JFreeChart** - Mature but outdated UI aesthetics, Swing-based (not JavaFX native)
- **Flowless** - High-performance virtualized lists (not charts)
- **AtlantaFX** - Modern theming only, no chart library
- **Recommendation:** Stick with built-in JavaFX charts - sufficient for our use case

---

## 2. Useful Visualizations for Minecraft Data

### 2.1 Books Analytics

**Primary Metrics (KPI Cards):**
- Total unique books extracted
- Total duplicate copies found
- Books with authors vs. unsigned
- Average pages per book

**Visualization 1: Books by Author (BarChart)**
- X-axis: Author names (top 20, "Others" grouped)
- Y-axis: Book count
- Click bar → filter main viewer to that author's books
- Tooltip: Author name, exact count, % of total

**Visualization 2: Books by Page Count Distribution (BarChart/LineChart)**
- X-axis: Page count ranges (1-10, 11-20, 21-30, 31-50, 51+)
- Y-axis: Number of books
- Identifies if players write short notes or epic novels

**Visualization 3: Books by Generation (PieChart)**
- Slices: Original, Copy of Original, Copy of Copy, Tattered
- Shows preservation/copying patterns

**Visualization 4: Books by Container Type (StackedBarChart)**
- X-axis: Container types (Chest, Shulker Box, Barrel, etc.)
- Y-axis: Book count
- Multiple series: Authored vs. Unsigned
- Shows where players store books (e.g., shulkers for transport)

### 2.2 Items Analytics

**Primary Metrics (KPI Cards):**
- Total custom-named items
- Most common item type with custom name
- Longest custom name length

**Visualization 1: Custom Names by Item Type (PieChart)**
- Top 10 item types, "Others" grouped
- Shows what items players rename most

**Visualization 2: Custom Names by Dimension (BarChart)**
- X-axis: Overworld, Nether, End
- Y-axis: Count
- Shows dimension activity patterns

**Visualization 3: Enchanted Items by Enchantment (BarChart)**
- X-axis: Enchantment types (if extracted)
- Y-axis: Count
- Note: Not currently extracted, future feature

### 2.3 Blocks Analytics

**Primary Metrics (KPI Cards):**
- Total block types indexed
- Total block instances found
- Most common block type
- Search coverage (% of world scanned)

**Visualization 1: Block Distribution by Type (BarChart)**
- X-axis: Block types (top 20)
- Y-axis: Count
- Click bar → show coordinates in table below

**Visualization 2: Blocks by Dimension (StackedBarChart)**
- X-axis: Dimensions
- Y-axis: Count
- Series: Different block types
- Shows dimension-specific blocks (e.g., nether_portal in Overworld/Nether)

**Visualization 3: Block Density Heatmap (Future - Advanced)**
- 2D grid visualization of X/Z coordinates
- Color intensity = block frequency
- Requires custom drawing, not built-in chart

### 2.4 Signs Analytics

**Primary Metrics (KPI Cards):**
- Total unique signs
- Total duplicate signs
- Average text length
- Empty signs count

**Visualization 1: Word Cloud of Sign Text (Custom Implementation)**
- Parse all sign lines
- Calculate word frequency (excluding common words: "the", "a", "and")
- Size = frequency
- **Implementation Notes:**
  - No native JavaFX word cloud component
  - Can use `TextFlow` with varied font sizes
  - Alternative: External library like Kumo (Java word cloud library)
  - Complexity: High, recommend **deprioritize for MVP**

**Visualization 2: Sign Text Length Distribution (BarChart)**
- X-axis: Character count ranges (0, 1-20, 21-40, 41-60, 61-80)
- Y-axis: Number of signs
- Shows if players use signs for labels vs. descriptions

**Visualization 3: Common Phrases/Keywords (BarChart - Simplified)**
- Extract most frequent 2-3 word phrases
- X-axis: Phrases (top 15)
- Y-axis: Frequency
- Simpler than word cloud, shows patterns

### 2.5 Portals Analytics

**Primary Metrics (KPI Cards):**
- Total portal structures found
- Average portal size (blocks)
- Largest portal size
- Portals by dimension

**Visualization 1: Portal Size Distribution (BarChart)**
- X-axis: Portal size ranges (2x3, 3x3, 4x5, custom large)
- Y-axis: Count
- Shows if players build standard or custom portals

**Visualization 2: Portals by Dimension (PieChart)**
- Slices: Overworld, Nether, End
- Simple distribution view

---

## 3. Interactive Charts Research

### 3.1 Click-Through Behavior

**Pattern: Chart Click → Filter Main Viewer**

**Implementation:**
```java
// PieChart example
for (PieChart.Data data : pieChart.getData()) {
    data.getNode().addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
        String category = data.getName();
        filterMainViewerBy(category);  // Call method to update main UI
    });
}

// BarChart example
for (XYChart.Series<String, Number> series : barChart.getData()) {
    for (XYChart.Data<String, Number> data : series.getData()) {
        data.getNode().addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            String category = data.getXValue();
            Number count = data.getYValue();
            showDrillDownView(category, count);
        });
    }
}
```

**User Flow:**
1. User clicks "Alice" bar in "Books by Author" chart
2. Dashboard fires event → Main GUI switches to "Books" tab
3. Filter field auto-populates with "Author: Alice"
4. TableView shows only Alice's books

**Visual Feedback:**
- Hover effect: Change opacity or add stroke
```css
.chart-bar:hover {
    -fx-opacity: 0.8;
    -fx-stroke: -fx-accent;
    -fx-stroke-width: 2;
}
```

### 3.2 Drill-Down Views

**Two-Level Hierarchy:**

**Level 1: Category Overview (e.g., Books by Author)**
- Shows top 20 authors + "Others (350)" grouped category
- Click "Others" → expands to second-level view

**Level 2: Detailed Breakdown**
- Shows all 350 authors in paginated table
- Sortable by book count
- Search filter box

**Breadcrumb Navigation:**
```
Dashboard > Books by Author > "Others" Authors (350)
[<< Back to Overview]
```

### 3.3 Tooltips on Hover

**Implementation Pattern:**
```java
Tooltip tooltip = new Tooltip();
tooltip.setShowDelay(Duration.millis(100));
tooltip.setHideDelay(Duration.millis(5000));

for (PieChart.Data data : pieChart.getData()) {
    double percentage = data.getPieValue() / total * 100;
    String tooltipText = String.format(
        "%s: %d items (%.1f%%)",
        data.getName(),
        (int) data.getPieValue(),
        percentage
    );
    Tooltip.install(data.getNode(), new Tooltip(tooltipText));
}
```

**Tooltip Content:**
- Exact counts (chart may round for space)
- Percentage of total
- Additional metadata (e.g., "42 books, avg 23 pages")

---

## 4. Data Comparison Features

### 4.1 Multiple Extraction Runs Side-by-Side

**Use Case:** Compare world state before/after major event (e.g., server reset, griefing cleanup)

**Implementation Approach:**

**Option A: Session History Database (Recommended)**
```groovy
// SQLite schema
CREATE TABLE extraction_sessions (
    id INTEGER PRIMARY KEY,
    world_name TEXT,
    extraction_date TIMESTAMP,
    total_books INTEGER,
    total_signs INTEGER,
    total_items INTEGER,
    output_folder TEXT
);

CREATE TABLE session_details (
    session_id INTEGER,
    metric_name TEXT,      -- e.g., "books_by_author"
    metric_value TEXT,     -- JSON: {"Alice": 25, "Bob": 42}
    FOREIGN KEY (session_id) REFERENCES extraction_sessions(id)
);
```

**Option B: JSON Export/Import**
```json
{
  "session": {
    "id": "2025-12-23T10:30:00",
    "world_name": "MyServer",
    "metrics": {
      "books_total": 150,
      "books_by_author": {"Alice": 25, "Bob": 42},
      "signs_total": 75,
      "items_custom_named": 320
    }
  }
}
```

**UI Design:**
```
[Comparison Mode]
┌─────────────────────────────────────────┐
│ Session 1: 2025-12-20 (Before Reset)   │
│ Session 2: 2025-12-23 (After Reset)    │
│                                         │
│ Books: 150 → 95  [-55, -36.7%] 📉      │
│ Signs: 75 → 82   [+7, +9.3%] 📈        │
│                                         │
│ [Show Side-by-Side Charts]             │
└─────────────────────────────────────────┘
```

### 4.2 Before/After World Changes

**Visual Comparison Patterns:**

**Pattern 1: Dual BarCharts (Side-by-Side)**
```
Books by Author Comparison
┌───────────────┬───────────────┐
│   Before      │     After     │
│               │               │
│ Alice ████    │ Alice ██      │
│ Bob ████████  │ Bob ██████    │
│ Carol ███     │ Carol ████    │
└───────────────┴───────────────┘
```

**Pattern 2: Delta Chart (Change Visualization)**
```
Change in Book Count by Author
┌─────────────────────────────┐
│ Alice  [-13] ◄◄◄◄◄          │
│ Bob    [-8]  ◄◄◄            │
│ Carol  [+5]       ►►        │
│ Dave   [+20]      ►►►►►►►►  │
└─────────────────────────────┘
```

**Implementation:**
```java
BarChart<String, Number> beforeChart = createChart(session1Data);
BarChart<String, Number> afterChart = createChart(session2Data);

HBox comparisonView = new HBox(10, beforeChart, afterChart);
```

### 4.3 Export Stats to CSV/JSON

**CSV Export Format:**
```csv
Metric,Session_Date,Value
books_total,2025-12-20,150
books_total,2025-12-23,95
books_by_author_Alice,2025-12-20,25
books_by_author_Alice,2025-12-23,12
```

**JSON Export Format:**
```json
{
  "sessions": [
    {
      "date": "2025-12-20T10:00:00",
      "world": "MyServer",
      "books": {"total": 150, "by_author": {"Alice": 25}},
      "signs": {"total": 75}
    }
  ]
}
```

**Implementation:**
```groovy
class StatisticsExporter {
    static void exportToCsv(DashboardMetrics metrics, File outputFile) {
        def csv = new StringBuilder("Metric,Value\n")
        csv.append("books_total,${metrics.booksTotal}\n")
        csv.append("signs_total,${metrics.signsTotal}\n")
        // ... more metrics
        outputFile.text = csv.toString()
    }

    static void exportToJson(DashboardMetrics metrics, File outputFile) {
        def json = JsonOutput.toJson([
            session: [
                timestamp: metrics.timestamp,
                world: metrics.worldName,
                books: [total: metrics.booksTotal],
                signs: [total: metrics.signsTotal]
            ]
        ])
        outputFile.text = JsonOutput.prettyPrint(json)
    }
}
```

---

## 5. Dashboard Layout Research

### 5.1 Layout Patterns

**Recommended Layout: Bento Grid / Card-Based Dashboard**

**Research Findings:**
- Modern dashboards use "bento grid" layouts (popularized by iOS widgets)
- Larger cards = primary metrics, smaller cards = secondary/tertiary
- Responsive grid adapts to window size

**JavaFX Layout Components:**

**GridPane (Recommended for Dashboard)**
```java
GridPane dashboardGrid = new GridPane();
dashboardGrid.setHgap(10);  // Horizontal gap between columns
dashboardGrid.setVgap(10);  // Vertical gap between rows
dashboardGrid.setPadding(new Insets(15));

// Add components with column/row span
dashboardGrid.add(kpiCardsRow, 0, 0, 3, 1);  // Span 3 columns
dashboardGrid.add(booksByAuthorChart, 0, 1, 2, 2);  // 2 cols, 2 rows
dashboardGrid.add(booksGenPieChart, 2, 1, 1, 1);  // 1 col, 1 row
```

**TilePane (Alternative for Equal-Sized Cards)**
```java
TilePane tilePane = new TilePane();
tilePane.setPrefColumns(3);
tilePane.setHgap(10);
tilePane.setVgap(10);
tilePane.getChildren().addAll(card1, card2, card3);  // Auto-flows
```

**FlowPane (Auto-Wrapping, Less Control)**
```java
FlowPane flowPane = new FlowPane(Orientation.HORIZONTAL);
flowPane.setHgap(10);
flowPane.setVgap(10);
flowPane.getChildren().addAll(charts);  // Wraps at window edge
```

**Recommendation:** Use **GridPane** for precise control, **TilePane** for simpler equal-sized layouts.

### 5.2 Responsive Layout for Different Window Sizes

**Strategy: ColumnConstraints with Percentage Widths**
```java
GridPane grid = new GridPane();

ColumnConstraints col1 = new ColumnConstraints();
col1.setPercentWidth(40);  // 40% of available width
ColumnConstraints col2 = new ColumnConstraints();
col2.setPercentWidth(30);
ColumnConstraints col3 = new ColumnConstraints();
col3.setPercentWidth(30);

grid.getColumnConstraints().addAll(col1, col2, col3);
```

**Window Size Breakpoints:**
- **Small (< 900px width):** Stack vertically, 1 column
- **Medium (900-1400px):** 2 columns
- **Large (> 1400px):** 3 columns

**Implementation:**
```java
scene.widthProperty().addListener((obs, oldVal, newVal) -> {
    double width = newVal.doubleValue();
    if (width < 900) {
        applySmallLayout(grid);
    } else if (width < 1400) {
        applyMediumLayout(grid);
    } else {
        applyLargeLayout(grid);
    }
});
```

### 5.3 Customizable/Rearrangeable Widgets (Advanced)

**Implementation Complexity: High**

**Approach 1: Drag-and-Drop Reordering**
```java
chart.setOnDragDetected(event -> {
    Dragboard db = chart.startDragAndDrop(TransferMode.MOVE);
    ClipboardContent content = new ClipboardContent();
    content.putString(chart.getId());
    db.setContent(content);
});

chartContainer.setOnDragDropped(event -> {
    String chartId = event.getDragboard().getString();
    reorderCharts(chartId);
});
```

**Approach 2: Configuration File (Simpler)**
```json
{
  "dashboard_layout": {
    "row_1": ["books_total_kpi", "signs_total_kpi", "items_total_kpi"],
    "row_2": ["books_by_author_chart", "books_generation_pie"],
    "row_3": ["signs_length_chart"]
  }
}
```

**Recommendation:** **Defer to v2** - Fixed layout for MVP, add customization later based on user feedback.

### 5.4 Print-Friendly Report Generation

**Approach: Export Dashboard as Image/PDF**

**Option 1: Snapshot to PNG (Recommended for MVP)**
```java
void exportDashboardAsImage(Node dashboardNode, File outputFile) {
    WritableImage snapshot = dashboardNode.snapshot(
        new SnapshotParameters(),
        null
    );
    try {
        ImageIO.write(
            SwingFXUtils.fromFXImage(snapshot, null),
            "png",
            outputFile
        );
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

**Option 2: HTML Report (Better for Printing)**
```groovy
class DashboardReportGenerator {
    static void generateHtmlReport(DashboardMetrics metrics, File outputFile) {
        def html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Extraction Statistics Report</title>
            <style>
                body { font-family: Arial; }
                .kpi-card { border: 1px solid #ccc; padding: 15px; margin: 10px; }
                .chart-placeholder { width: 600px; height: 400px; }
                @media print {
                    .no-print { display: none; }
                }
            </style>
        </head>
        <body>
            <h1>ReadSignsAndBooks Extraction Report</h1>
            <p>World: ${metrics.worldName}</p>
            <p>Date: ${metrics.timestamp}</p>

            <h2>Key Metrics</h2>
            <div class="kpi-card">
                <h3>Books</h3>
                <p>Total: ${metrics.booksTotal}</p>
            </div>

            <h2>Charts</h2>
            <img src="books_by_author.png" class="chart-placeholder">

            <button class="no-print" onclick="window.print()">Print Report</button>
        </body>
        </html>
        """
        outputFile.text = html
    }
}
```

**Option 3: PDF Export via iText (Advanced)**
- Requires additional library: `com.itextpdf:itextpdf:5.5.13`
- Embeds charts as images
- Full control over layout

**Recommendation:** Start with **PNG export** for MVP, add **HTML report** in v1.1.

---

## 6. Performance Metrics Display

### 6.1 Extraction Metrics to Show

**Real-Time Metrics (During Extraction):**
- Extraction time elapsed (live timer)
- Files processed / Total files (progress %)
- Current processing speed (files/second)
- Estimated time remaining

**Post-Extraction Summary:**
- Total extraction time
- Total files scanned (region + entity + playerdata)
- Average processing speed
- Memory usage peak (if available)

**Implementation:**
```groovy
class ExtractionMetrics {
    long startTime
    long endTime
    int totalFilesProcessed
    int regionFilesProcessed
    int entityFilesProcessed
    int playerdataFilesProcessed
    int errorsEncountered
    int warningsEncountered

    String getFormattedDuration() {
        long durationMs = endTime - startTime
        long seconds = durationMs / 1000
        long minutes = seconds / 60
        long hours = minutes / 60
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
    }

    double getProcessingSpeed() {
        double durationSeconds = (endTime - startTime) / 1000.0
        return totalFilesProcessed / durationSeconds
    }
}
```

### 6.2 Files Processed Breakdown

**Visualization: StackedBarChart**
```
Files Processed by Type
┌──────────────────────────┐
│ Region    ████████ 1250  │
│ Entities  ██ 150         │
│ Player    █ 45           │
└──────────────────────────┘
```

**Implementation:**
```java
CategoryAxis xAxis = new CategoryAxis();
xAxis.setLabel("File Type");
NumberAxis yAxis = new NumberAxis();
yAxis.setLabel("Count");

BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
XYChart.Series<String, Number> series = new XYChart.Series<>();
series.getData().add(new XYChart.Data<>("Region Files", 1250));
series.getData().add(new XYChart.Data<>("Entity Files", 150));
series.getData().add(new XYChart.Data<>("Playerdata Files", 45));
chart.getData().add(series);
```

### 6.3 Memory Usage Display

**Approach: Query JVM Runtime**
```java
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
long maxMemory = runtime.maxMemory();
double usagePercent = (usedMemory * 100.0) / maxMemory;

Label memoryLabel = new Label(String.format(
    "Memory: %d MB / %d MB (%.1f%%)",
    usedMemory / 1024 / 1024,
    maxMemory / 1024 / 1024,
    usagePercent
));
```

**Visualization: Progress Bar**
```java
ProgressBar memoryBar = new ProgressBar();
memoryBar.setProgress(usagePercent / 100.0);
if (usagePercent > 80) {
    memoryBar.setStyle("-fx-accent: red;");  // Warning color
} else if (usagePercent > 60) {
    memoryBar.setStyle("-fx-accent: orange;");
}
```

### 6.4 Errors/Warnings Encountered

**Display Format:**
```
Extraction Summary
──────────────────
✓ Success: 1,445 files
⚠ Warnings: 3 files (corrupted NBT, recovered)
✗ Errors: 1 file (skipped)

[View Error Log]
```

**Implementation:**
```java
VBox summaryBox = new VBox(5);
summaryBox.getChildren().addAll(
    new Label("✓ Success: " + metrics.successCount + " files"),
    new Label("⚠ Warnings: " + metrics.warningCount + " files"),
    new Label("✗ Errors: " + metrics.errorCount + " files")
);

Hyperlink viewLog = new Hyperlink("View Error Log");
viewLog.setOnAction(e -> showErrorLogDialog());
```

---

## 7. Modern Theming with AtlantaFX

### 7.1 AtlantaFX Integration

**Why AtlantaFX:**
- Modern, professionally designed themes
- Dark mode support out-of-box
- GitHub Primer, Nord, Dracula, Cupertino color schemes
- Drop-in replacement for default JavaFX styling
- Chart color variables (`-color-chart-1` through `-color-chart-8`)

**Dependency:**
```gradle
dependencies {
    implementation 'io.github.mkpaz:atlantafx-base:2.0.1'
}
```

**Application Setup:**
```java
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.PrimerDark;

public class GUI extends Application {
    @Override
    public void start(Stage stage) {
        // Apply theme
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Toggle dark mode
        boolean isDarkMode = true;  // From settings
        if (isDarkMode) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        }
    }
}
```

### 7.2 Chart Color Customization

**AtlantaFX provides 8 chart colors:**
```css
/* Example from Primer theme */
--color-chart-1: #0969da;  /* Blue */
--color-chart-2: #1a7f37;  /* Green */
--color-chart-3: #cf222e;  /* Red */
--color-chart-4: #8250df;  /* Purple */
--color-chart-5: #fb8500;  /* Orange */
--color-chart-6: #023047;  /* Dark Blue */
--color-chart-7: #ffb703;  /* Yellow */
--color-chart-8: #e63946;  /* Crimson */
```

**Custom CSS for Charts:**
```css
/* Custom chart styling */
.chart {
    -fx-background-color: -color-bg-default;
}

.chart-pie-label {
    -fx-fill: -color-fg-default;
    -fx-font-size: 12px;
}

.default-color0.chart-pie { -fx-pie-color: -color-chart-1; }
.default-color1.chart-pie { -fx-pie-color: -color-chart-2; }
.default-color2.chart-pie { -fx-pie-color: -color-chart-3; }

.chart-bar {
    -fx-bar-fill: -color-chart-1;
}

.chart-bar:hover {
    -fx-opacity: 0.8;
}
```

### 7.3 Dark Mode Support

**Theme Switching:**
```java
ComboBox<String> themeSelector = new ComboBox<>();
themeSelector.getItems().addAll("Light", "Dark");
themeSelector.setValue("Light");
themeSelector.setOnAction(e -> {
    String theme = themeSelector.getValue();
    if (theme.equals("Dark")) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    } else {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }
});
```

**Persistence:**
```groovy
// Save to preferences
Preferences prefs = Preferences.userNodeForPackage(GUI.class)
prefs.put("theme", "Dark")

// Load on startup
String savedTheme = prefs.get("theme", "Light")
applyTheme(savedTheme)
```

---

## 8. Implementation Roadmap

### Phase 1: MVP (Minimum Viable Product)

**Goal:** Basic dashboard with essential visualizations

**Features:**
1. **KPI Cards Row:**
   - Total Books, Total Signs, Total Items, Total Blocks
   - Simple Label/Text components with large font

2. **3 Primary Charts:**
   - Books by Author (BarChart, top 20)
   - Books by Generation (PieChart)
   - Items by Type (PieChart, top 10)

3. **Performance Metrics:**
   - Extraction time, Files processed, Speed (files/sec)
   - Display in bottom status bar

4. **Basic Layout:**
   - Fixed GridPane with 2-column layout
   - No responsive resizing yet

5. **Theming:**
   - AtlantaFX Primer Light theme only
   - No dark mode toggle

**Estimated Effort:** 2-3 days

### Phase 2: Enhanced Interactivity

**Features:**
1. **Click-Through Navigation:**
   - Click chart element → filter main viewer
   - Breadcrumb navigation

2. **Tooltips:**
   - All charts show detailed tooltips on hover

3. **Additional Charts:**
   - Blocks by Dimension (StackedBarChart)
   - Signs Text Length Distribution (BarChart)
   - Portal Size Distribution (BarChart)

4. **Export:**
   - Export dashboard as PNG image
   - Export metrics as CSV

**Estimated Effort:** 3-4 days

### Phase 3: Advanced Features

**Features:**
1. **Session History:**
   - SQLite database for past extractions
   - Comparison mode (before/after view)
   - Side-by-side charts

2. **Responsive Layout:**
   - Window size breakpoints
   - Dynamic column count

3. **Dark Mode:**
   - Theme switcher (Light/Dark)
   - Preference persistence

4. **Drill-Down Views:**
   - Click "Others" → show full paginated list
   - Search/filter within drill-down

5. **HTML Report Generation:**
   - Export as print-friendly HTML with embedded charts

**Estimated Effort:** 5-7 days

### Phase 4: Polish & Optional Enhancements

**Features:**
1. **Word Cloud (Optional):**
   - Custom implementation or Kumo library integration
   - Sign text word frequency visualization

2. **Customizable Layout:**
   - Save/load dashboard layout preferences
   - Drag-and-drop reordering

3. **Animated Transitions:**
   - Chart data updates with smooth animation
   - Page transitions

4. **PDF Export:**
   - iText library integration
   - Professional report layout

**Estimated Effort:** 4-6 days (deprioritize if time-constrained)

---

## 9. Technical Architecture

### 9.1 Data Model

**DashboardMetrics Class:**
```groovy
@groovy.transform.Canonical
class DashboardMetrics {
    // Session metadata
    String sessionId
    Instant timestamp
    String worldName
    String worldPath

    // Book metrics
    int booksTotal
    int booksDuplicates
    Map<String, Integer> booksByAuthor
    Map<String, Integer> booksByGeneration  // ORIGINAL, COPY_OF_ORIGINAL, etc.
    Map<String, Integer> booksByPageCount   // Ranges: 1-10, 11-20, etc.
    Map<String, Integer> booksByContainer

    // Sign metrics
    int signsTotal
    int signsDuplicates
    Map<String, Integer> signsByTextLength
    List<String> commonPhrases

    // Item metrics
    int itemsCustomNamed
    Map<String, Integer> itemsByType
    Map<String, Integer> itemsByDimension

    // Block metrics
    int blocksIndexed
    Map<String, Integer> blocksByType
    Map<String, Integer> blocksByDimension

    // Portal metrics
    int portalsTotal
    Map<String, Integer> portalsBySizeRange
    Map<String, Integer> portalsByDimension

    // Performance metrics
    long extractionDurationMs
    int regionFilesProcessed
    int entityFilesProcessed
    int playerdataFilesProcessed
    int errorsCount
    int warningsCount
    double filesPerSecond

    // Computed properties
    double getBooksDuplicatePercent() {
        booksTotal > 0 ? (booksDuplicates * 100.0 / booksTotal) : 0
    }
}
```

### 9.2 Metrics Collection

**During Extraction:**
```groovy
class Main {
    static DashboardMetrics currentMetrics = new DashboardMetrics()

    static void runExtraction() {
        currentMetrics.timestamp = Instant.now()
        currentMetrics.worldName = worldDir.name
        currentMetrics.worldPath = worldDir.absolutePath

        long startTime = System.currentTimeMillis()

        // Existing extraction logic...
        readPlayerData()
        readSignsAndBooks()
        readEntities()

        long endTime = System.currentTimeMillis()
        currentMetrics.extractionDurationMs = endTime - startTime

        // Calculate derived metrics
        calculateMetrics()

        // Notify GUI to update dashboard
        if (GUI.isRunning()) {
            Platform.runLater(() -> GUI.updateDashboard(currentMetrics))
        }
    }

    static void calculateMetrics() {
        // Books by author
        currentMetrics.booksByAuthor = [:]
        allBooks.each { book ->
            String author = book.author ?: "Unknown"
            currentMetrics.booksByAuthor[author] =
                currentMetrics.booksByAuthor.getOrDefault(author, 0) + 1
        }

        // Books by page count ranges
        currentMetrics.booksByPageCount = [
            "1-10": 0, "11-20": 0, "21-30": 0, "31-50": 0, "51+": 0
        ]
        allBooks.each { book ->
            int pages = book.pages.size()
            String range = pages <= 10 ? "1-10" :
                          pages <= 20 ? "11-20" :
                          pages <= 30 ? "21-30" :
                          pages <= 50 ? "31-50" : "51+"
            currentMetrics.booksByPageCount[range]++
        }

        // ... more calculations
    }
}
```

### 9.3 GUI Integration

**Dashboard Tab:**
```java
Tab dashboardTab = new Tab("Dashboard");
dashboardTab.setClosable(false);

ScrollPane scrollPane = new ScrollPane();
scrollPane.setFitToWidth(true);

VBox dashboardContent = createDashboardLayout();
scrollPane.setContent(dashboardContent);
dashboardTab.setContent(scrollPane);

tabPane.getTabs().add(0, dashboardTab);  // First tab
```

**Dashboard Layout Creation:**
```java
VBox createDashboardLayout() {
    VBox layout = new VBox(15);
    layout.setPadding(new Insets(15));

    // KPI Cards Row
    HBox kpiRow = createKpiCards();

    // Charts Grid
    GridPane chartsGrid = createChartsGrid();

    // Performance Metrics
    VBox perfMetrics = createPerformanceMetrics();

    layout.getChildren().addAll(kpiRow, chartsGrid, perfMetrics);
    return layout;
}

HBox createKpiCards() {
    HBox row = new HBox(10);
    row.getChildren().addAll(
        createKpiCard("Total Books", String.valueOf(metrics.booksTotal), "📚"),
        createKpiCard("Total Signs", String.valueOf(metrics.signsTotal), "🪧"),
        createKpiCard("Custom Items", String.valueOf(metrics.itemsCustomNamed), "⚔️"),
        createKpiCard("Blocks Indexed", String.valueOf(metrics.blocksIndexed), "🧱")
    );
    return row;
}

VBox createKpiCard(String label, String value, String icon) {
    VBox card = new VBox(5);
    card.getStyleClass().add("kpi-card");
    card.setPadding(new Insets(15));
    card.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8;");

    Label iconLabel = new Label(icon);
    iconLabel.setStyle("-fx-font-size: 32;");

    Label valueLabel = new Label(value);
    valueLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold;");

    Label titleLabel = new Label(label);
    titleLabel.setStyle("-fx-font-size: 12; -fx-text-fill: -color-fg-muted;");

    card.getChildren().addAll(iconLabel, valueLabel, titleLabel);
    return card;
}
```

### 9.4 Chart Factory Methods

```java
PieChart createBooksByGenerationChart() {
    ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
    metrics.booksByGeneration.forEach((gen, count) -> {
        data.add(new PieChart.Data(gen, count));
    });

    PieChart chart = new PieChart(data);
    chart.setTitle("Books by Generation");
    chart.setLegendSide(Side.RIGHT);

    // Add tooltips
    data.forEach(slice -> {
        double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();
        double percent = slice.getPieValue() / total * 100;
        String tooltipText = String.format("%s: %d (%.1f%%)",
            slice.getName(), (int)slice.getPieValue(), percent);
        Tooltip.install(slice.getNode(), new Tooltip(tooltipText));

        // Click handler
        slice.getNode().setOnMouseClicked(e -> {
            filterBooksByGeneration(slice.getName());
        });
    });

    return chart;
}

BarChart<String, Number> createBooksByAuthorChart() {
    CategoryAxis xAxis = new CategoryAxis();
    xAxis.setLabel("Author");
    NumberAxis yAxis = new NumberAxis();
    yAxis.setLabel("Book Count");

    BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
    chart.setTitle("Books by Author (Top 20)");
    chart.setLegendVisible(false);

    XYChart.Series<String, Number> series = new XYChart.Series<>();

    // Get top 20 authors
    List<Map.Entry<String, Integer>> sortedAuthors = metrics.booksByAuthor.entrySet()
        .stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(20)
        .collect(Collectors.toList());

    sortedAuthors.forEach(entry -> {
        XYChart.Data<String, Number> dataPoint =
            new XYChart.Data<>(entry.getKey(), entry.getValue());
        series.getData().add(dataPoint);
    });

    chart.getData().add(series);

    // Add click handlers
    series.getData().forEach(data -> {
        data.getNode().setOnMouseClicked(e -> {
            filterBooksByAuthor(data.getXValue());
        });

        Tooltip tooltip = new Tooltip(
            data.getXValue() + ": " + data.getYValue() + " books"
        );
        Tooltip.install(data.getNode(), tooltip);
    });

    return chart;
}
```

---

## 10. UI Mockups

### 10.1 Dashboard Layout (Large Window)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ReadSignsAndBooks.jar - Dashboard                                   │
├─────────────────────────────────────────────────────────────────────┤
│ [Dashboard] [Books] [Signs] [Items] [Blocks] [Portals] [Settings]  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐                          │
│  │  📚   │ │  🪧   │ │  ⚔️   │ │  🧱   │                          │
│  │  150  │ │   75  │ │  320  │ │ 1,445 │                          │
│  │ Books │ │ Signs │ │ Items │ │Blocks │                          │
│  └───────┘ └───────┘ └───────┘ └───────┘                          │
│                                                                       │
│  ┌─────────────────────────┐ ┌───────────────┐                     │
│  │ Books by Author         │ │ Books by Gen  │                     │
│  │                         │ │               │                     │
│  │ Alice     ████████  42  │ │ Original 60%  │                     │
│  │ Bob       ██████  25    │ │ Copy 30%      │                     │
│  │ Carol     ████  15      │ │ Copy² 10%     │                     │
│  │ Dave      ███  12       │ │               │                     │
│  │ Others    ██████████ 56 │ │  [Pie Chart]  │                     │
│  │                         │ │               │                     │
│  └─────────────────────────┘ └───────────────┘                     │
│                                                                       │
│  ┌─────────────────────────┐ ┌───────────────┐                     │
│  │ Items by Type           │ │ Blocks by Dim │                     │
│  │                         │ │               │                     │
│  │  Swords 25%             │ │ Over  ████    │                     │
│  │  Tools 20%              │ │ Nether ██     │                     │
│  │  Armor 18%              │ │ End █         │                     │
│  │  Books 15%              │ │               │                     │
│  │  [Pie Chart]            │ │ [Bar Chart]   │                     │
│  │                         │ │               │                     │
│  └─────────────────────────┘ └───────────────┘                     │
│                                                                       │
│  ─────────────────────────────────────────────────────────────────  │
│  Performance Metrics                                                 │
│  ⏱️ Extraction Time: 00:12:34 │ 📊 Files: 1,445 │ ⚡ Speed: 1.9/s │
│  ✓ Success: 1,442 │ ⚠️ Warnings: 2 │ ✗ Errors: 1                  │
│                                                                       │
│  [Export as PNG] [Export CSV] [Compare Sessions] [View Error Log]   │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.2 KPI Card Detail

```
┌──────────────┐
│     📚       │   <- Icon (emoji or FontAwesome)
│    150       │   <- Large value (28pt bold)
│   Books      │   <- Label (12pt gray)
│              │
│  +12 (+8.7%) │   <- Change indicator (optional, Phase 3)
│  vs. last    │
└──────────────┘
```

### 10.3 Chart with Tooltip

```
Books by Author
┌─────────────────────────────┐
│                             │
│ Alice   ████████  42        │  <- Hover shows tooltip
│         ┌─────────────────┐ │
│         │ Alice: 42 books │ │
│         │ (28% of total)  │ │
│         └─────────────────┘ │
│ Bob     ██████  25          │
│ Carol   ████  15            │
│                             │
└─────────────────────────────┘
```

### 10.4 Comparison Mode (Phase 3)

```
┌─────────────────────────────────────────────────────────────┐
│ Session Comparison                                           │
│                                                              │
│ ┌──────────────────┬──────────────────┐                    │
│ │ Session 1        │ Session 2        │                    │
│ │ 2025-12-20 10:00 │ 2025-12-23 14:30 │                    │
│ ├──────────────────┼──────────────────┤                    │
│ │ Books: 150       │ Books: 95        │  📉 -55 (-36.7%)  │
│ │ Signs: 75        │ Signs: 82        │  📈 +7 (+9.3%)    │
│ │ Items: 320       │ Items: 410       │  📈 +90 (+28.1%)  │
│ └──────────────────┴──────────────────┘                    │
│                                                              │
│ Books by Author (Side-by-Side)                              │
│ ┌─────────────────┬─────────────────┐                      │
│ │   Before        │     After       │                      │
│ │ Alice  ████████ │ Alice  ████     │  -50%               │
│ │ Bob    ██████   │ Bob    █████    │  -17%               │
│ │ Carol  ███      │ Carol  ████     │  +33%               │
│ └─────────────────┴─────────────────┘                      │
│                                                              │
│ [Show Delta Chart] [Export Comparison CSV]                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. CSS Styling Recommendations

**Custom CSS File: `dashboard.css`**

```css
/* KPI Cards */
.kpi-card {
    -fx-background-color: -color-bg-subtle;
    -fx-background-radius: 8px;
    -fx-padding: 15;
    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);
}

.kpi-card:hover {
    -fx-background-color: -color-bg-default;
    -fx-cursor: hand;
}

.kpi-icon {
    -fx-font-size: 32px;
}

.kpi-value {
    -fx-font-size: 28px;
    -fx-font-weight: bold;
    -fx-text-fill: -color-fg-default;
}

.kpi-label {
    -fx-font-size: 12px;
    -fx-text-fill: -color-fg-muted;
}

/* Charts */
.chart {
    -fx-background-color: -color-bg-default;
    -fx-background-radius: 8px;
    -fx-padding: 10;
}

.chart-title {
    -fx-font-size: 16px;
    -fx-font-weight: bold;
    -fx-text-fill: -color-fg-default;
}

.chart-legend {
    -fx-background-color: -color-bg-subtle;
    -fx-background-radius: 5px;
}

/* Pie Chart */
.chart-pie {
    -fx-stroke: -color-bg-default;
    -fx-stroke-width: 2;
}

.chart-pie:hover {
    -fx-opacity: 0.8;
    -fx-cursor: hand;
}

.chart-pie-label {
    -fx-fill: -color-fg-default;
    -fx-font-size: 12px;
}

/* Bar Chart */
.chart-bar {
    -fx-bar-fill: -color-chart-1;
}

.chart-bar:hover {
    -fx-opacity: 0.8;
    -fx-cursor: hand;
}

.axis {
    -fx-tick-label-fill: -color-fg-muted;
}

.axis-label {
    -fx-text-fill: -color-fg-default;
    -fx-font-size: 14px;
}

/* Performance Metrics */
.metrics-box {
    -fx-background-color: -color-bg-subtle;
    -fx-background-radius: 8px;
    -fx-padding: 15;
}

.metric-label {
    -fx-font-size: 14px;
    -fx-text-fill: -color-fg-default;
}

.metric-value {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
}

/* Buttons */
.export-button {
    -fx-background-color: -color-accent-emphasis;
    -fx-text-fill: white;
    -fx-background-radius: 5px;
    -fx-padding: 10 20;
}

.export-button:hover {
    -fx-opacity: 0.9;
    -fx-cursor: hand;
}

/* Responsive breakpoints */
@media (max-width: 900px) {
    .dashboard-grid {
        -fx-hgap: 5;
        -fx-vgap: 5;
    }

    .kpi-card {
        -fx-padding: 10;
    }
}
```

---

## 12. Testing Strategy

### 12.1 Unit Tests

**Metrics Calculation:**
```groovy
class DashboardMetricsSpec extends Specification {
    def "should calculate books by author correctly"() {
        given:
        def metrics = new DashboardMetrics()
        def books = [
            [author: "Alice", pages: []],
            [author: "Alice", pages: []],
            [author: "Bob", pages: []]
        ]

        when:
        metrics.calculateBooksMetrics(books)

        then:
        metrics.booksByAuthor["Alice"] == 2
        metrics.booksByAuthor["Bob"] == 1
    }

    def "should group page counts into ranges"() {
        given:
        def metrics = new DashboardMetrics()
        def books = [
            [pages: (1..5)],      // 1-10 range
            [pages: (1..15)],     // 11-20 range
            [pages: (1..60)]      // 51+ range
        ]

        when:
        metrics.calculateBooksMetrics(books)

        then:
        metrics.booksByPageCount["1-10"] == 1
        metrics.booksByPageCount["11-20"] == 1
        metrics.booksByPageCount["51+"] == 1
    }
}
```

### 12.2 GUI Tests (TestFX)

**Dashboard Rendering:**
```java
public class DashboardTest extends ApplicationTest {
    @Override
    public void start(Stage stage) {
        GUI app = new GUI();
        app.start(stage);
    }

    @Test
    public void shouldShowDashboardTab() {
        clickOn("Dashboard");
        verifyThat("#dashboardContent", isVisible());
    }

    @Test
    public void shouldShowKpiCards() {
        clickOn("Dashboard");
        verifyThat(".kpi-card", NodeMatchers.hasChild());
    }

    @Test
    public void shouldFilterBooksOnChartClick() {
        clickOn("Dashboard");
        clickOn("#booksByAuthorChart .chart-bar");  // Click first bar
        verifyThat("Books", TabPane::isSelected);
        verifyThat("#filterField", hasText("Author: Alice"));
    }
}
```

### 12.3 Integration Tests

**Full Extraction → Dashboard Update:**
```groovy
def "should populate dashboard after extraction"() {
    given:
    def worldDir = new File("src/test/resources/1_21_10-44-3")

    when:
    Main.runExtraction(worldDir)

    then:
    Main.currentMetrics.booksTotal == 44
    Main.currentMetrics.signsTotal == 3
    Main.currentMetrics.extractionDurationMs > 0
    Main.currentMetrics.booksByAuthor.size() > 0
}
```

---

## 13. Performance Optimization

### 13.1 Data Aggregation

**Problem:** World with 5,000 unique authors → BarChart becomes unreadable

**Solution:** Aggregate top N, group rest as "Others"
```groovy
static Map<String, Integer> getTopAuthorsWithOthers(Map<String, Integer> allAuthors, int topN) {
    def sorted = allAuthors.entrySet()
        .sort { -it.value }
        .take(topN)

    def result = sorted.collectEntries { [it.key, it.value] }

    def othersCount = allAuthors.values().sum() - result.values().sum()
    if (othersCount > 0) {
        result["Others (${allAuthors.size() - topN})"] = othersCount
    }

    return result
}
```

### 13.2 Lazy Chart Rendering

**Problem:** 10 charts on dashboard → slow initial render

**Solution:** Render visible charts first, defer offscreen
```java
ScrollPane scrollPane = new ScrollPane(dashboardContent);
scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
    renderVisibleCharts(newVal);
});

void renderVisibleCharts(Bounds viewport) {
    dashboardCharts.forEach(chart -> {
        if (chart.getBoundsInParent().intersects(viewport)) {
            if (!chart.hasRendered) {
                chart.loadData();
                chart.hasRendered = true;
            }
        }
    });
}
```

### 13.3 Background Data Processing

**Problem:** Calculating metrics blocks GUI thread

**Solution:** Use `Task` for background computation
```java
Task<DashboardMetrics> calculateMetricsTask = new Task<>() {
    @Override
    protected DashboardMetrics call() {
        updateMessage("Calculating metrics...");
        DashboardMetrics metrics = new DashboardMetrics();

        updateProgress(0, 5);
        metrics.calculateBooksMetrics(allBooks);

        updateProgress(1, 5);
        metrics.calculateSignsMetrics(allSigns);

        updateProgress(2, 5);
        metrics.calculateItemsMetrics(allItems);

        // ... more calculations

        return metrics;
    }
};

calculateMetricsTask.setOnSucceeded(e -> {
    DashboardMetrics result = calculateMetricsTask.getValue();
    updateDashboard(result);
});

new Thread(calculateMetricsTask).start();
```

---

## 14. Accessibility Considerations

**Keyboard Navigation:**
```java
chart.setFocusTraversable(true);
chart.setOnKeyPressed(e -> {
    if (e.getCode() == KeyCode.ENTER) {
        // Trigger click action
        handleChartSelection();
    }
});
```

**Screen Reader Support:**
```java
chart.setAccessibleText("Books by author bar chart. Use arrow keys to navigate.");
```

**High Contrast Mode:**
```css
/* Ensure sufficient contrast in dark mode */
.chart-pie-label {
    -fx-fill: -color-fg-default;
    -fx-stroke: -color-bg-default;
    -fx-stroke-width: 1;
}
```

---

## 15. Summary & Recommendations

### Core Recommendations

1. **Use JavaFX Built-in Charts** - Sufficient for this use case, no third-party libraries needed
2. **Implement AtlantaFX Theming** - Modern look with minimal effort, dark mode support
3. **Aggregate Large Datasets** - Top 20 + "Others" pattern for performance
4. **Phased Implementation** - Start with MVP (KPI cards + 3 charts), iterate based on feedback
5. **Focus on Interactivity** - Click-through navigation adds significant value
6. **Export Functionality** - PNG export is low-hanging fruit, defer PDF to later

### MVP Priority List (Phase 1)

1. KPI Cards (Books, Signs, Items, Blocks)
2. Books by Author (BarChart)
3. Books by Generation (PieChart)
4. Items by Type (PieChart)
5. Performance Metrics (text summary)
6. AtlantaFX Primer Light theme

**Estimated MVP Effort:** 2-3 days

### Must-Have for v1.0

- All Phase 1 + Phase 2 features
- Click-through to filter main viewer
- Tooltips on all charts
- Export dashboard as PNG
- Export metrics as CSV
- Dark mode toggle

**Estimated Total Effort:** 5-7 days

### Nice-to-Have (Future Versions)

- Session history comparison
- Word cloud for signs
- Customizable dashboard layout
- HTML/PDF report generation
- Real-time metrics during extraction

---

## 16. References & Resources

**JavaFX Documentation:**
- JavaFX 21 API Docs: https://openjfx.io/javadoc/21/
- Charts Package: https://openjfx.io/javadoc/21/javafx.controls/javafx/scene/chart/package-summary
- PieChart Tutorial: https://o7planning.org/11105/javafx-piechart
- BarChart Tutorial: https://o7planning.org/11107/javafx-barchart
- JavaFX Layouts: https://docs.oracle.com/javafx/2/layout/builtin_layouts.htm

**Theming:**
- AtlantaFX GitHub: https://github.com/mkpaz/atlantafx
- AtlantaFX Docs: https://mkpaz.github.io/atlantafx/

**Visualization:**
- Snapshot to PNG: https://code.makery.ch/blog/javafx-2-snapshot-as-png-image/
- Dashboard Design Best Practices: https://www.springpeople.com/blog/power-bi-dashboard-design-best-practices-a-complete-guide/

**Performance:**
- JavaFX Performance Tips: https://stackoverflow.com/questions/76813277/how-to-create-responsive-desktop-applications-ui-in-javafx

---

**Document Version:** 1.0
**Last Updated:** 2025-12-23
**Author:** Claude Opus 4.5 (Research & Design)
**Status:** Ready for Implementation
