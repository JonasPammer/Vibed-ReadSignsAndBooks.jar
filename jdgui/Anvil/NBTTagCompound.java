/*     */ package Anvil;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ import java.util.HashMap;
/*     */ import java.util.Iterator;
/*     */ import java.util.Map;
/*     */ import java.util.Set;
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
/*     */   private static final String __OBFID = "CL_00001215";
/*     */ 
/*     */ 
/*     */   
/*     */   void write(DataOutput p_74734_1_) throws IOException {
/*  26 */     Iterator<String> iterator = this.tagMap.keySet().iterator();
/*     */     
/*  28 */     while (iterator.hasNext()) {
/*     */       
/*  30 */       String s = iterator.next();
/*  31 */       NBTBase nbtbase = (NBTBase)this.tagMap.get(s);
/*  32 */       func_150298_a(s, nbtbase, p_74734_1_);
/*     */     } 
/*     */     
/*  35 */     p_74734_1_.writeByte(0);
/*     */   }
/*     */ 
/*     */   
/*     */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/*  40 */     if (p_152446_2_ > 512)
/*     */     {
/*  42 */       throw new RuntimeException("Tried to read NBT tag with too high complexity, depth > 512");
/*     */     }
/*     */ 
/*     */     
/*  46 */     this.tagMap.clear();
/*     */     
/*     */     byte b0;
/*  49 */     while ((b0 = func_152447_a(p_152446_1_, p_152446_3_)) != 0) {
/*     */       
/*  51 */       String s = func_152448_b(p_152446_1_, p_152446_3_);
/*  52 */       p_152446_3_.func_152450_a((16 * s.length()));
/*  53 */       NBTBase nbtbase = func_152449_a(b0, s, p_152446_1_, p_152446_2_ + 1, p_152446_3_);
/*  54 */       this.tagMap.put(s, nbtbase);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public Set func_150296_c() {
/*  61 */     return this.tagMap.keySet();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte getId() {
/*  69 */     return 10;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setTag(String p_74782_1_, NBTBase p_74782_2_) {
/*  77 */     this.tagMap.put(p_74782_1_, p_74782_2_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setByte(String p_74774_1_, byte p_74774_2_) {
/*  85 */     this.tagMap.put(p_74774_1_, new NBTTagByte(p_74774_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setShort(String p_74777_1_, short p_74777_2_) {
/*  93 */     this.tagMap.put(p_74777_1_, new NBTTagShort(p_74777_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setInteger(String p_74768_1_, int p_74768_2_) {
/* 101 */     this.tagMap.put(p_74768_1_, new NBTTagInt(p_74768_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setLong(String p_74772_1_, long p_74772_2_) {
/* 109 */     this.tagMap.put(p_74772_1_, new NBTTagLong(p_74772_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setFloat(String p_74776_1_, float p_74776_2_) {
/* 117 */     this.tagMap.put(p_74776_1_, new NBTTagFloat(p_74776_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setDouble(String p_74780_1_, double p_74780_2_) {
/* 125 */     this.tagMap.put(p_74780_1_, new NBTTagDouble(p_74780_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setString(String p_74778_1_, String p_74778_2_) {
/* 133 */     this.tagMap.put(p_74778_1_, new NBTTagString(p_74778_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setByteArray(String p_74773_1_, byte[] p_74773_2_) {
/* 141 */     this.tagMap.put(p_74773_1_, new NBTTagByteArray(p_74773_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setIntArray(String p_74783_1_, int[] p_74783_2_) {
/* 149 */     this.tagMap.put(p_74783_1_, new NBTTagIntArray(p_74783_2_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setBoolean(String p_74757_1_, boolean p_74757_2_) {
/* 157 */     setByte(p_74757_1_, (byte)(p_74757_2_ ? 1 : 0));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase getTag(String p_74781_1_) {
/* 165 */     return (NBTBase)this.tagMap.get(p_74781_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public byte func_150299_b(String p_150299_1_) {
/* 170 */     NBTBase nbtbase = (NBTBase)this.tagMap.get(p_150299_1_);
/* 171 */     return (nbtbase != null) ? nbtbase.getId() : 0;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public boolean hasKey(String p_74764_1_) {
/* 179 */     return this.tagMap.containsKey(p_74764_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean hasKey(String p_150297_1_, int p_150297_2_) {
/* 184 */     byte b0 = func_150299_b(p_150297_1_);
/* 185 */     return (b0 == p_150297_2_) ? true : ((p_150297_2_ != 99) ? false : (!(b0 != 1 && b0 != 2 && b0 != 3 && b0 != 4 && b0 != 5 && b0 != 6)));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte getByte(String p_74771_1_) {
/*     */     try {
/* 195 */       return !this.tagMap.containsKey(p_74771_1_) ? 0 : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74771_1_)).func_150290_f();
/*     */     }
/* 197 */     catch (ClassCastException classcastexception) {
/*     */       
/* 199 */       return 0;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public short getShort(String p_74765_1_) {
/*     */     try {
/* 210 */       return !this.tagMap.containsKey(p_74765_1_) ? 0 : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74765_1_)).func_150289_e();
/*     */     }
/* 212 */     catch (ClassCastException classcastexception) {
/*     */       
/* 214 */       return 0;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public int getInteger(String p_74762_1_) {
/*     */     try {
/* 225 */       return !this.tagMap.containsKey(p_74762_1_) ? 0 : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74762_1_)).func_150287_d();
/*     */     }
/* 227 */     catch (ClassCastException classcastexception) {
/*     */       
/* 229 */       return 0;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public long getLong(String p_74763_1_) {
/*     */     try {
/* 240 */       return !this.tagMap.containsKey(p_74763_1_) ? 0L : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74763_1_)).func_150291_c();
/*     */     }
/* 242 */     catch (ClassCastException classcastexception) {
/*     */       
/* 244 */       return 0L;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public float getFloat(String p_74760_1_) {
/*     */     try {
/* 255 */       return !this.tagMap.containsKey(p_74760_1_) ? 0.0F : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74760_1_)).func_150288_h();
/*     */     }
/* 257 */     catch (ClassCastException classcastexception) {
/*     */       
/* 259 */       return 0.0F;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public double getDouble(String p_74769_1_) {
/*     */     try {
/* 270 */       return !this.tagMap.containsKey(p_74769_1_) ? 0.0D : ((NBTBase.NBTPrimitive)this.tagMap.get(p_74769_1_)).func_150286_g();
/*     */     }
/* 272 */     catch (ClassCastException classcastexception) {
/*     */       
/* 274 */       return 0.0D;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getString(String p_74779_1_) {
/*     */     try {
/* 285 */       return !this.tagMap.containsKey(p_74779_1_) ? "" : ((NBTBase)this.tagMap.get(p_74779_1_)).func_150285_a_();
/*     */     }
/* 287 */     catch (ClassCastException classcastexception) {
/*     */       
/* 289 */       return "";
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte[] getByteArray(String p_74770_1_) {
/*     */     try {
/* 300 */       return !this.tagMap.containsKey(p_74770_1_) ? new byte[0] : ((NBTTagByteArray)this.tagMap.get(p_74770_1_)).func_150292_c();
/*     */     }
/* 302 */     catch (ClassCastException classcastexception) {
/*     */       
/* 304 */       throw null;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public int[] getIntArray(String p_74759_1_) {
/*     */     try {
/* 315 */       return !this.tagMap.containsKey(p_74759_1_) ? new int[0] : ((NBTTagIntArray)this.tagMap.get(p_74759_1_)).func_150302_c();
/*     */     }
/* 317 */     catch (ClassCastException classcastexception) {
/*     */       
/* 319 */       throw null;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTTagCompound getCompoundTag(String p_74775_1_) {
/*     */     try {
/* 331 */       return !this.tagMap.containsKey(p_74775_1_) ? new NBTTagCompound() : (NBTTagCompound)this.tagMap.get(p_74775_1_);
/*     */     }
/* 333 */     catch (ClassCastException classcastexception) {
/*     */       
/* 335 */       throw null;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTTagList getTagList(String p_150295_1_, int p_150295_2_) {
/*     */     try {
/* 346 */       if (func_150299_b(p_150295_1_) != 9)
/*     */       {
/* 348 */         return new NBTTagList();
/*     */       }
/*     */ 
/*     */       
/* 352 */       NBTTagList nbttaglist = (NBTTagList)this.tagMap.get(p_150295_1_);
/* 353 */       return (nbttaglist.tagCount() > 0 && nbttaglist.func_150303_d() != p_150295_2_) ? new NBTTagList() : nbttaglist;
/*     */     
/*     */     }
/* 356 */     catch (ClassCastException classcastexception) {
/*     */       
/* 358 */       throw null;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public boolean getBoolean(String p_74767_1_) {
/* 368 */     return (getByte(p_74767_1_) != 0);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void removeTag(String p_82580_1_) {
/* 376 */     this.tagMap.remove(p_82580_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public String toString() {
/* 381 */     String s = "{";
/*     */ 
/*     */     
/* 384 */     for (Iterator<String> iterator = this.tagMap.keySet().iterator(); iterator.hasNext(); s = String.valueOf(s) + s1 + ':' + this.tagMap.get(s1) + ',')
/*     */     {
/* 386 */       String s1 = iterator.next();
/*     */     }
/*     */     
/* 389 */     return String.valueOf(s) + "}";
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public boolean hasNoTags() {
/* 397 */     return this.tagMap.isEmpty();
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
/*     */   public NBTBase copy() {
/* 410 */     NBTTagCompound nbttagcompound = new NBTTagCompound();
/* 411 */     Iterator<String> iterator = this.tagMap.keySet().iterator();
/*     */     
/* 413 */     while (iterator.hasNext()) {
/*     */       
/* 415 */       String s = iterator.next();
/* 416 */       nbttagcompound.setTag(s, ((NBTBase)this.tagMap.get(s)).copy());
/*     */     } 
/*     */     
/* 419 */     return nbttagcompound;
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean equals(Object p_equals_1_) {
/* 424 */     if (super.equals(p_equals_1_)) {
/*     */       
/* 426 */       NBTTagCompound nbttagcompound = (NBTTagCompound)p_equals_1_;
/* 427 */       return this.tagMap.entrySet().equals(nbttagcompound.tagMap.entrySet());
/*     */     } 
/*     */ 
/*     */     
/* 431 */     return false;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public int hashCode() {
/* 437 */     return super.hashCode() ^ this.tagMap.hashCode();
/*     */   }
/*     */ 
/*     */   
/*     */   private static void func_150298_a(String p_150298_0_, NBTBase p_150298_1_, DataOutput p_150298_2_) throws IOException {
/* 442 */     p_150298_2_.writeByte(p_150298_1_.getId());
/*     */     
/* 444 */     if (p_150298_1_.getId() != 0) {
/*     */       
/* 446 */       p_150298_2_.writeUTF(p_150298_0_);
/* 447 */       p_150298_1_.write(p_150298_2_);
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private static byte func_152447_a(DataInput p_152447_0_, NBTSizeTracker p_152447_1_) throws IOException {
/* 453 */     return p_152447_0_.readByte();
/*     */   }
/*     */ 
/*     */   
/*     */   private static String func_152448_b(DataInput p_152448_0_, NBTSizeTracker p_152448_1_) throws IOException {
/* 458 */     return p_152448_0_.readUTF();
/*     */   }
/*     */ 
/*     */   
/*     */   static NBTBase func_152449_a(byte p_152449_0_, String p_152449_1_, DataInput p_152449_2_, int p_152449_3_, NBTSizeTracker p_152449_4_) {
/* 463 */     NBTBase nbtbase = NBTBase.func_150284_a(p_152449_0_);
/*     */ 
/*     */     
/*     */     try {
/* 467 */       nbtbase.func_152446_a(p_152449_2_, p_152449_3_, p_152449_4_);
/* 468 */       return nbtbase;
/*     */     }
/* 470 */     catch (IOException ioexception) {
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 477 */       return nbtbase;
/*     */     } 
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagCompound.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */