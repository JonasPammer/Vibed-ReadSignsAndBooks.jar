/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class NBTTagString
/*    */   extends NBTBase
/*    */ {
/*    */   public String stringValue;
/*    */   
/*    */   public NBTTagString() {}
/*    */   
/*    */   public NBTTagString(String s) {
/* 19 */     this.stringValue = s;
/* 20 */     if (s == null)
/*    */     {
/* 22 */       throw new IllegalArgumentException("Empty string not allowed");
/*    */     }
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 32 */     dataoutput.writeUTF(this.stringValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 38 */     this.stringValue = datainput.readUTF();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 43 */     return 8;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 48 */     return "" + this.stringValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagString.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */