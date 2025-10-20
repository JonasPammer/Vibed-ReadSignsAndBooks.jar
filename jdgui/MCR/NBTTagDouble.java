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
/*    */ public class NBTTagDouble
/*    */   extends NBTBase
/*    */ {
/*    */   public double doubleValue;
/*    */   
/*    */   public NBTTagDouble() {}
/*    */   
/*    */   public NBTTagDouble(double d) {
/* 19 */     this.doubleValue = d;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 25 */     dataoutput.writeDouble(this.doubleValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 31 */     this.doubleValue = datainput.readDouble();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 36 */     return 6;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 41 */     return "" + this.doubleValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagDouble.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */