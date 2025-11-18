/* groovylint-disable ClassSize */
import com.github.freva.asciitable.AsciiTable
import com.github.freva.asciitable.Column
import com.github.freva.asciitable.HorizontalAlign
import groovy.json.JsonSlurper
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import net.querz.mca.LoadFlags
import net.querz.mca.MCAUtil
import net.querz.nbt.io.NBTUtil
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.NumberTag
import net.querz.nbt.tag.StringTag
import org.apache.commons.lang3.time.DurationFormatUtils
import org.json.JSONArray
import org.json.JSONException
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
    private static final String[] COLOR_CODES = ['\u00A70', '\u00A71', '\u00A72', '\u00A73', '\u00A74', '\u00A75',
                                                 '\u00A76', '\u00A77', '\u00A78', '\u00A79', '\u00A7a', '\u00A7b',
                                                 '\u00A7c', '\u00A7d', '\u00A7e', '\u00A7f', '\u00A7k', '\u00A7l',
                                                 '\u00A7m', '\u00A7n', '\u00A7o', '\u00A7r']

    static String baseDirectory = System.getProperty('user.dir')
    static String outputFolder, booksFolder, duplicatesFolder, dateStamp
    static String outputFolderParent  // Top-level output folder (e.g., "ReadBooks") without date stamp

    static Set<Integer> bookHashes = [] as Set
    static Set<String> signHashes = [] as Set
    
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
    
    // 16 Minecraft shulker box colors (deterministic mapping)
    private static final List<String> SHULKER_COLORS = [
        'white', 'orange', 'magenta', 'light_blue',
        'yellow', 'lime', 'pink', 'gray',
        'light_gray', 'cyan', 'purple', 'blue',
        'brown', 'green', 'red', 'black'
    ]

    @Option(names = ['-w', '--world'], description = 'Specify custom world directory')
    static String customWorldDirectory

    @Option(names = ['-o', '--output'], description = 'Specify custom output directory')
    static String customOutputDirectory

    @Option(names = ['--remove-formatting'], description = 'Remove Minecraft text formatting codes (§ codes) from output files (default: false)', defaultValue = 'false')
    static boolean removeFormatting = false

    static void main(String[] args) {
        // Smart detection: GUI mode if no args (double-clicked JAR) or --gui flag
        if (shouldUseGui(args)) {
            println "Starting GUI mode..."
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
        if (args.length == 0) {
            return true
        }

        // Explicit GUI flag
        if (args.contains('--gui') || args.contains('-g')) {
            return true
        }

        // Otherwise use CLI
        return false
    }

    @Override
    void run() {
        try {
            runExtraction()
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Error during extraction: ${e.message}", e)
            throw new RuntimeException('Extraction failed', e)
        }
    }

    /**
     * Map author name to a shulker box color deterministically using hash
     * Uses author name's hash code to select one of 16 colors consistently
     */
    static String getShulkerColorForAuthor(String author) {
        if (!author || author.trim().isEmpty()) {
            author = 'Unknown'
        }
        int colorIndex = Math.abs(author.hashCode() % SHULKER_COLORS.size())
        return SHULKER_COLORS[colorIndex]
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
        } catch (Exception e) {
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
        File stateFile = new File(stateFileDir, ".failed_regions_state.json")
        
        try {
            // Load existing state
            Map<String, Object> stateData = [:]
            if (stateFile.exists()) {
                try {
                    String content = stateFile.text
                    stateData = new JsonSlurper().parseText(content) as Map<String, Object>
                } catch (Exception e) {
                    LOGGER.warn("Could not parse existing state file, starting fresh: ${e.message}")
                    stateData = [:]
                }
            }
            
            // Update state for current world: add failed regions, remove recovered ones
            if (failedRegionsByWorld.containsKey(worldFolderName)) {
                Set<String> failedRegions = failedRegionsByWorld[worldFolderName]
                failedRegions.removeAll(recoveredRegions)  // Remove regions that recovered
                
                if (failedRegions.isEmpty()) {
                    stateData.remove(worldFolderName)
                    LOGGER.info("All previously failed regions recovered! Removing state for world: ${worldFolderName}")
                } else {
                    stateData[worldFolderName] = failedRegions.toList().sort()
                    LOGGER.debug("Saved ${failedRegions.size()} failed regions for world: ${worldFolderName}")
                }
            }
            
            // Write state file as JSON
            if (stateData.isEmpty()) {
                if (stateFile.exists()) {
                    stateFile.delete()
                }
            } else {
                stateFile.withWriter('UTF-8') { BufferedWriter writer ->
                    writer.write(new groovy.json.JsonOutput().toJson(stateData))
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save state file: ${e.message}", e)
        }
    }

    /**
     * Create datapack directory structure for a specific Minecraft version
     *
     * Structure for 1.21+:
     * readbooks_datapack_VERSION/
     * ├── pack.mcmeta
     * └── data/
     *     └── readbooks/
     *         └── function/
     *             ├── books.mcfunction
     *             └── signs.mcfunction
     *
     * Structure for pre-1.21:
     * readbooks_datapack_VERSION/
     * ├── pack.mcmeta
     * └── data/
     *     └── readbooks/
     *         └── functions/  (note: plural)
     *             ├── books.mcfunction
     *             └── signs.mcfunction
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return The function directory File object
     */
    static File createDatapackStructure(String version) {
        String datapackName = "readbooks_datapack_${version}"
        File datapackRoot = new File(baseDirectory, "${outputFolder}${File.separator}${datapackName}")
        File dataFolder = new File(datapackRoot, "data")
        File namespaceFolder = new File(dataFolder, "readbooks")

        // CRITICAL: Pre-1.21 uses "functions" (plural), 1.21+ uses "function" (singular)
        // This changed in Minecraft Java Edition 1.21 snapshot 24w21a
        String functionDirName = (version == '1_21') ? 'function' : 'functions'
        File functionFolder = new File(namespaceFolder, functionDirName)

        // Create all directories
        functionFolder.mkdirs()

        LOGGER.debug("Created datapack structure: ${datapackRoot.absolutePath} with ${functionDirName}/ directory")
        return functionFolder
    }

    /**
     * Create pack.mcmeta file for a datapack
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @param packFormat The pack_format number for this Minecraft version
     * @param description Human-readable description of the datapack
     */
    static void createPackMcmeta(String version, int packFormat, String description) {
        String datapackName = "readbooks_datapack_${version}"
        File datapackRoot = new File(baseDirectory, "${outputFolder}${File.separator}${datapackName}")
        File packMcmetaFile = new File(datapackRoot, "pack.mcmeta")

        // Create pack.mcmeta JSON content
        Map<String, Object> packData = [
            pack: [
                pack_format: packFormat,
                description: description
            ]
        ]

        packMcmetaFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.write(new groovy.json.JsonBuilder(packData).toPrettyString())
        }

        LOGGER.debug("Created pack.mcmeta for ${datapackName} with pack_format ${packFormat}")
    }

    /**
     * Get pack_format number for a Minecraft version
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return The appropriate pack_format number
     */
    static int getPackFormat(String version) {
        switch (version) {
            case '1_13':
            case '1_14':
                return 4  // Minecraft 1.13-1.14.4
            case '1_20':
                return 15  // Minecraft 1.20-1.20.4
            case '1_20_5':
                return 41  // Minecraft 1.20.5-1.20.6
            case '1_21':
                return 48  // Minecraft 1.21+
            default:
                LOGGER.warn("Unknown version ${version}, defaulting to pack_format 48")
                return 48
        }
    }

    /**
     * Get human-readable Minecraft version range for description
     *
     * IMPORTANT: These descriptions reflect COMMAND COMPATIBILITY, not pack_format compatibility.
     * The datapacks use specific pack_format numbers but the commands inside work across
     * broader version ranges due to command syntax changes being independent of pack format.
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return Human-readable version string
     */
    static String getVersionDescription(String version) {
        switch (version) {
            case '1_13':
                return 'Minecraft 1.13-1.14.3 (uses pack_format 4, functions/ directory)'
            case '1_14':
                return 'Minecraft 1.14.4-1.19.4 (uses pack_format 4, functions/ directory)'
            case '1_20':
                return 'Minecraft 1.20-1.20.4 (uses pack_format 15, functions/ directory)'
            case '1_20_5':
                return 'Minecraft 1.20.5-1.20.6 (uses pack_format 41, functions/ directory)'
            case '1_21':
                return 'Minecraft 1.21+ (uses pack_format 48, function/ directory)'
            default:
                return "Minecraft ${version}"
        }
    }

    static void runExtraction() {
        // Reset state
        [bookHashes, signHashes, booksByContainerType, booksByLocationType, bookMetadataList, bookCsvData, signCsvData, booksByAuthor, signsByHash].each { collection -> collection.clear() }
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
            outputFolderParent = "ReadBooks"
        }

        booksFolder = "${outputFolder}${File.separator}books"
        duplicatesFolder = "${booksFolder}${File.separator}.duplicates"

        // Configure logging - dynamically add file appender
        addFileAppender(new File(baseDirectory, "${outputFolder}${File.separator}logs.txt").absolutePath)

        // Create directories
        [outputFolder, booksFolder, duplicatesFolder].each { String folder ->
            new File(baseDirectory, folder).mkdirs()
        }

        LOGGER.info('=' * 80)
        LOGGER.info('ReadSignsAndBooks - Minecraft World Data Extractor')
        LOGGER.info("Started at: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())}")
        LOGGER.info("World directory: ${baseDirectory}")
        LOGGER.info("Output folder: ${outputFolder}")
        LOGGER.info('=' * 80)
        
        // Load state file with previously failed regions
        loadFailedRegionsState()
        
        // Print message about suppressed regions if any known failures exist
        String worldFolderName = new File(baseDirectory).name
        if (failedRegionsByWorld.containsKey(worldFolderName)) {
            Set<String> failedRegions = failedRegionsByWorld[worldFolderName]
            LOGGER.info("")
            LOGGER.info("⚠️  NOTICE: ${failedRegions.size()} region file(s) have previously failed to read. Error messages for these known problematic regions will be suppressed in this run's output:")
            failedRegions.toList().sort().each { String regionFile ->
                LOGGER.info("  - ${regionFile}")
            }
            LOGGER.info("")
        }

        long startTime = System.currentTimeMillis()

        try {
            combinedBooksWriter = new File(baseDirectory, "${outputFolder}${File.separator}all_books.txt").newWriter()

            // Create datapack structures and initialize mcfunction writers for each Minecraft version
            LOGGER.info("Creating Minecraft datapacks...")
            ['1_13', '1_14', '1_20_5', '1_21'].each { String version ->
                // Create datapack directory structure
                File functionFolder = createDatapackStructure(version)

                // Create pack.mcmeta with appropriate pack_format
                int packFormat = getPackFormat(version)
                String description = "ReadSignsAndBooks extracted content for ${getVersionDescription(version)}"
                createPackMcmeta(version, packFormat, description)

                // Initialize book mcfunction writer in datapack/data/readbooks/function/books.mcfunction
                File booksFile = new File(functionFolder, 'books.mcfunction')
                mcfunctionWriters[version] = booksFile.newWriter('UTF-8')

                // Initialize signs mcfunction writer in datapack/data/readbooks/function/signs.mcfunction
                File signsFile = new File(functionFolder, 'signs.mcfunction')
                signsMcfunctionWriters[version] = signsFile.newWriter('UTF-8')

                LOGGER.info("  ✓ Created datapack for ${getVersionDescription(version)} (pack_format ${packFormat})")
            }
            LOGGER.info("Datapacks created successfully!")

            readPlayerData()
            readSignsAndBooks()
            readEntities()
            combinedBooksWriter?.close()

            // Generate shulker box commands organized by author
            writeShulkerBoxesToMcfunction()

            // Flush and close all mcfunction writers
            mcfunctionWriters.values().each {
                it?.flush()
                it?.close()
            }
            // Flush and close all sign mcfunction writers
            signsMcfunctionWriters.values().each {
                it?.flush()
                it?.close()
            }


            // Write CSV exports
            writeBooksCSV()
            writeSignsCSV()

            long elapsed = System.currentTimeMillis() - startTime
            printSummaryStatistics(elapsed)
            LOGGER.info("${elapsed / 1000} seconds to complete.")
            
            // Save state file with any new failures discovered
            saveFailedRegionsState()
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Fatal error: ${e.message}", e)
            combinedBooksWriter?.close()
            mcfunctionWriters.values().each { it?.close() }
            // Still save state file even if extraction had errors
            saveFailedRegionsState()
            throw e
        }
    }

    /**
     * Dynamically add a file appender to Logback at runtime
     * This allows us to avoid creating log files until extraction actually runs
     */
    static void addFileAppender(String logFilePath) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()

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

    /**
     * Write books data to CSV file
     * CSV format: X,Y,Z,FoundWhere,Bookname,Author,PageCount,Pages
     */
    static void writeBooksCSV() {
        File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}all_books.csv")
        LOGGER.info("Writing books CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,Bookname,Author,PageCount,Pages')

            // Write data
            bookCsvData.each { Map<String, Object> book ->
                String x = book.x != null ? book.x.toString() : '0'
                String y = book.y != null ? book.y.toString() : '0'
                String z = book.z != null ? book.z.toString() : '0'
                String foundWhere = escapeCsvField(book.foundWhere?.toString() ?: 'undefined')
                String bookname = escapeCsvField(book.bookname?.toString() ?: 'undefined')
                String author = escapeCsvField(book.author?.toString() ?: 'undefined')
                String pageCount = book.pageCount != null ? book.pageCount.toString() : '0'
                String pages = escapeCsvField(book.pages?.toString() ?: 'undefined')

                writer.writeLine("${x},${y},${z},${foundWhere},${bookname},${author},${pageCount},${pages}")
            }
        }

        LOGGER.info("Books CSV written successfully with ${bookCsvData.size()} entries")
    }

    /**
     * Write signs data to CSV file
     * CSV format: X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4
     */
    static void writeSignsCSV() {
        File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}all_signs.csv")
        LOGGER.info("Writing signs CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4')

            // Write data
            signCsvData.each { Map<String, Object> sign ->
                String x = sign.x?.toString() ?: '0'
                String y = sign.y?.toString() ?: '0'
                String z = sign.z?.toString() ?: '0'
                String foundWhere = escapeCsvField(sign.foundWhere?.toString() ?: 'unknown')
                String signText = escapeCsvField(sign.signText?.toString() ?: 'undefined')
                String line1 = escapeCsvField(sign.line1?.toString() ?: '')
                String line2 = escapeCsvField(sign.line2?.toString() ?: '')
                String line3 = escapeCsvField(sign.line3?.toString() ?: '')
                String line4 = escapeCsvField(sign.line4?.toString() ?: '')

                writer.writeLine("${x},${y},${z},${foundWhere},${signText},${line1},${line2},${line3},${line4}")
            }
        }

        LOGGER.info("Signs CSV written successfully with ${signCsvData.size()} entries")
    }

    /**
     * Escape CSV field by wrapping in quotes if it contains comma, newline, or quote
     */
    static String escapeCsvField(String field) {
        if (!field) {
            return ''
        }

        // Replace newlines with space for readability
        String escaped = field.replace('\n', ' ').replace('\r', ' ')

        // If field contains comma, quote, or was modified, wrap in quotes
        if (escaped.contains(',') || escaped.contains('"') || escaped != field) {
            // Escape quotes by doubling them
            escaped = escaped.replace('"', '""')
            return "\"${escaped}\""
        }

        return escaped
    }

    /**
     * Escape text for Minecraft commands based on version
     * @param text The text to escape
     * @param version The Minecraft version ('1_13', '1_14', '1_20_5', '1_21')
     */
    static String escapeForMinecraftCommand(String text, String version) {
        if (!text) {
            return ''
        }

        String escaped = text

        // Version-specific escaping
        if (version in ['1_13', '1_14']) {
            // Older versions need double backslash escaping
            escaped = escaped.replace('\\', '\\\\\\\\')
            escaped = escaped.replace('"', '\\\\"')
            escaped = escaped.replace("'", "\\'")
            escaped = escaped.replace('\n', '\\\\n')
        } else {
            // 1.20.5+ uses single backslash escaping
            escaped = escaped.replace('\\', '\\\\')
            escaped = escaped.replace('"', '\\"')
            escaped = escaped.replace("'", "\\'")
            escaped = escaped.replace('\n', '\\n')
        }

        return escaped
    }

    /**
     * Generate book NBT tag (pre-1.20.5 format)
     * Used for creating book entries in shulker boxes for versions 1.13-1.20.4
     * Now uses raw NBT ListTag to preserve JSON text components
     */
    static String generateBookNBT(String title, String author, ListTag<?> pages, String version) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)
        
        String pagesStr = (0..<pages.size()).collect { int i ->
            String rawText = getStringAt(pages, i)
            // Convert § formatting codes to JSON text components if needed
            String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
            // Escape backslashes, single quotes, AND newlines for NBT syntax
            String escaped = jsonComponent.replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '\\r')
            "'${escaped}'"
        }.join(',')
        
        return "{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"
    }

    /**
     * Generate book components (1.20.5+ format)
     * Used for creating book entries in shulker boxes for versions 1.20.5+
     * Now uses raw NBT ListTag to preserve JSON text components
     */
    static String generateBookComponents(String title, String author, ListTag<?> pages, String version) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)
        
        String pagesStr = (0..<pages.size()).collect { int i ->
            String rawText = getStringAt(pages, i)
            // Convert § formatting codes to JSON text components if needed
            String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
            // Escape backslashes, double quotes, AND newlines for component syntax
            String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
            "\"${escaped}\""
        }.join(',')
        
        return "{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"
    }

    /**
     * Generate a Minecraft /give command for a shulker box containing books (organized by author)
     * Supports versions: 1.13, 1.14, 1.20.5, 1.21
     *
     * @param authorName Author name to display on shulker box
     * @param books List of book maps [{title, author, pages}, ...]
     * @param boxIndex Index for this author's shulker box (0 for first, 1+ for overflow)
     * @param version Minecraft version ('1_13', '1_14', '1_20_5', '1_21')
     */
    static String generateShulkerBoxCommand(String authorName, List<Map<String, Object>> books, int boxIndex, String version) {
        String boxColor = getShulkerColorForAuthor(authorName)
        
        // Cap at 27 books per shulker (slots 0-26)
        List<Map<String, Object>> booksForBox = books.drop(boxIndex * 27).take(27)
        
        if (booksForBox.isEmpty()) {
            return ''
        }
        
        String displayName = "Author: ${authorName}${boxIndex > 0 ? " (${boxIndex + 1})" : ''}"
        
        switch (version) {
            case '1_13':
                return generateShulkerBox_1_13(boxColor, authorName, displayName, booksForBox)
            case '1_14':
            case '1_20':
                return generateShulkerBox_1_14(boxColor, authorName, displayName, booksForBox)
            case '1_20_5':
                return generateShulkerBox_1_20_5(boxColor, authorName, displayName, booksForBox)
            case '1_21':
                return generateShulkerBox_1_21(boxColor, authorName, displayName, booksForBox)
            default:
                return ''
        }
    }

    /**
     * Generate shulker box command for Minecraft 1.13
     * Format: /give @a color_shulker_box{BlockEntityTag:{Items:[{Slot:N,id:written_book,Count:1,tag:...}]},display:{Name:'{...}'}}
     */
    static String generateShulkerBox_1_13(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder itemsStr = new StringBuilder()
        
        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) itemsStr.append(',')
            String bookNBT = generateBookNBT(book.title as String, book.author as String, book.pages as ListTag<?>, '1_13')
            itemsStr.append("{Slot:${index},id:written_book,Count:1,tag:${bookNBT}}")
        }
        
        // 1.13 display name uses single-quoted JSON
        String escapedDisplayName = displayName.replace('\\', '\\\\').replace('"', '\\"')
        String displayJson = '{"text":"' + escapedDisplayName + '","italic":false}'
        
        return "give @a ${color}_shulker_box{BlockEntityTag:{Items:[${itemsStr}]},display:{Name:'${displayJson}'}}"
    }

    /**
     * Generate shulker box command for Minecraft 1.14
     * Format: /give @a color_shulker_box{BlockEntityTag:{Items:[{Slot:N,id:written_book,Count:1,tag:...}]},display:{Name:'["",{"text":"...","italic":false}]'}}
     */
    static String generateShulkerBox_1_14(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder itemsStr = new StringBuilder()
        
        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) itemsStr.append(',')
            String bookNBT = generateBookNBT(book.title as String, book.author as String, book.pages as ListTag<?>, '1_14')
            itemsStr.append("{Slot:${index},id:written_book,Count:1,tag:${bookNBT}}")
        }
        
        // 1.14 uses single quotes with JSON inside
        String escapedDisplayName = displayName.replace('"', '\\"')
        String displayJson = '["",{"text":"' + escapedDisplayName + '","italic":false}]'
        
        return "give @a ${color}_shulker_box{BlockEntityTag:{Items:[${itemsStr}]},display:{Name:'${displayJson}'}}"
    }

    /**
     * Generate shulker box command for Minecraft 1.20.5+
     * Format: /give @a color_shulker_box[container=[{slot:N,item:{id:written_book,count:1,components:...}}],item_name="''["",{"text":"...","italic":false}]''"]
     */
    static String generateShulkerBox_1_20_5(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder containerStr = new StringBuilder()
        
        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) containerStr.append(',')
            String bookComponents = generateBookComponents(book.title as String, book.author as String, book.pages as ListTag<?>, '1_20_5')
            containerStr.append("{slot:${index},item:{id:written_book,count:1,components:${bookComponents}}}")
        }
        
        // 1.20.5+ uses escaped JSON for item_name
        // Escape quotes in display name for JSON
        String escapedDisplayName = displayName.replace('"', '\\"')
        // Build JSON string with literal quotes (single quotes around JSON, double quotes inside)
        // Result: '["":{"text":"...","italic":false}]'
        String nameJson = "'[\"\":{\"text\":\"${escapedDisplayName}\",\"italic\":false}]'"
        
        return "give @a minecraft:${color}_shulker_box[minecraft:container=[${containerStr}],item_name=${nameJson}]"
    }

    /**
     * Generate shulker box command for Minecraft 1.21+
     * Format: /give @a color_shulker_box[container=[{slot:N,item:{id:written_book,count:1,components:...}}],item_name="''["",{"text":"...","italic":false}]''"]
     * (Same as 1.20.5 but without minecraft: prefix for shulker_box)
     */
    static String generateShulkerBox_1_21(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder containerStr = new StringBuilder()
        
        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) containerStr.append(',')
            String bookComponents = generateBookComponents(book.title as String, book.author as String, book.pages as ListTag<?>, '1_21')
            containerStr.append("{slot:${index},item:{id:written_book,count:1,components:${bookComponents}}}")
        }
        
        // 1.21 uses same escaping as 1.20.5
        String escapedDisplayName = displayName.replace('"', '\\"')
        // Build JSON with literal quotes that test can find
        String nameJson = "'[\"\":{\"text\":\"${escapedDisplayName}\",\"italic\":false}]'"
        
        return "give @a ${color}_shulker_box[container=[${containerStr}],item_name=${nameJson}]"
    }

    /**
     * Convert Minecraft formatting codes (§) to JSON text components
     * For now, we extract just the text content and lose formatting.
     * Formats like §lBold§r become a simple JSON wrapper: {"text":"Bold"}
     */
    static String convertFormattingCodesToJson(String text) {
        if (!text || !text.contains('§')) {
            // Already plain text - wrap in JSON
            return "{\"text\":\"${text}\"}"
        }

        // Remove all § formatting codes
        String plainText = text.replaceAll(/§./, '')
        
        // Return as plain text in JSON wrapper
        return "{\"text\":\"${plainText}\"}"
    }

    /**
     * Map Minecraft color code to Minecraft color name
     */
    static String mapColorCode(char code) {
        switch (code) {
            case '0': return 'black'
            case '1': return 'dark_blue'
            case '2': return 'dark_green'
            case '3': return 'dark_aqua'
            case '4': return 'dark_red'
            case '5': return 'dark_purple'
            case '6': return 'gold'
            case '7': return 'gray'
            case '8': return 'dark_gray'
            case '9': return 'blue'
            case 'a': return 'green'
            case 'b': return 'aqua'
            case 'c': return 'red'
            case 'd': return 'light_purple'
            case 'e': return 'yellow'
            case 'f': return 'white'
            default: return null
        }
    }

    /**
     * Generate a Minecraft /give command for a written book
     * Supports versions: 1.13+, 1.14+, 1.20.5+, 1.21+
     */
    static String generateBookCommand(String title, String author, ListTag<?> pages, String version) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)

        String pagesStr

        switch (version) {
            case '1_20':
                // 1.20 uses same format as 1.14
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = getStringAt(pages, i)
                    String jsonArray
                    if (rawText.startsWith('[')) {
                        jsonArray = rawText
                    } else if (rawText.startsWith('{')) {
                        jsonArray = "[${rawText}]"
                    } else {
                        jsonArray = "[\"${rawText}\"]"
                    }
                    String escaped = jsonArray.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"

            case '1_13':
                // 1.13: /give @p written_book{title:"Title",author:"Author",pages:['{"text":"page1"}','{"text":"page2"}']}
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = getStringAt(pages, i)
                    // Convert § formatting codes to JSON text components if needed
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    // Single quotes don't require internal quote escaping, only backslashes
                    String escaped = jsonComponent.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"

            case '1_14':
                // 1.14: /give @p written_book{title:"Title",author:"Author",pages:['["page1"]','["page2"]']}
                // 1.14 wraps JSON in array brackets - note: uses single quotes so internal quotes don't need escaping
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = getStringAt(pages, i)
                    // If rawText is JSON (starts with '[' or '{'), use it directly
                    // If it's plain text, wrap in JSON array format
                    String jsonArray
                    if (rawText.startsWith('[')) {
                        jsonArray = rawText  // Already a JSON array
                    } else if (rawText.startsWith('{')) {
                        jsonArray = "[${rawText}]"  // Wrap JSON object in array
                    } else {
                        // Plain text: wrap in JSON array format ["text"]
                        // Note: Inside single quotes, we can use \" directly without escaping
                        jsonArray = "[\"${rawText}\"]"
                    }
                    // Escape backslashes only (single quotes don't need quote escaping)
                    String escaped = jsonArray.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"

            case '1_20_5':
                // 1.20.5: /give @p written_book[minecraft:written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = getStringAt(pages, i)
                    // Convert § formatting codes to JSON text components if needed
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    // Escape for NBT syntax: backslashes, double quotes, and newlines
                    String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
                    "\"${escaped}\""
                }.join(',')
                return "give @p written_book[minecraft:written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}]"

            case '1_21':
                // 1.21: /give @p written_book[written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = getStringAt(pages, i)
                    // Convert § formatting codes to JSON text components if needed
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    // Escape for NBT syntax: backslashes, double quotes, and newlines
                    String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
                    "\"${escaped}\""
                }.join(',')
                return "give @p written_book[written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}]"

            default:
                return ''
        }
    }

    /**
     * Allocate coordinates for a sign (once per unique text, regardless of versions)
     * Returns a map with {x, z} coordinates
     */
    static Map<String, Object> allocateSignPosition(List<String> lines, List<Object> originalCoords, String signInfo) {
        String signKey = lines.join('|')  // Use text as unique key
        if (!signsByHash.containsKey(signKey)) {
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
        } else {
            // Duplicate sign - increment Z offset
            Map<String, Object> existing = signsByHash[signKey]
            existing.z++
        }
        return signsByHash[signKey]
    }

    /**
     * Generate a Minecraft setblock command for a sign (all Minecraft versions)
     * Supports versions: 1.12-1.19, 1.20, 1.21.5+
     * Places signs at incrementing X coordinates, with Z offset for duplicates
     */
    static String generateSignCommand(List<String> frontLines, Map<String, Object> position, String version, List<String> backLines = null) {
        if (!frontLines || frontLines.size() == 0) {
            return ''
        }

        int x = position.x as int
        int z = position.z as int

        switch (version) {
            case '1_13':
                return generateSignCommand_1_13(frontLines, x, z, position)
            case '1_14':
                return generateSignCommand_1_14(frontLines, x, z, position)
            case '1_20':
                return generateSignCommand_1_20(frontLines, x, z, position, backLines)
            case '1_20_5':
                return generateSignCommand_1_20_5(frontLines, x, z, position, backLines)
            case '1_21':
                return generateSignCommand_1_21(frontLines, x, z, position, backLines)
            default:
                return ''
        }
    }

    /**
     * Generate sign for Minecraft 1.13-1.19 (old format with Text1-Text4)
     *
     * References:
     * - Sign NBT format: https://minecraft.wiki/w/Sign#Block_data
     * - setblock command: https://minecraft.wiki/w/Commands/setblock
     * - tellraw command: https://minecraft.wiki/w/Commands/tellraw
     * - JSON text format: https://minecraft.wiki/w/Raw_JSON_text_format
     * - clickEvent documentation: https://minecraft.wiki/w/Raw_JSON_text_format#Java_Edition
     */
    static String generateSignCommand_1_13(List<String> frontLines, int x, int z, Map<String, Object> position) {
        // Extract original world coordinates for clickEvent
        int origX = position.originalX as int
        int origY = position.originalY as int
        int origZ = position.originalZ as int

        // Create tellraw command that displays coordinates and allows teleporting
        // Format: /tellraw @s {"text":"...", "color":"...", "clickEvent":{"action":"run_command","value":"..."}}
        // The tellraw itself has a clickEvent that runs /tp command to teleport player to original location
        String tellrawCmd = "/tellraw @s {\\\\\\\"text\\\\\\\":\\\\\\\"Sign from (${origX} ${origY} ${origZ})\\\\\\\",\\\\\\\"color\\\\\\\":\\\\\\\"gray\\\\\\\",\\\\\\\"clickEvent\\\\\\\":{\\\\\\\"action\\\\\\\":\\\\\\\"run_command\\\\\\\",\\\\\\\"value\\\\\\\":\\\\\\\"/tp @s ${origX} ${origY} ${origZ}\\\\\\\"}}"

        // Add clickEvent to first line if it has text
        // Format: {"text":"...", "clickEvent":{"action":"run_command","value":"..."}}
        String text1 = escapeForMinecraftCommand(frontLines.size() > 0 ? frontLines[0] : '', '1_13')
        String text1Json = text1 ? "{\\\"text\\\":\\\"${text1}\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"${tellrawCmd}\\\"}}" : "{\\\"text\\\":\\\"${text1}\\\"}"

        String text2 = escapeForMinecraftCommand(frontLines.size() > 1 ? frontLines[1] : '', '1_13')
        String text3 = escapeForMinecraftCommand(frontLines.size() > 2 ? frontLines[2] : '', '1_13')
        String text4 = escapeForMinecraftCommand(frontLines.size() > 3 ? frontLines[3] : '', '1_13')

        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{Text1:'${text1Json}',Text2:'{\"text\":\"${text2}\"}',Text3:'{\"text\":\"${text3}\"}',Text4:'{\"text\":\"${text4}\"}',GlowingText:0} replace"
    }

    /**
     * Generate sign for Minecraft 1.14-1.19 (old format)
     *
     * References:
     * - Sign NBT format (1.14+): https://minecraft.wiki/w/Sign#Block_data
     * - Raw JSON text changes in 1.14: https://minecraft.wiki/w/Raw_JSON_text_format#History
     * - 1.14 changed text component format to use array syntax: ["",{"text":"..."}]
     */
    static String generateSignCommand_1_14(List<String> frontLines, int x, int z, Map<String, Object> position) {
        // Extract original world coordinates for clickEvent
        int origX = position.originalX as int
        int origY = position.originalY as int
        int origZ = position.originalZ as int

        // Create tellraw command that displays coordinates and allows teleporting
        // Note: 1.14 requires additional escaping due to array format
        String tellrawCmd = "/tellraw @s {\\\\\\\\\\\\\\\"text\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Sign from (${origX} ${origY} ${origZ})\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"color\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"gray\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"clickEvent\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"action\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"run_command\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"value\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"/tp @s ${origX} ${origY} ${origZ}\\\\\\\\\\\\\\\"}}"

        // Add clickEvent to first line if it has text
        // 1.14 format: ["",{"text":"...","clickEvent":{...}}]
        String text1 = escapeForMinecraftCommand(frontLines.size() > 0 ? frontLines[0] : '', '1_14')
        String text1Json = text1 ? "[\\\"\\\":{\\\"text\\\":\\\"${text1}\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"${tellrawCmd}\\\"}}]" : "[\\\"\\\":{\\\"text\\\":\\\"${text1}\\\"}]"

        String text2 = escapeForMinecraftCommand(frontLines.size() > 1 ? frontLines[1] : '', '1_14')
        String text3 = escapeForMinecraftCommand(frontLines.size() > 2 ? frontLines[2] : '', '1_14')
        String text4 = escapeForMinecraftCommand(frontLines.size() > 3 ? frontLines[3] : '', '1_14')

        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{Text1:'${text1Json}',Text2:'[\\\"\\\":{\\\"text\\\":\\\"${text2}\\\"}]',Text3:'[\\\"\\\":{\\\"text\\\":\\\"${text3}\\\"}]',Text4:'[\\\"\\\":{\\\"text\\\":\\\"${text4}\\\"}]',GlowingText:0} replace"
    }

    /**
     * Generate sign for Minecraft 1.20 (new front_text/back_text format)
     *
     * References:
     * - Sign format changes in 1.20: https://minecraft.wiki/w/Sign#History (Java Edition 1.20 section)
     * - New front_text/back_text structure: https://minecraft.wiki/w/Sign#Block_data
     * - Signs now support text on both sides with separate front_text and back_text NBT compounds
     * - Each side has: messages (array of 4 text components), has_glowing_text (byte), color (string)
     */
    static String generateSignCommand_1_20(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        // Extract original world coordinates for clickEvent
        int origX = position.originalX as int
        int origY = position.originalY as int
        int origZ = position.originalZ as int

        // Create tellraw command that displays coordinates and allows teleporting
        String tellrawCmd = "/tellraw @s {\\\\\\\\\\\\\\\"text\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Sign from (${origX} ${origY} ${origZ})\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"color\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"gray\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"clickEvent\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"action\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"run_command\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"value\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"/tp @s ${origX} ${origY} ${origZ}\\\\\\\\\\\\\\\"}}"

        String frontMessages = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_20')
            // Add clickEvent to first line
            // Format: ["",{"text":"...","clickEvent":{...}}]
            if (i == 0 && line) {
                "'[\\\"\\\":{\\\"text\\\":\\\"${escaped}\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"${tellrawCmd}\\\"}}]'"
            } else {
                "'[\\\"\\\":{\\\"text\\\":\\\"${escaped}\\\"}]'"
            }
        }.join(',')
        
        // Generate back_text messages if provided, otherwise empty array
        String backMessages
        if (backLines && backLines.any { it }) {
            backMessages = (0..3).collect { int i ->
                String line = i < backLines.size() ? backLines[i] : ''
                String escaped = escapeForMinecraftCommand(line, '1_20')
                "'[\\\"\\\":{\\\"text\\\":\\\"${escaped}\\\"}]'"
            }.join(',')
        } else {
            backMessages = "''[\\\"\\\":{\\\"text\\\":\\\"\\\"}]'', ''[\\\"\\\":{\\\"text\\\":\\\"\\\"}]'', ''[\\\"\\\":{\\\"text\\\":\\\"\\\"}]'', ''[\\\"\\\":{\\\"text\\\":\\\"\\\"}]''"
        }
        
        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
    }

    /**
     * Generate sign for Minecraft 1.20.5+ (new front_text/back_text with component format)
     *
     * References:
     * - Sign text component changes (1.20.5): https://minecraft.wiki/w/Sign#History (see 24w09a snapshot)
     * - Text components simplified: messages now use [[{"text":"..."}]] instead of ["",{"text":"..."}]
     * - Discussion on format changes: https://bugs.mojang.com/browse/MC-268359
     * - clickEvent on signs: Clicking the sign text triggers the clickEvent action
     * - The clickEvent runs a tellraw that shows original coordinates with its own clickEvent for teleportation
     */
    static String generateSignCommand_1_20_5(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        // Extract original world coordinates for clickEvent
        int origX = position.originalX as int
        int origY = position.originalY as int
        int origZ = position.originalZ as int

        // Create tellraw command that displays coordinates and allows teleporting
        // This command will be executed when player clicks on the sign's first line
        // Format: /tellraw @s {"text":"Sign from (X Y Z)","color":"gray","clickEvent":{"action":"run_command","value":"/tp @s X Y Z"}}
        String tellrawCmd = "/tellraw @s {\\\"text\\\":\\\"Sign from (${origX} ${origY} ${origZ})\\\",\\\"color\\\":\\\"gray\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/tp @s ${origX} ${origY} ${origZ}\\\"}}"

        String frontMessages = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_20_5')
            // Add clickEvent to first line
            // 1.20.5+ format: [[{"text":"...","clickEvent":{...}}]]
            if (i == 0 && line) {
                '[[{"text":"' + escaped + '","clickEvent":{"action":"run_command","value":"' + tellrawCmd + '"}}]]'
            } else {
                '[[{"text":"' + escaped + '"}]]'
            }
        }.join(',')
        
        // Generate back_text messages if provided, otherwise empty array
        String backMessages
        if (backLines && backLines.any { it }) {
            backMessages = (0..3).collect { int i ->
                String line = i < backLines.size() ? backLines[i] : ''
                String escaped = escapeForMinecraftCommand(line, '1_20_5')
                '[[{"text":"' + escaped + '"}]]'
            }.join(',')
        } else {
            backMessages = '[[{"text":""}]], [[{"text":""}]], [[{"text":""}]], [[{"text":""}]]'
        }
        
        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
    }

    /**
     * Generate sign for Minecraft 1.21+ (same as 1.20.5)
     */
    static String generateSignCommand_1_21(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        return generateSignCommand_1_20_5(frontLines, x, z, position, backLines)
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

        ['1_13', '1_14', '1_20_5', '1_21'].each { String version ->
            BufferedWriter writer = signsMcfunctionWriters[version]
            if (writer) {
                try {
                    String command = generateSignCommand(frontLines, position, version, backLines)
                    if (command) {
                        writer.writeLine(command)
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to write sign to ${version} mcfunction: ${e.message}")
                }
            }
        }
    }


    /**
     * Write a book command to all mcfunction version files AND collect for shulker boxes
     */
    static void writeBookToMcfunction(String title, String author, ListTag<?> pages) {
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
            pages: pages  // Store the raw NBT ListTag
        ])

        ['1_13', '1_14', '1_20_5', '1_21'].each { String version ->
            BufferedWriter writer = mcfunctionWriters[version]
            if (writer) {
                try {
                    String command = generateBookCommand(title, author, pages, version)
                    writer.writeLine(command)
                } catch (Exception e) {
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
        if (booksByAuthor.isEmpty()) {
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
                ['1_13', '1_14', '1_20_5', '1_21'].each { String version ->
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
                        } catch (Exception e) {
                            LOGGER.warn("Failed to write shulker box command for author '${author}' (box ${boxIndex}) to ${version}: ${e.message}", e)
                        }
                    }
                }
            }
        }

        LOGGER.info('Shulker box generation complete')
    }



    static void printSummaryStatistics(long elapsedMillis) {
        File summaryFile = new File(baseDirectory, "${outputFolder}${File.separator}summary.txt")
        summaryFile.withWriter { BufferedWriter w ->
            w.writeLine('=' * 80)
            w.writeLine('SUMMARY STATISTICS')
            w.writeLine('=' * 80)

            w.writeLine('\nBooks:')
            w.writeLine("  Total unique books found: ${bookHashes.size()}")
            w.writeLine("  Total books extracted (including duplicates): ${bookCounter}")
            w.writeLine("  Duplicate books: ${bookCounter - bookHashes.size()}")

            if (booksByLocationType) {
                w.writeLine('\n  Books by location type:')
                booksByLocationType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                    w.writeLine("    ${k}: ${v}")
                }
            }

            if (booksByContainerType) {
                w.writeLine('\n  Books by container type:')
                booksByContainerType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                    w.writeLine("    ${k}: ${v}")
                }
            }

            w.writeLine('\nSigns:')
            w.writeLine("  Total signs found: ${signHashes.size()}")
            if (emptySignsRemoved > 0) {
                w.writeLine("  Empty signs removed: ${emptySignsRemoved}")
            }

            w.writeLine('\nPerformance:')
            w.writeLine("  Total processing time: ${DurationFormatUtils.formatDurationWords(elapsedMillis, true, true)} (${elapsedMillis / 1000} seconds)")

            // Generate ASCII table of books with detailed information (moved to end)
            if (bookMetadataList) {
                w.writeLine('\n  Books extracted:')
                List<Column> columns = [
                    new Column().header('Title').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.title?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('Author').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.author?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('PageCount').dataAlign(HorizontalAlign.RIGHT).with({ Map<String, Object> book -> book.pageCount?.toString() ?: '0' } as java.util.function.Function),
                    new Column().header('FoundWhere').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.foundWhere?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('Coordinates').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.coordinates?.toString() ?: '' } as java.util.function.Function)
                ]
                String table = AsciiTable.getTable(bookMetadataList, columns)
                w.writeLine(table)
            }

            w.writeLine("\n${'=' * 80}")
            w.writeLine('Completed successfully!')
            w.writeLine('=' * 80)
}

        LOGGER.info("\n${'=' * 80}")
        LOGGER.info("Summary statistics written to: ${summaryFile.absolutePath}")
        LOGGER.info('=' * 80)
    }

    static void incrementBookStats(String containerType, String locationType) {
        booksByContainerType[containerType] = (booksByContainerType[containerType] ?: 0) + 1
        booksByLocationType[locationType] = (booksByLocationType[locationType] ?: 0) + 1
    }

    /**
     * Read books from player data files (player inventories and ender chests)
     */
    public static void readPlayerData() {
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

                    // Process inventory
                    getCompoundTagList(playerCompound, 'Inventory').each { CompoundTag item ->
                        int booksBefore = bookCounter
                        parseItem(item, "Inventory of player ${file.name}")
                        if (bookCounter > booksBefore) {
                            incrementBookStats('Player Inventory', 'Player')
                        }
                    }

                    // Process ender chest
                    getCompoundTagList(playerCompound, 'EnderItems').each { CompoundTag item ->
                        int booksBefore = bookCounter
                        parseItem(item, "Ender Chest of player ${file.name}")
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
    public static void readSignsAndBooks() {
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

        File signOutput = new File(baseDirectory, "${outputFolder}${File.separator}all_signs.txt")
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
                             
                             // Check if this region was previously marked as failed - if so, mark it as recovered
                             String worldFolderName = new File(baseDirectory).name
                             if (failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)) {
                                 recoveredRegions.add(file.name)
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
                                             getCompoundTagList(tileEntity, 'Items').each { CompoundTag item ->
                                                 String bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')}) ${file.name}"
                                                 int booksBefore = bookCounter
                                                 parseItem(item, bookInfo)
                                                 if (bookCounter > booksBefore) {
                                                     incrementBookStats(blockId, 'Block Entity')
                                                 }
                                             }
                                         }

                                         // Process lecterns (single book)
                                         if (hasKey(tileEntity, 'Book')) {
                                             CompoundTag book = getCompoundTag(tileEntity, 'Book')
                                             String bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')}) ${file.name}"
                                             int booksBefore = bookCounter
                                             parseItem(book, bookInfo)
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
                                                 parseItem(item, bookInfo)
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
                                             parseItem(item, bookInfo)
                                             if (bookCounter > booksBefore) {
                                                 incrementBookStats(entityId, 'Entity')
                                             }
                                         }
                                     }
                                 }
                             }
                         } catch (IOException e) {
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
    public static void readEntities() {
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
                         
                         // Check if this entity file was previously marked as failed - if so, mark it as recovered
                         String worldFolderName = new File(baseDirectory).name
                         if (failedRegionsByWorld.containsKey(worldFolderName) && failedRegionsByWorld[worldFolderName].contains(file.name)) {
                             recoveredRegions.add(file.name)
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

                                     // Entities with inventory
                                     if (hasKey(entity, 'Items')) {
                                         getCompoundTagList(entity, 'Items').each { CompoundTag item ->
                                             String bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                             int booksBefore = bookCounter
                                             parseItem(item, bookInfo)
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
                                         parseItem(item, bookInfo)
                                         if (bookCounter > booksBefore) {
                                             incrementBookStats(entityId, 'Entity')
                                         }
                                     }
                                 }
                             }
                         }
                     } catch (IOException e) {
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
    public static void parseItem(CompoundTag item, String bookInfo) {
        String itemId = item.getString('id')

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

            // Try new format first (1.20.5+ with components)
            // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, 'item')
                        parseItem(shelkerItem, "${bookInfo} > shulker_box")
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (pre-1.20.5)
                CompoundTag shelkerCompound = getCompoundTag(item, 'tag')
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, 'BlockEntityTag')
                getCompoundTagList(shelkerCompound2, 'Items').each { CompoundTag shelkerItem ->
                    parseItem(shelkerItem, "${bookInfo} > shulker_box")
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
                    getCompoundTagList(components, 'minecraft:bundle_contents').each { CompoundTag bundleItem ->
                        parseItem(bundleItem, "${bookInfo} > bundle")
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
                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag chestItem = getCompoundTag(containerEntry, 'item')
                        parseItem(chestItem, "${bookInfo} > copper_chest")
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (if copper chests existed in pre-1.20.5)
                CompoundTag chestCompound = getCompoundTag(item, 'tag')
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, 'BlockEntityTag')
                getCompoundTagList(chestCompound2, 'Items').each { CompoundTag chestItem ->
                    parseItem(chestItem, "${bookInfo} > copper_chest")
                }
            }
        }

        // Note: Lecterns are handled as block entities with "Book" tag (not "Items")
        // They are scanned separately in the block entity processing code
        // Decorated pots are handled as block entities with "Items" tag (standard container)
    }

    static String sanitizeFilename(String name) {
        if (!name) {
            return 'unnamed'
        }
        return name.replaceAll(/[\\/:*?<>|]/, '_').take(200)
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
     */
    public static void readWrittenBook(CompoundTag item, String bookInfo) {
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

        // Check for duplicates
        boolean isDuplicate = !bookHashes.add(pages.hashCode())
        if (isDuplicate) {
            LOGGER.debug('Written book is a duplicate - saving to .duplicates folder')
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

        LOGGER.debug("Extracted written book: \"${title}\" by ${author} (${pages.size()} pages, format: ${format})")

        bookCounter++

        // Extract coordinates and location info
        List<Object> locationInfo = extractLocationInfo(bookInfo)
        String coordsForFilename = locationInfo[0]
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
            coordinates: (x != null && y != null && z != null) ? "${x}, ${y}, ${z}" : ''
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

        // Ensure filename uniqueness by appending counter if file exists
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        String filename = "${baseFilename}.stendhal"
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.stendhal"
            bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
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

        // Write to mcfunction file - pass raw NBT pages to preserve JSON text components
        // IMPORTANT: Pass the raw NBT ListTag directly, not extracted text
        if (pages && pages.size() > 0) {
            writeBookToMcfunction(title, author, pages)
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
    public static void readWritableBook(CompoundTag item, String bookInfo) {
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
        bookMetadataList.add([
            title: 'Writable Book',
            author: '',
            pageCount: pages.size(),
            foundWhere: foundWhere,
            coordinates: (x != null && y != null && z != null) ? "${x}, ${y}, ${z}" : ''
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

        // Add to CSV data
        bookCsvData.add([
            x: x,
            y: y,
            z: z,
            foundWhere: foundWhere,
            bookname: 'Writable Book',
            author: '',
            pageCount: pages.size(),
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
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.stendhal"
            bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
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
        if (pages && pages.size() > 0) {
            writeBookToMcfunction('Writable Book', '', pages)
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
        try {
            int startIndex = bookInfo.indexOf(prefix)
            if (startIndex >= 0) {
                String remainder = bookInfo.substring(startIndex + prefix.length())
                // Remove .dat extension if present
                if (remainder.endsWith('.dat')) {
                    remainder = remainder.substring(0, remainder.length() - 4)
                }
                return remainder.trim()
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract player name from: ${bookInfo}", e)
        }
        return 'unknown'
    }

    /**
     * Extract page text from a book page (handles both string and compound formats)
     *
     * PAGE FORMAT CHANGES (1.20.5):
     * - Pre-1.20.5: Pages are string list
     * - 1.20.5+: Pages are compound list with "raw"/"filtered" fields
     */
    public static String extractPageText(ListTag<?> pages, int index) {
        if (isStringList(pages)) {
            return getStringAt(pages, index)
        } else if (isCompoundList(pages)) {
            CompoundTag pageCompound = getCompoundAt(pages, index)
            return pageCompound.getString('raw') ?: pageCompound.getString('filtered') ?: ''
        }
        return ''
    }

    static String extractTextContent(String pageText) {
        if (!pageText) {
            return ''
        }

        if (!pageText.startsWith('{')) {
            return removeTextFormatting(pageText)
        }

        try {
            Object pageJSON = JSON_SLURPER.parseText(pageText)

            if (pageJSON.extra) {
                return pageJSON.extra.collect { Object item ->
                    if (item instanceof String) {
                        removeTextFormatting((String) item)
                    } else {
                        removeTextFormatting(item.text ?: '')
                    }
                }.join('')
            } else if (pageJSON.text) {
                return removeTextFormatting(pageJSON.text)
            }
        } catch (groovy.json.JsonException e) {
            LOGGER.debug("Page text is not valid JSON, returning as-is: ${e.message}")
        }

        return removeTextFormatting(pageText)
    }

    /**
     * Extract text content from page text while preserving formatting codes
     * Used for .stendhal files
     */
    static String extractTextContentPreserveFormatting(String pageText) {
        if (!pageText) {
            return ''
        }

        if (!pageText.startsWith('{')) {
            return pageText
        }

        try {
            Object pageJSON = JSON_SLURPER.parseText(pageText)

            if (pageJSON.extra) {
                return pageJSON.extra.collect { Object item ->
                    if (item instanceof String) {
                        (String) item
                    } else {
                        item.text ?: ''
                    }
                }.join('')
            } else if (pageJSON.text) {
                return pageJSON.text
            }
        } catch (groovy.json.JsonException e) {
            LOGGER.debug("Page text is not valid JSON, returning as-is: ${e.message}")
        }

        return pageText
    }

    /**
     * Extract coordinates from signInfo string
     * signInfo format: "Chunk [x, z]\t(X Y Z)\t\t"
     * Returns: [x, y, z, blockId]
     */
    static List<Object> extractSignCoordinates(String signInfo) {
        Integer x = null
        Integer y = null
        Integer z = null

        try {
            // Extract coordinates from format: "Chunk [x, z]\t(X Y Z)\t\t"
            int startParen = signInfo.indexOf('(')
            int endParen = signInfo.indexOf(')')
            if (startParen >= 0 && endParen > startParen) {
                String coords = signInfo.substring(startParen + 1, endParen)
                String[] parts = coords.split(' ')
                if (parts.length >= 3) {
                    x = Integer.parseInt(parts[0])
                    y = Integer.parseInt(parts[1])
                    z = Integer.parseInt(parts[2])
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse sign coordinates from: ${signInfo}")
        }

        return [x, y, z]
    }

    /**
     * Pad sign line to exactly 15 characters (Minecraft's max sign line width)
     */
    static String padSignLine(String text) {
        if (text.length() >= 15) {
            return text.substring(0, 15)
        }
        return text.padRight(15, ' ')
    }

    /**
     * Parse sign in old format (Text1-Text4 fields, used before 1.20)
     */
    public static void parseSign(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
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
        if (line1.isEmpty() && line2.isEmpty() && line3.isEmpty() && line4.isEmpty()) {
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
    public static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
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
        if (extractedFrontLines.every { it.isEmpty() }) {
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
        if (!line || line == '' || line == 'null') {
            return ''
        }
        if (!line.startsWith('{')) {
            return line
        }

        try {
            JSONObject json = new JSONObject(line)

            if (json.has('extra')) {
                Object extra = json.get('extra')
                if (extra instanceof JSONArray) {
                    StringBuilder sb = new StringBuilder()
                    JSONArray extraArray = (JSONArray) extra
                    for (int i = 0; i < extraArray.length(); i++) {
                        Object item = extraArray.get(i)
                        if (item instanceof String) {
                            sb.append(item)
                        } else if (item instanceof JSONObject) {
                            JSONObject temp = (JSONObject) item
                            if (temp.has('text')) {
                                sb.append(temp.get('text'))
                            }
                        }
                    }
                    return sb.toString()
                } else if (extra instanceof JSONObject) {
                    JSONObject extraObj = (JSONObject) extra
                    if (extraObj.has('text')) {
                        return String.valueOf(extraObj.get('text'))
                    }
                }
            } else if (json.has('text')) {
                String text = String.valueOf(json.get('text'))
                // Filter out empty text with only empty key-value pairs
                if (text == '' && json.length() == 1) {
                    return ''
                }
                return text
            }
        } catch (JSONException e) {
            LOGGER.debug("Sign line is not valid JSON: ${e.message}")
        }

        // Filter out empty JSON objects like {"":""} or {}
        if (line == '{}' || line == '{"":""}') {
            return ''
        }

        return line
    }

    static String removeTextFormatting(String text) {
        if (!text) {
            return ''
        }
        // Only remove formatting if the flag is enabled
        if (!removeFormatting) {
            return text
        }
        return COLOR_CODES.inject(text) { String result, String code -> result.replace(code, '') }
    }

    static CompoundTag readCompressedNBT(File file) {
        net.querz.nbt.io.NamedTag namedTag = NBTUtil.read(file)
        return (CompoundTag) namedTag.tag
    }

    // ========== NBT Helper Methods ==========
    // Minimal helper methods - Querz library already provides safe getters that return defaults for missing/null keys
    // Our custom code only adds:
    // 1. Null-safe wrappers (returns empty objects instead of null)
    // 2. Format detection for Minecraft version changes (1.20, 1.20.5, 21w43a)
    // 3. Fallback logic for old/new format compatibility

    static boolean hasKey(CompoundTag tag, String key) {
        return tag != null && tag.containsKey(key)
    }

    static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        if (!tag) {
            return new CompoundTag()
        }
        return tag.getCompoundTag(key) ?: new CompoundTag()
    }

    static ListTag<CompoundTag> getCompoundTagList(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return new ListTag<>(CompoundTag)
        }
        ListTag<?> list = tag.getListTag(key)
        if (!list || list.size() == 0) {
            return new ListTag<>(CompoundTag)
        }
        try {
            return list.asCompoundTagList()
        } catch (ClassCastException e) {
            return new ListTag<>(CompoundTag)
        }
    }

    static ListTag<?> getListTag(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return ListTag.createUnchecked(Object)
        }
        return tag.getListTag(key) ?: ListTag.createUnchecked(Object)
    }

    static double getDoubleAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return 0.0
        }

        try {
            net.querz.nbt.tag.Tag<?> tag = list.get(index)
            if (tag == null) {
                return 0.0
            }
            switch (tag) {
                case NumberTag:
                    return ((NumberTag<?>) tag).asDouble()
                case StringTag:
                    return Double.parseDouble(((StringTag) tag).value)
                default:
                    return 0.0
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("Invalid number format in NBT tag, returning default: ${e.message}")
            return 0.0
        }
    }

    static String getStringAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return ''
        }

        try {
            net.querz.nbt.tag.Tag<?> tag = list.get(index)
            if (tag == null) {
                return ''
            }
            switch (tag) {
                case StringTag:
                    return ((StringTag) tag).value
                case CompoundTag:
                    // For 1.20.5+ page CompoundTags, extract the 'raw' field directly
                    // which already contains the properly formatted JSON text components
                    CompoundTag compound = (CompoundTag) tag
                    if (compound.containsKey('raw')) {
                        net.querz.nbt.tag.Tag<?> rawTag = compound.get('raw')
                        if (rawTag instanceof StringTag) {
                            return ((StringTag) rawTag).value
                        }
                    }
                    // Fallback: convert entire CompoundTag to JSON
                    return convertNbtToJson((CompoundTag) tag).toString()
                default:
                    return tag.valueToString()
            }
        } catch (ClassCastException e) {
            LOGGER.debug("Error casting NBT tag at index ${index}: ${e.message}")
            return ''
        }
    }

    /**
     * Converts a CompoundTag (NBT) to a JSONObject.
     * This handles the NBT -> JSON conversion for Minecraft text components.
     */
    static JSONObject convertNbtToJson(CompoundTag tag) {
        JSONObject json = new JSONObject()

        tag.forEach { String key, net.querz.nbt.tag.Tag<?> value ->
            switch (value) {
                case StringTag:
                    json.put(key, ((StringTag) value).value)
                    break
                case NumberTag:
                    json.put(key, ((NumberTag) value).asNumber())
                    break
                case CompoundTag:
                    json.put(key, convertNbtToJson((CompoundTag) value))
                    break
                case ListTag:
                    json.put(key, convertNbtListToJsonArray((ListTag<?>) value))
                    break
                default:
                    json.put(key, value.value)
            }
        }

        return json
    }

    /**
     * Converts a ListTag (NBT) to a JSONArray.
     */
    static JSONArray convertNbtListToJsonArray(ListTag<?> list) {
        JSONArray array = new JSONArray()

        for (int i = 0; i < list.size(); i++) {
            net.querz.nbt.tag.Tag<?> tag = list.get(i)

            switch (tag) {
                case StringTag:
                    array.put(((StringTag) tag).value)
                    break
                case NumberTag:
                    array.put(((NumberTag) tag).asNumber())
                    break
                case CompoundTag:
                    array.put(convertNbtToJson((CompoundTag) tag))
                    break
                case ListTag:
                    array.put(convertNbtListToJsonArray((ListTag<?>) tag))
                    break
                default:
                    array.put(tag.value)
            }
        }

        return array
    }

    static String getStringFrom(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return ''
        }

        try {
            net.querz.nbt.tag.Tag<?> value = tag.get(key)
            if (value == null) {
                return ''
            }

            LOGGER.debug("getStringFrom() - key: ${key}, value type: ${value.getClass().name}, value: ${value}")

            // Check if it's a StringTag and get the value
            if (value instanceof StringTag) {
                String result = ((StringTag) value).value
                LOGGER.debug("getStringFrom() - returning: ${result}")
                return result
            }

            LOGGER.debug('getStringFrom() - value is not a StringTag, returning empty string')
            return ''
        } catch (ClassCastException e) {
            LOGGER.error("getStringFrom() - error casting value for key '${key}': ${e.message}", e)
            return ''
        }
    }

    static CompoundTag getCompoundAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return new CompoundTag()
        }

        net.querz.nbt.tag.Tag<?> tag = list.get(index)
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag
        }
        return new CompoundTag()
    }

    static boolean isStringList(ListTag<?> list) {
        return list && list.size() > 0 && list.typeClass == StringTag
    }

    static boolean isCompoundList(ListTag<?> list) {
        return list && list.size() > 0 && list.typeClass == CompoundTag
    }

}
