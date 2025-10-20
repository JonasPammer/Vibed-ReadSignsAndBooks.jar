/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ public class NBTTagShort
/*    */   extends NBTBase.NBTPrimitive
/*    */ {
/*    */   private short data;
/*    */   private static final String __OBFID = "CL_00001227";
/*    */   
/*    */   public NBTTagShort() {}
/*    */   
/*    */   public NBTTagShort(short p_i45135_1_) {
/* 16 */     this.data = p_i45135_1_;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 24 */     p_74734_1_.writeShort(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 29 */     p_152446_3_.func_152450_a(16L);
/* 30 */     this.data = p_152446_1_.readShort();
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 38 */     return 2;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 43 */     return this.data + "s";
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 51 */     return new NBTTagShort(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 56 */     if (super.equals(p_equals_1_)) {
/*    */       
/* 58 */       NBTTagShort nbttagshort = (NBTTagShort)p_equals_1_;
/* 59 */       return (this.data == nbttagshort.data);
/*    */     } 
/*    */ 
/*    */     
/* 63 */     return false;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 69 */     return super.hashCode() ^ this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public long func_150291_c() {
/* 74 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public int func_150287_d() {
/* 79 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public short func_150289_e() {
/* 84 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public byte func_150290_f() {
/* 89 */     return (byte)(this.data & 0xFF);
/*    */   }
/*    */ 
/*    */   
/*    */   public double func_150286_g() {
/* 94 */     return this.data;
/*    */   }
/*    */ 
/*    */   
/*    */   public float func_150288_h() {
/* 99 */     return this.data;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagShort.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */