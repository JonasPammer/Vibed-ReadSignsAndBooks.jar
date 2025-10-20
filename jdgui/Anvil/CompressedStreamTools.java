/*     */ package Anvil;
/*     */ 
/*     */ import java.io.BufferedInputStream;
/*     */ import java.io.BufferedOutputStream;
/*     */ import java.io.ByteArrayInputStream;
/*     */ import java.io.ByteArrayOutputStream;
/*     */ import java.io.DataInput;
/*     */ import java.io.DataInputStream;
/*     */ import java.io.DataOutput;
/*     */ import java.io.DataOutputStream;
/*     */ import java.io.File;
/*     */ import java.io.FileInputStream;
/*     */ import java.io.FileOutputStream;
/*     */ import java.io.IOException;
/*     */ import java.io.InputStream;
/*     */ import java.io.OutputStream;
/*     */ import java.util.zip.GZIPInputStream;
/*     */ import java.util.zip.GZIPOutputStream;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class CompressedStreamTools
/*     */ {
/*     */   private static final String __OBFID = "CL_00001226";
/*     */   
/*     */   public static NBTTagCompound readCompressed(InputStream p_74796_0_) throws IOException {
/*     */     NBTTagCompound nbttagcompound;
/*  31 */     DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(p_74796_0_)));
/*     */ 
/*     */ 
/*     */     
/*     */     try {
/*  36 */       nbttagcompound = func_152456_a(datainputstream, NBTSizeTracker.field_152451_a);
/*     */     }
/*     */     finally {
/*     */       
/*  40 */       datainputstream.close();
/*     */     } 
/*     */     
/*  43 */     return nbttagcompound;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static void writeCompressed(NBTTagCompound p_74799_0_, OutputStream p_74799_1_) throws IOException {
/*  51 */     DataOutputStream dataoutputstream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(p_74799_1_)));
/*     */ 
/*     */     
/*     */     try {
/*  55 */       write(p_74799_0_, dataoutputstream);
/*     */     }
/*     */     finally {
/*     */       
/*  59 */       dataoutputstream.close();
/*     */     } 
/*     */   }
/*     */   
/*     */   public static NBTTagCompound func_152457_a(byte[] p_152457_0_, NBTSizeTracker p_152457_1_) throws IOException {
/*     */     NBTTagCompound nbttagcompound;
/*  65 */     DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(p_152457_0_))));
/*     */ 
/*     */ 
/*     */     
/*     */     try {
/*  70 */       nbttagcompound = func_152456_a(datainputstream, p_152457_1_);
/*     */     }
/*     */     finally {
/*     */       
/*  74 */       datainputstream.close();
/*     */     } 
/*     */     
/*  77 */     return nbttagcompound;
/*     */   }
/*     */ 
/*     */   
/*     */   public static byte[] compress(NBTTagCompound p_74798_0_) throws IOException {
/*  82 */     ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
/*  83 */     DataOutputStream dataoutputstream = new DataOutputStream(new GZIPOutputStream(bytearrayoutputstream));
/*     */ 
/*     */     
/*     */     try {
/*  87 */       write(p_74798_0_, dataoutputstream);
/*     */     }
/*     */     finally {
/*     */       
/*  91 */       dataoutputstream.close();
/*     */     } 
/*     */     
/*  94 */     return bytearrayoutputstream.toByteArray();
/*     */   }
/*     */ 
/*     */   
/*     */   public static void safeWrite(NBTTagCompound p_74793_0_, File p_74793_1_) throws IOException {
/*  99 */     File file2 = new File(String.valueOf(p_74793_1_.getAbsolutePath()) + "_tmp");
/*     */     
/* 101 */     if (file2.exists())
/*     */     {
/* 103 */       file2.delete();
/*     */     }
/*     */     
/* 106 */     write(p_74793_0_, file2);
/*     */     
/* 108 */     if (p_74793_1_.exists())
/*     */     {
/* 110 */       p_74793_1_.delete();
/*     */     }
/*     */     
/* 113 */     if (p_74793_1_.exists())
/*     */     {
/* 115 */       throw new IOException("Failed to delete " + p_74793_1_);
/*     */     }
/*     */ 
/*     */     
/* 119 */     file2.renameTo(p_74793_1_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static NBTTagCompound read(DataInputStream p_74794_0_) throws IOException {
/* 128 */     return func_152456_a(p_74794_0_, NBTSizeTracker.field_152451_a);
/*     */   }
/*     */ 
/*     */   
/*     */   public static NBTTagCompound func_152456_a(DataInput p_152456_0_, NBTSizeTracker p_152456_1_) throws IOException {
/* 133 */     NBTBase nbtbase = func_152455_a(p_152456_0_, 0, p_152456_1_);
/*     */     
/* 135 */     if (nbtbase instanceof NBTTagCompound)
/*     */     {
/* 137 */       return (NBTTagCompound)nbtbase;
/*     */     }
/*     */ 
/*     */     
/* 141 */     throw new IOException("Root tag must be a named compound tag");
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public static void write(NBTTagCompound p_74800_0_, DataOutput p_74800_1_) throws IOException {
/* 147 */     func_150663_a(p_74800_0_, p_74800_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   private static void func_150663_a(NBTBase p_150663_0_, DataOutput p_150663_1_) throws IOException {
/* 152 */     p_150663_1_.writeByte(p_150663_0_.getId());
/*     */     
/* 154 */     if (p_150663_0_.getId() != 0) {
/*     */       
/* 156 */       p_150663_1_.writeUTF("");
/* 157 */       p_150663_0_.write(p_150663_1_);
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private static NBTBase func_152455_a(DataInput p_152455_0_, int p_152455_1_, NBTSizeTracker p_152455_2_) throws IOException {
/* 163 */     byte b0 = p_152455_0_.readByte();
/*     */     
/* 165 */     if (b0 == 0)
/*     */     {
/* 167 */       return new NBTTagEnd();
/*     */     }
/*     */ 
/*     */     
/* 171 */     p_152455_0_.readUTF();
/* 172 */     NBTBase nbtbase = NBTBase.func_150284_a(b0);
/*     */ 
/*     */     
/*     */     try {
/* 176 */       nbtbase.func_152446_a(p_152455_0_, p_152455_1_, p_152455_2_);
/* 177 */       return nbtbase;
/*     */     }
/* 179 */     catch (IOException ioexception) {
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 186 */       throw null;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public static void write(NBTTagCompound p_74795_0_, File p_74795_1_) throws IOException {
/* 193 */     DataOutputStream dataoutputstream = new DataOutputStream(new FileOutputStream(p_74795_1_));
/*     */ 
/*     */     
/*     */     try {
/* 197 */       write(p_74795_0_, dataoutputstream);
/*     */     }
/*     */     finally {
/*     */       
/* 201 */       dataoutputstream.close();
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   public static NBTTagCompound read(File p_74797_0_) throws IOException {
/* 207 */     return func_152458_a(p_74797_0_, NBTSizeTracker.field_152451_a);
/*     */   }
/*     */   
/*     */   public static NBTTagCompound func_152458_a(File p_152458_0_, NBTSizeTracker p_152458_1_) throws IOException {
/*     */     NBTTagCompound nbttagcompound;
/* 212 */     if (!p_152458_0_.exists())
/*     */     {
/* 214 */       return null;
/*     */     }
/*     */ 
/*     */     
/* 218 */     DataInputStream datainputstream = new DataInputStream(new FileInputStream(p_152458_0_));
/*     */ 
/*     */ 
/*     */     
/*     */     try {
/* 223 */       nbttagcompound = func_152456_a(datainputstream, p_152458_1_);
/*     */     }
/*     */     finally {
/*     */       
/* 227 */       datainputstream.close();
/*     */     } 
/*     */     
/* 230 */     return nbttagcompound;
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\CompressedStreamTools.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */