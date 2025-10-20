/*     */ package Anvil;
/*     */ 
/*     */ import java.io.DataInput;
/*     */ import java.io.DataOutput;
/*     */ import java.io.IOException;
/*     */ 
/*     */ 
/*     */ public class NBTTagDouble
/*     */   extends NBTBase.NBTPrimitive
/*     */ {
/*     */   private double data;
/*     */   private static final String __OBFID = "CL_00001218";
/*     */   
/*     */   NBTTagDouble() {}
/*     */   
/*     */   public NBTTagDouble(double p_i45130_1_) {
/*  17 */     this.data = p_i45130_1_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   void write(DataOutput p_74734_1_) throws IOException {
/*  25 */     p_74734_1_.writeDouble(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/*  30 */     p_152446_3_.func_152450_a(64L);
/*  31 */     this.data = p_152446_1_.readDouble();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public byte getId() {
/*  39 */     return 6;
/*     */   }
/*     */ 
/*     */   
/*     */   public String toString() {
/*  44 */     return this.data + "d";
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public NBTBase copy() {
/*  52 */     return new NBTTagDouble(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean equals(Object p_equals_1_) {
/*  57 */     if (super.equals(p_equals_1_)) {
/*     */       
/*  59 */       NBTTagDouble nbttagdouble = (NBTTagDouble)p_equals_1_;
/*  60 */       return (this.data == nbttagdouble.data);
/*     */     } 
/*     */ 
/*     */     
/*  64 */     return false;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public int hashCode() {
/*  70 */     long i = Double.doubleToLongBits(this.data);
/*  71 */     return super.hashCode() ^ (int)(i ^ i >>> 32L);
/*     */   }
/*     */ 
/*     */   
/*     */   public long func_150291_c() {
/*  76 */     return (long)Math.floor(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public int func_150287_d() {
/*  81 */     return MathHelper.floor_double(this.data);
/*     */   }
/*     */ 
/*     */   
/*     */   public short func_150289_e() {
/*  86 */     return (short)(MathHelper.floor_double(this.data) & 0xFFFF);
/*     */   }
/*     */ 
/*     */   
/*     */   public byte func_150290_f() {
/*  91 */     return (byte)(MathHelper.floor_double(this.data) & 0xFF);
/*     */   }
/*     */ 
/*     */   
/*     */   public double func_150286_g() {
/*  96 */     return this.data;
/*     */   }
/*     */ 
/*     */   
/*     */   public float func_150288_h() {
/* 101 */     return (float)this.data;
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagDouble.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */