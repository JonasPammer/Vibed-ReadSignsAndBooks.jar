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

    private static final Logger logger = LoggerFactory.getLogger(Main)
    private static final JsonSlurper jsonSlurper = new JsonSlurper()
    private static final String[] COLOR_CODES = ["\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75",
                                                 "\u00A76", "\u00A77", "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
                                                 "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f", "\u00A7k", "\u00A7l",
                                                 "\u00A7m", "\u00A7n", "\u00A7o", "\u00A7r"]

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
        System.exit(new CommandLine(new Main()).execute(args))
    }

    @Override
    void run() {
        try {
            runExtraction()
        } catch (Exception e) {
            logger.error("Error during extraction: ${e.message}", e)
            System.exit(1)
        }
    }

    static void runExtraction() {
        // Apply flag logic
        enableSignExtraction = !(booksOnly || disableSigns)
        enableBookExtraction = !(signsOnly || disableBooks)

        // Reset state
        [bookHashes, signHashes, booksByContainerType, booksByLocationType].each { it.clear() }
        bookCounter = 0

        // Set directories
        baseDirectory = customWorldDirectory ?: System.getProperty('user.dir')
        dateStamp = new SimpleDateFormat('yyyy-MM-dd').format(new Date())
        outputFolder = customOutputDirectory ?: "ReadBooks${File.separator}${dateStamp}"
        booksFolder = "${outputFolder}${File.separator}books"
        duplicatesFolder = "${booksFolder}${File.separator}.duplicates"

        // Configure logging
        System.setProperty('LOG_FILE', new File(baseDirectory, "${outputFolder}${File.separator}logs.txt").absolutePath)
        reloadLogbackConfiguration()

        // Create directories
        [outputFolder, booksFolder, duplicatesFolder].each {
            new File(baseDirectory, it).mkdirs()
        }

        logger.info('=' * 80)
        logger.info('ReadSignsAndBooks - Minecraft World Data Extractor')
        logger.info("Started at: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())}")
        logger.info("World directory: ${baseDirectory}")
        logger.info("Output folder: ${outputFolder}")
        logger.info("Book extraction: ${enableBookExtraction}")
        logger.info("Sign extraction: ${enableSignExtraction}")
        logger.info('=' * 80)

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
            logger.info("${elapsed / 1000} seconds to complete.")
        } catch (Exception e) {
            logger.error("Fatal error: ${e.message}", e)
            combinedBooksWriter?.close()
            throw e
        }
    }

    static void reloadLogbackConfiguration() {
        def loggerContext = LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
        loggerContext.reset()
        def configurator = new ch.qos.logback.classic.joran.JoranConfigurator()
        configurator.context = loggerContext
        try {
            configurator.doConfigure(Main.classLoader.getResourceAsStream('logback.xml'))
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    static String formatTime(long millis) {
        long seconds = millis / 1000
        long minutes = seconds / 60
        long hours = minutes / 60

        if (hours > 0) return String.format('%dh %dm %ds', hours, minutes % 60, seconds % 60)
        if (minutes > 0) return String.format('%dm %ds', minutes, seconds % 60)
        return "${seconds}s"
    }

    static void printSummaryStatistics(long elapsedMillis) {
        def summaryFile = new File(baseDirectory, "${outputFolder}${File.separator}summary.txt")
        summaryFile.withWriter { w ->
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
                    booksByLocationType.sort { -it.value }.each { k, v ->
                        w.writeLine("    ${k}: ${v}")
                }
            }

                if (booksByContainerType) {
                    w.writeLine('\n  Books by container type:')
                    booksByContainerType.sort { -it.value }.each { k, v ->
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

        logger.info("\n${'=' * 80}")
        logger.info("Summary statistics written to: ${summaryFile.absolutePath}")
        logger.info('=' * 80)
    }

    static void incrementBookStats(String containerType, String locationType) {
        booksByContainerType[containerType] = (booksByContainerType[containerType] ?: 0) + 1
        booksByLocationType[locationType] = (booksByLocationType[locationType] ?: 0) + 1
    }

    static void readPlayerData() {
        logger.debug('Starting readPlayerData()')
        def folder = new File(baseDirectory, 'playerdata')

        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("No player data files found in: ${folder.absolutePath}")
            return
        }

        def files = folder.listFiles()
        if (!files) {
            logger.warn("No player data files found in: ${folder.absolutePath}")
            return
        }

        logger.debug("Found ${files.length} player data files to process")

        new ProgressBarBuilder()
                .setTaskName('Player data')
                .setInitialMax(files.length)
                .setStyle(ProgressBarStyle.ASCII)
                .build().withCloseable { pb ->
                            files.each { file ->
                    logger.debug("Processing player data: ${file.name}")
                    def playerCompound = readCompressedNBT(file)

                    // Process inventory
                    getCompoundTagList(playerCompound, 'Inventory').each { item ->
                        def booksBefore = bookCounter
                        parseItem(item, "Inventory of player ${file.name}")
                        if (bookCounter > booksBefore) {
                            incrementBookStats('Player Inventory', 'Player')
                        }
                    }

                    // Process ender chest
                    getCompoundTagList(playerCompound, 'EnderItems').each { item ->
                        def booksBefore = bookCounter
                        parseItem(item, "Ender Chest of player ${file.name}")
                        if (bookCounter > booksBefore) {
                            incrementBookStats('Ender Chest', 'Player')
                        }
                    }

                    pb.step()
                            }
                }

        logger.debug('Player data processing complete!')
    }

    static void readSignsAndBooks() {
        logger.debug('Starting readSignsAndBooks()')
        def folder = new File(baseDirectory, 'region')

        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("No region files found in: ${folder.absolutePath}")
            return
        }

        def files = folder.listFiles()
        if (!files) {
            logger.warn("No region files found in: ${folder.absolutePath}")
            return
        }

        logger.debug("Found ${files.length} region files to process")

        def signOutput = new File(baseDirectory, "${outputFolder}${File.separator}signs.txt")
        signOutput.withWriter { signWriter ->
            new ProgressBarBuilder()
                    .setTaskName('Region files')
                    .setInitialMax(files.length)
                    .setStyle(ProgressBarStyle.ASCII)
                    .build().withCloseable { pb ->
                                files.each { file ->
                        logger.debug("Processing region file: ${file.name}")

                        try {
                            def mcaFile = MCAUtil.read(file, LoadFlags.RAW)

                            (0..31).each { x ->
                                (0..31).each { z ->
                                    def chunk = mcaFile.getChunk(x, z)
                                    if (!chunk) return

                                    def chunkData = chunk.handle
                                    if (!chunkData) return

                                    // Handle both old and new chunk formats
                                    def level = chunkData.getCompoundTag('Level')
                                    def chunkRoot = level ?: chunkData

                                    // Process block entities (chests, signs, etc.)
                                    def tileEntities = chunkRoot.containsKey('block_entities') ?
                                        getCompoundTagList(chunkRoot, 'block_entities') :
                                        getCompoundTagList(chunkRoot, 'TileEntities')

                                    tileEntities.each { tileEntity ->
                                        def blockId = tileEntity.getString('id')

                                        // Process containers with items
                                        if (hasKey(tileEntity, 'id')) {
                                            getCompoundTagList(tileEntity, 'Items').each { item ->
                                                def bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')}) ${file.name}"
                                                def booksBefore = bookCounter
                                                parseItem(item, bookInfo)
                                                if (bookCounter > booksBefore) {
                                                    incrementBookStats(blockId, 'Block Entity')
                                                }
                                            }
                                        }

                                        // Process lecterns (single book)
                                        if (hasKey(tileEntity, 'Book')) {
                                            def book = getCompoundTag(tileEntity, 'Book')
                                            def bookInfo = "Chunk [${x}, ${z}] Inside ${blockId} at (${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')}) ${file.name}"
                                            def booksBefore = bookCounter
                                            parseItem(book, bookInfo)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats('Lectern', 'Block Entity')
                                            }
                                        }

                                        // Process signs
                                        def signInfo = "Chunk [${x}, ${z}]\t(${tileEntity.getInt('x')} ${tileEntity.getInt('y')} ${tileEntity.getInt('z')})\t\t"
                                        if (hasKey(tileEntity, 'Text1')) {
                                            parseSign(tileEntity, signWriter, signInfo)
                                    } else if (hasKey(tileEntity, 'front_text')) {
                                            parseSignNew(tileEntity, signWriter, signInfo)
                                        }
                                    }

                                    // Process entities in chunk (for proto-chunks)
                                    def entities = chunkRoot.containsKey('entities') ?
                                        getCompoundTagList(chunkRoot, 'entities') :
                                        getCompoundTagList(chunkRoot, 'Entities')

                                    entities.each { entity ->
                                        def entityId = entity.getString('id')
                                        def entityPos = getListTag(entity, 'Pos')
                                        def xPos = getDoubleAt(entityPos, 0) as int
                                        def yPos = getDoubleAt(entityPos, 1) as int
                                        def zPos = getDoubleAt(entityPos, 2) as int

                                        // Entities with inventory
                                        if (hasKey(entity, 'Items')) {
                                            getCompoundTagList(entity, 'Items').each { item ->
                                                def bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                                def booksBefore = bookCounter
                                                parseItem(item, bookInfo)
                                                if (bookCounter > booksBefore) {
                                                    incrementBookStats(entityId, 'Entity')
                                                }
                                            }
                                        }

                                        // Item frames and items on ground
                                        if (hasKey(entity, 'Item')) {
                                            def item = getCompoundTag(entity, 'Item')
                                            def bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                            def booksBefore = bookCounter
                                            parseItem(item, bookInfo)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats(entityId, 'Entity')
                                            }
                                        }
                                    }
                                }
                            }
                    } catch (IOException e) {
                            logger.debug("Failed to read region file ${file.name}: ${e.message}")
                        }

                        pb.step()
                                }
                    }

            signWriter.writeLine('\nCompleted.')
        }

        logger.debug('Processing complete!')
        logger.debug("Total unique signs found: ${signHashes.size()}")
        logger.debug("Total unique books found: ${bookHashes.size()}")
    }

    static void readEntities() {
        logger.debug('Starting readEntities()')
        def folder = new File(baseDirectory, 'entities')

        if (!folder.exists() || !folder.isDirectory()) {
            logger.debug("Entities folder not found (normal for pre-1.17 worlds): ${folder.absolutePath}")
            return
        }

        def files = folder.listFiles()?.findAll { it.isFile() && it.name.endsWith('.mca') }
        if (!files) {
            logger.debug("No entity files found in: ${folder.absolutePath}")
            return
        }

        logger.debug("Found ${files.size()} entity files to process")

        new ProgressBarBuilder()
                .setTaskName('Entity files')
                .setInitialMax(files.size())
                .setStyle(ProgressBarStyle.ASCII)
                .build().withCloseable { pb ->
                            files.each { file ->
                    logger.debug("Processing entity file: ${file.name}")

                    try {
                        def mcaFile = MCAUtil.read(file, LoadFlags.RAW)

                        (0..31).each { x ->
                            (0..31).each { z ->
                                def chunk = mcaFile.getChunk(x, z)
                                if (!chunk) return

                                def chunkData = chunk.handle
                                if (!chunkData) return

                                def level = chunkData.getCompoundTag('Level')
                                def chunkRoot = level ?: chunkData

                                def entities = chunkRoot.containsKey('entities') ?
                                    getCompoundTagList(chunkRoot, 'entities') :
                                    getCompoundTagList(chunkRoot, 'Entities')

                                entities.each { entity ->
                                    def entityId = entity.getString('id')
                                    def entityPos = getListTag(entity, 'Pos')
                                    def xPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 0) as int : 0
                                    def yPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 1) as int : 0
                                    def zPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 2) as int : 0

                                    // Entities with inventory
                                    if (hasKey(entity, 'Items')) {
                                        getCompoundTagList(entity, 'Items').each { item ->
                                            def bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                            def booksBefore = bookCounter
                                            parseItem(item, bookInfo)
                                            if (bookCounter > booksBefore) {
                                                incrementBookStats(entityId, 'Entity')
                                            }
                                        }
                                    }

                                    // Item frames and items on ground
                                    if (hasKey(entity, 'Item')) {
                                        def item = getCompoundTag(entity, 'Item')
                                        def bookInfo = "Chunk [${x}, ${z}] In ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
                                        def booksBefore = bookCounter
                                        parseItem(item, bookInfo)
                                        if (bookCounter > booksBefore) {
                                            incrementBookStats(entityId, 'Entity')
                                        }
                                    }
                                }
                            }
                        }
                } catch (Exception e) {
                        logger.debug("Error processing entity file ${file.name}: ${e.message}")
                    }

                    pb.step()
                            }
                }

        logger.debug('Entity processing complete!')
    }

    static void parseItem(CompoundTag item, String bookInfo) {
        def itemId = item.getString('id')

        if (itemId == 'minecraft:written_book') {
            logger.debug("Found written book: ${bookInfo.take(80)}...")
            readWrittenBook(item, bookInfo)
        }

        if (itemId == 'minecraft:writable_book') {
            logger.debug("Found writable book: ${bookInfo.take(80)}...")
            readWritableBook(item, bookInfo)
        }

        // Shulker boxes
        if (itemId.contains('shulker_box')) {
            logger.debug('Found shulker box, scanning contents...')

            if (hasKey(item, 'components')) {
                def components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { containerEntry ->
                        def shelkerItem = getCompoundTag(containerEntry, 'item')
                        parseItem(shelkerItem, "${bookInfo} > shulker_box")
                    }
                }
            } else if (hasKey(item, 'tag')) {
                def shelkerCompound = getCompoundTag(item, 'tag')
                def shelkerCompound2 = getCompoundTag(shelkerCompound, 'BlockEntityTag')
                getCompoundTagList(shelkerCompound2, 'Items').each { shelkerItem ->
                    parseItem(shelkerItem, "${bookInfo} > shulker_box")
                }
            }
        }

        // Bundles
        if (itemId.contains('bundle')) {
            logger.debug('Found bundle, scanning contents...')

            if (hasKey(item, 'components')) {
                def components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:bundle_contents')) {
                    getCompoundTagList(components, 'minecraft:bundle_contents').each { bundleItem ->
                        parseItem(bundleItem, "${bookInfo} > bundle")
                    }
                }
            }
        }

        // Copper chests
        if (itemId.contains('copper_chest')) {
            logger.debug('Found copper chest, scanning contents...')

            if (hasKey(item, 'components')) {
                def components = getCompoundTag(item, 'components')
                if (hasKey(components, 'minecraft:container')) {
                    getCompoundTagList(components, 'minecraft:container').each { containerEntry ->
                        def chestItem = getCompoundTag(containerEntry, 'item')
                        parseItem(chestItem, "${bookInfo} > copper_chest")
                    }
                }
            } else if (hasKey(item, 'tag')) {
                def chestCompound = getCompoundTag(item, 'tag')
                def chestCompound2 = getCompoundTag(chestCompound, 'BlockEntityTag')
                getCompoundTagList(chestCompound2, 'Items').each { chestItem ->
                    parseItem(chestItem, "${bookInfo} > copper_chest")
                }
            }
        }
    }

    static String sanitizeFilename(String name) {
        if (!name) return 'unnamed'
        name.replaceAll(/[\\/:*?<>|]/, '_').take(200)
    }

    static void readWrittenBook(CompoundTag item, String bookInfo) {
        def tag, pages, format

        // Try both old and new formats
        if (hasKey(item, 'tag')) {
            tag = getCompoundTag(item, 'tag')
            pages = getListTag(tag, 'pages')
            format = 'pre-1.20.5'
        } else if (hasKey(item, 'components')) {
            def components = getCompoundTag(item, 'components')
            if (hasKey(components, 'minecraft:written_book_content')) {
                def bookContent = getCompoundTag(components, 'minecraft:written_book_content')
                pages = getListTag(bookContent, 'pages')
                tag = bookContent
                format = '1.20.5+'
            }
        }

        if (!pages || pages.size() == 0) {
            logger.debug("Written book has no pages (format: ${format})")
            return
        }

        // Check for duplicates
        def isDuplicate = !bookHashes.add(pages.hashCode())
        if (isDuplicate) {
            logger.debug('Written book is a duplicate - saving to .duplicates folder')
        }

        // Extract author and title
        def author = tag?.getString('author') ?: ''
        def title = ''

        def titleTag = tag?.get('title')
        if (titleTag instanceof CompoundTag) {
            // 1.20.5+ format
            title = titleTag.getString('raw') ?: titleTag.getString('filtered') ?: ''
        } else if (titleTag instanceof StringTag) {
            // Pre-1.20.5 format
            title = tag.getString('title')
        }

        logger.debug("Extracted written book: \"${title}\" by ${author} (${pages.size()} pages, format: ${format})")

        bookCounter++

        // Extract coordinates for filename
        def (coordsForFilename, locationForFilename) = extractLocationInfo(bookInfo)

        def filename = sanitizeFilename(String.format('%03d_', bookCounter) + "${title ?: 'untitled'}_by_${author ?: 'unknown'}_at_${coordsForFilename}_${locationForFilename}_pages_1-${pages.size()}.txt")
        def targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        def bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")

        bookFile.withWriter { writer ->
            combinedBooksWriter?.with {
                writeLine("#region ${filename}")
                writeLine("Title: ${title}")
                writeLine("Author: ${author}")
                writeLine("Pages: ${pages.size()}")
                writeLine("Location: ${bookInfo}")
                writeLine('')
            }

            (0..<pages.size()).each { pc ->
                def pageText = extractPageText(pages, pc)
                if (!pageText) return

                def pageContent = extractTextContent(pageText)

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
        def pages, format

        if (hasKey(item, 'tag')) {
            def tag = getCompoundTag(item, 'tag')
            pages = getListTag(tag, 'pages')
            format = 'pre-1.20.5'
        } else if (hasKey(item, 'components')) {
            def components = getCompoundTag(item, 'components')
            if (hasKey(components, 'minecraft:writable_book_content')) {
                def bookContent = getCompoundTag(components, 'minecraft:writable_book_content')
                pages = getListTag(bookContent, 'pages')
                format = '1.20.5+'
            }
        }

        if (!pages || pages.size() == 0) {
            logger.debug("Writable book has no pages (format: ${format})")
            return
        }

        def isDuplicate = !bookHashes.add(pages.hashCode())
        if (isDuplicate) {
            logger.debug('Writable book is a duplicate - saving to .duplicates folder')
        }

        logger.debug("Extracted writable book (${pages.size()} pages, format: ${format})")

        bookCounter++

        def (coordsForFilename, locationForFilename) = extractLocationInfo(bookInfo)
        def filename = sanitizeFilename(String.format('%03d_', bookCounter) + "writable_book_at_${coordsForFilename}_${locationForFilename}_pages_1-${pages.size()}.txt")
        def targetFolder = isDuplicate ? duplicatesFolder : booksFolder
        def bookFile = new File(baseDirectory, "${targetFolder}${File.separator}${filename}")

        bookFile.withWriter { writer ->
            combinedBooksWriter?.with {
                writeLine("#region ${filename}")
                writeLine('WRITABLE BOOK (Book & Quill)')
                writeLine("Pages: ${pages.size()}")
                writeLine("Location: ${bookInfo}")
                writeLine('')
            }

            (0..<pages.size()).each { pc ->
                def pageText = extractPageText(pages, pc)
                if (!pageText) return

                def pageContent = removeTextFormatting(pageText)

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

    static List extractLocationInfo(String bookInfo) {
        def coordsForFilename = 'unknown'
        def locationForFilename = 'unknown'

        try {
            if (bookInfo.contains(' at (')) {
                def atIndex = bookInfo.indexOf(' at (')
                def endParenIndex = bookInfo.indexOf(')', atIndex)
                if (endParenIndex > atIndex) {
                    coordsForFilename = bookInfo.substring(atIndex + 5, endParenIndex).replace(' ', '_')
                }

                if (bookInfo.contains('Inside ')) {
                    def insideIndex = bookInfo.indexOf('Inside ') + 7
                    locationForFilename = bookInfo.substring(insideIndex, atIndex).trim()
                } else if (bookInfo.contains('In ')) {
                    def inIndex = bookInfo.indexOf('In ') + 3
                    locationForFilename = bookInfo.substring(inIndex, atIndex).trim()
                }
            } else if (bookInfo.contains('Inventory of player')) {
                coordsForFilename = 'player_inventory'
                locationForFilename = 'player_inventory'
            } else if (bookInfo.contains('Ender Chest of player')) {
                coordsForFilename = 'ender_chest'
                locationForFilename = 'ender_chest'
            }
        } catch (Exception e) {
            logger.warn("Failed to parse bookInfo for filename: ${bookInfo}")
        }

        [coordsForFilename, locationForFilename]
    }

    static String extractPageText(ListTag<?> pages, int index) {
        if (isStringList(pages)) {
            return getStringAt(pages, index)
        } else if (isCompoundList(pages)) {
            def pageCompound = getCompoundAt(pages, index)
            return pageCompound.getString('raw') ?: pageCompound.getString('filtered') ?: ''
        }
        return ''
    }

    static String extractTextContent(String pageText) {
        if (!pageText) return ''

        if (!pageText.startsWith('{')) {
            return removeTextFormatting(pageText)
        }

        try {
            def pageJSON = jsonSlurper.parseText(pageText)

            if (pageJSON.extra) {
                return pageJSON.extra.collect { item ->
                    if (item instanceof String) {
                        removeTextFormatting(item)
                    } else {
                        removeTextFormatting(item.text ?: '')
                    }
                }.join('')
            } else if (pageJSON.text) {
                return removeTextFormatting(pageJSON.text)
            }
        } catch (Exception e) {
        // Not valid JSON, return as-is
        }

        return removeTextFormatting(pageText)
    }

    static void parseSign(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
        logger.debug('parseSign() - Extracting text from old format sign')

        // Get the StringTag objects and extract their values
        String text1 = ((StringTag) tileEntity.get("Text1")).getValue()
        String text2 = ((StringTag) tileEntity.get("Text2")).getValue()
        String text3 = ((StringTag) tileEntity.get("Text3")).getValue()
        String text4 = ((StringTag) tileEntity.get("Text4")).getValue()

        def hash = signInfo + text1 + text2 + text3 + text4
        if (!signHashes.add(hash)) {
            logger.debug('Sign is duplicate, skipping')
            return
        }

        signWriter.write(signInfo)
        signWriter.write(extractSignLineText(text1) + " ")
        signWriter.write(extractSignLineText(text2) + " ")
        signWriter.write(extractSignLineText(text3) + " ")
        signWriter.write(extractSignLineText(text4) + " ")
        signWriter.newLine()
    }

    static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) {
        logger.debug('parseSignNew() - Extracting text from new format sign')

        def frontText = getCompoundTag(tileEntity, 'front_text')
        def messages = getListTag(frontText, 'messages')

        if (messages.size() == 0) {
            logger.debug('No messages found, returning')
            return
        }

        def signLines = (0..3).collect { getStringAt(messages, it) }

        def hash = signInfo + signLines.join('')
        if (!signHashes.add(hash)) {
            logger.debug('Sign is duplicate, skipping')
            return
        }

        signWriter.write(signInfo)

        signLines.each { line ->
            def text = extractSignLineText(line)
            signWriter.write("${text} ")
        }

        signWriter.newLine()
    }

    static String extractSignLineText(String line) {
        if (!line || line == '' || line == 'null') return ''
        if (!line.startsWith('{')) return line

        try {
            def json = new JSONObject(line)

            if (json.has('extra')) {
                def extra = json.get('extra')
                if (extra instanceof JSONArray) {
                    def sb = new StringBuilder()
                    def extraArray = (JSONArray) extra
                    for (int i = 0; i < extraArray.length(); i++) {
                        def item = extraArray.get(i)
                        if (item instanceof String) {
                            sb.append(item)
                        } else if (item instanceof JSONObject) {
                            def temp = (JSONObject) item
                            if (temp.has('text')) {
                                sb.append(temp.get('text'))
                            }
                        }
                    }
                    return sb.toString()
                } else if (extra instanceof JSONObject) {
                    def extraObj = (JSONObject) extra
                    if (extraObj.has('text')) {
                        return extraObj.get('text').toString()
                    }
                }
            } else if (json.has('text')) {
                def text = json.get('text').toString()
                // Filter out empty text with only empty key-value pairs
                if (text == '' && json.length() == 1) {
                    return ''
                }
                return text
            }
        } catch (JSONException e) {
            // Not valid JSON
        }

        // Filter out empty JSON objects like {"":""} or {}
        if (line == '{}' || line == '{"":""}') {
            return ''
        }

        return line
    }

    static String removeTextFormatting(String text) {
        if (!text) return ''
        COLOR_CODES.inject(text) { result, code -> result.replace(code, '') }
    }

    static CompoundTag readCompressedNBT(File file) {
        def namedTag = NBTUtil.read(file)
        (CompoundTag) namedTag.tag
    }

    // ========== NBT Helper Methods ==========

    static boolean hasKey(CompoundTag tag, String key) {
        tag != null && tag.containsKey(key)
    }

    static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        if (!tag) return new CompoundTag()
        tag.getCompoundTag(key) ?: new CompoundTag()
    }

    static ListTag<CompoundTag> getCompoundTagList(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return new ListTag<>(CompoundTag.class)
        }
        def list = tag.getListTag(key)
        if (!list || list.size() == 0) {
            return new ListTag<>(CompoundTag.class)
        }
        try {
            return list.asCompoundTagList()
        } catch (ClassCastException e) {
            return new ListTag<>(CompoundTag.class)
        }
    }

    static ListTag<?> getListTag(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return ListTag.createUnchecked(Object.class)
        }
        tag.getListTag(key) ?: ListTag.createUnchecked(Object.class)
    }

    static double getDoubleAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) return 0.0

        try {
            def tag = list.get(index)
            if (tag instanceof NumberTag) {
                return ((NumberTag<?>) tag).asDouble()
            } else if (tag instanceof StringTag) {
                return Double.parseDouble(((StringTag) tag).value)
            }
        } catch (Exception e) {
        // Ignore
        }
        return 0.0
    }

    static String getStringAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) return ''

        try {
            def tag = list.get(index)
            if (tag instanceof StringTag) {
                return ((StringTag) tag).getValue()
            } else if (tag instanceof CompoundTag) {
                // Convert CompoundTag to JSON using org.json library
                return convertNbtToJson((CompoundTag) tag).toString()
            }
            return tag.valueToString()
        } catch (Exception e) {
            return ''
        }
    }

    /**
     * Converts a CompoundTag (NBT) to a JSONObject.
     * This handles the NBT -> JSON conversion for Minecraft text components.
     */
    static JSONObject convertNbtToJson(CompoundTag tag) {
        def json = new JSONObject()

        tag.forEach { key, value ->
            if (value instanceof StringTag) {
                json.put(key, ((StringTag) value).getValue())
            } else if (value instanceof NumberTag) {
                json.put(key, ((NumberTag) value).asNumber())
            } else if (value instanceof CompoundTag) {
                json.put(key, convertNbtToJson((CompoundTag) value))
            } else if (value instanceof ListTag) {
                json.put(key, convertNbtListToJsonArray((ListTag<?>) value))
            } else {
                json.put(key, value.getValue())
            }
        }

        return json
    }

    /**
     * Converts a ListTag (NBT) to a JSONArray.
     */
    static JSONArray convertNbtListToJsonArray(ListTag<?> list) {
        def array = new JSONArray()

        for (int i = 0; i < list.size(); i++) {
            def tag = list.get(i)

            if (tag instanceof StringTag) {
                array.put(((StringTag) tag).getValue())
            } else if (tag instanceof NumberTag) {
                array.put(((NumberTag) tag).asNumber())
            } else if (tag instanceof CompoundTag) {
                array.put(convertNbtToJson((CompoundTag) tag))
            } else if (tag instanceof ListTag) {
                array.put(convertNbtListToJsonArray((ListTag<?>) tag))
            } else {
                array.put(tag.getValue())
            }
        }

        return array
    }

    static String getStringFrom(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) return ''

        try {
            def value = tag.get(key)
            if (value == null) return ''

            logger.debug("getStringFrom() - key: ${key}, value type: ${value.getClass().name}, value: ${value}")

            // Check if it's a StringTag and get the value
            if (value instanceof net.querz.nbt.tag.StringTag) {
                def result = ((net.querz.nbt.tag.StringTag) value).getValue()
                logger.debug("getStringFrom() - returning: ${result}")
                return result
            }

            logger.debug("getStringFrom() - value is not a StringTag, returning empty string")
            return ''
        } catch (Exception e) {
            logger.error("getStringFrom() - exception: ${e.message}", e)
            return ''
        }
    }

    static CompoundTag getCompoundAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) return new CompoundTag()

        try {
            def tag = list.get(index)
            if (tag instanceof CompoundTag) {
                return (CompoundTag) tag
            }
        } catch (Exception e) {
        // Ignore
        }
        return new CompoundTag()
    }

    static boolean isStringList(ListTag<?> list) {
        list && list.size() > 0 && list.typeClass == StringTag.class
    }

    static boolean isCompoundList(ListTag<?> list) {
        list && list.size() > 0 && list.typeClass == CompoundTag.class
    }

}

