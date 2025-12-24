package viewers

import groovy.json.JsonOutput
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ChoiceDialog
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DataFormat
import javafx.stage.FileChooser
import javafx.stage.Window
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Export Manager for all data viewers in ReadSignsAndBooks.
 *
 * Supports exporting:
 * - Items (from ItemGridViewer/ItemDatabase)
 * - Books (from BookViewer)
 * - Signs (from SignViewer)
 * - Custom Names (from CustomNamesViewer)
 * - Blocks (from BlockGridViewer)
 * - Portals (from PortalViewer)
 *
 * Export formats:
 * - CSV: Standard comma-separated for spreadsheets
 * - JSON: Structured data for programmatic use
 * - HTML: Formatted report with styling for web viewing/printing
 * - CLIPBOARD: Quick copy with formatted text
 *
 * Export scopes:
 * - ALL: Export complete dataset
 * - FILTERED: Export currently filtered/visible results
 * - SELECTED: Export only selected items
 */
class ExportManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportManager)

    enum ExportFormat {
        CSV('CSV Files', '*.csv'),
        JSON('JSON Files', '*.json'),
        HTML('HTML Files', '*.html'),
        TEXT('Text Files', '*.txt'),
        CLIPBOARD('Clipboard', null)

        final String description
        final String extension

        ExportFormat(String description, String extension) {
            this.description = description
            this.extension = extension
        }
    }

    enum ExportScope {
        ALL('All Data'),
        FILTERED('Filtered Results'),
        SELECTED('Selected Items Only')

        final String description

        ExportScope(String description) {
            this.description = description
        }
    }

    enum DataType {
        ITEMS, BOOKS, SIGNS, CUSTOM_NAMES, BLOCKS, PORTALS, STATS
    }

    // ==================== Public Export API ====================

    /**
     * Show export dialog with format and scope selection.
     * @param data The data to export (List<Map> typically)
     * @param dataType Type of data being exported
     * @param ownerWindow Parent window for file chooser
     * @param currentFilter Description of current filter state (e.g., "Showing 42 of 1000 items")
     */
    static void showExportDialog(List<?> data, DataType dataType, Window ownerWindow = null, String currentFilter = null) {
        if (!data || data.isEmpty()) {
            showAlert('No Data', 'There is no data to export.', Alert.AlertType.WARNING)
            return
        }

        // Ask for export format
        ChoiceDialog<ExportFormat> formatDialog = new ChoiceDialog<>(ExportFormat.CSV, ExportFormat.values() as List)
        formatDialog.title = 'Export Format'
        formatDialog.headerText = "Export ${dataType.name().toLowerCase().replace('_', ' ')}"
        formatDialog.contentText = 'Choose export format:'

        def formatResult = formatDialog.showAndWait()
        if (!formatResult.isPresent()) return

        ExportFormat format = formatResult.get()

        // Handle clipboard export immediately
        if (format == ExportFormat.CLIPBOARD) {
            exportToClipboard(data, dataType)
            showAlert('Exported', "Data copied to clipboard (${data.size()} items)", Alert.AlertType.INFORMATION)
            return
        }

        // Show file chooser
        FileChooser chooser = new FileChooser()
        chooser.title = "Export ${dataType.name().toLowerCase().replace('_', ' ')}"
        chooser.initialFileName = generateDefaultFileName(dataType, format)

        chooser.extensionFilters.add(
            new FileChooser.ExtensionFilter(format.description, format.extension)
        )

        File file = chooser.showSaveDialog(ownerWindow)
        if (!file) return

        // Ensure correct extension
        if (!file.name.endsWith(format.extension.replace('*', ''))) {
            file = new File(file.absolutePath + format.extension.replace('*', ''))
        }

        // Perform export
        try {
            exportData(data, file, format, dataType)
            showAlert('Export Successful', "Exported ${data.size()} items to:\n${file.absolutePath}", Alert.AlertType.INFORMATION)
            LOGGER.info("Exported {} {} items to {}", data.size(), dataType, file.absolutePath)
        } catch (Exception e) {
            LOGGER.error("Export failed", e)
            showAlert('Export Failed', "Error: ${e.message}", Alert.AlertType.ERROR)
        }
    }

    /**
     * Export data to file with specified format.
     */
    static void exportData(List<?> data, File outputFile, ExportFormat format, DataType dataType) {
        switch (format) {
            case ExportFormat.CSV:
                exportToCsv(data, outputFile, dataType)
                break
            case ExportFormat.JSON:
                exportToJson(data, outputFile)
                break
            case ExportFormat.HTML:
                exportToHtml(data, outputFile, dataType)
                break
            case ExportFormat.TEXT:
                exportToText(data, outputFile, dataType)
                break
            default:
                throw new IllegalArgumentException("Unsupported format: $format")
        }
    }

    /**
     * Quick copy teleport command to clipboard.
     */
    static void copyTpCommand(int x, int y, int z) {
        String command = "/tp @s $x $y $z"
        Clipboard clipboard = Clipboard.systemClipboard
        ClipboardContent content = new ClipboardContent()
        content.putString(command)
        clipboard.setContent(content)
        LOGGER.debug("Copied TP command: {}", command)
    }

    /**
     * Copy custom text to clipboard.
     */
    static void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.systemClipboard
        ClipboardContent content = new ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }

    // ==================== CSV Export ====================

    private static void exportToCsv(List<?> data, File file, DataType dataType) {
        file.withWriter('UTF-8') { writer ->
            switch (dataType) {
                case DataType.ITEMS:
                    exportItemsToCsv(data, writer)
                    break
                case DataType.BOOKS:
                    exportBooksToCsv(data, writer)
                    break
                case DataType.SIGNS:
                    exportSignsToCsv(data, writer)
                    break
                case DataType.CUSTOM_NAMES:
                    exportCustomNamesToCsv(data, writer)
                    break
                case DataType.BLOCKS:
                    exportBlocksToCsv(data, writer)
                    break
                case DataType.PORTALS:
                    exportPortalsToCsv(data, writer)
                    break
            }
        }
    }

    private static void exportItemsToCsv(List<Map> items, Writer writer) {
        writer.writeLine('Item ID,Custom Name,Count,Enchantments,Dimension,X,Y,Z,Container Type,Container ID')
        items.each { item ->
            def enchants = formatEnchantments(item.enchantments)
            writer.writeLine([
                escapeCsv(item.itemId ?: ''),
                escapeCsv(item.customName ?: ''),
                item.count ?: 0,
                escapeCsv(enchants),
                escapeCsv(item.dimension ?: ''),
                item.x ?: 0,
                item.y ?: 0,
                item.z ?: 0,
                escapeCsv(item.containerType ?: ''),
                escapeCsv(item.containerId ?: '')
            ].join(','))
        }
    }

    private static void exportBooksToCsv(List<Map> books, Writer writer) {
        writer.writeLine('Title,Author,Page Count,Dimension,X,Y,Z,Container,Pages Preview')
        books.each { book ->
            def preview = book.pages ? truncate(book.pages[0]?.toString(), 100) : ''
            writer.writeLine([
                escapeCsv(book.title ?: 'Untitled'),
                escapeCsv(book.author ?: 'Unknown'),
                book.pages?.size() ?: 0,
                escapeCsv(book.dimension ?: ''),
                book.x ?: 0,
                book.y ?: 0,
                book.z ?: 0,
                escapeCsv(book.container ?: ''),
                escapeCsv(preview)
            ].join(','))
        }
    }

    private static void exportSignsToCsv(List<Map> signs, Writer writer) {
        writer.writeLine('Line 1,Line 2,Line 3,Line 4,Dimension,X,Y,Z,Block Type')
        signs.each { sign ->
            writer.writeLine([
                escapeCsv(sign.line1 ?: ''),
                escapeCsv(sign.line2 ?: ''),
                escapeCsv(sign.line3 ?: ''),
                escapeCsv(sign.line4 ?: ''),
                escapeCsv(sign.dimension ?: ''),
                sign.x ?: 0,
                sign.y ?: 0,
                sign.z ?: 0,
                escapeCsv(sign.blockType ?: 'oak_sign')
            ].join(','))
        }
    }

    private static void exportCustomNamesToCsv(List<Map> items, Writer writer) {
        writer.writeLine('Type,Custom Name,Item ID,Count,Dimension,X,Y,Z,Container')
        items.each { item ->
            writer.writeLine([
                escapeCsv(item.type ?: ''),
                escapeCsv(item.customName ?: ''),
                escapeCsv(item.itemId ?: ''),
                item.count ?: 0,
                escapeCsv(item.dimension ?: ''),
                item.x ?: 0,
                item.y ?: 0,
                item.z ?: 0,
                escapeCsv(item.container ?: '')
            ].join(','))
        }
    }

    private static void exportBlocksToCsv(List<Map> blocks, Writer writer) {
        writer.writeLine('Block Type,Count,Dimension,X,Y,Z')
        blocks.each { block ->
            writer.writeLine([
                escapeCsv(block.blockType ?: ''),
                block.count ?: 1,
                escapeCsv(block.dimension ?: ''),
                block.x ?: 0,
                block.y ?: 0,
                block.z ?: 0
            ].join(','))
        }
    }

    private static void exportPortalsToCsv(List<Map> portals, Writer writer) {
        writer.writeLine('Portal Type,Size,Dimension,Center X,Center Y,Center Z,Block Count')
        portals.each { portal ->
            writer.writeLine([
                escapeCsv(portal.type ?: 'nether_portal'),
                escapeCsv(portal.size ?: ''),
                escapeCsv(portal.dimension ?: ''),
                portal.centerX ?: 0,
                portal.centerY ?: 0,
                portal.centerZ ?: 0,
                portal.blockCount ?: 0
            ].join(','))
        }
    }

    // ==================== JSON Export ====================

    private static void exportToJson(List<?> data, File file) {
        def jsonData = [
            exportDate: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            totalItems: data.size(),
            items: data
        ]
        file.text = JsonOutput.prettyPrint(JsonOutput.toJson(jsonData))
    }

    // ==================== HTML Export ====================

    private static void exportToHtml(List<?> data, File file, DataType dataType) {
        String title = dataType.name().toLowerCase().replace('_', ' ').capitalize()
        String html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Exported ${title}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', 'Arial', sans-serif;
            margin: 20px;
            background: #f5f5f5;
            color: #333;
        }
        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 8px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        header h1 {
            font-size: 32px;
            margin-bottom: 10px;
        }
        header .meta {
            opacity: 0.9;
            font-size: 14px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        th {
            background: #4a4a4a;
            color: white;
            padding: 15px;
            text-align: left;
            font-weight: 600;
            text-transform: uppercase;
            font-size: 12px;
            letter-spacing: 0.5px;
        }
        td {
            padding: 12px 15px;
            border-bottom: 1px solid #e8e8e8;
        }
        tr:last-child td {
            border-bottom: none;
        }
        tr:hover {
            background: #f9f9f9;
        }
        .enchanted {
            color: #8040ff;
            font-weight: bold;
        }
        .named {
            color: #ffaa00;
            font-weight: bold;
        }
        .coords {
            font-family: 'Courier New', monospace;
            color: #555;
            font-size: 0.9em;
        }
        .dimension {
            display: inline-block;
            padding: 3px 8px;
            border-radius: 3px;
            font-size: 0.85em;
            font-weight: 500;
        }
        .dim-overworld { background: #90ee90; color: #1b5e20; }
        .dim-nether { background: #ff6b6b; color: #7f0000; }
        .dim-end { background: #d4af37; color: #5c4710; }
        .book-pages {
            max-width: 600px;
            white-space: pre-wrap;
            font-family: Georgia, serif;
            line-height: 1.6;
        }
        footer {
            text-align: center;
            margin-top: 40px;
            padding: 20px;
            color: #999;
            font-size: 13px;
        }
        @media print {
            body { background: white; }
            header { background: #333; }
            table { box-shadow: none; }
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>Minecraft ${title}</h1>
            <div class="meta">
                Exported on ${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))} |
                Total Items: ${data.size()}
            </div>
        </header>
"""

        html += generateHtmlTable(data, dataType)

        html += """
        <footer>
            Generated by ReadSignsAndBooks.jar | Data exported from Minecraft world save
        </footer>
    </div>
</body>
</html>
"""
        file.text = html
    }

    private static String generateHtmlTable(List<?> data, DataType dataType) {
        switch (dataType) {
            case DataType.ITEMS:
                return generateItemsHtmlTable(data)
            case DataType.BOOKS:
                return generateBooksHtmlTable(data)
            case DataType.SIGNS:
                return generateSignsHtmlTable(data)
            case DataType.CUSTOM_NAMES:
                return generateCustomNamesHtmlTable(data)
            case DataType.BLOCKS:
                return generateBlocksHtmlTable(data)
            case DataType.PORTALS:
                return generatePortalsHtmlTable(data)
            default:
                return '<p>Unsupported data type</p>'
        }
    }

    private static String generateItemsHtmlTable(List<Map> items) {
        def rows = items.collect { item ->
            def enchants = formatEnchantments(item.enchantments)
            def hasEnchants = enchants && !enchants.isEmpty()
            def hasName = item.customName
            """
        <tr>
            <td class="${hasName ? 'named' : ''}">${escapeHtml(item.itemId ?: '')}</td>
            <td class="${hasName ? 'named' : ''}">${hasName ? escapeHtml(item.customName) : '-'}</td>
            <td>${item.count ?: 0}</td>
            <td class="${hasEnchants ? 'enchanted' : ''}">${hasEnchants ? escapeHtml(enchants) : '-'}</td>
            <td>${formatDimensionBadge(item.dimension)}</td>
            <td class="coords">${item.x ?: 0}, ${item.y ?: 0}, ${item.z ?: 0}</td>
            <td>${escapeHtml(item.containerType ?: '-')}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Item</th>
                    <th>Custom Name</th>
                    <th>Count</th>
                    <th>Enchantments</th>
                    <th>Dimension</th>
                    <th>Location</th>
                    <th>Container</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    private static String generateBooksHtmlTable(List<Map> books) {
        def rows = books.collect { book ->
            """
        <tr>
            <td><strong>${escapeHtml(book.title ?: 'Untitled')}</strong></td>
            <td><em>${escapeHtml(book.author ?: 'Unknown')}</em></td>
            <td>${book.pages?.size() ?: 0}</td>
            <td>${formatDimensionBadge(book.dimension)}</td>
            <td class="coords">${book.x ?: 0}, ${book.y ?: 0}, ${book.z ?: 0}</td>
            <td>${escapeHtml(book.container ?: '-')}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Title</th>
                    <th>Author</th>
                    <th>Pages</th>
                    <th>Dimension</th>
                    <th>Location</th>
                    <th>Container</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    private static String generateSignsHtmlTable(List<Map> signs) {
        def rows = signs.collect { sign ->
            """
        <tr>
            <td>${escapeHtml(sign.line1 ?: '')}</td>
            <td>${escapeHtml(sign.line2 ?: '')}</td>
            <td>${escapeHtml(sign.line3 ?: '')}</td>
            <td>${escapeHtml(sign.line4 ?: '')}</td>
            <td>${formatDimensionBadge(sign.dimension)}</td>
            <td class="coords">${sign.x ?: 0}, ${sign.y ?: 0}, ${sign.z ?: 0}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Line 1</th>
                    <th>Line 2</th>
                    <th>Line 3</th>
                    <th>Line 4</th>
                    <th>Dimension</th>
                    <th>Location</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    private static String generateCustomNamesHtmlTable(List<Map> items) {
        def rows = items.collect { item ->
            """
        <tr>
            <td>${escapeHtml(item.type ?: '')}</td>
            <td class="named">${escapeHtml(item.customName ?: '')}</td>
            <td>${escapeHtml(item.itemId ?: '')}</td>
            <td>${item.count ?: 0}</td>
            <td>${formatDimensionBadge(item.dimension)}</td>
            <td class="coords">${item.x ?: 0}, ${item.y ?: 0}, ${item.z ?: 0}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Type</th>
                    <th>Custom Name</th>
                    <th>Item ID</th>
                    <th>Count</th>
                    <th>Dimension</th>
                    <th>Location</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    private static String generateBlocksHtmlTable(List<Map> blocks) {
        def rows = blocks.collect { block ->
            """
        <tr>
            <td>${escapeHtml(block.blockType ?: '')}</td>
            <td>${block.count ?: 1}</td>
            <td>${formatDimensionBadge(block.dimension)}</td>
            <td class="coords">${block.x ?: 0}, ${block.y ?: 0}, ${block.z ?: 0}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Block Type</th>
                    <th>Count</th>
                    <th>Dimension</th>
                    <th>Location</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    private static String generatePortalsHtmlTable(List<Map> portals) {
        def rows = portals.collect { portal ->
            """
        <tr>
            <td>${escapeHtml(portal.type ?: 'nether_portal')}</td>
            <td>${escapeHtml(portal.size ?: '-')}</td>
            <td>${formatDimensionBadge(portal.dimension)}</td>
            <td class="coords">${portal.centerX ?: 0}, ${portal.centerY ?: 0}, ${portal.centerZ ?: 0}</td>
            <td>${portal.blockCount ?: 0}</td>
        </tr>
"""
        }.join('')

        return """
        <table>
            <thead>
                <tr>
                    <th>Portal Type</th>
                    <th>Size</th>
                    <th>Dimension</th>
                    <th>Center Location</th>
                    <th>Blocks</th>
                </tr>
            </thead>
            <tbody>
$rows
            </tbody>
        </table>
"""
    }

    // ==================== TEXT Export ====================

    private static void exportToText(List<?> data, File file, DataType dataType) {
        file.withWriter('UTF-8') { writer ->
            writer.writeLine("=" * 80)
            writer.writeLine("ReadSignsAndBooks.jar Export")
            writer.writeLine("Data Type: ${dataType.name().toLowerCase().replace('_', ' ').capitalize()}")
            writer.writeLine("Export Date: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))}")
            writer.writeLine("Total Items: ${data.size()}")
            writer.writeLine("=" * 80)
            writer.writeLine()

            data.eachWithIndex { item, idx ->
                writer.writeLine("[${ (idx + 1).toString().padLeft(5, '0') }] ${formatItemAsText(item, dataType)}")
                writer.writeLine("-" * 80)
            }
        }
    }

    private static String formatItemAsText(Map item, DataType dataType) {
        switch (dataType) {
            case DataType.ITEMS:
                return """
  Item: ${item.itemId}
  Name: ${item.customName ?: '(none)'}
  Count: ${item.count}
  Enchantments: ${formatEnchantments(item.enchantments) ?: '(none)'}
  Location: ${item.x}, ${item.y}, ${item.z} (${item.dimension})
  Container: ${item.containerType ?: 'unknown'}
"""
            case DataType.BOOKS:
                return """
  Title: ${item.title ?: 'Untitled'}
  Author: ${item.author ?: 'Unknown'}
  Pages: ${item.pages?.size() ?: 0}
  Location: ${item.x}, ${item.y}, ${item.z} (${item.dimension})
"""
            case DataType.SIGNS:
                return """
  Line 1: ${item.line1 ?: ''}
  Line 2: ${item.line2 ?: ''}
  Line 3: ${item.line3 ?: ''}
  Line 4: ${item.line4 ?: ''}
  Location: ${item.x}, ${item.y}, ${item.z} (${item.dimension})
"""
            default:
                return item.toString()
        }
    }

    // ==================== CLIPBOARD Export ====================

    private static void exportToClipboard(List<?> data, DataType dataType) {
        StringBuilder text = new StringBuilder()
        text.append("${dataType.name()} Export (${data.size()} items)\n")
        text.append("=" * 60).append("\n\n")

        data.take(100).each { item ->  // Limit to 100 items for clipboard
            text.append(formatItemAsText(item, dataType))
            text.append("\n")
        }

        if (data.size() > 100) {
            text.append("\n... and ${data.size() - 100} more items (export to file for full data)")
        }

        copyToClipboard(text.toString())
    }

    // ==================== Utility Methods ====================

    private static String escapeCsv(String value) {
        if (!value) return ''
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return '"' + value.replace('"', '""') + '"'
        }
        return value
    }

    private static String escapeHtml(String value) {
        if (!value) return ''
        return value
            .replace('&', '&amp;')
            .replace('<', '&lt;')
            .replace('>', '&gt;')
            .replace('"', '&quot;')
            .replace("'", '&#39;')
    }

    private static String formatEnchantments(Map enchantments) {
        if (!enchantments) return ''
        return enchantments.collect { k, v -> "$k $v" }.join(', ')
    }

    private static String formatDimensionBadge(String dimension) {
        if (!dimension) dimension = 'overworld'
        def cssClass = "dim-${dimension.toLowerCase().replace('minecraft:', '').replace('the_', '')}"
        def displayName = dimension.replace('minecraft:', '').replace('_', ' ').capitalize()
        return "<span class=\"dimension $cssClass\">$displayName</span>"
    }

    private static String truncate(String text, int maxLength) {
        if (!text) return ''
        if (text.length() <= maxLength) return text
        return text.substring(0, maxLength - 3) + '...'
    }

    private static String generateDefaultFileName(DataType dataType, ExportFormat format) {
        def timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd_HHmmss'))
        def name = dataType.name().toLowerCase().replace('_', '-')
        def ext = format.extension.replace('*', '')
        return "${name}_${timestamp}${ext}"
    }

    private static void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    // ==================== Book-Specific Export ====================

    /**
     * Export full book content with all pages (HTML format for reading/printing).
     */
    static void exportBookWithPages(Map book, File outputFile) {
        def html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${escapeHtml(book.title ?: 'Minecraft Book')}</title>
    <style>
        body {
            font-family: Georgia, serif;
            max-width: 800px;
            margin: 40px auto;
            padding: 40px;
            background: #f5f5dc;
            line-height: 1.8;
        }
        .book-cover {
            background: linear-gradient(135deg, #8b4513 0%, #654321 100%);
            color: #f4e4c1;
            padding: 60px 40px;
            text-align: center;
            border: 4px solid #5c3317;
            margin-bottom: 40px;
            box-shadow: 0 8px 16px rgba(0,0,0,0.3);
        }
        .book-title {
            font-size: 42px;
            font-weight: bold;
            margin-bottom: 20px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.5);
        }
        .book-author {
            font-size: 24px;
            font-style: italic;
            opacity: 0.9;
        }
        .page {
            background: white;
            border: 2px solid #8b4513;
            padding: 30px;
            margin: 20px 0;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .page-number {
            text-align: center;
            color: #666;
            font-size: 14px;
            margin-top: 20px;
        }
        .page-content {
            white-space: pre-wrap;
            font-size: 16px;
        }
        @media print {
            body { background: white; }
            .page { page-break-after: always; }
        }
    </style>
</head>
<body>
    <div class="book-cover">
        <div class="book-title">${escapeHtml(book.title ?: 'Untitled')}</div>
        <div class="book-author">by ${escapeHtml(book.author ?: 'Unknown')}</div>
    </div>
"""

        book.pages?.eachWithIndex { page, idx ->
            html += """
    <div class="page">
        <div class="page-content">${escapeHtml(page.toString())}</div>
        <div class="page-number">Page ${idx + 1} of ${book.pages.size()}</div>
    </div>
"""
        }

        html += """
</body>
</html>
"""
        outputFile.text = html
    }
}
