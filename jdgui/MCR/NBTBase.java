/*     */ package MCR;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public abstract class NBTBase
/*     */ {
/*  22 */   private String key = null;
/*     */ 
/*     */ 
/*     */   
/*     */   abstract void writeTagContents(DataOutput paramDataOutput) throws IOException;
/*     */ 
/*     */ 
/*     */   
/*     */   abstract void readTagContents(DataInput paramDataInput) throws IOException;
/*     */ 
/*     */   
/*     */   public abstract byte getType();
/*     */ 
/*     */   
/*     */   public String getKey() {
/*  37 */     if (this.key == null)
/*     */     {
/*  39 */       return "";
/*     */     }
/*     */     
/*  42 */     return this.key;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase setKey(String s) {
/*  48 */     this.key = s;
/*  49 */     return this;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public static NBTBase readTag(DataInput datainput) throws IOException {
/*  55 */     byte byte0 = datainput.readByte();
/*  56 */     if (byte0 == 0)
/*     */     {
/*  58 */       return new NBTTagEnd();
/*     */     }
/*     */     
/*  61 */     NBTBase nbtbase = createTagOfType(byte0);
/*  62 */     nbtbase.key = datainput.readUTF();
/*  63 */     nbtbase.readTagContents(datainput);
/*  64 */     return nbtbase;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static void writeTag(NBTBase nbtbase, DataOutput dataoutput) throws IOException {
/*  71 */     dataoutput.writeByte(nbtbase.getType());
/*  72 */     if (nbtbase.getType() == 0) {
/*     */       return;
/*     */     }
/*     */ 
/*     */     
/*  77 */     dataoutput.writeUTF(nbtbase.getKey());
/*  78 */     nbtbase.writeTagContents(dataoutput);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static NBTBase createTagOfType(byte byte0) {
/*  85 */     switch (byte0) {
/*     */       
/*     */       case 0:
/*  88 */         return new NBTTagEnd();
/*     */       
/*     */       case 1:
/*  91 */         return new NBTTagByte();
/*     */       
/*     */       case 2:
/*  94 */         return new NBTTagShort();
/*     */       
/*     */       case 3:
/*  97 */         return new NBTTagInt();
/*     */       
/*     */       case 4:
/* 100 */         return new NBTTagLong();
/*     */       
/*     */       case 5:
/* 103 */         return new NBTTagFloat();
/*     */       
/*     */       case 6:
/* 106 */         return new NBTTagDouble();
/*     */       
/*     */       case 7:
/* 109 */         return new NBTTagByteArray();
/*     */       
/*     */       case 8:
/* 112 */         return new NBTTagString();
/*     */       
/*     */       case 9:
/* 115 */         return new NBTTagList();
/*     */       
/*     */       case 10:
/* 118 */         return new NBTTagCompound();
/*     */     } 
/* 120 */     return null;
/*     */   }
/*     */ 
/*     */   
/*     */   public static String getTagName(byte byte0) {
/* 125 */     switch (byte0) {
/*     */       
/*     */       case 0:
/* 128 */         return "TAG_End";
/*     */       
/*     */       case 1:
/* 131 */         return "TAG_Byte";
/*     */       
/*     */       case 2:
/* 134 */         return "TAG_Short";
/*     */       
/*     */       case 3:
/* 137 */         return "TAG_Int";
/*     */       
/*     */       case 4:
/* 140 */         return "TAG_Long";
/*     */       
/*     */       case 5:
/* 143 */         return "TAG_Float";
/*     */       
/*     */       case 6:
/* 146 */         return "TAG_Double";
/*     */       
/*     */       case 7:
/* 149 */         return "TAG_Byte_Array";
/*     */       
/*     */       case 8:
/* 152 */         return "TAG_String";
/*     */       
/*     */       case 9:
/* 155 */         return "TAG_List";
/*     */       
/*     */       case 10:
/* 158 */         return "TAG_Compound";
/*     */     } 
/* 160 */     return "UNKNOWN";
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTBase.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */