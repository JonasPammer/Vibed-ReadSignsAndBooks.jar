/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ public class NBTTagLong
/*    */   extends NBTBase.NBTPrimitive
/*    */ {
/*    */   private long data;
/*    */   private static final String __OBFID = "CL_00001225";
/*    */   
/*    */   NBTTagLong() {}
/*    */   
/*    */   public NBTTagLong(long p_i45134_1_) {
/* 16 */     this.data = p_i45134_1_;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 24 */     p_74734_1_.writeLong(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 29 */     p_152446_3_.func_152450_a(64L);
/* 30 */     this.data = p_152446_1_.readLong();
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 38 */     return 4;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 43 */     return this.data + "L";
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 51 */     return new NBTTagLong(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 56 */     if (super.equals(p_equals_1_)) {
/*    */       
/* 58 */       NBTTagLong nbttaglong = (NBTTagLong)p_equals_1_;
/* 59 */       return (this.data == nbttaglong.data);
/*    */     } 
/*    */ 
/*    */     
/* 63 */     return false;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 69 */     return super.hashCode() ^ (int)(this.data ^ this.data >>> 32L);
/*    */   }
/*    */ 
/*    */   
/*    */   public long func_150291_c() {
/* 74 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public int func_150287_d() {
/* 79 */     return (int)(this.data & 0xFFFFFFFFFFFFFFFFL);
/*    */   }
/*    */ 
/*    */   
/*    */   public short func_150289_e() {
/* 84 */     return (short)(int)(this.data & 0xFFFFL);
/*    */   }
/*    */ 
/*    */   
/*    */   public byte func_150290_f() {
/* 89 */     return (byte)(int)(this.data & 0xFFL);
/*    */   }
/*    */ 
/*    */   
/*    */   public double func_150286_g() {
/* 94 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public float func_150288_h() {
/* 99 */     return (float)this.data;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagLong.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */