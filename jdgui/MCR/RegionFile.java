/*     */ package MCR;
/*     */ 
/*     */ import java.io.ByteArrayInputStream;
/*     */ import java.io.DataInputStream;
/*     */ import java.io.DataOutputStream;
/*     */ import java.io.File;
/*     */ import java.io.IOException;
/*     */ import java.io.RandomAccessFile;
/*     */ import java.util.ArrayList;
/*     */ import java.util.zip.DeflaterOutputStream;
/*     */ import java.util.zip.GZIPInputStream;
/*     */ import java.util.zip.InflaterInputStream;
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
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class RegionFile
/*     */ {
/*     */   public RegionFile(File file) {
/* 328 */     this.offsets = new int[1024];
/* 329 */     this.field_22217_e = new int[1024];
/*     */     this.field_22214_h = 0L;
/*     */     this.fileName = file;
/*     */     debugln("REGION LOAD " + this.fileName);
/*     */     this.sizeDelta = 0;
/*     */     try {
/*     */       if (file.exists())
/*     */         this.field_22214_h = file.lastModified(); 
/*     */       this.dataFile = new RandomAccessFile(file, "rw");
/*     */       if (this.dataFile.length() < 4096L) {
/*     */         for (int i = 0; i < 1024; i++)
/*     */           this.dataFile.writeInt(0); 
/*     */         for (int j = 0; j < 1024; j++)
/*     */           this.dataFile.writeInt(0); 
/*     */         this.sizeDelta += 8192;
/*     */       } 
/*     */       if ((this.dataFile.length() & 0xFFFL) != 0L)
/*     */         for (int k = 0; k < (this.dataFile.length() & 0xFFFL); k++)
/*     */           this.dataFile.write(0);  
/*     */       int l = (int)this.dataFile.length() / 4096;
/*     */       this.sectorFree = new ArrayList(l);
/*     */       for (int i1 = 0; i1 < l; i1++)
/*     */         this.sectorFree.add(Boolean.valueOf(true)); 
/*     */       this.sectorFree.set(0, Boolean.valueOf(false));
/*     */       this.sectorFree.set(1, Boolean.valueOf(false));
/*     */       this.dataFile.seek(0L);
/*     */       for (int j1 = 0; j1 < 1024; j1++) {
/*     */         int l1 = this.dataFile.readInt();
/*     */         this.offsets[j1] = l1;
/*     */         if (l1 != 0 && (l1 >> 8) + (l1 & 0xFF) <= this.sectorFree.size())
/*     */           for (int j2 = 0; j2 < (l1 & 0xFF); j2++)
/*     */             this.sectorFree.set((l1 >> 8) + j2, Boolean.valueOf(false));  
/*     */       } 
/*     */       for (int k1 = 0; k1 < 1024; k1++) {
/*     */         int i2 = this.dataFile.readInt();
/*     */         this.field_22217_e[k1] = i2;
/*     */       } 
/*     */     } catch (IOException ioexception) {
/*     */       ioexception.printStackTrace();
/*     */     } 
/*     */   }
/*     */   
/*     */   public synchronized int func_22209_a() {
/*     */     int i = this.sizeDelta;
/*     */     this.sizeDelta = 0;
/*     */     return i;
/*     */   }
/*     */   
/*     */   private void func_22211_a(String s) {
/*     */     System.out.println("\n" + s);
/*     */   }
/*     */   
/*     */   private void debugln(String s) {}
/*     */   
/*     */   private void func_22199_a(String s, int i, int j, String s1) {
/*     */     func_22211_a("REGION " + s + " " + this.fileName.getName() + "[" + i + "," + j + "] = " + s1);
/*     */   }
/*     */   
/*     */   private void func_22197_a(String s, int i, int j, int k, String s1) {
/*     */     func_22211_a("REGION " + s + " " + this.fileName.getName() + "[" + i + "," + j + "] " + k + "B = " + s1);
/*     */   }
/*     */   
/*     */   private void debugln(String s, int i, int j, String s1) {
/*     */     func_22199_a(s, i, j, s1 + "\n");
/*     */   }
/*     */   
/*     */   public synchronized DataInputStream getChunkDataInputStream(int i, int j) {
/*     */     if (outOfBounds(i, j)) {
/*     */       debugln("READ", i, j, "out of bounds");
/*     */       return null;
/*     */     } 
/*     */     try {
/*     */       int k = getOffset(i, j);
/*     */       if (k == 0)
/*     */         return null; 
/*     */       int l = k >> 8;
/*     */       int i1 = k & 0xFF;
/*     */       if (l + i1 > this.sectorFree.size()) {
/*     */         debugln("READ", i, j, "invalid sector");
/*     */         return null;
/*     */       } 
/*     */       this.dataFile.seek((l * 4096));
/*     */       int j1 = this.dataFile.readInt();
/*     */       if (j1 > 4096 * i1) {
/*     */         debugln("READ", i, j, "invalid length: " + j1 + " > 4096 * " + i1);
/*     */         return null;
/*     */       } 
/*     */       byte byte0 = this.dataFile.readByte();
/*     */       if (byte0 == 1) {
/*     */         byte[] abyte0 = new byte[j1 - 1];
/*     */         this.dataFile.read(abyte0);
/*     */         DataInputStream datainputstream = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(abyte0)));
/*     */         return datainputstream;
/*     */       } 
/*     */       if (byte0 == 2) {
/*     */         byte[] abyte1 = new byte[j1 - 1];
/*     */         this.dataFile.read(abyte1);
/*     */         DataInputStream datainputstream1 = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(abyte1)));
/*     */         return datainputstream1;
/*     */       } 
/*     */       debugln("READ", i, j, "unknown version " + byte0);
/*     */       return null;
/*     */     } catch (IOException ioexception) {
/*     */       debugln("READ", i, j, "exception");
/*     */       return null;
/*     */     } 
/*     */   }
/*     */   
/*     */   public DataOutputStream getChunkDataOutputStream(int i, int j) {
/*     */     if (outOfBounds(i, j))
/*     */       return null; 
/*     */     return new DataOutputStream(new DeflaterOutputStream(new RegionFileChunkBuffer(this, i, j)));
/*     */   }
/*     */   
/*     */   protected synchronized void write(int i, int j, byte[] abyte0, int k) {
/*     */     try {
/*     */       int l = getOffset(i, j);
/*     */       int i1 = l >> 8;
/*     */       int l1 = l & 0xFF;
/*     */       int i2 = (k + 5) / 4096 + 1;
/*     */       if (i2 >= 256)
/*     */         return; 
/*     */       if (i1 != 0 && l1 == i2) {
/*     */         func_22197_a("SAVE", i, j, k, "rewrite");
/*     */         write(i1, abyte0, k);
/*     */       } else {
/*     */         for (int j2 = 0; j2 < l1; j2++)
/*     */           this.sectorFree.set(i1 + j2, Boolean.valueOf(true)); 
/*     */         int k2 = this.sectorFree.indexOf(Boolean.valueOf(true));
/*     */         int l2 = 0;
/*     */         if (k2 != -1) {
/*     */           int i3 = k2;
/*     */           while (i3 < this.sectorFree.size()) {
/*     */             if (l2 != 0) {
/*     */               if (((Boolean)this.sectorFree.get(i3)).booleanValue()) {
/*     */                 l2++;
/*     */               } else {
/*     */                 l2 = 0;
/*     */               } 
/*     */             } else if (((Boolean)this.sectorFree.get(i3)).booleanValue()) {
/*     */               k2 = i3;
/*     */               l2 = 1;
/*     */             } 
/*     */             if (l2 >= i2)
/*     */               break; 
/*     */             i3++;
/*     */           } 
/*     */         } 
/*     */         if (l2 >= i2) {
/*     */           func_22197_a("SAVE", i, j, k, "reuse");
/*     */           int j1 = k2;
/*     */           setOffset(i, j, j1 << 8 | i2);
/*     */           for (int j3 = 0; j3 < i2; j3++)
/*     */             this.sectorFree.set(j1 + j3, Boolean.valueOf(false)); 
/*     */           write(j1, abyte0, k);
/*     */         } else {
/*     */           func_22197_a("SAVE", i, j, k, "grow");
/*     */           this.dataFile.seek(this.dataFile.length());
/*     */           int k1 = this.sectorFree.size();
/*     */           for (int k3 = 0; k3 < i2; k3++) {
/*     */             this.dataFile.write(emptySector);
/*     */             this.sectorFree.add(Boolean.valueOf(false));
/*     */           } 
/*     */           this.sizeDelta += 4096 * i2;
/*     */           write(k1, abyte0, k);
/*     */           setOffset(i, j, k1 << 8 | i2);
/*     */         } 
/*     */       } 
/*     */       func_22208_b(i, j, (int)(System.currentTimeMillis() / 1000L));
/*     */     } catch (IOException ioexception) {
/*     */       ioexception.printStackTrace();
/*     */     } 
/*     */   }
/*     */   
/*     */   private void write(int i, byte[] abyte0, int j) throws IOException {
/*     */     debugln(" " + i);
/*     */     this.dataFile.seek((i * 4096));
/*     */     this.dataFile.writeInt(j + 1);
/*     */     this.dataFile.writeByte(2);
/*     */     this.dataFile.write(abyte0, 0, j);
/*     */   }
/*     */   
/*     */   private boolean outOfBounds(int i, int j) {
/*     */     return !(i >= 0 && i < 32 && j >= 0 && j < 32);
/*     */   }
/*     */   
/*     */   private int getOffset(int i, int j) {
/*     */     return this.offsets[i + j * 32];
/*     */   }
/*     */   
/*     */   public boolean func_22202_c(int i, int j) {
/*     */     return (getOffset(i, j) != 0);
/*     */   }
/*     */   
/*     */   private void setOffset(int i, int j, int k) throws IOException {
/*     */     this.offsets[i + j * 32] = k;
/*     */     this.dataFile.seek(((i + j * 32) * 4));
/*     */     this.dataFile.writeInt(k);
/*     */   }
/*     */   
/*     */   private void func_22208_b(int i, int j, int k) throws IOException {
/*     */     this.field_22217_e[i + j * 32] = k;
/*     */     this.dataFile.seek((4096 + (i + j * 32) * 4));
/*     */     this.dataFile.writeInt(k);
/*     */   }
/*     */   
/*     */   public void func_22196_b() throws IOException {
/*     */     this.dataFile.close();
/*     */   }
/*     */   
/*     */   private static final byte[] emptySector = new byte[4096];
/*     */   private final File fileName;
/*     */   private RandomAccessFile dataFile;
/*     */   private final int[] offsets;
/*     */   private final int[] field_22217_e;
/*     */   private ArrayList sectorFree;
/*     */   private int sizeDelta;
/*     */   private long field_22214_h;
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\RegionFile.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */