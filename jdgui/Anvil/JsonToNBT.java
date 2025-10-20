/*     */ package Anvil;
/*     */ 
/*     */ import java.util.ArrayList;
/*     */ import java.util.Iterator;
/*     */ import java.util.Stack;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class JsonToNBT
/*     */ {
/*     */   private static final String __OBFID = "CL_00001232";
/*     */   
/*     */   public static NBTBase func_150315_a(String p_150315_0_) throws NBTException {
/*  16 */     p_150315_0_ = p_150315_0_.trim();
/*  17 */     int i = func_150310_b(p_150315_0_);
/*     */     
/*  19 */     if (i != 1)
/*     */     {
/*  21 */       throw new NBTException("Encountered multiple top tags, only one expected");
/*     */     }
/*     */ 
/*     */     
/*  25 */     Any any = null;
/*     */     
/*  27 */     if (p_150315_0_.startsWith("{")) {
/*     */       
/*  29 */       any = func_150316_a("tag", p_150315_0_);
/*     */     }
/*     */     else {
/*     */       
/*  33 */       any = func_150316_a(func_150313_b(p_150315_0_, false), func_150311_c(p_150315_0_, false));
/*     */     } 
/*     */     
/*  36 */     return any.func_150489_a();
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   static int func_150310_b(String p_150310_0_) throws NBTException {
/*  42 */     int i = 0;
/*  43 */     boolean flag = false;
/*  44 */     Stack<Character> stack = new Stack();
/*     */     
/*  46 */     for (int j = 0; j < p_150310_0_.length(); j++) {
/*     */       
/*  48 */       char c0 = p_150310_0_.charAt(j);
/*     */       
/*  50 */       if (c0 == '"') {
/*     */         
/*  52 */         if (j > 0 && p_150310_0_.charAt(j - 1) == '\\')
/*     */         {
/*  54 */           if (!flag)
/*     */           {
/*  56 */             throw new NBTException("Illegal use of \\\": " + p_150310_0_);
/*     */           }
/*     */         }
/*     */         else
/*     */         {
/*  61 */           flag = !flag;
/*     */         }
/*     */       
/*  64 */       } else if (!flag) {
/*     */         
/*  66 */         if (c0 != '{' && c0 != '[') {
/*     */           
/*  68 */           if (c0 == '}' && (stack.isEmpty() || ((Character)stack.pop()).charValue() != '{'))
/*     */           {
/*  70 */             throw new NBTException("Unbalanced curly brackets {}: " + p_150310_0_);
/*     */           }
/*     */           
/*  73 */           if (c0 == ']' && (stack.isEmpty() || ((Character)stack.pop()).charValue() != '['))
/*     */           {
/*  75 */             throw new NBTException("Unbalanced square brackets []: " + p_150310_0_);
/*     */           }
/*     */         }
/*     */         else {
/*     */           
/*  80 */           if (stack.isEmpty())
/*     */           {
/*  82 */             i++;
/*     */           }
/*     */           
/*  85 */           stack.push(Character.valueOf(c0));
/*     */         } 
/*     */       } 
/*     */     } 
/*     */     
/*  90 */     if (flag)
/*     */     {
/*  92 */       throw new NBTException("Unbalanced quotation: " + p_150310_0_);
/*     */     }
/*  94 */     if (!stack.isEmpty())
/*     */     {
/*  96 */       throw new NBTException("Unbalanced brackets: " + p_150310_0_);
/*     */     }
/*  98 */     if (i == 0 && !p_150310_0_.isEmpty())
/*     */     {
/* 100 */       return 1;
/*     */     }
/*     */ 
/*     */     
/* 104 */     return i;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   static Any func_150316_a(String p_150316_0_, String p_150316_1_) throws NBTException {
/* 110 */     p_150316_1_ = p_150316_1_.trim();
/* 111 */     func_150310_b(p_150316_1_);
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 117 */     if (p_150316_1_.startsWith("{")) {
/*     */       
/* 119 */       if (!p_150316_1_.endsWith("}"))
/*     */       {
/* 121 */         throw new NBTException("Unable to locate ending bracket for: " + p_150316_1_);
/*     */       }
/*     */ 
/*     */       
/* 125 */       p_150316_1_ = p_150316_1_.substring(1, p_150316_1_.length() - 1);
/* 126 */       Compound compound = new Compound(p_150316_0_);
/*     */       
/* 128 */       while (p_150316_1_.length() > 0) {
/*     */         
/* 130 */         String s2 = func_150314_a(p_150316_1_, false);
/*     */         
/* 132 */         if (s2.length() > 0) {
/*     */           
/* 134 */           String s3 = func_150313_b(s2, false);
/* 135 */           String s4 = func_150311_c(s2, false);
/* 136 */           compound.field_150491_b.add(func_150316_a(s3, s4));
/*     */           
/* 138 */           if (p_150316_1_.length() < s2.length() + 1) {
/*     */             break;
/*     */           }
/*     */ 
/*     */           
/* 143 */           char c0 = p_150316_1_.charAt(s2.length());
/*     */           
/* 145 */           if (c0 != ',' && c0 != '{' && c0 != '}' && c0 != '[' && c0 != ']')
/*     */           {
/* 147 */             throw new NBTException("Unexpected token '" + c0 + "' at: " + p_150316_1_.substring(s2.length()));
/*     */           }
/*     */           
/* 150 */           p_150316_1_ = p_150316_1_.substring(s2.length() + 1);
/*     */         } 
/*     */       } 
/*     */       
/* 154 */       return compound;
/*     */     } 
/*     */     
/* 157 */     if (p_150316_1_.startsWith("[") && !p_150316_1_.matches("\\[[-\\d|,\\s]+\\]")) {
/*     */       
/* 159 */       if (!p_150316_1_.endsWith("]"))
/*     */       {
/* 161 */         throw new NBTException("Unable to locate ending bracket for: " + p_150316_1_);
/*     */       }
/*     */ 
/*     */       
/* 165 */       p_150316_1_ = p_150316_1_.substring(1, p_150316_1_.length() - 1);
/* 166 */       List list = new List(p_150316_0_);
/*     */       
/* 168 */       while (p_150316_1_.length() > 0) {
/*     */         
/* 170 */         String s2 = func_150314_a(p_150316_1_, true);
/*     */         
/* 172 */         if (s2.length() > 0) {
/*     */           
/* 174 */           String s3 = func_150313_b(s2, true);
/* 175 */           String s4 = func_150311_c(s2, true);
/* 176 */           list.field_150492_b.add(func_150316_a(s3, s4));
/*     */           
/* 178 */           if (p_150316_1_.length() < s2.length() + 1) {
/*     */             break;
/*     */           }
/*     */ 
/*     */           
/* 183 */           char c0 = p_150316_1_.charAt(s2.length());
/*     */           
/* 185 */           if (c0 != ',' && c0 != '{' && c0 != '}' && c0 != '[' && c0 != ']')
/*     */           {
/* 187 */             throw new NBTException("Unexpected token '" + c0 + "' at: " + p_150316_1_.substring(s2.length()));
/*     */           }
/*     */           
/* 190 */           p_150316_1_ = p_150316_1_.substring(s2.length() + 1);
/*     */         } 
/*     */       } 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 198 */       return list;
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/* 203 */     return new Primitive(p_150316_0_, p_150316_1_);
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private static String func_150314_a(String p_150314_0_, boolean p_150314_1_) throws NBTException {
/* 209 */     int i = func_150312_a(p_150314_0_, ':');
/*     */     
/* 211 */     if (i < 0 && !p_150314_1_)
/*     */     {
/* 213 */       throw new NBTException("Unable to locate name/value separator for string: " + p_150314_0_);
/*     */     }
/*     */ 
/*     */     
/* 217 */     int j = func_150312_a(p_150314_0_, ',');
/*     */     
/* 219 */     if (j >= 0 && j < i && !p_150314_1_)
/*     */     {
/* 221 */       throw new NBTException("Name error at: " + p_150314_0_);
/*     */     }
/*     */ 
/*     */     
/* 225 */     if (p_150314_1_ && (i < 0 || i > j))
/*     */     {
/* 227 */       i = -1;
/*     */     }
/*     */     
/* 230 */     Stack<Character> stack = new Stack();
/* 231 */     int k = i + 1;
/* 232 */     boolean flag1 = false;
/* 233 */     boolean flag2 = false;
/* 234 */     boolean flag3 = false;
/*     */     
/* 236 */     for (int l = 0; k < p_150314_0_.length(); k++) {
/*     */       
/* 238 */       char c0 = p_150314_0_.charAt(k);
/*     */       
/* 240 */       if (c0 == '"') {
/*     */         
/* 242 */         if (k > 0 && p_150314_0_.charAt(k - 1) == '\\')
/*     */         {
/* 244 */           if (!flag1)
/*     */           {
/* 246 */             throw new NBTException("Illegal use of \\\": " + p_150314_0_);
/*     */           }
/*     */         }
/*     */         else
/*     */         {
/* 251 */           flag1 = !flag1;
/*     */           
/* 253 */           if (flag1 && !flag3)
/*     */           {
/* 255 */             flag2 = true;
/*     */           }
/*     */           
/* 258 */           if (!flag1)
/*     */           {
/* 260 */             l = k;
/*     */           }
/*     */         }
/*     */       
/* 264 */       } else if (!flag1) {
/*     */         
/* 266 */         if (c0 != '{' && c0 != '[') {
/*     */           
/* 268 */           if (c0 == '}' && (stack.isEmpty() || ((Character)stack.pop()).charValue() != '{'))
/*     */           {
/* 270 */             throw new NBTException("Unbalanced curly brackets {}: " + p_150314_0_);
/*     */           }
/*     */           
/* 273 */           if (c0 == ']' && (stack.isEmpty() || ((Character)stack.pop()).charValue() != '['))
/*     */           {
/* 275 */             throw new NBTException("Unbalanced square brackets []: " + p_150314_0_);
/*     */           }
/*     */           
/* 278 */           if (c0 == ',' && stack.isEmpty())
/*     */           {
/* 280 */             return p_150314_0_.substring(0, k);
/*     */           }
/*     */         }
/*     */         else {
/*     */           
/* 285 */           stack.push(Character.valueOf(c0));
/*     */         } 
/*     */       } 
/*     */       
/* 289 */       if (!Character.isWhitespace(c0)) {
/*     */         
/* 291 */         if (!flag1 && flag2 && l != k)
/*     */         {
/* 293 */           return p_150314_0_.substring(0, l + 1);
/*     */         }
/*     */         
/* 296 */         flag3 = true;
/*     */       } 
/*     */     } 
/*     */     
/* 300 */     return p_150314_0_.substring(0, k);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static String func_150313_b(String p_150313_0_, boolean p_150313_1_) throws NBTException {
/* 307 */     if (p_150313_1_) {
/*     */       
/* 309 */       p_150313_0_ = p_150313_0_.trim();
/*     */       
/* 311 */       if (p_150313_0_.startsWith("{") || p_150313_0_.startsWith("["))
/*     */       {
/* 313 */         return "";
/*     */       }
/*     */     } 
/*     */     
/* 317 */     int i = p_150313_0_.indexOf(':');
/*     */     
/* 319 */     if (i < 0) {
/*     */       
/* 321 */       if (p_150313_1_)
/*     */       {
/* 323 */         return "";
/*     */       }
/*     */ 
/*     */       
/* 327 */       throw new NBTException("Unable to locate name/value separator for string: " + p_150313_0_);
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/* 332 */     return p_150313_0_.substring(0, i).trim();
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private static String func_150311_c(String p_150311_0_, boolean p_150311_1_) throws NBTException {
/* 338 */     if (p_150311_1_) {
/*     */       
/* 340 */       p_150311_0_ = p_150311_0_.trim();
/*     */       
/* 342 */       if (p_150311_0_.startsWith("{") || p_150311_0_.startsWith("["))
/*     */       {
/* 344 */         return p_150311_0_;
/*     */       }
/*     */     } 
/*     */     
/* 348 */     int i = p_150311_0_.indexOf(':');
/*     */     
/* 350 */     if (i < 0) {
/*     */       
/* 352 */       if (p_150311_1_)
/*     */       {
/* 354 */         return p_150311_0_;
/*     */       }
/*     */ 
/*     */       
/* 358 */       throw new NBTException("Unable to locate name/value separator for string: " + p_150311_0_);
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/* 363 */     return p_150311_0_.substring(i + 1).trim();
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private static int func_150312_a(String p_150312_0_, char p_150312_1_) {
/* 369 */     int i = 0;
/*     */     
/* 371 */     for (boolean flag = false; i < p_150312_0_.length(); i++) {
/*     */       
/* 373 */       char c1 = p_150312_0_.charAt(i);
/*     */       
/* 375 */       if (c1 == '"') {
/*     */         
/* 377 */         if (i <= 0 || p_150312_0_.charAt(i - 1) != '\\')
/*     */         {
/* 379 */           flag = !flag;
/*     */         }
/*     */       }
/* 382 */       else if (!flag) {
/*     */         
/* 384 */         if (c1 == p_150312_1_)
/*     */         {
/* 386 */           return i;
/*     */         }
/*     */         
/* 389 */         if (c1 == '{' || c1 == '[')
/*     */         {
/* 391 */           return -1;
/*     */         }
/*     */       } 
/*     */     } 
/*     */     
/* 396 */     return -1;
/*     */   }
/*     */   
/*     */   static abstract class Any
/*     */   {
/*     */     protected String field_150490_a;
/*     */     private static final String __OBFID = "CL_00001233";
/*     */     
/*     */     public abstract NBTBase func_150489_a();
/*     */   }
/*     */   
/*     */   static class Compound
/*     */     extends Any {
/* 409 */     protected ArrayList field_150491_b = new ArrayList();
/*     */     
/*     */     private static final String __OBFID = "CL_00001234";
/*     */     
/*     */     public Compound(String p_i45137_1_) {
/* 414 */       this.field_150490_a = p_i45137_1_;
/*     */     }
/*     */ 
/*     */     
/*     */     public NBTBase func_150489_a() {
/* 419 */       NBTTagCompound nbttagcompound = new NBTTagCompound();
/* 420 */       Iterator<JsonToNBT.Any> iterator = this.field_150491_b.iterator();
/*     */       
/* 422 */       while (iterator.hasNext()) {
/*     */         
/* 424 */         JsonToNBT.Any any = iterator.next();
/* 425 */         nbttagcompound.setTag(any.field_150490_a, any.func_150489_a());
/*     */       } 
/*     */       
/* 428 */       return nbttagcompound;
/*     */     }
/*     */   }
/*     */   
/*     */   static class List
/*     */     extends Any {
/* 434 */     protected ArrayList field_150492_b = new ArrayList();
/*     */     
/*     */     private static final String __OBFID = "CL_00001235";
/*     */     
/*     */     public List(String p_i45138_1_) {
/* 439 */       this.field_150490_a = p_i45138_1_;
/*     */     }
/*     */ 
/*     */     
/*     */     public NBTBase func_150489_a() {
/* 444 */       NBTTagList nbttaglist = new NBTTagList();
/* 445 */       Iterator<JsonToNBT.Any> iterator = this.field_150492_b.iterator();
/*     */       
/* 447 */       while (iterator.hasNext()) {
/*     */         
/* 449 */         JsonToNBT.Any any = iterator.next();
/* 450 */         nbttaglist.appendTag(any.func_150489_a());
/*     */       } 
/*     */       
/* 453 */       return nbttaglist;
/*     */     }
/*     */   }
/*     */   
/*     */   static class Primitive
/*     */     extends Any
/*     */   {
/*     */     protected String field_150493_b;
/*     */     private static final String __OBFID = "CL_00001236";
/*     */     
/*     */     public Primitive(String p_i45139_1_, String p_i45139_2_) {
/* 464 */       this.field_150490_a = p_i45139_1_;
/* 465 */       this.field_150493_b = p_i45139_2_;
/*     */     }
/*     */ 
/*     */ 
/*     */     
/*     */     public NBTBase func_150489_a() {
/*     */       try {
/* 472 */         if (this.field_150493_b.matches("[-+]?[0-9]*\\.?[0-9]+[d|D]"))
/*     */         {
/* 474 */           return new NBTTagDouble(Double.parseDouble(this.field_150493_b.substring(0, this.field_150493_b.length() - 1)));
/*     */         }
/* 476 */         if (this.field_150493_b.matches("[-+]?[0-9]*\\.?[0-9]+[f|F]"))
/*     */         {
/* 478 */           return new NBTTagFloat(Float.parseFloat(this.field_150493_b.substring(0, this.field_150493_b.length() - 1)));
/*     */         }
/* 480 */         if (this.field_150493_b.matches("[-+]?[0-9]+[b|B]"))
/*     */         {
/* 482 */           return new NBTTagByte(Byte.parseByte(this.field_150493_b.substring(0, this.field_150493_b.length() - 1)));
/*     */         }
/* 484 */         if (this.field_150493_b.matches("[-+]?[0-9]+[l|L]"))
/*     */         {
/* 486 */           return new NBTTagLong(Long.parseLong(this.field_150493_b.substring(0, this.field_150493_b.length() - 1)));
/*     */         }
/* 488 */         if (this.field_150493_b.matches("[-+]?[0-9]+[s|S]"))
/*     */         {
/* 490 */           return new NBTTagShort(Short.parseShort(this.field_150493_b.substring(0, this.field_150493_b.length() - 1)));
/*     */         }
/* 492 */         if (this.field_150493_b.matches("[-+]?[0-9]+"))
/*     */         {
/* 494 */           return new NBTTagInt(Integer.parseInt(this.field_150493_b.substring(0, this.field_150493_b.length())));
/*     */         }
/* 496 */         if (this.field_150493_b.matches("[-+]?[0-9]*\\.?[0-9]+"))
/*     */         {
/* 498 */           return new NBTTagDouble(Double.parseDouble(this.field_150493_b.substring(0, this.field_150493_b.length())));
/*     */         }
/* 500 */         if (!this.field_150493_b.equalsIgnoreCase("true") && !this.field_150493_b.equalsIgnoreCase("false")) {
/*     */           
/* 502 */           if (this.field_150493_b.startsWith("[") && this.field_150493_b.endsWith("]")) {
/*     */             
/* 504 */             if (this.field_150493_b.length() > 2) {
/*     */               
/* 506 */               String s = this.field_150493_b.substring(1, this.field_150493_b.length() - 1);
/* 507 */               String[] astring = s.split(",");
/*     */ 
/*     */               
/*     */               try {
/* 511 */                 if (astring.length <= 1)
/*     */                 {
/* 513 */                   return new NBTTagIntArray(new int[] { Integer.parseInt(s.trim()) });
/*     */                 }
/*     */ 
/*     */                 
/* 517 */                 int[] aint = new int[astring.length];
/*     */                 
/* 519 */                 for (int i = 0; i < astring.length; i++)
/*     */                 {
/* 521 */                   aint[i] = Integer.parseInt(astring[i].trim());
/*     */                 }
/*     */                 
/* 524 */                 return new NBTTagIntArray(aint);
/*     */               
/*     */               }
/* 527 */               catch (NumberFormatException numberformatexception) {
/*     */                 
/* 529 */                 return new NBTTagString(this.field_150493_b);
/*     */               } 
/*     */             } 
/*     */ 
/*     */             
/* 534 */             return new NBTTagIntArray();
/*     */           } 
/*     */ 
/*     */ 
/*     */           
/* 539 */           if (this.field_150493_b.startsWith("\"") && this.field_150493_b.endsWith("\"") && this.field_150493_b.length() > 2)
/*     */           {
/* 541 */             this.field_150493_b = this.field_150493_b.substring(1, this.field_150493_b.length() - 1);
/*     */           }
/*     */           
/* 544 */           this.field_150493_b = this.field_150493_b.replaceAll("\\\\\"", "\"");
/* 545 */           return new NBTTagString(this.field_150493_b);
/*     */         } 
/*     */ 
/*     */ 
/*     */         
/* 550 */         return new NBTTagByte((byte)(Boolean.parseBoolean(this.field_150493_b) ? 1 : 0));
/*     */       
/*     */       }
/* 553 */       catch (NumberFormatException numberformatexception1) {
/*     */         
/* 555 */         this.field_150493_b = this.field_150493_b.replaceAll("\\\\\"", "\"");
/* 556 */         return new NBTTagString(this.field_150493_b);
/*     */       } 
/*     */     }
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\JsonToNBT.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */