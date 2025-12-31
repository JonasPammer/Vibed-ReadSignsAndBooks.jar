package viewers

import groovy.transform.CompileStatic
import javafx.animation.Animation
import javafx.animation.TranslateTransition
import javafx.scene.Node
import javafx.scene.effect.BlendMode
import javafx.scene.image.Image
import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Rectangle
import javafx.util.Duration

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Singleton manager for loading and caching Minecraft item textures.
 * Handles texture loading from JAR resources, scaling, and fallback placeholders.
 */
@CompileStatic
@Singleton
class IconManager {
    private static final Map<String, Image> iconCache = [:] as Map<String, Image>
    private static final int ICON_SIZE = 32
    private static final int ORIGINAL_SIZE = 16  // Minecraft texture size

    // Optional external texture source (user-provided Minecraft client jar / resource pack zip / extracted dir)
    private static File externalTextureSource = null
    private static ZipFile externalZip = null

    /**
     * Get an icon image for a Minecraft item ID.
     * Loads from resources if available, otherwise generates a colored placeholder.
     *
     * @param itemId The Minecraft item ID (e.g., "minecraft:diamond_pickaxe")
     * @return The icon image (32x32)
     */
    static Image getIcon(String itemId) {
        if (iconCache.containsKey(itemId)) {
            return iconCache[itemId]
        }

        // Try to load from external texture source (if configured)
        Image external = tryLoadExternalIcon(itemId)
        if (external) {
            iconCache[itemId] = external
            return external
        }

        // Try to load from resources
        String resourceName = itemId.replace('minecraft:', '')
        String path = "/textures/item/${resourceName}.png"
        InputStream stream = IconManager.class.getResourceAsStream(path)

        if (stream) {
            try {
                // Load and scale with nearest neighbor (preserves pixel art)
                Image original = new Image(stream, ICON_SIZE, ICON_SIZE, false, false)
                iconCache[itemId] = original
                return original
            } catch (Exception e) {
                // Failed to load, fall through to placeholder
            } finally {
                stream.close()
            }
        }

        // Fallback: generate colored placeholder
        return generatePlaceholder(itemId)
    }

    /**
     * Configure an external texture source (Minecraft client jar/zip/resource pack folder).
     *
     * This does NOT ship any Minecraft assets; it only reads from a user-selected local file/folder.
     *
     * @return true if source was accepted, false otherwise.
     */
    static boolean setExternalTextureSource(File source) {
        clearExternalTextureSource()

        if (!source || !source.exists()) {
            return false
        }

        if (source.directory) {
            externalTextureSource = source
            clearCache()
            return true
        }

        if (source.file) {
            try {
                externalZip = new ZipFile(source)
                externalTextureSource = source
                clearCache()
                return true
            } catch (Exception e) {
                // Ensure cleanup on failure
                clearExternalTextureSource()
                return false
            }
        }

        return false
    }

    /**
     * Clear configured external texture source.
     */
    static void clearExternalTextureSource() {
        if (externalZip != null) {
            try {
                externalZip.close()
            } catch (Exception ignored) {
                // ignore
            }
        }
        externalZip = null
        externalTextureSource = null
        clearCache()
    }

    /**
     * Human-readable label for the currently active texture source.
     */
    static String getTextureSourceLabel() {
        if (externalTextureSource == null) {
            return 'Built-in placeholders'
        }
        return externalTextureSource.name
    }

    private static Image tryLoadExternalIcon(String itemId) {
        if (externalTextureSource == null) {
            return null
        }

        String resourceName = itemId.replace('minecraft:', '')

        // Try item texture first, then block texture as fallback for block-items
        String[] candidatePaths = [
            "assets/minecraft/textures/item/${resourceName}.png",
            "assets/minecraft/textures/block/${resourceName}.png"
        ] as String[]

        for (String relPath : candidatePaths) {
            InputStream stream = null
            try {
                stream = openExternalStream(relPath)
                if (stream != null) {
                    return new Image(stream, ICON_SIZE, ICON_SIZE, false, false)
                }
            } catch (Exception ignored) {
                // ignore and try next
            } finally {
                try {
                    stream?.close()
                } catch (Exception ignored2) {
                    // ignore
                }
            }
        }

        return null
    }

    private static InputStream openExternalStream(String relPath) {
        if (externalTextureSource == null) {
            return null
        }

        // Zip/jar
        if (externalZip != null) {
            ZipEntry entry = externalZip.getEntry(relPath)
            if (entry != null) {
                return externalZip.getInputStream(entry)
            }
            return null
        }

        // Directory
        if (externalTextureSource.directory) {
            File f = new File(externalTextureSource, relPath)
            if (f.exists() && f.file) {
                return f.newInputStream()
            }
        }

        return null
    }

    /**
     * Generate a colored placeholder square for items without textures.
     *
     * @param itemId The Minecraft item ID
     * @return A colored square image
     */
    private static Image generatePlaceholder(String itemId) {
        WritableImage img = new WritableImage(ICON_SIZE, ICON_SIZE)
        PixelWriter pw = img.pixelWriter

        Color color = getColorForItem(itemId)
        Color borderColor = color.darker()

        // Fill with color and add border
        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                // 2px border
                if (x < 2 || x >= ICON_SIZE - 2 || y < 2 || y >= ICON_SIZE - 2) {
                    pw.setColor(x, y, borderColor)
                } else {
                    pw.setColor(x, y, color)
                }
            }
        }

        iconCache[itemId] = img
        return img
    }

    /**
     * Map item IDs to representative colors for placeholder generation.
     * Uses category-based matching for broad coverage.
     *
     * @param itemId The Minecraft item ID
     * @return A color representing the item category
     */
    private static Color getColorForItem(String itemId) {
        String id = itemId.toLowerCase()

        // Ores and minerals
        if (id.contains('diamond')) return Color.CYAN
        if (id.contains('emerald')) return Color.LIMEGREEN
        if (id.contains('gold')) return Color.GOLD
        if (id.contains('iron')) return Color.LIGHTGRAY
        if (id.contains('netherite')) return Color.rgb(68, 58, 59)
        if (id.contains('redstone')) return Color.RED
        if (id.contains('lapis')) return Color.BLUE
        if (id.contains('coal')) return Color.rgb(40, 40, 40)
        if (id.contains('copper')) return Color.rgb(184, 115, 51)
        if (id.contains('quartz')) return Color.WHITE
        if (id.contains('amethyst')) return Color.rgb(158, 100, 181)

        // Wood types
        if (id.contains('oak')) return Color.rgb(162, 130, 78)
        if (id.contains('birch')) return Color.rgb(216, 204, 154)
        if (id.contains('spruce')) return Color.rgb(114, 84, 48)
        if (id.contains('jungle')) return Color.rgb(160, 115, 80)
        if (id.contains('acacia')) return Color.rgb(168, 90, 50)
        if (id.contains('dark_oak')) return Color.rgb(66, 43, 20)
        if (id.contains('mangrove')) return Color.rgb(117, 54, 48)
        if (id.contains('cherry')) return Color.rgb(227, 182, 176)
        if (id.contains('bamboo')) return Color.rgb(199, 177, 52)
        if (id.contains('crimson')) return Color.rgb(101, 32, 50)
        if (id.contains('warped')) return Color.rgb(43, 104, 99)
        if (id.contains('wood') || id.contains('planks')) return Color.BURLYWOOD

        // Stone and building materials
        if (id.contains('stone') && !id.contains('blackstone')) return Color.GRAY
        if (id.contains('blackstone')) return Color.rgb(42, 35, 40)
        if (id.contains('deepslate')) return Color.rgb(100, 100, 100)
        if (id.contains('granite')) return Color.rgb(150, 90, 77)
        if (id.contains('diorite')) return Color.rgb(200, 200, 200)
        if (id.contains('andesite')) return Color.rgb(132, 135, 132)
        if (id.contains('brick')) return Color.rgb(150, 97, 83)
        if (id.contains('concrete')) return Color.LIGHTGRAY
        if (id.contains('terracotta')) return Color.rgb(152, 94, 67)
        if (id.contains('sandstone')) return Color.rgb(226, 224, 164)
        if (id.contains('prismarine')) return Color.rgb(99, 171, 158)

        // Natural blocks
        if (id.contains('dirt')) return Color.SADDLEBROWN
        if (id.contains('grass')) return Color.rgb(124, 176, 70)
        if (id.contains('sand')) return Color.rgb(237, 230, 166)
        if (id.contains('gravel')) return Color.rgb(132, 125, 125)
        if (id.contains('snow')) return Color.WHITE
        if (id.contains('ice')) return Color.rgb(158, 207, 255)
        if (id.contains('clay')) return Color.rgb(160, 166, 179)
        if (id.contains('moss')) return Color.rgb(89, 109, 45)
        if (id.contains('mycelium')) return Color.rgb(111, 99, 105)

        // Nether materials
        if (id.contains('netherrack')) return Color.rgb(111, 54, 52)
        if (id.contains('nether_brick')) return Color.rgb(44, 21, 26)
        if (id.contains('soul')) return Color.rgb(82, 68, 54)
        if (id.contains('basalt')) return Color.rgb(74, 71, 73)
        if (id.contains('glowstone')) return Color.rgb(255, 207, 154)
        if (id.contains('magma')) return Color.rgb(147, 58, 31)

        // End materials
        if (id.contains('end_stone')) return Color.rgb(221, 223, 165)
        if (id.contains('purpur')) return Color.rgb(169, 125, 169)
        if (id.contains('chorus')) return Color.rgb(96, 69, 96)

        // Organic materials
        if (id.contains('leather')) return Color.rgb(198, 123, 72)
        if (id.contains('wool')) return Color.WHITE
        if (id.contains('string')) return Color.WHITE
        if (id.contains('feather')) return Color.WHITE
        if (id.contains('slime')) return Color.rgb(112, 192, 91)
        if (id.contains('honey')) return Color.rgb(255, 165, 0)

        // Food items
        if (id.contains('bread')) return Color.rgb(217, 163, 107)
        if (id.contains('apple')) return Color.RED
        if (id.contains('carrot')) return Color.ORANGE
        if (id.contains('potato')) return Color.rgb(230, 200, 130)
        if (id.contains('wheat')) return Color.rgb(214, 181, 86)
        if (id.contains('beef') || id.contains('porkchop') || id.contains('mutton')) return Color.rgb(139, 69, 69)
        if (id.contains('fish') || id.contains('salmon') || id.contains('cod')) return Color.rgb(70, 130, 180)

        // Tools and weapons (by material if not caught above)
        if (id.contains('sword') || id.contains('axe') || id.contains('pickaxe') ||
            id.contains('shovel') || id.contains('hoe')) {
            if (id.contains('wooden')) return Color.BURLYWOOD
            if (id.contains('stone')) return Color.GRAY
            return Color.LIGHTGRAY  // Default for tools
        }

        // Armor
        if (id.contains('helmet') || id.contains('chestplate') ||
            id.contains('leggings') || id.contains('boots')) {
            return Color.LIGHTGRAY
        }

        // Dyes
        if (id.contains('white_dye')) return Color.WHITE
        if (id.contains('black_dye')) return Color.BLACK
        if (id.contains('red_dye')) return Color.RED
        if (id.contains('green_dye')) return Color.GREEN
        if (id.contains('blue_dye')) return Color.BLUE
        if (id.contains('yellow_dye')) return Color.YELLOW
        if (id.contains('purple_dye')) return Color.PURPLE
        if (id.contains('orange_dye')) return Color.ORANGE
        if (id.contains('pink_dye')) return Color.PINK
        if (id.contains('lime_dye')) return Color.LIME
        if (id.contains('cyan_dye')) return Color.CYAN
        if (id.contains('magenta_dye')) return Color.MAGENTA
        if (id.contains('light_blue_dye')) return Color.LIGHTBLUE
        if (id.contains('light_gray_dye')) return Color.LIGHTGRAY
        if (id.contains('gray_dye')) return Color.GRAY
        if (id.contains('brown_dye')) return Color.rgb(139, 69, 19)

        // Special items
        if (id.contains('ender')) return Color.rgb(20, 142, 129)
        if (id.contains('blaze')) return Color.rgb(255, 170, 0)
        if (id.contains('bone')) return Color.WHITE
        if (id.contains('spider')) return Color.rgb(52, 52, 52)
        if (id.contains('gunpowder')) return Color.rgb(72, 72, 72)
        if (id.contains('paper')) return Color.WHITE
        if (id.contains('book')) return Color.rgb(139, 69, 19)

        // Default gray for unknown items
        return Color.SLATEGRAY
    }

    /**
     * Create an enchanted glint overlay effect for items.
     * Returns a Node that can be added as an overlay to an ImageView.
     *
     * @return An animated enchantment glint Node
     */
    static Node createEnchantGlint() {
        Rectangle glint = new Rectangle(ICON_SIZE, ICON_SIZE)

        // Purple/magenta gradient that sweeps across
        glint.fill = new LinearGradient(
            0, 0, 1, 1, true, CycleMethod.REPEAT,
            new Stop(0, Color.TRANSPARENT),
            new Stop(0.4, Color.TRANSPARENT),
            new Stop(0.5, Color.rgb(128, 64, 255, 0.3)),
            new Stop(0.6, Color.TRANSPARENT),
            new Stop(1, Color.TRANSPARENT)
        )
        glint.blendMode = BlendMode.SCREEN

        // Animate the glint sweeping across
        TranslateTransition tt = new TranslateTransition(Duration.seconds(2), glint)
        tt.fromX = -ICON_SIZE
        tt.toX = ICON_SIZE
        tt.cycleCount = Animation.INDEFINITE
        tt.play()

        return glint
    }

    /**
     * Create an isometric block icon from a top face texture.
     * Useful for rendering blocks in item form.
     *
     * @param blockId The Minecraft block ID
     * @return The isometric block icon (32x32)
     */
    static Image getBlockIcon(String blockId) {
        // For now, use the same logic as items
        // In the future, this could render an isometric cube
        return getIcon(blockId)
    }

    /**
     * Clear the icon cache to free memory.
     * Useful when switching between large datasets.
     */
    static void clearCache() {
        iconCache.clear()
    }

    /**
     * Get the current cache size (number of cached icons).
     *
     * @return Number of icons in cache
     */
    static int getCacheSize() {
        return iconCache.size()
    }

    /**
     * Preload commonly used icons for better performance.
     * Call this on application startup.
     */
    static void preloadCommonIcons() {
        List<String> commonItems = [
            'minecraft:diamond',
            'minecraft:emerald',
            'minecraft:gold_ingot',
            'minecraft:iron_ingot',
            'minecraft:netherite_ingot',
            'minecraft:diamond_sword',
            'minecraft:diamond_pickaxe',
            'minecraft:enchanted_book',
            'minecraft:written_book',
            'minecraft:book',
            'minecraft:chest',
            'minecraft:shulker_box',
            'minecraft:ender_chest'
        ]

        commonItems.each { itemId ->
            getIcon(itemId)  // Load into cache
        }
    }
}
