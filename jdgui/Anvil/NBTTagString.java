/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ public class NBTTagString
/*    */   extends NBTBase
/*    */ {
/*    */   private String data;
/*    */   private static final String __OBFID = "CL_00001228";
/*    */   
/*    */   public NBTTagString() {
/* 14 */     this.data = "";
/*    */   }
/*    */ 
/*    */   
/*    */   public NBTTagString(String p_i1389_1_) {
/* 19 */     this.data = p_i1389_1_;
/*    */     
/* 21 */     if (p_i1389_1_ == null)
/*    */     {
/* 23 */       throw new IllegalArgumentException("Empty string not allowed");
/*    */     }
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void write(DataOutput p_74734_1_) throws IOException {
/* 32 */     p_74734_1_.writeUTF(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException {
/* 37 */     this.data = p_152446_1_.readUTF();
/* 38 */     p_152446_3_.func_152450_a((16 * this.data.length()));
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getId() {
/* 46 */     return 8;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 51 */     return "\"" + this.data + "\"";
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public NBTBase copy() {
/* 59 */     return new NBTTagString(this.data);
/*    */   }
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 64 */     if (!super.equals(p_equals_1_))
/*    */     {
/* 66 */       return false;
/*    */     }
/*    */ 
/*    */     
/* 70 */     NBTTagString nbttagstring = (NBTTagString)p_equals_1_;
/* 71 */     return !((this.data != null || nbttagstring.data != null) && (this.data == null || !this.data.equals(nbttagstring.data)));
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 77 */     return super.hashCode() ^ this.data.hashCode();
/*    */   }
/*    */ 
/*    */   
/*    */   public String func_150285_a_() {
/* 82 */     return this.data;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTTagString.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */