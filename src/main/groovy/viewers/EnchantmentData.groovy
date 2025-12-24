package viewers

/**
 * Complete Minecraft enchantment database with formatting utilities.
 *
 * Provides:
 * - All vanilla enchantments with max levels
 * - Roman numeral conversion
 * - Enchantment categories and metadata
 * - Display name formatting
 *
 * @since 1.0.0
 */
class EnchantmentData {

    /**
     * Enchantment metadata container
     */
    static class Enchantment {
        String id
        String displayName
        int maxLevel
        String category
        List<String> applicableTo

        Enchantment(Map args) {
            this.id = args.id
            this.displayName = args.displayName
            this.maxLevel = args.maxLevel
            this.category = args.category
            this.applicableTo = args.applicableTo
        }
    }

    /**
     * Complete enchantment database
     * Updated for Minecraft 1.21+ (includes all vanilla enchantments)
     */
    static final Map<String, Enchantment> ENCHANTMENTS = [
        // ==================== WEAPON ENCHANTMENTS ====================
        'sharpness': new Enchantment(
            id: 'sharpness',
            displayName: 'Sharpness',
            maxLevel: 5,
            category: 'weapon',
            applicableTo: ['sword', 'axe']
        ),
        'smite': new Enchantment(
            id: 'smite',
            displayName: 'Smite',
            maxLevel: 5,
            category: 'weapon',
            applicableTo: ['sword', 'axe']
        ),
        'bane_of_arthropods': new Enchantment(
            id: 'bane_of_arthropods',
            displayName: 'Bane of Arthropods',
            maxLevel: 5,
            category: 'weapon',
            applicableTo: ['sword', 'axe']
        ),
        'fire_aspect': new Enchantment(
            id: 'fire_aspect',
            displayName: 'Fire Aspect',
            maxLevel: 2,
            category: 'weapon',
            applicableTo: ['sword']
        ),
        'knockback': new Enchantment(
            id: 'knockback',
            displayName: 'Knockback',
            maxLevel: 2,
            category: 'weapon',
            applicableTo: ['sword']
        ),
        'looting': new Enchantment(
            id: 'looting',
            displayName: 'Looting',
            maxLevel: 3,
            category: 'weapon',
            applicableTo: ['sword']
        ),
        'sweeping': new Enchantment(
            id: 'sweeping',
            displayName: 'Sweeping Edge',
            maxLevel: 3,
            category: 'weapon',
            applicableTo: ['sword']
        ),
        'sweeping_edge': new Enchantment(
            id: 'sweeping_edge',
            displayName: 'Sweeping Edge',
            maxLevel: 3,
            category: 'weapon',
            applicableTo: ['sword']
        ),

        // ==================== BOW ENCHANTMENTS ====================
        'power': new Enchantment(
            id: 'power',
            displayName: 'Power',
            maxLevel: 5,
            category: 'bow',
            applicableTo: ['bow']
        ),
        'punch': new Enchantment(
            id: 'punch',
            displayName: 'Punch',
            maxLevel: 2,
            category: 'bow',
            applicableTo: ['bow']
        ),
        'flame': new Enchantment(
            id: 'flame',
            displayName: 'Flame',
            maxLevel: 1,
            category: 'bow',
            applicableTo: ['bow']
        ),
        'infinity': new Enchantment(
            id: 'infinity',
            displayName: 'Infinity',
            maxLevel: 1,
            category: 'bow',
            applicableTo: ['bow']
        ),

        // ==================== CROSSBOW ENCHANTMENTS ====================
        'quick_charge': new Enchantment(
            id: 'quick_charge',
            displayName: 'Quick Charge',
            maxLevel: 3,
            category: 'crossbow',
            applicableTo: ['crossbow']
        ),
        'multishot': new Enchantment(
            id: 'multishot',
            displayName: 'Multishot',
            maxLevel: 1,
            category: 'crossbow',
            applicableTo: ['crossbow']
        ),
        'piercing': new Enchantment(
            id: 'piercing',
            displayName: 'Piercing',
            maxLevel: 4,
            category: 'crossbow',
            applicableTo: ['crossbow']
        ),

        // ==================== TRIDENT ENCHANTMENTS ====================
        'loyalty': new Enchantment(
            id: 'loyalty',
            displayName: 'Loyalty',
            maxLevel: 3,
            category: 'trident',
            applicableTo: ['trident']
        ),
        'channeling': new Enchantment(
            id: 'channeling',
            displayName: 'Channeling',
            maxLevel: 1,
            category: 'trident',
            applicableTo: ['trident']
        ),
        'riptide': new Enchantment(
            id: 'riptide',
            displayName: 'Riptide',
            maxLevel: 3,
            category: 'trident',
            applicableTo: ['trident']
        ),
        'impaling': new Enchantment(
            id: 'impaling',
            displayName: 'Impaling',
            maxLevel: 5,
            category: 'trident',
            applicableTo: ['trident']
        ),

        // ==================== TOOL ENCHANTMENTS ====================
        'efficiency': new Enchantment(
            id: 'efficiency',
            displayName: 'Efficiency',
            maxLevel: 5,
            category: 'tool',
            applicableTo: ['pickaxe', 'shovel', 'axe', 'hoe']
        ),
        'silk_touch': new Enchantment(
            id: 'silk_touch',
            displayName: 'Silk Touch',
            maxLevel: 1,
            category: 'tool',
            applicableTo: ['pickaxe', 'shovel', 'axe', 'hoe']
        ),
        'fortune': new Enchantment(
            id: 'fortune',
            displayName: 'Fortune',
            maxLevel: 3,
            category: 'tool',
            applicableTo: ['pickaxe', 'shovel', 'axe', 'hoe']
        ),

        // ==================== ARMOR ENCHANTMENTS ====================
        'protection': new Enchantment(
            id: 'protection',
            displayName: 'Protection',
            maxLevel: 4,
            category: 'armor',
            applicableTo: ['helmet', 'chestplate', 'leggings', 'boots']
        ),
        'fire_protection': new Enchantment(
            id: 'fire_protection',
            displayName: 'Fire Protection',
            maxLevel: 4,
            category: 'armor',
            applicableTo: ['helmet', 'chestplate', 'leggings', 'boots']
        ),
        'blast_protection': new Enchantment(
            id: 'blast_protection',
            displayName: 'Blast Protection',
            maxLevel: 4,
            category: 'armor',
            applicableTo: ['helmet', 'chestplate', 'leggings', 'boots']
        ),
        'projectile_protection': new Enchantment(
            id: 'projectile_protection',
            displayName: 'Projectile Protection',
            maxLevel: 4,
            category: 'armor',
            applicableTo: ['helmet', 'chestplate', 'leggings', 'boots']
        ),
        'thorns': new Enchantment(
            id: 'thorns',
            displayName: 'Thorns',
            maxLevel: 3,
            category: 'armor',
            applicableTo: ['helmet', 'chestplate', 'leggings', 'boots']
        ),
        'feather_falling': new Enchantment(
            id: 'feather_falling',
            displayName: 'Feather Falling',
            maxLevel: 4,
            category: 'armor',
            applicableTo: ['boots']
        ),
        'depth_strider': new Enchantment(
            id: 'depth_strider',
            displayName: 'Depth Strider',
            maxLevel: 3,
            category: 'armor',
            applicableTo: ['boots']
        ),
        'frost_walker': new Enchantment(
            id: 'frost_walker',
            displayName: 'Frost Walker',
            maxLevel: 2,
            category: 'armor',
            applicableTo: ['boots']
        ),
        'soul_speed': new Enchantment(
            id: 'soul_speed',
            displayName: 'Soul Speed',
            maxLevel: 3,
            category: 'armor',
            applicableTo: ['boots']
        ),
        'swift_sneak': new Enchantment(
            id: 'swift_sneak',
            displayName: 'Swift Sneak',
            maxLevel: 3,
            category: 'armor',
            applicableTo: ['leggings']
        ),
        'respiration': new Enchantment(
            id: 'respiration',
            displayName: 'Respiration',
            maxLevel: 3,
            category: 'armor',
            applicableTo: ['helmet']
        ),
        'aqua_affinity': new Enchantment(
            id: 'aqua_affinity',
            displayName: 'Aqua Affinity',
            maxLevel: 1,
            category: 'armor',
            applicableTo: ['helmet']
        ),

        // ==================== FISHING ENCHANTMENTS ====================
        'luck_of_the_sea': new Enchantment(
            id: 'luck_of_the_sea',
            displayName: 'Luck of the Sea',
            maxLevel: 3,
            category: 'fishing',
            applicableTo: ['fishing_rod']
        ),
        'lure': new Enchantment(
            id: 'lure',
            displayName: 'Lure',
            maxLevel: 3,
            category: 'fishing',
            applicableTo: ['fishing_rod']
        ),

        // ==================== UNIVERSAL ENCHANTMENTS ====================
        'unbreaking': new Enchantment(
            id: 'unbreaking',
            displayName: 'Unbreaking',
            maxLevel: 3,
            category: 'universal',
            applicableTo: ['all']
        ),
        'mending': new Enchantment(
            id: 'mending',
            displayName: 'Mending',
            maxLevel: 1,
            category: 'universal',
            applicableTo: ['all']
        ),

        // ==================== CURSE ENCHANTMENTS ====================
        'vanishing_curse': new Enchantment(
            id: 'vanishing_curse',
            displayName: 'Curse of Vanishing',
            maxLevel: 1,
            category: 'curse',
            applicableTo: ['all']
        ),
        'binding_curse': new Enchantment(
            id: 'binding_curse',
            displayName: 'Curse of Binding',
            maxLevel: 1,
            category: 'curse',
            applicableTo: ['armor']
        ),
        'curse_of_vanishing': new Enchantment(
            id: 'curse_of_vanishing',
            displayName: 'Curse of Vanishing',
            maxLevel: 1,
            category: 'curse',
            applicableTo: ['all']
        ),
        'curse_of_binding': new Enchantment(
            id: 'curse_of_binding',
            displayName: 'Curse of Binding',
            maxLevel: 1,
            category: 'curse',
            applicableTo: ['armor']
        )
    ].asImmutable()

    /**
     * Roman numeral conversion table (1-10)
     */
    static final String[] ROMAN_NUMERALS = [
        '',     // 0 (placeholder)
        'I',    // 1
        'II',   // 2
        'III',  // 3
        'IV',   // 4
        'V',    // 5
        'VI',   // 6
        'VII',  // 7
        'VIII', // 8
        'IX',   // 9
        'X'     // 10
    ]

    /**
     * Convert integer level to Roman numeral.
     *
     * @param level Enchantment level (1-10)
     * @return Roman numeral string (e.g., "III" for 3)
     */
    static String toRoman(int level) {
        if (level <= 0) return ''
        if (level <= 10) return ROMAN_NUMERALS[level]
        return level.toString() // Fallback to numeric for > 10
    }

    /**
     * Get display name for enchantment ID.
     * Falls back to capitalized ID if not in database.
     *
     * @param enchantId Enchantment ID (e.g., "sharpness", "minecraft:sharpness")
     * @return Display name (e.g., "Sharpness")
     */
    static String getDisplayName(String enchantId) {
        // Strip minecraft: namespace if present
        String cleanId = enchantId.replaceFirst(/^minecraft:/, '')

        Enchantment ench = ENCHANTMENTS[cleanId]
        if (ench) {
            return ench.displayName
        }

        // Fallback: capitalize and replace underscores
        return cleanId.split('_').collect { it.capitalize() }.join(' ')
    }

    /**
     * Format enchantment with level.
     *
     * @param enchantId Enchantment ID
     * @param level Enchantment level
     * @return Formatted string (e.g., "Sharpness V")
     */
    static String formatEnchantment(String enchantId, int level) {
        String displayName = getDisplayName(enchantId)
        if (level <= 1) {
            // Only show level for > 1 (Mending, Silk Touch, etc. don't need "I")
            String cleanId = enchantId.replaceFirst(/^minecraft:/, '')
            Enchantment ench = ENCHANTMENTS[cleanId]
            if (ench?.maxLevel == 1) {
                return displayName
            }
        }
        return "${displayName} ${toRoman(level)}".trim()
    }

    /**
     * Format multiple enchantments into a list of strings.
     *
     * @param enchantments Map of enchantment IDs to levels
     * @return List of formatted enchantment strings
     */
    static List<String> formatEnchantments(Map<String, Integer> enchantments) {
        if (!enchantments) return []
        return enchantments.collect { k, v -> formatEnchantment(k, v) }
    }

    /**
     * Check if enchantment is a curse.
     *
     * @param enchantId Enchantment ID
     * @return True if curse
     */
    static boolean isCurse(String enchantId) {
        String cleanId = enchantId.replaceFirst(/^minecraft:/, '')
        return ENCHANTMENTS[cleanId]?.category == 'curse'
    }

    /**
     * Get enchantment category.
     *
     * @param enchantId Enchantment ID
     * @return Category string (weapon, armor, tool, curse, etc.)
     */
    static String getCategory(String enchantId) {
        String cleanId = enchantId.replaceFirst(/^minecraft:/, '')
        return ENCHANTMENTS[cleanId]?.category ?: 'unknown'
    }

    /**
     * Get maximum level for enchantment.
     *
     * @param enchantId Enchantment ID
     * @return Max level (defaults to 1 if unknown)
     */
    static int getMaxLevel(String enchantId) {
        String cleanId = enchantId.replaceFirst(/^minecraft:/, '')
        return ENCHANTMENTS[cleanId]?.maxLevel ?: 1
    }

    /**
     * Check if enchantment is applicable to item type.
     *
     * @param enchantId Enchantment ID
     * @param itemType Item type (e.g., "sword", "helmet")
     * @return True if applicable
     */
    static boolean isApplicableTo(String enchantId, String itemType) {
        String cleanId = enchantId.replaceFirst(/^minecraft:/, '')
        Enchantment ench = ENCHANTMENTS[cleanId]
        if (!ench) return false
        return ench.applicableTo.contains('all') || ench.applicableTo.contains(itemType)
    }
}
