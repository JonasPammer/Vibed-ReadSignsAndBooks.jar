/*    */ package Anvil;
/*    */ 
/*    */ import java.io.DataInput;
/*    */ import java.io.DataOutput;
/*    */ import java.io.IOException;
/*    */ 
/*    */ public abstract class NBTBase
/*    */ {
/*  9 */   public static final String[] NBTTypes = new String[] { "END", "BYTE", "SHORT", "INT", "LONG", "FLOAT", "DOUBLE", "BYTE[]", "STRING", "LIST", "COMPOUND", "INT[]" };
/*    */ 
/*    */   
/*    */   private static final String __OBFID = "CL_00001229";
/*    */ 
/*    */   
/*    */   abstract void write(DataOutput paramDataOutput) throws IOException;
/*    */ 
/*    */   
/*    */   abstract void func_152446_a(DataInput paramDataInput, int paramInt, NBTSizeTracker paramNBTSizeTracker) throws IOException;
/*    */ 
/*    */   
/*    */   public abstract String toString();
/*    */ 
/*    */   
/*    */   public abstract byte getId();
/*    */ 
/*    */   
/*    */   protected static NBTBase func_150284_a(byte p_150284_0_) {
/* 28 */     switch (p_150284_0_) {
/*    */       
/*    */       case 0:
/* 31 */         return new NBTTagEnd();
/*    */       case 1:
/* 33 */         return new NBTTagByte();
/*    */       case 2:
/* 35 */         return new NBTTagShort();
/*    */       case 3:
/* 37 */         return new NBTTagInt();
/*    */       case 4:
/* 39 */         return new NBTTagLong();
/*    */       case 5:
/* 41 */         return new NBTTagFloat();
/*    */       case 6:
/* 43 */         return new NBTTagDouble();
/*    */       case 7:
/* 45 */         return new NBTTagByteArray();
/*    */       case 8:
/* 47 */         return new NBTTagString();
/*    */       case 9:
/* 49 */         return new NBTTagList();
/*    */       case 10:
/* 51 */         return new NBTTagCompound();
/*    */       case 11:
/* 53 */         return new NBTTagIntArray();
/*    */     } 
/* 55 */     return null;
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public abstract NBTBase copy();
/*    */ 
/*    */ 
/*    */   
/*    */   public boolean equals(Object p_equals_1_) {
/* 66 */     if (!(p_equals_1_ instanceof NBTBase))
/*    */     {
/* 68 */       return false;
/*    */     }
/*    */ 
/*    */     
/* 72 */     NBTBase nbtbase = (NBTBase)p_equals_1_;
/* 73 */     return (getId() == nbtbase.getId());
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public int hashCode() {
/* 79 */     return getId();
/*    */   }
/*    */ 
/*    */   
/*    */   protected String func_150285_a_() {
/* 84 */     return toString();
/*    */   }
/*    */   
/*    */   public static abstract class NBTPrimitive extends NBTBase {
/*    */     private static final String __OBFID = "CL_00001230";
/*    */     
/*    */     public abstract long func_150291_c();
/*    */     
/*    */     public abstract int func_150287_d();
/*    */     
/*    */     public abstract short func_150289_e();
/*    */     
/*    */     public abstract byte func_150290_f();
/*    */     
/*    */     public abstract double func_150286_g();
/*    */     
/*    */     public abstract float func_150288_h();
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\NBTBase.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */