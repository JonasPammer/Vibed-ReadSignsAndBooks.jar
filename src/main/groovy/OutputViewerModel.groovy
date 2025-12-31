import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Data model for the Output Viewer.
 * Loads and holds all extracted data from the output folder.
 */
class OutputViewerModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputViewerModel)

    File outputFolder
    List<Map> books = []
    List<Map> signs = []
    List<Map> customNames = []
    List<Map> portals = []
    List<Map> blockResults = []
    ItemDatabase itemDatabase
    BlockDatabase blockDatabase
    Map<String, Object> metadata = [:]

    /**
     * Load all data from the output folder.
     * @param folder The output folder to load from
     * @return True if successfully loaded, false otherwise
     */
    boolean loadFromFolder(File folder) {
        if (!folder || !folder.exists() || !folder.isDirectory()) {
            LOGGER.error("Invalid output folder: ${folder?.absolutePath}")
            return false
        }

        // If user reloads a different folder, ensure we don't leak DB handles / stale data
        try {
            close()
        } catch (Exception ignored) {
            // Best effort cleanup
        }

        // Reset state for fresh load
        books = []
        signs = []
        customNames = []
        portals = []
        blockResults = []
        itemDatabase = null
        blockDatabase = null
        metadata = [:]

        this.outputFolder = folder
        LOGGER.info("Loading output data from: ${folder.absolutePath}")

        try {
            // Load books
            loadBooks()

            // Load signs
            loadSigns()

            // Load custom names
            loadCustomNames()

            // Load portals
            loadPortals()

            // Load block search results
            loadBlockResults()

            // Load item database
            loadItemDatabase()

            // Load block database
            loadBlockDatabase()

            // Build metadata summary
            buildMetadata()

            LOGGER.info("Successfully loaded output data")
            return true
        } catch (Exception e) {
            LOGGER.error("Failed to load output data: ${e.message}", e)
            return false
        }
    }

    private void loadBooks() {
        File booksFile = new File(outputFolder, 'all_books_stendhal.json')
        if (booksFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                def booksData = slurper.parse(booksFile)

                if (booksData instanceof Map && booksData.books instanceof List) {
                    books = booksData.books
                    LOGGER.debug("Loaded ${books.size()} books from ${booksFile.name}")
                } else {
                    LOGGER.warn("Unexpected JSON structure in ${booksFile.name}")
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load books: ${e.message}")
            }
        } else {
            LOGGER.debug("Books file not found: ${booksFile.name}")
        }
    }

    private void loadSigns() {
        File signsFile = new File(outputFolder, 'all_signs.csv')
        if (signsFile.exists()) {
            try {
                signs = parseSignsCsv(signsFile)
                LOGGER.debug("Loaded ${signs.size()} signs from ${signsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load signs: ${e.message}")
            }
        } else {
            LOGGER.debug("Signs file not found: ${signsFile.name}")
        }
    }

    private void loadCustomNames() {
        // Canonical extractor output is `all_custom_names.json`, but some older tests/docs used `custom_names.json`
        File customNamesFile = new File(outputFolder, 'all_custom_names.json')
        if (!customNamesFile.exists()) {
            customNamesFile = new File(outputFolder, 'custom_names.json')
        }

        if (customNamesFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                def customNamesData = slurper.parse(customNamesFile)

                if (customNamesData instanceof List) {
                    customNames = normalizeCustomNames(customNamesData as List<Map>)
                    LOGGER.debug("Loaded ${customNames.size()} custom names from ${customNamesFile.name}")
                } else {
                    LOGGER.warn("Unexpected JSON structure in ${customNamesFile.name}")
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load custom names: ${e.message}")
            }
        } else {
            LOGGER.debug('Custom names file not found: all_custom_names.json / custom_names.json')
        }
    }

    /**
     * Normalize custom name entries into a common schema used by GlobalSearch + OutputViewer:
     * - name (String)
     * - type (String)
     * - item_id (String)  (or entity/item id)
     * - x/y/z (int?) when present
     * - location (String) when present
     * - dimension (best-effort derived from location)
     *
     * Supports both:
     * - Legacy schema: {name,type,item_id,...}
     * - Current extractor schema: {customName,type,itemOrEntityId,location,...}
     */
    private static List<Map> normalizeCustomNames(List<Map> data) {
        if (!data) return []

        return data.collect { Map item ->
            if (!item) return null

            // Already in legacy viewer/search schema
            if (item.containsKey('name') || item.containsKey('item_id')) {
                return item
            }

            // Extractor schema
            String customName = item.customName ?: item.CustomName
            String type = item.type ?: item.Type
            String itemOrEntityId = item.itemOrEntityId ?: item.itemOrEntityID ?: item.ItemOrEntityID
            String location = (item.location ?: item.Location ?: '').toString()

            Integer x = safeParseInt(item.x)
            Integer y = safeParseInt(item.y)
            Integer z = safeParseInt(item.z)

            String dimension = location ? detectDimension(location) : null

            return [
                // Preferred keys for GlobalSearch
                name: customName,
                type: (type ?: '').toString().toLowerCase(),
                item_id: itemOrEntityId,
                x: x,
                y: y,
                z: z,
                location: location,
                dimension: dimension,

                // Preserve original keys too
                customName: customName,
                itemOrEntityId: itemOrEntityId
            ]
        }.findAll { it != null }
    }

    private void loadPortals() {
        File portalsFile = new File(outputFolder, 'portals.json')
        if (portalsFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                Object parsed = slurper.parse(portalsFile)

                // Support both formats:
                // - Current extraction output: { "portals": [...], "summary": {...} }
                // - Older test/demo format:   [ {...}, {...} ]
                List portalList = null
                if (parsed instanceof Map && (parsed as Map).portals instanceof List) {
                    portalList = (parsed as Map).portals as List
                } else if (parsed instanceof List) {
                    portalList = parsed as List
                }

                if (portalList == null) {
                    LOGGER.warn("Unexpected JSON structure in ${portalsFile.name}")
                    portals = []
                    return
                }

                portals = normalizePortals(portalList)
                LOGGER.debug("Loaded ${portals.size()} portals from ${portalsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load portals: ${e.message}")
            }
        } else {
            LOGGER.debug("Portals file not found: ${portalsFile.name}")
        }
    }

    private void loadBlockResults() {
        // Main.groovy writes blocks.json, support both for backwards compatibility
        File blockResultsFile = new File(outputFolder, 'blocks.json')
        if (!blockResultsFile.exists()) {
            blockResultsFile = new File(outputFolder, 'block_results.json')
        }
        if (blockResultsFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                def parsed = slurper.parse(blockResultsFile)
                // blocks.json has structure { "blocks": [...], "summary": {...} }
                // Extract the blocks array from the root object
                if (parsed instanceof Map && parsed.containsKey('blocks')) {
                    blockResults = parsed.blocks as List
                } else if (parsed instanceof List) {
                    // Legacy format: just a list of blocks
                    blockResults = parsed
                } else {
                    LOGGER.warn("Unexpected block results JSON structure, expected Map with 'blocks' key or List")
                    blockResults = []
                }
                LOGGER.debug("Loaded ${blockResults.size()} block results from ${blockResultsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load block results: ${e.message}")
            }
        } else {
            LOGGER.debug("Block results file not found: blocks.json / block_results.json")
        }
    }

    private void loadItemDatabase() {
        // Support both legacy filenames and the current extraction output name
        File itemsDbFile = new File(outputFolder, 'item_index.db')
        if (!itemsDbFile.exists()) {
            itemsDbFile = new File(outputFolder, 'items.db')
        }

        if (itemsDbFile.exists()) {
            try {
                itemDatabase = ItemDatabase.openForQuery(itemsDbFile)
                if (itemDatabase) {
                    LOGGER.debug("Opened item database: ${itemsDbFile.name}")
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to open item database: ${e.message}")
            }
        } else {
            LOGGER.debug('Item database not found: item_index.db / items.db')
        }
    }

    private void loadBlockDatabase() {
        // Support both legacy filenames and the current extraction output name
        File blocksDbFile = new File(outputFolder, 'block_index.db')
        if (!blocksDbFile.exists()) {
            blocksDbFile = new File(outputFolder, 'blocks.db')
        }

        if (blocksDbFile.exists()) {
            try {
                blockDatabase = BlockDatabase.openForQuery(blocksDbFile)
                if (blockDatabase) {
                    LOGGER.debug("Opened block database: ${blocksDbFile.name}")
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to open block database: ${e.message}")
            }
        } else {
            LOGGER.debug('Block database not found: block_index.db / blocks.db')
        }
    }

    private void buildMetadata() {
        metadata.booksCount = books.size()
        metadata.signsCount = signs.size()
        metadata.customNamesCount = customNames.size()
        metadata.portalsCount = portals.size()
        metadata.blockResultsCount = blockResults.size()

        if (itemDatabase) {
            metadata.itemTypesCount = itemDatabase.getItemTypeCount()
            metadata.totalItemsIndexed = itemDatabase.getTotalItemsIndexed()
            metadata.totalItemCount = itemDatabase.getTotalItemCount()
        } else {
            metadata.itemTypesCount = 0
            metadata.totalItemsIndexed = 0
            metadata.totalItemCount = 0
        }

        if (blockDatabase) {
            metadata.blockTypesCount = blockDatabase.getBlockTypeCount()
            metadata.totalBlocksIndexed = blockDatabase.getTotalBlocksIndexed()
        } else {
            metadata.blockTypesCount = 0
            metadata.totalBlocksIndexed = 0
        }
    }

    /**
     * Parse a CSV file into a list of maps.
     * @param file CSV file to parse
     * @return List of row maps
     */
    private List<Map> parseCsv(File file) {
        List<Map> results = []
        List<String> lines = file.readLines()

        if (lines.isEmpty()) {
            return results
        }

        // Parse header
        List<String> headers = parseCsvLine(lines[0])

        // Parse data rows
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = parseCsvLine(lines[i])
            if (values.size() == headers.size()) {
                Map row = [:]
                headers.eachWithIndex { header, idx ->
                    row[header] = values[idx]
                }
                results << row
            }
        }

        return results
    }

    /**
     * Parse the generated `all_signs.csv` into normalized sign maps that all viewers expect:
     * - x/y/z (ints)
     * - foundWhere, signText, line1-4 (strings)
     * - dimension (overworld/nether/end) best-effort
     * - woodType (oak/spruce/...) best-effort
     * - lines (List<String>) convenience field
     */
    private List<Map> parseSignsCsv(File file) {
        List<Map> rawRows = parseCsv(file)
        List<Map> normalized = []

        rawRows.each { Map row ->
            Map sign = normalizeSignRow(row)
            if (sign) {
                normalized << sign
            }
        }

        return normalized
    }

    private static Map normalizeSignRow(Map row) {
        if (!row) return null

        // Support both header styles and already-normalized rows
        Object xRaw = row.x ?: row.X ?: row['X']
        Object yRaw = row.y ?: row.Y ?: row['Y']
        Object zRaw = row.z ?: row.Z ?: row['Z']

        Integer x = safeParseInt(xRaw)
        Integer y = safeParseInt(yRaw)
        Integer z = safeParseInt(zRaw)

        // Some CSVs include explicit dimension column; if present, prefer it.
        String dimensionCol = (row.dimension ?: row.Dimension ?: row['dimension'] ?: row['Dimension'] ?: '').toString()

        String foundWhere = (row.foundWhere ?: row.FoundWhere ?: row['FoundWhere'] ?: '').toString()
        String signText = (row.signText ?: row.SignText ?: row['SignText'] ?: '').toString()
        String line1 = (row.line1 ?: row.Line1 ?: row['Line1'] ?: '').toString()
        String line2 = (row.line2 ?: row.Line2 ?: row['Line2'] ?: '').toString()
        String line3 = (row.line3 ?: row.Line3 ?: row['Line3'] ?: '').toString()
        String line4 = (row.line4 ?: row.Line4 ?: row['Line4'] ?: '').toString()

        // If signText is missing, derive from lines (keeps search behavior consistent)
        if (!signText) {
            signText = [line1, line2, line3, line4].findAll { it && !it.trim().isEmpty() }.join(' | ')
        }

        String dimension = dimensionCol ? normalizeDimension(dimensionCol) : detectDimension(foundWhere)
        String woodType = detectWoodType(foundWhere)

        return [
            x        : x,
            y        : y,
            z        : z,
            foundWhere: foundWhere,
            signText : signText,
            line1    : line1,
            line2    : line2,
            line3    : line3,
            line4    : line4,
            dimension: dimension,
            woodType : woodType,
            // Alias used by SignViewer
            blockType: woodType,
            lines    : [line1, line2, line3, line4]
        ]
    }

    private static String normalizeDimension(String dim) {
        String s = (dim ?: '').toLowerCase().trim()
        if (!s) return 'overworld'
        if (s.contains('overworld')) return 'overworld'
        if (s.contains('nether')) return 'nether'
        if (s.contains('end')) return 'end'
        if (s.contains('dim-1')) return 'nether'
        if (s.contains('dim1')) return 'end'
        return s
    }

    private static String detectDimension(String foundWhere) {
        String s = (foundWhere ?: '').toLowerCase()
        if (s.contains('dim-1') || s.contains('nether')) return 'nether'
        if (s.contains('dim1') || s.contains('end')) return 'end'
        return 'overworld'
    }

    private static String detectWoodType(String foundWhere) {
        String s = (foundWhere ?: '').toLowerCase()
        // Order matters: match more specific names first
        List<String> woodTypes = [
            'dark_oak', 'pale_oak',
            'mangrove', 'cherry',
            'crimson', 'warped',
            'spruce', 'birch', 'jungle', 'acacia',
            'bamboo',
            'oak'
        ]
        return woodTypes.find { s.contains(it) } ?: 'oak'
    }

    private static Integer safeParseInt(Object value, Integer defaultVal = null) {
        if (value == null) return defaultVal
        if (value instanceof Number) return ((Number) value).intValue()
        try {
            String s = value.toString().trim()
            if (!s) return defaultVal
            return Integer.parseInt(s)
        } catch (Exception ignored) {
            return defaultVal
        }
    }

    private static Double safeParseDouble(Object value, Double defaultVal = null) {
        if (value == null) return defaultVal
        if (value instanceof Number) return ((Number) value).doubleValue()
        try {
            String s = value.toString().trim()
            if (!s) return defaultVal
            return Double.parseDouble(s)
        } catch (Exception ignored) {
            return defaultVal
        }
    }

    /**
     * Normalize portals from JSON into a flat map form used by the GUI/search code.
     * Supports both the current nested structure (anchor/center/size maps) and older flat keys.
     */
    private static List<Map> normalizePortals(List portalList) {
        List<Map> normalized = []

        portalList.eachWithIndex { Object portalObj, int idx ->
            if (!(portalObj instanceof Map)) {
                return
            }

            Map portal = portalObj as Map

            String dimension = (portal.dimension ?: portal.Dimension ?: 'overworld').toString()
            String axis = (portal.axis ?: portal.Axis ?: '').toString()

            Map anchor = (portal.anchor instanceof Map) ? (portal.anchor as Map) : [:]
            Map center = (portal.center instanceof Map) ? (portal.center as Map) : [:]
            Map size = (portal.size instanceof Map) ? (portal.size as Map) : [:]

            Integer anchorX = safeParseInt(anchor.x ?: portal.anchor_x ?: portal.anchorX)
            Integer anchorY = safeParseInt(anchor.y ?: portal.anchor_y ?: portal.anchorY)
            Integer anchorZ = safeParseInt(anchor.z ?: portal.anchor_z ?: portal.anchorZ)

            Double centerX = safeParseDouble(center.x ?: portal.center_x ?: portal.centerX)
            Double centerY = safeParseDouble(center.y ?: portal.center_y ?: portal.centerY)
            Double centerZ = safeParseDouble(center.z ?: portal.center_z ?: portal.centerZ)

            Integer width = safeParseInt(size.width ?: portal.width)
            Integer height = safeParseInt(size.height ?: portal.height)
            Integer blockCount = safeParseInt(portal.block_count ?: portal.blockCount ?: portal.block_count)

            Object portalId = portal.portal_id ?: portal.id ?: (idx + 1)

            normalized << [
                portal_id : portalId,
                dimension : dimension,
                axis      : axis,
                width     : width,
                height    : height,
                block_count: blockCount,

                // Flat coordinate keys (used by GlobalSearchDemo and older UI code)
                anchor_x  : anchorX,
                anchor_y  : anchorY,
                anchor_z  : anchorZ,
                center_x  : centerX,
                center_y  : centerY,
                center_z  : centerZ,

                // CamelCase aliases (used by some viewer/export code)
                anchorX   : anchorX,
                anchorY   : anchorY,
                anchorZ   : anchorZ,
                centerX   : centerX,
                centerY   : centerY,
                centerZ   : centerZ,
                blockCount: blockCount,

                // Preserve nested structure too (matches extractor JSON)
                anchor    : [x: anchorX, y: anchorY, z: anchorZ],
                center    : [x: centerX, y: centerY, z: centerZ],
                size      : [width: width, height: height]
            ]
        }

        return normalized
    }

    /**
     * Parse a single CSV line respecting quoted fields.
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = []
        StringBuilder currentField = new StringBuilder()
        boolean inQuotes = false

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i)

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentField.append('"')
                    i++
                } else {
                    // Toggle quote mode
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                // Field separator
                fields << currentField.toString()
                currentField.setLength(0)
            } else {
                currentField.append(c)
            }
        }

        // Add last field
        fields << currentField.toString()
        return fields
    }

    /**
     * Close database connections when done.
     */
    void close() {
        itemDatabase?.close()
        blockDatabase?.close()
    }

    /**
     * Get a summary string of loaded data.
     */
    String getSummaryText() {
        StringBuilder sb = new StringBuilder()
        sb.append("Loaded: ")

        List<String> parts = []
        if ((metadata.booksCount ?: 0) > 0) parts << "${metadata.booksCount} books"
        if ((metadata.signsCount ?: 0) > 0) parts << "${metadata.signsCount} signs"
        if ((metadata.customNamesCount ?: 0) > 0) parts << "${metadata.customNamesCount} custom names"
        if ((metadata.portalsCount ?: 0) > 0) parts << "${metadata.portalsCount} portals"
        if ((metadata.blockResultsCount ?: 0) > 0) parts << "${metadata.blockResultsCount} block results"
        if ((metadata.itemTypesCount ?: 0) > 0) parts << "${metadata.itemTypesCount} item types"
        if ((metadata.blockTypesCount ?: 0) > 0) parts << "${metadata.blockTypesCount} block types"

        if (parts.isEmpty()) {
            sb.append("No data")
        } else {
            sb.append(parts.join(', '))
        }

        return sb.toString()
    }
}
