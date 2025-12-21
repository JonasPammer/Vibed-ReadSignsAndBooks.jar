/* groovylint-disable ClassSize */
import com.github.freva.asciitable.AsciiTable
import com.github.freva.asciitable.Column
import com.github.freva.asciitable.HorizontalAlign
import groovy.json.JsonSlurper
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import net.querz.mca.LoadFlags
import net.querz.mca.MCAUtil
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.NumberTag
import net.querz.nbt.tag.StringTag
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.FileAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import javafx.application.Application

import java.text.SimpleDateFormat

@Command(name = 'ReadSignsAndBooks',
        mixinStandardHelpOptions = true,
        description = 'Minecraft World Data Extractor - Extracts books and signs from Minecraft worlds',
        version = '1.0')
class Main implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main)
    private static final JsonSlurper JSON_SLURPER = new JsonSlurper()
    // Note: COLOR_CODES moved to TextUtils.groovy during refactoring

    static String baseDirectory = System.getProperty('user.dir')
    static String outputFolder, booksFolder, duplicatesFolder, dateStamp
    static String outputFolderParent  // Top-level output folder (e.g., "ReadBooks") without date stamp

    static Set<Integer> bookHashes = [] as Set
    static Set<String> signHashes = [] as Set
    static Set<String> customNameHashes = [] as Set  // Custom name deduplication
    static List<Map<String, Object>> customNameData = []  // Custom name extraction data

    // Generation tracking for original-prioritization
    // Maps pages.hashCode() -> [generation: Int, filePath: String] for swapping if more original found
    static Map<Integer, Map<String, Object>> bookGenerationByHash = [:]

    // Human-readable generation names
    private static final List<String> GENERATION_NAMES = ['Original', 'Copy of Original', 'Copy of Copy', 'Tattered']

    // Default dimensions for block/portal search
    private static final List<String> DEFAULT_DIMENSIONS = ['overworld', 'nether', 'end']

    // Minecraft command versions for book/sign generation
    private static final List<String> MC_VERSIONS = ['1_13', '1_14', '1_20_5', '1_21']

    // State file management for tracking failed region files
    static Map<String, Set<String>> failedRegionsByWorld = [:]  // worldFolderName -> Set of failed region filenames
    static Set<String> recoveredRegions = [] as Set  // Regions that recovered in this run

    static int bookCounter = 0
    static Map<String, Integer> booksByContainerType = [:]
    static Map<String, Integer> booksByLocationType = [:]
    static List<Map<String, String>> bookMetadataList = []
    static List<Map<String, Object>> bookCsvData = []
    static List<Map<String, Object>> signCsvData = []
    static int emptySignsRemoved = 0
    static BufferedWriter combinedBooksWriter
    static Map<String, BufferedWriter> mcfunctionWriters = [:]
    static Map<String, BufferedWriter> signsMcfunctionWriters = [:]  // Separate writers for sign mcfunction files
    static Map<String, Map<String, Object>> signsByHash = [:]  // Tracks unique signs by hash with position tracking
    static int signXCoordinate = 1  // Current X coordinate for sign placement

    static Map<String, List<Map<String, Object>>> booksByAuthor = [:]  // Tracks books by author for shulker generation
    // Note: SHULKER_COLORS moved to ShulkerBoxGenerator.groovy during refactoring

    @Option(names = ['-w', '--world'], description = 'Specify custom world directory')
    static String customWorldDirectory

    @Option(names = ['-o', '--output'], description = 'Specify custom output directory')
    static String customOutputDirectory

    @Option(names = ['--remove-formatting'], description = 'Remove Minecraft text formatting codes (§ codes) from output files (default: false)', defaultValue = 'false')
    static boolean removeFormatting = false

    @Option(names = ['--extract-custom-names'], description = 'Extract custom names from items and entities (default: false)', defaultValue = 'false')
    static boolean extractCustomNames = false

    @Option(names = ['--start'], description = 'GUI mode: auto-start extraction after 3 second countdown', defaultValue = 'false')
    static boolean autoStart = false

    @Option(names = ['-g', '--gui'], description = 'Launch GUI mode', defaultValue = 'false')
    static boolean guiMode = false

    @Option(names = ['--track-failed-regions'], description = 'Track and suppress errors for repeatedly failing region files (default: false)', defaultValue = 'false')
    static boolean trackFailedRegions = false

    @Option(names = ['--search-blocks'], description = 'Search for specific block types (comma-separated). If no types specified, indexes all blocks (rarity-filtered by --index-limit). Always skips air/cave_air.', split = ',', arity = '0..1')
    static List<String> searchBlocks = []

    // Flag to track if --search-blocks was explicitly specified (even with no args)
    static boolean searchBlocksSpecified = false

    @CommandLine.Spec
    static CommandLine.Model.CommandSpec commandSpec

    @Option(names = ['--find-portals'], description = 'Find all nether portals with intelligent clustering (outputs one entry per portal structure)', defaultValue = 'false')
    static boolean findPortals = false

    @Option(names = ['--search-dimensions'], description = 'Dimensions to search for blocks/portals (default: all). Options: overworld,nether,end', split = ',', defaultValue = 'overworld,nether,end')
    static List<String> searchDimensions = DEFAULT_DIMENSIONS

    @Option(names = ['--block-output-format'], description = 'Output format for block search (csv, json, txt)', defaultValue = 'csv')
    static String blockOutputFormat = 'csv'

    // Block index database options

    @Option(names = ['--index-limit'], description = 'Max blocks per type to index (default: 5000, 0 for unlimited)', defaultValue = '5000')
    static int indexLimit = 5000

    @Option(names = ['--index-query'], description = 'Query block coordinates from existing index (e.g., "nether_portal")')
    static String indexQuery = null

    @Option(names = ['--index-list'], description = 'List all indexed block types from existing database', defaultValue = 'false')
    static boolean indexList = false

    @Option(names = ['--index-dimension'], description = 'Filter query results by dimension (overworld, nether, end)')
    static String indexDimension = null

    // Item index database options

    @Option(names = ['--index-items'], description = 'Build item index database during extraction', defaultValue = 'false')
    static boolean indexItems = false

    @Option(names = ['--item-limit'], description = 'Max items per type to index (default: 1000, 0 for unlimited)', defaultValue = '1000')
    static int itemLimit = 1000

    @Option(names = ['--item-query'], description = 'Query items from existing index (e.g., "diamond_sword", "*" for all)')
    static String itemQuery = null

    @Option(names = ['--item-filter'], description = 'Filter item query results (enchanted, named, or enchantment name)')
    static String itemFilter = null

    @Option(names = ['--item-list'], description = 'List all indexed item types from existing database', defaultValue = 'false')
    static boolean itemList = false

    @Option(names = ['--skip-common-items'], description = 'Skip indexing common items like stone, dirt, cobblestone (default: true)', defaultValue = 'true')
    static boolean skipCommonItems = true

    // Block search results storage
    static List<BlockSearcher.BlockLocation> blockSearchResults = []
    static List<PortalDetector.Portal> portalResults = []
    static BlockDatabase blockDatabase = null  // Database instance for building index
    static ItemDatabase itemDatabase = null  // Database instance for building item index

    /**
     * Reset all static state for testing purposes.
     * This method clears all accumulated state from previous extraction runs,
     * allowing tests to run in isolation without state bleeding between tests.
     */
    static void resetState() {
        // Reset hash sets for deduplication
        bookHashes = [] as Set
        signHashes = [] as Set
        customNameHashes = [] as Set

        // Reset data collections
        customNameData = []
        bookGenerationByHash = [:]
        failedRegionsByWorld = [:]
        recoveredRegions = [] as Set

        // Reset counters
        bookCounter = 0
        emptySignsRemoved = 0
        signXCoordinate = 1

        // Reset maps and lists
        booksByContainerType = [:]
        booksByLocationType = [:]
        bookMetadataList = []
        bookCsvData = []
        signCsvData = []
        signsByHash = [:]
        booksByAuthor = [:]

        // Reset writers (they should be closed, but clear the references)
        mcfunctionWriters = [:]
        signsMcfunctionWriters = [:]
        combinedBooksWriter = null

        // Reset output paths (will be recomputed on next run)
        outputFolder = null
        booksFolder = null
        duplicatesFolder = null
        dateStamp = null
        outputFolderParent = null
        baseDirectory = System.getProperty('user.dir')

        // Reset command-line option fields to prevent test pollution
        customWorldDirectory = null
        customOutputDirectory = null
        removeFormatting = false
        extractCustomNames = false
        autoStart = false
        guiMode = false
        trackFailedRegions = false

        // Reset block search options and results
        searchBlocks = []
        searchBlocksSpecified = false
        commandSpec = null
        findPortals = false
        searchDimensions = DEFAULT_DIMENSIONS
        blockOutputFormat = 'csv'
        blockSearchResults = []
        portalResults = []

        // Reset block index database options
        indexLimit = 5000
        indexQuery = null
        indexList = false
        indexDimension = null
        blockDatabase?.close()
        blockDatabase = null

        // Reset item index database options
        indexItems = false
        itemLimit = 1000
        itemQuery = null
        itemFilter = null
        itemList = false
        skipCommonItems = true
        itemDatabase?.close()
        itemDatabase = null
    }

    static void main(String[] args) {
        // Smart detection: GUI mode if no args (double-clicked JAR) or --gui flag
        if (shouldUseGui(args)) {
            println 'Starting GUI mode...'
            Application.launch(GUI, args)
        } else {
            // CLI mode with picocli
            runCli(args)
        }
    }

    static void runCli(String[] args) {
        new CommandLine(new Main()).execute(args)
    }

    static boolean shouldUseGui(String[] args) {
        // No arguments? Assume GUI (double-clicked JAR)
        // Explicit GUI flag also triggers GUI mode
        return args.length == 0 || args.contains('--gui') || args.contains('-g')
    }

    @Override
    void run() {
        // Detect if --search-blocks was explicitly specified (even without arguments)
        detectSearchBlocksSpecified()

        try {
            // Check if this is a query-only mode (no extraction needed)
            if (indexQuery || indexList) {
                runBlockIndexQuery()
            } else if (itemQuery || itemList) {
                runItemIndexQuery()
            } else {
                runExtraction()
            }
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Error during extraction: ${e.message}", e)
            throw new RuntimeException('Extraction failed', e)
        }
    }

    /**
     * Detect if --search-blocks was explicitly specified.
     * Static method to avoid instance method assigning to static field.
     */
    private static void detectSearchBlocksSpecified() {
        // Check if --search-blocks option was parsed by picocli
        if (commandSpec != null) {
            searchBlocksSpecified = commandSpec.commandLine().parseResult?.hasMatchedOption('--search-blocks') ?: false
        }
    }

    /**
     * Run block index query mode - reads from existing database without extraction.
     * Can use -o (output directory) or -w (world directory) to find the database.
     */
    static void runBlockIndexQuery() {
        // For query mode, check -o first, then -w, then current directory
        baseDirectory = customOutputDirectory ?: customWorldDirectory ?: System.getProperty('user.dir')
        dateStamp = new SimpleDateFormat('yyyy-MM-dd', Locale.US).format(new Date())

        // Find the database file
        File dbFile = findBlockIndexDatabase()
        if (!dbFile || !dbFile.exists()) {
            LOGGER.error('Block index database not found. Run extraction with --search-blocks first.')
            LOGGER.info("Searched in: ${baseDirectory}")
            LOGGER.info('Use -o to specify the output folder containing block_index.db')
            return
        }

        LOGGER.info("Opening block index database: ${dbFile.absolutePath}")
        BlockDatabase db = BlockDatabase.openForQuery(dbFile)

        try {
            if (indexList) {
                printBlockIndexSummary(db)
            }

            if (indexQuery) {
                queryAndPrintBlocks(db, indexQuery, indexDimension)
            }
        } finally {
            db?.close()
        }
    }

    /**
     * Find the block index database file.
     * For query mode: searches in -o directory, -w directory, or current directory.
     * Supports pointing directly to the db file, to a date folder, or to a world/output folder.
     */
    static File findBlockIndexDatabase() {
        // Helper closure to search a directory for block_index.db
        Closure<File> searchDirectory = { String dirPath ->
            if (!dirPath) {
                return null
            }
            File dir = new File(dirPath)

            // Check if path points directly to the db file
            if (dir.name == 'block_index.db' && dir.exists()) {
                return dir
            }

            // Check if path is a folder containing block_index.db directly
            File dbFile = new File(dir, 'block_index.db')
            if (dbFile.exists()) {
                return dbFile
            }

            // Check in ReadBooks subfolder
            File readBooksDir = new File(dir, 'ReadBooks')
            if (readBooksDir.exists()) {
                File[] dateFolders = readBooksDir.listFiles()?.findAll { File f ->
                    f.directory && f.name.matches('\\d{4}-\\d{2}-\\d{2}')
                }?.sort { File f -> -f.lastModified() }

                if (dateFolders) {
                    for (File folder : dateFolders) {
                        dbFile = new File(folder, 'block_index.db')
                        if (dbFile.exists()) {
                            return dbFile
                        }
                    }
                }
                }

            // Check if path itself contains date folders (e.g., -o points to ReadBooks folder)
            File[] dateFolders = dir.listFiles()?.findAll { File f ->
                f.directory && f.name.matches('\\d{4}-\\d{2}-\\d{2}')
            }?.sort { File f -> -f.lastModified() }

            if (dateFolders) {
                for (File folder : dateFolders) {
                    dbFile = new File(folder, 'block_index.db')
                    if (dbFile.exists()) {
                        return dbFile
                    }
                }
            }

            return null
            }

        // Search in order: -o, -w, current directory
        File result = searchDirectory(customOutputDirectory)
        if (result) {
            return result
        }

        result = searchDirectory(customWorldDirectory)
        if (result) {
            return result
        }

        result = searchDirectory(System.getProperty('user.dir'))
        if (result) {
            return result
        }

        return null
            }

    /**
     * Get expected database path for error messages.
     */
    static String getExpectedDatabasePath() {
        return customOutputDirectory ?
            "${customOutputDirectory}${File.separator}block_index.db" :
            "${baseDirectory}${File.separator}ReadBooks${File.separator}YYYY-MM-DD${File.separator}block_index.db"
    }

    /**
     * Print block index summary (--index-list).
     */
    static void printBlockIndexSummary(BlockDatabase db) {
        List<Map> summary = db.summary

        if (summary.empty) {
            LOGGER.info('No blocks indexed in database.')
            return
        }

        LOGGER.info('')
        LOGGER.info('Block Index Summary')
        LOGGER.info('=' * 80)

        // Print as ASCII table
        List<Column> columns = [
            new Column().header('Block Type').dataAlign(HorizontalAlign.LEFT)
                .with({ Map row -> row.block_type?.toString() ?: '' } as java.util.function.Function),
            new Column().header('Total Found').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.total_found ?: 0) } as java.util.function.Function),
            new Column().header('Indexed').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.indexed_count ?: 0) } as java.util.function.Function),
            new Column().header('Limited').dataAlign(HorizontalAlign.CENTER)
                .with({ Map row -> row.limit_reached ? '✓' : '' } as java.util.function.Function)
        ]

        String table = AsciiTable.getTable(summary, columns)
        println table

        // Print metadata
        String worldPath = db.getMetadata('world_path')
        String extractionDate = db.getMetadata('extraction_date')
        String blockLimitMeta = db.getMetadata('block_limit')

        LOGGER.info('')
        LOGGER.info('Database Info:')
        if (worldPath) {
            LOGGER.info("  World: ${worldPath}")
        }
        if (extractionDate) {
            LOGGER.info("  Extracted: ${extractionDate}")
        }
        if (blockLimitMeta) {
            LOGGER.info("  Block limit: ${blockLimitMeta} per type")
        }
        LOGGER.info("  Total block types: ${summary.size()}")
        LOGGER.info("  Total blocks indexed: ${db.totalBlocksIndexed}")
    }

    /**
     * Query and print blocks by type (--query-blocks).
     */
    static void queryAndPrintBlocks(BlockDatabase db, String blockType, String dimension = null) {
        // Normalize block type
        String normalizedType = blockType.contains(':') ? blockType : "minecraft:${blockType}"

        List<Map> blocks = db.queryByBlockType(normalizedType, dimension)

        if (blocks.empty) {
            LOGGER.info("No blocks found for type: ${normalizedType}" + (dimension ? " in ${dimension}" : ''))
            return
        }

        LOGGER.info('')
        LOGGER.info("Query Results: ${normalizedType}" + (dimension ? " in ${dimension}" : ''))
        LOGGER.info('=' * 80)

        // Get summary info
        Map countInfo = db.getBlockCount(normalizedType)
        if (countInfo) {
            LOGGER.info("Total in world: ${String.format('%,d', countInfo.total_found)}")
            LOGGER.info("Indexed: ${String.format('%,d', countInfo.indexed_count)}" +
                       (countInfo.limit_reached ? ' (limit reached)' : ''))
        }

        LOGGER.info('')

        // Output as CSV to stdout
        println 'block_type,dimension,x,y,z,properties,region_file'
        blocks.each { Map block ->
            String props = block.properties ?: ''
            String region = block.region_file ?: ''
            println "${block.block_type},${block.dimension},${block.x},${block.y},${block.z},${props},${region}"
        }

        LOGGER.info('')
        LOGGER.info("Total results: ${blocks.size()}")
    }

    // ========== Item Index Query Methods ==========

    /**
     * Run item index query mode - reads from existing database without extraction.
     * Can use -o (output directory) or -w (world directory) to find the database.
     */
    static void runItemIndexQuery() {
        // For query mode, check -o first, then -w, then current directory
        baseDirectory = customOutputDirectory ?: customWorldDirectory ?: System.getProperty('user.dir')
        dateStamp = new SimpleDateFormat('yyyy-MM-dd', Locale.US).format(new Date())

        // Find the database file
        File dbFile = findItemIndexDatabase()
        if (!dbFile || !dbFile.exists()) {
            LOGGER.error('Item index database not found. Run extraction with --index-items first.')
            LOGGER.info("Searched in: ${baseDirectory}")
            LOGGER.info('Use -o to specify the output folder containing item_index.db')
            return
        }

        LOGGER.info("Opening item index database: ${dbFile.absolutePath}")
        ItemDatabase db = ItemDatabase.openForQuery(dbFile)

        try {
            if (itemList) {
                printItemIndexSummary(db)
            }

            if (itemQuery) {
                queryAndPrintItems(db, itemQuery, itemFilter, indexDimension)
            }
        } finally {
            db?.close()
        }
    }

    /**
     * Find the item index database file.
     * For query mode: searches in -o directory, -w directory, or current directory.
     * Supports pointing directly to the db file, to a date folder, or to a world/output folder.
     */
    static File findItemIndexDatabase() {
        // Helper closure to search a directory for item_index.db
        Closure<File> searchDirectory = { String dirPath ->
            if (!dirPath) {
                return null
            }
            File dir = new File(dirPath)

            // Check if path points directly to the db file
            if (dir.name == 'item_index.db' && dir.exists()) {
                return dir
            }

            // Check if path is a folder containing item_index.db directly
            File dbFile = new File(dir, 'item_index.db')
            if (dbFile.exists()) {
                return dbFile
            }

            // Check in ReadBooks subfolder
            File readBooksDir = new File(dir, 'ReadBooks')
            if (readBooksDir.exists()) {
                File[] dateFolders = readBooksDir.listFiles()?.findAll { File f ->
                    f.directory && f.name.matches('\\d{4}-\\d{2}-\\d{2}')
                }?.sort { File f -> -f.lastModified() }

                if (dateFolders) {
                    for (File folder : dateFolders) {
                        dbFile = new File(folder, 'item_index.db')
                        if (dbFile.exists()) {
                            return dbFile
                        }
                    }
                }
                }

            // Check if path itself contains date folders (e.g., -o points to ReadBooks folder)
            File[] dateFolders = dir.listFiles()?.findAll { File f ->
                f.directory && f.name.matches('\\d{4}-\\d{2}-\\d{2}')
            }?.sort { File f -> -f.lastModified() }

            if (dateFolders) {
                for (File folder : dateFolders) {
                    dbFile = new File(folder, 'item_index.db')
                    if (dbFile.exists()) {
                        return dbFile
                    }
                }
            }

            return null
            }

        // Search in order: -o, -w, current directory
        File result = searchDirectory(customOutputDirectory)
        if (result) {
            return result
        }

        result = searchDirectory(customWorldDirectory)
        if (result) {
            return result
        }

        result = searchDirectory(System.getProperty('user.dir'))
        if (result) {
            return result
        }

        return null
            }

    /**
     * Print item index summary (--item-list).
     */
    static void printItemIndexSummary(ItemDatabase db) {
        List<Map> summary = db.summary

        if (summary.empty) {
            LOGGER.info('No items indexed in database.')
            return
        }

        LOGGER.info('')
        LOGGER.info('Item Index Summary')
        LOGGER.info('=' * 80)

        // Print as ASCII table
        List<Column> columns = [
            new Column().header('Item Type').dataAlign(HorizontalAlign.LEFT)
                .with({ Map row -> row.item_id?.toString()?.replace('minecraft:', '') ?: '' } as java.util.function.Function),
            new Column().header('Total').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.total_count ?: 0) } as java.util.function.Function),
            new Column().header('Indexed').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.unique_locations ?: 0) } as java.util.function.Function),
            new Column().header('Enchanted').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.with_enchantments ?: 0) } as java.util.function.Function),
            new Column().header('Named').dataAlign(HorizontalAlign.RIGHT)
                .with({ Map row -> String.format('%,d', row.with_custom_name ?: 0) } as java.util.function.Function),
            new Column().header('Limited').dataAlign(HorizontalAlign.CENTER)
                .with({ Map row -> row.limit_reached ? '✓' : '' } as java.util.function.Function)
        ]

        String table = AsciiTable.getTable(summary, columns)
        println table

        // Print totals
        LOGGER.info('')
        LOGGER.info("Total item types: ${summary.size()}")
        LOGGER.info("Total items: ${String.format('%,d', db.totalItemCount)}")
        LOGGER.info("Indexed items: ${String.format('%,d', db.totalItemsIndexed)}")
    }

    /**
     * Query and print items (--item-query).
     *
     * @param db The item database
     * @param query Item type to query ("*" for all, or specific item ID)
     * @param filter Optional filter (enchanted, named, or enchantment name)
     * @param dimension Optional dimension filter
     */
    static void queryAndPrintItems(ItemDatabase db, String query, String filter = null, String dimension = null) {
        List<Map> items

        // Determine query type based on query and filter
        if (filter?.toLowerCase() == 'enchanted') {
            items = db.queryEnchantedItems(null, dimension)
        } else if (filter?.toLowerCase() == 'named') {
            items = db.queryNamedItems(null, dimension)
        } else if (filter && filter != 'enchanted' && filter != 'named') {
            // Filter is an enchantment name
            items = db.queryEnchantedItems(filter, dimension)
        } else if (query == '*') {
            // Query all items - get summary instead
            LOGGER.info('Use --item-list to see all item types. Showing items with enchantments or custom names...')
            items = db.queryEnchantedItems(null, dimension) + db.queryNamedItems(null, dimension)
            items = items.unique { Map item -> "${item.item_id}|${item.x}|${item.y}|${item.z}" }
        } else {
            // Query specific item type
            items = db.queryByItemType(query, dimension)
        }

        if (items.empty) {
            LOGGER.info('No items found' + (dimension ? " in ${dimension}" : ''))
            return
        }

        LOGGER.info('')
        String queryDesc = query == '*' ? 'All Special Items' : query.replace('minecraft:', '')
        LOGGER.info("Query Results: ${queryDesc}" + (filter ? " (filter: ${filter})" : '') + (dimension ? " in ${dimension}" : ''))
        LOGGER.info('=' * 80)
        LOGGER.info('')

        // Output as CSV to stdout
        println 'item_id,count,dimension,x,y,z,container_type,custom_name,enchantments,stored_enchantments,region_file'
        items.each { Map item ->
            String enchants = item.enchantments ?: ''
            String storedEnchants = item.stored_enchantments ?: ''
            String customName = item.custom_name ? "\"${item.custom_name}\"" : ''
            String region = item.region_file ?: ''
            println "${item.item_id},${item.count ?: 1},${item.dimension ?: ''},${item.x ?: ''},${item.y ?: ''},${item.z ?: ''},${item.container_type ?: ''},${customName},${enchants},${storedEnchants},${region}"
        }

        LOGGER.info('')
        LOGGER.info("Total results: ${items.size()}")
    }

    // ========== Shulker Box Generation (delegated to ShulkerBoxGenerator) ==========
    static String getShulkerColorForAuthor(String author) {
        return ShulkerBoxGenerator.getShulkerColorForAuthor(author)
    }

    /**
     * Load failed regions state from persistent state file
     * State file tracks region files that have repeatedly failed across multiple runs
     * Keyed by world folder name to support processing multiple worlds
     */
    static void loadFailedRegionsState() {
        String worldFolderName = new File(baseDirectory).name
        File stateFile = new File(baseDirectory, "${outputFolderParent}${File.separator}.failed_regions_state.json")

        failedRegionsByWorld.clear()
        recoveredRegions.clear()

        if (!stateFile.exists()) {
            LOGGER.debug("No existing state file found at: ${stateFile.absolutePath}")
            return
        }

        try {
            String content = stateFile.text
            Map<String, Object> stateData = new JsonSlurper().parseText(content) as Map<String, Object>

            if (stateData.containsKey(worldFolderName)) {
                List<String> failedRegions = stateData[worldFolderName] as List<String>
                failedRegionsByWorld[worldFolderName] = failedRegions.toSet()
                LOGGER.debug("Loaded ${failedRegions.size()} previously failed regions for world: ${worldFolderName}")
            }
        } catch (IOException | groovy.json.JsonException e) {
            // Catch all exceptions: JSON parsing, file I/O, type casting all possible
            LOGGER.warn("Failed to load state file ${stateFile.absolutePath}: ${e.message}")
        }
    }

    /**
     * Save failed regions state to persistent state file
     * Updates entries for the current world and removes regions that recovered
     */
    static void saveFailedRegionsState() {
        String worldFolderName = new File(baseDirectory).name
        File stateFileDir = new File(baseDirectory, outputFolderParent)
        stateFileDir.mkdirs()
        File stateFile = new File(stateFileDir, '.failed_regions_state.json')

        try {
            // Load existing state
            Map<String, Object> stateData = [:]
            if (stateFile.exists()) {
                try {
                    String content = stateFile.text
                    stateData = new JsonSlurper().parseText(content) as Map<String, Object>
                } catch (IOException | groovy.json.JsonException e) {
                    // Catch all exceptions: JSON parsing, file I/O, type casting all possible
                    LOGGER.warn("Could not parse existing state file, starting fresh: ${e.message}")
                    stateData = [:]
                }
            }

            // Update state for current world: add failed regions, remove recovered ones
            if (failedRegionsByWorld.containsKey(worldFolderName)) {
                Set<String> failedRegions = failedRegionsByWorld[worldFolderName]
                failedRegions.removeAll(recoveredRegions)  // Remove regions that recovered

                if (failedRegions.empty) {
                    stateData.remove(worldFolderName)
                    LOGGER.info("All previously failed regions recovered! Removing state for world: ${worldFolderName}")
                } else {
                    stateData[worldFolderName] = failedRegions.toList().sort()
                    LOGGER.debug("Saved ${failedRegions.size()} failed regions for world: ${worldFolderName}")
                }
            }

            // Write state file as JSON
            if (stateData.empty) {
                if (stateFile.exists()) {
                    stateFile.delete()
                }
            } else {
                stateFile.withWriter('UTF-8') { BufferedWriter writer ->
                    writer.write(new groovy.json.JsonOutput().toJson(stateData))
                }
            }
        } catch (IOException e) {
            // Catch all exceptions: file I/O, JSON serialization all possible
            LOGGER.warn("Failed to save state file: ${e.message}", e)
        }
    }

    // ========== Datapack Generation (delegated to DatapackGenerator) ==========
    static File setupDatapackStructure(String version) {
        return DatapackGenerator.setupDatapackStructure(baseDirectory, outputFolder, version)
    }

    static void writePackMcmeta(String version, int packFormat, String description) {
        DatapackGenerator.writePackMcmeta(baseDirectory, outputFolder, version, packFormat, description)
    }

    static int getPackFormat(String version) {
        return DatapackGenerator.getPackFormat(version)
    }

    static String getVersionDescription(String version) {
        return DatapackGenerator.getVersionDescription(version)
    }

    /**
     * Resolve the output base directory as a File.
     *
     * - Default: outputFolder is relative ("ReadBooks/YYYY-MM-DD") → baseDirectory/outputFolder
     * - Custom -o/--output: outputFolder is absolute → outputFolder
     *
     * IMPORTANT: do not build paths via string concatenation with baseDirectory, because on Windows an absolute
     * path like "C:\\out" concatenated becomes an invalid path like "<world>\\C:\\out".
     */
    static File resolveOutputBaseDir() {
        File out = new File(outputFolder)
        return out.absolute ? out : new File(baseDirectory, outputFolder)
    }

    /**
     * Resolve a path that may be absolute or relative to the world base directory.
     * This is used for derived folders like booksFolder/duplicatesFolder which may become absolute
     * when outputFolder is absolute (-o/--output).
     */
    static File resolveMaybeRelativeToWorld(String path) {
        File f = new File(path)
        return f.absolute ? f : new File(baseDirectory, path)
    }

    static void runExtraction() {
        // Reset collections but preserve @Option fields that were intentionally set
        // (e.g., customWorldDirectory set by tests or CLI arguments)
        [bookHashes, signHashes, customNameHashes, customNameData, booksByContainerType,
         booksByLocationType, bookMetadataList, bookCsvData, signCsvData, booksByAuthor,
         signsByHash, bookGenerationByHash].each { collection -> collection.clear() }
        bookCounter = 0
        emptySignsRemoved = 0
        signXCoordinate = 1  // Reset sign coordinate counter

        // Set directories
        baseDirectory = customWorldDirectory ?: System.getProperty('user.dir')
        dateStamp = new SimpleDateFormat('yyyy-MM-dd', Locale.US).format(new Date())
        outputFolder = customOutputDirectory ?: "ReadBooks${File.separator}${dateStamp}"

        // Extract parent folder for state file (top-level output folder without date stamp)
        if (customOutputDirectory) {
            // If custom directory given, extract parent folder
            File customFile = new File(customOutputDirectory)
            outputFolderParent = customFile.parentFile?.absolutePath ?: customOutputDirectory
        } else {
            // Default is ReadBooks
            outputFolderParent = 'ReadBooks'
        }

        booksFolder = "${outputFolder}${File.separator}books"
        duplicatesFolder = "${booksFolder}${File.separator}.duplicates"

        File outputBaseDir = resolveOutputBaseDir()

        // Configure logging - dynamically add file appender
        addFileAppender(new File(outputBaseDir, 'logs.txt').absolutePath)

        // Create directories
        [outputFolder, booksFolder, duplicatesFolder].each { String folder ->
            File f = new File(folder)
            if (!f.absolute) {
                f = new File(baseDirectory, folder)
            }
            f.mkdirs()
        }

        LOGGER.info('=' * 80)
        LOGGER.info('ReadSignsAndBooks - Minecraft World Data Extractor')
        LOGGER.info("Started at: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())}")
        LOGGER.info("World directory: ${baseDirectory}")
        LOGGER.info("Output folder: ${outputFolder}")
        LOGGER.info('=' * 80)

        // Load state file with previously failed regions (only if tracking enabled)
        if (trackFailedRegions) {
            loadFailedRegionsState()

            // Print message about suppressed regions if any known failures exist
            String worldFolderName = new File(baseDirectory).name
            if (failedRegionsByWorld.containsKey(worldFolderName)) {
                Set<String> failedRegions = failedRegionsByWorld[worldFolderName]
                LOGGER.info('')
                LOGGER.info("⚠️  NOTICE: ${failedRegions.size()} region file(s) have previously failed to read. Error messages for these known problematic regions will be suppressed in this run's output:")
                failedRegions.toList().sort().each { String regionFile ->
                    LOGGER.info("  - ${regionFile}")
                }
                LOGGER.info('')
            }
        }

        long startTime = System.currentTimeMillis()

        try {
            combinedBooksWriter = new File(outputBaseDir, 'all_books.txt').newWriter()

            // Create datapack structures and initialize mcfunction writers for each Minecraft version
            LOGGER.info('Creating Minecraft datapacks...')
            MC_VERSIONS.each { String version ->
                // Create datapack directory structure
                File functionFolder = setupDatapackStructure(version)

                // Create pack.mcmeta with appropriate pack_format
                int packFormat = getPackFormat(version)
                String description = "ReadSignsAndBooks extracted content for ${getVersionDescription(version)}"
                writePackMcmeta(version, packFormat, description)

                // Initialize book mcfunction writer in datapack/data/readbooks/function/books.mcfunction
                File booksFile = new File(functionFolder, 'books.mcfunction')
                mcfunctionWriters[version] = booksFile.newWriter('UTF-8')

                // Initialize signs mcfunction writer in datapack/data/readbooks/function/signs.mcfunction
                File signsFile = new File(functionFolder, 'signs.mcfunction')
                signsMcfunctionWriters[version] = signsFile.newWriter('UTF-8')

                LOGGER.info("  ✓ Created datapack for ${getVersionDescription(version)} (pack_format ${packFormat})")
            }
            LOGGER.info('Datapacks created successfully!')

            // Initialize item index database if enabled
            if (indexItems) {
                File dbFile = new File(outputBaseDir, 'item_index.db')
                LOGGER.info("Creating item index database: ${dbFile.absolutePath}")
                LOGGER.info("Item limit per type: ${itemLimit == 0 ? 'unlimited' : itemLimit}")
                LOGGER.info("Skip common items: ${skipCommonItems}")

                itemDatabase = new ItemDatabase(dbFile, itemLimit)
                itemDatabase.worldPath = baseDirectory
                itemDatabase.extractionDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())
                itemDatabase.setItemLimitMetadata()
                itemDatabase.beginTransaction()  // Use transaction for batch inserts
            }

            readPlayerData()
            readSignsAndBooks()
            readEntities()
            combinedBooksWriter?.close()

            // Generate shulker box commands organized by author
            writeShulkerBoxesToMcfunction()

            // Flush and close all mcfunction writers
            mcfunctionWriters.values().each { BufferedWriter writer ->
                writer?.flush()
                writer?.close()
            }
            // Flush and close all sign mcfunction writers
            signsMcfunctionWriters.values().each { BufferedWriter writer ->
                writer?.flush()
                writer?.close()
            }

            // Write CSV exports
            writeBooksCSV()
            writeSignsCSV()
            writeCustomNamesOutput()

            // Commit and close item index database if enabled
            if (itemDatabase) {
                itemDatabase.commitTransaction()
                LOGGER.info('')
                LOGGER.info('Item Index Summary:')
                LOGGER.info("  Item types indexed: ${itemDatabase.itemTypeCount}")
                LOGGER.info("  Unique items indexed: ${itemDatabase.totalItemsIndexed}")
                LOGGER.info("  Total items found: ${itemDatabase.totalItemCount}")
                itemDatabase.close()
                itemDatabase = null
            }

            // Block search and portal detection (if enabled)
            runBlockSearch()
            writeBlockSearchOutput()

            long elapsed = System.currentTimeMillis() - startTime
            printSummaryStatistics(elapsed)
            LOGGER.info("${elapsed / 1000} seconds to complete.")

            // Save state file with any new failures discovered (only if tracking enabled)
            if (trackFailedRegions) {
                saveFailedRegionsState()
            }
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Fatal error: ${e.message}", e)
            combinedBooksWriter?.close()
            mcfunctionWriters.values().each { BufferedWriter writer -> writer?.close() }
            // Rollback and close item database on error
            if (itemDatabase) {
                try {
                    itemDatabase.rollbackTransaction()
                } catch (java.sql.SQLException ignored) {
                // Ignore rollback errors - database may already be closed or in invalid state
                }
                itemDatabase.close()
                itemDatabase = null
            }
            // Still save state file even if extraction had errors (only if tracking enabled)
            if (trackFailedRegions) {
                saveFailedRegionsState()
            }
            throw e
        }
    }

    /**
     * Dynamically add a file appender to Logback at runtime
     * This allows us to avoid creating log files until extraction actually runs
     */
    static void addFileAppender(String logFilePath) {
        LoggerContext lc = (LoggerContext) LoggerFactory.ILoggerFactory

        // Create and configure the encoder
        PatternLayoutEncoder ple = new PatternLayoutEncoder()
        ple.pattern = '%d{HH:mm:ss.SSS} [%level] %msg%n'
        ple.context = lc
        ple.start()

        // Create and configure the file appender
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<ILoggingEvent>()
        fileAppender.file = logFilePath
        fileAppender.append = false
        fileAppender.encoder = ple
        fileAppender.context = lc
        fileAppender.start()

        // Add to root logger
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME)
        rootLogger.addAppender(fileAppender)
    }

    // ========== Output Writers (delegated to OutputWriters) ==========
    static void writeBooksCSV() {
        OutputWriters.writeBooksCSV(baseDirectory, outputFolder, bookCsvData)
    }

    static void writeSignsCSV() {
        OutputWriters.writeSignsCSV(baseDirectory, outputFolder, signCsvData)
    }

    static String escapeCsvField(String field) {
        return OutputWriters.escapeCsvField(field)
    }

    /**
     * Write custom names output files (CSV, TXT, JSON)
     */
    static void writeCustomNamesOutput() {
        LOGGER.debug("writeCustomNamesOutput() called: extractCustomNames=${extractCustomNames}, customNameData.size()=${customNameData.size()}")

        if (!extractCustomNames) {
            LOGGER.debug('Skipping custom names output: --extract-custom-names flag not set')
            return
        }

        if (customNameData.empty) {
            LOGGER.debug('Skipping custom names output: no custom names found')
            return
        }

        LOGGER.info("Writing custom names output (${customNameData.size()} unique custom names found)")

        File outputBaseDir = resolveOutputBaseDir()

        // Write CSV file
        File csvFile = new File(outputBaseDir, 'all_custom_names.csv')
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('CustomName,Type,ItemOrEntityID,X,Y,Z,Location')
            customNameData.each { Map<String, Object> data ->
                writer.writeLine([
                    escapeCsvField(data.customName as String),
                    escapeCsvField(data.type as String),
                    escapeCsvField(data.itemOrEntityId as String),
                    data.x ?: '',
                    data.y ?: '',
                    data.z ?: '',
                    escapeCsvField(data.location as String)
                ].join(','))
            }
        }

        // Write TXT file with grouped report
        File txtFile = new File(outputBaseDir, 'all_custom_names.txt')
        txtFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Custom Names Extraction Report')
            writer.writeLine('=' * 80)
            writer.writeLine('')

            // Group by type
            Map<String, List<Map<String, Object>>> groupedByType = customNameData.groupBy { Map<String, Object> data -> data.type as String }

            groupedByType.each { String type, List<Map<String, Object>> items ->
                writer.writeLine("${type.toUpperCase()}S (${items.size()}):")
                writer.writeLine('-' * 40)
                items.each { Map<String, Object> data ->
                    writer.writeLine("  Name: ${data.customName}")
                    writer.writeLine("  ID: ${data.itemOrEntityId}")
                    if (data.x != null && data.y != null && data.z != null) {
                        writer.writeLine("  Coordinates: (${data.x}, ${data.y}, ${data.z})")
                    }
                    writer.writeLine("  Location: ${data.location}")
                    writer.writeLine('')
                }
                writer.writeLine('')
            }
        }

        // Write JSON file
        File jsonFile = new File(outputBaseDir, 'all_custom_names.json')
        jsonFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.write('[')
            customNameData.eachWithIndex { Map<String, Object> data, int index ->
                if (index > 0) {
                    writer.write(',')
                }
                writer.writeLine('')
                writer.writeLine('  {')
                writer.writeLine("    \"type\": ${escapeJson(data.type as String)},")
                writer.writeLine("    \"itemOrEntityId\": ${escapeJson(data.itemOrEntityId as String)},")
                writer.writeLine("    \"customName\": ${escapeJson(data.customName as String)},")
                writer.writeLine("    \"x\": ${data.x ?: 'null'},")
                writer.writeLine("    \"y\": ${data.y ?: 'null'},")
                writer.writeLine("    \"z\": ${data.z ?: 'null'},")
                writer.writeLine("    \"location\": ${escapeJson(data.location as String)}")
                writer.write('  }')
            }
            writer.writeLine('')
            writer.writeLine(']')
        }

        LOGGER.info('Custom names written to: all_custom_names.csv, all_custom_names.txt, all_custom_names.json')
    }

    /**
     * Escape a string for JSON output
     */
    static String escapeJson(String text) {
        if (!text) {
            return '""'
        }
        String escaped = text
            .replace('\\', '\\\\')
            .replace('"', '\\"')
            .replace('\n', '\\n')
            .replace('\r', '\\r')
            .replace('\t', '\\t')
        return "\"${escaped}\""
    }

    // ========== Block Search and Portal Detection ==========

    /**
     * Run block search and/or portal detection if enabled via CLI flags.
     *
     * Supports two modes:
     * 1. Targeted search: --search-blocks obsidian,nether_portal (specific block types)
     * 2. Index-all mode: --search-blocks (no arguments) - indexes all blocks except air/cave_air,
     *    rarity-filtered by --index-limit
     */
    static void runBlockSearch() {
        boolean hasSpecificBlocks = searchBlocks && !searchBlocks.empty
        boolean hasIndexAllMode = searchBlocksSpecified && !hasSpecificBlocks
        boolean hasBlockSearch = hasSpecificBlocks || hasIndexAllMode
        boolean hasPortalSearch = findPortals

        if (!hasBlockSearch && !hasPortalSearch) {
            LOGGER.debug('Block search skipped: neither --search-blocks nor --find-portals specified')
            return
        }

        LOGGER.info('')
        LOGGER.info('=' * 80)
        LOGGER.info('BLOCK SEARCH')
        LOGGER.info('=' * 80)

        // Always create block index database when searching for blocks
        if (hasBlockSearch) {
            File dbFile = new File(resolveOutputBaseDir(), 'block_index.db')
            LOGGER.info("Creating block index database: ${dbFile.absolutePath}")
            LOGGER.info("Block limit per type: ${indexLimit == 0 ? 'unlimited' : indexLimit}")

            blockDatabase = new BlockDatabase(dbFile, indexLimit)
            blockDatabase.worldPath = baseDirectory
            blockDatabase.extractionDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())
            blockDatabase.setBlockLimitMetadata()
        }

        try {
            // Run portal detection if requested
            if (hasPortalSearch) {
                LOGGER.info("Finding nether portals in dimensions: ${searchDimensions.join(', ')}")
                portalResults = PortalDetector.findPortalsInWorld(baseDirectory, searchDimensions)
                LOGGER.info("Found ${portalResults.size()} portal structures")
            }

            // Run block search with database indexing
            if (hasBlockSearch) {
                if (hasIndexAllMode) {
                    // Index-all mode: scan all blocks except air/cave_air
                    LOGGER.info("INDEX-ALL MODE: Scanning all blocks (rarity-filtered by limit: ${indexLimit == 0 ? 'unlimited' : indexLimit})")
                    LOGGER.info("Dimensions: ${searchDimensions.join(', ')}")
                    LOGGER.info('Skipping: minecraft:air, minecraft:cave_air')

                    // Use specialized index-all method that streams directly to DB
                    BlockSearcher.indexAllBlocks(baseDirectory, searchDimensions, blockDatabase)
                    // Results stay in DB only - no in-memory list needed
                    blockSearchResults = []  // Empty - will stream from DB for output
                } else {
                    // Targeted search mode
                    Set<String> targetBlocks = BlockSearcher.parseBlockIds(searchBlocks.join(','))
                    LOGGER.info("Searching for blocks: ${targetBlocks.join(', ')}")
                    LOGGER.info("Dimensions: ${searchDimensions.join(', ')}")

                    blockSearchResults = BlockSearcher.searchBlocks(baseDirectory, targetBlocks, searchDimensions, blockDatabase)
                    LOGGER.info("Found ${blockSearchResults.size()} matching blocks")
                }
            }
        } finally {
            // Close database
            if (blockDatabase) {
                // Print summary
                LOGGER.info('')
                LOGGER.info('Block Index Summary:')
                LOGGER.info("  Block types indexed: ${blockDatabase.blockTypeCount}")
                LOGGER.info("  Total blocks indexed: ${blockDatabase.totalBlocksIndexed}")

                blockDatabase.close()
                blockDatabase = null
            }
        }
    }

    /**
     * Write block search and portal detection results to output files.
     *
     * Handles two modes:
     * 1. Targeted search: writes from blockSearchResults list
     * 2. Index-all mode: streams from block_index.db database
     */
    static void writeBlockSearchOutput() {
        boolean hasBlockResults = blockSearchResults && !blockSearchResults.empty
        boolean hasIndexAllMode = searchBlocksSpecified && (searchBlocks == null || searchBlocks.empty)
        boolean hasPortalResults = portalResults && !portalResults.empty

        if (!hasBlockResults && !hasIndexAllMode && !hasPortalResults) {
            LOGGER.debug('No block/portal search results to write')
            return
        }

        String outputPath = resolveOutputBaseDir().absolutePath

        // Write portal results
        if (hasPortalResults) {
            writePortalOutput(outputPath)
        }

        // Write block search results
        if (hasBlockResults) {
            // Targeted search mode - use in-memory results
            writeBlockOutput(outputPath)
        } else if (hasIndexAllMode) {
            // Index-all mode - stream from database
            writeBlockOutputFromDb(outputPath)
        }
    }

    /**
     * Write portal detection results to output files
     */
    static void writePortalOutput(String outputPath) {
        LOGGER.info("Writing portal output (${portalResults.size()} portals found)")

        switch (blockOutputFormat.toLowerCase()) {
            case 'json':
                writePortalJson(outputPath)
                break
            case 'txt':
                writePortalTxt(outputPath)
                break
            case 'csv':
            default:
                writePortalCsv(outputPath)
                break
        }
    }

    static void writePortalCsv(String outputPath) {
        File csvFile = new File(outputPath, 'portals.csv')
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine(PortalDetector.CSV_HEADER)
            portalResults.each { PortalDetector.Portal portal ->
                writer.writeLine(portal.toCsvRow())
            }
        }
        LOGGER.info('Portal CSV written to: portals.csv')
    }

    static void writePortalTxt(String outputPath) {
        File txtFile = new File(outputPath, 'portals.txt')
        txtFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Nether Portal Detection Report')
            writer.writeLine('=' * 80)
            writer.writeLine("Total portals found: ${portalResults.size()}")
            writer.writeLine('')

            // Group by dimension
            Map<String, List<PortalDetector.Portal>> byDimension = portalResults.groupBy { PortalDetector.Portal portal -> portal.dimension }

            byDimension.each { String dimension, List<PortalDetector.Portal> portals ->
                writer.writeLine("${dimension.toUpperCase()} (${portals.size()} portals):")
                writer.writeLine('-' * 40)
                portals.each { PortalDetector.Portal portal ->
                    writer.writeLine("  Portal #${portal.id}:")
                    writer.writeLine("    Anchor: (${portal.anchorX}, ${portal.anchorY}, ${portal.anchorZ})")
                    writer.writeLine("    Size: ${portal.width}x${portal.height} (${portal.blockCount} blocks)")
                    writer.writeLine("    Axis: ${portal.axis}")
                    writer.writeLine("    Center: (${portal.centerX}, ${portal.centerY}, ${portal.centerZ})")
                    writer.writeLine('')
                }
            }
        }
        LOGGER.info('Portal TXT written to: portals.txt')
    }

    static void writePortalJson(String outputPath) {
        File jsonFile = new File(outputPath, 'portals.json')
        jsonFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('{')
            writer.writeLine('  "portals": [')
            portalResults.eachWithIndex { PortalDetector.Portal portal, int index ->
                Map<String, Object> map = portal.toMap()
                String json = mapToJson(map, 4)
                writer.write("    ${json}")
                if (index < portalResults.size() - 1) {
                    writer.writeLine(',')
                } else {
                    writer.writeLine('')
                }
            }
            writer.writeLine('  ],')
            writer.writeLine('  "summary": {')
            writer.writeLine("    \"total_portals\": ${portalResults.size()},")

            // Count by dimension
            Map<String, Integer> byDimension = [:]
            portalResults.each { PortalDetector.Portal p ->
                byDimension[p.dimension] = (byDimension[p.dimension] ?: 0) + 1
            }
            writer.writeLine('    "by_dimension": {')
            byDimension.eachWithIndex { String dim, Integer count, int idx ->
                String comma = idx < byDimension.size() - 1 ? ',' : ''
                writer.writeLine("      \"${dim}\": ${count}${comma}")
            }
            writer.writeLine('    }')
            writer.writeLine('  }')
            writer.writeLine('}')
        }
        LOGGER.info('Portal JSON written to: portals.json')
    }

    /**
     * Write block search results to output files
     */
    static void writeBlockOutput(String outputPath) {
        LOGGER.info("Writing block output (${blockSearchResults.size()} blocks found)")

        switch (blockOutputFormat.toLowerCase()) {
            case 'json':
                writeBlockJson(outputPath)
                break
            case 'txt':
                writeBlockTxt(outputPath)
                break
            case 'csv':
            default:
                writeBlockCsv(outputPath)
                break
        }
    }

    static void writeBlockCsv(String outputPath) {
        File csvFile = new File(outputPath, 'blocks.csv')
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('block_type,dimension,x,y,z,properties,region_file')
            blockSearchResults.each { BlockSearcher.BlockLocation block ->
                writer.writeLine(block.toCsvRow())
            }
        }
        LOGGER.info('Block CSV written to: blocks.csv')
    }

    static void writeBlockTxt(String outputPath) {
        File txtFile = new File(outputPath, 'blocks.txt')
        txtFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Block Search Report')
            writer.writeLine('=' * 80)
            writer.writeLine("Total blocks found: ${blockSearchResults.size()}")
            writer.writeLine('')

            // Group by block type
            Map<String, List<BlockSearcher.BlockLocation>> byType = blockSearchResults.groupBy { BlockSearcher.BlockLocation block -> block.blockType }

            byType.each { String blockType, List<BlockSearcher.BlockLocation> blocks ->
                writer.writeLine("${blockType} (${blocks.size()} found):")
                writer.writeLine('-' * 40)
                blocks.each { BlockSearcher.BlockLocation block ->
                    String propsStr = block.properties ? " [${block.properties.collect { k, v -> "${k}=${v}" }.join(', ')}]" : ''
                    writer.writeLine("  ${block.dimension}: (${block.x}, ${block.y}, ${block.z})${propsStr}")
                }
                writer.writeLine('')
            }
        }
        LOGGER.info('Block TXT written to: blocks.txt')
    }

    static void writeBlockJson(String outputPath) {
        File jsonFile = new File(outputPath, 'blocks.json')
        jsonFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('{')
            writer.writeLine('  "blocks": [')
            blockSearchResults.eachWithIndex { BlockSearcher.BlockLocation block, int index ->
                writer.writeLine('    {')
                writer.writeLine("      \"type\": ${escapeJson(block.blockType)},")
                writer.writeLine("      \"dimension\": ${escapeJson(block.dimension)},")
                writer.writeLine('      "coordinates": {')
                writer.writeLine("        \"x\": ${block.x},")
                writer.writeLine("        \"y\": ${block.y},")
                writer.writeLine("        \"z\": ${block.z}")
                writer.writeLine('      },')
                writer.write('      "properties": {')
                if (block.properties && !block.properties.empty) {
                    writer.writeLine('')
                    block.properties.eachWithIndex { String k, String v, int idx ->
                        String comma = idx < block.properties.size() - 1 ? ',' : ''
                        writer.writeLine("        \"${k}\": ${escapeJson(v)}${comma}")
                    }
                    writer.write('      ')
                }
                writer.writeLine('},')
                writer.writeLine("      \"region\": ${escapeJson(block.regionFile)}")
                writer.write('    }')
                if (index < blockSearchResults.size() - 1) {
                    writer.writeLine(',')
                } else {
                    writer.writeLine('')
                }
            }
            writer.writeLine('  ],')
            writer.writeLine('  "summary": {')
            writer.writeLine("    \"total_blocks\": ${blockSearchResults.size()},")

            // Count by type
            Map<String, Integer> byType = [:]
            blockSearchResults.each { BlockSearcher.BlockLocation b ->
                byType[b.blockType] = (byType[b.blockType] ?: 0) + 1
            }
            writer.writeLine('    "by_type": {')
            byType.eachWithIndex { String type, Integer count, int idx ->
                String comma = idx < byType.size() - 1 ? ',' : ''
                writer.writeLine("      ${escapeJson(type)}: ${count}${comma}")
            }
            writer.writeLine('    },')

            // Count by dimension
            Map<String, Integer> byDimension = [:]
            blockSearchResults.each { BlockSearcher.BlockLocation b ->
                byDimension[b.dimension] = (byDimension[b.dimension] ?: 0) + 1
            }
            writer.writeLine('    "by_dimension": {')
            byDimension.eachWithIndex { String dim, Integer count, int idx ->
                String comma = idx < byDimension.size() - 1 ? ',' : ''
                writer.writeLine("      \"${dim}\": ${count}${comma}")
            }
            writer.writeLine('    }')
            writer.writeLine('  }')
            writer.writeLine('}')
        }
        LOGGER.info('Block JSON written to: blocks.json')
    }

    // ========== Index-All Mode: Stream from Database ==========

    /**
     * Write block output by streaming from the database (index-all mode).
     * This avoids loading all blocks into memory.
     */
    static void writeBlockOutputFromDb(String outputPath) {
        File dbFile = new File(outputPath, 'block_index.db')
        if (!dbFile.exists()) {
            LOGGER.warn("Block index database not found: ${dbFile.absolutePath}")
            return
        }

        BlockDatabase db = BlockDatabase.openForQuery(dbFile)
        if (!db) {
            LOGGER.warn('Failed to open block index database')
            return
        }

        try {
            int totalBlocks = db.totalBlockCount
            LOGGER.info("Writing block output from database (${totalBlocks} blocks indexed)")

            switch (blockOutputFormat.toLowerCase()) {
                case 'json':
                    writeBlockJsonFromDb(outputPath, db)
                    break
                case 'txt':
                    writeBlockTxtFromDb(outputPath, db)
                    break
                case 'csv':
                default:
                    writeBlockCsvFromDb(outputPath, db)
                    break
            }
        } finally {
            db.close()
        }
    }

    /**
     * Write blocks.csv by streaming from database.
     */
    static void writeBlockCsvFromDb(String outputPath, BlockDatabase db) {
        File csvFile = new File(outputPath, 'blocks.csv')
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('block_type,dimension,x,y,z,properties,region_file')

            db.streamBlocks { String blockType, String dimension, int x, int y, int z, String propsJson, String regionFile ->
                // Convert JSON properties back to k=v;k=v format for consistency
                String propsStr = ''
                if (propsJson) {
                    try {
                        Object props = JSON_SLURPER.parseText(propsJson)
                        if (props instanceof Map) {
                            propsStr = props.collect { String k, Object v -> "${k}=${v}" }.join(';')
                        }
                    } catch (groovy.json.JsonException e) {
                        // Catch all exceptions: JSON parsing may fail on malformed data
                        propsStr = propsJson  // Fallback to raw JSON if parse fails
                    }
                }
                writer.writeLine("${blockType},${dimension},${x},${y},${z},${propsStr},${regionFile ?: ''}")
            }
        }
        LOGGER.info('Block CSV written to: blocks.csv')
    }

    /**
     * Write blocks.txt by streaming from database.
     * Groups by block type on the fly by detecting type changes while iterating ordered rows.
     */
    static void writeBlockTxtFromDb(String outputPath, BlockDatabase db) {
        File txtFile = new File(outputPath, 'blocks.txt')
        int totalBlocks = db.totalBlockCount

        txtFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Block Search Report (Index-All Mode)')
            writer.writeLine('=' * 80)
            writer.writeLine("Total blocks indexed: ${totalBlocks}")
            writer.writeLine('')

            // Get summary for per-type counts
            List<Map> summary = db.summary
            summary.each { Map row ->
                String blockType = row.block_type
                int count = row.indexed_count
                boolean limitReached = row.limit_reached == 1

                writer.writeLine("${blockType} (${count} indexed${limitReached ? ', limit reached' : ''}):")
                writer.writeLine('-' * 40)

                // Query blocks of this type
                List<Map> blocks = db.queryByBlockType(blockType)
                blocks.each { Map block ->
                    String propsStr = ''
                    if (block.properties) {
                        try {
                            Object props = JSON_SLURPER.parseText(block.properties)
                            if (props instanceof Map) {
                                propsStr = " [${props.collect { String k, Object v -> "${k}=${v}" }.join(', ')}]"
                            }
                        } catch (groovy.json.JsonException e) {
                            // Empty catch: JSON parsing may fail on malformed data, we simply skip properties and continue
                            LOGGER.trace("Failed to parse block properties JSON: ${e.message}")
                        }
                    }
                    writer.writeLine("  ${block.dimension}: (${block.x}, ${block.y}, ${block.z})${propsStr}")
                }
                writer.writeLine('')
            }
        }
        LOGGER.info('Block TXT written to: blocks.txt')
    }

    /**
     * Write blocks.json by streaming from database.
     */
    static void writeBlockJsonFromDb(String outputPath, BlockDatabase db) {
        File jsonFile = new File(outputPath, 'blocks.json')
        int totalBlocks = db.totalBlockCount

        // Pre-compute summary counts
        Map<String, Integer> byType = [:]
        Map<String, Integer> byDimension = [:]
        List<Map> summary = db.summary
        summary.each { Map row ->
            byType[row.block_type] = row.indexed_count
        }

        jsonFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('{')
            writer.writeLine('  "blocks": [')

            int blockIndex = 0
            db.streamBlocks { String blockType, String dimension, int x, int y, int z, String propsJson, String regionFile ->
                byDimension[dimension] = (byDimension[dimension] ?: 0) + 1

                if (blockIndex > 0) {
                    writer.writeLine(',')
                }
                writer.writeLine('    {')
                writer.writeLine("      \"type\": ${escapeJson(blockType)},")
                writer.writeLine("      \"dimension\": ${escapeJson(dimension)},")
                writer.writeLine('      "coordinates": {')
                writer.writeLine("        \"x\": ${x},")
                writer.writeLine("        \"y\": ${y},")
                writer.writeLine("        \"z\": ${z}")
                writer.writeLine('      },')

                // Parse properties
                writer.write('      "properties": {')
                if (propsJson) {
                    try {
                        Object props = JSON_SLURPER.parseText(propsJson)
                        if (props instanceof Map && !props.empty) {
                            writer.writeLine('')
                            int propIdx = 0
                            props.each { String k, v ->
                                String comma = propIdx < props.size() - 1 ? ',' : ''
                                writer.writeLine("        \"${k}\": ${escapeJson(v?.toString())}${comma}")
                                propIdx++
                            }
                            writer.write('      ')
                        }
                    } catch (groovy.json.JsonException e) {
                        // Empty catch: JSON parsing may fail on malformed data, we simply skip properties and continue
                        LOGGER.trace("Failed to parse block properties JSON for output: ${e.message}")
                    }
                }
                writer.writeLine('},')
                writer.write("      \"region\": ${escapeJson(regionFile)}")
                writer.writeLine('')
                writer.write('    }')

                blockIndex++
            }

            if (blockIndex > 0) {
                writer.writeLine('')
            }
            writer.writeLine('  ],')
            writer.writeLine('  "summary": {')
            writer.writeLine("    \"total_blocks\": ${totalBlocks},")
            writer.writeLine('    "mode": "index-all",')

            // Write by_type
            writer.writeLine('    "by_type": {')
            int typeIdx = 0
            byType.each { String type, Integer count ->
                String comma = typeIdx < byType.size() - 1 ? ',' : ''
                writer.writeLine("      ${escapeJson(type)}: ${count}${comma}")
                typeIdx++
            }
            writer.writeLine('    },')

            // Write by_dimension
            writer.writeLine('    "by_dimension": {')
            int dimIdx = 0
            byDimension.each { String dim, Integer count ->
                String comma = dimIdx < byDimension.size() - 1 ? ',' : ''
                writer.writeLine("      \"${dim}\": ${count}${comma}")
                dimIdx++
            }
            writer.writeLine('    }')
            writer.writeLine('  }')
            writer.writeLine('}')
        }
        LOGGER.info('Block JSON written to: blocks.json')
    }

    /**
     * Convert a map to JSON string (simple implementation for nested maps)
     */
    static String mapToJson(Map<String, Object> map, int indent = 0) {
        String indentStr = ' ' * indent
        StringBuilder sb = new StringBuilder()
        sb.append('{\n')
        map.eachWithIndex { String key, Object value, int idx ->
            sb.append("${indentStr}  \"${key}\": ")
            if (value instanceof Map) {
                sb.append(mapToJson(value as Map<String, Object>, indent + 2))
            } else if (value instanceof Number) {
                sb.append(value)
            } else {
                sb.append(escapeJson(value?.toString()))
            }
            if (idx < map.size() - 1) {
                sb.append(',')
            }
            sb.append('\n')
        }
        sb.append("${indentStr}}")
        return sb.toString()
    }

    /**
     * Extract custom name from an item (handles both 1.20.5+ and pre-1.20.5 formats)
     * Returns null if no custom name found or if name is empty/whitespace
     */
    static String extractCustomNameFromItem(CompoundTag item) {
        String customName = null

        // Try new format first (1.20.5+ with components)
        if (hasKey(item, 'components')) {
            CompoundTag components = getCompoundTag(item, 'components')
            if (hasKey(components, 'minecraft:custom_name')) {
                // Custom name is stored as JSON text component
                net.querz.nbt.tag.Tag<?> customNameTag = components.get('minecraft:custom_name')
                if (customNameTag instanceof StringTag) {
                    String rawName = ((StringTag) customNameTag).value
                    customName = TextUtils.extractTextContent(rawName, false)
                }
            }
        } else if (hasKey(item, 'tag')) {
            // Old format (pre-1.20.5): tag.display.Name
            CompoundTag tag = getCompoundTag(item, 'tag')
            if (hasKey(tag, 'display')) {
                CompoundTag display = getCompoundTag(tag, 'display')
                if (hasKey(display, 'Name')) {
                    String rawName = display.getString('Name')
                    customName = TextUtils.extractTextContent(rawName, false)
                }
            }
        }

        // Return null if empty or whitespace
        if (customName && customName.trim().empty) {
            return null
        }
        return customName?.trim()
    }

    /**
     * Extract custom name from an entity
     * Returns null if no custom name found or if name is empty/whitespace
     */
    static String extractCustomNameFromEntity(CompoundTag entity) {
        String customName = null

        // Entities store CustomName at root level as JSON text component
        if (hasKey(entity, 'CustomName')) {
            String rawName = entity.getString('CustomName')
            customName = TextUtils.extractTextContent(rawName, false)
        }

        // Return null if empty or whitespace
        if (customName && customName.trim().empty) {
            return null
        }
        return customName?.trim()
    }

    /**
     * Record a custom name with deduplication
     * Creates hash from: customName|type|itemOrEntityId
     */
    @SuppressWarnings('ParameterCount')
    static void recordCustomName(String customName, String itemOrEntityId, String type, String location, int x, int y, int z) {
        if (!customName) {
            return
        }

        // Create deduplication hash
        String hashKey = "${customName}|${type}|${itemOrEntityId}".hashCode()

        if (!customNameHashes.add(hashKey)) {
            LOGGER.debug("Duplicate custom name found, skipping: ${customName}")
            return
        }

        customNameData.add([
            type: type,
            itemOrEntityId: itemOrEntityId,
            customName: customName,
            x: x,
            y: y,
            z: z,
            location: location
        ])

        LOGGER.debug("Recorded custom name: '${customName}' (${type}: ${itemOrEntityId})")
    }

    /**
     * Extract item metadata from NBT for database indexing.
     * Handles both pre-1.20.5 (tag) and 1.20.5+ (components) formats.
     *
     * Research notes (added during Issue #17 review):
     * - 1.20.5+ item stacks store most metadata in `components` (data components system).
     * - Pre-1.20.5 item stacks store metadata in `tag` (classic NBT item format).
     *
     * References:
     * - Item structure overview: https://minecraft.wiki/w/Player.dat_format#Item_structure
     * - Data components: https://minecraft.wiki/w/Data_component_format
     *
     * Related feature request: https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/issues/17
     *
     * @param item The CompoundTag representing the item
     * @param bookInfo Location context string (e.g., "Chunk [x, z] Inside minecraft:chest at (X Y Z)")
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return ItemMetadata object or null if item should be skipped
     */
    @SuppressWarnings('ParameterCount')
    static ItemDatabase.ItemMetadata extractItemMetadata(CompoundTag item, String bookInfo, int x, int y, int z, Integer slotOverride = null) {
        String itemId = item.getString('id')
        if (!itemId) {
            return null
        }
        // Normalize item ids: some formats/commands omit the namespace (e.g. "diamond_sword").
        // The feature request explicitly calls out both forms; normalize early so:
        // - skipCommonItems matches
        // - DB rows are queryable consistently
        // Ref: https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/issues/17
        if (!itemId.contains(':')) {
            itemId = "minecraft:${itemId}"
        }

        // Check if we should skip common items
        if (skipCommonItems && ItemDatabase.DEFAULT_SKIP_ITEMS.contains(itemId)) {
            return null
        }

        ItemDatabase.ItemMetadata metadata = new ItemDatabase.ItemMetadata(itemId)
        metadata.x = x
        metadata.y = y
        metadata.z = z

        // Extract count
        if (item.containsKey('Count') || item.containsKey('count')) {
            Object countTag = item.get('Count') ?: item.get('count')
            if (countTag instanceof NumberTag) {
                metadata.count = ((NumberTag) countTag).asInt()
            }
        }

        // Parse bookInfo to extract container type, dimension, and player UUID if available
        metadata.containerType = parseContainerType(bookInfo)
        metadata.dimension = parseDimension(bookInfo)
        metadata.playerUuid = parsePlayerUuid(bookInfo)
        metadata.regionFile = parseRegionFile(bookInfo)

        // Extract slot if present (classic NBT uses Slot on the item stack).
        // For 1.20.5+ component containers (minecraft:container), the slot may live on the container entry instead;
        // callers can supply slotOverride to preserve uniqueness in the item index.
        if (item.containsKey('Slot') || item.containsKey('slot')) {
            Object slotTag = item.get('Slot') ?: item.get('slot')
            if (slotTag instanceof NumberTag) {
                metadata.slot = ((NumberTag) slotTag).asInt()
            }
        } else if (slotOverride != null) {
            metadata.slot = slotOverride
        }

        // Try new format first (1.20.5+ with components)
        if (hasKey(item, 'components')) {
            extractMetadataFromComponents(item.getCompoundTag('components'), metadata)
        } else if (hasKey(item, 'tag')) {
            // Old format (pre-1.20.5)
            extractMetadataFromTag(item.getCompoundTag('tag'), metadata)
        }

        return metadata
    }

    /**
     * Extract metadata from 1.20.5+ components format.
     */
    private static void extractMetadataFromComponents(CompoundTag components, ItemDatabase.ItemMetadata metadata) {
        // 1.20.5+ data components:
        // - enchantments/stored_enchantments are stored as compounds with `levels` maps
        // - damage is `minecraft:damage`
        // - custom_name is `minecraft:custom_name` (JSON text component)
        // Ref: https://minecraft.wiki/w/Data_component_format

        // Custom name
        if (hasKey(components, 'minecraft:custom_name')) {
            net.querz.nbt.tag.Tag<?> nameTag = components.get('minecraft:custom_name')
            if (nameTag instanceof StringTag) {
                metadata.customName = TextUtils.extractTextContent(((StringTag) nameTag).value, false)?.trim()
            }
        }

        // Damage
        if (hasKey(components, 'minecraft:damage')) {
            Object damageTag = components.get('minecraft:damage')
            if (damageTag instanceof NumberTag) {
                metadata.damage = ((NumberTag) damageTag).asInt()
            }
        }

        // Enchantments (1.20.5+ format: minecraft:enchantments = {levels: {sharpness: 5}})
        if (hasKey(components, 'minecraft:enchantments')) {
            CompoundTag enchants = getCompoundTag(components, 'minecraft:enchantments')
            if (hasKey(enchants, 'levels')) {
                CompoundTag levels = getCompoundTag(enchants, 'levels')
                levels.keySet().each { String enchantName ->
                    Object levelTag = levels.get(enchantName)
                    if (levelTag instanceof NumberTag) {
                        String shortName = enchantName.replace('minecraft:', '')
                        metadata.enchantments[shortName] = ((NumberTag) levelTag).asInt()
                    }
                }
            }
        }

        // Stored enchantments (for enchanted books)
        if (hasKey(components, 'minecraft:stored_enchantments')) {
            CompoundTag storedEnchants = getCompoundTag(components, 'minecraft:stored_enchantments')
            if (hasKey(storedEnchants, 'levels')) {
                CompoundTag levels = getCompoundTag(storedEnchants, 'levels')
                levels.keySet().each { String enchantName ->
                    Object levelTag = levels.get(enchantName)
                    if (levelTag instanceof NumberTag) {
                        String shortName = enchantName.replace('minecraft:', '')
                        metadata.storedEnchantments[shortName] = ((NumberTag) levelTag).asInt()
                    }
                }
            }
        }

        // Lore
        if (hasKey(components, 'minecraft:lore')) {
            ListTag<?> loreList = getListTag(components, 'minecraft:lore')
            if (loreList) {
                loreList.each { loreTag ->
                    if (loreTag instanceof StringTag) {
                        String loreText = TextUtils.extractTextContent(((StringTag) loreTag).value, false)?.trim()
                        if (loreText) {
                            metadata.lore.add(loreText)
                        }
                    }
                }
            }
        }

        // Unbreakable
        if (hasKey(components, 'minecraft:unbreakable')) {
            metadata.unbreakable = true
        }
    }

    /**
     * Extract metadata from pre-1.20.5 tag format.
     */
    private static void extractMetadataFromTag(CompoundTag tag, ItemDatabase.ItemMetadata metadata) {
        // Pre-1.20.5 classic item NBT:
        // - display.Name / display.Lore for naming/lore
        // - Enchantments / StoredEnchantments lists with entries {id, lvl}
        // Ref: https://minecraft.wiki/w/Player.dat_format#Item_structure

        // Custom name (tag.display.Name)
        if (hasKey(tag, 'display')) {
            CompoundTag display = getCompoundTag(tag, 'display')
            if (hasKey(display, 'Name')) {
                String rawName = display.getString('Name')
                metadata.customName = TextUtils.extractTextContent(rawName, false)?.trim()
            }
            // Lore (tag.display.Lore)
            if (hasKey(display, 'Lore')) {
                ListTag<?> loreList = getListTag(display, 'Lore')
                if (loreList) {
                    loreList.each { loreTag ->
                        if (loreTag instanceof StringTag) {
                            String loreText = TextUtils.extractTextContent(((StringTag) loreTag).value, false)?.trim()
                            if (loreText) {
                                metadata.lore.add(loreText)
                            }
                        }
                    }
                }
            }
        }

        // Damage
        if (hasKey(tag, 'Damage')) {
            Object damageTag = tag.get('Damage')
            if (damageTag instanceof NumberTag) {
                metadata.damage = ((NumberTag) damageTag).asInt()
            }
        }

        // Enchantments (pre-1.20.5 format: Enchantments = [{id: "minecraft:sharpness", lvl: 5s}])
        if (hasKey(tag, 'Enchantments')) {
            ListTag<?> enchantsList = getListTag(tag, 'Enchantments')
            if (enchantsList) {
                enchantsList.each { enchantTag ->
                    if (enchantTag instanceof CompoundTag) {
                        CompoundTag enchant = (CompoundTag) enchantTag
                        String enchantId = enchant.getString('id')?.replace('minecraft:', '')
                        Object lvlTag = enchant.get('lvl')
                        if (enchantId && lvlTag instanceof NumberTag) {
                            metadata.enchantments[enchantId] = ((NumberTag) lvlTag).asInt()
                        }
                    }
                }
            }
        }

        // Stored enchantments (for enchanted books in old format)
        if (hasKey(tag, 'StoredEnchantments')) {
            ListTag<?> storedList = getListTag(tag, 'StoredEnchantments')
            if (storedList) {
                storedList.each { enchantTag ->
                    if (enchantTag instanceof CompoundTag) {
                        CompoundTag enchant = (CompoundTag) enchantTag
                        String enchantId = enchant.getString('id')?.replace('minecraft:', '')
                        Object lvlTag = enchant.get('lvl')
                        if (enchantId && lvlTag instanceof NumberTag) {
                            metadata.storedEnchantments[enchantId] = ((NumberTag) lvlTag).asInt()
                        }
                    }
                }
            }
        }

        // Unbreakable
        if (hasKey(tag, 'Unbreakable')) {
            Object unbTag = tag.get('Unbreakable')
            if (unbTag instanceof NumberTag) {
                metadata.unbreakable = ((NumberTag) unbTag).asInt() != 0
            }
        }
    }

    /**
     * Parse container type from bookInfo string.
     * Examples:
     * - "Chunk [x, z] Inside minecraft:chest at (X Y Z)" -> "chest"
     * - "Inventory of player UUID.dat" -> "player_inventory"
     * - "EnderItems of player UUID.dat" -> "ender_chest"
     */
    static String parseContainerType(String bookInfo) {
        if (!bookInfo) {
            return 'unknown'
        }

        // Playerdata can include multiple snapshots (e.g. "<uuid>.dat" and "<uuid>.dat_old").
        // Treat these as distinct sources for item-index uniqueness, otherwise items at the same (x,y,z,slot)
        // will be de-duplicated across snapshots and the item index won't match the extractor's counts.
        boolean isDatOld = bookInfo.contains('.dat_old')

        if (bookInfo.contains('Inventory of player')) {
            return isDatOld ? 'player_inventory_old' : 'player_inventory'
        }
        // Historical strings used across the codebase/tests:
        // - "EnderItems of player <uuid>.dat" (older)
        // - "Ender Chest of player <uuid>.dat" (newer, more readable)
        if (bookInfo.contains('EnderItems of player') || bookInfo.contains('Ender Chest of player')) {
            return isDatOld ? 'ender_chest_old' : 'ender_chest'
        }
        if (bookInfo.contains('In minecraft:item_frame')) {
            return 'item_frame'
        }
        if (bookInfo.contains('In minecraft:glow_item_frame')) {
            return 'glow_item_frame'
        }

        // Try to extract container type from "Inside minecraft:X at" pattern
        java.util.regex.Matcher matcher = bookInfo =~ /Inside (minecraft:)?(\w+) at/
        if (matcher.find()) {
            return matcher.group(2)
        }

        // Try "In minecraft:X at" pattern (for entities)
        matcher = bookInfo =~ /In (minecraft:)?(\w+) at/
        if (matcher.find()) {
            return matcher.group(2)
        }

        // Check for nested containers
        if (bookInfo.contains('> shulker_box')) {
            return 'shulker_box'
        }
        if (bookInfo.contains('> bundle')) {
            return 'bundle'
        }
        return bookInfo.contains('> copper_chest') ? 'copper_chest' : 'unknown'
    }

    /**
     * Parse dimension from bookInfo string.
     * Returns: overworld, nether, end, or null if unknown
     */
    static String parseDimension(String bookInfo) {
        if (!bookInfo) {
            return null
        }

        // Check for player data (dimension unknown)
        if (bookInfo.contains('of player')) {
            return null
        }

        // Check for dimension folders in path
        if (bookInfo.contains('DIM-1') || bookInfo.contains('nether')) {
            return 'nether'
        }
        if (bookInfo.contains('DIM1') || bookInfo.contains('the_end')) {
            return 'end'
        }

        // Default to overworld for region files without dimension markers
        return (bookInfo.contains('region/') || bookInfo.contains('Chunk [')) ? 'overworld' : null
    }

    /**
     * Parse player UUID from bookInfo string.
     * Example: "Inventory of player 12345678-1234-1234-1234-123456789abc.dat" -> "12345678-1234-1234-1234-123456789abc"
     */
    static String parsePlayerUuid(String bookInfo) {
        if (!bookInfo) {
            return null
        }

        java.util.regex.Matcher matcher = bookInfo =~ /of player ([0-9a-fA-F-]+)\.dat/
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    /**
     * Parse region file from bookInfo string.
     * Example: "Chunk [1, 2] (r.0.0.mca)" -> "r.0.0.mca"
     */
    static String parseRegionFile(String bookInfo) {
        if (!bookInfo) {
            return null
        }

        java.util.regex.Matcher matcher = bookInfo =~ /\((r\.-?\d+\.-?\d+\.mca)\)/
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    // ========== Minecraft Command Generation (delegated to MinecraftCommands & ShulkerBoxGenerator) ==========
    static String escapeForMinecraftCommand(String text, String version) {
        return MinecraftCommands.escapeForMinecraftCommand(text, version)
    }

    static String generateBookNBT(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        return MinecraftCommands.generateBookNBT(title, author, pages, version, generation)
    }

    static String generateBookComponents(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        return MinecraftCommands.generateBookComponents(title, author, pages, version, generation)
    }

    static String generateShulkerBoxCommand(String authorName, List<Map<String, Object>> books, int boxIndex, String version) {
        return ShulkerBoxGenerator.generateShulkerBoxCommand(authorName, books, boxIndex, version)
    }

    static String generateShulkerBox_1_13(String color, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_13(color, displayName, books)
    }

    static String generateShulkerBox_1_14(String color, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_14(color, displayName, books)
    }

    static String generateShulkerBox_1_20_5(String color, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_20_5(color, displayName, books)
    }

    static String generateShulkerBox_1_21(String color, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_21(color, displayName, books)
    }

    static String convertFormattingCodesToJson(String text) {
        return MinecraftCommands.convertFormattingCodesToJson(text)
    }

    static String mapColorCode(char code) {
        return MinecraftCommands.mapColorCode(code)
    }

    static String generateBookCommand(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        return MinecraftCommands.generateBookCommand(title, author, pages, version, generation)
    }

    /**
     * Allocate coordinates for a sign (once per unique text, regardless of versions)
     * Returns a map with {x, z} coordinates
     */
    static Map<String, Object> allocateSignPosition(List<String> lines, List<Object> originalCoords, String signInfo) {
        String signKey = lines.join('|')  // Use text as unique key
        if (signsByHash.containsKey(signKey)) {
            // Duplicate sign - increment Z offset
            Map<String, Object> existing = signsByHash[signKey]
            existing.z++
        } else {
            signsByHash[signKey] = [
                x: signXCoordinate,
                z: 0,  // First occurrence at Z 0, duplicates at Z+1, Z+2, etc.
                lines: lines,
                originalX: originalCoords[0],
                originalY: originalCoords[1],
                originalZ: originalCoords[2],
                signInfo: signInfo
            ]
            signXCoordinate++
        }
        return signsByHash[signKey]
    }

    static String generateSignCommand(List<String> frontLines, Map<String, Object> position, String version, List<String> backLines = null) {
        return MinecraftCommands.generateSignCommand(frontLines, position, version, backLines)
    }

    static String generateSignCommand_1_13(List<String> frontLines, int x, int z, Map<String, Object> position) {
        return MinecraftCommands.generateSignCommand_1_13(frontLines, x, z, position)
    }

    static String generateSignCommand_1_14(List<String> frontLines, int x, int z, Map<String, Object> position) {
        return MinecraftCommands.generateSignCommand_1_14(frontLines, x, z, position)
    }

    static String generateSignCommand_1_20(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        return MinecraftCommands.generateSignCommand_1_20(frontLines, x, z, position, backLines)
    }

    static String generateSignCommand_1_20_5(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        return MinecraftCommands.generateSignCommand_1_20_5(frontLines, x, z, position, backLines)
    }

    static String generateSignCommand_1_21(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        return MinecraftCommands.generateSignCommand_1_21(frontLines, x, z, position, backLines)
    }

    /**
     * Write a sign command to all sign mcfunction version files (with deduplication)
     * @param frontLines List of front text lines (required)
     * @param signInfo Sign info string containing coordinates (required for clickEvent)
     * @param backLines List of back text lines (optional, can be null or empty)
     */
    static void writeSignToMcfunction(List<String> frontLines, String signInfo, List<String> backLines = null) {
        if (!frontLines || frontLines.size() == 0) {
            return
        }

        // Extract original world coordinates from signInfo
        List<Object> originalCoords = extractSignCoordinates(signInfo)

        // Allocate coordinates once for all versions
        Map<String, Object> position = allocateSignPosition(frontLines, originalCoords, signInfo)

        MC_VERSIONS.each { String version ->
            BufferedWriter writer = signsMcfunctionWriters[version]
            if (writer) {
                try {
                    String command = generateSignCommand(frontLines, position, version, backLines)
                    if (command) {
                        writer.writeLine(command)
                    }
                } catch (IOException e) {
                    // Catch all exceptions: command generation or file I/O may fail
                    LOGGER.warn("Failed to write sign to ${version} mcfunction: ${e.message}")
                }
            }
        }
    }

    /**
     * Write a book command to all mcfunction version files AND collect for shulker boxes
     * Includes generation parameter (0=Original, 1=Copy of Original, 2=Copy of Copy, 3=Tattered)
     */
    static void writeBookToMcfunction(String title, String author, ListTag<?> pages, int generation = 0) {
        if (!pages || pages.size() == 0) {
            return
        }

        // Collect book data by author for shulker box generation
        String authorName = author ?: 'Unknown'
        if (!booksByAuthor.containsKey(authorName)) {
            booksByAuthor[authorName] = []
        }
        booksByAuthor[authorName].add([
            title: title ?: 'Untitled',
            author: authorName,
            pages: pages,  // Store the raw NBT ListTag
            generation: generation
        ])

        MC_VERSIONS.each { String version ->
            BufferedWriter writer = mcfunctionWriters[version]
            if (writer) {
                try {
                    String command = generateBookCommand(title, author, pages, version, generation)
                    writer.writeLine(command)
                } catch (IOException e) {
                    // Catch all exceptions: command generation or file I/O may fail
                    LOGGER.warn("Failed to write book to ${version} mcfunction: ${e.message}")
                }
            }
        }
    }

    /**
     * Write all author-organized shulker boxes to mcfunction files
     * Called after all books have been extracted and collected by author
     */
    static void writeShulkerBoxesToMcfunction() {
        if (booksByAuthor.empty) {
            LOGGER.debug('No books collected by author, skipping shulker box generation')
            return
        }

        LOGGER.info("Generating shulker boxes for ${booksByAuthor.size()} author(s)")

        // Sort authors for consistent output
        List<String> sortedAuthors = booksByAuthor.keySet().sort()

        sortedAuthors.each { String author ->
            List<Map<String, Object>> authorBooks = booksByAuthor[author]
            int boxCount = (authorBooks.size() + 26) / 27  // Ceiling division for boxes needed

            LOGGER.debug("Author '${author}' has ${authorBooks.size()} books requiring ${boxCount} shulker box(es)")

            (0..<boxCount).each { int boxIndex ->
                MC_VERSIONS.each { String version ->
                    BufferedWriter writer = mcfunctionWriters[version]
                    if (writer) {
                        try {
                            String command = generateShulkerBoxCommand(author, authorBooks, boxIndex, version)
                            if (command) {
                                // Add separator comment before first shulker box of an author
                                if (boxIndex == 0) {
                                    writer.writeLine("# ========== Shulker Boxes by Author: ${author} ==========")
                                }
                                writer.writeLine(command)
                            }
                        } catch (IOException e) {
                            // Catch all exceptions: command generation or file I/O may fail
                            LOGGER.warn("Failed to write shulker box command for author '${author}' (box ${boxIndex}) to ${version}: ${e.message}", e)
                        }
                    }
                }
            }
        }

        LOGGER.info('Shulker box generation complete')
    }

    static void printSummaryStatistics(long elapsedMillis) {
        OutputWriters.printSummaryStatistics(
            baseDirectory, outputFolder, elapsedMillis,
            bookHashes, signHashes, bookCounter, emptySignsRemoved,
            booksByLocationType, booksByContainerType, bookMetadataList
        )
    }

    static void incrementBookStats(String containerType, String locationType) {
        booksByContainerType[containerType] = (booksByContainerType[containerType] ?: 0) + 1
        booksByLocationType[locationType] = (booksByLocationType[locationType] ?: 0) + 1
    }

    /**
     * Read books from player data files (player inventories and ender chests)
     */
    static void readPlayerData() {
        LOGGER.debug('Starting readPlayerData()')
        File folder = new File(baseDirectory, 'playerdata')

        if (!folder.exists() || !folder.directory) {
            LOGGER.warn("No player data files found in: ${folder.absolutePath}")
            return
        }

        File[] files = folder.listFiles()
        if (!files) {
            LOGGER.warn("No player data files found in: ${folder.absolutePath}")
            return
        }

        LOGGER.debug("Found ${files.length} player data files to process")

        new ProgressBarBuilder()
                .setTaskName('Player data')
                .setInitialMax(files.length)
                .setStyle(ProgressBarStyle.ASCII)
                .build().withCloseable { pb ->
                            files.each { File file ->
                    LOGGER.debug("Processing player data: ${file.name}")
                    CompoundTag playerCompound = readCompressedNBT(file)

                    // Extract player position from Pos tag (list of 3 doubles: [x, y, z])
                    // Player position is used for items in inventory/ender chest that don't have world coordinates
                    int playerX = 0, playerY = 0, playerZ = 0
                    if (hasKey(playerCompound, 'Pos')) {
                        ListTag<?> posTag = getListTag(playerCompound, 'Pos')
                        if (posTag && posTag.size() >= 3) {
                            playerX = getDoubleAt(posTag, 0) as int
                            playerY = getDoubleAt(posTag, 1) as int
                            playerZ = getDoubleAt(posTag, 2) as int
                            LOGGER.debug("Player position: (${playerX}, ${playerY}, ${playerZ})")
                        }
                    }

                    // Process inventory - pass player coordinates for items without world position
                    getCompoundTagList(playerCompound, 'Inventory').each { CompoundTag item ->
                        int booksBefore = bookCounter
                        parseItem(item, "Inventory of player ${file.name}", playerX, playerY, playerZ)
                        if (bookCounter > booksBefore) {
                            incrementBookStats('Player Inventory', 'Player')
                        }
                    }

                    // Process ender chest - pass player coordinates for items without world position
                    getCompoundTagList(playerCompound, 'EnderItems').each { CompoundTag item ->
                        int booksBefore = bookCounter
                        parseItem(item, "Ender Chest of player ${file.name}", playerX, playerY, playerZ)
                        if (bookCounter > booksBefore) {
                            incrementBookStats('Ender Chest', 'Player')
                        }
                    }

                    pb.step()
                            }
                }

        LOGGER.debug('Player data processing complete!')
    }

    /**
     * Read books and signs from region files (main world data)
     * Processes both block entities (chests, signs, lecterns) and entities (item frames, minecarts)
     *
     * CHUNK FORMAT CHANGES (21w43a/1.18):
     * - Removed "Level" wrapper tag
     * - Renamed "TileEntities" → "block_entities"
     * - Renamed "Entities" → "entities"
     *
     * SIGN FORMAT CHANGES (1.20):
     * - Old format: "Text1", "Text2", "Text3", "Text4" fields
     * - New format: "front_text"/"back_text" with "messages" array
     */
    static void readSignsAndBooks() {
        LOGGER.debug('Starting readSignsAndBooks()')
        File folder = new File(baseDirectory, 'region')

        if (!folder.exists() || !folder.directory) {
            LOGGER.warn("No region files found in: ${folder.absolutePath}")
            return
        }

        File[] files = folder.listFiles()
        if (!files) {
            LOGGER.warn("No region files found in: ${folder.absolutePath}")
            return
        }

        LOGGER.debug("Found ${files.length} region files to process")

        File signOutput = new File(resolveOutputBaseDir(), 'all_signs.txt')
        signOutput.withWriter { BufferedWriter signWriter ->
            new ProgressBarBuilder()
                    .setTaskName('Region files')
                    .setInitialMax(files.length)
                    .setStyle(ProgressBarStyle.ASCII)
                    .build().withCloseable { pb ->
                                files.each { File file ->
                        LOGGER.debug("Processing region file: ${file.name}")

                        try {
                            net.querz.mca.MCAFile mcaFile = MCAUtil.read(file, LoadFlags.RAW)

                            // Check if this region was previously marked as failed - if so, mark it as recovered (only if tracking enabled)
                            if (trackFailedRegions) {
                                String worldFolderName = new File(baseDirectory).name
                                if (failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)) {
                                    recoveredRegions.add(file.name)
                                }
                            }

                            (0..31).each { int x ->
                                (0..31).each { int z ->
                                    net.querz.mca.Chunk chunk = mcaFile.getChunk(x, z)
                                    if (!chunk) {
                                        return
                                    }

                                    CompoundTag chunkData = chunk.handle
                                    if (!chunkData) {
                                        return
                                    }

                                    // Handle both old and new chunk formats
                                    CompoundTag level = chunkData.getCompoundTag('Level')
                                    CompoundTag chunkRoot = level ?: chunkData

                                    // Process block entities (chests, signs, etc.)
                                    ListTag<CompoundTag> tileEntities = chunkRoot.containsKey('block_entities') ?
                                         getCompoundTagList(chunkRoot, 'block_entities') :
                                         getCompoundTagList(chunkRoot, 'TileEntities')

                                    tileEntities.each { CompoundTag tileEntity ->
                                        String blockId = tileEntity.getString('id')

                                        // Process containers with items
                                        if (hasKey(tileEntity, 'id')) {
                                            int tileX = tileEntity.getInt('x')
                                            int tileY = tileEntity.getInt('y')
                                            int tileZ = tileEntity.getInt('z')
                                            getCompoundTagList(tileEntity, 'Items').each { CompoundTag item ->
                                                String bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${tileX} ${tileY} ${tileZ}) ${file.name}"
                                                int booksBefore = bookCounter
                                                parseItem(item, bookInfo, tileX, tileY, tileZ)
                                                if (bookCounter > booksBefore) {
                                                    incrementBookStats(blockId, 'Block Entity')
                                                }
                                            }
                                        }

                                        // Process lecterns (single book)
                                        if (hasKey(tileEntity, 'Book')) {
                                            int lectX = tileEntity.getInt('x')
                                            int lectY = tileEntity.getInt('y')
                                            int lectZ = tileEntity.getInt('z')
                                            CompoundTag book = getCompoundTag(tileEntity, 'Book')
                                            String bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${lectX} ${lectY} ${lectZ}) ${file.name}"
                                            int booksBefore = bookCounter
                                            parseItem(book, bookInfo, lectX, lectY, lectZ)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats('Lectern', 'Block Entity')
                                            }
                                        }

                                        // Process signs
                                        String signInfo = "Chunk [${x}, ${z}]\t(${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')})\t\t"
                                        if (hasKey(tileEntity, 'Text1')) {
                                            parseSign(tileEntity, signWriter, signInfo)
                                         } else if (hasKey(tileEntity, 'front_text')) {
                                            parseSignNew(tileEntity, signWriter, signInfo)
                                        }
                                    }

                                    // Process entities in chunk (for proto-chunks)
                                    ListTag<CompoundTag> entities = chunkRoot.containsKey('entities') ?
                                         getCompoundTagList(chunkRoot, 'entities') :
                                         getCompoundTagList(chunkRoot, 'Entities')

                                    entities.each { CompoundTag entity ->
                                        String entityId = entity.getString('id')
                                        ListTag<?> entityPos = getListTag(entity, 'Pos')
                                        int xPos = getDoubleAt(entityPos, 0) as int
                                        int yPos = getDoubleAt(entityPos, 1) as int
                                        int zPos = getDoubleAt(entityPos, 2) as int

                                        // Entities with inventory
                                        if (hasKey(entity, 'Items')) {
                                            getCompoundTagList(entity, 'Items').each { CompoundTag item ->
                                                String bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                                int booksBefore = bookCounter
                                                parseItem(item, bookInfo, xPos, yPos, zPos)
                                                if (bookCounter > booksBefore) {
                                                    incrementBookStats(entityId, 'Entity')
                                                }
                                            }
                                        }

                                        // Item frames and items on ground
                                        if (hasKey(entity, 'Item')) {
                                            CompoundTag item = getCompoundTag(entity, 'Item')
                                            String bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                            int booksBefore = bookCounter
                                            parseItem(item, bookInfo, xPos, yPos, zPos)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats(entityId, 'Entity')
                                            }
                                        }
                                    }
                                }
                            }
                         } catch (IOException e) {
                            // Catch all exceptions: region file parsing can fail in many ways (corruption, format changes, NBT errors)
                            if (trackFailedRegions) {
                                // Check if this region is known to have failed before - if so, suppress the error message
                                String worldFolderName = new File(baseDirectory).name
                                boolean isKnownFailure = failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)

                                if (isKnownFailure) {
                                    LOGGER.debug("(Previously failed region, error suppressed) ${file.name}: ${e.message}")
                                 } else {
                                    LOGGER.warn("Failed to read region file ${file.name}: ${e.message}")
                                    // Track this as a new failure
                                    if (!failedRegionsByWorld.containsKey(worldFolderName)) {
                                        failedRegionsByWorld[worldFolderName] = [] as Set
                                    }
                                    failedRegionsByWorld[worldFolderName].add(file.name)
                                }
                             } else {
                                // Always log when tracking is disabled
                                LOGGER.warn("Failed to read region file ${file.name}: ${e.message}")
                            }
                        }

                        pb.step()
                                }
                    }

            signWriter.writeLine('\nCompleted.')
        }

        LOGGER.debug('Processing complete!')
        LOGGER.debug("Total unique signs found: ${signHashes.size()}")
        LOGGER.debug("Total unique books found: ${bookHashes.size()}")
    }

    /**
     * Read entities from separate entity files (introduced in 20w45a/1.17)
     * Entities like item frames, minecarts, boats are stored in entities/ folder
     * Prior to 20w45a, entities were stored within chunk data in region files
     *
     * CHUNK FORMAT CHANGES (21w43a/1.18):
     * - Removed "Level" wrapper tag
     * - Renamed "Entities" → "entities"
     */
    static void readEntities() {
        LOGGER.debug('Starting readEntities()')
        File folder = new File(baseDirectory, 'entities')

        if (!folder.exists() || !folder.directory) {
            LOGGER.debug("Entities folder not found (normal for pre-1.17 worlds): ${folder.absolutePath}")
            return
        }

        List<File> files = folder.listFiles()?.findAll { File f -> f.file && f.name.endsWith('.mca') }
        if (!files) {
            LOGGER.debug("No entity files found in: ${folder.absolutePath}")
            return
        }

        LOGGER.debug("Found ${files.size()} entity files to process")

        new ProgressBarBuilder()
                .setTaskName('Entity files')
                .setInitialMax(files.size())
                .setStyle(ProgressBarStyle.ASCII)
                .build().withCloseable { pb ->
                            files.each { File file ->
                    LOGGER.debug("Processing entity file: ${file.name}")

                    try {
                        net.querz.mca.MCAFile mcaFile = MCAUtil.read(file, LoadFlags.RAW)

                        // Check if this entity file was previously marked as failed - if so, mark it as recovered (only if tracking enabled)
                        if (trackFailedRegions) {
                            String worldFolderName = new File(baseDirectory).name
                            if (failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)) {
                                recoveredRegions.add(file.name)
                            }
                        }

                        (0..31).each { int x ->
                            (0..31).each { int z ->
                                net.querz.mca.Chunk chunk = mcaFile.getChunk(x, z)
                                if (!chunk) {
                                    return
                                }

                                CompoundTag chunkData = chunk.handle
                                if (!chunkData) {
                                    return
                                }

                                CompoundTag level = chunkData.getCompoundTag('Level')
                                CompoundTag chunkRoot = level ?: chunkData

                                ListTag<CompoundTag> entities = chunkRoot.containsKey('entities') ?
                                     getCompoundTagList(chunkRoot, 'entities') :
                                     getCompoundTagList(chunkRoot, 'Entities')

                                entities.each { CompoundTag entity ->
                                    String entityId = entity.getString('id')
                                    ListTag<?> entityPos = getListTag(entity, 'Pos')
                                    int xPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 0) as int : 0
                                    int yPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 1) as int : 0
                                    int zPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 2) as int : 0

                                    // Extract custom name from entity if enabled
                                    if (extractCustomNames) {
                                        String customName = extractCustomNameFromEntity(entity)
                                        if (customName) {
                                            String location = "Chunk [${x}, ${z}] Entity ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                            recordCustomName(customName, entityId, 'entity', location, xPos, yPos, zPos)
                                        }
                                    }

                                    // Entities with inventory
                                    if (hasKey(entity, 'Items')) {
                                        getCompoundTagList(entity, 'Items').each { CompoundTag item ->
                                            String bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                            int booksBefore = bookCounter
                                            parseItem(item, bookInfo, xPos, yPos, zPos)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats(entityId, 'Entity')
                                            }
                                        }
                                    }

                                    // Item frames and items on ground
                                    if (hasKey(entity, 'Item')) {
                                        CompoundTag item = getCompoundTag(entity, 'Item')
                                        String bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                        int booksBefore = bookCounter
                                        parseItem(item, bookInfo, xPos, yPos, zPos)
                                        if (bookCounter > booksBefore) {
                                            incrementBookStats(entityId, 'Entity')
                                        }
                                    }
                                }
                            }
                        }
                     } catch (IOException e) {
                        // Catch all exceptions: entity file parsing can fail in many ways (corruption, format changes, NBT errors)
                        if (trackFailedRegions) {
                            // Check if this entity file is known to have failed before - if so, suppress the error message
                            String worldFolderName = new File(baseDirectory).name
                            boolean isKnownFailure = failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)

                            if (isKnownFailure) {
                                LOGGER.debug("(Previously failed entity file, error suppressed) ${file.name}: ${e.message}")
                             } else {
                                LOGGER.warn("Failed to read entity file ${file.name}: ${e.message}")
                                // Track this as a new failure
                                if (!failedRegionsByWorld.containsKey(worldFolderName)) {
                                    failedRegionsByWorld[worldFolderName] = [] as Set
                                }
                                failedRegionsByWorld[worldFolderName].add(file.name)
                            }
                         } else {
                            // Always log when tracking is disabled
                            LOGGER.warn("Failed to read entity file ${file.name}: ${e.message}")
                        }
                    }

                    pb.step()
                            }
                }

        LOGGER.debug('Entity processing complete!')
    }

    /**
     * Parse an item and recursively scan for books in nested containers
     *
     * ITEM FORMAT CHANGES (1.20.5):
     * - Changed "tag" → "components"
     * - Changed "BlockEntityTag" → "minecraft:container"
     * - minecraft:container stores items as list of slot records: {slot: int, item: ItemStack}
     * - minecraft:bundle_contents stores items as direct list of ItemStacks (not slot records)
     *
     * CONTAINERS THAT CAN HOLD BOOKS:
     * - Barrel, Chest, Trapped Chest, Ender Chest (block entities with Items)
     * - Shulker Box (item/block with container component) - all 17 color variants
     * - Bundle (item with bundle_contents component) - 1.20.5+
     * - Copper Chest (various oxidation states) - modded
     * - Decorated Pot (block entity with Items)
     * - Dispenser, Dropper (block entities with Items)
     * - Furnace, Blast Furnace, Smoker (block entities with Items)
     * - Hopper (block entity with Items)
     * - Lectern (block entity with Book tag) - holds single book
     * - Minecart with Chest/Hopper (entity with Items)
     * - Item Frame, Glow Item Frame (entity with Item)
     * - Player Inventory, Ender Chest (player data with Inventory/EnderItems)
     *
     * CONTAINERS THAT CANNOT HOLD BOOKS:
     * - Armor Stand (can only hold armor/equipment)
     * - Brewing Stand (can only hold potions/ingredients)
     * - Campfire, Soul Campfire (can only hold 4 food items for cooking)
     * - Cauldron (holds liquids/powders, not items)
     * - Flower Pot (can only hold flowers/plants)
     * - Jukebox (can only hold music discs)
     */
    @SuppressWarnings('ParameterCount')
    static void parseItem(CompoundTag item, String bookInfo, int x = 0, int y = 0, int z = 0, Integer slotOverride = null) {
        String itemId = item.getString('id')
        if (itemId && !itemId.contains(':')) {
            itemId = "minecraft:${itemId}"
        }

        // Extract custom name if enabled
        if (extractCustomNames) {
            String customName = extractCustomNameFromItem(item)
            if (customName) {
                recordCustomName(customName, itemId, 'item', bookInfo, x, y, z)
            }
        }

        // Index item to database if enabled
        if (indexItems && itemDatabase != null) {
            ItemDatabase.ItemMetadata metadata = extractItemMetadata(item, bookInfo, x, y, z, slotOverride)
            if (metadata != null) {
                itemDatabase.insertItem(metadata)
            }
        }

        if (itemId == 'minecraft:written_book') {
            LOGGER.debug("Found written book: ${bookInfo.take(80)}...")
            readWrittenBook(item, bookInfo)
        }

        if (itemId == 'minecraft:writable_book') {
            LOGGER.debug("Found writable book: ${bookInfo.take(80)}...")
            readWritableBook(item, bookInfo)
        }

        // Shulker boxes (all 17 color variants)
        // Matches: minecraft:shulker_box, minecraft:white_shulker_box, minecraft:orange_shulker_box, etc.
        if (itemId.contains('shulker_box')) {
            LOGGER.debug('Found shulker box, scanning contents...')

            // Preserve nested-container uniqueness:
            // Use the shulker's *own* slot (in its parent container) as a prefix for the inner-slot,
            // so two shulkers at the same (x,y,z) don't collide in the SQLite UNIQUE constraint.
            Integer parentSlot = slotOverride
            if (parentSlot == null && (item.containsKey('Slot') || item.containsKey('slot'))) {
                Object parentSlotTag = item.get('Slot') ?: item.get('slot')
                if (parentSlotTag instanceof NumberTag) {
                    parentSlot = ((NumberTag) parentSlotTag).asInt()
                }
            }

            // Try new format first (1.20.5+ with components)
            // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, 'item')
                        Integer slot = null
                        if (containerEntry.containsKey('slot')) {
                            Object slotTag = containerEntry.get('slot')
                            if (slotTag instanceof NumberTag) {
                                slot = ((NumberTag) slotTag).asInt()
                            }
                        }
                        Integer compositeSlot = slot
                        if (parentSlot != null && compositeSlot != null) {
                            compositeSlot = (parentSlot * 1000) + compositeSlot
                        }
                        parseItem(shelkerItem, "${bookInfo} > shulker_box", x, y, z, compositeSlot)
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (pre-1.20.5)
                CompoundTag shelkerCompound = getCompoundTag(item, 'tag')
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, 'BlockEntityTag')
                getCompoundTagList(shelkerCompound2, 'Items').each { CompoundTag shelkerItem ->
                    // Old format already stores per-item Slot; still prefix with parentSlot (if known) to avoid collisions
                    // when multiple shulkers exist at the same coordinates.
                    Integer innerSlot = null
                    if (shelkerItem.containsKey('Slot') || shelkerItem.containsKey('slot')) {
                        Object innerSlotTag = shelkerItem.get('Slot') ?: shelkerItem.get('slot')
                        if (innerSlotTag instanceof NumberTag) {
                            innerSlot = ((NumberTag) innerSlotTag).asInt()
                        }
                    }
                    Integer compositeSlot = innerSlot
                    if (parentSlot != null && compositeSlot != null) {
                        compositeSlot = (parentSlot * 1000) + compositeSlot
                    }
                    parseItem(shelkerItem, "${bookInfo} > shulker_box", x, y, z, compositeSlot)
                }
            }
        }

        // Bundle support (1.20.5+)
        // Matches: minecraft:bundle, minecraft:white_bundle, minecraft:orange_bundle, etc.
        // Note: minecraft:bundle_contents stores items as a direct list of ItemStacks (not slot records like containers)
        if (itemId.contains('bundle')) {
            LOGGER.debug('Found bundle, scanning contents...')

            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:bundle_contents')) {
                    // Same uniqueness trick as shulkers: prefix list index with parent slot.
                    Integer parentSlot = slotOverride
                    if (parentSlot == null && (item.containsKey('Slot') || item.containsKey('slot'))) {
                        Object parentSlotTag = item.get('Slot') ?: item.get('slot')
                        if (parentSlotTag instanceof NumberTag) {
                            parentSlot = ((NumberTag) parentSlotTag).asInt()
                        }
                    }

                    int idx = 0
                    getCompoundTagList(components, 'minecraft:bundle_contents').each { CompoundTag bundleItem ->
                        Integer compositeSlot = idx
                        if (parentSlot != null) {
                            compositeSlot = (parentSlot * 1000) + idx
                        }
                        parseItem(bundleItem, "${bookInfo} > bundle", x, y, z, compositeSlot)
                        idx++
                    }
                }
            }
        }

        // Copper chest support (various oxidation states)
        // Matches: minecraft:copper_chest, minecraft:exposed_copper_chest, minecraft:weathered_copper_chest,
        //          minecraft:oxidized_copper_chest, and waxed variants
        // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
        if (itemId.contains('copper_chest')) {
            LOGGER.debug('Found copper chest, scanning contents...')

            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    Integer parentSlot = slotOverride
                    if (parentSlot == null && (item.containsKey('Slot') || item.containsKey('slot'))) {
                        Object parentSlotTag = item.get('Slot') ?: item.get('slot')
                        if (parentSlotTag instanceof NumberTag) {
                            parentSlot = ((NumberTag) parentSlotTag).asInt()
                        }
                    }

                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag chestItem = getCompoundTag(containerEntry, 'item')
                        Integer slot = null
                        if (containerEntry.containsKey('slot')) {
                            Object slotTag = containerEntry.get('slot')
                            if (slotTag instanceof NumberTag) {
                                slot = ((NumberTag) slotTag).asInt()
                            }
                        }
                        Integer compositeSlot = slot
                        if (parentSlot != null && compositeSlot != null) {
                            compositeSlot = (parentSlot * 1000) + compositeSlot
                        }
                        parseItem(chestItem, "${bookInfo} > copper_chest", x, y, z, compositeSlot)
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (if copper chests existed in pre-1.20.5)
                CompoundTag chestCompound = getCompoundTag(item, 'tag')
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, 'BlockEntityTag')
                getCompoundTagList(chestCompound2, 'Items').each { CompoundTag chestItem ->
                    // Old format: items have Slot; still prefix with parent slot (if known) for uniqueness.
                    Integer parentSlot = slotOverride
                    if (parentSlot == null && (item.containsKey('Slot') || item.containsKey('slot'))) {
                        Object parentSlotTag = item.get('Slot') ?: item.get('slot')
                        if (parentSlotTag instanceof NumberTag) {
                            parentSlot = ((NumberTag) parentSlotTag).asInt()
                        }
                    }

                    Integer innerSlot = null
                    if (chestItem.containsKey('Slot') || chestItem.containsKey('slot')) {
                        Object innerSlotTag = chestItem.get('Slot') ?: chestItem.get('slot')
                        if (innerSlotTag instanceof NumberTag) {
                            innerSlot = ((NumberTag) innerSlotTag).asInt()
                        }
                    }
                    Integer compositeSlot = innerSlot
                    if (parentSlot != null && compositeSlot != null) {
                        compositeSlot = (parentSlot * 1000) + compositeSlot
                    }
                    parseItem(chestItem, "${bookInfo} > copper_chest", x, y, z, compositeSlot)
                }
            }
        }

    // Note: Lecterns are handled as block entities with "Book" tag (not "Items")
    // They are scanned separately in the block entity processing code
    // Decorated pots are handled as block entities with "Items" tag (standard container)
    }

    // ========== Text Utility Methods (delegated to TextUtils) ==========
    static String sanitizeFilename(String name) {
        return TextUtils.sanitizeFilename(name)
    }

    /**
     * Read a written book (signed book with author and title)
     *
     * BOOK FORMAT CHANGES (1.20.5):
     * - Changed "tag" → "components"
     * - Changed book data location to "minecraft:written_book_content"
     * - Pages changed from string list to compound list with "raw"/"filtered" fields
     * - Title changed from plain string to filterable string (CompoundTag with "raw"/"filtered")
     * - Author remains a plain string in both formats
     *
     * GENERATION TAG:
     * - generation: Int tag indicating copy status (0=Original, 1=Copy of Original, 2=Copy of Copy, 3=Tattered)
     * - Defaults to 0 if not present
     * - Used for original-prioritization in duplicates folder
     */
    static void readWrittenBook(CompoundTag item, String bookInfo) {
        CompoundTag tag = null
        ListTag<?> pages = null
        String format = null

        // Try both old and new formats
        if (hasKey(item, 'tag')) {
            tag = getCompoundTag(item, 'tag')
            pages = getListTag(tag, 'pages')
            format = 'pre-1.20.5'
        } else if (hasKey(item, 'components')) {
            CompoundTag components = getCompoundTag(item, 'components')
            if (hasKey(components, 'minecraft:written_book_content')) {
                CompoundTag bookContent = getCompoundTag(components, 'minecraft:written_book_content')
                pages = getListTag(bookContent, 'pages')
                tag = bookContent
                format = '1.20.5+'
            }
        }

        if (!pages || pages.size() == 0) {
            LOGGER.debug("Written book has no pages (format: ${format})")
            return
        }

        // Extract generation tag: 0 = Original (default), 1 = Copy of Original, 2 = Copy of Copy, 3 = Tattered
        int generation = 0
        if (tag != null && hasKey(tag, 'generation')) {
            net.querz.nbt.tag.Tag<?> genTag = tag.get('generation')
            if (genTag instanceof NumberTag) {
                generation = ((NumberTag) genTag).asInt()
                // Clamp to valid range 0-3
                generation = Math.max(0, Math.min(3, generation))
            }
        }
        String generationName = GENERATION_NAMES[generation]

        // Check for duplicates with original-prioritization
        int pagesHash = pages.hashCode()
        boolean isDuplicate = bookHashes.contains(pagesHash)
        boolean shouldSwap = false
        Map<String, Object> existingBookInfo = null

        if (isDuplicate) {
            // Check if this book is more "original" than the existing one
            existingBookInfo = bookGenerationByHash[pagesHash]
            if (existingBookInfo != null && generation < (existingBookInfo.generation as int)) {
                // This book is more original - we should swap
                shouldSwap = true
                LOGGER.debug("Found more original version (${generationName} vs ${GENERATION_NAMES[existingBookInfo.generation as int]}) - will swap")
            } else {
                LOGGER.debug("Written book is a duplicate (${generationName}) - saving to .duplicates folder")
            }
        } else {
            // First occurrence - add to hash set
            bookHashes.add(pagesHash)
        }

        // Extract author and title - handle both old format (plain string) and new format (filterable string)
        // IMPORTANT: In 1.20.5+, author is a plain STRING, but title is a filterable string (CompoundTag with "raw"/"filtered")
        // In pre-1.20.5, both are plain strings
        String author = tag?.getString('author') ?: 'herobrine'
        String title = ''

        net.querz.nbt.tag.Tag<?> titleTag = tag?.get('title')
        if (titleTag instanceof CompoundTag) {
            // 1.20.5+ format: filterable string (compound with "raw"/"filtered" fields)
            title = ((CompoundTag) titleTag).getString('raw') ?: ((CompoundTag) titleTag).getString('filtered') ?: 'untitled'
        } else if (titleTag instanceof StringTag) {
            // Pre-1.20.5 format: plain string
            title = tag.getString('title')
        }

        LOGGER.debug("Extracted written book: \"${title}\" by ${author} (${pages.size()} pages, format: ${format}, generation: ${generationName})")

        bookCounter++

        // Extract coordinates and location info
        List<Object> locationInfo = extractLocationInfo(bookInfo)
        String locationForFilename = locationInfo[1]
        Integer x = locationInfo[2]
        Integer y = locationInfo[3]
        Integer z = locationInfo[4]
        String foundWhere = locationInfo[5]

        // Store book metadata for summary table
        bookMetadataList.add([
            title: title ?: 'untitled',
            author: author ?: '',
            pageCount: pages.size(),
            foundWhere: foundWhere,
            coordinates: (x != null && y != null && z != null) ? "${x}, ${y}, ${z}" : '',
            generation: generationName
        ])

        // Collect all page content for CSV
        List<String> allPageContents = []
        (0..<pages.size()).each { int pc ->
            String pageText = extractPageText(pages, pc)
            if (pageText) {
                allPageContents.add(extractTextContent(pageText))
            }
        }
        String concatenatedPages = allPageContents.join(' ')

        // Add to CSV data
        bookCsvData.add([
            x: x,
            y: y,
            z: z,
            foundWhere: foundWhere,
            bookname: title ?: 'untitled',
            author: author ?: 'unknown',
            pageCount: pages.size(),
            generation: generation,
            generationName: generationName,
            pages: concatenatedPages
        ])

        // New filename format: Title_(PageCount)_by_Author~location~coords.stendhal
        // Remove sequence number, use tilde separator
        String titlePart = sanitizeFilename(title ?: 'untitled')
        String authorPart = sanitizeFilename(author ?: 'unknown')
        String locationPart = sanitizeFilename(locationForFilename)

        String baseFilename
        if (x != null && y != null && z != null) {
            // For positioned blocks: Title_(PageCount)_by_Author~block_type~X_Y_Z.stendhal
            baseFilename = "${titlePart}_(${pages.size()})_by_${authorPart}~${locationPart}~${x}_${y}_${z}"
        } else {
            // For inventory/ender chest: Title_(PageCount)_by_Author~container_type.stendhal
            baseFilename = "${titlePart}_(${pages.size()})_by_${authorPart}~${locationPart}"
        }

        // Original-prioritization logic:
        // If shouldSwap is true, the existing book in main folder is a copy and this book is more original
        // We need to: 1) Move existing book to duplicates, 2) Put this book in main folder
        String targetFolder
        if (shouldSwap) {
            // Move existing book from booksFolder to duplicatesFolder
            String existingFilePath = existingBookInfo.filePath as String
            File existingFile = new File(existingFilePath)
            if (existingFile.exists()) {
                File newDuplicateLocation = new File(resolveMaybeRelativeToWorld(duplicatesFolder), existingFile.name)
                // Ensure unique filename in duplicates folder
                int dupCounter = 2
                while (newDuplicateLocation.exists()) {
                    String nameWithoutExt = existingFile.name.replace('.stendhal', '')
                    newDuplicateLocation = new File(resolveMaybeRelativeToWorld(duplicatesFolder), "${nameWithoutExt}_${dupCounter}.stendhal")
                    dupCounter++
                }
                existingFile.renameTo(newDuplicateLocation)
                LOGGER.debug("Moved existing copy to duplicates: ${newDuplicateLocation.name}")
            }
            // This more original book goes to main folder
            targetFolder = booksFolder
        } else if (isDuplicate) {
            // Regular duplicate goes to duplicates folder
            targetFolder = duplicatesFolder
        } else {
            // First occurrence goes to main folder
            targetFolder = booksFolder
        }

        // Ensure filename uniqueness by appending counter if file exists
        String filename = "${baseFilename}.stendhal"
        File bookFile = new File(resolveMaybeRelativeToWorld(targetFolder), filename)
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.stendhal"
            bookFile = new File(resolveMaybeRelativeToWorld(targetFolder), filename)
            counter++
        }

        // Write .stendhal file (preserve formatting codes)
        bookFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine("title: ${title ?: 'Untitled'}")
            writer.writeLine("author: ${author ?: ''}")
            writer.writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = extractTextContentPreserveFormatting(pageText)
                writer.write('#- ')
                writer.writeLine(pageContent)
            }
        }

        // Write to combined books file in Stendhal format with VSCode regions
        String filenameWithoutExtension = filename.replace('.stendhal', '')
        String regionDelimiter = '─' * 40
        combinedBooksWriter?.with {
            writeLine("#region ${regionDelimiter} ${filenameWithoutExtension}")
            writeLine("title: ${title ?: 'Untitled'}")
            writeLine("author: ${author ?: ''}")
            writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = extractTextContentPreserveFormatting(pageText)
                writeLine('#- ')
                writeLine(pageContent)
            }

            writeLine('')
            writeLine("#endregion ${filenameWithoutExtension}")
            writeLine('')
            flush() // Flush immediately to ensure streaming output
        }

        // Update generation tracking for original-prioritization
        // Track this book's generation and file path so we can swap if a more original version is found later
        if (!isDuplicate || shouldSwap) {
            // This is either the first occurrence or we just swapped in a more original version
            bookGenerationByHash[pagesHash] = [
                generation: generation,
                filePath: bookFile.absolutePath
            ]
        }

        // Write to mcfunction file - pass raw NBT pages to preserve JSON text components
        // IMPORTANT: Pass the raw NBT ListTag directly, not extracted text
        if (pages && pages.size() > 0) {
            writeBookToMcfunction(title, author, pages, generation)
        }
    }

    /**
     * Read a writable book (unsigned book & quill)
     *
     * BOOK FORMAT CHANGES (1.20.5):
     * - Changed "tag" → "components"
     * - Changed book data location to "minecraft:writable_book_content"
     * - Pages changed from string list to compound list with "raw"/"filtered" fields containing plain strings
     */
    static void readWritableBook(CompoundTag item, String bookInfo) {
        ListTag<?> pages = null
        String format = null

        if (hasKey(item, 'tag')) {
            CompoundTag tag = getCompoundTag(item, 'tag')
            pages = getListTag(tag, 'pages')
            format = 'pre-1.20.5'
        } else if (hasKey(item, 'components')) {
            CompoundTag components = getCompoundTag(item, 'components')
            if (hasKey(components, 'minecraft:writable_book_content')) {
                CompoundTag bookContent = getCompoundTag(components, 'minecraft:writable_book_content')
                pages = getListTag(bookContent, 'pages')
                format = '1.20.5+'
            }
        }

        if (!pages || pages.size() == 0) {
            LOGGER.debug("Writable book has no pages (format: ${format})")
            return
        }

        boolean isDuplicate = !bookHashes.add(pages.hashCode())
        if (isDuplicate) {
            LOGGER.debug('Writable book is a duplicate - saving to .duplicates folder')
        }

        LOGGER.debug("Extracted writable book (${pages.size()} pages, format: ${format})")

        bookCounter++

        // Extract coordinates and location info
        List<Object> locationInfo = extractLocationInfo(bookInfo)
        String locationForFilename = locationInfo[1]
        Integer x = locationInfo[2]
        Integer y = locationInfo[3]
        Integer z = locationInfo[4]
        String foundWhere = locationInfo[5]

        // Store book metadata for summary table (writable books have no title/author)
        // Writable books are always "Original" since they can't be copied
        bookMetadataList.add([
            title: 'Writable Book',
            author: '',
            pageCount: pages.size(),
            foundWhere: foundWhere,
            coordinates: (x != null && y != null && z != null) ? "${x}, ${y}, ${z}" : '',
            generation: 'Original'
        ])

        // Collect all page content for CSV
        List<String> allPageContents = []
        (0..<pages.size()).each { int pc ->
            String pageText = extractPageText(pages, pc)
            if (pageText) {
                allPageContents.add(removeTextFormatting(pageText))
            }
        }
        String concatenatedPages = allPageContents.join(' ')

        // Add to CSV data (writable books are always generation 0/Original)
        bookCsvData.add([
            x: x,
            y: y,
            z: z,
            foundWhere: foundWhere,
            bookname: 'Writable Book',
            author: '',
            pageCount: pages.size(),
            generation: 0,
            generationName: 'Original',
            pages: concatenatedPages
        ])

        // New filename format: writable_book_(PageCount)~location~coords.stendhal
        String locationPart = sanitizeFilename(locationForFilename)

        String baseFilename
        if (x != null && y != null && z != null) {
            baseFilename = "writable_book_(${pages.size()})~${locationPart}~${x}_${y}_${z}"
        } else {
            baseFilename = "writable_book_(${pages.size()})~${locationPart}"
        }

        // Ensure filename uniqueness by appending counter if file exists
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        String filename = "${baseFilename}.stendhal"
        File bookFile = new File(resolveMaybeRelativeToWorld(targetFolder), filename)
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.stendhal"
            bookFile = new File(resolveMaybeRelativeToWorld(targetFolder), filename)
            counter++
        }

        // Write .stendhal file (preserve formatting codes)
        bookFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('title: Writable Book')
            writer.writeLine('author: ')
            writer.writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                // For .stendhal files, always preserve formatting codes
                String pageContent = pageText
                writer.write('#- ')
                writer.writeLine(pageContent)
            }
        }

        // Write to combined books file in Stendhal format with VSCode regions
        String filenameWithoutExtension = filename.replace('.stendhal', '')
        String regionDelimiter = '─' * 40
        combinedBooksWriter?.with {
            writeLine("#region ${regionDelimiter} ${filenameWithoutExtension}")
            writeLine('title: Writable Book')
            writeLine('author: ')
            writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                // For .stendhal files, always preserve formatting codes
                String pageContent = pageText
                writeLine('#- ')
                writeLine(pageContent)
            }

            writeLine('')
            writeLine("#endregion ${filenameWithoutExtension}")
            writeLine('')
            flush() // Flush immediately to ensure streaming output
        }

        // Write to mcfunction file - pass raw NBT pages to preserve formatting
        // Writable books (book_and_quill) always have generation=0 since they can't be copied
        if (pages && pages.size() > 0) {
            writeBookToMcfunction('Writable Book', '', pages, 0)
        }
    }

    /**
     * Extract location information from bookInfo string
     * Returns: [coordsForFilename, locationForFilename, x, y, z, foundWhere]
     */
    static List<Object> extractLocationInfo(String bookInfo) {
        String coordsForFilename = 'unknown'
        String locationForFilename = 'unknown'
        Integer x = null
        Integer y = null
        Integer z = null
        String foundWhere = 'unknown'

        try {
            if (bookInfo.contains(' at (')) {
                int atIndex = bookInfo.indexOf(' at (')
                int endParenIndex = bookInfo.indexOf(')', atIndex)
                if (endParenIndex > atIndex && atIndex + 5 < bookInfo.length()) {
                    String coords = bookInfo.substring(atIndex + 5, endParenIndex)
                    coordsForFilename = coords.replace(' ', '_')

                    // Parse individual coordinates
                    String[] coordParts = coords.split(' ')
                    if (coordParts.length >= 3) {
                        try {
                            x = Integer.parseInt(coordParts[0])
                            y = Integer.parseInt(coordParts[1])
                            z = Integer.parseInt(coordParts[2])
                        } catch (NumberFormatException e) {
                            LOGGER.debug("Failed to parse coordinates: ${coords}")
                        }
                    }
                }

                if (bookInfo.contains('Inside ')) {
                    int insideIndex = bookInfo.indexOf('Inside ') + 7
                    if (insideIndex < atIndex && insideIndex < bookInfo.length()) {
                        locationForFilename = bookInfo.substring(insideIndex, atIndex).trim()
                        foundWhere = locationForFilename
                    }
                } else if (bookInfo.contains('In ')) {
                    int inIndex = bookInfo.indexOf('In ') + 3
                    if (inIndex < atIndex && inIndex < bookInfo.length()) {
                        locationForFilename = bookInfo.substring(inIndex, atIndex).trim()
                        foundWhere = locationForFilename
                    }
                }
            } else if (bookInfo.contains('Inventory of player')) {
                // Extract player name from "Inventory of player <uuid>.dat"
                String playerName = extractPlayerName(bookInfo, 'Inventory of player ')
                coordsForFilename = "player_${playerName}_inventory"
                locationForFilename = "player_${playerName}_inventory"
                foundWhere = "player_${playerName}_inventory"
            } else if (bookInfo.contains('Ender Chest of player')) {
                // Extract player name from "Ender Chest of player <uuid>.dat"
                String playerName = extractPlayerName(bookInfo, 'Ender Chest of player ')
                coordsForFilename = "player_${playerName}_enderchest"
                locationForFilename = "player_${playerName}_enderchest"
                foundWhere = "player_${playerName}_enderchest"
            }
        } catch (StringIndexOutOfBoundsException e) {
            LOGGER.warn("Failed to parse bookInfo for filename: ${bookInfo}", e)
        }

        return [coordsForFilename, locationForFilename, x, y, z, foundWhere]
    }

    /**
     * Extract player name from bookInfo string
     * Example: "Inventory of player abc123-def456.dat" -> "abc123-def456"
     */
    static String extractPlayerName(String bookInfo, String prefix) {
        return TextUtils.extractPlayerName(bookInfo, prefix)
    }

    /**
     * Extract page text from a book page (handles both string and compound formats)
     *
     * PAGE FORMAT CHANGES (1.20.5):
     * - Pre-1.20.5: Pages are string list
     * - 1.20.5+: Pages are compound list with "raw"/"filtered" fields
     */
    static String extractPageText(ListTag<?> pages, int index) {
        return TextUtils.extractPageText(pages, index)
    }

    static String extractTextContent(String pageText) {
        return TextUtils.extractTextContent(pageText, removeFormatting)
    }

    /**
     * Extract text content from page text while preserving formatting codes
     * Used for .stendhal files
     */
    static String extractTextContentPreserveFormatting(String pageText) {
        return TextUtils.extractTextContentPreserveFormatting(pageText)
    }

    /**
     * Extract coordinates from signInfo string
     * signInfo format: "Chunk [x, z]\t(X Y Z)\t\t"
     * Returns: [x, y, z, blockId]
     */
    static List<Object> extractSignCoordinates(String signInfo) {
        return TextUtils.extractSignCoordinates(signInfo)
    }

    /**
     * Pad sign line to exactly 15 characters (Minecraft's max sign line width)
     */
    static String padSignLine(String text) {
        return TextUtils.padSignLine(text)
    }

    /**
     * Parse sign in old format (Text1-Text4 fields, used before 1.20)
     */
    static void parseSign(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
        LOGGER.debug('parseSign() - Extracting text from old format sign')

        // Get the StringTag objects and extract their values
        String text1 = ((StringTag) tileEntity.get('Text1')).value
        String text2 = ((StringTag) tileEntity.get('Text2')).value
        String text3 = ((StringTag) tileEntity.get('Text3')).value
        String text4 = ((StringTag) tileEntity.get('Text4')).value

        String hash = signInfo + text1 + text2 + text3 + text4
        if (!signHashes.add(hash)) {
            LOGGER.debug('Sign is duplicate, skipping')
            return
        }

        // Extract sign text
        String line1 = extractSignLineText(text1)
        String line2 = extractSignLineText(text2)
        String line3 = extractSignLineText(text3)
        String line4 = extractSignLineText(text4)

        // Check if sign is completely empty
        if (line1.empty && line2.empty && line3.empty && line4.empty) {
            emptySignsRemoved++
            List<Object> coords = extractSignCoordinates(signInfo)
            LOGGER.debug("Removed empty sign at coordinates: ${coords[0]}, ${coords[1]}, ${coords[2]}")
            return
        }

        // Pad lines to 15 characters
        String paddedLine1 = padSignLine(line1)
        String paddedLine2 = padSignLine(line2)
        String paddedLine3 = padSignLine(line3)
        String paddedLine4 = padSignLine(line4)

        // Write to file with delimiter between lines
        // Write sign to mcfunction files
        writeSignToMcfunction([line1, line2, line3, line4], signInfo)

        signWriter.with {
            write(signInfo)
            write(paddedLine1)
            write('│')  // Unicode box drawing pipe delimiter
            write(paddedLine2)
            write('│')
            write(paddedLine3)
            write('│')
            write(paddedLine4)
            newLine()
        }

        // Collect CSV data
        List<Object> coords = extractSignCoordinates(signInfo)
        String blockId = tileEntity.getString('id')
        signCsvData.add([
            x: coords[0],
            y: coords[1],
            z: coords[2],
            foundWhere: blockId,
            signText: "${paddedLine1}│${paddedLine2}│${paddedLine3}│${paddedLine4}".trim(),
            line1: paddedLine1,
            line2: paddedLine2,
            line3: paddedLine3,
            line4: paddedLine4
        ])
    }

    /**
     * Parse sign in new format (front_text/back_text fields, introduced in 1.20)
     * The front_text/back_text compounds contain a "messages" array of 4 text component JSON strings
     */
    static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
        LOGGER.debug('parseSignNew() - Extracting text from new format sign')

        CompoundTag frontText = getCompoundTag(tileEntity, 'front_text')
        ListTag<?> frontMessages = getListTag(frontText, 'messages')

        if (frontMessages.size() == 0) {
            LOGGER.debug('No front messages found, returning')
            return
        }

        List<String> frontSignLines = (0..3).collect { int i -> getStringAt(frontMessages, i) }

        // Also read back_text if it exists
        List<String> backSignLines = null
        CompoundTag backText = getCompoundTag(tileEntity, 'back_text')
        if (backText) {
            ListTag<?> backMessages = getListTag(backText, 'messages')
            if (backMessages && backMessages.size() > 0) {
                backSignLines = (0..3).collect { int i -> getStringAt(backMessages, i) }
            }
        }

        String hash = signInfo + frontSignLines.join('')
        if (!signHashes.add(hash)) {
            LOGGER.debug('Sign is duplicate, skipping')
            return
        }

        // Extract sign text
        List<String> extractedFrontLines = frontSignLines.collect { String line -> extractSignLineText(line) }
        List<String> extractedBackLines = backSignLines ? backSignLines.collect { String line -> extractSignLineText(line) } : null

        // Check if sign is completely empty
        if (extractedFrontLines.every { String line -> line.empty }) {
            emptySignsRemoved++
            List<Object> coords = extractSignCoordinates(signInfo)
            LOGGER.debug("Removed empty sign at coordinates: ${coords[0]}, ${coords[1]}, ${coords[2]}")
            return
    }

        // Pad lines to 15 characters and write to file with delimiter
        List<String> paddedLines = extractedFrontLines.collect { String text -> padSignLine(text) }

        // Write sign to mcfunction files (preserve back_text if it exists)
        writeSignToMcfunction(extractedFrontLines, signInfo, extractedBackLines)
        signWriter.write(signInfo)
        paddedLines.eachWithIndex { String text, int index ->
            signWriter.write(text)
            if (index < 3) {
                signWriter.write('│')  // Unicode box drawing pipe delimiter
            }
        }
        signWriter.newLine()

        // Collect CSV data
        List<Object> coords = extractSignCoordinates(signInfo)
        String blockId = tileEntity.getString('id')
        signCsvData.add([
            x: coords[0],
            y: coords[1],
            z: coords[2],
            foundWhere: blockId,
            signText: paddedLines.join('│').trim(),
            line1: paddedLines.size() > 0 ? paddedLines[0] : '',
            line2: paddedLines.size() > 1 ? paddedLines[1] : '',
            line3: paddedLines.size() > 2 ? paddedLines[2] : '',
            line4: paddedLines.size() > 3 ? paddedLines[3] : ''
        ])
        }

    static String extractSignLineText(String line) {
        return TextUtils.extractSignLineText(line)
    }

    static String removeTextFormatting(String text) {
        return TextUtils.removeTextFormattingIfEnabled(text, removeFormatting)
    }

    // ========== NBT Helper Methods (delegated to NbtUtils) ==========
    static CompoundTag readCompressedNBT(File file) {
        return NbtUtils.readCompressedNBT(file)
    }

    static boolean hasKey(CompoundTag tag, String key) {
        return NbtUtils.hasKey(tag, key)
    }

    static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        return NbtUtils.getCompoundTag(tag, key)
    }

    static ListTag<CompoundTag> getCompoundTagList(CompoundTag tag, String key) {
        return NbtUtils.getCompoundTagList(tag, key)
    }

    static ListTag<?> getListTag(CompoundTag tag, String key) {
        return NbtUtils.getListTag(tag, key)
    }

    static double getDoubleAt(ListTag<?> list, int index) {
        return NbtUtils.getDoubleAt(list, index)
    }

    static String getStringAt(ListTag<?> list, int index) {
        return NbtUtils.getStringAt(list, index)
    }

    static JSONObject convertNbtToJson(CompoundTag tag) {
        return NbtUtils.convertNbtToJson(tag)
    }

    static JSONArray convertNbtListToJsonArray(ListTag<?> list) {
        return NbtUtils.convertNbtListToJsonArray(list)
    }

    static String getStringFrom(CompoundTag tag, String key) {
        return NbtUtils.getStringFrom(tag, key)
    }

    static CompoundTag getCompoundAt(ListTag<?> list, int index) {
        return NbtUtils.getCompoundAt(list, index)
    }

    static boolean isStringList(ListTag<?> list) {
        return NbtUtils.isStringList(list)
    }

    static boolean isCompoundList(ListTag<?> list) {
        return NbtUtils.isCompoundList(list)
    }

    }
