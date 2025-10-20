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
/*    */ public class NBTTagByteArray
/*    */   extends NBTBase
/*    */ {
/*    */   public byte[] byteArray;
/*    */   
/*    */   public NBTTagByteArray() {}
/*    */   
/*    */   public NBTTagByteArray(byte[] abyte0) {
/* 19 */     this.byteArray = abyte0;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 25 */     dataoutput.writeInt(this.byteArray.length);
/* 26 */     dataoutput.write(this.byteArray);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 32 */     int i = datainput.readInt();
/* 33 */     this.byteArray = new byte[i];
/* 34 */     datainput.readFully(this.byteArray);
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 39 */     return 7;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 44 */     return "[" + this.byteArray.length + " bytes]";
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagByteArray.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */