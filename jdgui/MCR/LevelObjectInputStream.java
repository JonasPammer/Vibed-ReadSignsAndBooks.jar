/*    */ package MCR;
/*    */ 
/*    */ import java.io.IOException;
/*    */ import java.io.InputStream;
/*    */ import java.io.ObjectInputStream;
/*    */ import java.io.ObjectStreamClass;
/*    */ import java.util.HashSet;
/*    */ import java.util.Set;
/*    */ 
/*    */ public final class LevelObjectInputStream
/*    */   extends ObjectInputStream
/*    */ {
/* 13 */   private Set classes = new HashSet();
/*    */ 
/*    */   
/*    */   public LevelObjectInputStream(InputStream var1) throws IOException {
/* 17 */     super(var1);
/* 18 */     this.classes.add("com.mojang.minecraft.player.Player$1");
/* 19 */     this.classes.add("com.mojang.minecraft.mob.Creeper$1");
/* 20 */     this.classes.add("com.mojang.minecraft.mob.Skeleton$1");
/*    */   }
/*    */ 
/*    */   
/*    */   protected final ObjectStreamClass readClassDescriptor() {
/*    */     try {
/* 26 */       ObjectStreamClass var1 = super.readClassDescriptor();
/* 27 */       return this.classes.contains(var1.getName()) ? ObjectStreamClass.lookup(Class.forName(var1.getName())) : var1;
/* 28 */     } catch (ClassNotFoundException e) {
/* 29 */       e.printStackTrace();
/* 30 */     } catch (IOException e) {
/* 31 */       e.printStackTrace();
/*    */     } 
/*    */     
/* 34 */     return null;
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\MCR\LevelObjectInputStream.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */