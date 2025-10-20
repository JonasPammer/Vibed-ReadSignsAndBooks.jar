/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ import java.util.Arrays;
/*    */ 
/*    */ public class NBTTagByteArray
/*    */   extends NBTBase
/*    */ {
/*    */   private byte[] byteArray;
/*    */   private static final String __OBFID = "CL_00001213";
/*    */   
/*    */   NBTTagByteArray() {}
/*    */   
/*    */   public NBTTagByteArray(byte[] p_i45128_1_) {
/* 17 */     this.byteArray = p_i45128_1_;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 25 */     p_74734_1_.writeInt(this.byteArray.length);
/* 26 */     p_74734_1_.write(this.byteArray);
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 31 */     int j = p_152446_1_.readInt();
/* 32 */     p_152446_3_.func_152450_a((8 * j));
/* 33 */     this.byteArray = new byte[j];
/* 34 */     p_152446_1_.readFully(this.byteArray);
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 42 */     return 7;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 47 */     return "[" + this.byteArray.length + " bytes]";
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 55 */     byte[] abyte = new byte[this.byteArray.length];
/* 56 */     System.arraycopy(this.byteArray, 0, abyte, 0, this.byteArray.length);
/* 57 */     return new NBTTagByteArray(abyte);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 62 */     return super.equals(p_equals_1_) ? Arrays.equals(this.byteArray, ((NBTTagByteArray)p_equals_1_).byteArray) : false;
/*    */   }
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 67 */     return super.hashCode() ^ Arrays.hashCode(this.byteArray);
/*    */   }
/*    */ 
/*    */   
/*    */   public byte[] func_150292_c() {
/* 72 */     return this.byteArray;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagByteArray.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */