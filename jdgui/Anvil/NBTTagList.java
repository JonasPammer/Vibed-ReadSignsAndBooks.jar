/*     */ package Anvil;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Iterator;
/*     */ import java.util.List;
/*     */ 
/*     */ public class NBTTagList
/*     */   extends NBTBase {
/*  12 */   private List tagList = new ArrayList();
/*     */ 
/*     */ 
/*     */   
/*  16 */   private byte tagType = 0;
/*     */ 
/*     */   
/*     */   private static final String __OBFID = "CL_00001224";
/*     */ 
/*     */ 
/*     */   
/*     */   void write(DataOutput p_74734_1_) throws IOException {
/*  24 */     if (!this.tagList.isEmpty()) {
/*     */       
/*  26 */       this.tagType = ((NBTBase)this.tagList.get(0)).getId();
/*     */     }
/*     */     else {
/*     */       
/*  30 */       this.tagType = 0;
/*     */     } 
/*     */     
/*  33 */     p_74734_1_.writeByte(this.tagType);
/*  34 */     p_74734_1_.writeInt(this.tagList.size());
/*     */     
/*  36 */     for (int i = 0; i < this.tagList.size(); i++)
/*     */     {
/*  38 */       ((NBTBase)this.tagList.get(i)).write(p_74734_1_);
/*     */     }
/*     */   }
/*     */ 
/*     */   
/*     */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/*  44 */     if (p_152446_2_ > 512)
/*     */     {
/*  46 */       throw new RuntimeException("Tried to read NBT tag with too high complexity, depth > 512");
/*     */     }
/*     */ 
/*     */     
/*  50 */     p_152446_3_.func_152450_a(8L);
/*  51 */     this.tagType = p_152446_1_.readByte();
/*  52 */     int j = p_152446_1_.readInt();
/*  53 */     this.tagList = new ArrayList();
/*     */     
/*  55 */     for (int k = 0; k < j; k++) {
/*     */       
/*  57 */       NBTBase nbtbase = NBTBase.func_150284_a(this.tagType);
/*  58 */       nbtbase.func_152446_a(p_152446_1_, p_152446_2_ + 1, p_152446_3_);
/*  59 */       this.tagList.add(nbtbase);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte getId() {
/*  69 */     return 9;
/*     */   }
/*     */ 
/*     */   
/*     */   public String toString() {
/*  74 */     String s = "[";
/*  75 */     int i = 0;
/*     */     
/*  77 */     for (Iterator<NBTBase> iterator = this.tagList.iterator(); iterator.hasNext(); i++) {
/*     */       
/*  79 */       NBTBase nbtbase = iterator.next();
/*  80 */       s = String.valueOf(s) + i + ':' + nbtbase + ',';
/*     */     } 
/*     */     
/*  83 */     return String.valueOf(s) + "]";
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void appendTag(NBTBase p_74742_1_) {
/*  92 */     if (this.tagType == 0) {
/*     */       
/*  94 */       this.tagType = p_74742_1_.getId();
/*     */     }
/*  96 */     else if (this.tagType != p_74742_1_.getId()) {
/*     */       
/*  98 */       System.err.println("WARNING: Adding mismatching tag types to tag list");
/*     */       
/*     */       return;
/*     */     } 
/* 102 */     this.tagList.add(p_74742_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public void func_150304_a(int p_150304_1_, NBTBase p_150304_2_) {
/* 107 */     if (p_150304_1_ >= 0 && p_150304_1_ < this.tagList.size()) {
/*     */       
/* 109 */       if (this.tagType == 0) {
/*     */         
/* 111 */         this.tagType = p_150304_2_.getId();
/*     */       }
/* 113 */       else if (this.tagType != p_150304_2_.getId()) {
/*     */         
/* 115 */         System.err.println("WARNING: Adding mismatching tag types to tag list");
/*     */         
/*     */         return;
/*     */       } 
/* 119 */       this.tagList.set(p_150304_1_, p_150304_2_);
/*     */     }
/*     */     else {
/*     */       
/* 123 */       System.err.println("WARNING: index out of bounds to set tag in tag list");
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase removeTag(int p_74744_1_) {
/* 132 */     return this.tagList.remove(p_74744_1_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTTagCompound getCompoundTagAt(int p_150305_1_) {
/* 140 */     if (p_150305_1_ >= 0 && p_150305_1_ < this.tagList.size()) {
/*     */       
/* 142 */       NBTBase nbtbase = this.tagList.get(p_150305_1_);
/* 143 */       return (nbtbase.getId() == 10) ? (NBTTagCompound)nbtbase : new NBTTagCompound();
/*     */     } 
/*     */ 
/*     */     
/* 147 */     return new NBTTagCompound();
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public int[] func_150306_c(int p_150306_1_) {
/* 153 */     if (p_150306_1_ >= 0 && p_150306_1_ < this.tagList.size()) {
/*     */       
/* 155 */       NBTBase nbtbase = this.tagList.get(p_150306_1_);
/* 156 */       return (nbtbase.getId() == 11) ? ((NBTTagIntArray)nbtbase).func_150302_c() : new int[0];
/*     */     } 
/*     */ 
/*     */     
/* 160 */     return new int[0];
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public double func_150309_d(int p_150309_1_) {
/* 166 */     if (p_150309_1_ >= 0 && p_150309_1_ < this.tagList.size()) {
/*     */       
/* 168 */       NBTBase nbtbase = this.tagList.get(p_150309_1_);
/* 169 */       return (nbtbase.getId() == 6) ? ((NBTTagDouble)nbtbase).func_150286_g() : 0.0D;
/*     */     } 
/*     */ 
/*     */     
/* 173 */     return 0.0D;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public float func_150308_e(int p_150308_1_) {
/* 179 */     if (p_150308_1_ >= 0 && p_150308_1_ < this.tagList.size()) {
/*     */       
/* 181 */       NBTBase nbtbase = this.tagList.get(p_150308_1_);
/* 182 */       return (nbtbase.getId() == 5) ? ((NBTTagFloat)nbtbase).func_150288_h() : 0.0F;
/*     */     } 
/*     */ 
/*     */     
/* 186 */     return 0.0F;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getStringTagAt(int p_150307_1_) {
/* 195 */     if (p_150307_1_ >= 0 && p_150307_1_ < this.tagList.size()) {
/*     */       
/* 197 */       NBTBase nbtbase = this.tagList.get(p_150307_1_);
/* 198 */       return (nbtbase.getId() == 8) ? nbtbase.func_150285_a_() : nbtbase.toString();
/*     */     } 
/*     */ 
/*     */     
/* 202 */     return "";
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public int tagCount() {
/* 211 */     return this.tagList.size();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase copy() {
/* 219 */     NBTTagList nbttaglist = new NBTTagList();
/* 220 */     nbttaglist.tagType = this.tagType;
/* 221 */     Iterator<NBTBase> iterator = this.tagList.iterator();
/*     */     
/* 223 */     while (iterator.hasNext()) {
/*     */       
/* 225 */       NBTBase nbtbase = iterator.next();
/* 226 */       NBTBase nbtbase1 = nbtbase.copy();
/* 227 */       nbttaglist.tagList.add(nbtbase1);
/*     */     } 
/*     */     
/* 230 */     return nbttaglist;
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean equals(Object p_equals_1_) {
/* 235 */     if (super.equals(p_equals_1_)) {
/*     */       
/* 237 */       NBTTagList nbttaglist = (NBTTagList)p_equals_1_;
/*     */       
/* 239 */       if (this.tagType == nbttaglist.tagType)
/*     */       {
/* 241 */         return this.tagList.equals(nbttaglist.tagList);
/*     */       }
/*     */     } 
/*     */     
/* 245 */     return false;
/*     */   }
/*     */ 
/*     */   
/*     */   public int hashCode() {
/* 250 */     return super.hashCode() ^ this.tagList.hashCode();
/*     */   }
/*     */ 
/*     */   
/*     */   public int func_150303_d() {
/* 255 */     return this.tagType;
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagList.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */