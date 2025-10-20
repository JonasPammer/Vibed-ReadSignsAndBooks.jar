/*     */ package org.json;
/*     */ 
/*     */ import java.util.Iterator;
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
/*     */ public class XML
/*     */ {
/*  39 */   public static final Character AMP = Character.valueOf('&');
/*     */ 
/*     */   
/*  42 */   public static final Character APOS = Character.valueOf('\'');
/*     */ 
/*     */   
/*  45 */   public static final Character BANG = Character.valueOf('!');
/*     */ 
/*     */   
/*  48 */   public static final Character EQ = Character.valueOf('=');
/*     */ 
/*     */   
/*  51 */   public static final Character GT = Character.valueOf('>');
/*     */ 
/*     */   
/*  54 */   public static final Character LT = Character.valueOf('<');
/*     */ 
/*     */   
/*  57 */   public static final Character QUEST = Character.valueOf('?');
/*     */ 
/*     */   
/*  60 */   public static final Character QUOT = Character.valueOf('"');
/*     */ 
/*     */   
/*  63 */   public static final Character SLASH = Character.valueOf('/');
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
/*     */   private static Iterable<Integer> codePointIterator(final String string) {
/*  77 */     return new Iterable<Integer>()
/*     */       {
/*     */         public Iterator<Integer> iterator() {
/*  80 */           return new Iterator<Integer>() {
/*  81 */               private int nextIndex = 0;
/*  82 */               private int length = string.length();
/*     */ 
/*     */               
/*     */               public boolean hasNext() {
/*  86 */                 return (this.nextIndex < this.length);
/*     */               }
/*     */ 
/*     */               
/*     */               public Integer next() {
/*  91 */                 int result = string.codePointAt(this.nextIndex);
/*  92 */                 this.nextIndex += Character.charCount(result);
/*  93 */                 return Integer.valueOf(result);
/*     */               }
/*     */ 
/*     */               
/*     */               public void remove() {
/*  98 */                 throw new UnsupportedOperationException();
/*     */               }
/*     */             };
/*     */         }
/*     */       };
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
/*     */   public static String escape(String string) {
/* 121 */     StringBuilder sb = new StringBuilder(string.length());
/* 122 */     for (Iterator<Integer> iterator = codePointIterator(string).iterator(); iterator.hasNext(); ) { int cp = ((Integer)iterator.next()).intValue();
/* 123 */       switch (cp) {
/*     */         case 38:
/* 125 */           sb.append("&amp;");
/*     */           continue;
/*     */         case 60:
/* 128 */           sb.append("&lt;");
/*     */           continue;
/*     */         case 62:
/* 131 */           sb.append("&gt;");
/*     */           continue;
/*     */         case 34:
/* 134 */           sb.append("&quot;");
/*     */           continue;
/*     */         case 39:
/* 137 */           sb.append("&apos;");
/*     */           continue;
/*     */       } 
/* 140 */       if (mustEscape(cp)) {
/* 141 */         sb.append("&#x");
/* 142 */         sb.append(Integer.toHexString(cp));
/* 143 */         sb.append(';'); continue;
/*     */       } 
/* 145 */       sb.appendCodePoint(cp); }
/*     */ 
/*     */ 
/*     */     
/* 149 */     return sb.toString();
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
/*     */   private static boolean mustEscape(int cp) {
/* 165 */     return ((Character.isISOControl(cp) && cp != 9 && cp != 10 && cp != 13) || ((cp < 32 || cp > 55295) && (cp < 57344 || cp > 65533) && (cp < 65536 || cp > 1114111)));
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
/*     */   
/*     */   public static String unescape(String string) {
/* 186 */     StringBuilder sb = new StringBuilder(string.length());
/* 187 */     for (int i = 0, length = string.length(); i < length; i++) {
/* 188 */       char c = string.charAt(i);
/* 189 */       if (c == '&') {
/* 190 */         int semic = string.indexOf(';', i);
/* 191 */         if (semic > i) {
/* 192 */           String entity = string.substring(i + 1, semic);
/* 193 */           sb.append(XMLTokener.unescapeEntity(entity));
/*     */           
/* 195 */           i += entity.length() + 1;
/*     */         }
/*     */         else {
/*     */           
/* 199 */           sb.append(c);
/*     */         } 
/*     */       } else {
/*     */         
/* 203 */         sb.append(c);
/*     */       } 
/*     */     } 
/* 206 */     return sb.toString();
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
/*     */   public static void noSpace(String string) throws JSONException {
/* 218 */     int length = string.length();
/* 219 */     if (length == 0) {
/* 220 */       throw new JSONException("Empty string.");
/*     */     }
/* 222 */     for (int i = 0; i < length; i++) {
/* 223 */       if (Character.isWhitespace(string.charAt(i))) {
/* 224 */         throw new JSONException("'" + string + "' contains a space character.");
/*     */       }
/*     */     } 
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
/*     */   private static boolean parse(XMLTokener x, JSONObject context, String name, boolean keepStrings) throws JSONException {
/* 246 */     JSONObject jsonobject = null;
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
/* 261 */     Object token = x.nextToken();
/*     */ 
/*     */ 
/*     */     
/* 265 */     if (token == BANG) {
/* 266 */       char c = x.next();
/* 267 */       if (c == '-') {
/* 268 */         if (x.next() == '-') {
/* 269 */           x.skipPast("-->");
/* 270 */           return false;
/*     */         } 
/* 272 */         x.back();
/* 273 */       } else if (c == '[') {
/* 274 */         token = x.nextToken();
/* 275 */         if ("CDATA".equals(token) && 
/* 276 */           x.next() == '[') {
/* 277 */           String string = x.nextCDATA();
/* 278 */           if (string.length() > 0) {
/* 279 */             context.accumulate("content", string);
/*     */           }
/* 281 */           return false;
/*     */         } 
/*     */         
/* 284 */         throw x.syntaxError("Expected 'CDATA['");
/*     */       } 
/* 286 */       int i = 1;
/*     */       while (true)
/* 288 */       { token = x.nextMeta();
/* 289 */         if (token == null)
/* 290 */           throw x.syntaxError("Missing '>' after '<!'."); 
/* 291 */         if (token == LT) {
/* 292 */           i++;
/* 293 */         } else if (token == GT) {
/* 294 */           i--;
/*     */         } 
/* 296 */         if (i <= 0)
/* 297 */           return false;  } 
/* 298 */     }  if (token == QUEST) {
/*     */ 
/*     */       
/* 301 */       x.skipPast("?>");
/* 302 */       return false;
/* 303 */     }  if (token == SLASH) {
/*     */ 
/*     */ 
/*     */       
/* 307 */       token = x.nextToken();
/* 308 */       if (name == null) {
/* 309 */         throw x.syntaxError("Mismatched close tag " + token);
/*     */       }
/* 311 */       if (!token.equals(name)) {
/* 312 */         throw x.syntaxError("Mismatched " + name + " and " + token);
/*     */       }
/* 314 */       if (x.nextToken() != GT) {
/* 315 */         throw x.syntaxError("Misshaped close tag");
/*     */       }
/* 317 */       return true;
/*     */     } 
/* 319 */     if (token instanceof Character) {
/* 320 */       throw x.syntaxError("Misshaped tag");
/*     */     }
/*     */ 
/*     */ 
/*     */     
/* 325 */     String tagName = (String)token;
/* 326 */     token = null;
/* 327 */     jsonobject = new JSONObject();
/*     */     while (true) {
/* 329 */       if (token == null) {
/* 330 */         token = x.nextToken();
/*     */       }
/*     */       
/* 333 */       if (token instanceof String) {
/* 334 */         String string = (String)token;
/* 335 */         token = x.nextToken();
/* 336 */         if (token == EQ) {
/* 337 */           token = x.nextToken();
/* 338 */           if (!(token instanceof String)) {
/* 339 */             throw x.syntaxError("Missing value");
/*     */           }
/* 341 */           jsonobject.accumulate(string, 
/* 342 */               keepStrings ? token : stringToValue((String)token));
/* 343 */           token = null; continue;
/*     */         } 
/* 345 */         jsonobject.accumulate(string, ""); continue;
/*     */       } 
/*     */       break;
/*     */     } 
/* 349 */     if (token == SLASH) {
/*     */       
/* 351 */       if (x.nextToken() != GT) {
/* 352 */         throw x.syntaxError("Misshaped tag");
/*     */       }
/* 354 */       if (jsonobject.length() > 0) {
/* 355 */         context.accumulate(tagName, jsonobject);
/*     */       } else {
/* 357 */         context.accumulate(tagName, "");
/*     */       } 
/* 359 */       return false;
/*     */     } 
/* 361 */     if (token == GT) {
/*     */       while (true) {
/*     */         
/* 364 */         token = x.nextContent();
/* 365 */         if (token == null) {
/* 366 */           if (tagName != null) {
/* 367 */             throw x.syntaxError("Unclosed tag " + tagName);
/*     */           }
/* 369 */           return false;
/* 370 */         }  if (token instanceof String) {
/* 371 */           String string = (String)token;
/* 372 */           if (string.length() > 0)
/* 373 */             jsonobject.accumulate("content", 
/* 374 */                 keepStrings ? string : stringToValue(string)); 
/*     */           continue;
/*     */         } 
/* 377 */         if (token == LT)
/*     */         {
/* 379 */           if (parse(x, jsonobject, tagName, keepStrings)) {
/* 380 */             if (jsonobject.length() == 0) {
/* 381 */               context.accumulate(tagName, "");
/* 382 */             } else if (jsonobject.length() == 1 && jsonobject
/* 383 */               .opt("content") != null) {
/* 384 */               context.accumulate(tagName, jsonobject
/* 385 */                   .opt("content"));
/*     */             } else {
/* 387 */               context.accumulate(tagName, jsonobject);
/*     */             } 
/* 389 */             return false;
/*     */           } 
/*     */         }
/*     */       } 
/*     */     }
/* 394 */     throw x.syntaxError("Misshaped tag");
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
/*     */   public static Object stringToValue(String string) {
/* 409 */     if (string.equals("")) {
/* 410 */       return string;
/*     */     }
/* 412 */     if (string.equalsIgnoreCase("true")) {
/* 413 */       return Boolean.TRUE;
/*     */     }
/* 415 */     if (string.equalsIgnoreCase("false")) {
/* 416 */       return Boolean.FALSE;
/*     */     }
/* 418 */     if (string.equalsIgnoreCase("null")) {
/* 419 */       return JSONObject.NULL;
/*     */     }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 427 */     char initial = string.charAt(0);
/* 428 */     if ((initial >= '0' && initial <= '9') || initial == '-') {
/*     */       
/*     */       try {
/*     */         
/* 432 */         if (string.indexOf('.') > -1 || string.indexOf('e') > -1 || string
/* 433 */           .indexOf('E') > -1 || "-0".equals(string)) {
/* 434 */           Double d = Double.valueOf(string);
/* 435 */           if (!d.isInfinite() && !d.isNaN()) {
/* 436 */             return d;
/*     */           }
/*     */         } else {
/* 439 */           Long myLong = Long.valueOf(string);
/* 440 */           if (string.equals(myLong.toString())) {
/* 441 */             if (myLong.longValue() == myLong.intValue()) {
/* 442 */               return Integer.valueOf(myLong.intValue());
/*     */             }
/* 444 */             return myLong;
/*     */           } 
/*     */         } 
/* 447 */       } catch (Exception exception) {}
/*     */     }
/*     */     
/* 450 */     return string;
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
/*     */   public static JSONObject toJSONObject(String string) throws JSONException {
/* 470 */     return toJSONObject(string, false);
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
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
/* 496 */     JSONObject jo = new JSONObject();
/* 497 */     XMLTokener x = new XMLTokener(string);
/* 498 */     while (x.more()) {
/* 499 */       x.skipPast("<");
/* 500 */       if (x.more()) {
/* 501 */         parse(x, jo, null, keepStrings);
/*     */       }
/*     */     } 
/* 504 */     return jo;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static String toString(Object object) throws JSONException {
/* 515 */     return toString(object, null);
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
/*     */   public static String toString(Object object, String tagName) throws JSONException {
/* 530 */     StringBuilder sb = new StringBuilder();
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 535 */     if (object instanceof JSONObject) {
/*     */ 
/*     */       
/* 538 */       if (tagName != null) {
/* 539 */         sb.append('<');
/* 540 */         sb.append(tagName);
/* 541 */         sb.append('>');
/*     */       } 
/*     */ 
/*     */ 
/*     */       
/* 546 */       JSONObject jo = (JSONObject)object;
/* 547 */       for (String key : jo.keySet()) {
/* 548 */         Object value = jo.opt(key);
/* 549 */         if (value == null) {
/* 550 */           value = "";
/* 551 */         } else if (value.getClass().isArray()) {
/* 552 */           value = new JSONArray(value);
/*     */         } 
/*     */ 
/*     */         
/* 556 */         if ("content".equals(key)) {
/* 557 */           if (value instanceof JSONArray) {
/* 558 */             JSONArray ja = (JSONArray)value;
/* 559 */             int jaLength = ja.length();
/*     */             
/* 561 */             for (int i = 0; i < jaLength; i++) {
/* 562 */               if (i > 0) {
/* 563 */                 sb.append('\n');
/*     */               }
/* 565 */               Object val = ja.opt(i);
/* 566 */               sb.append(escape(val.toString()));
/*     */             }  continue;
/*     */           } 
/* 569 */           sb.append(escape(value.toString()));
/*     */           
/*     */           continue;
/*     */         } 
/*     */         
/* 574 */         if (value instanceof JSONArray) {
/* 575 */           JSONArray ja = (JSONArray)value;
/* 576 */           int jaLength = ja.length();
/*     */           
/* 578 */           for (int i = 0; i < jaLength; i++) {
/* 579 */             Object val = ja.opt(i);
/* 580 */             if (val instanceof JSONArray) {
/* 581 */               sb.append('<');
/* 582 */               sb.append(key);
/* 583 */               sb.append('>');
/* 584 */               sb.append(toString(val));
/* 585 */               sb.append("</");
/* 586 */               sb.append(key);
/* 587 */               sb.append('>');
/*     */             } else {
/* 589 */               sb.append(toString(val, key));
/*     */             } 
/*     */           }  continue;
/* 592 */         }  if ("".equals(value)) {
/* 593 */           sb.append('<');
/* 594 */           sb.append(key);
/* 595 */           sb.append("/>");
/*     */           
/*     */           continue;
/*     */         } 
/*     */         
/* 600 */         sb.append(toString(value, key));
/*     */       } 
/*     */       
/* 603 */       if (tagName != null) {
/*     */ 
/*     */         
/* 606 */         sb.append("</");
/* 607 */         sb.append(tagName);
/* 608 */         sb.append('>');
/*     */       } 
/* 610 */       return sb.toString();
/*     */     } 
/*     */ 
/*     */     
/* 614 */     if (object != null && (object instanceof JSONArray || object.getClass().isArray())) {
/* 615 */       JSONArray ja; if (object.getClass().isArray()) {
/* 616 */         ja = new JSONArray(object);
/*     */       } else {
/* 618 */         ja = (JSONArray)object;
/*     */       } 
/* 620 */       int jaLength = ja.length();
/*     */       
/* 622 */       for (int i = 0; i < jaLength; i++) {
/* 623 */         Object val = ja.opt(i);
/*     */ 
/*     */ 
/*     */         
/* 627 */         sb.append(toString(val, (tagName == null) ? "array" : tagName));
/*     */       } 
/* 629 */       return sb.toString();
/*     */     } 
/*     */     
/* 632 */     String string = (object == null) ? "null" : escape(object.toString());
/* 633 */     return (tagName == null) ? ("\"" + string + "\"") : (
/* 634 */       (string.length() == 0) ? ("<" + tagName + "/>") : (
/* 635 */       "<" + tagName + ">" + string + "</" + tagName + ">"));
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\org\json\XML.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */