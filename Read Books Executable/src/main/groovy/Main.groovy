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
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

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

    static Set<Integer> bookHashes = [] as Set
    static Set<String> signHashes = [] as Set

    static int bookCounter = 0
    static Map<String, Integer> booksByContainerType = [:]
    static Map<String, Integer> booksByLocationType = [:]
    static List<Map<String, String>> bookMetadataList = []
    static List<Map<String, Object>> bookCsvData = []
    static List<Map<String, Object>> signCsvData = []
    static int emptySignsRemoved = 0
    static BufferedWriter combinedBooksWriter

    // Page separator for books - using Unicode box drawing for subtle visual separation
    static final String PAGE_SEPARATOR = '─' * 40

    @Option(names = ['-w', '--world'], description = 'Specify custom world directory')
    static String customWorldDirectory

    @Option(names = ['-o', '--output'], description = 'Specify custom output directory')
    static String customOutputDirectory

    @Option(names = ['--no-books'], description = 'Disable book extraction')
    static boolean disableBooks = false

    @Option(names = ['--no-signs'], description = 'Disable sign extraction')
    static boolean disableSigns = false

    // Derived flags
    static boolean enableBookExtraction = true
    static boolean enableSignExtraction = true

    static void main(String[] args) {
        new CommandLine(new Main()).execute(args)
    // Exit code is automatically handled by picocli
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

    static void runExtraction() {
        // Apply flag logic
        enableSignExtraction = !disableSigns
        enableBookExtraction = !disableBooks

        // Reset state
        [bookHashes, signHashes, booksByContainerType, booksByLocationType, bookMetadataList, bookCsvData, signCsvData].each { collection -> collection.clear() }
        bookCounter = 0
        emptySignsRemoved = 0

        // Set directories
        baseDirectory = customWorldDirectory ?: System.getProperty('user.dir')
        dateStamp = new SimpleDateFormat('yyyy-MM-dd', Locale.US).format(new Date())
        outputFolder = customOutputDirectory ?: "ReadBooks${File.separator}${dateStamp}"
        booksFolder = "${outputFolder}${File.separator}books"
        duplicatesFolder = "${booksFolder}${File.separator}.duplicates"

        // Configure logging
        System.setProperty('LOG_FILE', new File(baseDirectory, "${outputFolder}${File.separator}logs.txt").absolutePath)
        reloadLogbackConfiguration()

        // Create directories
        [outputFolder, booksFolder, duplicatesFolder].each { String folder ->
            new File(baseDirectory, folder).mkdirs()
        }

        LOGGER.info('=' * 80)
        LOGGER.info('ReadSignsAndBooks - Minecraft World Data Extractor')
        LOGGER.info("Started at: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.US).format(new Date())}")
        LOGGER.info("World directory: ${baseDirectory}")
        LOGGER.info("Output folder: ${outputFolder}")
        LOGGER.info("Book extraction: ${enableBookExtraction}")
        LOGGER.info("Sign extraction: ${enableSignExtraction}")
        LOGGER.info('=' * 80)

        long startTime = System.currentTimeMillis()

        try {
            if (enableBookExtraction) {
                combinedBooksWriter = new File(baseDirectory, "${outputFolder}${File.separator}all_books.txt").newWriter()
                readPlayerData()
            }

            readSignsAndBooks()

            if (enableBookExtraction) {
                readEntities()
            }

            combinedBooksWriter?.close()

            // Write CSV exports
            if (enableBookExtraction) {
                writeBooksCSV()
            }
            if (enableSignExtraction) {
                writeSignsCSV()
            }

            long elapsed = System.currentTimeMillis() - startTime
            printSummaryStatistics(elapsed)
            LOGGER.info("${elapsed / 1000} seconds to complete.")
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Fatal error: ${e.message}", e)
            combinedBooksWriter?.close()
            throw e
        }
    }

    static void reloadLogbackConfiguration() {
        ch.qos.logback.classic.LoggerContext loggerContext = LoggerFactory.ILoggerFactory as ch.qos.logback.classic.LoggerContext
        loggerContext.reset()
        ch.qos.logback.classic.joran.JoranConfigurator configurator = new ch.qos.logback.classic.joran.JoranConfigurator()
        configurator.context = loggerContext
        try {
            configurator.doConfigure(Main.classLoader.getResourceAsStream('logback.xml'))
        } catch (IllegalStateException e) {
            LOGGER.debug("Failed to configure logback from XML, using default configuration: ${e.message}")
        }
    }

    /**
     * Write books data to CSV file
     * CSV format: X,Y,Z,FoundWhere,Bookname,Author,Pages
     */
    static void writeBooksCSV() {
        File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}books.csv")
        LOGGER.info("Writing books CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,Bookname,Author,Pages')

            // Write data
            bookCsvData.each { Map<String, Object> book ->
                String x = book.x != null ? book.x.toString() : ''
                String y = book.y != null ? book.y.toString() : ''
                String z = book.z != null ? book.z.toString() : ''
                String foundWhere = escapeCsvField(book.foundWhere?.toString() ?: '')
                String bookname = escapeCsvField(book.bookname?.toString() ?: '')
                String author = escapeCsvField(book.author?.toString() ?: '')
                String pages = escapeCsvField(book.pages?.toString() ?: '')

                writer.writeLine("${x},${y},${z},${foundWhere},${bookname},${author},${pages}")
            }
        }

        LOGGER.info("Books CSV written successfully with ${bookCsvData.size()} entries")
    }

    /**
     * Write signs data to CSV file
     * CSV format: X,Y,Z,FoundWhere,SignText
     */
    static void writeSignsCSV() {
        File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}signs.csv")
        LOGGER.info("Writing signs CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,SignText')

            // Write data
            signCsvData.each { Map<String, Object> sign ->
                String x = sign.x?.toString() ?: ''
                String y = sign.y?.toString() ?: ''
                String z = sign.z?.toString() ?: ''
                String foundWhere = escapeCsvField(sign.foundWhere?.toString() ?: '')
                String signText = escapeCsvField(sign.signText?.toString() ?: '')

                writer.writeLine("${x},${y},${z},${foundWhere},${signText}")
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

    static void printSummaryStatistics(long elapsedMillis) {
        File summaryFile = new File(baseDirectory, "${outputFolder}${File.separator}summary.txt")
        summaryFile.withWriter { BufferedWriter w ->
            w.writeLine('=' * 80)
            w.writeLine('SUMMARY STATISTICS')
            w.writeLine('=' * 80)

            if (enableBookExtraction) {
                w.writeLine('\nBooks:')
                w.writeLine("  Total unique books found: ${bookHashes.size()}")
                w.writeLine("  Total books extracted (including duplicates): ${bookCounter}")
                w.writeLine("  Duplicate books: ${bookCounter - bookHashes.size()}")

                // Generate ASCII table of books with titles and authors
                if (bookMetadataList) {
                    w.writeLine('\n  Books extracted:')
                    List<Column> columns = [
                        new Column().header('Title').dataAlign(HorizontalAlign.LEFT).with({ Map<String, String> book -> book.title } as java.util.function.Function),
                        new Column().header('Author').dataAlign(HorizontalAlign.LEFT).with({ Map<String, String> book -> book.author } as java.util.function.Function)
                    ]
                    String table = AsciiTable.getTable(bookMetadataList, columns)
                    w.writeLine(table)
                }

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
    }

            if (enableSignExtraction) {
                w.writeLine('\nSigns:')
                w.writeLine("  Total signs found: ${signHashes.size()}")
                if (emptySignsRemoved > 0) {
                    w.writeLine("  Empty signs removed: ${emptySignsRemoved}")
                }
            }

            w.writeLine('\nPerformance:')
            w.writeLine("  Total processing time: ${DurationFormatUtils.formatDurationWords(elapsedMillis, true, true)} (${elapsedMillis / 1000} seconds)")
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
                            LOGGER.debug("Failed to read region file ${file.name}: ${e.message}")
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
                        LOGGER.debug("Error processing entity file ${file.name}: ${e.message}")
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
        String author = tag?.getString('author') ?: ''
        String title = ''

        net.querz.nbt.tag.Tag<?> titleTag = tag?.get('title')
        if (titleTag instanceof CompoundTag) {
            // 1.20.5+ format: filterable string (compound with "raw"/"filtered" fields)
            title = ((CompoundTag) titleTag).getString('raw') ?: ((CompoundTag) titleTag).getString('filtered') ?: ''
        } else if (titleTag instanceof StringTag) {
            // Pre-1.20.5 format: plain string
            title = tag.getString('title')
        }

        LOGGER.debug("Extracted written book: \"${title}\" by ${author} (${pages.size()} pages, format: ${format})")

        bookCounter++

        // Store book metadata for summary table
        bookMetadataList.add([
            title: title ?: 'Untitled',
            author: author ?: ''
        ])

        // Extract coordinates and location info
        List<Object> locationInfo = extractLocationInfo(bookInfo)
        String coordsForFilename = locationInfo[0]
        String locationForFilename = locationInfo[1]
        Integer x = locationInfo[2]
        Integer y = locationInfo[3]
        Integer z = locationInfo[4]
        String foundWhere = locationInfo[5]

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
            bookname: title ?: 'Untitled',
            author: author ?: '',
            pages: concatenatedPages
        ])

        // New filename format: Title_(PageCount)_by_Author~location~coords.txt
        // Remove sequence number, use tilde separator
        String titlePart = sanitizeFilename(title ?: 'untitled')
        String authorPart = sanitizeFilename(author ?: 'unknown')
        String locationPart = locationForFilename.replace(':', '_')

        String baseFilename
        if (x != null && y != null && z != null) {
            // For positioned blocks: Title_(PageCount)_by_Author~block_type~X_Y_Z.txt
            baseFilename = "${titlePart}_(${pages.size()})_by_${authorPart}~${locationPart}~${x}_${y}_${z}"
        } else {
            // For inventory/ender chest: Title_(PageCount)_by_Author~container_type.txt
            baseFilename = "${titlePart}_(${pages.size()})_by_${authorPart}~${locationPart}"
        }

        // Ensure filename uniqueness by appending counter if file exists
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        String filename = "${baseFilename}.txt"
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.txt"
            bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
            counter++
        }

        // Write .txt file
        bookFile.withWriter('UTF-8') { BufferedWriter writer ->
            combinedBooksWriter?.with {
                writeLine("#region ${filename}")
                writeLine("Title: ${title}")
                writeLine("Author: ${author}")
                writeLine("Pages: ${pages.size()}")
                writeLine("Location: ${bookInfo}")
                writeLine('')
            }

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = extractTextContent(pageText)

                // Write to individual book file
                if (pc > 0) {
                    writer.writeLine(PAGE_SEPARATOR)
                }
                writer.writeLine(pageContent)

                // Write to combined books file (without "Page X:" prefix)
                if (pc > 0) {
                    combinedBooksWriter?.writeLine(PAGE_SEPARATOR)
                }
                combinedBooksWriter?.writeLine(pageContent)
            }

            combinedBooksWriter?.with {
                writeLine('')
                writeLine("#endregion ${filename}")
                writeLine('')
                flush() // Flush immediately to ensure streaming output
            }
        }

        // Write .stendhal file
        File stendhalFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename.replace('.txt', '.stendhal')}")
        stendhalFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine("title: ${title ?: 'Untitled'}")
            writer.writeLine("author: ${author ?: ''}")
            writer.writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = extractTextContent(pageText)
                writer.writeLine('#- ')
                writer.writeLine(pageContent)
            }
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

        // Store book metadata for summary table (writable books have no title/author)
        bookMetadataList.add([
            title: 'Untitled',
            author: ''
        ])

        // Extract coordinates and location info
        List<Object> locationInfo = extractLocationInfo(bookInfo)
        String locationForFilename = locationInfo[1]
        Integer x = locationInfo[2]
        Integer y = locationInfo[3]
        Integer z = locationInfo[4]
        String foundWhere = locationInfo[5]

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
            pages: concatenatedPages
        ])

        // New filename format: writable_book_(PageCount)~location~coords.txt
        String locationPart = locationForFilename.replace(':', '_')

        String baseFilename
        if (x != null && y != null && z != null) {
            baseFilename = "writable_book_(${pages.size()})~${locationPart}~${x}_${y}_${z}"
        } else {
            baseFilename = "writable_book_(${pages.size()})~${locationPart}"
        }

        // Ensure filename uniqueness by appending counter if file exists
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        String filename = "${baseFilename}.txt"
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
        int counter = 2
        while (bookFile.exists()) {
            filename = "${baseFilename}_${counter}.txt"
            bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")
            counter++
        }

        // Write .txt file
        bookFile.withWriter('UTF-8') { BufferedWriter writer ->
            combinedBooksWriter?.with {
                writeLine("#region ${filename}")
                writeLine('WRITABLE BOOK (Book & Quill)')
                writeLine("Pages: ${pages.size()}")
                writeLine("Location: ${bookInfo}")
                writeLine('')
            }

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = removeTextFormatting(pageText)

                // Write to individual book file
                if (pc > 0) {
                    writer.writeLine(PAGE_SEPARATOR)
                }
                writer.writeLine(pageContent)

                // Write to combined books file (without "Page X:" prefix)
                if (pc > 0) {
                    combinedBooksWriter?.writeLine(PAGE_SEPARATOR)
                }
                combinedBooksWriter?.writeLine(pageContent)
            }

            combinedBooksWriter?.with {
                writeLine('')
                writeLine("#endregion ${filename}")
                writeLine('')
                flush() // Flush immediately to ensure streaming output
            }
        }

        // Write .stendhal file
        File stendhalFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename.replace('.txt', '.stendhal')}")
        stendhalFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.writeLine('title: Writable Book')
            writer.writeLine('author: ')
            writer.writeLine('pages:')

            (0..<pages.size()).each { int pc ->
                String pageText = extractPageText(pages, pc)
                if (!pageText) {
                    return
                }

                String pageContent = removeTextFormatting(pageText)
                writer.writeLine('#- ')
                writer.writeLine(pageContent)
            }
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
                coordsForFilename = 'player_inventory'
                locationForFilename = 'player_inventory'
                foundWhere = 'player_inventory'
            } else if (bookInfo.contains('Ender Chest of player')) {
                coordsForFilename = 'ender_chest'
                locationForFilename = 'ender_chest'
                foundWhere = 'ender_chest'
            }
        } catch (StringIndexOutOfBoundsException e) {
            LOGGER.warn("Failed to parse bookInfo for filename: ${bookInfo}", e)
        }

        return [coordsForFilename, locationForFilename, x, y, z, foundWhere]
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
            LOGGER.info("Removed empty sign at coordinates: ${coords[0]}, ${coords[1]}, ${coords[2]}")
            return
        }

        // Pad lines to 15 characters
        String paddedLine1 = padSignLine(line1)
        String paddedLine2 = padSignLine(line2)
        String paddedLine3 = padSignLine(line3)
        String paddedLine4 = padSignLine(line4)

        // Write to file
        signWriter.with {
            write(signInfo)
            write(paddedLine1)
            write(paddedLine2)
            write(paddedLine3)
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
            signText: "${line1} ${line2} ${line3} ${line4}".trim()
        ])
    }

    /**
     * Parse sign in new format (front_text/back_text fields, introduced in 1.20)
     * The front_text/back_text compounds contain a "messages" array of 4 text component JSON strings
     */
    public static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
        LOGGER.debug('parseSignNew() - Extracting text from new format sign')

        CompoundTag frontText = getCompoundTag(tileEntity, 'front_text')
        ListTag<?> messages = getListTag(frontText, 'messages')

        if (messages.size() == 0) {
            LOGGER.debug('No messages found, returning')
            return
        }

        List<String> signLines = (0..3).collect { int i -> getStringAt(messages, i) }

        String hash = signInfo + signLines.join('')
        if (!signHashes.add(hash)) {
            LOGGER.debug('Sign is duplicate, skipping')
            return
        }

        // Extract sign text
        List<String> extractedLines = signLines.collect { String line -> extractSignLineText(line) }

        // Check if sign is completely empty
        if (extractedLines.every { it.isEmpty() }) {
            emptySignsRemoved++
            List<Object> coords = extractSignCoordinates(signInfo)
            LOGGER.info("Removed empty sign at coordinates: ${coords[0]}, ${coords[1]}, ${coords[2]}")
            return
        }

        // Pad lines to 15 characters and write to file
        signWriter.write(signInfo)
        extractedLines.each { String text ->
            signWriter.write(padSignLine(text))
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
            signText: extractedLines.join(' ').trim()
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
                    // Convert CompoundTag to JSON using org.json library
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

