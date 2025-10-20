/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataInputStream;
/*    */ import java.io.DataOutput;
/*    */ import java.io.DataOutputStream;
/*    */ import java.io.IOException;
/*    */ import java.io.InputStream;
/*    */ import java.io.OutputStream;
/*    */ import java.util.zip.GZIPInputStream;
/*    */ import java.util.zip.GZIPOutputStream;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class CompressedStreamTools
/*    */ {
/*    */   public static NBTTagCompound func_1138_a(InputStream inputstream) throws IOException {
/* 22 */     DataInputStream datainputstream = new DataInputStream(new GZIPInputStream(inputstream));
/*    */     
/*    */     try {
/* 25 */       NBTTagCompound nbttagcompound = func_1141_a(datainputstream);
/* 26 */       return nbttagcompound;
/*    */     }
/*    */     finally {
/*    */       
/* 30 */       datainputstream.close();
/*    */     } 
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public static void writeGzippedCompoundToOutputStream(NBTTagCompound nbttagcompound, OutputStream outputstream) throws IOException {
/* 37 */     DataOutputStream dataoutputstream = new DataOutputStream(new GZIPOutputStream(outputstream));
/*    */     
/*    */     try {
/* 40 */       func_1139_a(nbttagcompound, dataoutputstream);
/*    */     }
/*    */     finally {
/*    */       
/* 44 */       dataoutputstream.close();
/*    */     } 
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public static NBTTagCompound func_1141_a(DataInput datainput) throws IOException {
/* 51 */     NBTBase nbtbase = NBTBase.readTag(datainput);
/* 52 */     if (nbtbase instanceof NBTTagCompound)
/*    */     {
/* 54 */       return (NBTTagCompound)nbtbase;
/*    */     }
/*    */     
/* 57 */     throw new IOException("Root tag must be a named compound tag");
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public static void func_1139_a(NBTTagCompound nbttagcompound, DataOutput dataoutput) throws IOException {
/* 64 */     NBTBase.writeTag(nbttagcompound, dataoutput);
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\CompressedStreamTools.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */