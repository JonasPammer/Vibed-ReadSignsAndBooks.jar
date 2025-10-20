/*     */ package Anvil;
/*     */ 
/*     */ import java.io.File;
/*     */ import java.io.FileOutputStream;
/*     */ import java.io.IOException;
/*     */ 
/*     */ 
/*     */ 
/*     */ public class Schematic
/*     */ {
/*     */   public byte[] blocks;
/*     */   public byte[] data;
/*     */   public short width;
/*     */   public short length;
/*     */   public short height;
/*     */   public NBTTagList tileEntities;
/*     */   
/*     */   public Schematic(int width, int height, int length) {
/*  19 */     this.width = (short)width;
/*  20 */     this.length = (short)length;
/*  21 */     this.height = (short)height;
/*  22 */     this.blocks = new byte[length * width * height];
/*  23 */     this.data = new byte[length * width * height];
/*  24 */     this.tileEntities = new NBTTagList();
/*     */   }
/*     */ 
/*     */   
/*     */   public byte getBlockID(int x, int y, int z) {
/*  29 */     return this.blocks[(y * this.length + z) * this.width + x];
/*     */   }
/*     */ 
/*     */   
/*     */   public byte getBlockData(int x, int y, int z) {
/*  34 */     return this.data[(y * this.length + z) * this.width + x];
/*     */   }
/*     */ 
/*     */   
/*     */   public void setBlock(int x, int y, int z, byte blockID) {
/*  39 */     this.blocks[(y * this.length + z) * this.width + x] = blockID;
/*     */   }
/*     */ 
/*     */   
/*     */   public void setBlock(int x, int y, int z, byte blockID, byte blockData) {
/*  44 */     setBlock(x, y, z, blockID);
/*  45 */     this.data[(y * this.length + z) * this.width + x] = blockData;
/*     */   }
/*     */ 
/*     */   
/*     */   public Schematic rotate() {
/*  50 */     Schematic newSchematic = new Schematic(this.length, this.height, this.width);
/*     */     
/*  52 */     for (int y = 0; y < this.height; y++) {
/*     */       
/*  54 */       for (int x = 0; x < this.width; x++) {
/*     */         
/*  56 */         for (int z = 0; z < this.length; z++)
/*     */         {
/*  58 */           newSchematic.setBlock(z, y, x, getBlockID(x, y, z), getBlockData(x, y, z));
/*     */         }
/*     */       } 
/*     */     } 
/*     */     
/*  63 */     return newSchematic;
/*     */   }
/*     */ 
/*     */   
/*     */   public Schematic mirrorWidth() {
/*  68 */     Schematic newSchematic = new Schematic(this.width, this.height, this.length);
/*  69 */     for (int y = 0; y < this.height; y++) {
/*     */       
/*  71 */       for (int x = this.width - 1; x >= 0; x--) {
/*     */         
/*  73 */         for (int z = 0; z < this.length; z++)
/*     */         {
/*  75 */           newSchematic.setBlock(this.width - x - 1, y, z, getBlockID(x, y, z), getBlockData(x, y, z));
/*     */         }
/*     */       } 
/*     */     } 
/*  79 */     return newSchematic;
/*     */   }
/*     */ 
/*     */   
/*     */   public Schematic mirrorLength() {
/*  84 */     Schematic newSchematic = new Schematic(this.width, this.height, this.length);
/*  85 */     for (int y = 0; y < this.height; y++) {
/*     */       
/*  87 */       for (int x = 0; x < this.width; x++) {
/*     */         
/*  89 */         for (int z = this.length - 1; z >= 0; z--)
/*     */         {
/*  91 */           newSchematic.setBlock(x, y, this.length - z - 1, getBlockID(x, y, z), getBlockData(x, y, z));
/*     */         }
/*     */       } 
/*     */     } 
/*  95 */     return newSchematic;
/*     */   }
/*     */ 
/*     */   
/*     */   public void writeSchematic(String fileName) throws IOException {
/* 100 */     NBTTagCompound schematic = new NBTTagCompound();
/* 101 */     NBTTagCompound base = new NBTTagCompound();
/*     */     
/* 103 */     base.setTag("Schematic", schematic);
/*     */     
/* 105 */     File file = new File(String.valueOf(fileName) + ".schematic");
/* 106 */     FileOutputStream fos = new FileOutputStream(file);
/*     */     
/* 108 */     schematic.setShort("Width", this.width);
/* 109 */     schematic.setShort("Height", this.height);
/* 110 */     schematic.setShort("Length", this.length);
/* 111 */     schematic.setString("Materials", "Alpha");
/* 112 */     schematic.setByteArray("Blocks", this.blocks);
/* 113 */     schematic.setByteArray("Data", this.data);
/* 114 */     schematic.setTag("Entities", new NBTTagList());
/* 115 */     schematic.setTag("TileEntities", this.tileEntities);
/*     */     
/* 117 */     CompressedStreamTools.writeCompressed(schematic, fos);
/* 118 */     fos.close();
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\Schematic.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */