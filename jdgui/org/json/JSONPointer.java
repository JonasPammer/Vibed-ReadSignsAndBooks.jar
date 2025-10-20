/*     */ package org.json;
/*     */ 
/*     */ import java.io.UnsupportedEncodingException;
/*     */ import java.net.URLDecoder;
/*     */ import java.net.URLEncoder;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Collections;
/*     */ import java.util.List;
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
/*     */ public class JSONPointer
/*     */ {
/*     */   private static final String ENCODING = "utf-8";
/*     */   private final List<String> refTokens;
/*     */   
/*     */   public static class Builder
/*     */   {
/*  66 */     private final List<String> refTokens = new ArrayList<String>();
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*     */     public JSONPointer build() {
/*  73 */       return new JSONPointer(this.refTokens);
/*     */     }
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
/*     */     public Builder append(String token) {
/*  89 */       if (token == null) {
/*  90 */         throw new NullPointerException("token cannot be null");
/*     */       }
/*  92 */       this.refTokens.add(token);
/*  93 */       return this;
/*     */     }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*     */     public Builder append(int arrayIndex) {
/* 104 */       this.refTokens.add(String.valueOf(arrayIndex));
/* 105 */       return this;
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
/*     */   public static Builder builder() {
/* 125 */     return new Builder();
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
/*     */   public JSONPointer(String pointer) {
/*     */     String refs;
/* 140 */     if (pointer == null) {
/* 141 */       throw new NullPointerException("pointer cannot be null");
/*     */     }
/* 143 */     if (pointer.isEmpty() || pointer.equals("#")) {
/* 144 */       this.refTokens = Collections.emptyList();
/*     */       
/*     */       return;
/*     */     } 
/* 148 */     if (pointer.startsWith("#/")) {
/* 149 */       refs = pointer.substring(2);
/*     */       try {
/* 151 */         refs = URLDecoder.decode(refs, "utf-8");
/* 152 */       } catch (UnsupportedEncodingException e) {
/* 153 */         throw new RuntimeException(e);
/*     */       } 
/* 155 */     } else if (pointer.startsWith("/")) {
/* 156 */       refs = pointer.substring(1);
/*     */     } else {
/* 158 */       throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
/*     */     } 
/* 160 */     this.refTokens = new ArrayList<String>();
/* 161 */     for (String token : refs.split("/")) {
/* 162 */       this.refTokens.add(unescape(token));
/*     */     }
/*     */   }
/*     */   
/*     */   public JSONPointer(List<String> refTokens) {
/* 167 */     this.refTokens = new ArrayList<String>(refTokens);
/*     */   }
/*     */   
/*     */   private String unescape(String token) {
/* 171 */     return token.replace("~1", "/").replace("~0", "~")
/* 172 */       .replace("\\\"", "\"")
/* 173 */       .replace("\\\\", "\\");
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
/*     */   public Object queryFrom(Object document) throws JSONPointerException {
/* 187 */     if (this.refTokens.isEmpty()) {
/* 188 */       return document;
/*     */     }
/* 190 */     Object current = document;
/* 191 */     for (String token : this.refTokens) {
/* 192 */       if (current instanceof JSONObject) {
/* 193 */         current = ((JSONObject)current).opt(unescape(token)); continue;
/* 194 */       }  if (current instanceof JSONArray) {
/* 195 */         current = readByIndexToken(current, token); continue;
/*     */       } 
/* 197 */       throw new JSONPointerException(String.format("value [%s] is not an array or object therefore its key %s cannot be resolved", new Object[] { current, token }));
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/* 202 */     return current;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private Object readByIndexToken(Object current, String indexToken) throws JSONPointerException {
/*     */     try {
/* 214 */       int index = Integer.parseInt(indexToken);
/* 215 */       JSONArray currentArr = (JSONArray)current;
/* 216 */       if (index >= currentArr.length()) {
/* 217 */         throw new JSONPointerException(String.format("index %d is out of bounds - the array has %d elements", new Object[] { Integer.valueOf(index), 
/* 218 */                 Integer.valueOf(currentArr.length()) }));
/*     */       }
/*     */       try {
/* 221 */         return currentArr.get(index);
/* 222 */       } catch (JSONException e) {
/* 223 */         throw new JSONPointerException("Error reading value at index position " + index, e);
/*     */       } 
/* 225 */     } catch (NumberFormatException e) {
/* 226 */       throw new JSONPointerException(String.format("%s is not an array index", new Object[] { indexToken }), e);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String toString() {
/* 236 */     StringBuilder rval = new StringBuilder("");
/* 237 */     for (String token : this.refTokens) {
/* 238 */       rval.append('/').append(escape(token));
/*     */     }
/* 240 */     return rval.toString();
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
/*     */   private String escape(String token) {
/* 252 */     return token.replace("~", "~0")
/* 253 */       .replace("/", "~1")
/* 254 */       .replace("\\", "\\\\")
/* 255 */       .replace("\"", "\\\"");
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String toURIFragment() {
/*     */     try {
/* 264 */       StringBuilder rval = new StringBuilder("#");
/* 265 */       for (String token : this.refTokens) {
/* 266 */         rval.append('/').append(URLEncoder.encode(token, "utf-8"));
/*     */       }
/* 268 */       return rval.toString();
/* 269 */     } catch (UnsupportedEncodingException e) {
/* 270 */       throw new RuntimeException(e);
/*     */     } 
/*     */   }
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\org\json\JSONPointer.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */