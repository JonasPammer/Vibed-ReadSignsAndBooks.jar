/*    */ package MCR;
/*    */ 
/*    */ import java.io.ByteArrayOutputStream;
/*    */ 
/*    */ 
/*    */ 
/*    */ class RegionFileChunkBuffer
/*    */   extends ByteArrayOutputStream
/*    */ {
/*    */   private int field_22283_b;
/*    */   private int field_22285_c;
/*    */   final RegionFile field_22284_a;
/*    */   
/*    */   public RegionFileChunkBuffer(RegionFile regionfile, int i, int j) {
/* 15 */     super(8096);
/* 16 */     this.field_22284_a = regionfile;
/* 17 */     this.field_22283_b = i;
/* 18 */     this.field_22285_c = j;
/*    */   }
/*    */ 
/*    */   
/*    */   public void close() {
/* 23 */     this.field_22284_a.write(this.field_22283_b, this.field_22285_c, this.buf, this.count);
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\RegionFileChunkBuffer.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */