/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ import java.util.Arrays;
/*    */ 
/*    */ public class NBTTagIntArray
/*    */   extends NBTBase
/*    */ {
/*    */   private int[] intArray;
/*    */   private static final String __OBFID = "CL_00001221";
/*    */   
/*    */   NBTTagIntArray() {}
/*    */   
/*    */   public NBTTagIntArray(int[] p_i45132_1_) {
/* 17 */     this.intArray = p_i45132_1_;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 25 */     p_74734_1_.writeInt(this.intArray.length);
/*    */     
/* 27 */     for (int i = 0; i < this.intArray.length; i++)
/*    */     {
/* 29 */       p_74734_1_.writeInt(this.intArray[i]);
/*    */     }
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 35 */     int j = p_152446_1_.readInt();
/* 36 */     p_152446_3_.func_152450_a((32 * j));
/* 37 */     this.intArray = new int[j];
/*    */     
/* 39 */     for (int k = 0; k < j; k++)
/*    */     {
/* 41 */       this.intArray[k] = p_152446_1_.readInt();
/*    */     }
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 50 */     return 11;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 55 */     String s = "[";
/* 56 */     int[] aint = this.intArray;
/* 57 */     int i = aint.length;
/*    */     
/* 59 */     for (int j = 0; j < i; j++) {
/*    */       
/* 61 */       int k = aint[j];
/* 62 */       s = String.valueOf(s) + k + ",";
/*    */     } 
/*    */     
/* 65 */     return String.valueOf(s) + "]";
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 73 */     int[] aint = new int[this.intArray.length];
/* 74 */     System.arraycopy(this.intArray, 0, aint, 0, this.intArray.length);
/* 75 */     return new NBTTagIntArray(aint);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 80 */     return super.equals(p_equals_1_) ? Arrays.equals(this.intArray, ((NBTTagIntArray)p_equals_1_).intArray) : false;
/*    */   }
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 85 */     return super.hashCode() ^ Arrays.hashCode(this.intArray);
/*    */   }
/*    */ 
/*    */   
/*    */   public int[] func_150302_c() {
/* 90 */     return this.intArray;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagIntArray.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */