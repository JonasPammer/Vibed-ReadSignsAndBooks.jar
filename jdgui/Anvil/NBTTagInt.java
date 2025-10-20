/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ public class NBTTagInt
/*    */   extends NBTBase.NBTPrimitive
/*    */ {
/*    */   private int data;
/*    */   private static final String __OBFID = "CL_00001223";
/*    */   
/*    */   NBTTagInt() {}
/*    */   
/*    */   public NBTTagInt(int p_i45133_1_) {
/* 16 */     this.data = p_i45133_1_;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 24 */     p_74734_1_.writeInt(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 29 */     p_152446_3_.func_152450_a(32L);
/* 30 */     this.data = p_152446_1_.readInt();
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 38 */     return 3;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 43 */     return this.data;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 51 */     return new NBTTagInt(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 56 */     if (super.equals(p_equals_1_)) {
/*    */       
/* 58 */       NBTTagInt nbttagint = (NBTTagInt)p_equals_1_;
/* 59 */       return (this.data == nbttagint.data);
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
/* 84 */     return (short)(this.data & 0xFFFF);
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


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagInt.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */