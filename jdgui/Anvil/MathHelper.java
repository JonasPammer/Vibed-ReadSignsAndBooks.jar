/*     */ package Anvil;
/*     */ 
/*     */ import java.util.Random;
/*     */ 
/*     */ 
/*     */ 
/*     */ public class MathHelper
/*     */ {
/*   9 */   private static float[] SIN_TABLE = new float[65536];
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
/*     */   public static final float sin(float p_76126_0_) {
/*  25 */     return SIN_TABLE[(int)(p_76126_0_ * 10430.378F) & 0xFFFF];
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static final float cos(float p_76134_0_) {
/*  33 */     return SIN_TABLE[(int)(p_76134_0_ * 10430.378F + 16384.0F) & 0xFFFF];
/*     */   }
/*     */ 
/*     */   
/*     */   public static final float sqrt_float(float p_76129_0_) {
/*  38 */     return (float)Math.sqrt(p_76129_0_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static final float sqrt_double(double p_76133_0_) {
/*  43 */     return (float)Math.sqrt(p_76133_0_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int floor_float(float p_76141_0_) {
/*  51 */     int i = (int)p_76141_0_;
/*  52 */     return (p_76141_0_ < i) ? (i - 1) : i;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int truncateDoubleToInt(double p_76140_0_) {
/*  60 */     return (int)(p_76140_0_ + 1024.0D) - 1024;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int floor_double(double p_76128_0_) {
/*  68 */     int i = (int)p_76128_0_;
/*  69 */     return (p_76128_0_ < i) ? (i - 1) : i;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static long floor_double_long(double p_76124_0_) {
/*  77 */     long i = (long)p_76124_0_;
/*  78 */     return (p_76124_0_ < i) ? (i - 1L) : i;
/*     */   }
/*     */ 
/*     */   
/*     */   public static int func_154353_e(double p_154353_0_) {
/*  83 */     return (int)((p_154353_0_ >= 0.0D) ? p_154353_0_ : (-p_154353_0_ + 1.0D));
/*     */   }
/*     */ 
/*     */   
/*     */   public static float abs(float p_76135_0_) {
/*  88 */     return (p_76135_0_ >= 0.0F) ? p_76135_0_ : -p_76135_0_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int abs_int(int p_76130_0_) {
/*  96 */     return (p_76130_0_ >= 0) ? p_76130_0_ : -p_76130_0_;
/*     */   }
/*     */ 
/*     */   
/*     */   public static int ceiling_float_int(float p_76123_0_) {
/* 101 */     int i = (int)p_76123_0_;
/* 102 */     return (p_76123_0_ > i) ? (i + 1) : i;
/*     */   }
/*     */ 
/*     */   
/*     */   public static int ceiling_double_int(double p_76143_0_) {
/* 107 */     int i = (int)p_76143_0_;
/* 108 */     return (p_76143_0_ > i) ? (i + 1) : i;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int clamp_int(int p_76125_0_, int p_76125_1_, int p_76125_2_) {
/* 117 */     return (p_76125_0_ < p_76125_1_) ? p_76125_1_ : ((p_76125_0_ > p_76125_2_) ? p_76125_2_ : p_76125_0_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static float clamp_float(float p_76131_0_, float p_76131_1_, float p_76131_2_) {
/* 126 */     return (p_76131_0_ < p_76131_1_) ? p_76131_1_ : ((p_76131_0_ > p_76131_2_) ? p_76131_2_ : p_76131_0_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static double clamp_double(double p_151237_0_, double p_151237_2_, double p_151237_4_) {
/* 131 */     return (p_151237_0_ < p_151237_2_) ? p_151237_2_ : ((p_151237_0_ > p_151237_4_) ? p_151237_4_ : p_151237_0_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static double denormalizeClamp(double p_151238_0_, double p_151238_2_, double p_151238_4_) {
/* 136 */     return (p_151238_4_ < 0.0D) ? p_151238_0_ : ((p_151238_4_ > 1.0D) ? p_151238_2_ : (p_151238_0_ + (p_151238_2_ - p_151238_0_) * p_151238_4_));
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static double abs_max(double p_76132_0_, double p_76132_2_) {
/* 144 */     if (p_76132_0_ < 0.0D)
/*     */     {
/* 146 */       p_76132_0_ = -p_76132_0_;
/*     */     }
/*     */     
/* 149 */     if (p_76132_2_ < 0.0D)
/*     */     {
/* 151 */       p_76132_2_ = -p_76132_2_;
/*     */     }
/*     */     
/* 154 */     return (p_76132_0_ > p_76132_2_) ? p_76132_0_ : p_76132_2_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int bucketInt(int p_76137_0_, int p_76137_1_) {
/* 162 */     return (p_76137_0_ < 0) ? (-((-p_76137_0_ - 1) / p_76137_1_) - 1) : (p_76137_0_ / p_76137_1_);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static boolean stringNullOrLengthZero(String p_76139_0_) {
/* 170 */     return !(p_76139_0_ != null && p_76139_0_.length() != 0);
/*     */   }
/*     */ 
/*     */   
/*     */   public static int getRandomIntegerInRange(Random p_76136_0_, int p_76136_1_, int p_76136_2_) {
/* 175 */     return (p_76136_1_ >= p_76136_2_) ? p_76136_1_ : (p_76136_0_.nextInt(p_76136_2_ - p_76136_1_ + 1) + p_76136_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static float randomFloatClamp(Random p_151240_0_, float p_151240_1_, float p_151240_2_) {
/* 180 */     return (p_151240_1_ >= p_151240_2_) ? p_151240_1_ : (p_151240_0_.nextFloat() * (p_151240_2_ - p_151240_1_) + p_151240_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static double getRandomDoubleInRange(Random p_82716_0_, double p_82716_1_, double p_82716_3_) {
/* 185 */     return (p_82716_1_ >= p_82716_3_) ? p_82716_1_ : (p_82716_0_.nextDouble() * (p_82716_3_ - p_82716_1_) + p_82716_1_);
/*     */   }
/*     */ 
/*     */   
/*     */   public static double average(long[] p_76127_0_) {
/* 190 */     long i = 0L;
/* 191 */     long[] along1 = p_76127_0_;
/* 192 */     int j = p_76127_0_.length;
/*     */     
/* 194 */     for (int k = 0; k < j; k++) {
/*     */       
/* 196 */       long l = along1[k];
/* 197 */       i += l;
/*     */     } 
/*     */     
/* 200 */     return i / p_76127_0_.length;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static float wrapAngleTo180_float(float p_76142_0_) {
/* 208 */     p_76142_0_ %= 360.0F;
/*     */     
/* 210 */     if (p_76142_0_ >= 180.0F)
/*     */     {
/* 212 */       p_76142_0_ -= 360.0F;
/*     */     }
/*     */     
/* 215 */     if (p_76142_0_ < -180.0F)
/*     */     {
/* 217 */       p_76142_0_ += 360.0F;
/*     */     }
/*     */     
/* 220 */     return p_76142_0_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static double wrapAngleTo180_double(double p_76138_0_) {
/* 228 */     p_76138_0_ %= 360.0D;
/*     */     
/* 230 */     if (p_76138_0_ >= 180.0D)
/*     */     {
/* 232 */       p_76138_0_ -= 360.0D;
/*     */     }
/*     */     
/* 235 */     if (p_76138_0_ < -180.0D)
/*     */     {
/* 237 */       p_76138_0_ += 360.0D;
/*     */     }
/*     */     
/* 240 */     return p_76138_0_;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int parseIntWithDefault(String p_82715_0_, int p_82715_1_) {
/* 248 */     int j = p_82715_1_;
/*     */ 
/*     */     
/*     */     try {
/* 252 */       j = Integer.parseInt(p_82715_0_);
/*     */     }
/* 254 */     catch (Throwable throwable) {}
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 259 */     return j;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int parseIntWithDefaultAndMax(String p_82714_0_, int p_82714_1_, int p_82714_2_) {
/* 267 */     int k = p_82714_1_;
/*     */ 
/*     */     
/*     */     try {
/* 271 */       k = Integer.parseInt(p_82714_0_);
/*     */     }
/* 273 */     catch (Throwable throwable) {}
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 278 */     if (k < p_82714_2_)
/*     */     {
/* 280 */       k = p_82714_2_;
/*     */     }
/*     */     
/* 283 */     return k;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static double parseDoubleWithDefault(String p_82712_0_, double p_82712_1_) {
/* 291 */     double d1 = p_82712_1_;
/*     */ 
/*     */     
/*     */     try {
/* 295 */       d1 = Double.parseDouble(p_82712_0_);
/*     */     }
/* 297 */     catch (Throwable throwable) {}
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 302 */     return d1;
/*     */   }
/*     */ 
/*     */   
/*     */   public static double parseDoubleWithDefaultAndMax(String p_82713_0_, double p_82713_1_, double p_82713_3_) {
/* 307 */     double d2 = p_82713_1_;
/*     */ 
/*     */     
/*     */     try {
/* 311 */       d2 = Double.parseDouble(p_82713_0_);
/*     */     }
/* 313 */     catch (Throwable throwable) {}
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 318 */     if (d2 < p_82713_3_)
/*     */     {
/* 320 */       d2 = p_82713_3_;
/*     */     }
/*     */     
/* 323 */     return d2;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static int roundUpToPowerOfTwo(int p_151236_0_) {
/* 331 */     int j = p_151236_0_ - 1;
/* 332 */     j |= j >> 1;
/* 333 */     j |= j >> 2;
/* 334 */     j |= j >> 4;
/* 335 */     j |= j >> 8;
/* 336 */     j |= j >> 16;
/* 337 */     return j + 1;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static boolean isPowerOfTwo(int p_151235_0_) {
/* 345 */     return (p_151235_0_ != 0 && (p_151235_0_ & p_151235_0_ - 1) == 0);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static int calculateLogBaseTwoDeBruijn(int p_151241_0_) {
/* 355 */     p_151241_0_ = isPowerOfTwo(p_151241_0_) ? p_151241_0_ : roundUpToPowerOfTwo(p_151241_0_);
/* 356 */     return multiplyDeBruijnBitPosition[(int)(p_151241_0_ * 125613361L >> 27L) & 0x1F];
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
/*     */   public static int calculateLogBaseTwo(int p_151239_0_) {
/* 370 */     return calculateLogBaseTwoDeBruijn(p_151239_0_) - (isPowerOfTwo(p_151239_0_) ? 0 : 1);
/*     */   }
/*     */ 
/*     */   
/*     */   public static int func_154354_b(int p_154354_0_, int p_154354_1_) {
/* 375 */     if (p_154354_1_ == 0)
/*     */     {
/* 377 */       return 0;
/*     */     }
/*     */ 
/*     */     
/* 381 */     if (p_154354_0_ < 0)
/*     */     {
/* 383 */       p_154354_1_ *= -1;
/*     */     }
/*     */     
/* 386 */     int k = p_154354_0_ % p_154354_1_;
/* 387 */     return (k == 0) ? p_154354_0_ : (p_154354_0_ + p_154354_1_ - k);
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   static {
/* 393 */     for (int var0 = 0; var0 < 65536; var0++)
/*     */     {
/* 395 */       SIN_TABLE[var0] = (float)Math.sin(var0 * Math.PI * 2.0D / 65536.0D); } 
/*     */   }
/*     */   
/* 398 */   private static final int[] multiplyDeBruijnBitPosition = new int[] { 0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9 };
/*     */   private static final String __OBFID = "CL_00001496";
/*     */ }


/* Location:              O:\OneDrive\_Games\Minecraft\Jualmi Server\ReadSignsAndBooks.jar!\Anvil\MathHelper.class
 * Java compiler version: 7 (51.0)
 * JD-Core Version:       1.1.3
 */