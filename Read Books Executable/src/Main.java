import net.querz.mca.Chunk;
import net.querz.mca.LoadFlags;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.json.JSONException;
import org.json.JSONObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    static ArrayList<Integer> bookHashes = new ArrayList<Integer>();
    static ArrayList<String> signHashes = new ArrayList<String>();
    static String[] colorCodes = {"\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79", "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f", "\u00A7k", "\u00A7l", "\u00A7m", "\u00A7n", "\u00A7o", "\u00A7r"};

    // Base directory for all file operations (defaults to current directory)
    static String baseDirectory = System.getProperty("user.dir");

    // Logging infrastructure
    static BufferedWriter logWriter;
    static String outputFolder;
    static String booksFolder;
    static String duplicatesFolder;
    static String dateStamp;
    static SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
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

        // Create output directories (relative to baseDirectory)
        File outputDir = new File(baseDirectory, outputFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            System.out.println("Created output directory: " + outputDir.getAbsolutePath());
        }

        File booksDirFile = new File(baseDirectory, booksFolder);
        if (!booksDirFile.exists()) {
            booksDirFile.mkdirs();
            System.out.println("Created books directory: " + booksDirFile.getAbsolutePath());
        }

        File duplicatesDirFile = new File(baseDirectory, duplicatesFolder);
        if (!duplicatesDirFile.exists()) {
            duplicatesDirFile.mkdirs();
            System.out.println("Created duplicates directory: " + duplicatesDirFile.getAbsolutePath());
        }

        // Initialize log file (relative to baseDirectory)
        File logFile = new File(baseDirectory, outputFolder + File.separator + "logs.txt");
        logWriter = new BufferedWriter(new FileWriter(logFile));

        log("INFO", "=".repeat(80));
        log("INFO", "ReadSignsAndBooks - Minecraft World Data Extractor");
        log("INFO", "Started at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        log("INFO", "World directory: " + baseDirectory);
        log("INFO", "Output folder: " + outputFolder);
        log("INFO", "Book extraction: " + (enableBookExtraction ? "ENABLED" : "DISABLED"));
        log("INFO", "Sign extraction: " + (enableSignExtraction ? "ENABLED" : "DISABLED"));
        log("INFO", "=".repeat(80));

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

            System.out.println(elapsed / 1000 + " seconds to complete.");
        } catch (Exception e) {
            log("ERROR", "Fatal error during execution: " + e.getMessage());
            logException(e);
            throw e;
        } finally {
            if (logWriter != null) {
                logWriter.close();
            }
        }
    }

    /**
     * Log a message to both console and log file
     */
    static void log(String level, String message) {
        String timestamp = logTimeFormat.format(new Date());
        String logLine = "[" + timestamp + "] [" + level + "] " + message;
        System.out.println(logLine);
        try {
            if (logWriter != null) {
                logWriter.write(logLine);
                logWriter.newLine();
                logWriter.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Log an exception with full stack trace
     */
    static void logException(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        log("ERROR", "Exception stack trace:\n" + sw);
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
        log("INFO", "");
        log("INFO", "=".repeat(80));
        log("INFO", "Summary statistics written to: " + summaryFile.getAbsolutePath());
        log("INFO", "=".repeat(80));
    }

    /**
     * Increment statistics for a book found in a specific container type and location
     */
    static void incrementBookStats(String containerType, String locationType) {
        booksByContainerType.put(containerType, booksByContainerType.getOrDefault(containerType, 0) + 1);
        booksByLocationType.put(locationType, booksByLocationType.getOrDefault(locationType, 0) + 1);
    }

    public static void readSignsAndBooks() throws IOException {
        log("INFO", "Starting readSignsAndBooks()");

        File folder = new File(baseDirectory, "region");
        if (!folder.exists() || !folder.isDirectory()) {
            log("WARN", "No region files found in: " + folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            log("WARN", "No region files found in: " + folder.getAbsolutePath());
            return;
        }

        log("INFO", "Found " + listOfFiles.length + " region files to process");

        File signOutput = new File(baseDirectory, outputFolder + File.separator + "signs.txt");
        BufferedWriter signWriter = new BufferedWriter(new FileWriter(signOutput));

        log("INFO", "Output files:");
        log("INFO", "  - Books: Individual files in " + booksFolder);
        log("INFO", "  - Signs: " + signOutput.getAbsolutePath());

        int processedFiles = 0;
        int totalChunks = 0;
        int totalSigns = 0;
        int totalBooks = 0;

        for (int f = 0; f < listOfFiles.length; f++) {
            String fileName = listOfFiles[f].getName();
            log("DEBUG", "Processing region file [" + (f + 1) + "/" + listOfFiles.length + "]: " + fileName);

            signWriter.newLine();
            signWriter.write("--------------------------------" + fileName + "--------------------------------");
            signWriter.newLine();
            signWriter.newLine();

            try {
                // Load the entire region file using Querz MCA library
                // Note: We use RAW mode to avoid chunk format validation (supports both pre-1.18 and 1.18+ formats)
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

                        // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
                        CompoundTag level = chunkData.getCompoundTag("Level");
                        CompoundTag chunkRoot = (level != null) ? level : chunkData;

                        // Get tile entities from the chunk
                        ListTag<CompoundTag> tileEntities = null;
                        if (chunkRoot.containsKey("block_entities")) {
                            // 1.18+ format uses "block_entities"
                            tileEntities = getTagList(chunkRoot, "block_entities", 10);
                        } else if (chunkRoot.containsKey("TileEntities")) {
                            // Pre-1.18 format uses "TileEntities"
                            tileEntities = getTagList(chunkRoot, "TileEntities", 10);
                        }
                        if (tileEntities == null) {
                            tileEntities = new ListTag<>(CompoundTag.class);
                        }

                        int chunkSigns = 0;
                        int chunkBooks = 0;

                        for (int i = 0; i < tagCount(tileEntities); i++) {
                            CompoundTag tileEntity = getCompoundTagAt(tileEntities, i);
                            {
                                //If it is an item
                                if (hasKey(tileEntity, "id")) {
                                    String blockId = getString(tileEntity, "id");
                                    log("DEBUG", "  Chunk [" + x + "," + z + "] - Processing block entity: " + blockId);
                                    ListTag<CompoundTag> chestItems = getTagList(tileEntity, "Items", 10);
                                    if (tagCount(chestItems) > 0) {
                                        log("DEBUG", "  Chunk [" + x + "," + z + "] - Found container " + getString(tileEntity, "id") + " with " + tagCount(chestItems) + " items");
                                    }
                                    for (int n = 0; n < tagCount(chestItems); n++) {
                                        CompoundTag item = getCompoundTagAt(chestItems, n);
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + getString(tileEntity, "id") + " at (" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ") " + listOfFiles[f].getName());
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
                                    log("DEBUG", "  Chunk [" + x + "," + z + "] - Found lectern with book");
                                    String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + getString(tileEntity, "id") + " at (" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ") " + listOfFiles[f].getName());
                                    int booksBefore = bookCounter;
                                    parseItem(book, bookInfo);
                                    if (bookCounter > booksBefore) {
                                        chunkBooks++;
                                        incrementBookStats("Lectern", "Block Entity");
                                    }
                                }

                                //If Sign (pre-1.20 format with Text1-Text4)
                                if (hasKey(tileEntity, "Text1")) {
                                    log("DEBUG", "  Chunk [" + x + "," + z + "] - Found sign (pre-1.20 format) at (" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ")");
                                    String signInfo = "Chunk [" + x + ", " + z + "]\t(" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ")\t\t";
                                    int signsBefore = signHashes.size();
                                    parseSign(tileEntity, signWriter, signInfo);
                                    if (signHashes.size() > signsBefore) {
                                        chunkSigns++;
                                        log("DEBUG", "    -> Sign was unique, added to output");
                                    } else {
                                        log("DEBUG", "    -> Sign was duplicate, skipped");
                                    }
                                }
                                //If Sign (1.20+ format with front_text/back_text)
                                else if (hasKey(tileEntity, "front_text")) {
                                    log("DEBUG", "  Chunk [" + x + "," + z + "] - Found sign (1.20+ format) at (" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ")");
                                    String signInfo = "Chunk [" + x + ", " + z + "]\t(" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ")\t\t";
                                    int signsBefore = signHashes.size();
                                    parseSignNew(tileEntity, signWriter, signInfo);
                                    if (signHashes.size() > signsBefore) {
                                        chunkSigns++;
                                        log("DEBUG", "    -> Sign was unique, added to output");
                                    } else {
                                        log("DEBUG", "    -> Sign was duplicate, skipped");
                                    }
                                }
                            }
                        }

                        // Get entities from the chunk
                        ListTag<CompoundTag> entities = null;
                        if (chunkRoot.containsKey("entities")) {
                            // 1.18+ format uses "entities"
                            entities = getTagList(chunkRoot, "entities", 10);
                        } else if (chunkRoot.containsKey("Entities")) {
                            // Pre-1.18 format uses "Entities"
                            entities = getTagList(chunkRoot, "Entities", 10);
                        }
                        if (entities == null) {
                            entities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tagCount(entities); i++) {
                            CompoundTag entity = getCompoundTagAt(entities, i);
                            {
                                String entityId = getString(entity, "id");

                                //Donkey, llama, villagers, zombies, etc.
                                if (hasKey(entity, "Items")) {
                                    ListTag<CompoundTag> entityItems = getTagList(entity, "Items", 10);
                                    ListTag<?> entityPos = getTagList(entity, "Pos", 6);

                                    int xPos = (int) Double.parseDouble(getStringTagAt(entityPos, 0));
                                    int yPos = (int) Double.parseDouble(getStringTagAt(entityPos, 1));
                                    int zPos = (int) Double.parseDouble(getStringTagAt(entityPos, 2));

                                    if (tagCount(entityItems) > 0) {
                                        log("DEBUG", "  Chunk [" + x + "," + z + "] - Found " + entityId + " with " + tagCount(entityItems) + " items at (" + xPos + "," + yPos + "," + zPos + ")");
                                    }

                                    for (int n = 0; n < tagCount(entityItems); n++) {
                                        CompoundTag item = getCompoundTagAt(entityItems, n);
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
                                    ListTag<?> entityPos = getTagList(entity, "Pos", 6);

                                    int xPos = (int) Double.parseDouble(getStringTagAt(entityPos, 0));
                                    int yPos = (int) Double.parseDouble(getStringTagAt(entityPos, 1));
                                    int zPos = (int) Double.parseDouble(getStringTagAt(entityPos, 2));

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
                            log("DEBUG", "  Chunk [" + x + "," + z + "] - Found " + chunkSigns + " signs, " + chunkBooks + " books");
                        }
                    }
                }
            } catch (IOException e) {
                log("ERROR", "Failed to read region file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
            }

            processedFiles++;
            log("INFO", "Completed region file [" + processedFiles + "/" + listOfFiles.length + "]: " + fileName);
        }

        log("INFO", "");
        log("INFO", "Processing complete!");
        log("INFO", "Total region files processed: " + processedFiles);
        log("INFO", "Total chunks processed: " + totalChunks);
        log("INFO", "Total unique signs found: " + signHashes.size());
        log("INFO", "Total unique books found: " + bookHashes.size());
        log("INFO", "Total books extracted (including duplicates): " + bookCounter);

        signWriter.newLine();
        signWriter.write("Completed.");
        signWriter.newLine();
        signWriter.close();

        log("INFO", "Output files written successfully");
    }

    /**
     * Read entities from separate entity files (Minecraft 1.17+)
     * Entities like item frames, minecarts, boats are stored in entities/ folder
     */
    public static void readEntities() throws IOException {
        log("INFO", "Starting readEntities()");

        File folder = new File(baseDirectory, "entities");
        if (!folder.exists() || !folder.isDirectory()) {
            log("DEBUG", "Entities folder not found (this is normal for pre-1.17 worlds): " + folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            log("DEBUG", "No entity files found in: " + folder.getAbsolutePath());
            return;
        }

        log("INFO", "Found " + listOfFiles.length + " entity files to process");

        int processedFiles = 0;
        int totalEntities = 0;
        int entitiesWithBooks = 0;

        for (int f = 0; f < listOfFiles.length; f++) {
            if (listOfFiles[f].isFile() && listOfFiles[f].getName().endsWith(".mca")) {
                processedFiles++;
                log("DEBUG", "Processing entity file [" + processedFiles + "/" + listOfFiles.length + "]: " + listOfFiles[f].getName());

                try {
                    // Load the entire region file using Querz MCA library
                    // Note: We use RAW mode to avoid chunk format validation (supports both pre-1.18 and 1.18+ formats)
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

                            // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
                            CompoundTag level = chunkData.getCompoundTag("Level");
                            CompoundTag chunkRoot = (level != null) ? level : chunkData;

                            // Get the entities list from the chunk
                            ListTag<CompoundTag> entities = null;
                            if (chunkRoot.containsKey("entities")) {
                                // 1.18+ format uses "entities"
                                entities = getTagList(chunkRoot, "entities", 10);
                            } else if (chunkRoot.containsKey("Entities")) {
                                // Pre-1.18 format uses "Entities"
                                entities = getTagList(chunkRoot, "Entities", 10);
                            }
                            if (entities == null) {
                                entities = new ListTag<>(CompoundTag.class);
                            }

                            for (int i = 0; i < tagCount(entities); i++) {
                                totalEntities++;
                                CompoundTag entity = getCompoundTagAt(entities, i);

                                String entityId = getString(entity, "id");
                                ListTag<?> entityPos = getTagList(entity, "Pos", 6);

                                int xPos = 0, yPos = 0, zPos = 0;
                                if (tagCount(entityPos) >= 3) {
                                    xPos = (int) Double.parseDouble(getStringTagAt(entityPos, 0));
                                    yPos = (int) Double.parseDouble(getStringTagAt(entityPos, 1));
                                    zPos = (int) Double.parseDouble(getStringTagAt(entityPos, 2));
                                }

                                int booksBefore = bookCounter;


                                // Entities with inventory (minecarts, boats, donkeys, llamas, villagers, zombies, etc.)
                                if (hasKey(entity, "Items")) {
                                    ListTag<?> entityItems = getTagList(entity, "Items", 10);

                                    if (tagCount(entityItems) > 0) {
                                        log("DEBUG", "  Chunk [" + x + "," + z + "] - Found " + entityId + " with " + tagCount(entityItems) + " items at (" + xPos + "," + yPos + "," + zPos + ")");
                                    }

                                    for (int n = 0; n < tagCount(entityItems); n++) {
                                        CompoundTag item = getCompoundTagAt(entityItems, n);
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
                                    log("DEBUG", "  Chunk [" + x + "," + z + "] - Found " + entityId + " with item at (" + xPos + "," + yPos + "," + zPos + ")");
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

                    log("INFO", "Completed entity file [" + processedFiles + "/" + listOfFiles.length + "]: " + listOfFiles[f].getName());

                } catch (Exception e) {
                    log("ERROR", "Error processing entity file " + listOfFiles[f].getName() + ": " + e.getMessage());
                    logException(e);
                }
            }
        }

        log("INFO", "Entity processing complete!");
        log("INFO", "Total entity files processed: " + processedFiles);
        log("INFO", "Total entities scanned: " + totalEntities);
        log("INFO", "Entities with books: " + entitiesWithBooks);
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
                // Note: We use RAW mode to avoid chunk format validation (supports both pre-1.18 and 1.18+ formats)
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

                        // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
                        CompoundTag level = chunkData.getCompoundTag("Level");
                        CompoundTag chunkRoot = (level != null) ? level : chunkData;

                        // Get tile entities from the chunk
                        ListTag<CompoundTag> tileEntities = null;
                        if (chunkRoot.containsKey("block_entities")) {
                            // 1.18+ format uses "block_entities"
                            tileEntities = getTagList(chunkRoot, "block_entities", 10);
                        } else if (chunkRoot.containsKey("TileEntities")) {
                            // Pre-1.18 format uses "TileEntities"
                            tileEntities = getTagList(chunkRoot, "TileEntities", 10);
                        }
                        if (tileEntities == null) {
                            tileEntities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tagCount(tileEntities); i++) {
                            CompoundTag entity = getCompoundTagAt(tileEntities, i);

                            //If Sign (pre-1.20 format)
                            if (hasKey(entity, "Text1")) {
                                String signInfo = "Chunk [" + x + ", " + z + "]\t(" + getInteger(entity, "x") + " " + getInteger(entity, "y") + " " + getInteger(entity, "z") + ")\t\t";
                                parseSign(entity, writer, signInfo);
                            }
                            //If Sign (1.20+ format)
                            else if (hasKey(entity, "front_text")) {
                                String signInfo = "Chunk [" + x + ", " + z + "]\t(" + getInteger(entity, "x") + " " + getInteger(entity, "y") + " " + getInteger(entity, "z") + ")\t\t";
                                parseSignNew(entity, writer, signInfo);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log("ERROR", "Failed to read region file " + listOfFiles[f].getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        writer.newLine();
        writer.write("Completed.");
        writer.newLine();
        writer.close();
    }

    public static void readPlayerData() throws IOException {
        log("INFO", "Starting readPlayerData()");

        File folder = new File(baseDirectory, "playerdata");
        if (!folder.exists() || !folder.isDirectory()) {
            log("WARN", "No player data files found in: " + folder.getAbsolutePath());
            return;
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            log("WARN", "No player data files found in: " + folder.getAbsolutePath());
            return;
        }

        log("INFO", "Found " + listOfFiles.length + " player data files to process");

        int totalPlayerBooks = 0;

        //Loop through player .dat files
        for (int i = 0; i < listOfFiles.length; i++) {
            log("DEBUG", "Processing player data [" + (i + 1) + "/" + listOfFiles.length + "]: " + listOfFiles[i].getName());

            CompoundTag playerCompound = readCompressedNBT(listOfFiles[i]);
            ListTag<CompoundTag> playerInventory = getTagList(playerCompound, "Inventory", 10);

            int playerBooks = 0;

            for (int n = 0; n < tagCount(playerInventory); n++) {
                CompoundTag item = getCompoundTagAt(playerInventory, n);
                String bookInfo = ("Inventory of player " + listOfFiles[i].getName());
                int booksBefore = bookCounter;
                parseItem(item, bookInfo);
                if (bookCounter > booksBefore) {
                    playerBooks++;
                    incrementBookStats("Player Inventory", "Player");
                }
            }

            ListTag<?> enderInventory = getTagList(playerCompound, "EnderItems", 10);

            for (int e = 0; e < tagCount(enderInventory); e++) {
                CompoundTag item = getCompoundTagAt(playerInventory, e);
                String bookInfo = ("Ender Chest of player " + listOfFiles[i].getName());
                int booksBefore = bookCounter;
                parseItem(item, bookInfo);
                if (bookCounter > booksBefore) {
                    playerBooks++;
                    incrementBookStats("Ender Chest", "Player");
                }
            }

            if (playerBooks > 0) {
                log("DEBUG", "  Found " + playerBooks + " books in player inventory/ender chest");
            }
            totalPlayerBooks += playerBooks;

            //MCR.CompoundTag nbttagcompound = MCR.CompressedStreamTools.func_1138_a(fileinputstream);
        }

        log("INFO", "Player data processing complete!");
        log("INFO", "Total books found in player inventories: " + totalPlayerBooks);
    }

    public static void readBooksAnvil() throws IOException {
        File folder = new File(baseDirectory, "region");
        File[] listOfFiles = folder.listFiles();
        File output = new File("bookOutput.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));

        for (int f = 0; f < listOfFiles.length; f++) {
            try {
                // Load the entire region file using Querz MCA library
                // Note: We use RAW mode to avoid chunk format validation (supports both pre-1.18 and 1.18+ formats)
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

                        // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
                        CompoundTag level = chunkData.getCompoundTag("Level");
                        CompoundTag chunkRoot = (level != null) ? level : chunkData;

                        // Get tile entities from the chunk
                        ListTag<CompoundTag> tileEntities = null;
                        if (chunkRoot.containsKey("block_entities")) {
                            // 1.18+ format uses "block_entities"
                            tileEntities = getTagList(chunkRoot, "block_entities", 10);
                        } else if (chunkRoot.containsKey("TileEntities")) {
                            // Pre-1.18 format uses "TileEntities"
                            tileEntities = getTagList(chunkRoot, "TileEntities", 10);
                        }
                        if (tileEntities == null) {
                            tileEntities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tagCount(tileEntities); i++) {
                            CompoundTag tileEntity = getCompoundTagAt(tileEntities, i);
                            {
                                if (hasKey(tileEntity, "id")) //If it is an item
                                {
                                    ListTag<CompoundTag> chestItems = getTagList(tileEntity, "Items", 10);
                                    for (int n = 0; n < tagCount(chestItems); n++) {
                                        CompoundTag item = getCompoundTagAt(chestItems, n);
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + getInteger(tileEntity, "x") + " " + getInteger(tileEntity, "y") + " " + getInteger(tileEntity, "z") + ") " + listOfFiles[f].getName());
                                        parseItem(item, bookInfo);
                                    }
                                }
                            }
                        }

                        // Get entities from the chunk
                        ListTag<CompoundTag> entities = null;
                        if (chunkRoot.containsKey("entities")) {
                            // 1.18+ format uses "entities"
                            entities = getTagList(chunkRoot, "entities", 10);
                        } else if (chunkRoot.containsKey("Entities")) {
                            // Pre-1.18 format uses "Entities"
                            entities = getTagList(chunkRoot, "Entities", 10);
                        }
                        if (entities == null) {
                            entities = new ListTag<>(CompoundTag.class);
                        }

                        for (int i = 0; i < tagCount(entities); i++) {
                            CompoundTag entity = getCompoundTagAt(entities, i);
                            {
                                //Donkey, llama etc.
                                if (hasKey(entity, "Items")) {
                                    ListTag<CompoundTag> entityItems = getTagList(entity, "Items", 10);
                                    ListTag<?> entityPos = getTagList(entity, "Pos", 6);

                                    int xPos = (int) Double.parseDouble(getStringTagAt(entityPos, 0));
                                    int yPos = (int) Double.parseDouble(getStringTagAt(entityPos, 1));
                                    int zPos = (int) Double.parseDouble(getStringTagAt(entityPos, 2));

                                    for (int n = 0; n < tagCount(entityItems); n++) {
                                        CompoundTag item = getCompoundTagAt(entityItems, n);
                                        String bookInfo = ("Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
                                        parseItem(item, bookInfo);
                                    }
                                }
                                //Item frame or item on the ground
                                if (hasKey(entity, "Item")) {
                                    CompoundTag item = getCompoundTag(entity, "Item");
                                    ListTag<?> entityPos = getTagList(entity, "Pos", 6);

                                    int xPos = (int) Double.parseDouble(getStringTagAt(entityPos, 0));
                                    int yPos = (int) Double.parseDouble(getStringTagAt(entityPos, 1));
                                    int zPos = (int) Double.parseDouble(getStringTagAt(entityPos, 2));

                                    String bookInfo = ("Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());

                                    parseItem(item, bookInfo);
                                    //System.out.println(item);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log("ERROR", "Failed to read region file " + listOfFiles[f].getName() + ": " + e.getMessage());
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
        String itemId = getString(item, "id");

        if (itemId.equals("minecraft:written_book") || getShort(item, "id") == 387) {
            log("DEBUG", "    Found written book: " + bookInfo.substring(0, Math.min(80, bookInfo.length())) + "...");
            readWrittenBook(item, bookInfo);
        }
        if (itemId.equals("minecraft:writable_book") || getShort(item, "id") == 386) {
            log("DEBUG", "    Found writable book: " + bookInfo.substring(0, Math.min(80, bookInfo.length())) + "...");
            readWritableBook(item, bookInfo);
        }
        if (itemId.contains("shulker_box") || (getShort(item, "id") >= 219 && getShort(item, "id") <= 234)) {
            log("DEBUG", "    Found shulker box, scanning contents...");

            // Try new format first (1.20.5+ with components)
            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:container")) {
                    ListTag<?> shelkerContents = getTagList(components, "minecraft:container", 10);
                    log("DEBUG", "      Shulker box contains " + tagCount(shelkerContents) + " items (1.20.5+ format)");
                    for (int i = 0; i < tagCount(shelkerContents); i++) {
                        CompoundTag containerEntry = getCompoundTagAt(shelkerContents, i);
                        CompoundTag shelkerItem = getCompoundTag(containerEntry, "item");
                        parseItem(shelkerItem, bookInfo + " > shulker_box");
                    }
                }
            } else if (hasKey(item, "tag")) {
                // Old format (pre-1.20.5)
                CompoundTag shelkerCompound = getCompoundTag(item, "tag");
                CompoundTag shelkerCompound2 = getCompoundTag(shelkerCompound, "BlockEntityTag");
                ListTag<?> shelkerContents = getTagList(shelkerCompound2, "Items", 10);
                log("DEBUG", "      Shulker box contains " + tagCount(shelkerContents) + " items (pre-1.20.5 format)");
                for (int i = 0; i < tagCount(shelkerContents); i++) {
                    CompoundTag shelkerItem = getCompoundTagAt(shelkerContents, i);
                    parseItem(shelkerItem, bookInfo + " > shulker_box");
                }
            }
        }

        // Bundle support (1.20.5+)
        // Matches: minecraft:bundle, minecraft:white_bundle, minecraft:orange_bundle, etc.
        if (itemId.contains("bundle")) {
            log("DEBUG", "    Found bundle, scanning contents...");

            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:bundle_contents")) {
                    ListTag<?> bundleContents = getTagList(components, "minecraft:bundle_contents", 10);
                    log("DEBUG", "      Bundle contains " + tagCount(bundleContents) + " items");
                    for (int i = 0; i < tagCount(bundleContents); i++) {
                        CompoundTag bundleItem = getCompoundTagAt(bundleContents, i);
                        parseItem(bundleItem, bookInfo + " > bundle");
                    }
                }
            }
        }

        // Copper chest support (various oxidation states)
        // Matches: minecraft:copper_chest, minecraft:exposed_copper_chest, minecraft:weathered_copper_chest,
        //          minecraft:oxidized_copper_chest, and waxed variants
        if (itemId.contains("copper_chest")) {
            log("DEBUG", "    Found copper chest, scanning contents...");

            if (hasKey(item, "components")) {
                CompoundTag components = getCompoundTag(item, "components");
                if (hasKey(components, "minecraft:container")) {
                    ListTag<?> chestContents = getTagList(components, "minecraft:container", 10);
                    log("DEBUG", "      Copper chest contains " + tagCount(chestContents) + " items");
                    for (int i = 0; i < tagCount(chestContents); i++) {
                        CompoundTag containerEntry = getCompoundTagAt(chestContents, i);
                        CompoundTag chestItem = getCompoundTag(containerEntry, "item");
                        parseItem(chestItem, bookInfo + " > copper_chest");
                    }
                }
            } else if (hasKey(item, "tag")) {
                // Old format (if copper chests existed in pre-1.20.5)
                CompoundTag chestCompound = getCompoundTag(item, "tag");
                CompoundTag chestCompound2 = getCompoundTag(chestCompound, "BlockEntityTag");
                ListTag<?> chestContents = getTagList(chestCompound2, "Items", 10);
                log("DEBUG", "      Copper chest contains " + tagCount(chestContents) + " items (pre-1.20.5 format)");
                for (int i = 0; i < tagCount(chestContents); i++) {
                    CompoundTag chestItem = getCompoundTagAt(chestContents, i);
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
            pages = getTagList(tag, "pages", 8);
            format = "pre-1.20.5";
        } else if (hasKey(item, "components")) {
            // 1.20.5+ format
            CompoundTag components = getCompoundTag(item, "components");
            if (hasKey(components, "minecraft:written_book_content")) {
                CompoundTag bookContent = getCompoundTag(components, "minecraft:written_book_content");
                pages = getTagList(bookContent, "pages", 10); // In 1.20.5+, pages are compounds, not strings
                tag = bookContent; // Use bookContent as tag for author/title
                format = "1.20.5+";
            }
        }

        if (pages == null || tagCount(pages) == 0) {
            log("DEBUG", "      Written book has no pages (format: " + format + ")");
            return;
        }

        // Check if this is a duplicate
        boolean isDuplicate = bookHashes.contains(pages.hashCode());
        if (isDuplicate) {
            log("DEBUG", "      Written book is a duplicate - saving to .duplicates folder");
        } else {
            bookHashes.add(pages.hashCode());
        }

        // Extract author and title - handle both old format (plain string) and new format (text component)
        String author = getString(tag, "author");
        String title = getString(tag, "title");

        // In 1.20.5+, author and title might be stored as text components like {raw:"text",}
        // Extract the actual text from the component if needed
        if (author.startsWith("{") && author.contains("raw:")) {
            try {
                JSONObject authorJSON = new JSONObject(author);
                if (authorJSON.has("raw")) {
                    author = authorJSON.getString("raw");
                }
            } catch (JSONException e) {
                log("WARN", "      Failed to parse author JSON: " + e.getMessage());
            }
        }
        if (title.startsWith("{") && title.contains("raw:")) {
            try {
                JSONObject titleJSON = new JSONObject(title);
                if (titleJSON.has("raw")) {
                    title = titleJSON.getString("raw");
                }
            } catch (JSONException e) {
                log("WARN", "      Failed to parse title JSON: " + e.getMessage());
            }
        }

        log("DEBUG", "      Extracted written book: \"" + title + "\" by " + author + " (" + tagCount(pages) + " pages, format: " + format + ")");

        // Create individual file for this book
        bookCounter++;
        String filename = String.format("%03d_written_%s_by_%s.txt", bookCounter, sanitizeFilename(title), sanitizeFilename(author));

        // Save to duplicates folder if it's a duplicate, otherwise to main books folder
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder;
        File bookFile = new File(baseDirectory, targetFolder + File.separator + filename);
        BufferedWriter writer = new BufferedWriter(new FileWriter(bookFile));

        log("DEBUG", "      Writing book to: " + (isDuplicate ? ".duplicates/" : "") + filename);

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
        writer.write("Pages: " + tagCount(pages));
        writer.newLine();
        writer.write("Format: " + format);
        writer.newLine();
        writer.write("Location: " + bookInfo);
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.newLine();
        for (int pc = 0; pc < tagCount(pages); pc++) {
            String pageText = null;

            // Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
            if (getListType(pages) == 8) {
                // String list (pre-1.20.5)
                pageText = getStringTagAt(pages, pc);
            } else if (getListType(pages) == 10) {
                // Compound list (1.20.5+)
                CompoundTag pageCompound = getCompoundTagAt(pages, pc);
                if (hasKey(pageCompound, "raw")) {
                    pageText = getString(pageCompound, "raw");
                } else if (hasKey(pageCompound, "filtered")) {
                    pageText = getString(pageCompound, "filtered");
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
            pages = getTagList(tag, "pages", 8);
            format = "pre-1.20.5";
        } else if (hasKey(item, "components")) {
            // 1.20.5+ format
            CompoundTag components = getCompoundTag(item, "components");
            if (hasKey(components, "minecraft:writable_book_content")) {
                CompoundTag bookContent = getCompoundTag(components, "minecraft:writable_book_content");
                pages = getTagList(bookContent, "pages", 10); // In 1.20.5+, pages are compounds
                format = "1.20.5+";
            }
        }

        if (pages == null || tagCount(pages) == 0) {
            log("DEBUG", "      Writable book has no pages (format: " + format + ")");
            return;
        }

        // Check if this is a duplicate
        boolean isDuplicate = bookHashes.contains(pages.hashCode());
        if (isDuplicate) {
            log("DEBUG", "      Writable book is a duplicate - saving to .duplicates folder");
        } else {
            bookHashes.add(pages.hashCode());
        }

        log("DEBUG", "      Extracted writable book (" + tagCount(pages) + " pages, format: " + format + ")");

        // Create individual file for this book
        bookCounter++;
        String filename = String.format("%03d_writable_book.txt", bookCounter);

        // Save to duplicates folder if it's a duplicate, otherwise to main books folder
        String targetFolder = isDuplicate ? duplicatesFolder : booksFolder;
        File bookFile = new File(baseDirectory, targetFolder + File.separator + filename);
        BufferedWriter writer = new BufferedWriter(new FileWriter(bookFile));

        log("DEBUG", "      Writing book to: " + (isDuplicate ? ".duplicates/" : "") + filename);

        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("WRITABLE BOOK (Book & Quill)");
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("Pages: " + tagCount(pages));
        writer.newLine();
        writer.write("Format: " + format);
        writer.newLine();
        writer.write("Location: " + bookInfo);
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.newLine();

        for (int pc = 0; pc < tagCount(pages); pc++) {
            String pageText = null;

            // Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
            if (getListType(pages) == 8) {
                // String list (pre-1.20.5)
                pageText = getStringTagAt(pages, pc);
            } else if (getListType(pages) == 10) {
                // Compound list (1.20.5+)
                CompoundTag pageCompound = getCompoundTagAt(pages, pc);
                if (hasKey(pageCompound, "raw")) {
                    pageText = getString(pageCompound, "raw");
                } else if (hasKey(pageCompound, "filtered")) {
                    pageText = getString(pageCompound, "filtered");
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

    private static void parseSign(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) throws IOException {
        log("DEBUG", "    parseSign() - Extracting text from pre-1.20 sign");
        String text1 = getString(tileEntity, "Text1");
        String text2 = getString(tileEntity, "Text2");
        String text3 = getString(tileEntity, "Text3");
        String text4 = getString(tileEntity, "Text4");
        log("DEBUG", "    parseSign() - Raw text: [" + text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");

        JSONObject json1 = null;
        JSONObject json2 = null;
        JSONObject json3 = null;
        JSONObject json4 = null;

        String[] signLines = {text1, text2, text3, text4};

        // Create hash based on LOCATION + TEXT to count all physical signs
        // (not just unique text content like we did before)
        String hash = signInfo + text1 + text2 + text3 + text4;
        log("DEBUG", "    parseSign() - Sign hash (location+text): [" + hash + "]");
        log("DEBUG", "    parseSign() - Current signHashes size: " + signHashes.size());
        if (signHashes.contains(hash)) {
            log("DEBUG", "    parseSign() - Sign is duplicate (same location+text), returning early");
            return;
        } else {
            signHashes.add(hash);
            log("DEBUG", "    parseSign() - Sign is unique, added to hash list");
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
                    for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++) {
                        if ((objects[o].getJSONArray("extra").get(0) instanceof String))
                            signWriter.write(objects[o].getJSONArray("extra").get(0).toString());
                        else {
                            JSONObject temp = (JSONObject) objects[o].getJSONArray("extra").get(0);
                            signWriter.write(temp.get("text").toString());
                        }
                    }
                } else if (objects[o].has("text"))
                    signWriter.write(objects[o].getString("text"));
            } else if ((!signLines[o].equals("\"\"")) && (!signLines[o].equals("null")))
                signWriter.write(signLines[o]);

            signWriter.write(" ");
        }
        signWriter.newLine();
    }

    // Parse sign in Minecraft 1.20+ format (front_text/back_text)
    private static void parseSignNew(CompoundTag tileEntity, BufferedWriter signWriter, String signInfo) throws IOException {
        log("DEBUG", "    parseSignNew() - Extracting text from 1.20+ sign");
        // Get front_text compound
        CompoundTag frontText = getCompoundTag(tileEntity, "front_text");

        // Extract messages array (contains 4 lines)
        ListTag<?> messages = getTagList(frontText, "messages", 8); // 8 = String type

        if (tagCount(messages) == 0) {
            log("DEBUG", "    parseSignNew() - No messages found, returning");
            return;
        }

        String text1 = getStringTagAt(messages, 0);
        String text2 = getStringTagAt(messages, 1);
        String text3 = getStringTagAt(messages, 2);
        String text4 = getStringTagAt(messages, 3);
        log("DEBUG", "    parseSignNew() - Raw text: [" + text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");

        JSONObject json1 = null;
        JSONObject json2 = null;
        JSONObject json3 = null;
        JSONObject json4 = null;

        String[] signLines = {text1, text2, text3, text4};

        // Create hash based on LOCATION + TEXT to count all physical signs
        // (not just unique text content like we did before)
        String hash = signInfo + text1 + text2 + text3 + text4;
        log("DEBUG", "    parseSignNew() - Sign hash (location+text): [" + hash + "]");
        log("DEBUG", "    parseSignNew() - Current signHashes size: " + signHashes.size());
        if (signHashes.contains(hash)) {
            log("DEBUG", "    parseSignNew() - Sign is duplicate (same location+text), returning early");
            return;
        } else {
            signHashes.add(hash);
            log("DEBUG", "    parseSignNew() - Sign is unique, added to hash list");
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
                    for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++) {
                        if ((objects[o].getJSONArray("extra").get(0) instanceof String))
                            signWriter.write(objects[o].getJSONArray("extra").get(0).toString());
                        else {
                            JSONObject temp = (JSONObject) objects[o].getJSONArray("extra").get(0);
                            signWriter.write(temp.get("text").toString());
                        }
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

    /**
     * Check if a CompoundTag has a key
     */
    private static boolean hasKey(CompoundTag tag, String key) {
        return tag != null && tag.containsKey(key);
    }

    // ========== BitBuf NBT Helper Methods ==========
    // Helper methods for Querz NBT library

    /**
     * Get a string value from a CompoundTag, returns empty string if not found
     */
    private static String getString(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return "";
        }
        try {
            return tag.getString(key);
        } catch (ClassCastException e) {
            // Key exists but is not a string tag
            return "";
        }
    }

    /**
     * Get an integer value from a CompoundTag, returns 0 if not found
     */
    private static int getInteger(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return 0;
        }
        try {
            return tag.getInt(key);
        } catch (ClassCastException e) {
            return 0;
        }
    }

    /**
     * Get a short value from a CompoundTag, returns 0 if not found
     */
    private static short getShort(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return 0;
        }
        try {
            return tag.getShort(key);
        } catch (ClassCastException e) {
            return 0;
        }
    }

    /**
     * Get a CompoundTag from a CompoundTag, returns empty CompoundTag if not found
     */
    private static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        if (tag == null || !tag.containsKey(key)) {
            return new CompoundTag();
        }
        try {
            CompoundTag result = tag.getCompoundTag(key);
            return result != null ? result : new CompoundTag();
        } catch (ClassCastException e) {
            return new CompoundTag();
        }
    }

    /**
     * Get a ListTag from a CompoundTag, returns empty ListTag if not found
     *
     * @param typeId The NBT type ID (kept for API compatibility, not used)
     */
    @SuppressWarnings("unchecked")
    private static ListTag<CompoundTag> getTagList(CompoundTag tag, String key, int typeId) {
        if (tag == null || !tag.containsKey(key)) {
            return new ListTag<>(CompoundTag.class);
        }
        try {
            ListTag<?> list = tag.getListTag(key);
            if (list == null) {
                return new ListTag<>(CompoundTag.class);
            }
            return (ListTag<CompoundTag>) list;
        } catch (ClassCastException e) {
            return new ListTag<>(CompoundTag.class);
        }
    }

    /**
     * Get the number of elements in a ListTag
     */
    private static int tagCount(ListTag<?> list) {
        return list != null ? list.size() : 0;
    }

    /**
     * Get a CompoundTag at a specific index in a ListTag
     */
    private static CompoundTag getCompoundTagAt(ListTag<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return new CompoundTag();
        }
        Object tag = list.get(index);
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        }
        return new CompoundTag();
    }

    /**
     * Get a string value at a specific index in a ListTag
     */
    private static String getStringTagAt(ListTag<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return "";
        }
        Object tag = list.get(index);
        if (tag instanceof net.querz.nbt.tag.StringTag) {
            return ((net.querz.nbt.tag.StringTag) tag).getValue();
        } else if (tag instanceof net.querz.nbt.tag.DoubleTag) {
            return String.valueOf(((net.querz.nbt.tag.DoubleTag) tag).asDouble());
        }
        return tag != null ? tag.toString() : "";
    }

    /**
     * Get the type ID of elements in a ListTag (for compatibility with old API)
     */
    private static int getListType(ListTag<?> list) {
        if (list == null || list.size() == 0) {
            return 0;
        }
        // Querz NBT doesn't have getTypeId(), but we can get it from the type class
        Class<?> typeClass = list.getTypeClass();
        if (typeClass == net.querz.nbt.tag.EndTag.class) return 0;
        if (typeClass == net.querz.nbt.tag.ByteTag.class) return 1;
        if (typeClass == net.querz.nbt.tag.ShortTag.class) return 2;
        if (typeClass == net.querz.nbt.tag.IntTag.class) return 3;
        if (typeClass == net.querz.nbt.tag.LongTag.class) return 4;
        if (typeClass == net.querz.nbt.tag.FloatTag.class) return 5;
        if (typeClass == net.querz.nbt.tag.DoubleTag.class) return 6;
        if (typeClass == net.querz.nbt.tag.ByteArrayTag.class) return 7;
        if (typeClass == net.querz.nbt.tag.StringTag.class) return 8;
        if (typeClass == net.querz.nbt.tag.ListTag.class) return 9;
        if (typeClass == net.querz.nbt.tag.CompoundTag.class) return 10;
        if (typeClass == net.querz.nbt.tag.IntArrayTag.class) return 11;
        if (typeClass == net.querz.nbt.tag.LongArrayTag.class) return 12;
        return 0;
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

    public void displayGUI() {

        /**
         * add nether support
         * GUI TO-DO
         * Read saves folder, dropdown menu for each world
         * books + signs in one app
         * sign filter option. Lifts, [private], empty etc.
         * Book author / title filter
         * progress bar
         * SCAN ITEM FRAME AND FURNACES
         * ender chests
         * shulker chests
         * item frames
         * remove colour tags and such
         * multithreading
         */

        File minecraftDir = new File(System.getenv("APPDATA") + "\\.minecraft\\saves");

        Choice test = new Choice();

        for (int i = 0; i < minecraftDir.listFiles().length; i++) {
            if (minecraftDir.listFiles()[i].isDirectory()) {
                File worldFolder = minecraftDir.listFiles()[i];
                Path leveldat = Paths.get(worldFolder.getAbsolutePath() + "\\level.dat");


                if (Files.exists(leveldat))
                    test.add(worldFolder.getName());
            }
        }

        Frame frame = new Frame();
        frame.setSize(new Dimension(500, 400));
        frame.setLayout(new FlowLayout());
        frame.setTitle("Test");
        frame.setResizable(false);
        frame.setVisible(true);

        Button btn = new Button("Output Signs");
        btn.setLocation(1, 10);
        btn.setSize(50, 50);

        Button btn2 = new Button("Output books");
        btn.setLocation(1, 10);
        btn.setSize(50, 50);

        frame.add(btn);
        frame.add(btn2);
        frame.add(test);
    }

}
