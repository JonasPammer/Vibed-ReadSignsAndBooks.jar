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
                signs = parseCsv(signsFile)
                LOGGER.debug("Loaded ${signs.size()} signs from ${signsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load signs: ${e.message}")
            }
        } else {
            LOGGER.debug("Signs file not found: ${signsFile.name}")
        }
    }

    private void loadCustomNames() {
        File customNamesFile = new File(outputFolder, 'custom_names.json')
        if (customNamesFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                def customNamesData = slurper.parse(customNamesFile)

                if (customNamesData instanceof List) {
                    customNames = customNamesData
                    LOGGER.debug("Loaded ${customNames.size()} custom names from ${customNamesFile.name}")
                } else {
                    LOGGER.warn("Unexpected JSON structure in ${customNamesFile.name}")
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load custom names: ${e.message}")
            }
        } else {
            LOGGER.debug("Custom names file not found: ${customNamesFile.name}")
        }
    }

    private void loadPortals() {
        File portalsFile = new File(outputFolder, 'portals.json')
        if (portalsFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                portals = slurper.parse(portalsFile) as List
                LOGGER.debug("Loaded ${portals.size()} portals from ${portalsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load portals: ${e.message}")
            }
        } else {
            LOGGER.debug("Portals file not found: ${portalsFile.name}")
        }
    }

    private void loadBlockResults() {
        File blockResultsFile = new File(outputFolder, 'block_results.json')
        if (blockResultsFile.exists()) {
            try {
                JsonSlurper slurper = new JsonSlurper()
                blockResults = slurper.parse(blockResultsFile) as List
                LOGGER.debug("Loaded ${blockResults.size()} block results from ${blockResultsFile.name}")
            } catch (Exception e) {
                LOGGER.warn("Failed to load block results: ${e.message}")
            }
        } else {
            LOGGER.debug("Block results file not found: ${blockResultsFile.name}")
        }
    }

    private void loadItemDatabase() {
        File itemsDbFile = new File(outputFolder, 'items.db')
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
            LOGGER.debug("Item database not found: ${itemsDbFile.name}")
        }
    }

    private void loadBlockDatabase() {
        File blocksDbFile = new File(outputFolder, 'blocks.db')
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
            LOGGER.debug("Block database not found: ${blocksDbFile.name}")
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
        }

        if (blockDatabase) {
            metadata.blockTypesCount = blockDatabase.getBlockTypeCount()
            metadata.totalBlocksIndexed = blockDatabase.getTotalBlocksIndexed()
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
        if (metadata.booksCount > 0) parts << "${metadata.booksCount} books"
        if (metadata.signsCount > 0) parts << "${metadata.signsCount} signs"
        if (metadata.customNamesCount > 0) parts << "${metadata.customNamesCount} custom names"
        if (metadata.portalsCount > 0) parts << "${metadata.portalsCount} portals"
        if (metadata.blockResultsCount > 0) parts << "${metadata.blockResultsCount} block results"
        if (metadata.itemTypesCount > 0) parts << "${metadata.itemTypesCount} item types"
        if (metadata.blockTypesCount > 0) parts << "${metadata.blockTypesCount} block types"

        if (parts.isEmpty()) {
            sb.append("No data")
        } else {
            sb.append(parts.join(', '))
        }

        return sb.toString()
    }
}
