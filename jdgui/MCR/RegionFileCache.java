/*    */ package MCR;
/*    */ 
/*    */ import java.io.DataInputStream;
/*    */ import java.io.DataOutputStream;
/*    */ import java.io.File;
/*    */ import java.io.IOException;
/*    */ import java.lang.ref.Reference;
/*    */ import java.lang.ref.SoftReference;
/*    */ import java.util.HashMap;
/*    */ import java.util.Iterator;
/*    */ import java.util.Map;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class RegionFileCache
/*    */ {
/*    */   public static synchronized RegionFile func_22193_a(File file, int i, int j) {
/* 24 */     File file2 = file;
/* 25 */     Reference<RegionFile> reference = (Reference)cache.get(file2);
/* 26 */     if (reference != null) {
/*    */       
/* 28 */       RegionFile regionfile = reference.get();
/* 29 */       if (regionfile != null)
/*    */       {
/* 31 */         return regionfile;
/*    */       }
/*    */     } 
/*    */     
/* 35 */     if (cache.size() >= 256)
/*    */     {
/* 37 */       func_22192_a();
/*    */     }
/* 39 */     RegionFile regionfile1 = new RegionFile(file2);
/* 40 */     cache.put(file2, new SoftReference<>(regionfile1));
/* 41 */     return regionfile1;
/*    */   }
/*    */ 
/*    */   
/*    */   public static synchronized void func_22192_a() {
/* 46 */     Iterator<Reference> iterator = cache.values().iterator();
/*    */ 
/*    */     
/* 49 */     while (iterator.hasNext()) {
/*    */ 
/*    */ 
/*    */       
/* 53 */       Reference<RegionFile> reference = iterator.next();
/*    */       
/*    */       try {
/* 56 */         RegionFile regionfile = reference.get();
/* 57 */         if (regionfile != null)
/*    */         {
/* 59 */           regionfile.func_22196_b();
/*    */         }
/*    */       }
/* 62 */       catch (IOException ioexception) {
/*    */         
/* 64 */         ioexception.printStackTrace();
/*    */       } 
/*    */     } 
/* 67 */     cache.clear();
/*    */   }
/*    */ 
/*    */   
/*    */   public static int getSizeDelta(File file, int i, int j) {
/* 72 */     RegionFile regionfile = func_22193_a(file, i, j);
/* 73 */     return regionfile.func_22209_a();
/*    */   }
/*    */ 
/*    */   
/*    */   public static DataInputStream getChunkInputStream(File file, int i, int j) {
/* 78 */     RegionFile regionfile = func_22193_a(file, i, j);
/* 79 */     return regionfile.getChunkDataInputStream(i & 0x1F, j & 0x1F);
/*    */   }
/*    */ 
/*    */   
/*    */   public static DataOutputStream getChunkOutputStream(File file, int i, int j) {
/* 84 */     RegionFile regionfile = func_22193_a(file, i, j);
/* 85 */     return regionfile.getChunkDataOutputStream(i & 0x1F, j & 0x1F);
/*    */   }
/*    */   
/* 88 */   private static final Map cache = new HashMap<>();
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\RegionFileCache.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */