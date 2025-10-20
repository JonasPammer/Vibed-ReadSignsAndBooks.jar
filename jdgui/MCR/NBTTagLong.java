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
/*    */ public class NBTTagLong
/*    */   extends NBTBase
/*    */ {
/*    */   public long longValue;
/*    */   
/*    */   public NBTTagLong() {}
/*    */   
/*    */   public NBTTagLong(long l) {
/* 19 */     this.longValue = l;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 25 */     dataoutput.writeLong(this.longValue);
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 31 */     this.longValue = datainput.readLong();
/*    */   }
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 36 */     return 4;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 41 */     return "" + this.longValue;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagLong.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */