/*      */ package org.json;
/*      */ 
/*      */ import java.io.Closeable;
/*      */ import java.io.IOException;
/*      */ import java.io.StringWriter;
/*      */ import java.io.Writer;
/*      */ import java.lang.reflect.Field;
/*      */ import java.lang.reflect.InvocationTargetException;
/*      */ import java.lang.reflect.Method;
/*      */ import java.lang.reflect.Modifier;
/*      */ import java.math.BigDecimal;
/*      */ import java.math.BigInteger;
/*      */ import java.util.Collection;
/*      */ import java.util.Enumeration;
/*      */ import java.util.HashMap;
/*      */ import java.util.Iterator;
/*      */ import java.util.Locale;
/*      */ import java.util.Map;
/*      */ import java.util.ResourceBundle;
/*      */ import java.util.Set;
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ public class JSONObject
/*      */ {
/*      */   private final Map<String, Object> map;
/*      */   
/*      */   private static final class Null
/*      */   {
/*      */     private Null() {}
/*      */     
/*      */     protected final Object clone() {
/*  117 */       return this;
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*      */     public boolean equals(Object object) {
/*  130 */       return (object == null || object == this);
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*      */     public int hashCode() {
/*  139 */       return 0;
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*      */     public String toString() {
/*  149 */       return "null";
/*      */     }
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*  164 */   public static final Object NULL = new Null();
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject() {
/*  176 */     this.map = new HashMap<String, Object>();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(JSONObject jo, String[] names) {
/*  190 */     this(names.length);
/*  191 */     for (int i = 0; i < names.length; i++) {
/*      */       try {
/*  193 */         putOnce(names[i], jo.opt(names[i]));
/*  194 */       } catch (Exception exception) {}
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(JSONTokener x) throws JSONException {
/*  209 */     this();
/*      */ 
/*      */ 
/*      */     
/*  213 */     if (x.nextClean() != '{') {
/*  214 */       throw x.syntaxError("A JSONObject text must begin with '{'");
/*      */     }
/*      */     while (true) {
/*  217 */       char c = x.nextClean();
/*  218 */       switch (c) {
/*      */         case '\000':
/*  220 */           throw x.syntaxError("A JSONObject text must end with '}'");
/*      */         case '}':
/*      */           return;
/*      */       } 
/*  224 */       x.back();
/*  225 */       String key = x.nextValue().toString();
/*      */ 
/*      */ 
/*      */ 
/*      */       
/*  230 */       c = x.nextClean();
/*  231 */       if (c != ':') {
/*  232 */         throw x.syntaxError("Expected a ':' after a key");
/*      */       }
/*      */ 
/*      */ 
/*      */       
/*  237 */       if (key != null) {
/*      */         
/*  239 */         if (opt(key) != null)
/*      */         {
/*  241 */           throw x.syntaxError("Duplicate key \"" + key + "\"");
/*      */         }
/*      */         
/*  244 */         Object value = x.nextValue();
/*  245 */         if (value != null) {
/*  246 */           put(key, value);
/*      */         }
/*      */       } 
/*      */ 
/*      */ 
/*      */       
/*  252 */       switch (x.nextClean()) {
/*      */         case ',':
/*      */         case ';':
/*  255 */           if (x.nextClean() == '}') {
/*      */             return;
/*      */           }
/*  258 */           x.back(); continue;
/*      */         case '}':
/*      */           return;
/*      */       }  break;
/*      */     } 
/*  263 */     throw x.syntaxError("Expected a ',' or '}'");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(Map<?, ?> m) {
/*  276 */     if (m == null) {
/*  277 */       this.map = new HashMap<String, Object>();
/*      */     } else {
/*  279 */       this.map = new HashMap<String, Object>(m.size());
/*  280 */       for (Map.Entry<?, ?> e : m.entrySet()) {
/*  281 */         Object value = e.getValue();
/*  282 */         if (value != null) {
/*  283 */           this.map.put(String.valueOf(e.getKey()), wrap(value));
/*      */         }
/*      */       } 
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(Object bean) {
/*  314 */     this();
/*  315 */     populateMap(bean);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(Object object, String[] names) {
/*  333 */     this(names.length);
/*  334 */     Class<?> c = object.getClass();
/*  335 */     for (int i = 0; i < names.length; i++) {
/*  336 */       String name = names[i];
/*      */       try {
/*  338 */         putOpt(name, c.getField(name).get(object));
/*  339 */       } catch (Exception exception) {}
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(String source) throws JSONException {
/*  357 */     this(new JSONTokener(source));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject(String baseName, Locale locale) throws JSONException {
/*  371 */     this();
/*  372 */     ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale, 
/*  373 */         Thread.currentThread().getContextClassLoader());
/*      */ 
/*      */ 
/*      */     
/*  377 */     Enumeration<String> keys = bundle.getKeys();
/*  378 */     while (keys.hasMoreElements()) {
/*  379 */       Object key = keys.nextElement();
/*  380 */       if (key != null) {
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */         
/*  386 */         String[] path = ((String)key).split("\\.");
/*  387 */         int last = path.length - 1;
/*  388 */         JSONObject target = this;
/*  389 */         for (int i = 0; i < last; i++) {
/*  390 */           String segment = path[i];
/*  391 */           JSONObject nextTarget = target.optJSONObject(segment);
/*  392 */           if (nextTarget == null) {
/*  393 */             nextTarget = new JSONObject();
/*  394 */             target.put(segment, nextTarget);
/*      */           } 
/*  396 */           target = nextTarget;
/*      */         } 
/*  398 */         target.put(path[last], bundle.getString((String)key));
/*      */       } 
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   protected JSONObject(int initialCapacity) {
/*  411 */     this.map = new HashMap<String, Object>(initialCapacity);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject accumulate(String key, Object value) throws JSONException {
/*  434 */     testValidity(value);
/*  435 */     Object object = opt(key);
/*  436 */     if (object == null) {
/*  437 */       put(key, 
/*  438 */           (value instanceof JSONArray) ? (new JSONArray()).put(value) : 
/*  439 */           value);
/*  440 */     } else if (object instanceof JSONArray) {
/*  441 */       ((JSONArray)object).put(value);
/*      */     } else {
/*  443 */       put(key, (new JSONArray()).put(object).put(value));
/*      */     } 
/*  445 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject append(String key, Object value) throws JSONException {
/*  464 */     testValidity(value);
/*  465 */     Object object = opt(key);
/*  466 */     if (object == null) {
/*  467 */       put(key, (new JSONArray()).put(value));
/*  468 */     } else if (object instanceof JSONArray) {
/*  469 */       put(key, ((JSONArray)object).put(value));
/*      */     } else {
/*  471 */       throw new JSONException("JSONObject[" + key + "] is not a JSONArray.");
/*      */     } 
/*      */     
/*  474 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String doubleToString(double d) {
/*  486 */     if (Double.isInfinite(d) || Double.isNaN(d)) {
/*  487 */       return "null";
/*      */     }
/*      */ 
/*      */ 
/*      */     
/*  492 */     String string = Double.toString(d);
/*  493 */     if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string
/*  494 */       .indexOf('E') < 0) {
/*  495 */       while (string.endsWith("0")) {
/*  496 */         string = string.substring(0, string.length() - 1);
/*      */       }
/*  498 */       if (string.endsWith(".")) {
/*  499 */         string = string.substring(0, string.length() - 1);
/*      */       }
/*      */     } 
/*  502 */     return string;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object get(String key) throws JSONException {
/*  515 */     if (key == null) {
/*  516 */       throw new JSONException("Null key.");
/*      */     }
/*  518 */     Object object = opt(key);
/*  519 */     if (object == null) {
/*  520 */       throw new JSONException("JSONObject[" + quote(key) + "] not found.");
/*      */     }
/*  522 */     return object;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public <E extends Enum<E>> E getEnum(Class<E> clazz, String key) throws JSONException {
/*  538 */     E val = optEnum(clazz, key);
/*  539 */     if (val == null)
/*      */     {
/*      */ 
/*      */       
/*  543 */       throw new JSONException("JSONObject[" + quote(key) + "] is not an enum of type " + 
/*  544 */           quote(clazz.getSimpleName()) + ".");
/*      */     }
/*      */     
/*  547 */     return val;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean getBoolean(String key) throws JSONException {
/*  561 */     Object object = get(key);
/*  562 */     if (object.equals(Boolean.FALSE) || (object instanceof String && ((String)object)
/*      */       
/*  564 */       .equalsIgnoreCase("false")))
/*  565 */       return false; 
/*  566 */     if (object.equals(Boolean.TRUE) || (object instanceof String && ((String)object)
/*      */       
/*  568 */       .equalsIgnoreCase("true"))) {
/*  569 */       return true;
/*      */     }
/*  571 */     throw new JSONException("JSONObject[" + quote(key) + "] is not a Boolean.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public BigInteger getBigInteger(String key) throws JSONException {
/*  586 */     Object object = get(key);
/*      */     try {
/*  588 */       return new BigInteger(object.toString());
/*  589 */     } catch (Exception e) {
/*  590 */       throw new JSONException("JSONObject[" + quote(key) + "] could not be converted to BigInteger.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public BigDecimal getBigDecimal(String key) throws JSONException {
/*  606 */     Object object = get(key);
/*  607 */     if (object instanceof BigDecimal) {
/*  608 */       return (BigDecimal)object;
/*      */     }
/*      */     try {
/*  611 */       return new BigDecimal(object.toString());
/*  612 */     } catch (Exception e) {
/*  613 */       throw new JSONException("JSONObject[" + quote(key) + "] could not be converted to BigDecimal.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public double getDouble(String key) throws JSONException {
/*  629 */     Object object = get(key);
/*      */     try {
/*  631 */       return (object instanceof Number) ? ((Number)object).doubleValue() : 
/*  632 */         Double.parseDouble(object.toString());
/*  633 */     } catch (Exception e) {
/*  634 */       throw new JSONException("JSONObject[" + quote(key) + "] is not a number.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public float getFloat(String key) throws JSONException {
/*  650 */     Object object = get(key);
/*      */     try {
/*  652 */       return (object instanceof Number) ? ((Number)object).floatValue() : 
/*  653 */         Float.parseFloat(object.toString());
/*  654 */     } catch (Exception e) {
/*  655 */       throw new JSONException("JSONObject[" + quote(key) + "] is not a number.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Number getNumber(String key) throws JSONException {
/*  671 */     Object object = get(key);
/*      */     try {
/*  673 */       if (object instanceof Number) {
/*  674 */         return (Number)object;
/*      */       }
/*  676 */       return stringToNumber(object.toString());
/*  677 */     } catch (Exception e) {
/*  678 */       throw new JSONException("JSONObject[" + quote(key) + "] is not a number.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public int getInt(String key) throws JSONException {
/*  694 */     Object object = get(key);
/*      */     try {
/*  696 */       return (object instanceof Number) ? ((Number)object).intValue() : 
/*  697 */         Integer.parseInt((String)object);
/*  698 */     } catch (Exception e) {
/*  699 */       throw new JSONException("JSONObject[" + quote(key) + "] is not an int.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray getJSONArray(String key) throws JSONException {
/*  714 */     Object object = get(key);
/*  715 */     if (object instanceof JSONArray) {
/*  716 */       return (JSONArray)object;
/*      */     }
/*  718 */     throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONArray.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject getJSONObject(String key) throws JSONException {
/*  732 */     Object object = get(key);
/*  733 */     if (object instanceof JSONObject) {
/*  734 */       return (JSONObject)object;
/*      */     }
/*  736 */     throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public long getLong(String key) throws JSONException {
/*  751 */     Object object = get(key);
/*      */     try {
/*  753 */       return (object instanceof Number) ? ((Number)object).longValue() : 
/*  754 */         Long.parseLong((String)object);
/*  755 */     } catch (Exception e) {
/*  756 */       throw new JSONException("JSONObject[" + quote(key) + "] is not a long.", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String[] getNames(JSONObject jo) {
/*  767 */     int length = jo.length();
/*  768 */     if (length == 0) {
/*  769 */       return null;
/*      */     }
/*  771 */     return jo.keySet().<String>toArray(new String[length]);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String[] getNames(Object object) {
/*  780 */     if (object == null) {
/*  781 */       return null;
/*      */     }
/*  783 */     Class<?> klass = object.getClass();
/*  784 */     Field[] fields = klass.getFields();
/*  785 */     int length = fields.length;
/*  786 */     if (length == 0) {
/*  787 */       return null;
/*      */     }
/*  789 */     String[] names = new String[length];
/*  790 */     for (int i = 0; i < length; i++) {
/*  791 */       names[i] = fields[i].getName();
/*      */     }
/*  793 */     return names;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public String getString(String key) throws JSONException {
/*  806 */     Object object = get(key);
/*  807 */     if (object instanceof String) {
/*  808 */       return (String)object;
/*      */     }
/*  810 */     throw new JSONException("JSONObject[" + quote(key) + "] not a string.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean has(String key) {
/*  821 */     return this.map.containsKey(key);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject increment(String key) throws JSONException {
/*  837 */     Object value = opt(key);
/*  838 */     if (value == null) {
/*  839 */       put(key, 1);
/*  840 */     } else if (value instanceof BigInteger) {
/*  841 */       put(key, ((BigInteger)value).add(BigInteger.ONE));
/*  842 */     } else if (value instanceof BigDecimal) {
/*  843 */       put(key, ((BigDecimal)value).add(BigDecimal.ONE));
/*  844 */     } else if (value instanceof Integer) {
/*  845 */       put(key, ((Integer)value).intValue() + 1);
/*  846 */     } else if (value instanceof Long) {
/*  847 */       put(key, ((Long)value).longValue() + 1L);
/*  848 */     } else if (value instanceof Double) {
/*  849 */       put(key, ((Double)value).doubleValue() + 1.0D);
/*  850 */     } else if (value instanceof Float) {
/*  851 */       put(key, ((Float)value).floatValue() + 1.0F);
/*      */     } else {
/*  853 */       throw new JSONException("Unable to increment [" + quote(key) + "].");
/*      */     } 
/*  855 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean isNull(String key) {
/*  868 */     return NULL.equals(opt(key));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Iterator<String> keys() {
/*  880 */     return keySet().iterator();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Set<String> keySet() {
/*  892 */     return this.map.keySet();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   protected Set<Map.Entry<String, Object>> entrySet() {
/*  908 */     return this.map.entrySet();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public int length() {
/*  917 */     return this.map.size();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray names() {
/*  928 */     if (this.map.isEmpty()) {
/*  929 */       return null;
/*      */     }
/*  931 */     return new JSONArray(this.map.keySet());
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String numberToString(Number number) throws JSONException {
/*  944 */     if (number == null) {
/*  945 */       throw new JSONException("Null pointer");
/*      */     }
/*  947 */     testValidity(number);
/*      */ 
/*      */ 
/*      */     
/*  951 */     String string = number.toString();
/*  952 */     if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string
/*  953 */       .indexOf('E') < 0) {
/*  954 */       while (string.endsWith("0")) {
/*  955 */         string = string.substring(0, string.length() - 1);
/*      */       }
/*  957 */       if (string.endsWith(".")) {
/*  958 */         string = string.substring(0, string.length() - 1);
/*      */       }
/*      */     } 
/*  961 */     return string;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object opt(String key) {
/*  972 */     return (key == null) ? null : this.map.get(key);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public <E extends Enum<E>> E optEnum(Class<E> clazz, String key) {
/*  985 */     return optEnum(clazz, key, null);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public <E extends Enum<E>> E optEnum(Class<E> clazz, String key, E defaultValue) {
/*      */     try {
/* 1002 */       Object val = opt(key);
/* 1003 */       if (NULL.equals(val)) {
/* 1004 */         return defaultValue;
/*      */       }
/* 1006 */       if (clazz.isAssignableFrom(val.getClass()))
/*      */       {
/*      */         
/* 1009 */         return (E)val;
/*      */       }
/*      */       
/* 1012 */       return Enum.valueOf(clazz, val.toString());
/* 1013 */     } catch (IllegalArgumentException e) {
/* 1014 */       return defaultValue;
/* 1015 */     } catch (NullPointerException e) {
/* 1016 */       return defaultValue;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean optBoolean(String key) {
/* 1029 */     return optBoolean(key, false);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean optBoolean(String key, boolean defaultValue) {
/* 1044 */     Object val = opt(key);
/* 1045 */     if (NULL.equals(val)) {
/* 1046 */       return defaultValue;
/*      */     }
/* 1048 */     if (val instanceof Boolean) {
/* 1049 */       return ((Boolean)val).booleanValue();
/*      */     }
/*      */     
/*      */     try {
/* 1053 */       return getBoolean(key);
/* 1054 */     } catch (Exception e) {
/* 1055 */       return defaultValue;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public BigDecimal optBigDecimal(String key, BigDecimal defaultValue) {
/* 1071 */     Object val = opt(key);
/* 1072 */     if (NULL.equals(val)) {
/* 1073 */       return defaultValue;
/*      */     }
/* 1075 */     if (val instanceof BigDecimal) {
/* 1076 */       return (BigDecimal)val;
/*      */     }
/* 1078 */     if (val instanceof BigInteger) {
/* 1079 */       return new BigDecimal((BigInteger)val);
/*      */     }
/* 1081 */     if (val instanceof Double || val instanceof Float) {
/* 1082 */       return new BigDecimal(((Number)val).doubleValue());
/*      */     }
/* 1084 */     if (val instanceof Long || val instanceof Integer || val instanceof Short || val instanceof Byte)
/*      */     {
/* 1086 */       return new BigDecimal(((Number)val).longValue());
/*      */     }
/*      */     
/*      */     try {
/* 1090 */       return new BigDecimal(val.toString());
/* 1091 */     } catch (Exception e) {
/* 1092 */       return defaultValue;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public BigInteger optBigInteger(String key, BigInteger defaultValue) {
/* 1108 */     Object val = opt(key);
/* 1109 */     if (NULL.equals(val)) {
/* 1110 */       return defaultValue;
/*      */     }
/* 1112 */     if (val instanceof BigInteger) {
/* 1113 */       return (BigInteger)val;
/*      */     }
/* 1115 */     if (val instanceof BigDecimal) {
/* 1116 */       return ((BigDecimal)val).toBigInteger();
/*      */     }
/* 1118 */     if (val instanceof Double || val instanceof Float) {
/* 1119 */       return (new BigDecimal(((Number)val).doubleValue())).toBigInteger();
/*      */     }
/* 1121 */     if (val instanceof Long || val instanceof Integer || val instanceof Short || val instanceof Byte)
/*      */     {
/* 1123 */       return BigInteger.valueOf(((Number)val).longValue());
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*      */     try {
/* 1132 */       String valStr = val.toString();
/* 1133 */       if (isDecimalNotation(valStr)) {
/* 1134 */         return (new BigDecimal(valStr)).toBigInteger();
/*      */       }
/* 1136 */       return new BigInteger(valStr);
/* 1137 */     } catch (Exception e) {
/* 1138 */       return defaultValue;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public double optDouble(String key) {
/* 1152 */     return optDouble(key, Double.NaN);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public double optDouble(String key, double defaultValue) {
/* 1167 */     Object val = opt(key);
/* 1168 */     if (NULL.equals(val)) {
/* 1169 */       return defaultValue;
/*      */     }
/* 1171 */     if (val instanceof Number) {
/* 1172 */       return ((Number)val).doubleValue();
/*      */     }
/* 1174 */     if (val instanceof String) {
/*      */       try {
/* 1176 */         return Double.parseDouble((String)val);
/* 1177 */       } catch (Exception e) {
/* 1178 */         return defaultValue;
/*      */       } 
/*      */     }
/* 1181 */     return defaultValue;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public float optFloat(String key) {
/* 1194 */     return optFloat(key, Float.NaN);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public float optFloat(String key, float defaultValue) {
/* 1209 */     Object val = opt(key);
/* 1210 */     if (NULL.equals(val)) {
/* 1211 */       return defaultValue;
/*      */     }
/* 1213 */     if (val instanceof Number) {
/* 1214 */       return ((Number)val).floatValue();
/*      */     }
/* 1216 */     if (val instanceof String) {
/*      */       try {
/* 1218 */         return Float.parseFloat((String)val);
/* 1219 */       } catch (Exception e) {
/* 1220 */         return defaultValue;
/*      */       } 
/*      */     }
/* 1223 */     return defaultValue;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public int optInt(String key) {
/* 1236 */     return optInt(key, 0);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public int optInt(String key, int defaultValue) {
/* 1251 */     Object val = opt(key);
/* 1252 */     if (NULL.equals(val)) {
/* 1253 */       return defaultValue;
/*      */     }
/* 1255 */     if (val instanceof Number) {
/* 1256 */       return ((Number)val).intValue();
/*      */     }
/*      */     
/* 1259 */     if (val instanceof String) {
/*      */       try {
/* 1261 */         return (new BigDecimal((String)val)).intValue();
/* 1262 */       } catch (Exception e) {
/* 1263 */         return defaultValue;
/*      */       } 
/*      */     }
/* 1266 */     return defaultValue;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray optJSONArray(String key) {
/* 1278 */     Object o = opt(key);
/* 1279 */     return (o instanceof JSONArray) ? (JSONArray)o : null;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject optJSONObject(String key) {
/* 1291 */     Object object = opt(key);
/* 1292 */     return (object instanceof JSONObject) ? (JSONObject)object : null;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public long optLong(String key) {
/* 1305 */     return optLong(key, 0L);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public long optLong(String key, long defaultValue) {
/* 1320 */     Object val = opt(key);
/* 1321 */     if (NULL.equals(val)) {
/* 1322 */       return defaultValue;
/*      */     }
/* 1324 */     if (val instanceof Number) {
/* 1325 */       return ((Number)val).longValue();
/*      */     }
/*      */     
/* 1328 */     if (val instanceof String) {
/*      */       try {
/* 1330 */         return (new BigDecimal((String)val)).longValue();
/* 1331 */       } catch (Exception e) {
/* 1332 */         return defaultValue;
/*      */       } 
/*      */     }
/* 1335 */     return defaultValue;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Number optNumber(String key) {
/* 1349 */     return optNumber(key, null);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Number optNumber(String key, Number defaultValue) {
/* 1365 */     Object val = opt(key);
/* 1366 */     if (NULL.equals(val)) {
/* 1367 */       return defaultValue;
/*      */     }
/* 1369 */     if (val instanceof Number) {
/* 1370 */       return (Number)val;
/*      */     }
/*      */     
/* 1373 */     if (val instanceof String) {
/*      */       try {
/* 1375 */         return stringToNumber((String)val);
/* 1376 */       } catch (Exception e) {
/* 1377 */         return defaultValue;
/*      */       } 
/*      */     }
/* 1380 */     return defaultValue;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public String optString(String key) {
/* 1393 */     return optString(key, "");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public String optString(String key, String defaultValue) {
/* 1407 */     Object object = opt(key);
/* 1408 */     return NULL.equals(object) ? defaultValue : object.toString();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   private void populateMap(Object bean) {
/* 1421 */     Class<?> klass = bean.getClass();
/*      */ 
/*      */ 
/*      */     
/* 1425 */     boolean includeSuperClass = (klass.getClassLoader() != null);
/*      */ 
/*      */     
/* 1428 */     Method[] methods = includeSuperClass ? klass.getMethods() : klass.getDeclaredMethods();
/* 1429 */     for (Method method : methods) {
/* 1430 */       int modifiers = method.getModifiers();
/* 1431 */       if (Modifier.isPublic(modifiers) && 
/* 1432 */         !Modifier.isStatic(modifiers) && (method
/* 1433 */         .getParameterTypes()).length == 0 && 
/* 1434 */         !method.isBridge() && method
/* 1435 */         .getReturnType() != void.class) {
/* 1436 */         String key, name = method.getName();
/*      */         
/* 1438 */         if (name.startsWith("get")) {
/* 1439 */           if ("getClass".equals(name) || "getDeclaringClass".equals(name)) {
/*      */             continue;
/*      */           }
/* 1442 */           key = name.substring(3);
/* 1443 */         } else if (name.startsWith("is")) {
/* 1444 */           key = name.substring(2);
/*      */         } else {
/*      */           continue;
/*      */         } 
/* 1448 */         if (key.length() > 0 && 
/* 1449 */           Character.isUpperCase(key.charAt(0))) {
/* 1450 */           if (key.length() == 1) {
/* 1451 */             key = key.toLowerCase(Locale.ROOT);
/* 1452 */           } else if (!Character.isUpperCase(key.charAt(1))) {
/*      */             
/* 1454 */             key = key.substring(0, 1).toLowerCase(Locale.ROOT) + key.substring(1);
/*      */           } 
/*      */ 
/*      */           
/* 1458 */           try { Object result = method.invoke(bean, new Object[0]);
/* 1459 */             if (result != null) {
/* 1460 */               this.map.put(key, wrap(result));
/*      */ 
/*      */               
/* 1463 */               if (result instanceof Closeable) {
/*      */                 try {
/* 1465 */                   ((Closeable)result).close();
/* 1466 */                 } catch (IOException iOException) {}
/*      */               }
/*      */             }
/*      */              }
/* 1470 */           catch (IllegalAccessException illegalAccessException) {  }
/* 1471 */           catch (IllegalArgumentException illegalArgumentException) {  }
/* 1472 */           catch (InvocationTargetException invocationTargetException) {}
/*      */         } 
/*      */       } 
/*      */       continue;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, boolean value) throws JSONException {
/* 1491 */     put(key, value ? Boolean.TRUE : Boolean.FALSE);
/* 1492 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, Collection<?> value) throws JSONException {
/* 1507 */     put(key, new JSONArray(value));
/* 1508 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, double value) throws JSONException {
/* 1523 */     put(key, Double.valueOf(value));
/* 1524 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, float value) throws JSONException {
/* 1539 */     put(key, Float.valueOf(value));
/* 1540 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, int value) throws JSONException {
/* 1555 */     put(key, Integer.valueOf(value));
/* 1556 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, long value) throws JSONException {
/* 1571 */     put(key, Long.valueOf(value));
/* 1572 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, Map<?, ?> value) throws JSONException {
/* 1587 */     put(key, new JSONObject(value));
/* 1588 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject put(String key, Object value) throws JSONException {
/* 1606 */     if (key == null) {
/* 1607 */       throw new NullPointerException("Null key.");
/*      */     }
/* 1609 */     if (value != null) {
/* 1610 */       testValidity(value);
/* 1611 */       this.map.put(key, value);
/*      */     } else {
/* 1613 */       remove(key);
/*      */     } 
/* 1615 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject putOnce(String key, Object value) throws JSONException {
/* 1630 */     if (key != null && value != null) {
/* 1631 */       if (opt(key) != null) {
/* 1632 */         throw new JSONException("Duplicate key \"" + key + "\"");
/*      */       }
/* 1634 */       put(key, value);
/*      */     } 
/* 1636 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONObject putOpt(String key, Object value) throws JSONException {
/* 1654 */     if (key != null && value != null) {
/* 1655 */       put(key, value);
/*      */     }
/* 1657 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object query(String jsonPointer) {
/* 1680 */     return query(new JSONPointer(jsonPointer));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object query(JSONPointer jsonPointer) {
/* 1702 */     return jsonPointer.queryFrom(this);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object optQuery(String jsonPointer) {
/* 1714 */     return optQuery(new JSONPointer(jsonPointer));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object optQuery(JSONPointer jsonPointer) {
/*      */     try {
/* 1727 */       return jsonPointer.queryFrom(this);
/* 1728 */     } catch (JSONPointerException e) {
/* 1729 */       return null;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String quote(String string) {
/* 1744 */     StringWriter sw = new StringWriter();
/* 1745 */     synchronized (sw.getBuffer()) {
/*      */       
/* 1747 */       return quote(string, sw).toString();
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static Writer quote(String string, Writer w) throws IOException {
/* 1756 */     if (string == null || string.length() == 0) {
/* 1757 */       w.write("\"\"");
/* 1758 */       return w;
/*      */     } 
/*      */ 
/*      */     
/* 1762 */     char c = Character.MIN_VALUE;
/*      */ 
/*      */     
/* 1765 */     int len = string.length();
/*      */     
/* 1767 */     w.write(34);
/* 1768 */     for (int i = 0; i < len; i++) {
/* 1769 */       char b = c;
/* 1770 */       c = string.charAt(i);
/* 1771 */       switch (c) {
/*      */         case '"':
/*      */         case '\\':
/* 1774 */           w.write(92);
/* 1775 */           w.write(c);
/*      */           break;
/*      */         case '/':
/* 1778 */           if (b == '<') {
/* 1779 */             w.write(92);
/*      */           }
/* 1781 */           w.write(c);
/*      */           break;
/*      */         case '\b':
/* 1784 */           w.write("\\b");
/*      */           break;
/*      */         case '\t':
/* 1787 */           w.write("\\t");
/*      */           break;
/*      */         case '\n':
/* 1790 */           w.write("\\n");
/*      */           break;
/*      */         case '\f':
/* 1793 */           w.write("\\f");
/*      */           break;
/*      */         case '\r':
/* 1796 */           w.write("\\r");
/*      */           break;
/*      */         default:
/* 1799 */           if (c < ' ' || (c >= '' && c < ' ') || (c >= ' ' && c < '℀')) {
/*      */             
/* 1801 */             w.write("\\u");
/* 1802 */             String hhhh = Integer.toHexString(c);
/* 1803 */             w.write("0000", 0, 4 - hhhh.length());
/* 1804 */             w.write(hhhh); break;
/*      */           } 
/* 1806 */           w.write(c);
/*      */           break;
/*      */       } 
/*      */     } 
/* 1810 */     w.write(34);
/* 1811 */     return w;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object remove(String key) {
/* 1823 */     return this.map.remove(key);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean similar(Object other) {
/*      */     try {
/* 1836 */       if (!(other instanceof JSONObject)) {
/* 1837 */         return false;
/*      */       }
/* 1839 */       if (!keySet().equals(((JSONObject)other).keySet())) {
/* 1840 */         return false;
/*      */       }
/* 1842 */       for (Map.Entry<String, ?> entry : entrySet()) {
/* 1843 */         String name = entry.getKey();
/* 1844 */         Object valueThis = entry.getValue();
/* 1845 */         Object valueOther = ((JSONObject)other).get(name);
/* 1846 */         if (valueThis == valueOther) {
/*      */           continue;
/*      */         }
/* 1849 */         if (valueThis == null) {
/* 1850 */           return false;
/*      */         }
/* 1852 */         if (valueThis instanceof JSONObject) {
/* 1853 */           if (!((JSONObject)valueThis).similar(valueOther))
/* 1854 */             return false;  continue;
/*      */         } 
/* 1856 */         if (valueThis instanceof JSONArray) {
/* 1857 */           if (!((JSONArray)valueThis).similar(valueOther))
/* 1858 */             return false;  continue;
/*      */         } 
/* 1860 */         if (!valueThis.equals(valueOther)) {
/* 1861 */           return false;
/*      */         }
/*      */       } 
/* 1864 */       return true;
/* 1865 */     } catch (Throwable exception) {
/* 1866 */       return false;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   protected static boolean isDecimalNotation(String val) {
/* 1877 */     return (val.indexOf('.') > -1 || val.indexOf('e') > -1 || val
/* 1878 */       .indexOf('E') > -1 || "-0".equals(val));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   protected static Number stringToNumber(String val) throws NumberFormatException {
/* 1892 */     char initial = val.charAt(0);
/* 1893 */     if ((initial >= '0' && initial <= '9') || initial == '-') {
/*      */       
/* 1895 */       if (isDecimalNotation(val)) {
/*      */ 
/*      */         
/* 1898 */         if (val.length() > 14) {
/* 1899 */           return new BigDecimal(val);
/*      */         }
/* 1901 */         Double d = Double.valueOf(val);
/* 1902 */         if (d.isInfinite() || d.isNaN())
/*      */         {
/*      */ 
/*      */ 
/*      */           
/* 1907 */           return new BigDecimal(val);
/*      */         }
/* 1909 */         return d;
/*      */       } 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */       
/* 1933 */       BigInteger bi = new BigInteger(val);
/* 1934 */       if (bi.bitLength() <= 31) {
/* 1935 */         return Integer.valueOf(bi.intValue());
/*      */       }
/* 1937 */       if (bi.bitLength() <= 63) {
/* 1938 */         return Long.valueOf(bi.longValue());
/*      */       }
/* 1940 */       return bi;
/*      */     } 
/* 1942 */     throw new NumberFormatException("val [" + val + "] is not a valid number.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static Object stringToValue(String string) {
/* 1956 */     if (string.equals("")) {
/* 1957 */       return string;
/*      */     }
/* 1959 */     if (string.equalsIgnoreCase("true")) {
/* 1960 */       return Boolean.TRUE;
/*      */     }
/* 1962 */     if (string.equalsIgnoreCase("false")) {
/* 1963 */       return Boolean.FALSE;
/*      */     }
/* 1965 */     if (string.equalsIgnoreCase("null")) {
/* 1966 */       return NULL;
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/* 1974 */     char initial = string.charAt(0);
/* 1975 */     if ((initial >= '0' && initial <= '9') || initial == '-') {
/*      */       
/*      */       try {
/*      */         
/* 1979 */         if (isDecimalNotation(string)) {
/* 1980 */           Double d = Double.valueOf(string);
/* 1981 */           if (!d.isInfinite() && !d.isNaN()) {
/* 1982 */             return d;
/*      */           }
/*      */         } else {
/* 1985 */           Long myLong = Long.valueOf(string);
/* 1986 */           if (string.equals(myLong.toString())) {
/* 1987 */             if (myLong.longValue() == myLong.intValue()) {
/* 1988 */               return Integer.valueOf(myLong.intValue());
/*      */             }
/* 1990 */             return myLong;
/*      */           } 
/*      */         } 
/* 1993 */       } catch (Exception exception) {}
/*      */     }
/*      */     
/* 1996 */     return string;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static void testValidity(Object o) throws JSONException {
/* 2008 */     if (o != null) {
/* 2009 */       if (o instanceof Double) {
/* 2010 */         if (((Double)o).isInfinite() || ((Double)o).isNaN()) {
/* 2011 */           throw new JSONException("JSON does not allow non-finite numbers.");
/*      */         }
/*      */       }
/* 2014 */       else if (o instanceof Float && ((
/* 2015 */         (Float)o).isInfinite() || ((Float)o).isNaN())) {
/* 2016 */         throw new JSONException("JSON does not allow non-finite numbers.");
/*      */       } 
/*      */     }
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray toJSONArray(JSONArray names) throws JSONException {
/* 2035 */     if (names == null || names.length() == 0) {
/* 2036 */       return null;
/*      */     }
/* 2038 */     JSONArray ja = new JSONArray();
/* 2039 */     for (int i = 0; i < names.length(); i++) {
/* 2040 */       ja.put(opt(names.getString(i)));
/*      */     }
/* 2042 */     return ja;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public String toString() {
/*      */     try {
/* 2061 */       return toString(0);
/* 2062 */     } catch (Exception e) {
/* 2063 */       return null;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public String toString(int indentFactor) throws JSONException {
/* 2094 */     StringWriter w = new StringWriter();
/* 2095 */     synchronized (w.getBuffer()) {
/* 2096 */       return write(w, indentFactor, 0).toString();
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static String valueToString(Object value) throws JSONException {
/* 2129 */     return JSONWriter.valueToString(value);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public static Object wrap(Object object) {
/*      */     try {
/* 2146 */       if (object == null) {
/* 2147 */         return NULL;
/*      */       }
/* 2149 */       if (object instanceof JSONObject || object instanceof JSONArray || NULL
/* 2150 */         .equals(object) || object instanceof JSONString || object instanceof Byte || object instanceof Character || object instanceof Short || object instanceof Integer || object instanceof Long || object instanceof Boolean || object instanceof Float || object instanceof Double || object instanceof String || object instanceof BigInteger || object instanceof BigDecimal || object instanceof Enum)
/*      */       {
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */         
/* 2157 */         return object;
/*      */       }
/*      */       
/* 2160 */       if (object instanceof Collection) {
/* 2161 */         Collection<?> coll = (Collection)object;
/* 2162 */         return new JSONArray(coll);
/*      */       } 
/* 2164 */       if (object.getClass().isArray()) {
/* 2165 */         return new JSONArray(object);
/*      */       }
/* 2167 */       if (object instanceof Map) {
/* 2168 */         Map<?, ?> map = (Map<?, ?>)object;
/* 2169 */         return new JSONObject(map);
/*      */       } 
/* 2171 */       Package objectPackage = object.getClass().getPackage();
/*      */       
/* 2173 */       String objectPackageName = (objectPackage != null) ? objectPackage.getName() : "";
/* 2174 */       if (objectPackageName.startsWith("java.") || objectPackageName
/* 2175 */         .startsWith("javax.") || object
/* 2176 */         .getClass().getClassLoader() == null) {
/* 2177 */         return object.toString();
/*      */       }
/* 2179 */       return new JSONObject(object);
/* 2180 */     } catch (Exception exception) {
/* 2181 */       return null;
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Writer write(Writer writer) throws JSONException {
/* 2196 */     return write(writer, 0, 0);
/*      */   }
/*      */ 
/*      */   
/*      */   static final Writer writeValue(Writer writer, Object value, int indentFactor, int indent) throws JSONException, IOException {
/* 2201 */     if (value == null || value.equals(null)) {
/* 2202 */       writer.write("null");
/* 2203 */     } else if (value instanceof JSONString) {
/*      */       Object o;
/*      */       try {
/* 2206 */         o = ((JSONString)value).toJSONString();
/* 2207 */       } catch (Exception e) {
/* 2208 */         throw new JSONException(e);
/*      */       } 
/* 2210 */       writer.write((o != null) ? o.toString() : quote(value.toString()));
/* 2211 */     } else if (value instanceof Number) {
/*      */       
/* 2213 */       String numberAsString = numberToString((Number)value);
/*      */ 
/*      */       
/*      */       try {
/* 2217 */         BigDecimal testNum = new BigDecimal(numberAsString);
/*      */         
/* 2219 */         writer.write(numberAsString);
/* 2220 */       } catch (NumberFormatException ex) {
/*      */ 
/*      */         
/* 2223 */         quote(numberAsString, writer);
/*      */       } 
/* 2225 */     } else if (value instanceof Boolean) {
/* 2226 */       writer.write(value.toString());
/* 2227 */     } else if (value instanceof Enum) {
/* 2228 */       writer.write(quote(((Enum)value).name()));
/* 2229 */     } else if (value instanceof JSONObject) {
/* 2230 */       ((JSONObject)value).write(writer, indentFactor, indent);
/* 2231 */     } else if (value instanceof JSONArray) {
/* 2232 */       ((JSONArray)value).write(writer, indentFactor, indent);
/* 2233 */     } else if (value instanceof Map) {
/* 2234 */       Map<?, ?> map = (Map<?, ?>)value;
/* 2235 */       (new JSONObject(map)).write(writer, indentFactor, indent);
/* 2236 */     } else if (value instanceof Collection) {
/* 2237 */       Collection<?> coll = (Collection)value;
/* 2238 */       (new JSONArray(coll)).write(writer, indentFactor, indent);
/* 2239 */     } else if (value.getClass().isArray()) {
/* 2240 */       (new JSONArray(value)).write(writer, indentFactor, indent);
/*      */     } else {
/* 2242 */       quote(value.toString(), writer);
/*      */     } 
/* 2244 */     return writer;
/*      */   }
/*      */   
/*      */   static final void indent(Writer writer, int indent) throws IOException {
/* 2248 */     for (int i = 0; i < indent; i++) {
/* 2249 */       writer.write(32);
/*      */     }
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
/*      */     try {
/* 2282 */       boolean commanate = false;
/* 2283 */       int length = length();
/* 2284 */       writer.write(123);
/*      */       
/* 2286 */       if (length == 1) {
/* 2287 */         Map.Entry<String, ?> entry = entrySet().iterator().next();
/* 2288 */         String key = entry.getKey();
/* 2289 */         writer.write(quote(key));
/* 2290 */         writer.write(58);
/* 2291 */         if (indentFactor > 0) {
/* 2292 */           writer.write(32);
/*      */         }
/*      */         try {
/* 2295 */           writeValue(writer, entry.getValue(), indentFactor, indent);
/* 2296 */         } catch (Exception e) {
/* 2297 */           throw new JSONException("Unable to write JSONObject value for key: " + key, e);
/*      */         } 
/* 2299 */       } else if (length != 0) {
/* 2300 */         int newindent = indent + indentFactor;
/* 2301 */         for (Map.Entry<String, ?> entry : entrySet()) {
/* 2302 */           if (commanate) {
/* 2303 */             writer.write(44);
/*      */           }
/* 2305 */           if (indentFactor > 0) {
/* 2306 */             writer.write(10);
/*      */           }
/* 2308 */           indent(writer, newindent);
/* 2309 */           String key = entry.getKey();
/* 2310 */           writer.write(quote(key));
/* 2311 */           writer.write(58);
/* 2312 */           if (indentFactor > 0) {
/* 2313 */             writer.write(32);
/*      */           }
/*      */           try {
/* 2316 */             writeValue(writer, entry.getValue(), indentFactor, newindent);
/* 2317 */           } catch (Exception e) {
/* 2318 */             throw new JSONException("Unable to write JSONObject value for key: " + key, e);
/*      */           } 
/* 2320 */           commanate = true;
/*      */         } 
/* 2322 */         if (indentFactor > 0) {
/* 2323 */           writer.write(10);
/*      */         }
/* 2325 */         indent(writer, indent);
/*      */       } 
/* 2327 */       writer.write(125);
/* 2328 */       return writer;
/* 2329 */     } catch (IOException exception) {
/* 2330 */       throw new JSONException(exception);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Map<String, Object> toMap() {
/* 2344 */     Map<String, Object> results = new HashMap<String, Object>();
/* 2345 */     for (Map.Entry<String, Object> entry : entrySet()) {
/*      */       Object value;
/* 2347 */       if (entry.getValue() == null || NULL.equals(entry.getValue())) {
/* 2348 */         value = null;
/* 2349 */       } else if (entry.getValue() instanceof JSONObject) {
/* 2350 */         value = ((JSONObject)entry.getValue()).toMap();
/* 2351 */       } else if (entry.getValue() instanceof JSONArray) {
/* 2352 */         value = ((JSONArray)entry.getValue()).toList();
/*      */       } else {
/* 2354 */         value = entry.getValue();
/*      */       } 
/* 2356 */       results.put(entry.getKey(), value);
/*      */     } 
/* 2358 */     return results;
/*      */   }
/*      */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\org\json\JSONObject.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */