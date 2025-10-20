/*      */ package org.json;
/*      */ 
/*      */ import java.io.IOException;
/*      */ import java.io.StringWriter;
/*      */ import java.io.Writer;
/*      */ import java.lang.reflect.Array;
/*      */ import java.math.BigDecimal;
/*      */ import java.math.BigInteger;
/*      */ import java.util.ArrayList;
/*      */ import java.util.Collection;
/*      */ import java.util.Iterator;
/*      */ import java.util.List;
/*      */ import java.util.Map;
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ public class JSONArray
/*      */   implements Iterable<Object>
/*      */ {
/*      */   private final ArrayList<Object> myArrayList;
/*      */   
/*      */   public JSONArray() {
/*   94 */     this.myArrayList = new ArrayList();
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
/*      */   public JSONArray(JSONTokener x) throws JSONException {
/*  106 */     this();
/*  107 */     if (x.nextClean() != '[') {
/*  108 */       throw x.syntaxError("A JSONArray text must start with '['");
/*      */     }
/*      */     
/*  111 */     char nextChar = x.nextClean();
/*  112 */     if (nextChar == '\000')
/*      */     {
/*  114 */       throw x.syntaxError("Expected a ',' or ']'");
/*      */     }
/*  116 */     if (nextChar != ']') {
/*  117 */       x.back();
/*      */       while (true) {
/*  119 */         if (x.nextClean() == ',') {
/*  120 */           x.back();
/*  121 */           this.myArrayList.add(JSONObject.NULL);
/*      */         } else {
/*  123 */           x.back();
/*  124 */           this.myArrayList.add(x.nextValue());
/*      */         } 
/*  126 */         switch (x.nextClean()) {
/*      */           
/*      */           case '\000':
/*  129 */             throw x.syntaxError("Expected a ',' or ']'");
/*      */           case ',':
/*  131 */             nextChar = x.nextClean();
/*  132 */             if (nextChar == '\000')
/*      */             {
/*  134 */               throw x.syntaxError("Expected a ',' or ']'");
/*      */             }
/*  136 */             if (nextChar == ']') {
/*      */               return;
/*      */             }
/*  139 */             x.back(); continue;
/*      */           case ']':
/*      */             return;
/*      */         }  break;
/*      */       } 
/*  144 */       throw x.syntaxError("Expected a ',' or ']'");
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
/*      */   public JSONArray(String source) throws JSONException {
/*  161 */     this(new JSONTokener(source));
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray(Collection<?> collection) {
/*  171 */     if (collection == null) {
/*  172 */       this.myArrayList = new ArrayList();
/*      */     } else {
/*  174 */       this.myArrayList = new ArrayList(collection.size());
/*  175 */       for (Object o : collection) {
/*  176 */         this.myArrayList.add(JSONObject.wrap(o));
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
/*      */   public JSONArray(Object array) throws JSONException {
/*  188 */     this();
/*  189 */     if (array.getClass().isArray()) {
/*  190 */       int length = Array.getLength(array);
/*  191 */       this.myArrayList.ensureCapacity(length);
/*  192 */       for (int i = 0; i < length; i++) {
/*  193 */         put(JSONObject.wrap(Array.get(array, i)));
/*      */       }
/*      */     } else {
/*  196 */       throw new JSONException("JSONArray initial value should be a string or collection or array.");
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public Iterator<Object> iterator() {
/*  203 */     return this.myArrayList.iterator();
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
/*      */   public Object get(int index) throws JSONException {
/*  216 */     Object object = opt(index);
/*  217 */     if (object == null) {
/*  218 */       throw new JSONException("JSONArray[" + index + "] not found.");
/*      */     }
/*  220 */     return object;
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
/*      */   public boolean getBoolean(int index) throws JSONException {
/*  235 */     Object object = get(index);
/*  236 */     if (object.equals(Boolean.FALSE) || (object instanceof String && ((String)object)
/*      */       
/*  238 */       .equalsIgnoreCase("false")))
/*  239 */       return false; 
/*  240 */     if (object.equals(Boolean.TRUE) || (object instanceof String && ((String)object)
/*      */       
/*  242 */       .equalsIgnoreCase("true"))) {
/*  243 */       return true;
/*      */     }
/*  245 */     throw new JSONException("JSONArray[" + index + "] is not a boolean.");
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
/*      */   public double getDouble(int index) throws JSONException {
/*  259 */     Object object = get(index);
/*      */     try {
/*  261 */       return (object instanceof Number) ? ((Number)object).doubleValue() : 
/*  262 */         Double.parseDouble((String)object);
/*  263 */     } catch (Exception e) {
/*  264 */       throw new JSONException("JSONArray[" + index + "] is not a number.", e);
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
/*      */   public float getFloat(int index) throws JSONException {
/*  279 */     Object object = get(index);
/*      */     try {
/*  281 */       return (object instanceof Number) ? ((Number)object).floatValue() : 
/*  282 */         Float.parseFloat(object.toString());
/*  283 */     } catch (Exception e) {
/*  284 */       throw new JSONException("JSONArray[" + index + "] is not a number.", e);
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
/*      */   public Number getNumber(int index) throws JSONException {
/*  300 */     Object object = get(index);
/*      */     try {
/*  302 */       if (object instanceof Number) {
/*  303 */         return (Number)object;
/*      */       }
/*  305 */       return JSONObject.stringToNumber(object.toString());
/*  306 */     } catch (Exception e) {
/*  307 */       throw new JSONException("JSONArray[" + index + "] is not a number.", e);
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
/*      */   public <E extends Enum<E>> E getEnum(Class<E> clazz, int index) throws JSONException {
/*  324 */     E val = optEnum(clazz, index);
/*  325 */     if (val == null)
/*      */     {
/*      */ 
/*      */       
/*  329 */       throw new JSONException("JSONArray[" + index + "] is not an enum of type " + 
/*  330 */           JSONObject.quote(clazz.getSimpleName()) + ".");
/*      */     }
/*  332 */     return val;
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
/*      */   public BigDecimal getBigDecimal(int index) throws JSONException {
/*  346 */     Object object = get(index);
/*      */     try {
/*  348 */       return new BigDecimal(object.toString());
/*  349 */     } catch (Exception e) {
/*  350 */       throw new JSONException("JSONArray[" + index + "] could not convert to BigDecimal.", e);
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
/*      */   public BigInteger getBigInteger(int index) throws JSONException {
/*  366 */     Object object = get(index);
/*      */     try {
/*  368 */       return new BigInteger(object.toString());
/*  369 */     } catch (Exception e) {
/*  370 */       throw new JSONException("JSONArray[" + index + "] could not convert to BigInteger.", e);
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
/*      */   public int getInt(int index) throws JSONException {
/*  385 */     Object object = get(index);
/*      */     try {
/*  387 */       return (object instanceof Number) ? ((Number)object).intValue() : 
/*  388 */         Integer.parseInt((String)object);
/*  389 */     } catch (Exception e) {
/*  390 */       throw new JSONException("JSONArray[" + index + "] is not a number.", e);
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
/*      */   public JSONArray getJSONArray(int index) throws JSONException {
/*  405 */     Object object = get(index);
/*  406 */     if (object instanceof JSONArray) {
/*  407 */       return (JSONArray)object;
/*      */     }
/*  409 */     throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");
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
/*      */   public JSONObject getJSONObject(int index) throws JSONException {
/*  423 */     Object object = get(index);
/*  424 */     if (object instanceof JSONObject) {
/*  425 */       return (JSONObject)object;
/*      */     }
/*  427 */     throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
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
/*      */   public long getLong(int index) throws JSONException {
/*  441 */     Object object = get(index);
/*      */     try {
/*  443 */       return (object instanceof Number) ? ((Number)object).longValue() : 
/*  444 */         Long.parseLong((String)object);
/*  445 */     } catch (Exception e) {
/*  446 */       throw new JSONException("JSONArray[" + index + "] is not a number.", e);
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
/*      */   public String getString(int index) throws JSONException {
/*  460 */     Object object = get(index);
/*  461 */     if (object instanceof String) {
/*  462 */       return (String)object;
/*      */     }
/*  464 */     throw new JSONException("JSONArray[" + index + "] not a string.");
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean isNull(int index) {
/*  475 */     return JSONObject.NULL.equals(opt(index));
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
/*      */   public String join(String separator) throws JSONException {
/*  490 */     int len = length();
/*  491 */     StringBuilder sb = new StringBuilder();
/*      */     
/*  493 */     for (int i = 0; i < len; i++) {
/*  494 */       if (i > 0) {
/*  495 */         sb.append(separator);
/*      */       }
/*  497 */       sb.append(JSONObject.valueToString(this.myArrayList.get(i)));
/*      */     } 
/*  499 */     return sb.toString();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public int length() {
/*  508 */     return this.myArrayList.size();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public Object opt(int index) {
/*  519 */     return (index < 0 || index >= length()) ? null : 
/*  520 */       this.myArrayList.get(index);
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
/*      */   public boolean optBoolean(int index) {
/*  533 */     return optBoolean(index, false);
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
/*      */   public boolean optBoolean(int index, boolean defaultValue) {
/*      */     try {
/*  549 */       return getBoolean(index);
/*  550 */     } catch (Exception e) {
/*  551 */       return defaultValue;
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
/*      */   public double optDouble(int index) {
/*  565 */     return optDouble(index, Double.NaN);
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
/*      */   public double optDouble(int index, double defaultValue) {
/*  580 */     Object val = opt(index);
/*  581 */     if (JSONObject.NULL.equals(val)) {
/*  582 */       return defaultValue;
/*      */     }
/*  584 */     if (val instanceof Number) {
/*  585 */       return ((Number)val).doubleValue();
/*      */     }
/*  587 */     if (val instanceof String) {
/*      */       try {
/*  589 */         return Double.parseDouble((String)val);
/*  590 */       } catch (Exception e) {
/*  591 */         return defaultValue;
/*      */       } 
/*      */     }
/*  594 */     return defaultValue;
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
/*      */   public float optFloat(int index) {
/*  607 */     return optFloat(index, Float.NaN);
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
/*      */   public float optFloat(int index, float defaultValue) {
/*  622 */     Object val = opt(index);
/*  623 */     if (JSONObject.NULL.equals(val)) {
/*  624 */       return defaultValue;
/*      */     }
/*  626 */     if (val instanceof Number) {
/*  627 */       return ((Number)val).floatValue();
/*      */     }
/*  629 */     if (val instanceof String) {
/*      */       try {
/*  631 */         return Float.parseFloat((String)val);
/*  632 */       } catch (Exception e) {
/*  633 */         return defaultValue;
/*      */       } 
/*      */     }
/*  636 */     return defaultValue;
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
/*      */   public int optInt(int index) {
/*  649 */     return optInt(index, 0);
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
/*      */   public int optInt(int index, int defaultValue) {
/*  664 */     Object val = opt(index);
/*  665 */     if (JSONObject.NULL.equals(val)) {
/*  666 */       return defaultValue;
/*      */     }
/*  668 */     if (val instanceof Number) {
/*  669 */       return ((Number)val).intValue();
/*      */     }
/*      */     
/*  672 */     if (val instanceof String) {
/*      */       try {
/*  674 */         return (new BigDecimal(val.toString())).intValue();
/*  675 */       } catch (Exception e) {
/*  676 */         return defaultValue;
/*      */       } 
/*      */     }
/*  679 */     return defaultValue;
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
/*      */   public <E extends Enum<E>> E optEnum(Class<E> clazz, int index) {
/*  692 */     return optEnum(clazz, index, null);
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
/*      */   public <E extends Enum<E>> E optEnum(Class<E> clazz, int index, E defaultValue) {
/*      */     try {
/*  709 */       Object val = opt(index);
/*  710 */       if (JSONObject.NULL.equals(val)) {
/*  711 */         return defaultValue;
/*      */       }
/*  713 */       if (clazz.isAssignableFrom(val.getClass()))
/*      */       {
/*      */         
/*  716 */         return (E)val;
/*      */       }
/*      */       
/*  719 */       return Enum.valueOf(clazz, val.toString());
/*  720 */     } catch (IllegalArgumentException e) {
/*  721 */       return defaultValue;
/*  722 */     } catch (NullPointerException e) {
/*  723 */       return defaultValue;
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
/*      */   public BigInteger optBigInteger(int index, BigInteger defaultValue) {
/*  740 */     Object val = opt(index);
/*  741 */     if (JSONObject.NULL.equals(val)) {
/*  742 */       return defaultValue;
/*      */     }
/*  744 */     if (val instanceof BigInteger) {
/*  745 */       return (BigInteger)val;
/*      */     }
/*  747 */     if (val instanceof BigDecimal) {
/*  748 */       return ((BigDecimal)val).toBigInteger();
/*      */     }
/*  750 */     if (val instanceof Double || val instanceof Float) {
/*  751 */       return (new BigDecimal(((Number)val).doubleValue())).toBigInteger();
/*      */     }
/*  753 */     if (val instanceof Long || val instanceof Integer || val instanceof Short || val instanceof Byte)
/*      */     {
/*  755 */       return BigInteger.valueOf(((Number)val).longValue());
/*      */     }
/*      */     try {
/*  758 */       String valStr = val.toString();
/*  759 */       if (JSONObject.isDecimalNotation(valStr)) {
/*  760 */         return (new BigDecimal(valStr)).toBigInteger();
/*      */       }
/*  762 */       return new BigInteger(valStr);
/*  763 */     } catch (Exception e) {
/*  764 */       return defaultValue;
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
/*      */   public BigDecimal optBigDecimal(int index, BigDecimal defaultValue) {
/*  780 */     Object val = opt(index);
/*  781 */     if (JSONObject.NULL.equals(val)) {
/*  782 */       return defaultValue;
/*      */     }
/*  784 */     if (val instanceof BigDecimal) {
/*  785 */       return (BigDecimal)val;
/*      */     }
/*  787 */     if (val instanceof BigInteger) {
/*  788 */       return new BigDecimal((BigInteger)val);
/*      */     }
/*  790 */     if (val instanceof Double || val instanceof Float) {
/*  791 */       return new BigDecimal(((Number)val).doubleValue());
/*      */     }
/*  793 */     if (val instanceof Long || val instanceof Integer || val instanceof Short || val instanceof Byte)
/*      */     {
/*  795 */       return new BigDecimal(((Number)val).longValue());
/*      */     }
/*      */     try {
/*  798 */       return new BigDecimal(val.toString());
/*  799 */     } catch (Exception e) {
/*  800 */       return defaultValue;
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
/*      */   public JSONArray optJSONArray(int index) {
/*  813 */     Object o = opt(index);
/*  814 */     return (o instanceof JSONArray) ? (JSONArray)o : null;
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
/*      */   public JSONObject optJSONObject(int index) {
/*  827 */     Object o = opt(index);
/*  828 */     return (o instanceof JSONObject) ? (JSONObject)o : null;
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
/*      */   public long optLong(int index) {
/*  841 */     return optLong(index, 0L);
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
/*      */   public long optLong(int index, long defaultValue) {
/*  856 */     Object val = opt(index);
/*  857 */     if (JSONObject.NULL.equals(val)) {
/*  858 */       return defaultValue;
/*      */     }
/*  860 */     if (val instanceof Number) {
/*  861 */       return ((Number)val).longValue();
/*      */     }
/*      */     
/*  864 */     if (val instanceof String) {
/*      */       try {
/*  866 */         return (new BigDecimal(val.toString())).longValue();
/*  867 */       } catch (Exception e) {
/*  868 */         return defaultValue;
/*      */       } 
/*      */     }
/*  871 */     return defaultValue;
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
/*      */   public Number optNumber(int index) {
/*  885 */     return optNumber(index, null);
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
/*      */   public Number optNumber(int index, Number defaultValue) {
/*  901 */     Object val = opt(index);
/*  902 */     if (JSONObject.NULL.equals(val)) {
/*  903 */       return defaultValue;
/*      */     }
/*  905 */     if (val instanceof Number) {
/*  906 */       return (Number)val;
/*      */     }
/*      */     
/*  909 */     if (val instanceof String) {
/*      */       try {
/*  911 */         return JSONObject.stringToNumber((String)val);
/*  912 */       } catch (Exception e) {
/*  913 */         return defaultValue;
/*      */       } 
/*      */     }
/*  916 */     return defaultValue;
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
/*      */   public String optString(int index) {
/*  929 */     return optString(index, "");
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
/*      */   public String optString(int index, String defaultValue) {
/*  943 */     Object object = opt(index);
/*  944 */     return JSONObject.NULL.equals(object) ? defaultValue : 
/*  945 */       object.toString();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray put(boolean value) {
/*  956 */     put(value ? Boolean.TRUE : Boolean.FALSE);
/*  957 */     return this;
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
/*      */   public JSONArray put(Collection<?> value) {
/*  969 */     put(new JSONArray(value));
/*  970 */     return this;
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
/*      */   public JSONArray put(double value) throws JSONException {
/*  983 */     Double d = new Double(value);
/*  984 */     JSONObject.testValidity(d);
/*  985 */     put(d);
/*  986 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray put(int value) {
/*  997 */     put(new Integer(value));
/*  998 */     return this;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public JSONArray put(long value) {
/* 1009 */     put(new Long(value));
/* 1010 */     return this;
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
/*      */   public JSONArray put(Map<?, ?> value) {
/* 1022 */     put(new JSONObject(value));
/* 1023 */     return this;
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
/*      */   public JSONArray put(Object value) {
/* 1036 */     this.myArrayList.add(value);
/* 1037 */     return this;
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
/*      */   public JSONArray put(int index, boolean value) throws JSONException {
/* 1054 */     put(index, value ? Boolean.TRUE : Boolean.FALSE);
/* 1055 */     return this;
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
/*      */   public JSONArray put(int index, Collection<?> value) throws JSONException {
/* 1071 */     put(index, new JSONArray(value));
/* 1072 */     return this;
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
/*      */   public JSONArray put(int index, double value) throws JSONException {
/* 1089 */     put(index, new Double(value));
/* 1090 */     return this;
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
/*      */   public JSONArray put(int index, int value) throws JSONException {
/* 1107 */     put(index, new Integer(value));
/* 1108 */     return this;
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
/*      */   public JSONArray put(int index, long value) throws JSONException {
/* 1125 */     put(index, new Long(value));
/* 1126 */     return this;
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
/*      */   public JSONArray put(int index, Map<?, ?> value) throws JSONException {
/* 1143 */     put(index, new JSONObject(value));
/* 1144 */     return this;
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
/*      */   public JSONArray put(int index, Object value) throws JSONException {
/* 1164 */     JSONObject.testValidity(value);
/* 1165 */     if (index < 0) {
/* 1166 */       throw new JSONException("JSONArray[" + index + "] not found.");
/*      */     }
/* 1168 */     if (index < length()) {
/* 1169 */       this.myArrayList.set(index, value);
/* 1170 */     } else if (index == length()) {
/*      */       
/* 1172 */       put(value);
/*      */     }
/*      */     else {
/*      */       
/* 1176 */       this.myArrayList.ensureCapacity(index + 1);
/* 1177 */       while (index != length()) {
/* 1178 */         put(JSONObject.NULL);
/*      */       }
/* 1180 */       put(value);
/*      */     } 
/* 1182 */     return this;
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
/* 1205 */     return query(new JSONPointer(jsonPointer));
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
/*      */   public Object query(JSONPointer jsonPointer) {
/* 1228 */     return jsonPointer.queryFrom(this);
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
/* 1240 */     return optQuery(new JSONPointer(jsonPointer));
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
/* 1253 */       return jsonPointer.queryFrom(this);
/* 1254 */     } catch (JSONPointerException e) {
/* 1255 */       return null;
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
/*      */   public Object remove(int index) {
/* 1268 */     return (index >= 0 && index < length()) ? 
/* 1269 */       this.myArrayList.remove(index) : 
/* 1270 */       null;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public boolean similar(Object other) {
/* 1281 */     if (!(other instanceof JSONArray)) {
/* 1282 */       return false;
/*      */     }
/* 1284 */     int len = length();
/* 1285 */     if (len != ((JSONArray)other).length()) {
/* 1286 */       return false;
/*      */     }
/* 1288 */     for (int i = 0; i < len; i++) {
/* 1289 */       Object valueThis = this.myArrayList.get(i);
/* 1290 */       Object valueOther = ((JSONArray)other).myArrayList.get(i);
/* 1291 */       if (valueThis != valueOther) {
/*      */ 
/*      */         
/* 1294 */         if (valueThis == null) {
/* 1295 */           return false;
/*      */         }
/* 1297 */         if (valueThis instanceof JSONObject) {
/* 1298 */           if (!((JSONObject)valueThis).similar(valueOther)) {
/* 1299 */             return false;
/*      */           }
/* 1301 */         } else if (valueThis instanceof JSONArray) {
/* 1302 */           if (!((JSONArray)valueThis).similar(valueOther)) {
/* 1303 */             return false;
/*      */           }
/* 1305 */         } else if (!valueThis.equals(valueOther)) {
/* 1306 */           return false;
/*      */         } 
/*      */       } 
/* 1309 */     }  return true;
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
/*      */   public JSONObject toJSONObject(JSONArray names) throws JSONException {
/* 1325 */     if (names == null || names.length() == 0 || length() == 0) {
/* 1326 */       return null;
/*      */     }
/* 1328 */     JSONObject jo = new JSONObject(names.length());
/* 1329 */     for (int i = 0; i < names.length(); i++) {
/* 1330 */       jo.put(names.getString(i), opt(i));
/*      */     }
/* 1332 */     return jo;
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
/*      */   public String toString() {
/*      */     try {
/* 1350 */       return toString(0);
/* 1351 */     } catch (Exception e) {
/* 1352 */       return null;
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
/*      */   public String toString(int indentFactor) throws JSONException {
/* 1384 */     StringWriter sw = new StringWriter();
/* 1385 */     synchronized (sw.getBuffer()) {
/* 1386 */       return write(sw, indentFactor, 0).toString();
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
/* 1401 */     return write(writer, 0, 0);
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
/*      */   
/*      */   public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
/*      */     try {
/* 1435 */       boolean commanate = false;
/* 1436 */       int length = length();
/* 1437 */       writer.write(91);
/*      */       
/* 1439 */       if (length == 1) {
/*      */         try {
/* 1441 */           JSONObject.writeValue(writer, this.myArrayList.get(0), indentFactor, indent);
/*      */         }
/* 1443 */         catch (Exception e) {
/* 1444 */           throw new JSONException("Unable to write JSONArray value at index: 0", e);
/*      */         } 
/* 1446 */       } else if (length != 0) {
/* 1447 */         int newindent = indent + indentFactor;
/*      */         
/* 1449 */         for (int i = 0; i < length; i++) {
/* 1450 */           if (commanate) {
/* 1451 */             writer.write(44);
/*      */           }
/* 1453 */           if (indentFactor > 0) {
/* 1454 */             writer.write(10);
/*      */           }
/* 1456 */           JSONObject.indent(writer, newindent);
/*      */           try {
/* 1458 */             JSONObject.writeValue(writer, this.myArrayList.get(i), indentFactor, newindent);
/*      */           }
/* 1460 */           catch (Exception e) {
/* 1461 */             throw new JSONException("Unable to write JSONArray value at index: " + i, e);
/*      */           } 
/* 1463 */           commanate = true;
/*      */         } 
/* 1465 */         if (indentFactor > 0) {
/* 1466 */           writer.write(10);
/*      */         }
/* 1468 */         JSONObject.indent(writer, indent);
/*      */       } 
/* 1470 */       writer.write(93);
/* 1471 */       return writer;
/* 1472 */     } catch (IOException e) {
/* 1473 */       throw new JSONException(e);
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
/*      */   public List<Object> toList() {
/* 1487 */     List<Object> results = new ArrayList(this.myArrayList.size());
/* 1488 */     for (Object element : this.myArrayList) {
/* 1489 */       if (element == null || JSONObject.NULL.equals(element)) {
/* 1490 */         results.add(null); continue;
/* 1491 */       }  if (element instanceof JSONArray) {
/* 1492 */         results.add(((JSONArray)element).toList()); continue;
/* 1493 */       }  if (element instanceof JSONObject) {
/* 1494 */         results.add(((JSONObject)element).toMap()); continue;
/*      */       } 
/* 1496 */       results.add(element);
/*      */     } 
/*      */     
/* 1499 */     return results;
/*      */   }
/*      */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\org\json\JSONArray.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */