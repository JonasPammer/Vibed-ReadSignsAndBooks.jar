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
/*    */ public class NBTTagFloat
/*    */   extends NBTBase
/*    */ {
/*    */   public float floatValue;
/*    */   
/*    */   public NBTTagFloat() {}
/*    */   
/*    */   public NBTTagFloat(float f) {
/* 19 */     this.floatValue = f;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 25 */     dataoutput.writeFloat(this.floatValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 31 */     this.floatValue = datainput.readFloat();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 36 */     return 5;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 41 */     return "" + this.floatValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagFloat.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */