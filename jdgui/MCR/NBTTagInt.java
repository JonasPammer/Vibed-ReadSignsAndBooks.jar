/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class NBTTagInt
/*    */   extends NBTBase
/*    */ {
/*    */   public int intValue;
/*    */   
/*    */   public NBTTagInt() {}
/*    */   
/*    */   public NBTTagInt(int i) {
/* 18 */     this.intValue = i;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 24 */     dataoutput.writeInt(this.intValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 30 */     this.intValue = datainput.readInt();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 35 */     return 3;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 40 */     return "" + this.intValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagInt.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */