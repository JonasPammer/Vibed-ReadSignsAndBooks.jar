/*     */ package org.json;
/*     */ 
/*     */ import java.util.HashMap;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class XMLTokener
/*     */   extends JSONTokener
/*     */ {
/*  42 */   public static final HashMap<String, Character> entity = new HashMap<String, Character>(8); static {
/*  43 */     entity.put("amp", XML.AMP);
/*  44 */     entity.put("apos", XML.APOS);
/*  45 */     entity.put("gt", XML.GT);
/*  46 */     entity.put("lt", XML.LT);
/*  47 */     entity.put("quot", XML.QUOT);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public XMLTokener(String s) {
/*  55 */     super(s);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String nextCDATA() throws JSONException {
/*  66 */     StringBuilder sb = new StringBuilder();
/*  67 */     while (more()) {
/*  68 */       char c = next();
/*  69 */       sb.append(c);
/*  70 */       int i = sb.length() - 3;
/*  71 */       if (i >= 0 && sb.charAt(i) == ']' && sb
/*  72 */         .charAt(i + 1) == ']' && sb.charAt(i + 2) == '>') {
/*  73 */         sb.setLength(i);
/*  74 */         return sb.toString();
/*     */       } 
/*     */     } 
/*  77 */     throw syntaxError("Unclosed CDATA");
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public Object nextContent() throws JSONException {
/*     */     while (true) {
/*  94 */       char c = next();
/*  95 */       if (!Character.isWhitespace(c)) {
/*  96 */         if (c == '\000') {
/*  97 */           return null;
/*     */         }
/*  99 */         if (c == '<') {
/* 100 */           return XML.LT;
/*     */         }
/* 102 */         StringBuilder sb = new StringBuilder();
/*     */         while (true) {
/* 104 */           if (c == '\000') {
/* 105 */             return sb.toString().trim();
/*     */           }
/* 107 */           if (c == '<') {
/* 108 */             back();
/* 109 */             return sb.toString().trim();
/*     */           } 
/* 111 */           if (c == '&') {
/* 112 */             sb.append(nextEntity(c));
/*     */           } else {
/* 114 */             sb.append(c);
/*     */           } 
/* 116 */           c = next();
/*     */         } 
/*     */         break;
/*     */       } 
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public Object nextEntity(char ampersand) throws JSONException {
/*     */     char c;
/* 129 */     StringBuilder sb = new StringBuilder();
/*     */     while (true) {
/* 131 */       c = next();
/* 132 */       if (Character.isLetterOrDigit(c) || c == '#')
/* 133 */       { sb.append(Character.toLowerCase(c)); continue; }  break;
/* 134 */     }  if (c == ';') {
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 140 */       String string = sb.toString();
/* 141 */       return unescapeEntity(string);
/*     */     } 
/*     */     throw syntaxError("Missing ';' in XML entity: &" + sb);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   static String unescapeEntity(String e) {
/* 151 */     if (e == null || e.isEmpty()) {
/* 152 */       return "";
/*     */     }
/*     */     
/* 155 */     if (e.charAt(0) == '#') {
/*     */       int cp;
/* 157 */       if (e.charAt(1) == 'x') {
/*     */         
/* 159 */         cp = Integer.parseInt(e.substring(2), 16);
/*     */       } else {
/*     */         
/* 162 */         cp = Integer.parseInt(e.substring(1));
/*     */       } 
/* 164 */       return new String(new int[] { cp }, 0, 1);
/*     */     } 
/* 166 */     Character knownEntity = entity.get(e);
/* 167 */     if (knownEntity == null)
/*     */     {
/* 169 */       return '&' + e + ';';
/*     */     }
/* 171 */     return knownEntity.toString();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public Object nextMeta() throws JSONException {
/*     */     char c, q;
/*     */     do {
/* 188 */       c = next();
/* 189 */     } while (Character.isWhitespace(c));
/* 190 */     switch (c) {
/*     */       case '\000':
/* 192 */         throw syntaxError("Misshaped meta tag");
/*     */       case '<':
/* 194 */         return XML.LT;
/*     */       case '>':
/* 196 */         return XML.GT;
/*     */       case '/':
/* 198 */         return XML.SLASH;
/*     */       case '=':
/* 200 */         return XML.EQ;
/*     */       case '!':
/* 202 */         return XML.BANG;
/*     */       case '?':
/* 204 */         return XML.QUEST;
/*     */       case '"':
/*     */       case '\'':
/* 207 */         q = c;
/*     */         while (true) {
/* 209 */           c = next();
/* 210 */           if (c == '\000') {
/* 211 */             throw syntaxError("Unterminated string");
/*     */           }
/* 213 */           if (c == q) {
/* 214 */             return Boolean.TRUE;
/*     */           }
/*     */         } 
/*     */     } 
/*     */     while (true) {
/* 219 */       c = next();
/* 220 */       if (Character.isWhitespace(c)) {
/* 221 */         return Boolean.TRUE;
/*     */       }
/* 223 */       switch (c) { case '\000':
/*     */         case '!':
/*     */         case '"':
/*     */         case '\'':
/*     */         case '/':
/*     */         case '<':
/*     */         case '=':
/*     */         case '>':
/*     */         case '?':
/*     */           break; } 
/* 233 */     }  back();
/* 234 */     return Boolean.TRUE;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public Object nextToken() throws JSONException {
/*     */     char c, q;
/*     */     do {
/* 254 */       c = next();
/* 255 */     } while (Character.isWhitespace(c));
/* 256 */     switch (c) {
/*     */       case '\000':
/* 258 */         throw syntaxError("Misshaped element");
/*     */       case '<':
/* 260 */         throw syntaxError("Misplaced '<'");
/*     */       case '>':
/* 262 */         return XML.GT;
/*     */       case '/':
/* 264 */         return XML.SLASH;
/*     */       case '=':
/* 266 */         return XML.EQ;
/*     */       case '!':
/* 268 */         return XML.BANG;
/*     */       case '?':
/* 270 */         return XML.QUEST;
/*     */ 
/*     */ 
/*     */       
/*     */       case '"':
/*     */       case '\'':
/* 276 */         q = c;
/* 277 */         sb = new StringBuilder();
/*     */         while (true) {
/* 279 */           c = next();
/* 280 */           if (c == '\000') {
/* 281 */             throw syntaxError("Unterminated string");
/*     */           }
/* 283 */           if (c == q) {
/* 284 */             return sb.toString();
/*     */           }
/* 286 */           if (c == '&') {
/* 287 */             sb.append(nextEntity(c)); continue;
/*     */           } 
/* 289 */           sb.append(c);
/*     */         } 
/*     */     } 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 296 */     StringBuilder sb = new StringBuilder();
/*     */     while (true)
/* 298 */     { sb.append(c);
/* 299 */       c = next();
/* 300 */       if (Character.isWhitespace(c)) {
/* 301 */         return sb.toString();
/*     */       }
/* 303 */       switch (c)
/*     */       { case '\000':
/* 305 */           return sb.toString();
/*     */         case '!':
/*     */         case '/':
/*     */         case '=':
/*     */         case '>':
/*     */         case '?':
/*     */         case '[':
/*     */         case ']':
/* 313 */           back();
/* 314 */           return sb.toString();
/*     */         case '"':
/*     */         case '\'':
/*     */         case '<':
/* 318 */           break; }  }  throw syntaxError("Bad character in a name");
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void skipPast(String to) {
/* 338 */     int offset = 0;
/* 339 */     int length = to.length();
/* 340 */     char[] circle = new char[length];
/*     */ 
/*     */ 
/*     */     
/*     */     int i;
/*     */ 
/*     */     
/* 347 */     for (i = 0; i < length; i++) {
/* 348 */       char c = next();
/* 349 */       if (c == '\000') {
/*     */         return;
/*     */       }
/* 352 */       circle[i] = c;
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/*     */     while (true) {
/* 358 */       int j = offset;
/* 359 */       boolean b = true;
/*     */ 
/*     */ 
/*     */       
/* 363 */       for (i = 0; i < length; i++) {
/* 364 */         if (circle[j] != to.charAt(i)) {
/* 365 */           b = false;
/*     */           break;
/*     */         } 
/* 368 */         j++;
/* 369 */         if (j >= length) {
/* 370 */           j -= length;
/*     */         }
/*     */       } 
/*     */ 
/*     */ 
/*     */       
/* 376 */       if (b) {
/*     */         return;
/*     */       }
/*     */ 
/*     */ 
/*     */       
/* 382 */       char c = next();
/* 383 */       if (c == '\000') {
/*     */         return;
/*     */       }
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 390 */       circle[offset] = c;
/* 391 */       offset++;
/* 392 */       if (offset >= length)
/* 393 */         offset -= length; 
/*     */     } 
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\org\json\XMLTokener.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */