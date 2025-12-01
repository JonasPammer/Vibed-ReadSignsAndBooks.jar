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

    // ========== Datapack Generation (delegated to DatapackGenerator) ==========
    static File createDatapackStructure(String version) {
        return DatapackGenerator.createDatapackStructure(baseDirectory, outputFolder, version)
    }

    static void createPackMcmeta(String version, int packFormat, String description) {
        DatapackGenerator.createPackMcmeta(baseDirectory, outputFolder, version, packFormat, description)
    }

    static int getPackFormat(String version) {
        return DatapackGenerator.getPackFormat(version)
    }

    static String getVersionDescription(String version) {
        return DatapackGenerator.getVersionDescription(version)
    }

    static void runExtraction() {
        // Reset state
        [bookHashes, signHashes, customNameHashes, customNameData, booksByContainerType, booksByLocationType, bookMetadataList, bookCsvData, signCsvData, booksByAuthor, signsByHash, bookGenerationByHash].each { collection -> collection.clear() }
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
            writeCustomNamesOutput()

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
        if (!extractCustomNames || customNameData.isEmpty()) {
            return
        }

        LOGGER.info("Writing custom names output (${customNameData.size()} unique custom names found)")

        // Write CSV file
        File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}all_custom_names.csv")
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Type,ItemOrEntityID,CustomName,X,Y,Z,Location')
            customNameData.each { Map<String, Object> data ->
                writer.writeLine([
                    escapeCsvField(data.type as String),
                    escapeCsvField(data.itemOrEntityId as String),
                    escapeCsvField(data.customName as String),
                    data.x ?: '',
                    data.y ?: '',
                    data.z ?: '',
                    escapeCsvField(data.location as String)
                ].join(','))
            }
        }

        // Write TXT file with grouped report
        File txtFile = new File(baseDirectory, "${outputFolder}${File.separator}all_custom_names.txt")
        txtFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('Custom Names Extraction Report')
            writer.writeLine('=' * 80)
            writer.writeLine('')

            // Group by type
            Map<String, List<Map<String, Object>>> groupedByType = customNameData.groupBy { it.type as String }

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
        File jsonFile = new File(baseDirectory, "${outputFolder}${File.separator}all_custom_names.json")
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

        LOGGER.info("Custom names written to: all_custom_names.csv, all_custom_names.txt, all_custom_names.json")
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
        if (customName && customName.trim().isEmpty()) {
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
        if (customName && customName.trim().isEmpty()) {
            return null
        }
        return customName?.trim()
    }

    /**
     * Record a custom name with deduplication
     * Creates hash from: customName|type|itemOrEntityId
     */
    static void recordCustomName(String customName, String itemOrEntityId, String type, String location, int x, int y, int z) {
        if (!customName) {
            return
        }

        // Create deduplication hash
        String hashKey = "${customName}|${type}|${itemOrEntityId}".hashCode().toString()

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

    static String generateShulkerBox_1_13(String color, String author, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_13(color, author, displayName, books)
    }

    static String generateShulkerBox_1_14(String color, String author, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_14(color, author, displayName, books)
    }

    static String generateShulkerBox_1_20_5(String color, String author, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_20_5(color, author, displayName, books)
    }

    static String generateShulkerBox_1_21(String color, String author, String displayName, List<Map<String, Object>> books) {
        return ShulkerBoxGenerator.generateShulkerBox_1_21(color, author, displayName, books)
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

        ['1_13', '1_14', '1_20_5', '1_21'].each { String version ->
            BufferedWriter writer = mcfunctionWriters[version]
            if (writer) {
                try {
                    String command = generateBookCommand(title, author, pages, version, generation)
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
    public static void parseItem(CompoundTag item, String bookInfo, int x = 0, int y = 0, int z = 0) {
        String itemId = item.getString('id')

        // Extract custom name if enabled
        if (extractCustomNames) {
            String customName = extractCustomNameFromItem(item)
            if (customName) {
                recordCustomName(customName, itemId, 'item', bookInfo, x, y, z)
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

            // Try new format first (1.20.5+ with components)
            // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, 'item')
                        parseItem(shelkerItem, "${bookInfo} > shulker_box", x, y, z)
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (pre-1.20.5)
                CompoundTag shelkerCompound = getCompoundTag(item, 'tag')
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, 'BlockEntityTag')
                getCompoundTagList(shelkerCompound2, 'Items').each { CompoundTag shelkerItem ->
                    parseItem(shelkerItem, "${bookInfo} > shulker_box", x, y, z)
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
                        parseItem(bundleItem, "${bookInfo} > bundle", x, y, z)
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
                        parseItem(chestItem, "${bookInfo} > copper_chest", x, y, z)
                    }
                }
            } else if (hasKey(item, 'tag')) {
                // Old format (if copper chests existed in pre-1.20.5)
                CompoundTag chestCompound = getCompoundTag(item, 'tag')
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, 'BlockEntityTag')
                getCompoundTagList(chestCompound2, 'Items').each { CompoundTag chestItem ->
                    parseItem(chestItem, "${bookInfo} > copper_chest", x, y, z)
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
                File newDuplicateLocation = new File(baseDirectory, "${duplicatesFolder}${File.separator}${existingFile.name}")
                // Ensure unique filename in duplicates folder
                int dupCounter = 2
                while (newDuplicateLocation.exists()) {
                    String nameWithoutExt = existingFile.name.replace('.stendhal', '')
                    newDuplicateLocation = new File(baseDirectory, "${duplicatesFolder}${File.separator}${nameWithoutExt}_${dupCounter}.stendhal")
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
    public static String extractPageText(ListTag<?> pages, int index) {
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
