/*     */ import Anvil.CompressedStreamTools;
/*     */ import Anvil.NBTTagCompound;
/*     */ import Anvil.NBTTagList;
/*     */ import MCR.RegionFileCache;
/*     */ import java.awt.Button;
/*     */ import java.awt.Choice;
/*     */ import java.awt.Dimension;
/*     */ import java.awt.FlowLayout;
/*     */ import java.awt.Frame;
/*     */ import java.io.BufferedWriter;
/*     */ import java.io.DataInputStream;
/*     */ import java.io.File;
/*     */ import java.io.FileInputStream;
/*     */ import java.io.FileWriter;
/*     */ import java.io.IOException;
/*     */ import java.nio.file.Files;
/*     */ import java.nio.file.Path;
/*     */ import java.nio.file.Paths;
/*     */ import java.util.ArrayList;
/*     */ import org.json.JSONException;
/*     */ import org.json.JSONObject;
/*     */ 
/*     */ 
/*     */ 
/*     */ public class Main
/*     */ {
/*  27 */   static ArrayList<Integer> bookHashes = new ArrayList<>();
/*  28 */   static ArrayList<String> signHashes = new ArrayList<>();
/*  29 */   static String[] colorCodes = new String[] { "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f", "§k", "§l", "§m", "§n", "§o", "§r" };
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static void main(String[] args) throws IOException {
/*  35 */     long startTime = System.currentTimeMillis();
/*  36 */     readSignsAndBooks();
/*     */     
/*  38 */     long elapsed = System.currentTimeMillis() - startTime;
/*  39 */     System.out.println(String.valueOf(elapsed / 1000L) + " seconds to complete.");
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void displayGUI() {
/*  59 */     File minecraftDir = new File(String.valueOf(System.getenv("APPDATA")) + "\\.minecraft\\saves");
/*     */     
/*  61 */     Choice test = new Choice();
/*     */     
/*  63 */     for (int i = 0; i < (minecraftDir.listFiles()).length; i++) {
/*     */       
/*  65 */       if (minecraftDir.listFiles()[i].isDirectory()) {
/*     */         
/*  67 */         File worldFolder = minecraftDir.listFiles()[i];
/*  68 */         Path leveldat = Paths.get(String.valueOf(worldFolder.getAbsolutePath()) + "\\level.dat", new String[0]);
/*     */ 
/*     */         
/*  71 */         if (Files.exists(leveldat, new java.nio.file.LinkOption[0])) {
/*  72 */           test.add(worldFolder.getName());
/*     */         }
/*     */       } 
/*     */     } 
/*  76 */     Frame frame = new Frame();
/*  77 */     frame.setSize(new Dimension(500, 400));
/*  78 */     frame.setLayout(new FlowLayout());
/*  79 */     frame.setTitle("Test");
/*  80 */     frame.setResizable(false);
/*  81 */     frame.setVisible(true);
/*     */     
/*  83 */     Button btn = new Button("Output Signs");
/*  84 */     btn.setLocation(1, 10);
/*  85 */     btn.setSize(50, 50);
/*     */     
/*  87 */     Button btn2 = new Button("Output books");
/*  88 */     btn.setLocation(1, 10);
/*  89 */     btn.setSize(50, 50);
/*     */     
/*  91 */     frame.add(btn);
/*  92 */     frame.add(btn2);
/*  93 */     frame.add(test);
/*     */   }
/*     */ 
/*     */   
/*     */   public static void readSignsAndBooks() throws IOException {
/*  98 */     File folder = new File("region");
/*  99 */     File[] listOfFiles = folder.listFiles();
/*     */     
/* 101 */     File bookOutput = new File("bookOutput.txt");
/* 102 */     BufferedWriter bookWriter = new BufferedWriter(new FileWriter(bookOutput));
/* 103 */     File signOutput = new File("signOutput.txt");
/* 104 */     BufferedWriter signWriter = new BufferedWriter(new FileWriter(signOutput));
/*     */     
/* 106 */     for (int f = 0; f < listOfFiles.length; f++) {
/*     */       
/* 108 */       signWriter.newLine();
/* 109 */       signWriter.write("--------------------------------" + listOfFiles[f].getName() + "--------------------------------");
/* 110 */       signWriter.newLine();
/* 111 */       signWriter.newLine();
/* 112 */       for (int x = 0; x < 32; x++) {
/*     */         
/* 114 */         for (int z = 0; z < 32; z++) {
/*     */           
/* 116 */           DataInputStream dataInputStream = RegionFileCache.getChunkInputStream(listOfFiles[f], x, z);
/*     */ 
/*     */           
/* 119 */           if (dataInputStream != null) {
/*     */ 
/*     */             
/* 122 */             NBTTagCompound nbttagcompund = new NBTTagCompound();
/*     */             
/* 124 */             nbttagcompund = CompressedStreamTools.read(dataInputStream);
/*     */             
/* 126 */             NBTTagCompound nbttagcompund2 = new NBTTagCompound();
/* 127 */             nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
/*     */             
/* 129 */             NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
/*     */             
/* 131 */             for (int i = 0; i < tileEntities.tagCount(); i++) {
/*     */               
/* 133 */               NBTTagCompound tileEntity = tileEntities.getCompoundTagAt(i);
/*     */ 
/*     */               
/* 136 */               if (tileEntity.hasKey("id")) {
/*     */                 
/* 138 */                 NBTTagList chestItems = tileEntity.getTagList("Items", 10);
/* 139 */                 for (int n = 0; n < chestItems.tagCount(); n++) {
/*     */                   
/* 141 */                   NBTTagCompound item = chestItems.getCompoundTagAt(n);
/* 142 */                   String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ") " + listOfFiles[f].getName() + "------------------------------------";
/* 143 */                   parseItem(item, bookWriter, bookInfo);
/*     */                 } 
/*     */               } 
/*     */ 
/*     */               
/* 148 */               if (tileEntity.hasKey("Text1")) {
/*     */                 
/* 150 */                 String signInfo = "Chunk [" + x + ", " + z + "]\t(" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ")\t\t";
/* 151 */                 parseSign(tileEntity, signWriter, signInfo);
/*     */               } 
/*     */             } 
/*     */ 
/*     */             
/* 156 */             NBTTagList entities = nbttagcompund2.getTagList("Entities", 10);
/*     */             
/* 158 */             for (int j = 0; j < entities.tagCount(); j++) {
/*     */               
/* 160 */               NBTTagCompound entity = entities.getCompoundTagAt(j);
/*     */ 
/*     */               
/* 163 */               if (entity.hasKey("Items")) {
/*     */                 
/* 165 */                 NBTTagList entityItems = entity.getTagList("Items", 10);
/* 166 */                 NBTTagList entityPos = entity.getTagList("Pos", 6);
/*     */                 
/* 168 */                 int xPos = (int)Double.parseDouble(entityPos.getStringTagAt(0));
/* 169 */                 int yPos = (int)Double.parseDouble(entityPos.getStringTagAt(1));
/* 170 */                 int zPos = (int)Double.parseDouble(entityPos.getStringTagAt(2));
/*     */                 
/* 172 */                 for (int n = 0; n < entityItems.tagCount(); n++) {
/*     */                   
/* 174 */                   NBTTagCompound item = entityItems.getCompoundTagAt(n);
/* 175 */                   String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName() + "------------------------------------";
/* 176 */                   parseItem(item, bookWriter, bookInfo);
/*     */                 } 
/*     */               } 
/*     */               
/* 180 */               if (entity.hasKey("Item")) {
/*     */                 
/* 182 */                 NBTTagCompound item = entity.getCompoundTag("Item");
/* 183 */                 NBTTagList entityPos = entity.getTagList("Pos", 6);
/*     */                 
/* 185 */                 int xPos = (int)Double.parseDouble(entityPos.getStringTagAt(0));
/* 186 */                 int yPos = (int)Double.parseDouble(entityPos.getStringTagAt(1));
/* 187 */                 int zPos = (int)Double.parseDouble(entityPos.getStringTagAt(2));
/*     */                 
/* 189 */                 String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName() + "--------------------------------";
/*     */                 
/* 191 */                 parseItem(item, bookWriter, bookInfo);
/*     */               } 
/*     */             } 
/*     */           } 
/*     */         } 
/*     */       } 
/*     */     } 
/* 198 */     bookWriter.newLine();
/* 199 */     bookWriter.write("Completed.");
/* 200 */     bookWriter.newLine();
/* 201 */     bookWriter.close();
/*     */     
/* 203 */     signWriter.newLine();
/* 204 */     signWriter.write("Completed.");
/* 205 */     signWriter.newLine();
/* 206 */     signWriter.close();
/*     */   }
/*     */ 
/*     */   
/*     */   public static void readSignsAnvil() throws IOException {
/* 211 */     File folder = new File("region");
/* 212 */     File[] listOfFiles = folder.listFiles();
/* 213 */     File output = new File("signOutput.txt");
/* 214 */     BufferedWriter writer = new BufferedWriter(new FileWriter(output));
/*     */     
/* 216 */     for (int f = 0; f < listOfFiles.length; f++) {
/*     */       
/* 218 */       writer.newLine();
/* 219 */       writer.write("--------------------------------" + listOfFiles[f].getName() + "--------------------------------");
/* 220 */       writer.newLine();
/* 221 */       writer.newLine();
/* 222 */       for (int x = 0; x < 32; x++) {
/*     */         
/* 224 */         for (int z = 0; z < 32; z++) {
/*     */           
/* 226 */           DataInputStream dataInputStream = RegionFileCache.getChunkInputStream(listOfFiles[f], x, z);
/*     */           
/* 228 */           if (dataInputStream != null) {
/*     */ 
/*     */             
/* 231 */             NBTTagCompound nbttagcompund = new NBTTagCompound();
/* 232 */             nbttagcompund = CompressedStreamTools.read(dataInputStream);
/*     */             
/* 234 */             NBTTagCompound nbttagcompund2 = new NBTTagCompound();
/* 235 */             nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
/*     */             
/* 237 */             NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
/*     */             
/* 239 */             for (int i = 0; i < tileEntities.tagCount(); i++) {
/*     */               
/* 241 */               NBTTagCompound entity = tileEntities.getCompoundTagAt(i);
/*     */ 
/*     */               
/* 244 */               if (entity.hasKey("Text1"))
/*     */               
/*     */               { 
/* 247 */                 String text1 = entity.getString("Text1");
/* 248 */                 String text2 = entity.getString("Text2");
/* 249 */                 String text3 = entity.getString("Text3");
/* 250 */                 String text4 = entity.getString("Text4");
/*     */                 
/* 252 */                 JSONObject json1 = null;
/* 253 */                 JSONObject json2 = null;
/* 254 */                 JSONObject json3 = null;
/* 255 */                 JSONObject json4 = null;
/*     */                 
/* 257 */                 String[] signLines = { text1, text2, text3, text4 };
/*     */                 
/* 259 */                 String hash = String.valueOf(text1) + text2 + text3 + text4;
/* 260 */                 if (!signHashes.contains(hash))
/*     */                 
/*     */                 { 
/* 263 */                   signHashes.add(hash);
/*     */                   
/* 265 */                   JSONObject[] objects = { json1, json2, json3, json4 };
/*     */                   
/* 267 */                   writer.write("Chunk [" + x + ", " + z + "]\t(" + entity.getInteger("x") + " " + entity.getInteger("y") + " " + entity.getInteger("z") + ")\t\t");
/*     */                   
/* 269 */                   for (int j = 0; j < 4; j++) {
/*     */                     
/* 271 */                     if (signLines[j].startsWith("{")) {
/*     */                       
/*     */                       try {
/*     */                         
/* 275 */                         objects[j] = new JSONObject(signLines[j]);
/*     */                       }
/* 277 */                       catch (JSONException e) {
/*     */                         
/* 279 */                         objects[j] = null;
/*     */                       } 
/*     */                     }
/*     */                   } 
/*     */                   
/* 284 */                   for (int o = 0; o < 4; o++) {
/*     */                     
/* 286 */                     if (objects[o] != null) {
/*     */                       
/* 288 */                       if (objects[o].has("extra")) {
/*     */                         
/* 290 */                         for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++) {
/*     */                           
/* 292 */                           if (objects[o].getJSONArray("extra").get(0) instanceof String) {
/* 293 */                             writer.write(objects[o].getJSONArray("extra").get(0).toString());
/*     */                           } else {
/*     */                             
/* 296 */                             JSONObject temp = (JSONObject)objects[o].getJSONArray("extra").get(0);
/* 297 */                             writer.write(temp.get("text").toString());
/*     */                           }
/*     */                         
/*     */                         } 
/* 301 */                       } else if (objects[o].has("text")) {
/* 302 */                         writer.write(objects[o].getString("text"));
/*     */                       } 
/* 304 */                     } else if (!signLines[o].equals("\"\"") && !signLines[o].equals("null")) {
/* 305 */                       writer.write(signLines[o]);
/*     */                     } 
/* 307 */                     writer.write(" ");
/*     */                   } 
/* 309 */                   writer.newLine(); }  } 
/*     */             } 
/*     */           } 
/*     */         } 
/*     */       } 
/* 314 */     }  writer.newLine();
/* 315 */     writer.write("Completed.");
/* 316 */     writer.newLine();
/* 317 */     writer.close();
/*     */   }
/*     */ 
/*     */   
/*     */   public static void readPlayerData() throws IOException {
/* 322 */     File folder = new File("playerdata");
/* 323 */     File[] listOfFiles = folder.listFiles();
/* 324 */     File output = new File("playerdataOutput.txt");
/* 325 */     BufferedWriter writer = new BufferedWriter(new FileWriter(output));
/*     */ 
/*     */     
/* 328 */     for (int i = 0; i < listOfFiles.length; i++) {
/*     */       
/* 330 */       System.out.println(String.valueOf(i) + " / " + listOfFiles.length);
/* 331 */       FileInputStream fileinputstream = new FileInputStream(listOfFiles[i]);
/* 332 */       NBTTagCompound playerCompound = CompressedStreamTools.readCompressed(fileinputstream);
/* 333 */       NBTTagList playerInventory = playerCompound.getTagList("Inventory", 10);
/*     */       
/* 335 */       for (int n = 0; n < playerInventory.tagCount(); n++) {
/*     */         
/* 337 */         NBTTagCompound item = playerInventory.getCompoundTagAt(n);
/* 338 */         String bookInfo = "------------------------------------Inventory of player " + listOfFiles[i].getName() + "------------------------------------";
/* 339 */         parseItem(item, writer, bookInfo);
/*     */       } 
/*     */       
/* 342 */       NBTTagList enderInventory = playerCompound.getTagList("EnderItems", 10);
/*     */       
/* 344 */       for (int e = 0; e < enderInventory.tagCount(); e++) {
/*     */         
/* 346 */         NBTTagCompound item = playerInventory.getCompoundTagAt(e);
/* 347 */         String bookInfo = "------------------------------------Ender Chest of player " + listOfFiles[i].getName() + "------------------------------------";
/* 348 */         parseItem(item, writer, bookInfo);
/*     */       } 
/*     */     } 
/*     */ 
/*     */     
/* 353 */     writer.close();
/*     */   }
/*     */ 
/*     */   
/*     */   public static void readBooksAnvil() throws IOException {
/* 358 */     File folder = new File("region");
/* 359 */     File[] listOfFiles = folder.listFiles();
/* 360 */     File output = new File("bookOutput.txt");
/* 361 */     BufferedWriter writer = new BufferedWriter(new FileWriter(output));
/*     */     
/* 363 */     for (int f = 0; f < listOfFiles.length; f++) {
/*     */       
/* 365 */       for (int x = 0; x < 32; x++) {
/*     */         
/* 367 */         for (int z = 0; z < 32; z++) {
/*     */           
/* 369 */           DataInputStream dataInputStream = RegionFileCache.getChunkInputStream(listOfFiles[f], x, z);
/*     */ 
/*     */           
/* 372 */           if (dataInputStream != null) {
/*     */ 
/*     */             
/* 375 */             NBTTagCompound nbttagcompund = new NBTTagCompound();
/*     */             
/* 377 */             nbttagcompund = CompressedStreamTools.read(dataInputStream);
/*     */             
/* 379 */             NBTTagCompound nbttagcompund2 = new NBTTagCompound();
/* 380 */             nbttagcompund2 = nbttagcompund.getCompoundTag("Level");
/*     */             
/* 382 */             NBTTagList tileEntities = nbttagcompund2.getTagList("TileEntities", 10);
/*     */             
/* 384 */             for (int i = 0; i < tileEntities.tagCount(); i++) {
/*     */               
/* 386 */               NBTTagCompound tileEntity = tileEntities.getCompoundTagAt(i);
/*     */               
/* 388 */               if (tileEntity.hasKey("id")) {
/*     */                 
/* 390 */                 NBTTagList chestItems = tileEntity.getTagList("Items", 10);
/* 391 */                 for (int n = 0; n < chestItems.tagCount(); n++) {
/*     */                   
/* 393 */                   NBTTagCompound item = chestItems.getCompoundTagAt(n);
/* 394 */                   String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] Inside " + tileEntity.getString("id") + " at (" + tileEntity.getInteger("x") + " " + tileEntity.getInteger("y") + " " + tileEntity.getInteger("z") + ") " + listOfFiles[f].getName() + "------------------------------------";
/* 395 */                   parseItem(item, writer, bookInfo);
/*     */                 } 
/*     */               } 
/*     */             } 
/*     */ 
/*     */             
/* 401 */             NBTTagList entities = nbttagcompund2.getTagList("Entities", 10);
/*     */             
/* 403 */             for (int j = 0; j < entities.tagCount(); j++) {
/*     */               
/* 405 */               NBTTagCompound entity = entities.getCompoundTagAt(j);
/*     */ 
/*     */               
/* 408 */               if (entity.hasKey("Items")) {
/*     */                 
/* 410 */                 NBTTagList entityItems = entity.getTagList("Items", 10);
/* 411 */                 NBTTagList entityPos = entity.getTagList("Pos", 6);
/*     */                 
/* 413 */                 int xPos = (int)Double.parseDouble(entityPos.getStringTagAt(0));
/* 414 */                 int yPos = (int)Double.parseDouble(entityPos.getStringTagAt(1));
/* 415 */                 int zPos = (int)Double.parseDouble(entityPos.getStringTagAt(2));
/*     */                 
/* 417 */                 for (int n = 0; n < entityItems.tagCount(); n++) {
/*     */                   
/* 419 */                   NBTTagCompound item = entityItems.getCompoundTagAt(n);
/* 420 */                   String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] On entity at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName() + "------------------------------------";
/* 421 */                   parseItem(item, writer, bookInfo);
/*     */                 } 
/*     */               } 
/*     */               
/* 425 */               if (entity.hasKey("Item")) {
/*     */                 
/* 427 */                 NBTTagCompound item = entity.getCompoundTag("Item");
/* 428 */                 NBTTagList entityPos = entity.getTagList("Pos", 6);
/*     */                 
/* 430 */                 int xPos = (int)Double.parseDouble(entityPos.getStringTagAt(0));
/* 431 */                 int yPos = (int)Double.parseDouble(entityPos.getStringTagAt(1));
/* 432 */                 int zPos = (int)Double.parseDouble(entityPos.getStringTagAt(2));
/*     */                 
/* 434 */                 String bookInfo = "------------------------------------Chunk [" + x + ", " + z + "] On ground or item frame at at (" + xPos + " " + yPos + " " + zPos + ") " + listOfFiles[f].getName() + "--------------------------------";
/*     */                 
/* 436 */                 parseItem(item, writer, bookInfo);
/*     */               } 
/*     */             } 
/*     */           } 
/*     */         } 
/*     */       } 
/*     */     } 
/*     */     
/* 444 */     writer.newLine();
/* 445 */     writer.write("Completed.");
/* 446 */     writer.newLine();
/* 447 */     writer.close();
/*     */   }
/*     */ 
/*     */   
/*     */   public static void parseItem(NBTTagCompound item, BufferedWriter writer, String bookInfo) throws IOException {
/* 452 */     if (item.getString("id").equals("minecraft:written_book") || item.getShort("id") == 387)
/*     */     {
/* 454 */       readWrittenBook(item, writer, bookInfo);
/*     */     }
/* 456 */     if (item.getString("id").equals("minecraft:writable_book") || item.getShort("id") == 386)
/*     */     {
/* 458 */       readWritableBook(item, writer, bookInfo);
/*     */     }
/* 460 */     if (item.getString("id").contains("shulker_box") || (item.getShort("id") >= 219 && item.getShort("id") <= 234)) {
/*     */       
/* 462 */       NBTTagCompound shelkerCompound = item.getCompoundTag("tag");
/* 463 */       NBTTagCompound shelkerCompound2 = shelkerCompound.getCompoundTag("BlockEntityTag");
/* 464 */       NBTTagList shelkerContents = shelkerCompound2.getTagList("Items", 10);
/* 465 */       for (int i = 0; i < shelkerContents.tagCount(); i++) {
/*     */         
/* 467 */         NBTTagCompound shelkerItem = shelkerContents.getCompoundTagAt(i);
/* 468 */         parseItem(shelkerItem, writer, bookInfo);
/*     */       } 
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private static void readWrittenBook(NBTTagCompound item, BufferedWriter writer, String bookInfo) throws IOException {
/* 475 */     NBTTagCompound tag = item.getCompoundTag("tag");
/* 476 */     NBTTagList pages = tag.getTagList("pages", 8);
/*     */     
/* 478 */     if (pages.tagCount() == 0)
/*     */       return; 
/* 480 */     if (bookHashes.contains(Integer.valueOf(pages.hashCode()))) {
/*     */       return;
/*     */     }
/* 483 */     bookHashes.add(Integer.valueOf(pages.hashCode()));
/*     */     
/* 485 */     String author = tag.getString("author");
/* 486 */     String title = tag.getString("title");
/*     */     
/* 488 */     writer.newLine();
/* 489 */     writer.write(bookInfo);
/* 490 */     writer.newLine();
/* 491 */     writer.write("\tTitle: " + title);
/* 492 */     writer.newLine();
/* 493 */     writer.write("\tAuthor: " + author);
/* 494 */     writer.newLine();
/* 495 */     writer.write("\tType: Written");
/* 496 */     writer.newLine();
/* 497 */     writer.newLine();
/* 498 */     for (int pc = 0; pc < pages.tagCount(); pc++) {
/*     */       
/* 500 */       JSONObject pageJSON = null;
/* 501 */       String pageText = pages.getStringTagAt(pc);
/*     */       
/* 503 */       if (pageText.startsWith("{")) {
/*     */         
/* 505 */         try { pageJSON = new JSONObject(pageText); }
/* 506 */         catch (JSONException e) { pageJSON = null; }
/*     */       
/*     */       }
/* 509 */       writer.write("Page " + pc + ": ");
/*     */ 
/*     */       
/* 512 */       if (pageJSON != null) {
/*     */         
/* 514 */         if (pageJSON.has("extra")) {
/*     */ 
/*     */           
/* 517 */           for (int h = 0; h < pageJSON.getJSONArray("extra").length(); h++) {
/*     */             
/* 519 */             if (pageJSON.getJSONArray("extra").get(h) instanceof String) {
/* 520 */               writer.write(removeTextFormatting(pageJSON.getJSONArray("extra").get(0).toString()));
/*     */             } else {
/*     */               
/* 523 */               JSONObject temp = (JSONObject)pageJSON.getJSONArray("extra").get(h);
/* 524 */               writer.write(removeTextFormatting(temp.get("text").toString()));
/*     */             }
/*     */           
/*     */           } 
/* 528 */         } else if (pageJSON.has("text")) {
/* 529 */           writer.write(removeTextFormatting(pageJSON.getString("text")));
/*     */         } 
/*     */       } else {
/* 532 */         writer.write(removeTextFormatting(pages.getStringTagAt(pc)));
/*     */       } 
/* 534 */       writer.newLine();
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private static void readWritableBook(NBTTagCompound item, BufferedWriter writer, String bookInfo) throws IOException {
/* 540 */     NBTTagCompound tag = item.getCompoundTag("tag");
/* 541 */     NBTTagList pages = tag.getTagList("pages", 8);
/* 542 */     if (pages.tagCount() == 0)
/*     */       return; 
/* 544 */     if (bookHashes.contains(Integer.valueOf(pages.hashCode()))) {
/*     */       return;
/*     */     }
/* 547 */     bookHashes.add(Integer.valueOf(pages.hashCode()));
/* 548 */     writer.newLine();
/* 549 */     writer.write(bookInfo);
/* 550 */     writer.newLine();
/* 551 */     writer.write("\tType: Writable");
/* 552 */     writer.newLine();
/* 553 */     writer.newLine();
/* 554 */     for (int pc = 0; pc < pages.tagCount(); pc++) {
/*     */       
/* 556 */       writer.write("Page " + pc + ": " + removeTextFormatting(pages.getStringTagAt(pc)));
/* 557 */       writer.newLine();
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private static void parseSign(NBTTagCompound tileEntity, BufferedWriter signWriter, String signInfo) throws IOException {
/* 563 */     String text1 = tileEntity.getString("Text1");
/* 564 */     String text2 = tileEntity.getString("Text2");
/* 565 */     String text3 = tileEntity.getString("Text3");
/* 566 */     String text4 = tileEntity.getString("Text4");
/*     */     
/* 568 */     JSONObject json1 = null;
/* 569 */     JSONObject json2 = null;
/* 570 */     JSONObject json3 = null;
/* 571 */     JSONObject json4 = null;
/*     */     
/* 573 */     String[] signLines = { text1, text2, text3, text4 };
/*     */     
/* 575 */     String hash = String.valueOf(text1) + text2 + text3 + text4;
/* 576 */     if (signHashes.contains(hash)) {
/*     */       return;
/*     */     }
/* 579 */     signHashes.add(hash);
/*     */     
/* 581 */     JSONObject[] objects = { json1, json2, json3, json4 };
/*     */     
/* 583 */     signWriter.write(signInfo);
/*     */     
/* 585 */     for (int j = 0; j < 4; j++) {
/*     */       
/* 587 */       if (signLines[j].startsWith("{")) {
/*     */         
/*     */         try {
/*     */           
/* 591 */           objects[j] = new JSONObject(signLines[j]);
/*     */         }
/* 593 */         catch (JSONException e) {
/*     */           
/* 595 */           objects[j] = null;
/*     */         } 
/*     */       }
/*     */     } 
/*     */     
/* 600 */     for (int o = 0; o < 4; o++) {
/*     */       
/* 602 */       if (objects[o] != null) {
/*     */         
/* 604 */         if (objects[o].has("extra")) {
/*     */           
/* 606 */           for (int h = 0; h < objects[o].getJSONArray("extra").length(); h++) {
/*     */             
/* 608 */             if (objects[o].getJSONArray("extra").get(0) instanceof String) {
/* 609 */               signWriter.write(objects[o].getJSONArray("extra").get(0).toString());
/*     */             } else {
/*     */               
/* 612 */               JSONObject temp = (JSONObject)objects[o].getJSONArray("extra").get(0);
/* 613 */               signWriter.write(temp.get("text").toString());
/*     */             }
/*     */           
/*     */           } 
/* 617 */         } else if (objects[o].has("text")) {
/* 618 */           signWriter.write(objects[o].getString("text"));
/*     */         } 
/* 620 */       } else if (!signLines[o].equals("\"\"") && !signLines[o].equals("null")) {
/* 621 */         signWriter.write(signLines[o]);
/*     */       } 
/* 623 */       signWriter.write(" ");
/*     */     } 
/* 625 */     signWriter.newLine();
/*     */   }
/*     */ 
/*     */   
/*     */   public static String removeTextFormatting(String text) {
/* 630 */     for (int i = 0; i < colorCodes.length; i++)
/* 631 */       text = text.replace(colorCodes[i], ""); 
/* 632 */     return text;
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Main.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */