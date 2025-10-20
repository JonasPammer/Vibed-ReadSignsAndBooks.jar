/*     */ package Anvil;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ 
/*     */ 
/*     */ public class NBTTagFloat
/*     */   extends NBTBase.NBTPrimitive
/*     */ {
/*     */   private float data;
/*     */   private static final String __OBFID = "CL_00001220";
/*     */   
/*     */   NBTTagFloat() {}
/*     */   
/*     */   public NBTTagFloat(float p_i45131_1_) {
/*  17 */     this.data = p_i45131_1_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   void write(DataOutput p_74734_1_) throws IOException {
/*  25 */     p_74734_1_.writeFloat(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/*  30 */     p_152446_3_.func_152450_a(32L);
/*  31 */     this.data = p_152446_1_.readFloat();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte getId() {
/*  39 */     return 5;
/*     */   }
/*     */ 
/*     */   
/*     */   public String toString() {
/*  44 */     return this.data + "f";
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase copy() {
/*  52 */     return new NBTTagFloat(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean equals(Object p_equals_1_) {
/*  57 */     if (super.equals(p_equals_1_)) {
/*     */       
/*  59 */       NBTTagFloat nbttagfloat = (NBTTagFloat)p_equals_1_;
/*  60 */       return (this.data == nbttagfloat.data);
/*     */     } 
/*     */ 
/*     */     
/*  64 */     return false;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public int hashCode() {
/*  70 */     return super.hashCode() ^ Float.floatToIntBits(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public long func_150291_c() {
/*  75 */     return (long)this.data;
/*     */   }
/*     */ 
/*     */   
/*     */   public int func_150287_d() {
/*  80 */     return MathHelper.floor_float(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public short func_150289_e() {
/*  85 */     return (short)(MathHelper.floor_float(this.data) & 0xFFFF);
/*     */   }
/*     */ 
/*     */   
/*     */   public byte func_150290_f() {
/*  90 */     return (byte)(MathHelper.floor_float(this.data) & 0xFF);
/*     */   }
/*     */ 
/*     */   
/*     */   public double func_150286_g() {
/*  95 */     return this.data;
/*     */   }
/*     */ 
/*     */   
/*     */   public float func_150288_h() {
/* 100 */     return this.data;
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagFloat.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */