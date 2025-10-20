/*    */ package Anvil;
/*    */ 
/*    */ import java.util.regex.Pattern;
/*    */ 
/*    */ public class StringUtils
/*    */ {
/*  7 */   private static final Pattern patternControlCode = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
/*    */ 
/*    */   
/*    */   private static final String __OBFID = "CL_00001501";
/*    */ 
/*    */ 
/*    */   
/*    */   public static String ticksToElapsedTime(int p_76337_0_) {
/* 15 */     int j = p_76337_0_ / 20;
/* 16 */     int k = j / 60;
/* 17 */     j %= 60;
/* 18 */     return (j < 10) ? (String.valueOf(k) + ":0" + j) : (String.valueOf(k) + ":" + j);
/*    */   }
/*    */ 
/*    */   
/*    */   public static String stripControlCodes(String p_76338_0_) {
/* 23 */     return patternControlCode.matcher(p_76338_0_).replaceAll("");
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public static boolean isNullOrEmpty(String p_151246_0_) {
/* 31 */     return !(p_151246_0_ != null && !"".equals(p_151246_0_));
/*    */   }
/*    */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\StringUtils.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */