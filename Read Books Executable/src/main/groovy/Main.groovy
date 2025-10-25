/* groovylint-disable ClassSize */
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
    static BufferedWriter combinedBooksWriter

    @Option(names = ['-w', '--world'], description = 'Specify custom world directory')
    static String customWorldDirectory

    @Option(names = ['-o', '--output'], description = 'Specify custom output directory')
    static String customOutputDirectory

    @Option(names = ['--no-books'], description = 'Disable book extraction')
    static boolean disableBooks = false

    @Option(names = ['--no-signs'], description = 'Disable sign extraction')
    static boolean disableSigns = false

    @Option(names = ['--books-only'], description = 'Extract only books')
    static boolean booksOnly = false

    @Option(names = ['--signs-only'], description = 'Extract only signs')
    static boolean signsOnly = false

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
        enableSignExtraction = !(booksOnly || disableSigns)
        enableBookExtraction = !(signsOnly || disableBooks)

        // Reset state
        [bookHashes, signHashes, booksByContainerType, booksByLocationType].each { collection -> collection.clear() }
        bookCounter = 0

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

    static String formatTime(long millis) {
        long seconds = millis / 1000
        long minutes = seconds / 60
        long hours = minutes / 60

        if (hours > 0) {
            return String.format('%dh %dm %ds', hours, minutes % 60, seconds % 60)
        }
        if (minutes > 0) {
            return String.format('%dm %ds', minutes, seconds % 60)
        }
        return "${seconds}s"
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
            }

            w.writeLine('\nPerformance:')
            w.writeLine("  Total processing time: ${formatTime(elapsedMillis)} (${elapsedMillis / 1000} seconds)")
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

        File signOutput = new File(baseDirectory, "${outputFolder}${File.separator}signs.txt")
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

    static void parseItem(CompoundTag item, String bookInfo) {
        String itemId = item.getString('id')

        if (itemId == 'minecraft:written_book') {
            LOGGER.debug("Found written book: ${bookInfo.take(80)}...")
            readWrittenBook(item, bookInfo)
        }

        if (itemId == 'minecraft:writable_book') {
            LOGGER.debug("Found writable book: ${bookInfo.take(80)}...")
            readWritableBook(item, bookInfo)
        }

        // Shulker boxes
        if (itemId.contains('shulker_box')) {
            LOGGER.debug('Found shulker box, scanning contents...')

            if (hasKey(item, 'components')) {
                CompoundTag components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { CompoundTag containerEntry ->
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, 'item')
                        parseItem(shelkerItem, "${bookInfo} > shulker_box")
                    }
                }
            } else if (hasKey(item, 'tag')) {
                CompoundTag shelkerCompound = getCompoundTag(item, 'tag')
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, 'BlockEntityTag')
                getCompoundTagList(shelkerCompound2, 'Items').each { CompoundTag shelkerItem ->
                    parseItem(shelkerItem, "${bookInfo} > shulker_box")
                }
            }
        }

        // Bundles
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

        // Copper chests
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
                CompoundTag chestCompound = getCompoundTag(item, 'tag')
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, 'BlockEntityTag')
                getCompoundTagList(chestCompound2, 'Items').each { CompoundTag chestItem ->
                    parseItem(chestItem, "${bookInfo} > copper_chest")
                }
            }
        }
    }

    static String sanitizeFilename(String name) {
        if (!name) {
            return 'unnamed'
        }
        return name.replaceAll(/[\\/:*?<>|]/, '_').take(200)
    }

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

        // Check for duplicates
        boolean isDuplicate = !bookHashes.add(pages.hashCode())
        if (isDuplicate) {
            LOGGER.debug('Written book is a duplicate - saving to .duplicates folder')
        }

        // Extract author and title
        String author = tag?.getString('author') ?: ''
        String title = ''

        net.querz.nbt.tag.Tag<?> titleTag = tag?.get('title')
        if (titleTag instanceof CompoundTag) {
            // 1.20.5+ format
            title = ((CompoundTag) titleTag).getString('raw') ?: ((CompoundTag) titleTag).getString('filtered') ?: ''
        } else if (titleTag instanceof StringTag) {
            // Pre-1.20.5 format
            title = tag.getString('title')
        }

        LOGGER.debug("Extracted written book: \"${title}\" by ${author} (${pages.size()} pages, format: ${format})")

        bookCounter++

        // Extract coordinates for filename
        List<String> locationInfo = extractLocationInfo(bookInfo)
        String coordsForFilename = locationInfo[0]
        String locationForFilename = locationInfo[1]

        String filename = sanitizeFilename(String.format('%03d_', bookCounter) + "${title ?: 'untitled'}_by_${author ?: 'unknown'}_at_${coordsForFilename}_${locationForFilename}_pages_1-${pages.size()}.txt")
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")

        bookFile.withWriter { BufferedWriter writer ->
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

                writer.writeLine(pageContent)
                combinedBooksWriter?.writeLine("Page ${pc + 1}: ${pageContent}")
            }

            combinedBooksWriter?.with {
                writeLine('')
                writeLine("#endregion ${filename}")
                writeLine('')
            }
        }
    }

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

        List<String> locationInfo = extractLocationInfo(bookInfo)
        String coordsForFilename = locationInfo[0]
        String locationForFilename = locationInfo[1]
        String filename = sanitizeFilename(String.format('%03d_', bookCounter) + "writable_book_at_${coordsForFilename}_${locationForFilename}_pages_1-${pages.size()}.txt")
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        File bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")

        bookFile.withWriter { BufferedWriter writer ->
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

                writer.writeLine(pageContent)
                combinedBooksWriter?.with {
                    writeLine("Page ${pc + 1}:")
                    writeLine(pageContent)
                    writeLine('')
                }
            }

            combinedBooksWriter?.with {
                writeLine('')
                writeLine("#endregion ${filename}")
                writeLine('')
            }
        }
    }

    static List<String> extractLocationInfo(String bookInfo) {
        String coordsForFilename = 'unknown'
        String locationForFilename = 'unknown'

        try {
            if (bookInfo.contains(' at (')) {
                int atIndex = bookInfo.indexOf(' at (')
                int endParenIndex = bookInfo.indexOf(')', atIndex)
                if (endParenIndex > atIndex && atIndex + 5 < bookInfo.length()) {
                    coordsForFilename = bookInfo.substring(atIndex + 5, endParenIndex).replace(' ', '_')
                }

                if (bookInfo.contains('Inside ')) {
                    int insideIndex = bookInfo.indexOf('Inside ') + 7
                    if (insideIndex < atIndex && insideIndex < bookInfo.length()) {
                        locationForFilename = bookInfo.substring(insideIndex, atIndex).trim()
                    }
                } else if (bookInfo.contains('In ')) {
                    int inIndex = bookInfo.indexOf('In ') + 3
                    if (inIndex < atIndex && inIndex < bookInfo.length()) {
                        locationForFilename = bookInfo.substring(inIndex, atIndex).trim()
                    }
                }
            } else if (bookInfo.contains('Inventory of player')) {
                coordsForFilename = 'player_inventory'
                locationForFilename = 'player_inventory'
            } else if (bookInfo.contains('Ender Chest of player')) {
                coordsForFilename = 'ender_chest'
                locationForFilename = 'ender_chest'
            }
        } catch (StringIndexOutOfBoundsException e) {
            LOGGER.warn("Failed to parse bookInfo for filename: ${bookInfo}", e)
        }

        return [coordsForFilename, locationForFilename]
    }

    static String extractPageText(ListTag<?> pages, int index) {
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

        signWriter.with {
            write(signInfo)
            write(extractSignLineText(text1) + ' ')
            write(extractSignLineText(text2) + ' ')
            write(extractSignLineText(text3) + ' ')
            write(extractSignLineText(text4) + ' ')
            newLine()
        }
    }

    static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
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

        signWriter.write(signInfo)

        signLines.each { String line ->
            String text = extractSignLineText(line)
            signWriter.write("${text} ")
        }

        signWriter.newLine()
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

