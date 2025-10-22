import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.json.JSONException;
import org.json.JSONObject;

import Anvil.NBTTagCompound;

import java.awt.*;


public class Main {

	static ArrayList<Integer> bookHashes = new ArrayList<Integer>();
	static ArrayList<String> signHashes = new ArrayList<String>();
	static String[] colorCodes = {"\u00A70","\u00A71","\u00A72","\u00A73","\u00A74","\u00A75","\u00A76","\u00A77","\u00A78","\u00A79","\u00A7a","\u00A7b","\u00A7c","\u00A7d","\u00A7e","\u00A7f","\u00A7k","\u00A7l","\u00A7m","\u00A7n","\u00A7o","\u00A7r"};

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

	public static void main(String[] args) throws IOException
	{
		// Reset static state (important for tests that run multiple times)
		bookHashes.clear();
		signHashes.clear();
		bookCounter = 0;
		baseDirectory = System.getProperty("user.dir");

		// Initialize logging and output folders
		dateStamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		outputFolder = "ReadBooks" + File.separator + dateStamp;
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
		log("INFO", "Output folder: " + outputFolder);
		log("INFO", "=".repeat(80));

		long startTime = System.currentTimeMillis();

		try {
			readPlayerData();
			readSignsAndBooks();
			readEntities();

			long elapsed = System.currentTimeMillis() - startTime;
			log("INFO", "=".repeat(80));
			log("INFO", "Completed successfully in " + elapsed / 1000 + " seconds (" + formatTime(elapsed) + ")");
			log("INFO", "=".repeat(80));
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
		log("ERROR", "Exception stack trace:\n" + sw.toString());
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
	
	public void displayGUI()
	{
		
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
		
		File  minecraftDir = new File((String)(System.getenv("APPDATA") +  "\\.minecraft\\saves"));
		
		Choice test = new Choice();
		
		for (int i = 0; i < minecraftDir.listFiles().length; i++)
		{
			if (minecraftDir.listFiles()[i].isDirectory())
			{
				File worldFolder = minecraftDir.listFiles()[i];
				Path leveldat = Paths.get(worldFolder.getAbsolutePath() + "\\level.dat");
						
				
				if (Files.exists(leveldat))
					test.add(worldFolder.getName());
			}
		}
		
		Frame frame = new Frame();
		frame.setSize(new Dimension(500,400));
		frame.setLayout(new FlowLayout());
		frame.setTitle("Test");
		frame.setResizable(false);
		frame.setVisible(true);
		
		Button btn = new Button("Output Signs");
		btn.setLocation(1, 10);
		btn.setSize(50,50);
		
		Button btn2 = new Button("Output books");
		btn.setLocation(1, 10);
		btn.setSize(50,50);
		
		frame.add(btn);
		frame.add(btn2);
		frame.add(test);
	}
	
	public static void readSignsAndBooks() throws IOException
	{
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

		for (int f = 0; f < listOfFiles.length; f++)
		{
			String fileName = listOfFiles[f].getName();
			log("DEBUG", "Processing region file [" + (f+1) + "/" + listOfFiles.length + "]: " + fileName);

			signWriter.newLine();
			signWriter.write("--------------------------------" + fileName + "--------------------------------");
			signWriter.newLine();
			signWriter.newLine();
			for (int x = 0; x < 32; x++)
			{
				for (int z = 0; z < 32; z++)
				{
					DataInputStream dataInputStream  = MCR.RegionFileCache.getChunkInputStream(listOfFiles[f], x, z); 
					
					//Stops a null pointer exception when there is no chunk in the .mcr
					if (dataInputStream == null) 
						continue;
					
					Anvil.NBTTagCompound nbttagcompund = new Anvil.NBTTagCompound();

				    nbttagcompund = Anvil.CompressedStreamTools.read(dataInputStream);

				    // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
				    Anvil.NBTTagCompound nbttagcompund2 = new Anvil.NBTTagCompound();
				    if (nbttagcompund.hasKey("Level")) {
				        // Pre-1.18 format
				        nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
				    } else {
				        // 1.18+ format - Level tag was removed, everything moved up
				        nbttagcompund2 = nbttagcompund;
				    }

				    // Try both "TileEntities" (pre-1.18) and "block_entities" (1.18+)
				    Anvil.NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
				    if (tileEntities.tagCount() == 0) {
				        tileEntities = nbttagcompund2.getTagList("block_entities", 10);
				    }
	
				    int chunkSigns = 0;
				    int chunkBooks = 0;

				    for (int i = 0; i < tileEntities.tagCount(); i ++)
				    {
				    	Anvil.NBTTagCompound tileEntity = (Anvil.NBTTagCompound) tileEntities.getCompoundTagAt(i);
				    	{
				    		//If it is an item
				    		if (tileEntity.hasKey("id"))
				    		{
				    			String blockId = tileEntity.getString("id");
				    			log("DEBUG", "  Chunk [" + x + "," + z + "] - Processing block entity: " + blockId);
				    			Anvil.NBTTagList chestItems = tileEntity.getTagList("Items", 10);
				    			if (chestItems.tagCount() > 0) {
				    				log("DEBUG", "  Chunk [" + x + "," + z + "] - Found container " + tileEntity.getString("id") + " with " + chestItems.tagCount() + " items");
				    			}
				    			for (int n = 0; n < chestItems.tagCount(); n++)
				    			{
				    				Anvil.NBTTagCompound item = chestItems.getCompoundTagAt(n);
			    					String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" +  tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ") " + listOfFiles[f].getName());
			    					int booksBefore = bookHashes.size();
			    					parseItem(item, bookInfo);
			    					if (bookHashes.size() > booksBefore) {
			    						chunkBooks++;
			    					}
				    			}
				    		}

				    		//If Lectern (stores single book in "Book" tag, not "Items")
				    		if (tileEntity.hasKey("Book"))
				    		{
				    			Anvil.NBTTagCompound book = tileEntity.getCompoundTag("Book");
				    			log("DEBUG", "  Chunk [" + x + "," + z + "] - Found lectern with book");
				    			String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" +  tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ") " + listOfFiles[f].getName());
				    			int booksBefore = bookHashes.size();
				    			parseItem(book, bookInfo);
				    			if (bookHashes.size() > booksBefore) {
				    				chunkBooks++;
				    			}
				    		}

				    		//If Sign (pre-1.20 format with Text1-Text4)
					    	if (tileEntity.hasKey("Text1"))
					    	{
					    		log("DEBUG", "  Chunk [" + x + "," + z + "] - Found sign (pre-1.20 format) at (" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ")");
					    		String signInfo = "Chunk [" + x + ", " + z + "]\t(" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ")\t\t";
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
					    	else if (tileEntity.hasKey("front_text"))
					    	{
					    		log("DEBUG", "  Chunk [" + x + "," + z + "] - Found sign (1.20+ format) at (" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ")");
					    		String signInfo = "Chunk [" + x + ", " + z + "]\t(" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ")\t\t";
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

				    Anvil.NBTTagList entities = nbttagcompund2.getTagList("Entities", 10);
				    
				    for (int i = 0; i < entities.tagCount(); i ++)
				    {
				    	Anvil.NBTTagCompound entity = (Anvil.NBTTagCompound) entities.getCompoundTagAt(i);
				    	{
				    		//Donkey, llama etc.
				    		if (entity.hasKey("Items"))
				    		{
				    			Anvil.NBTTagList entityItems = entity.getTagList("Items", 10);
				    			Anvil.NBTTagList entityPos = entity.getTagList("Pos", 6);

				    			int xPos = (int) Double.parseDouble(entityPos.getStringTagAt(0));
				    			int yPos = (int) Double.parseDouble(entityPos.getStringTagAt(1));
				    			int zPos = (int) Double.parseDouble(entityPos.getStringTagAt(2));

				    			if (entityItems.tagCount() > 0) {
				    				log("DEBUG", "  Chunk [" + x + "," + z + "] - Found entity with " + entityItems.tagCount() + " items at (" + xPos + "," + yPos + "," + zPos + ")");
				    			}

				    			for (int n = 0; n < entityItems.tagCount(); n++)
				    			{
				    				Anvil.NBTTagCompound item = entityItems.getCompoundTagAt(n);
			    					String bookInfo = ("Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
			    					int booksBefore = bookHashes.size();
			    					parseItem(item, bookInfo);
			    					if (bookHashes.size() > booksBefore) {
			    						chunkBooks++;
			    					}
				    			}
				    		}
				    		//Item frame or item on the ground
				    		if (entity.hasKey("Item"))
				    		{
				    			Anvil.NBTTagCompound item = entity.getCompoundTag("Item");
				    			Anvil.NBTTagList entityPos = entity.getTagList("Pos", 6);

				    			int xPos = (int) Double.parseDouble(entityPos.getStringTagAt(0));
				    			int yPos = (int) Double.parseDouble(entityPos.getStringTagAt(1));
				    			int zPos = (int) Double.parseDouble(entityPos.getStringTagAt(2));

		    					String bookInfo = ("Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
		    					int booksBefore = bookHashes.size();
				    			parseItem(item, bookInfo);
				    			if (bookHashes.size() > booksBefore) {
		    						chunkBooks++;
		    					}
				    		}
				    	}
				    }

				    totalChunks++;
				    totalSigns += chunkSigns;
				    totalBooks += chunkBooks;

				    if (chunkSigns > 0 || chunkBooks > 0) {
				    	log("DEBUG", "  Chunk [" + x + "," + z + "] - Found " + chunkSigns + " signs, " + chunkBooks + " books");
				    }
				}
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
	public static void readEntities() throws IOException
	{
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

		for (int f = 0; f < listOfFiles.length; f++)
		{
			if (listOfFiles[f].isFile() && listOfFiles[f].getName().endsWith(".mca"))
			{
				processedFiles++;
				log("DEBUG", "Processing entity file [" + processedFiles + "/" + listOfFiles.length + "]: " + listOfFiles[f].getName());

				try {
					for (int x = 0; x < 32; x++)
					{
						for (int z = 0; z < 32; z++)
						{
							DataInputStream dataInputStream = MCR.RegionFileCache.getChunkInputStream(listOfFiles[f], x, z);

							if (dataInputStream == null) {
								continue;
							}

							Anvil.NBTTagCompound nbttagcompund = Anvil.CompressedStreamTools.read(dataInputStream);
							dataInputStream.close();

							// Get the entities list
							Anvil.NBTTagList entities = nbttagcompund.getTagList("Entities", 10);

							for (int i = 0; i < entities.tagCount(); i++)
							{
								totalEntities++;
								Anvil.NBTTagCompound entity = (Anvil.NBTTagCompound) entities.getCompoundTagAt(i);

								String entityId = entity.getString("id");
								Anvil.NBTTagList entityPos = entity.getTagList("Pos", 6);

								int xPos = 0, yPos = 0, zPos = 0;
								if (entityPos.tagCount() >= 3) {
									xPos = (int) Double.parseDouble(entityPos.getStringTagAt(0));
									yPos = (int) Double.parseDouble(entityPos.getStringTagAt(1));
									zPos = (int) Double.parseDouble(entityPos.getStringTagAt(2));
								}

								int booksBefore = bookHashes.size();

								// Entities with inventory (minecarts, boats, donkeys, llamas, etc.)
								if (entity.hasKey("Items"))
								{
									Anvil.NBTTagList entityItems = entity.getTagList("Items", 10);

									if (entityItems.tagCount() > 0) {
										log("DEBUG", "  Chunk [" + x + "," + z + "] - Found entity " + entityId + " with " + entityItems.tagCount() + " items at (" + xPos + "," + yPos + "," + zPos + ")");
									}

									for (int n = 0; n < entityItems.tagCount(); n++)
									{
										Anvil.NBTTagCompound item = entityItems.getCompoundTagAt(n);
										String bookInfo = ("Chunk [" + x + ", " + z + "] In entity " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
										parseItem(item, bookInfo);
									}
								}

								// Item frames and items on ground (single item)
								if (entity.hasKey("Item"))
								{
									Anvil.NBTTagCompound item = entity.getCompoundTag("Item");
									String bookInfo = ("Chunk [" + x + ", " + z + "] In entity " + entityId + " at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
									log("DEBUG", "  Chunk [" + x + "," + z + "] - Found entity " + entityId + " with item at (" + xPos + "," + yPos + "," + zPos + ")");
									parseItem(item, bookInfo);
								}

								if (bookHashes.size() > booksBefore) {
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

	public static void readSignsAnvil() throws IOException
	{
		File folder = new File(baseDirectory, "region");
	    File[] listOfFiles = folder.listFiles();
	    File output = new File("signOutput.txt");
	    BufferedWriter writer = new BufferedWriter(new FileWriter(output));
	    
		for (int f = 0; f < listOfFiles.length; f++)
		{
			writer.newLine();
			writer.write("--------------------------------" + listOfFiles[f].getName() + "--------------------------------");
			writer.newLine();
			writer.newLine();
			for (int x = 0; x < 32; x++)
			{
				for (int z = 0; z < 32; z++)
				{
					DataInputStream dataInputStream = MCR.RegionFileCache.getChunkInputStream(listOfFiles[f], x, z);
					
					if (dataInputStream == null) 
						continue;
					
					Anvil. NBTTagCompound nbttagcompund = new Anvil.NBTTagCompound();
				    nbttagcompund = Anvil.CompressedStreamTools.read(dataInputStream);

				    // Handle both pre-1.18 (with "Level" tag) and 1.18+ (without "Level" tag) formats
				    Anvil.NBTTagCompound nbttagcompund2 = new Anvil.NBTTagCompound();
				    if (nbttagcompund.hasKey("Level")) {
				        // Pre-1.18 format
				        nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
				    } else {
				        // 1.18+ format - Level tag was removed, everything moved up
				        nbttagcompund2 = nbttagcompund;
				    }

				    // Try both "TileEntities" (pre-1.18) and "block_entities" (1.18+)
				    Anvil.NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
				    if (tileEntities.tagCount() == 0) {
				        tileEntities = nbttagcompund2.getTagList("block_entities", 10);
				    }
					
				    for (int i = 0; i < tileEntities.tagCount(); i ++)
				    {
				    	Anvil.NBTTagCompound entity = tileEntities.getCompoundTagAt(i);
				    	
				    	//If Sign (pre-1.20 format)
				    	if (entity.hasKey("Text1"))
				    	{
				    		String signInfo = "Chunk [" + x + ", " + z + "]\t(" + entity.getInteger("x") + " " + entity.getInteger("y") + " " + entity.getInteger("z") + ")\t\t";
				    		parseSign(entity, writer, signInfo);
				    	}
				    	//If Sign (1.20+ format)
				    	else if (entity.hasKey("front_text"))
				    	{
				    		String signInfo = "Chunk [" + x + ", " + z + "]\t(" + entity.getInteger("x") + " " + entity.getInteger("y") + " " + entity.getInteger("z") + ")\t\t";
				    		parseSignNew(entity, writer, signInfo);
				    	}
				    }
				}
			}
		}
		writer.newLine();
		writer.write("Completed.");
		writer.newLine();
		writer.close();
	}
	
	public static void readPlayerData() throws IOException
	{
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
		for (int i = 0; i < listOfFiles.length; i++)
		{
			log("DEBUG", "Processing player data [" + (i+1) + "/" + listOfFiles.length + "]: " + listOfFiles[i].getName());

			FileInputStream fileinputstream = new FileInputStream(listOfFiles[i]);
			Anvil.NBTTagCompound playerCompound = Anvil.CompressedStreamTools.readCompressed(fileinputstream);
			Anvil.NBTTagList playerInventory = playerCompound.getTagList("Inventory", 10);

			int playerBooks = 0;

			for (int n = 0; n < playerInventory.tagCount(); n ++)
			{
				Anvil.NBTTagCompound item = (Anvil.NBTTagCompound) playerInventory.getCompoundTagAt(n);
				String bookInfo = ("Inventory of player " + listOfFiles[i].getName());
				int booksBefore = bookHashes.size();
				parseItem(item, bookInfo);
				if (bookHashes.size() > booksBefore) {
					playerBooks++;
				}
			}

			Anvil.NBTTagList enderInventory = playerCompound.getTagList("EnderItems", 10);

			for (int e = 0; e < enderInventory.tagCount(); e ++)
			{
				Anvil.NBTTagCompound item = (Anvil.NBTTagCompound) playerInventory.getCompoundTagAt(e);
				String bookInfo = ("Ender Chest of player " + listOfFiles[i].getName());
				int booksBefore = bookHashes.size();
				parseItem(item, bookInfo);
				if (bookHashes.size() > booksBefore) {
					playerBooks++;
				}
			}

			if (playerBooks > 0) {
				log("DEBUG", "  Found " + playerBooks + " books in player inventory/ender chest");
			}
			totalPlayerBooks += playerBooks;

			//MCR.NBTTagCompound nbttagcompound = MCR.CompressedStreamTools.func_1138_a(fileinputstream);
		}

		log("INFO", "Player data processing complete!");
		log("INFO", "Total books found in player inventories: " + totalPlayerBooks);
	}

	public static void readBooksAnvil() throws IOException
	{
		File folder = new File(baseDirectory, "region");
		File[] listOfFiles = folder.listFiles();
		File output = new File("bookOutput.txt");
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));
		
		for (int f = 0; f < listOfFiles.length; f++)
		{
			for (int x = 0; x < 32; x++)
			{
				for (int z = 0; z < 32; z++)
				{
					DataInputStream dataInputStream  = MCR.RegionFileCache.getChunkInputStream(listOfFiles[f], x, z); 
					//Stops a null pointer exception when there is no chunk in the .mcr
					
					if (dataInputStream == null) 
						continue;
					
					Anvil.NBTTagCompound nbttagcompund = new Anvil.NBTTagCompound();
										
				    nbttagcompund = Anvil.CompressedStreamTools.read(dataInputStream);
				    
				    Anvil.NBTTagCompound nbttagcompund2 = new Anvil.NBTTagCompound();
				    nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
				       
				    Anvil.NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
	
				    for (int i = 0; i < tileEntities.tagCount(); i ++)
				    {
				    	Anvil.NBTTagCompound tileEntity = (Anvil.NBTTagCompound) tileEntities.getCompoundTagAt(i);
				    	{
				    		if (tileEntity.hasKey("id")) //If it is an item
				    		{
				    			Anvil.NBTTagList chestItems = tileEntity.getTagList("Items", 10);
				    			for (int n = 0; n < chestItems.tagCount(); n++)
				    			{
				    				Anvil.NBTTagCompound item = chestItems.getCompoundTagAt(n);
			    					String bookInfo = ("Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" +  tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ") " + listOfFiles[f].getName());
			    					parseItem(item, bookInfo);
				    			}
				    		}
				    	}
				    }
				    
				    Anvil.NBTTagList entities = nbttagcompund2.getTagList("Entities", 10);
				    
				    for (int i = 0; i < entities.tagCount(); i ++)
				    {
				    	Anvil.NBTTagCompound entity = (Anvil.NBTTagCompound) entities.getCompoundTagAt(i);
				    	{
				    		//Donkey, llama etc.
				    		if (entity.hasKey("Items"))
				    		{
				    			Anvil.NBTTagList entityItems = entity.getTagList("Items", 10);
				    			Anvil.NBTTagList entityPos = entity.getTagList("Pos", 6);
				    			
				    			int xPos = (int) Double.parseDouble(entityPos.getStringTagAt(0));
				    			int yPos = (int) Double.parseDouble(entityPos.getStringTagAt(1));
				    			int zPos = (int) Double.parseDouble(entityPos.getStringTagAt(2));
				    			
				    			for (int n = 0; n < entityItems.tagCount(); n++)
				    			{
				    				Anvil.NBTTagCompound item = entityItems.getCompoundTagAt(n);
			    					String bookInfo = ("Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());
			    					parseItem(item, bookInfo);
				    			}
				    		}
				    		//Item frame or item on the ground
				    		if (entity.hasKey("Item"))
				    		{
				    			Anvil.NBTTagCompound item = entity.getCompoundTag("Item");
				    			Anvil.NBTTagList entityPos = entity.getTagList("Pos", 6);

				    			int xPos = (int) Double.parseDouble(entityPos.getStringTagAt(0));
				    			int yPos = (int) Double.parseDouble(entityPos.getStringTagAt(1));
				    			int zPos = (int) Double.parseDouble(entityPos.getStringTagAt(2));

		    					String bookInfo = ("Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName());

				    			parseItem(item, bookInfo);
					    		//System.out.println(item);
				    		}
				    	}
				    }
				}
			}	
		}
		writer.newLine();
		writer.write("Completed.");
		writer.newLine();
		writer.close();
	}

	/**
	 * Parse an item and check if it's a book or a container with books.
	 *
	 * MINECRAFT STORAGE CONTAINERS - COMPREHENSIVE LIST (from minecraft.wiki/w/Category:Storage)
	 *
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
	 *
	 * ❌ CANNOT HOLD BOOKS - These containers have restrictions:
	 * - Armor Stand (can only hold armor/equipment, not books)
	 * - Brewing Stand (can only hold potions/ingredients, not books)
	 * - Campfire (can only hold 4 food items for cooking, not books)
	 * - Soul Campfire (can only hold 4 food items for cooking, not books)
	 * - Cauldron (holds liquids/powders, not items)
	 * - Flower Pot (can only hold flowers/plants, not books)
	 * - Jukebox (can only hold music discs, not books)
	 *
	 * DETECTION METHODS:
	 * 1. Block entities: Detected by scanning "Items" NBT tag in tile entities
	 * 2. Entity containers: Detected by scanning "Items" or "Item" NBT tag in entities
	 * 3. Item containers: Detected by itemId and scanning nested components
	 * 4. Player inventory: Detected by scanning player data files
	 */
	public static void parseItem(Anvil.NBTTagCompound item, String bookInfo) throws IOException
	{
		String itemId = item.getString("id");

		if (itemId.equals("minecraft:written_book") || item.getShort("id") == 387)
		{
			log("DEBUG", "    Found written book: " + bookInfo.substring(0, Math.min(80, bookInfo.length())) + "...");
			readWrittenBook(item, bookInfo);
		}
		if (itemId.equals("minecraft:writable_book") || item.getShort("id") == 386)
		{
			log("DEBUG", "    Found writable book: " + bookInfo.substring(0, Math.min(80, bookInfo.length())) + "...");
			readWritableBook(item, bookInfo);
		}
		if (itemId.contains("shulker_box") || (item.getShort("id") >= 219 && item.getShort("id") <= 234))
		{
			log("DEBUG", "    Found shulker box, scanning contents...");

			// Try new format first (1.20.5+ with components)
			if (item.hasKey("components")) {
				Anvil.NBTTagCompound components = item.getCompoundTag("components");
				if (components.hasKey("minecraft:container")) {
					Anvil.NBTTagList shelkerContents = components.getTagList("minecraft:container", 10);
					log("DEBUG", "      Shulker box contains " + shelkerContents.tagCount() + " items (1.20.5+ format)");
					for (int i = 0; i < shelkerContents.tagCount(); i++)
					{
						Anvil.NBTTagCompound containerEntry = shelkerContents.getCompoundTagAt(i);
						Anvil.NBTTagCompound shelkerItem = containerEntry.getCompoundTag("item");
						parseItem(shelkerItem, bookInfo + " > shulker_box");
					}
				}
			} else if (item.hasKey("tag")) {
				// Old format (pre-1.20.5)
				Anvil.NBTTagCompound shelkerCompound = item.getCompoundTag("tag");
				Anvil.NBTTagCompound shelkerCompound2 = shelkerCompound.getCompoundTag("BlockEntityTag");
				Anvil.NBTTagList shelkerContents = shelkerCompound2.getTagList("Items", 10);
				log("DEBUG", "      Shulker box contains " + shelkerContents.tagCount() + " items (pre-1.20.5 format)");
				for (int i = 0; i < shelkerContents.tagCount(); i++)
				{
					Anvil.NBTTagCompound shelkerItem = shelkerContents.getCompoundTagAt(i);
					parseItem(shelkerItem, bookInfo + " > shulker_box");
				}
			}
		}

		// Bundle support (1.20.5+)
		// Matches: minecraft:bundle, minecraft:white_bundle, minecraft:orange_bundle, etc.
		if (itemId.contains("bundle"))
		{
			log("DEBUG", "    Found bundle, scanning contents...");

			if (item.hasKey("components")) {
				Anvil.NBTTagCompound components = item.getCompoundTag("components");
				if (components.hasKey("minecraft:bundle_contents")) {
					Anvil.NBTTagList bundleContents = components.getTagList("minecraft:bundle_contents", 10);
					log("DEBUG", "      Bundle contains " + bundleContents.tagCount() + " items");
					for (int i = 0; i < bundleContents.tagCount(); i++)
					{
						Anvil.NBTTagCompound bundleItem = bundleContents.getCompoundTagAt(i);
						parseItem(bundleItem, bookInfo + " > bundle");
					}
				}
			}
		}

		// Copper chest support (various oxidation states)
		// Matches: minecraft:copper_chest, minecraft:exposed_copper_chest, minecraft:weathered_copper_chest,
		//          minecraft:oxidized_copper_chest, and waxed variants
		if (itemId.contains("copper_chest"))
		{
			log("DEBUG", "    Found copper chest, scanning contents...");

			if (item.hasKey("components")) {
				Anvil.NBTTagCompound components = item.getCompoundTag("components");
				if (components.hasKey("minecraft:container")) {
					Anvil.NBTTagList chestContents = components.getTagList("minecraft:container", 10);
					log("DEBUG", "      Copper chest contains " + chestContents.tagCount() + " items");
					for (int i = 0; i < chestContents.tagCount(); i++)
					{
						Anvil.NBTTagCompound containerEntry = chestContents.getCompoundTagAt(i);
						Anvil.NBTTagCompound chestItem = containerEntry.getCompoundTag("item");
						parseItem(chestItem, bookInfo + " > copper_chest");
					}
				}
			} else if (item.hasKey("tag")) {
				// Old format (if copper chests existed in pre-1.20.5)
				Anvil.NBTTagCompound chestCompound = item.getCompoundTag("tag");
				Anvil.NBTTagCompound chestCompound2 = chestCompound.getCompoundTag("BlockEntityTag");
				Anvil.NBTTagList chestContents = chestCompound2.getTagList("Items", 10);
				log("DEBUG", "      Copper chest contains " + chestContents.tagCount() + " items (pre-1.20.5 format)");
				for (int i = 0; i < chestContents.tagCount(); i++)
				{
					Anvil.NBTTagCompound chestItem = chestContents.getCompoundTagAt(i);
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
	
	private static void readWrittenBook(Anvil.NBTTagCompound item, String bookInfo) throws IOException
	{
		// Try both old format (pre-1.20.5 with "tag") and new format (1.20.5+ with "components")
		Anvil.NBTTagCompound tag = null;
		Anvil.NBTTagList pages = null;
		String format = "unknown";

		if (item.hasKey("tag")) {
			// Pre-1.20.5 format
			tag = item.getCompoundTag("tag");
			pages = tag.getTagList("pages", 8);
			format = "pre-1.20.5";
		} else if (item.hasKey("components")) {
			// 1.20.5+ format
			Anvil.NBTTagCompound components = item.getCompoundTag("components");
			if (components.hasKey("minecraft:written_book_content")) {
				Anvil.NBTTagCompound bookContent = components.getCompoundTag("minecraft:written_book_content");
				pages = bookContent.getTagList("pages", 10); // In 1.20.5+, pages are compounds, not strings
				tag = bookContent; // Use bookContent as tag for author/title
				format = "1.20.5+";
			}
		}

		if (pages == null || pages.tagCount() == 0) {
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
		String author = tag.getString("author");
		String title = tag.getString("title");

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

		log("DEBUG", "      Extracted written book: \"" + title + "\" by " + author + " (" + pages.tagCount() + " pages, format: " + format + ")");

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
		writer.write("Pages: " + pages.tagCount());
		writer.newLine();
		writer.write("Format: " + format);
		writer.newLine();
		writer.write("Location: " + bookInfo);
		writer.newLine();
		writer.write("=".repeat(80));
		writer.newLine();
		writer.newLine();
		for (int pc = 0; pc < pages.tagCount(); pc++)
		{
			String pageText = null;

			// Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
			if (pages.func_150303_d() == 8) {
				// String list (pre-1.20.5)
				pageText = pages.getStringTagAt(pc);
			} else if (pages.func_150303_d() == 10) {
				// Compound list (1.20.5+)
				Anvil.NBTTagCompound pageCompound = pages.getCompoundTagAt(pc);
				if (pageCompound.hasKey("raw")) {
					pageText = pageCompound.getString("raw");
				} else if (pageCompound.hasKey("filtered")) {
					pageText = pageCompound.getString("filtered");
				}
			}

			if (pageText == null || pageText.isEmpty()) {
				continue;
			}

			JSONObject pageJSON = null;
			if (pageText.startsWith("{")) //If valid JSON
			{
				try { pageJSON = new JSONObject(pageText); }
				catch(JSONException e) { pageJSON = null; }
			}

			writer.write("Page " + pc + ": ");

			//IF VALID JSON
			if (pageJSON != null)
			{
				if (pageJSON.has("extra"))
				{

    				for (int h = 0; h < pageJSON.getJSONArray("extra").length(); h++)
    				{
    					if (pageJSON.getJSONArray("extra").get(h) instanceof String)
    						writer.write(removeTextFormatting(pageJSON.getJSONArray("extra").get(0).toString()));
    					else
    					{
    						JSONObject temp = (JSONObject) pageJSON.getJSONArray("extra").get(h);
    						writer.write(removeTextFormatting(temp.get("text").toString()));
    					}
    				}
				}
				else if (pageJSON.has("text"))
					writer.write(removeTextFormatting(pageJSON.getString("text")));
			}
			else
				writer.write(removeTextFormatting(pageText));

			writer.newLine();
		}

		writer.close();
	}

	private static void readWritableBook(Anvil.NBTTagCompound item, String bookInfo) throws IOException
	{
		// Try both old format (pre-1.20.5 with "tag") and new format (1.20.5+ with "components")
		Anvil.NBTTagList pages = null;
		String format = "unknown";

		if (item.hasKey("tag")) {
			// Pre-1.20.5 format
			Anvil.NBTTagCompound tag = item.getCompoundTag("tag");
			pages = tag.getTagList("pages", 8);
			format = "pre-1.20.5";
		} else if (item.hasKey("components")) {
			// 1.20.5+ format
			Anvil.NBTTagCompound components = item.getCompoundTag("components");
			if (components.hasKey("minecraft:writable_book_content")) {
				Anvil.NBTTagCompound bookContent = components.getCompoundTag("minecraft:writable_book_content");
				pages = bookContent.getTagList("pages", 10); // In 1.20.5+, pages are compounds
				format = "1.20.5+";
			}
		}

		if (pages == null || pages.tagCount() == 0) {
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

		log("DEBUG", "      Extracted writable book (" + pages.tagCount() + " pages, format: " + format + ")");

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
		writer.write("Pages: " + pages.tagCount());
		writer.newLine();
		writer.write("Format: " + format);
		writer.newLine();
		writer.write("Location: " + bookInfo);
		writer.newLine();
		writer.write("=".repeat(80));
		writer.newLine();
		writer.newLine();

		for (int pc = 0; pc < pages.tagCount(); pc++)
		{
			String pageText = null;

			// Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
			if (pages.func_150303_d() == 8) {
				// String list (pre-1.20.5)
				pageText = pages.getStringTagAt(pc);
			} else if (pages.func_150303_d() == 10) {
				// Compound list (1.20.5+)
				Anvil.NBTTagCompound pageCompound = pages.getCompoundTagAt(pc);
				if (pageCompound.hasKey("raw")) {
					pageText = pageCompound.getString("raw");
				} else if (pageCompound.hasKey("filtered")) {
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
	
	private static void parseSign(Anvil.NBTTagCompound tileEntity, BufferedWriter signWriter, String signInfo) throws IOException
	{
		log("DEBUG", "    parseSign() - Extracting text from pre-1.20 sign");
		String text1 = tileEntity.getString("Text1");
        String text2 = tileEntity.getString("Text2");
        String text3 = tileEntity.getString("Text3");
        String text4 = tileEntity.getString("Text4");
        log("DEBUG", "    parseSign() - Raw text: [" + text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");
        			                
        JSONObject json1 = null;
        JSONObject json2 = null;
        JSONObject json3 = null;
        JSONObject json4 = null;
        
        String[] signLines = { text1, text2, text3, text4 };

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
        
        JSONObject[] objects = { json1, json2, json3, json4 };
        
        signWriter.write(signInfo);
        
        for (int j = 0; j < 4; j++)
        {
        	if (signLines[j].startsWith("{"))
        	{
        		try
        		{
        			objects[j] = new JSONObject(signLines[j]);
        		}
        		catch (JSONException e)
        		{
        			objects[j] = null;
        		}
        	}
        }
       
        for (int o = 0; o < 4; o++)
        {
        	if (objects[o] != null)
        	{
                if (objects[o].has("extra"))
                {
                	for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++)
                	{
                        if ((objects[o].getJSONArray("extra").get(0) instanceof String)) 
                        	signWriter.write(objects[o].getJSONArray("extra").get(0).toString());
                        else 
                        {
                          JSONObject temp = (JSONObject)objects[o].getJSONArray("extra").get(0);
                          signWriter.write(temp.get("text").toString());
                        }
                	}
                } 
                else if (objects[o].has("text"))
                	signWriter.write(objects[o].getString("text"));
          }
          else if ((!signLines[o].equals("\"\"")) && (!signLines[o].equals("null"))) 
        	  signWriter.write(signLines[o]);
        	
        	signWriter.write(" ");
        }
        signWriter.newLine();
	}

	// Parse sign in Minecraft 1.20+ format (front_text/back_text)
	private static void parseSignNew(Anvil.NBTTagCompound tileEntity, BufferedWriter signWriter, String signInfo) throws IOException
	{
		log("DEBUG", "    parseSignNew() - Extracting text from 1.20+ sign");
		// Get front_text compound
		Anvil.NBTTagCompound frontText = tileEntity.getCompoundTag("front_text");

		// Extract messages array (contains 4 lines)
		Anvil.NBTTagList messages = frontText.getTagList("messages", 8); // 8 = String type

		if (messages.tagCount() == 0) {
			log("DEBUG", "    parseSignNew() - No messages found, returning");
			return;
		}

		String text1 = messages.getStringTagAt(0);
		String text2 = messages.getStringTagAt(1);
		String text3 = messages.getStringTagAt(2);
		String text4 = messages.getStringTagAt(3);
		log("DEBUG", "    parseSignNew() - Raw text: [" + text1 + "] [" + text2 + "] [" + text3 + "] [" + text4 + "]");

		JSONObject json1 = null;
		JSONObject json2 = null;
		JSONObject json3 = null;
		JSONObject json4 = null;

		String[] signLines = { text1, text2, text3, text4 };

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

		JSONObject[] objects = { json1, json2, json3, json4 };

		signWriter.write(signInfo);

		for (int j = 0; j < 4; j++)
		{
			if (signLines[j].startsWith("{"))
			{
				try
				{
					objects[j] = new JSONObject(signLines[j]);
				}
				catch (JSONException e)
				{
					objects[j] = null;
				}
			}
		}

		for (int o = 0; o < 4; o++)
		{
			if (objects[o] != null)
			{
				if (objects[o].has("extra"))
				{
					for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++)
					{
						if ((objects[o].getJSONArray("extra").get(0) instanceof String))
							signWriter.write(objects[o].getJSONArray("extra").get(0).toString());
						else
						{
							JSONObject temp = (JSONObject)objects[o].getJSONArray("extra").get(0);
							signWriter.write(temp.get("text").toString());
						}
					}
				}
				else if (objects[o].has("text"))
					signWriter.write(objects[o].getString("text"));
			}
			else if ((!signLines[o].equals("\"\"")) && (!signLines[o].equals("null")))
				signWriter.write(signLines[o]);

			signWriter.write(" ");
		}
		signWriter.newLine();
	}

	public static String removeTextFormatting(String text)
	{
		for (int i = 0; i < colorCodes.length; i ++)
			text = text.replace(colorCodes[i], "");
		return text;
	}
	
}
