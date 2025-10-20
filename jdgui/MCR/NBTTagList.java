/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ import java.util.ArrayList;
/*    */ import java.util.List;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class NBTTagList
/*    */   extends NBTBase
/*    */ {
/* 17 */   private List tagList = new ArrayList();
/*    */   
/*    */   private byte tagType;
/*    */ 
/*    */   
/*    */   void writeTagContents(DataOutput dataoutput) throws IOException {
/* 23 */     if (this.tagList.size() > 0) {
/*    */       
/* 25 */       this.tagType = ((NBTBase)this.tagList.get(0)).getType();
/*    */     } else {
/*    */       
/* 28 */       this.tagType = 1;
/*    */     } 
/* 30 */     dataoutput.writeByte(this.tagType);
/* 31 */     dataoutput.writeInt(this.tagList.size());
/* 32 */     for (int i = 0; i < this.tagList.size(); i++)
/*    */     {
/* 34 */       ((NBTBase)this.tagList.get(i)).writeTagContents(dataoutput);
/*    */     }
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   void readTagContents(DataInput datainput) throws IOException {
/* 42 */     this.tagType = datainput.readByte();
/* 43 */     int i = datainput.readInt();
/* 44 */     this.tagList = new ArrayList();
/* 45 */     for (int j = 0; j < i; j++) {
/*    */       
/* 47 */       NBTBase nbtbase = NBTBase.createTagOfType(this.tagType);
/* 48 */       nbtbase.readTagContents(datainput);
/* 49 */       this.tagList.add(nbtbase);
/*    */     } 
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public byte getType() {
/* 56 */     return 9;
/*    */   }
/*    */ 
/*    */   
/*    */   public String toString() {
/* 61 */     return "" + this.tagList.size() + " entries of type " + NBTBase.getTagName(this.tagType);
/*    */   }
/*    */ 
/*    */   
/*    */   public void setTag(NBTBase nbtbase) {
/* 66 */     this.tagType = nbtbase.getType();
/* 67 */     this.tagList.add(nbtbase);
/*    */   }
/*    */ 
/*    */   
/*    */   public NBTBase tagAt(int i) {
/* 72 */     return this.tagList.get(i);
/*    */   }
/*    */ 
/*    */   
/*    */   public int tagCount() {
/* 77 */     return this.tagList.size();
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\NBTTagList.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */