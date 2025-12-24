package viewers

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.util.Duration

/**
 * Utility for rendering Minecraft formatting codes in JavaFX.
 *
 * Supports all Minecraft text formatting:
 * - Color codes (§0-§f)
 * - Style codes (§l=bold, §o=italic, §n=underline, §m=strikethrough, §k=obfuscated)
 * - Reset code (§r)
 *
 * Usage:
 *   TextFlow flow = MinecraftTextRenderer.render("§aGreen §lBold §rNormal")
 *   String plain = MinecraftTextRenderer.stripFormatting("§cRed text")
 */
class MinecraftTextRenderer {

    /**
     * Official Minecraft color code mappings (§0-§f)
     * Source: https://minecraft.wiki/w/Formatting_codes
     */
    static final Map<String, Color> COLORS = [
        '0': Color.web('#000000'),  // Black
        '1': Color.web('#0000AA'),  // Dark Blue
        '2': Color.web('#00AA00'),  // Dark Green
        '3': Color.web('#00AAAA'),  // Dark Aqua
        '4': Color.web('#AA0000'),  // Dark Red
        '5': Color.web('#AA00AA'),  // Dark Purple
        '6': Color.web('#FFAA00'),  // Gold
        '7': Color.web('#AAAAAA'),  // Gray
        '8': Color.web('#555555'),  // Dark Gray
        '9': Color.web('#5555FF'),  // Blue
        'a': Color.web('#55FF55'),  // Green
        'b': Color.web('#55FFFF'),  // Aqua
        'c': Color.web('#FF5555'),  // Red
        'd': Color.web('#FF55FF'),  // Light Purple
        'e': Color.web('#FFFF55'),  // Yellow
        'f': Color.web('#FFFFFF')   // White
    ]

    /**
     * Parse Minecraft-formatted text and return a JavaFX TextFlow with styled Text nodes.
     *
     * @param formattedText Text with Minecraft formatting codes (§ followed by code character)
     * @return TextFlow containing styled Text nodes
     */
    static TextFlow render(String formattedText) {
        TextFlow flow = new TextFlow()

        // Current formatting state
        Color currentColor = Color.WHITE
        boolean bold = false
        boolean italic = false
        boolean underline = false
        boolean strikethrough = false
        boolean obfuscated = false

        StringBuilder currentText = new StringBuilder()

        int i = 0
        while (i < formattedText.length()) {
            char c = formattedText.charAt(i)

            // Check for § format code
            if (c == '§' && i + 1 < formattedText.length()) {
                // Flush current text segment before applying new formatting
                if (currentText.length() > 0) {
                    flow.children.add(createTextNode(currentText.toString(),
                        currentColor, bold, italic, underline, strikethrough, obfuscated))
                    currentText.setLength(0)
                }

                char code = formattedText.charAt(i + 1).toLowerCase()

                // Color codes (§0-§f)
                if (COLORS.containsKey(code.toString())) {
                    currentColor = COLORS[code.toString()]
                    // Color codes reset style formatting in Minecraft
                    bold = false
                    italic = false
                    underline = false
                    strikethrough = false
                    obfuscated = false
                }
                // Style codes
                else if (code == 'l') {
                    bold = true
                } else if (code == 'o') {
                    italic = true
                } else if (code == 'n') {
                    underline = true
                } else if (code == 'm') {
                    strikethrough = true
                } else if (code == 'k') {
                    obfuscated = true
                } else if (code == 'r') {
                    // Reset all formatting
                    currentColor = Color.WHITE
                    bold = false
                    italic = false
                    underline = false
                    strikethrough = false
                    obfuscated = false
                }

                i += 2  // Skip the § and code character
            } else {
                currentText.append(c)
                i++
            }
        }

        // Flush remaining text
        if (currentText.length() > 0) {
            flow.children.add(createTextNode(currentText.toString(),
                currentColor, bold, italic, underline, strikethrough, obfuscated))
        }

        return flow
    }

    /**
     * Create a styled Text node with the specified formatting.
     */
    private static Text createTextNode(String text, Color color, boolean bold,
                                       boolean italic, boolean underline, boolean strikethrough, boolean obfuscated) {
        Text node = new Text(text)
        node.fill = color

        // Build font with weight and posture
        FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL
        FontPosture posture = italic ? FontPosture.ITALIC : FontPosture.REGULAR
        node.font = Font.font("Consolas", weight, posture, 14)

        node.underline = underline
        node.strikethrough = strikethrough

        // Handle obfuscated text with animation
        if (obfuscated) {
            startObfuscatedAnimation(node, text.length())
        }

        return node
    }

    /**
     * Start obfuscated text animation (§k).
     * Continuously replaces characters with random alphanumeric/symbol characters.
     */
    private static void startObfuscatedAnimation(Text node, int length) {
        final String chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*'
        final Random random = new Random()

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(50), { event ->
            StringBuilder sb = new StringBuilder()
            length.times {
                sb.append(chars.charAt(random.nextInt(chars.length())))
            }
            node.text = sb.toString()
        }))
        timeline.cycleCount = Animation.INDEFINITE
        timeline.play()

        // Store timeline reference in userData for potential cleanup
        node.userData = timeline
    }

    /**
     * Remove all Minecraft formatting codes from text.
     *
     * @param text Text with formatting codes
     * @return Plain text without any § codes
     */
    static String stripFormatting(String text) {
        if (text == null) {
            return null
        }
        // Remove all § followed by valid formatting code (0-9, a-f, k-o, r)
        return text.replaceAll('(?i)§[0-9a-fk-or]', '')
    }

    /**
     * Stop any obfuscated animations in a TextFlow.
     * Call this when removing TextFlow from scene to prevent memory leaks.
     *
     * @param flow TextFlow potentially containing obfuscated text
     */
    static void stopAnimations(TextFlow flow) {
        flow.children.each { node ->
            if (node instanceof Text && node.userData instanceof Timeline) {
                ((Timeline) node.userData).stop()
            }
        }
    }
}
