/*     */ package MCR;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ import java.util.Collection;
/*     */ import java.util.HashMap;
/*     */ import java.util.Iterator;
/*     */ import java.util.Map;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class NBTTagCompound
/*     */   extends NBTBase
/*     */ {
/*  18 */   private Map tagMap = new HashMap<>();
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   void writeTagContents(DataOutput dataoutput) throws IOException {
/*  25 */     for (Iterator<NBTBase> iterator = this.tagMap.values().iterator(); iterator.hasNext(); NBTBase.writeTag(nbtbase, dataoutput))
/*     */     {
/*  27 */       NBTBase nbtbase = iterator.next();
/*     */     }
/*     */     
/*  30 */     dataoutput.writeByte(0);
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   void readTagContents(DataInput datainput) throws IOException {
/*  36 */     this.tagMap.clear();
/*     */     NBTBase nbtbase;
/*  38 */     for (; (nbtbase = NBTBase.readTag(datainput)).getType() != 0; this.tagMap.put(nbtbase.getKey(), nbtbase));
/*     */   }
/*     */ 
/*     */   
/*     */   public Collection func_28110_c() {
/*  43 */     return this.tagMap.values();
/*     */   }
/*     */ 
/*     */   
/*     */   public byte getType() {
/*  48 */     return 10;
/*     */   }
/*     */ 
/*     */   
/*     */   public void setTag(String s, NBTBase nbtbase) {
/*  53 */     this.tagMap.put(s, nbtbase.setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setByte(String s, byte byte0) {
/*  58 */     this.tagMap.put(s, (new NBTTagByte(byte0)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setShort(String s, short word0) {
/*  63 */     this.tagMap.put(s, (new NBTTagShort(word0)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setInteger(String s, int i) {
/*  68 */     this.tagMap.put(s, (new NBTTagInt(i)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setLong(String s, long l) {
/*  73 */     this.tagMap.put(s, (new NBTTagLong(l)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setFloat(String s, float f) {
/*  78 */     this.tagMap.put(s, (new NBTTagFloat(f)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setDouble(String s, double d) {
/*  83 */     this.tagMap.put(s, (new NBTTagDouble(d)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setString(String s, String s1) {
/*  88 */     this.tagMap.put(s, (new NBTTagString(s1)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setByteArray(String s, byte[] abyte0) {
/*  93 */     this.tagMap.put(s, (new NBTTagByteArray(abyte0)).setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setCompoundTag(String s, NBTTagCompound nbttagcompound) {
/*  98 */     this.tagMap.put(s, nbttagcompound.setKey(s));
/*     */   }
/*     */ 
/*     */   
/*     */   public void setBoolean(String s, boolean flag) {
/* 103 */     setByte(s, (byte)(flag ? 1 : 0));
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean hasKey(String s) {
/* 108 */     return this.tagMap.containsKey(s);
/*     */   }
/*     */ 
/*     */   
/*     */   public byte getByte(String s) {
/* 113 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 115 */       return 0;
/*     */     }
/*     */     
/* 118 */     return ((NBTTagByte)this.tagMap.get(s)).byteValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public short getShort(String s) {
/* 124 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 126 */       return 0;
/*     */     }
/*     */     
/* 129 */     return ((NBTTagShort)this.tagMap.get(s)).shortValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public int getInteger(String s) {
/* 135 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 137 */       return 0;
/*     */     }
/*     */     
/* 140 */     return ((NBTTagInt)this.tagMap.get(s)).intValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public long getLong(String s) {
/* 146 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 148 */       return 0L;
/*     */     }
/*     */     
/* 151 */     return ((NBTTagLong)this.tagMap.get(s)).longValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public float getFloat(String s) {
/* 157 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 159 */       return 0.0F;
/*     */     }
/*     */     
/* 162 */     return ((NBTTagFloat)this.tagMap.get(s)).floatValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public double getDouble(String s) {
/* 168 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 170 */       return 0.0D;
/*     */     }
/*     */     
/* 173 */     return ((NBTTagDouble)this.tagMap.get(s)).doubleValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public String getString(String s) {
/* 179 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 181 */       return "";
/*     */     }
/*     */     
/* 184 */     return ((NBTTagString)this.tagMap.get(s)).stringValue;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public byte[] getByteArray(String s) {
/* 190 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 192 */       return new byte[0];
/*     */     }
/*     */     
/* 195 */     return ((NBTTagByteArray)this.tagMap.get(s)).byteArray;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTTagCompound getCompoundTag(String s) {
/* 201 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 203 */       return new NBTTagCompound();
/*     */     }
/*     */     
/* 206 */     return (NBTTagCompound)this.tagMap.get(s);
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTTagList getTagList(String s) {
/* 212 */     if (!this.tagMap.containsKey(s))
/*     */     {
/* 214 */       return new NBTTagList();
/*     */     }
/*     */     
/* 217 */     return (NBTTagList)this.tagMap.get(s);
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public boolean getBoolean(String s) {
/* 223 */     return (getByte(s) != 0);
/*     */   }
/*     */ 
/*     */   
/*     */   public String toString() {
/* 228 */     return "" + this.tagMap.size() + " entries";
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagCompound.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */