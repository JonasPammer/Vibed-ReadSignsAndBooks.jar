/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class NBTTagShort
/*    */   extends NBTBase
/*    */ {
/*    */   public short shortValue;
/*    */   
/*    */   public NBTTagShort() {}
/*    */   
/*    */   public NBTTagShort(short word0) {
/* 18 */     this.shortValue = word0;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 24 */     dataoutput.writeShort(this.shortValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 30 */     this.shortValue = datainput.readShort();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 35 */     return 2;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 40 */     return "" + this.shortValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagShort.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */