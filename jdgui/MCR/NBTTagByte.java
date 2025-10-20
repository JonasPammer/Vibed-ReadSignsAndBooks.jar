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
/*    */ 
/*    */ public class NBTTagByte
/*    */   extends NBTBase
/*    */ {
/*    */   public byte byteValue;
/*    */   
/*    */   public NBTTagByte() {}
/*    */   
/*    */   public NBTTagByte(byte byte0) {
/* 20 */     this.byteValue = byte0;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 26 */     dataoutput.writeByte(this.byteValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 32 */     this.byteValue = datainput.readByte();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 37 */     return 1;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 42 */     return "" + this.byteValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagByte.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */