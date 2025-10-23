import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import net.querz.mca.Chunk;
import net.querz.mca.LoadFlags;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


@Command(name = "ReadSignsAndBooks",
        mixinStandardHelpOptions = true,
        description = "Minecraft World Data Extractor - Extracts books and signs from Minecraft worlds",
        version = "1.0")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static ArrayList<Integer> bookHashes = new ArrayList<Integer>();
    static ArrayList<String> signHashes = new ArrayList<String>();
    static String[] colorCodes = {"\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79", "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f", "\u00A7k", "\u00A7l", "\u00A7m", "\u00A7n", "\u00A7o", "\u00A7r"};

    // Base directory for all file operations (defaults to current directory)
    static String baseDirectory = System.getProperty("user.dir");

    // Output folders
    static String outputFolder;
    static String booksFolder;
    static String duplicatesFolder;
    static String dateStamp;
    static int bookCounter = 0;

    // Statistics tracking
    static Map<String, Integer> booksByContainerType = new HashMap<>();
    static Map<String, Integer> booksByLocationType = new HashMap<>();
    static int totalSignsFound = 0;
    static int totalBooksFound = 0;
    static int totalDuplicateBooks = 0;

    // Configuration flags (set via command-line arguments using Picocli annotations)
    @Option(names = {"-w", "--world"},
            description = "Specify custom world directory (default: current directory)")
    static String customWorldDirectory = null;

    @Option(names = {"-o", "--output"},
            description = "Specify custom output directory (default: ReadBooks/YYYY-MM-DD)")
    static String customOutputDirectory = null;

    @Option(names = {"--no-books"},
            description = "Disable book extraction")
    static boolean disableBooks = false;

    @Option(names = {"--no-signs"},
            description = "Disable sign extraction")
    static boolean disableSigns = false;

    @Option(names = {"--books-only"},
            description = "Extract only books (same as --no-signs)")
    static boolean booksOnly = false;

    @Option(names = {"--signs-only"},
            description = "Extract only signs (same as --no-books)")
    static boolean signsOnly = false;

    // Derived flags
    static boolean enableBookExtraction = true;
    static boolean enableSignExtraction = true;

    public static void main(String[] args) throws IOException {
        // Parse command-line arguments using Picocli
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    static void runExtraction() throws IOException {
        // Apply flag logic
        if (booksOnly) {
            enableSignExtraction = false;
        }
        if (signsOnly) {
            enableBookExtraction = false;
        }
        if (disableBooks) {
            enableBookExtraction = false;
        }
        if (disableSigns) {
            enableSignExtraction = false;
        }

        // Reset static state (important for tests that run multiple times)
        bookHashes.clear();
        signHashes.clear();
        bookCounter = 0;
        booksByContainerType.clear();
        booksByLocationType.clear();
        totalSignsFound = 0;
        totalBooksFound = 0;
        totalDuplicateBooks = 0;

        // Set base directory from command-line or default to current directory
        if (customWorldDirectory != null) {
            baseDirectory = customWorldDirectory;
        } else {
            baseDirectory = System.getProperty("user.dir");
        }

        // Initialize logging and output folders
        dateStamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (customOutputDirectory != null) {
            outputFolder = customOutputDirectory;
        } else {
            outputFolder = "ReadBooks" + File.separator + dateStamp;
        }
        booksFolder = outputFolder + File.separator + "books";
        duplicatesFolder = booksFolder + File.separator + ".duplicates";

        // Configure Logback to write to the output folder BEFORE any logging
        System.setProperty("LOG_FILE", new File(baseDirectory, outputFolder + File.separator + "logs.txt").getAbsolutePath());

        // Reload Logback configuration to pick up the new LOG_FILE property
        ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.reset();
        ch.qos.logback.classic.joran.JoranConfigurator configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
        configurator.setContext(loggerContext);
        try {
            configurator.doConfigure(Main.class.getClassLoader().getResourceAsStream("logback.xml"));
        } catch (Exception e) {
            // Fallback to basic configuration if logback.xml is not found
            e.printStackTrace();
        }

        // Create output directories (relative to baseDirectory)
        File outputDir = new File(baseDirectory, outputFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            logger.info("Created output directory: {}", outputDir.getAbsolutePath());
        }

        File booksDirFile = new File(baseDirectory, booksFolder);
        if (!booksDirFile.exists()) {
            booksDirFile.mkdirs();
            logger.info("Created books directory: {}", booksDirFile.getAbsolutePath());
        }

        File duplicatesDirFile = new File(baseDirectory, duplicatesFolder);
        if (!duplicatesDirFile.exists()) {
            duplicatesDirFile.mkdirs();
            logger.info("Created duplicates directory: {}", duplicatesDirFile.getAbsolutePath());
        }

        logger.info("=".repeat(80));
        logger.info("ReadSignsAndBooks - Minecraft World Data Extractor");
        logger.info("Started at: {}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        logger.info("World directory: {}", baseDirectory);
        logger.info("Output folder: {}", outputFolder);
        logger.info("Book extraction: {}", enableBookExtraction ? "ENABLED" : "DISABLED");
        logger.info("Sign extraction: {}", enableSignExtraction ? "ENABLED" : "DISABLED");
        logger.info("=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            if (enableBookExtraction) {
                readPlayerData();
            }
            readSignsAndBooks();
            if (enableBookExtraction) {
                readEntities();
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // Print summary statistics
            printSummaryStatistics(elapsed);

            logger.info("{} seconds to complete.", elapsed / 1000);
        } catch (Exception e) {
            logger.error("Fatal error during execution: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Format milliseconds into human-readable time
     */
    static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Print summary statistics at the end of processing
     */
    static void printSummaryStatistics(long elapsedMillis) throws IOException {
        // Write summary to separate file
        File summaryFile = new File(baseDirectory, outputFolder + File.separator + "summary.txt");
        try (BufferedWriter summaryWriter = new BufferedWriter(new FileWriter(summaryFile))) {
            summaryWriter.write("=".repeat(80));
            summaryWriter.newLine();
            summaryWriter.write("SUMMARY STATISTICS");
            summaryWriter.newLine();
            summaryWriter.write("=".repeat(80));
            summaryWriter.newLine();

            // Books summary
            if (enableBookExtraction) {
                summaryWriter.newLine();
                summaryWriter.write("Books:");
                summaryWriter.newLine();
                summaryWriter.write("  Total unique books found: " + bookHashes.size());
                summaryWriter.newLine();
                summaryWriter.write("  Total books extracted (including duplicates): " + bookCounter);
                summaryWriter.newLine();
                summaryWriter.write("  Duplicate books: " + (bookCounter - bookHashes.size()));
                summaryWriter.newLine();

                if (!booksByLocationType.isEmpty()) {
                    summaryWriter.newLine();
                    summaryWriter.write("  Books by location type:");
                    summaryWriter.newLine();
                    booksByLocationType.entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .forEach(entry -> {
                                try {
                                    summaryWriter.write("    " + entry.getKey() + ": " + entry.getValue());
                                    summaryWriter.newLine();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }

                if (!booksByContainerType.isEmpty()) {
                    summaryWriter.newLine();
                    summaryWriter.write("  Books by container type:");
                    summaryWriter.newLine();
                    booksByContainerType.entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .forEach(entry -> {
                                try {
                                    summaryWriter.write("    " + entry.getKey() + ": " + entry.getValue());
                                    summaryWriter.newLine();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }

            // Signs summary
            if (enableSignExtraction) {
                summaryWriter.newLine();
                summaryWriter.write("Signs:");
                summaryWriter.newLine();
                summaryWriter.write("  Total signs found: " + signHashes.size());
                summaryWriter.newLine();
            }

            // Performance metrics
            summaryWriter.newLine();
            summaryWriter.write("Performance:");
            summaryWriter.newLine();
            summaryWriter.write("  Total processing time: " + formatTime(elapsedMillis) + " (" + (elapsedMillis / 1000) + " seconds)");
            summaryWriter.newLine();

            summaryWriter.newLine();
            summaryWriter.write("=".repeat(80));
            summaryWriter.newLine();
            summaryWriter.write("Completed successfully!");
            summaryWriter.newLine();
            summaryWriter.write("=".repeat(80));
            summaryWriter.newLine();
        }

        // Also log to console and logs.txt
        logger.info("");
        logger.info("{}", "=".repeat(80));
        logger.info("Summary statistics written to: {}", summaryFile.getAbsolutePath());
        logger.info("{}", "=".repeat(80));
    }

    /**
     * Increment statistics for a book found in a specific container type and location
     */
    static void incrementBookStats(String containerType, String locationType) {
        booksByContainerType.put(containerType, booksByContainerType.getOrDefault(containerType, 0) + 1);
        booksByLocationType.put(locationType, booksByLocationType.getOrDefault(locationType, 0) + 1);
    }

    public static void readSignsAndBooks() throws IOException {
        logger.info("Starting readSignsAndBooks()");

        File folder = new File(baseDirectory, "region");
        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("No region files found in: {}", folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            logger.warn("No region files found in: {}", folder.getAbsolutePath());
            return;
        }

        logger.info("Found {}", listOfFiles.length + " region files to process");

        File signOutput = new File(baseDirectory, outputFolder + File.separator + "signs.txt");
        BufferedWriter signWriter = new BufferedWriter(new FileWriter(signOutput));

        logger.info("Output files:");
        logger.info("  - Books: Individual files in {}", booksFolder);
        logger.info("  - Signs: {}", signOutput.getAbsolutePath());

        int processedFiles = 0;
        int totalChunks = 0;
        int totalSigns = 0;
        int totalBooks = 0;

        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Region files")
                .setInitialMax(listOfFiles.length)
                .setStyle(ProgressBarStyle.ASCII)
                .build()) {

            for (int f = 0; f < listOfFiles.length; f++) {
                String fileName = listOfFiles[f].getName();
                logger.debug("Processing region file [{}/{}]: {}", (f + 1), listOfFiles.length, fileName);

                signWriter.newLine();
                signWriter.write("--------------------------------" + fileName + "--------------------------------");
                signWriter.newLine();
                signWriter.newLine();

                try {
                    // Load the entire region file using Querz MCA library
                    // Note: We use RAW mode to avoid chunk format validation (supports both old and new chunk formats)
                    MCAFile mcaFile = MCAUtil.read(listOfFiles[f], LoadFlags.RAW);

                    // Iterate through all chunks in the region (32x32 grid)
                    for (int x = 0; x < 32; x++) {
                        for (int z = 0; z < 32; z++) {
                            Chunk chunk = mcaFile.getChunk(x, z);
                            if (chunk == null) {
                                continue;
                            }

                            totalChunks++;

                            // Get the raw chunk data
                            CompoundTag chunkData = chunk.getHandle();
                            if (chunkData == null) {
                                continue;
                            }

                            // Handle both old format (with "Level" tag, removed in 21w43a/1.18) and new format (without "Level" tag)
                            CompoundTag level = chunkData.getCompoundTag("Level");
                            CompoundTag chunkRoot = (level != null) ? level : chunkData;

                            // Get tile entities (block entities) from the chunk
                            ListTag<CompoundTag> tileEntities = null;
                            if (chunkRoot.containsKey("block_entities")) {
                                // New format (21w43a+): uses "block_entities"
                                tileEntities = getCompoundTagList(chunkRoot, "block_entities");
                            } else if (chunkRoot.containsKey("TileEntities")) {
                                // Old format (pre-21w43a): uses "TileEntities"
                                tileEntities = getCompoundTagList(chunkRoot, "TileEntities");
                            }
                            if (tileEntities == null) {
                                tileEntities = new ListTag<>(CompoundTag.class);
                            }

                            int chunkSigns = 0;
                            int chunkBooks = 0;

                            for (int i = 0; i < tileEntities.size(); i++) {
                                CompoundTag tileEntity = tileEntities.get(i);
                                {
                                    //If it is an item
                                    if (hasKey(tileEntity, "id")) {
                                        String blockId = tileEntity.getString("id");
                                        logger.debug("  Chunk [{}", x + "," + z + "] - Processing block entity: " + blockId);
                                        ListTag<CompoundTag> chestItems = getCompoundTagList(tileEntity, "Items");
                                        if (chestItems.size() > 0) {
                                            logger.debug("  Chunk [{},{}] - Found container {} with {} items", x, z, tileEntity.getString("id"), chestItems.size());
                                        }
                                        for (int n = 0; n < chestItems.size(); n++) {
                                            CompoundTag item = chestItems.get(n);
                                            String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + tileEntity.getInt("x") + " " + tileEntity.getInt("y") + " " + tileEntity.getInt("z") + ") " + listOfFiles[f].getName());
                                            int booksBefore = bookCounter;
                                            parseItem(item, bookInfo);
                                            if (bookCounter > booksBefore) {
                                                chunkBooks++;
                                                incrementBookStats(blockId, "Block Entity");
                                            }
                                        }
                                    }

                                    //If Lectern (stores single book in "Book" tag, not "Items")
                                    if (hasKey(tileEntity, "Book")) {
                                        CompoundTag book = getCompoundTag(tileEntity, "Book");
                                        logger.debug("  Chunk [{}", x + "," + z + "] - Found lectern with book");
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + tileEntity.getInt("x") + " " + tileEntity.getInt("y") + " " + tileEntity.getInt("z") + ") " + listOfFiles[f].getName());
                                        int booksBefore = bookCounter;
                                        parseItem(book, bookInfo);
                                        if (bookCounter > booksBefore) {
                                            chunkBooks++;
                                            incrementBookStats("Lectern", "Block Entity");
                                        }
                                    }

                                    //If Sign (old format with Text1-Text4, changed in 1.20)
                                    if (hasKey(tileEntity, "Text1")) {
                                        logger.debug("  Chunk [{},{}] - Found sign (old format) at ({} {} {})", x, z, tileEntity.getInt("x"), tileEntity.getInt("y"), tileEntity.getInt("z"));
                                        String signInfo = "Chunk [" + x + ", " + z + "]\t(" + tileEntity.getInt("x") + " " + tileEntity.getInt("y") + " " + tileEntity.getInt("z") + ")\t\t";
                                        int signsBefore = signHashes.size();
                                        parseSign(tileEntity, signWriter, signInfo);
                                        if (signHashes.size() > signsBefore) {
                                            chunkSigns++;
                                            logger.debug("    -> Sign was unique, added to output");
                                        } else {
                                            logger.debug("    -> Sign was duplicate, skipped");
                                        }
                                    }
                                    //If Sign (new format with front_text/back_text, introduced in 1.20)
                                    else if (hasKey(tileEntity, "front_text")) {
                                        logger.debug("  Chunk [{},{}] - Found sign (new format) at ({} {} {})", x, z, tileEntity.getInt("x"), tileEntity.getInt("y"), tileEntity.getInt("z"));
                                        String signInfo = "Chunk [" + x + ", " + z + "]\t(" + tileEntity.getInt("x") + " " + tileEntity.getInt("y") + " " + tileEntity.getInt("z") + ")\t\t";
                                        int signsBefore = signHashes.size();
                                        parseSignNew(tileEntity, signWriter, signInfo);
                                        if (signHashes.size() > signsBefore) {
                                            chunkSigns++;
                                            logger.debug("    -> Sign was unique, added to output");
                                        } else {
                                            logger.debug("    -> Sign was duplicate, skipped");
                                        }
                                    }
                                }
                            }

                            // Get entities from the chunk
                            // Note: In 20w45a (1.17), entities were moved to separate entity files (entities/ folder)
                            // However, for proto-chunks (incomplete generation), entities may still be in chunk data
                            ListTag<CompoundTag> entities = null;
                            if (chunkRoot.containsKey("entities")) {
                                // New format (21w43a+): uses "entities"
                                entities = getCompoundTagList(chunkRoot, "entities");
                            } else if (chunkRoot.containsKey("Entities")) {
                                // Old format (pre-21w43a): uses "Entities"
                                entities = getCompoundTagList(chunkRoot, "Entities");
                            }
                            if (entities == null) {
                                entities = new ListTag<>(CompoundTag.class);
                            }

                            for (int i = 0; i < entities.size(); i++) {
                                CompoundTag entity = entities.get(i);
                                {
                                    String entityId = entity.getString("id");

                                    //Donkey, llama, villagers, zombies, etc.
                                    if (hasKey(entity, "Items")) {
                                        ListTag<CompoundTag> entityItems = getCompoundTagList(entity, "Items");
                                        ListTag<?> entityPos = getListTag(entity, "Pos");

                                        int xPos = (int) getDoubleAt(entityPos, 0);
                                        int yPos = (int) getDoubleAt(entityPos, 1);
                                        int zPos = (int) getDoubleAt(entityPos, 2);

                                        if (entityItems.size() > 0) {
                                            logger.debug("  Chunk [{},{}] - Found {} with {} items at ({},{},{})", x, z, entityId, entityItems.size(), xPos, yPos, zPos);
                                        }

                                        for (int n = 0; n < entityItems.size(); n++) {
                                            CompoundTag item = entityItems.get(n);
                                            String bookInfo = ("Chunk [" + x + ", " + z + "] In " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                            int booksBefore = bookCounter;
                                            parseItem(item, bookInfo);
                                            if (bookCounter > booksBefore) {
                                                chunkBooks++;
                                                incrementBookStats(entityId, "Entity");
                                            }
                                        }
                                    }
                                    //Item frame or item on the ground
                                    if (hasKey(entity, "Item")) {
                                        CompoundTag item = getCompoundTag(entity, "Item");
                                        ListTag<?> entityPos = getListTag(entity, "Pos");

                                        int xPos = (int) getDoubleAt(entityPos, 0);
                                        int yPos = (int) getDoubleAt(entityPos, 1);
                                        int zPos = (int) getDoubleAt(entityPos, 2);

                                        String bookInfo = ("Chunk [" + x + ", " + z + "] In " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                        int booksBefore = bookCounter;
                                        parseItem(item, bookInfo);
                                        if (bookCounter > booksBefore) {
                                            chunkBooks++;
                                            incrementBookStats(entityId, "Entity");
                                        }
                                    }
                                }
                            }

                            totalSigns += chunkSigns;
                            totalBooks += chunkBooks;

                            if (chunkSigns > 0 || chunkBooks > 0) {
                                logger.debug("  Chunk [{}", x + "," + z + "] - Found " + chunkSigns + " signs, " + chunkBooks + " books");
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to read region file {}: {}", fileName, e.getMessage());
                    e.printStackTrace();
                }

                processedFiles++;
                logger.info("Completed region file [{}", processedFiles + "/" + listOfFiles.length + "]: " + fileName);
                pb.step();
            }
        }

        logger.info("");
        logger.info("Processing complete!");
        logger.info("Total region files processed: {}", processedFiles);
        logger.info("Total chunks processed: {}", totalChunks);
        logger.info("Total unique signs found: {}", signHashes.size());
        logger.info("Total unique books found: {}", bookHashes.size());
        logger.info("Total books extracted (including duplicates): {}", bookCounter);

        signWriter.newLine();
        signWriter.write("Completed.");
        signWriter.newLine();
        signWriter.close();

        logger.info("Output files written successfully");
    }

    /**
     * Read entities from separate entity files (introduced in 20w45a/1.17)
     * Entities like item frames, minecarts, boats are stored in entities/ folder
     * Prior to 20w45a, entities were stored within chunk data in region files
     */
    public static void readEntities() throws IOException {
        logger.info("Starting readEntities()");

        File folder = new File(baseDirectory, "entities");
        if (!folder.exists() || !folder.isDirectory()) {
            logger.debug("Entities folder not found (this is normal for pre-20w45a/1.17 worlds): {}", folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            logger.debug("No entity files found in: {}", folder.getAbsolutePath());
            return;
        }

        logger.info("Found {}", listOfFiles.length + " entity files to process");

        int processedFiles = 0;
        int totalEntities = 0;
        int entitiesWithBooks = 0;

        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Entity files")
                .setInitialMax(listOfFiles.length)
                .setStyle(ProgressBarStyle.ASCII)
                .build()) {

            for (int f = 0; f < listOfFiles.length; f++) {
                if (listOfFiles[f].isFile() && listOfFiles[f].getName().endsWith(".mca")) {
                    processedFiles++;
                    logger.debug("Processing entity file [{}/{}]: {}", processedFiles, listOfFiles.length, listOfFiles[f].getName());

                    try {
                        // Load the entire region file using Querz MCA library
                        // Note: We use RAW mode to avoid chunk format validation (supports both old and new chunk formats)
                        MCAFile mcaFile = MCAUtil.read(listOfFiles[f], LoadFlags.RAW);

                        for (int x = 0; x < 32; x++) {
                            for (int z = 0; z < 32; z++) {
                                Chunk chunk = mcaFile.getChunk(x, z);
                                if (chunk == null) {
                                    continue;
                                }

                                // Get the raw chunk data
                                CompoundTag chunkData = chunk.getHandle();
                                if (chunkData == null) {
                                    continue;
                                }

                                // Handle both old format (with "Level" tag, removed in 21w43a/1.18) and new format (without "Level" tag)
                                CompoundTag level = chunkData.getCompoundTag("Level");
                                CompoundTag chunkRoot = (level != null) ? level : chunkData;

                                // Get the entities list from the chunk
                                ListTag<CompoundTag> entities = null;
                                if (chunkRoot.containsKey("entities")) {
                                    // New format (21w43a+): uses "entities"
                                    entities = getCompoundTagList(chunkRoot, "entities");
                                } else if (chunkRoot.containsKey("Entities")) {
                                    // Old format (pre-21w43a): uses "Entities"
                                    entities = getCompoundTagList(chunkRoot, "Entities");
                                }
                                if (entities == null) {
                                    entities = new ListTag<>(CompoundTag.class);
                                }

                                for (int i = 0; i < entities.size(); i++) {
                                    totalEntities++;
                                    CompoundTag entity = entities.get(i);

                                    String entityId = entity.getString("id");
                                    ListTag<?> entityPos = getListTag(entity, "Pos");

                                    int xPos = 0, yPos = 0, zPos = 0;
                                    if (entityPos.size() >= 3) {
                                        xPos = (int) getDoubleAt(entityPos, 0);
                                        yPos = (int) getDoubleAt(entityPos, 1);
                                        zPos = (int) getDoubleAt(entityPos, 2);
                                    }

                                    int booksBefore = bookCounter;


                                    // Entities with inventory (minecarts, boats, donkeys, llamas, villagers, zombies, etc.)
                                    if (hasKey(entity, "Items")) {
                                        ListTag<CompoundTag> entityItems = getCompoundTagList(entity, "Items");

                                        if (entityItems.size() > 0) {
                                            logger.debug("  Chunk [{},{}] - Found {} with {} items at ({},{},{})", x, z, entityId, entityItems.size(), xPos, yPos, zPos);
                                        }

                                        for (int n = 0; n < entityItems.size(); n++) {
                                            CompoundTag item = entityItems.get(n);
                                            String bookInfo = ("Chunk [" + x + ", " + z + "] In " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                            int booksBeforeItem = bookCounter;
                                            parseItem(item, bookInfo);
                                            if (bookCounter > booksBeforeItem) {
                                                incrementBookStats(entityId, "Entity");
                                            }
                                        }
                                    }

                                    // Item frames and items on ground (single item)
                                    if (hasKey(entity, "Item")) {
                                        CompoundTag item = getCompoundTag(entity, "Item");
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] In " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                        logger.debug("  Chunk [{},{}] - Found {} with item at ({},{},{})", x, z, entityId, xPos, yPos, zPos);
                                        int booksBeforeItem = bookCounter;
                                        parseItem(item, bookInfo);
                                        if (bookCounter > booksBeforeItem) {
                                            incrementBookStats(entityId, "Entity");
                                        }
                                    }

                                    if (bookCounter > booksBefore) {
                                        entitiesWithBooks++;
                                    }
                                }
                            }
                        }

                        logger.info("Completed entity file [{}/{}]: {}", processedFiles, listOfFiles.length, listOfFiles[f].getName());

                    } catch (Exception e) {
                        logger.error("Error processing entity file {}: {}", listOfFiles[f].getName(), e.getMessage());
                        // Exception already logged above
                    }
                    pb.step();
                }
            }
        }

        logger.info("Entity processing complete!");
        logger.info("Total entity files processed: {}", processedFiles);
        logger.info("Total entities scanned: {}", totalEntities);
        logger.info("Entities with books: {}", entitiesWithBooks);
    }

    public static void readSignsAnvil() throws IOException {
        File folder = new File(baseDirectory, "region");
        File[] listOfFiles = folder.listFiles();
        File output = new File("signOutput.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));

        for (int f = 0; f < listOfFiles.length; f++) {
            writer.newLine();
            writer.write("--------------------------------" + listOfFiles[f].getName() + "--------------------------------");
            writer.newLine();
            writer.newLine();

            try {
                // Load the entire region file using Querz MCA library
                // Note: We use RAW mode to avoid chunk format validation (supports both old and new chunk formats)
                MCAFile mcaFile = MCAUtil.read(listOfFiles[f], LoadFlags.RAW);

                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        Chunk chunk = mcaFile.getChunk(x, z);
                        if (chunk == null) {
                            continue;
                        }

                        // Get the raw chunk data
                        CompoundTag chunkData = chunk.getHandle();
                        if (chunkData == null) {
                            continue;
                        }

                        // Handle both old format (with "Level" tag, removed in 21w43a/1.18) and new format (without "Level" tag)
                        CompoundTag level = chunkData.getCompoundTag("Level");
                        CompoundTag chunkRoot = (level != null) ? level : chunkData;

                        // Get tile entities (block entities) from the chunk
                        ListTag<CompoundTag> tileEntities = null;
                        if (chunkRoot.containsKey("block_entities")) {
                            // New format (21w43a+): uses "block_entities"
                            tileEntities = getCompoundTagList(chunkRoot, "block_entities");
                        } else if (chunkRoot.containsKey("TileEntities")) {
                            // Old format (pre-21w43a): uses "TileEntities"
                            tileEntities = getCompoundTagList(chunkRoot, "TileEntities");
                        }
                        if (tileEntities == null) {
                            tileEntities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tileEntities.size(); i++) {
                            CompoundTag entity = tileEntities.get(i);

                            //If Sign (old format, changed in 1.20)
                            if (hasKey(entity, "Text1")) {
                                String signInfo = "Chunk [" + x + ", " + z + "]\t(" + entity.getInt("x") + " " + entity.getInt("y") + " " + entity.getInt("z") + ")\t\t";
                                parseSign(entity, writer, signInfo);
                            }
                            //If Sign (new format, introduced in 1.20)
                            else if (hasKey(entity, "front_text")) {
                                String signInfo = "Chunk [" + x + ", " + z + "]\t(" + entity.getInt("x") + " " + entity.getInt("y") + " " + entity.getInt("z") + ")\t\t";
                                parseSignNew(entity, writer, signInfo);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to read region file {}: {}", listOfFiles[f].getName(), e.getMessage());
                e.printStackTrace();
            }
        }
        writer.newLine();
        writer.write("Completed.");
        writer.newLine();
        writer.close();
    }

    public static void readPlayerData() throws IOException {
        logger.info("Starting readPlayerData()");

        File folder = new File(baseDirectory, "playerdata");
        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("No player data files found in: {}", folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            logger.warn("No player data files found in: {}", folder.getAbsolutePath());
            return;
        }

        logger.info("Found {}", listOfFiles.length + " player data files to process");

        int totalPlayerBooks = 0;

        //Loop through player .dat files
        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Player data")
                .setInitialMax(listOfFiles.length)
                .setStyle(ProgressBarStyle.ASCII)
                .build()) {

            for (int i = 0; i < listOfFiles.length; i++) {
                logger.debug("Processing player data [{}/{}]: {}", (i + 1), listOfFiles.length, listOfFiles[i].getName());

                CompoundTag playerCompound = readCompressedNBT(listOfFiles[i]);
                ListTag<CompoundTag> playerInventory = getCompoundTagList(playerCompound, "Inventory");

                int playerBooks = 0;

                for (int n = 0; n < playerInventory.size(); n++) {
                    CompoundTag item = playerInventory.get(n);
                    String bookInfo = ("Inventory of player " + listOfFiles[i].getName());
                    int booksBefore = bookCounter;
                    parseItem(item, bookInfo);
                    if (bookCounter > booksBefore) {
                        playerBooks++;
                        incrementBookStats("Player Inventory", "Player");
                    }
                }

                ListTag<CompoundTag> enderInventory = getCompoundTagList(playerCompound, "EnderItems");

                for (int e = 0; e < enderInventory.size(); e++) {
                    CompoundTag item = enderInventory.get(e);
                    String bookInfo = ("Ender Chest of player " + listOfFiles[i].getName());
                    int booksBefore = bookCounter;
                    parseItem(item, bookInfo);
                    if (bookCounter > booksBefore) {
                        playerBooks++;
                        incrementBookStats("Ender Chest", "Player");
                    }
                }

                if (playerBooks > 0) {
                    logger.debug("  Found {}", playerBooks + " books in player inventory/ender chest");
                }
                totalPlayerBooks += playerBooks;

                pb.step();
                //MCR.CompoundTag nbttagcompound = MCR.CompressedStreamTools.func_1138_a(fileinputstream);
            }
        }

        logger.info("Player data processing complete!");
        logger.info("Total books found in player inventories: {}", totalPlayerBooks);
    }

    public static void readBooksAnvil() throws IOException {
        File folder = new File(baseDirectory, "region");
        File[] listOfFiles = folder.listFiles();
        File output = new File("bookOutput.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));

        for (int f = 0; f < listOfFiles.length; f++) {
            try {
                // Load the entire region file using Querz MCA library
                // Note: We use RAW mode to avoid chunk format validation (supports both old and new chunk formats)
                MCAFile mcaFile = MCAUtil.read(listOfFiles[f], LoadFlags.RAW);

                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        Chunk chunk = mcaFile.getChunk(x, z);
                        if (chunk == null) {
                            continue;
                        }

                        // Get the raw chunk data
                        CompoundTag chunkData = chunk.getHandle();
                        if (chunkData == null) {
                            continue;
                        }

                        // Handle both old format (with "Level" tag, removed in 21w43a/1.18) and new format (without "Level" tag)
                        CompoundTag level = chunkData.getCompoundTag("Level");
                        CompoundTag chunkRoot = (level != null) ? level : chunkData;

                        // Get tile entities (block entities) from the chunk
                        ListTag<CompoundTag> tileEntities = null;
                        if (chunkRoot.containsKey("block_entities")) {
                            // New format (21w43a+): uses "block_entities"
                            tileEntities = getCompoundTagList(chunkRoot, "block_entities");
                        } else if (chunkRoot.containsKey("TileEntities")) {
                            // Old format (pre-21w43a): uses "TileEntities"
                            tileEntities = getCompoundTagList(chunkRoot, "TileEntities");
                        }
                        if (tileEntities == null) {
                            tileEntities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tileEntities.size(); i++) {
                            CompoundTag tileEntity = tileEntities.get(i);
                            {
                                if (hasKey(tileEntity, "id")) //If it is an item
                                {
                                    ListTag<CompoundTag> chestItems = getCompoundTagList(tileEntity, "Items");
                                    for (int n = 0; n < chestItems.size(); n++) {
                                        CompoundTag item = chestItems.get(n);
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + tileEntity.getInt("x") + " " + tileEntity.getInt("y") + " " + tileEntity.getInt("z") + ") " + listOfFiles[f].getName());
                                        parseItem(item, bookInfo);
                                    }
                                }
                            }
                        }

                        // Get entities from the chunk
                        // Note: In 20w45a (1.17), entities were moved to separate entity files (entities/ folder)
                        // However, for proto-chunks (incomplete generation), entities may still be in chunk data
                        ListTag<CompoundTag> entities = null;
                        if (chunkRoot.containsKey("entities")) {
                            // New format (21w43a+): uses "entities"
                            entities = getCompoundTagList(chunkRoot, "entities");
                        } else if (chunkRoot.containsKey("Entities")) {
                            // Old format (pre-21w43a): uses "Entities"
                            entities = getCompoundTagList(chunkRoot, "Entities");
                        }
                        if (entities == null) {
                            entities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < entities.size(); i++) {
                            CompoundTag entity = entities.get(i);
                            {
                                //Donkey, llama etc.
                                if (hasKey(entity, "Items")) {
                                    ListTag<CompoundTag> entityItems = getCompoundTagList(entity, "Items");
                                    ListTag<?> entityPos = getListTag(entity, "Pos");

                                    int xPos = (int) getDoubleAt(entityPos, 0);
                                    int yPos = (int) getDoubleAt(entityPos, 1);
                                    int zPos = (int) getDoubleAt(entityPos, 2);

                                    for (int n = 0; n < entityItems.size(); n++) {
                                        CompoundTag item = entityItems.get(n);
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                        parseItem(item, bookInfo);
                                    }
                                }
                                //Item frame or item on the ground
                                if (hasKey(entity, "Item")) {
                                    CompoundTag item = getCompoundTag(entity, "Item");
                                    ListTag<?> entityPos = getListTag(entity, "Pos");

                                    int xPos = (int) getDoubleAt(entityPos, 0);
                                    int yPos = (int) getDoubleAt(entityPos, 1);
                                    int zPos = (int) getDoubleAt(entityPos, 2);

                                    String bookInfo = ("Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());

                                    parseItem(item, bookInfo);
                                    //System.out.println(item);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to read region file {}: {}", listOfFiles[f].getName(), e.getMessage());
                e.printStackTrace();
            }
        }
        writer.newLine();
        writer.write("Completed.");
        writer.newLine();
        writer.close();
    }

    /**
     * Parse an item and check if it's a book or a container with books.
     * <p>
     * MINECRAFT STORAGE CONTAINERS - COMPREHENSIVE LIST (from minecraft.wiki/w/Category:Storage)
     * <p>
     * ✅ SUPPORTED - Can hold books and are detected:
     * - Barrel (block entity with Items)
     * - Blast Furnace (block entity with Items)
     * - Boat with Chest (entity with Items) - all 9 wood variants
     * - Bundle (item with bundle_contents component) - all 16+ color variants
     * - Chest (block entity with Items)
     * - Chiseled Bookshelf (block entity with Items)
     * - Copper Sink (block entity with Items) - if it exists in vanilla
     * - Decorated Pot (block entity with Items)
     * - Dispenser (block entity with Items)
     * - Dropper (block entity with Items)
     * - Ender Chest (stored in player data)
     * - Furnace (block entity with Items)
     * - Golden Chest (block entity with Items) - if it exists in vanilla
     * - Glow Item Frame (entity with Item)
     * - Hopper (block entity with Items)
     * - Item Frame (entity with Item)
     * - Lectern (block entity with Book tag) - holds single book
     * - Minecart with Chest (entity with Items)
     * - Minecart with Hopper (entity with Items)
     * - Shelf (block entity with Items)
     * - Shulker Box (item/block with container component) - all 17 color variants
     * - Smoker (block entity with Items)
     * - Trapped Chest (block entity with Items)
     * <p>
     * ❌ CANNOT HOLD BOOKS - These containers have restrictions:
     * - Armor Stand (can only hold armor/equipment, not books)
     * - Brewing Stand (can only hold potions/ingredients, not books)
     * - Campfire (can only hold 4 food items for cooking, not books)
     * - Soul Campfire (can only hold 4 food items for cooking, not books)
     * - Cauldron (holds liquids/powders, not items)
     * - Flower Pot (can only hold flowers/plants, not books)
     * - Jukebox (can only hold music discs, not books)
     * <p>
     * DETECTION METHODS:
     * 1. Block entities: Detected by scanning "Items" NBT tag in tile entities
     * 2. Entity containers: Detected by scanning "Items" or "Item" NBT tag in entities
     * 3. Item containers: Detected by itemId and scanning nested components
     * 4. Player inventory: Detected by scanning player data files
     */
    public static void parseItem(CompoundTag item, String bookInfo) throws IOException {
        String itemId = item.getString("id");

        if (itemId.equals("minecraft:written_book")) {
            logger.debug("    Found written book: {}...", bookInfo.substring(0, Math.min(80, bookInfo.length())));
            readWrittenBook(item, bookInfo);
        }
        if (itemId.equals("minecraft:writable_book")) {
            logger.debug("    Found writable book: {}...", bookInfo.substring(0, Math.min(80, bookInfo.length())));
            readWritableBook(item, bookInfo);
        }
        if (itemId.contains("shulker_box")) {
            logger.debug("    Found shulker box, scanning contents...");

            // Try new format first (1.20.5+ with components)
            // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:container")) {
                    ListTag<CompoundTag> shelkerContents = getCompoundTagList(components, "minecraft:container");
                    logger.debug("      Shulker box contains {} items (1.20.5+ format)", shelkerContents.size());
                    for (int i = 0; i < shelkerContents.size(); i++) {
                        CompoundTag containerEntry = shelkerContents.get(i);
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, "item");
                        parseItem(shelkerItem, bookInfo + " > shulker_box");
                    }
                }
            } else if (hasKey(item, "tag")) {
                // Old format (pre-1.20.5)
                CompoundTag shelkerCompound = getCompoundTag(item, "tag");
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, "BlockEntityTag");
                ListTag<CompoundTag> shelkerContents = getCompoundTagList(shelkerCompound2, "Items");
                logger.debug("      Shulker box contains {} items (pre-1.20.5 format)", shelkerContents.size());
                for (int i = 0; i < shelkerContents.size(); i++) {
                    CompoundTag shelkerItem = shelkerContents.get(i);
                    parseItem(shelkerItem, bookInfo + " > shulker_box");
                }
            }
        }

        // Bundle support (1.20.5+)
        // Matches: minecraft:bundle, minecraft:white_bundle, minecraft:orange_bundle, etc.
        // Note: minecraft:bundle_contents stores items as a direct list of ItemStacks (not slot records like containers)
        if (itemId.contains("bundle")) {
            logger.debug("    Found bundle, scanning contents...");

            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:bundle_contents")) {
                    ListTag<CompoundTag> bundleContents = getCompoundTagList(components, "minecraft:bundle_contents");
                    logger.debug("      Bundle contains {} items", bundleContents.size());
                    for (int i = 0; i < bundleContents.size(); i++) {
                        CompoundTag bundleItem = bundleContents.get(i);
                        parseItem(bundleItem, bookInfo + " > bundle");
                    }
                }
            }
        }

        // Copper chest support (various oxidation states)
        // Matches: minecraft:copper_chest, minecraft:exposed_copper_chest, minecraft:weathered_copper_chest,
        //          minecraft:oxidized_copper_chest, and waxed variants
        // Note: minecraft:container stores items as a list of slot records: {slot: int, item: ItemStack}
        if (itemId.contains("copper_chest")) {
            logger.debug("    Found copper chest, scanning contents...");

            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:container")) {
                    ListTag<CompoundTag> chestContents = getCompoundTagList(components, "minecraft:container");
                    logger.debug("      Copper chest contains {} items", chestContents.size());
                    for (int i = 0; i < chestContents.size(); i++) {
                        CompoundTag containerEntry = chestContents.get(i);
                        CompoundTag chestItem = getCompoundTag(containerEntry, "item");
                        parseItem(chestItem, bookInfo + " > copper_chest");
                    }
                }
            } else if (hasKey(item, "tag")) {
                // Old format (if copper chests existed in pre-1.20.5)
                CompoundTag chestCompound = getCompoundTag(item, "tag");
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, "BlockEntityTag");
                ListTag<CompoundTag> chestContents = getCompoundTagList(chestCompound2, "Items");
                logger.debug("      Copper chest contains {} items (pre-1.20.5 format)", chestContents.size());
                for (int i = 0; i < chestContents.size(); i++) {
                    CompoundTag chestItem = chestContents.get(i);
                    parseItem(chestItem, bookInfo + " > copper_chest");
                }
            }
        }

        // Note: Lecterns are handled as block entities with "Book" tag (not "Items")
        // They are scanned separately in the block entity processing code
        // Decorated pots are handled as block entities with "Items" tag (standard container)
    }

    /**
     * Sanitize a string to be used as a filename
     */
    static String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed";
        }
        // Remove or replace invalid filename characters
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        // Limit length to 50 characters
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    private static void readWrittenBook(CompoundTag item, String bookInfo) throws IOException {
        // Try both old format (pre-1.20.5 with "tag") and new format (1.20.5+ with "components")
        CompoundTag tag = null;
        ListTag<?> pages = null;
        String format = "unknown";

        if (hasKey(item, "tag")) {
            // Pre-1.20.5 format
            tag = getCompoundTag(item, "tag");
            pages = getListTag(tag, "pages");
            format = "pre-1.20.5";
        } else if (hasKey(item, "components")) {
            // 1.20.5+ format
            CompoundTag components = getCompoundTag(item, "components");
            if (hasKey(components, "minecraft:written_book_content")) {
                CompoundTag bookContent = getCompoundTag(components, "minecraft:written_book_content");
                pages = getListTag(bookContent, "pages"); // In 1.20.5+, pages are compounds, not strings
                tag = bookContent; // Use bookContent as tag for author/title
                format = "1.20.5+";
            }
        }

        if (pages == null || pages.size() == 0) {
            logger.debug("      Written book has no pages (format: {})", format);
            return;
        }

        // Check if this is a duplicate
        boolean isDuplicate = bookHashes.contains(pages.hashCode());
        if (isDuplicate) {
            logger.debug("      Written book is a duplicate - saving to .duplicates folder");
        } else {
            bookHashes.add(pages.hashCode());
        }

        // Extract author and title - handle both old format (plain string) and new format (filterable string)
        String author = "";
        String title = "";

        // IMPORTANT: In 1.20.5+, author is a plain STRING, but title is a filterable string (CompoundTag with "raw"/"filtered")
        // In pre-1.20.5, both are plain strings
        if (tag.containsKey("author")) {
            // Author is always a plain string in both formats
            author = tag.getString("author");
        }

        if (tag.containsKey("title")) {
            Tag<?> titleTag = tag.get("title");
            if (titleTag instanceof CompoundTag titleComp) {
                // 1.20.5+ format: filterable string (compound with "raw"/"filtered" fields)
                if (titleComp.containsKey("raw")) {
                    title = titleComp.getString("raw");
                } else if (titleComp.containsKey("filtered")) {
                    title = titleComp.getString("filtered");
                }
            } else if (titleTag instanceof StringTag) {
                // Pre-1.20.5 format: plain string
                title = tag.getString("title");
            }
        }

        logger.debug("      Extracted written book: \"{}\" by {} ({} pages, format: {})", title, author, pages.size(), format);

        // Create individual file for this book
        bookCounter++;
        String filename = String.format("%03d_written_%s_by_%s.txt", bookCounter, sanitizeFilename(title), sanitizeFilename(author));

        // Save to duplicates folder if it's a duplicate, otherwise to main books folder
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder;
        File bookFile = new File(baseDirectory, targetFolder + File.separator + filename);
        BufferedWriter writer = new BufferedWriter(new FileWriter(bookFile));

        logger.debug("      Writing book to: {}{}", (isDuplicate ? ".duplicates/" : ""), filename);

        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("WRITTEN BOOK");
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("Title: " + title);
        writer.newLine();
        writer.write("Author: " + author);
        writer.newLine();
        writer.write("Pages: " + pages.size());
        writer.newLine();
        writer.write("Format: " + format);
        writer.newLine();
        writer.write("Location: " + bookInfo);
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.newLine();
        for (int pc = 0; pc < pages.size(); pc++) {
            String pageText = null;

            // Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
            // In 1.20.5+, pages are filterable text components (compound with "raw"/"filtered" fields containing Component JSON)
            if (isStringList(pages)) {
                // String list (pre-1.20.5)
                pageText = getStringAt(pages, pc);
            } else if (isCompoundList(pages)) {
                // Compound list (1.20.5+): filterable text components
                CompoundTag pageCompound = getCompoundAt(pages, pc);
                if (hasKey(pageCompound, "raw")) {
                    pageText = pageCompound.getString("raw");
                } else if (hasKey(pageCompound, "filtered")) {
                    pageText = pageCompound.getString("filtered");
                }
            }

            if (pageText == null || pageText.isEmpty()) {
                continue;
            }

            JSONObject pageJSON = null;
            if (pageText.startsWith("{")) //If valid JSON
            {
                try {
                    pageJSON = new JSONObject(pageText);
                } catch (JSONException e) {
                    pageJSON = null;
                }
            }

            writer.write("Page " + pc + ": ");

            //IF VALID JSON
            if (pageJSON != null) {
                if (pageJSON.has("extra")) {

                    for (int h = 0; h < pageJSON.getJSONArray("extra").length(); h++) {
                        if (pageJSON.getJSONArray("extra").get(h) instanceof String)
                            writer.write(removeTextFormatting(pageJSON.getJSONArray("extra").get(0).toString()));
                        else {
                            JSONObject temp = (JSONObject) pageJSON.getJSONArray("extra").get(h);
                            writer.write(removeTextFormatting(temp.get("text").toString()));
                        }
                    }
                } else if (pageJSON.has("text"))
                    writer.write(removeTextFormatting(pageJSON.getString("text")));
            } else
                writer.write(removeTextFormatting(pageText));

            writer.newLine();
        }

        writer.close();
    }

    private static void readWritableBook(CompoundTag item, String bookInfo) throws IOException {
        // Try both old format (pre-1.20.5 with "tag") and new format (1.20.5+ with "components")
        ListTag<?> pages = null;
        String format = "unknown";

        if (hasKey(item, "tag")) {
            // Pre-1.20.5 format
            CompoundTag tag = getCompoundTag(item, "tag");
            pages = getListTag(tag, "pages");
            format = "pre-1.20.5";
        } else if (hasKey(item, "components")) {
            // 1.20.5+ format
            CompoundTag components = getCompoundTag(item, "components");
            if (hasKey(components, "minecraft:writable_book_content")) {
                CompoundTag bookContent = getCompoundTag(components, "minecraft:writable_book_content");
                pages = getListTag(bookContent, "pages"); // In 1.20.5+, pages are compounds
                format = "1.20.5+";
            }
        }

        if (pages == null || pages.size() == 0) {
            logger.debug("      Writable book has no pages (format: {})", format);
            return;
        }

        // Check if this is a duplicate
        boolean isDuplicate = bookHashes.contains(pages.hashCode());
        if (isDuplicate) {
            logger.debug("      Writable book is a duplicate - saving to .duplicates folder");
        } else {
            bookHashes.add(pages.hashCode());
        }

        logger.debug("      Extracted writable book ({} pages, format: {})", pages.size(), format);

        // Create individual file for this book
        bookCounter++;
        String filename = String.format("%03d_writable_book.txt", bookCounter);

        // Save to duplicates folder if it's a duplicate, otherwise to main books folder
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder;
        File bookFile = new File(baseDirectory, targetFolder + File.separator + filename);
        BufferedWriter writer = new BufferedWriter(new FileWriter(bookFile));

        logger.debug("      Writing book to: {}{}", (isDuplicate ? ".duplicates/" : ""), filename);

        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("WRITABLE BOOK (Book & Quill)");
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("Pages: " + pages.size());
        writer.newLine();
        writer.write("Format: " + format);
        writer.newLine();
        writer.write("Location: " + bookInfo);
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.newLine();

        for (int pc = 0; pc < pages.size(); pc++) {
            String pageText = null;

            // Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
            // In 1.20.5+, pages are filterable strings (compound with "raw"/"filtered" fields containing plain strings)
            if (isStringList(pages)) {
                // String list (pre-1.20.5)
                pageText = getStringAt(pages, pc);
            } else if (isCompoundList(pages)) {
                // Compound list (1.20.5+): filterable strings
                CompoundTag pageCompound = getCompoundAt(pages, pc);
                if (hasKey(pageCompound, "raw")) {
                    pageText = pageCompound.getString("raw");
                } else if (hasKey(pageCompound, "filtered")) {
                    pageText = pageCompound.getString("filtered");
                }
            }

            if (pageText == null || pageText.isEmpty()) {
                continue;
            }

            writer.write("Page " + (pc + 1) + ":");
            writer.newLine();
            writer.write(removeTextFormatting(pageText));
            writer.newLine();
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Parse sign in old format (Text1-Text4 fields, used before 1.20)
     */
    private static void parseSign(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) throws IOException {
        logger.debug("    parseSign() - Extracting text from old format sign");
        String text1 = tileEntity.getString("Text1");
        String text2 = tileEntity.getString("Text2");
        String text3 = tileEntity.getString("Text3");
        String text4 = tileEntity.getString("Text4");
        logger.debug("    parseSign() - Raw text: [{}", text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");

        JSONObject json1 = null;
        JSONObject json2 = null;
        JSONObject json3 = null;
        JSONObject json4 = null;

        String[] signLines = {text1, text2, text3, text4};

        // Create hash based on LOCATION + TEXT to count all physical signs
        // (not just unique text content like we did before)
        String hash = signInfo + text1 + text2 + text3 + text4;
        logger.debug("    parseSign() - Sign hash (location+text): [{}", hash + "]");
        logger.debug("    parseSign() - Current signHashes size: {}", signHashes.size());
        if (signHashes.contains(hash)) {
            logger.debug("    parseSign() - Sign is duplicate (same location+text), returning early");
            return;
        } else {
            signHashes.add(hash);
            logger.debug("    parseSign() - Sign is unique, added to hash list");
        }

        JSONObject[] objects = {json1, json2, json3, json4};

        signWriter.write(signInfo);

        for (int j = 0; j < 4; j++) {
            if (signLines[j].startsWith("{")) {
                try {
                    objects[j] = new JSONObject(signLines[j]);
                } catch (JSONException e) {
                    objects[j] = null;
                }
            }
        }

        for (int o = 0; o < 4; o++) {
            if (objects[o] != null) {
                if (objects[o].has("extra")) {
                    Object extraObj = objects[o].get("extra");
                    if (extraObj instanceof JSONArray) {
                        JSONArray extraArray = (JSONArray) extraObj;
                        for (int h = 0; h < extraArray.length(); h++) {
                            if ((extraArray.get(h) instanceof String))
                                signWriter.write(extraArray.get(h).toString());
                            else {
                                JSONObject temp = (JSONObject) extraArray.get(h);
                                if (temp.has("text"))
                                    signWriter.write(temp.get("text").toString());
                            }
                        }
                    } else if (extraObj instanceof JSONObject) {
                        JSONObject extraObject = (JSONObject) extraObj;
                        if (extraObject.has("text"))
                            signWriter.write(extraObject.get("text").toString());
                    }
                } else if (objects[o].has("text"))
                    signWriter.write(objects[o].getString("text"));
            } else if ((!signLines[o].equals("\"\"")) && (!signLines[o].equals("null")))
                signWriter.write(signLines[o]);

            signWriter.write(" ");
        }
        signWriter.newLine();
    }

    /**
     * Parse sign in new format (front_text/back_text fields, introduced in 1.20)
     * The front_text/back_text compounds contain a "messages" array of 4 text component JSON strings
     */
    private static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) throws IOException {
        logger.debug("    parseSignNew() - Extracting text from new format sign");
        // Get front_text compound
        CompoundTag frontText = getCompoundTag(tileEntity, "front_text");

        // Extract messages array (contains 4 text component JSON lines)
        ListTag<?> messages = getListTag(frontText, "messages");

        if (messages.size() == 0) {
            logger.debug("    parseSignNew() - No messages found, returning");
            return;
        }

        String text1 = getStringAt(messages, 0);
        String text2 = getStringAt(messages, 1);
        String text3 = getStringAt(messages, 2);
        String text4 = getStringAt(messages, 3);
        logger.debug("    parseSignNew() - Raw text: [{}", text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");

        JSONObject json1 = null;
        JSONObject json2 = null;
        JSONObject json3 = null;
        JSONObject json4 = null;

        String[] signLines = {text1, text2, text3, text4};

        // Create hash based on LOCATION + TEXT to count all physical signs
        // (not just unique text content like we did before)
        String hash = signInfo + text1 + text2 + text3 + text4;
        logger.debug("    parseSignNew() - Sign hash (location+text): [{}", hash + "]");
        logger.debug("    parseSignNew() - Current signHashes size: {}", signHashes.size());
        if (signHashes.contains(hash)) {
            logger.debug("    parseSignNew() - Sign is duplicate (same location+text), returning early");
            return;
        } else {
            signHashes.add(hash);
            logger.debug("    parseSignNew() - Sign is unique, added to hash list");
        }

        JSONObject[] objects = {json1, json2, json3, json4};

        signWriter.write(signInfo);

        for (int j = 0; j < 4; j++) {
            if (signLines[j].startsWith("{")) {
                try {
                    objects[j] = new JSONObject(signLines[j]);
                } catch (JSONException e) {
                    objects[j] = null;
                }
            }
        }

        for (int o = 0; o < 4; o++) {
            if (objects[o] != null) {
                if (objects[o].has("extra")) {
                    Object extraObj = objects[o].get("extra");
                    if (extraObj instanceof JSONArray) {
                        JSONArray extraArray = (JSONArray) extraObj;
                        for (int h = 0; h < extraArray.length(); h++) {
                            if ((extraArray.get(h) instanceof String))
                                signWriter.write(extraArray.get(h).toString());
                            else {
                                JSONObject temp = (JSONObject) extraArray.get(h);
                                if (temp.has("text"))
                                    signWriter.write(temp.get("text").toString());
                            }
                        }
                    } else if (extraObj instanceof JSONObject) {
                        JSONObject extraObject = (JSONObject) extraObj;
                        if (extraObject.has("text"))
                            signWriter.write(extraObject.get("text").toString());
                    }
                } else if (objects[o].has("text"))
                    signWriter.write(objects[o].getString("text"));
            } else if ((!signLines[o].equals("\"\"")) && (!signLines[o].equals("null")))
                signWriter.write(signLines[o]);

            signWriter.write(" ");
        }
        signWriter.newLine();
    }

    public static String removeTextFormatting(String text) {
        for (int i = 0; i < colorCodes.length; i++)
            text = text.replace(colorCodes[i], "");
        return text;
    }

    /**
     * Read NBT data from a File (automatically handles compression)
     */
    private static CompoundTag readCompressedNBT(File file) throws IOException {
        NamedTag namedTag = NBTUtil.read(file);
        return (CompoundTag) namedTag.getTag();
    }

    // ========== Querz NBT Helper Methods ==========
    // Minimal helper methods - Querz library already provides safe getters that return defaults for missing/null keys

    /**
     * Check if a CompoundTag has a specific key (null-safe)
     */
    private static boolean hasKey(CompoundTag tag, String key) {
        return tag != null && tag.containsKey(key);
    }

    /**
     * Get a CompoundTag from a CompoundTag, returns empty CompoundTag if not found
     * (Querz returns null, we prefer empty CompoundTag for easier chaining)
     */
    private static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        if (tag == null) {
            return new CompoundTag();
        }
        CompoundTag result = tag.getCompoundTag(key);
        return result != null ? result : new CompoundTag();
    }

    /**
     * Get a ListTag as CompoundTagList from a CompoundTag, returns empty ListTag if not found
     * Uses Querz's type-safe asCompoundTagList() method
     */
    private static ListTag<CompoundTag> getCompoundTagList(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return new ListTag<>(CompoundTag.class);
        }
        ListTag<?> list = tag.getListTag(key);
        if (list == null || list.size() == 0) {
            return new ListTag<>(CompoundTag.class);
        }
        try {
            return list.asCompoundTagList();
        } catch (ClassCastException e) {
            // List exists but is not a CompoundTag list
            return new ListTag<>(CompoundTag.class);
        }
    }

    /**
     * Get a generic ListTag from a CompoundTag, returns empty ListTag if not found
     */
    private static ListTag<?> getListTag(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return ListTag.createUnchecked(Object.class);
        }
        ListTag<?> list = tag.getListTag(key);
        return list != null ? list : ListTag.createUnchecked(Object.class);
    }

    /**
     * Get a double value from a ListTag at a specific index (for position coordinates)
     * Returns 0.0 if index is out of bounds or tag is not a number
     */
    private static double getDoubleAt(ListTag<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return 0.0;
        }
        try {
            Tag<?> tag = list.get(index);
            if (tag instanceof NumberTag) {
                return ((NumberTag<?>) tag).asDouble();
            } else if (tag instanceof StringTag) {
                // Some old formats store positions as strings
                return Double.parseDouble(((StringTag) tag).getValue());
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0.0;
    }

    /**
     * Get a string value from a ListTag at a specific index
     * Returns empty string if index is out of bounds
     */
    private static String getStringAt(ListTag<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return "";
        }
        try {
            Tag<?> tag = list.get(index);
            if (tag instanceof StringTag) {
                return ((StringTag) tag).getValue();
            }
            return tag.valueToString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Get a CompoundTag from a ListTag at a specific index
     * Returns empty CompoundTag if index is out of bounds
     */
    private static CompoundTag getCompoundAt(ListTag<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return new CompoundTag();
        }
        try {
            Tag<?> tag = list.get(index);
            if (tag instanceof CompoundTag) {
                return (CompoundTag) tag;
            }
        } catch (Exception e) {
            // Ignore
        }
        return new CompoundTag();
    }

    /**
     * Check if a ListTag contains string elements (type ID 8)
     */
    private static boolean isStringList(ListTag<?> list) {
        if (list == null || list.size() == 0) {
            return false;
        }
        return list.getTypeClass() == StringTag.class;
    }

    /**
     * Check if a ListTag contains compound elements (type ID 10)
     */
    private static boolean isCompoundList(ListTag<?> list) {
        if (list == null || list.size() == 0) {
            return false;
        }
        return list.getTypeClass() == CompoundTag.class;
    }

    @Override
    public void run() {
        try {
            runExtraction();
        } catch (IOException e) {
            System.err.println("Error during extraction: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
