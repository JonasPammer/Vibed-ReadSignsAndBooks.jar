/**
 * Minecraft datapack structure generation utilities.
 *
 * Generates complete, ready-to-use Minecraft datapacks with proper structure:
 * - pack.mcmeta with correct pack_format for each version
 * - Proper directory structure (function/ vs functions/ based on version)
 * - Namespace folder organization
 *
 * CRITICAL: Minecraft 1.21 (snapshot 24w21a) changed directory naming:
 * - Pre-1.21 (1.13-1.20.6): Uses "functions/" (PLURAL)
 * - 1.21+: Uses "function/" (SINGULAR)
 *
 * References:
 * - Pack format: https://minecraft.wiki/w/Pack_format
 * - Data pack structure: https://minecraft.wiki/w/Data_pack
 * - 24w21a changes: https://minecraft.wiki/w/Java_Edition_24w21a
 *
 * This class is stateless - all methods are static utilities that take paths as parameters.
 */
import groovy.json.JsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DatapackGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackGenerator)

    /**
     * Create the datapack directory structure for a specific Minecraft version.
     *
     * Structure for 1.21+:
     * readbooks_datapack_VERSION/
     * +-- pack.mcmeta
     * +-- data/
     *     +-- readbooks/
     *         +-- function/  (note: singular)
     *             +-- books.mcfunction
     *             +-- signs.mcfunction
     *
     * Structure for pre-1.21:
     * readbooks_datapack_VERSION/
     * +-- pack.mcmeta
     * +-- data/
     *     +-- readbooks/
     *         +-- functions/  (note: plural)
     *             +-- books.mcfunction
     *             +-- signs.mcfunction
     *
     * @param baseDirectory The world/project base directory
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return The function directory File object
     */
    static File createDatapackStructure(String baseDirectory, String outputFolder, String version) {
        String datapackName = "readbooks_datapack_${version}"
        File datapackRoot = new File(baseDirectory, "${outputFolder}${File.separator}${datapackName}")
        File dataFolder = new File(datapackRoot, "data")
        File namespaceFolder = new File(dataFolder, "readbooks")

        // CRITICAL: Pre-1.21 uses "functions" (plural), 1.21+ uses "function" (singular)
        // This changed in Minecraft Java Edition 1.21 snapshot 24w21a
        String functionDirName = (version == '1_21') ? 'function' : 'functions'
        File functionFolder = new File(namespaceFolder, functionDirName)

        // Create all directories
        functionFolder.mkdirs()

        LOGGER.debug("Created datapack structure: ${datapackRoot.absolutePath} with ${functionDirName}/ directory")
        return functionFolder
    }

    /**
     * Create pack.mcmeta file for a datapack.
     *
     * @param baseDirectory The world/project base directory
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @param packFormat The pack_format number for this Minecraft version
     * @param description Human-readable description of the datapack
     */
    static void createPackMcmeta(String baseDirectory, String outputFolder, String version, int packFormat, String description) {
        String datapackName = "readbooks_datapack_${version}"
        File datapackRoot = new File(baseDirectory, "${outputFolder}${File.separator}${datapackName}")
        File packMcmetaFile = new File(datapackRoot, "pack.mcmeta")

        // Create pack.mcmeta JSON content
        Map<String, Object> packData = [
            pack: [
                pack_format: packFormat,
                description: description
            ]
        ]

        packMcmetaFile.withWriter('UTF-8') { BufferedWriter writer ->
            writer.write(new JsonBuilder(packData).toPrettyString())
        }

        LOGGER.debug("Created pack.mcmeta for ${datapackName} with pack_format ${packFormat}")
    }

    /**
     * Get pack_format number for a Minecraft version.
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return The appropriate pack_format number
     */
    static int getPackFormat(String version) {
        switch (version) {
            case '1_13':
            case '1_14':
                return 4  // Minecraft 1.13-1.14.4
            case '1_20':
                return 15  // Minecraft 1.20-1.20.4
            case '1_20_5':
                return 41  // Minecraft 1.20.5-1.20.6
            case '1_21':
                return 48  // Minecraft 1.21+
            default:
                LOGGER.warn("Unknown version ${version}, defaulting to pack_format 48")
                return 48
        }
    }

    /**
     * Get human-readable Minecraft version range for description.
     *
     * IMPORTANT: These descriptions reflect COMMAND COMPATIBILITY, not pack_format compatibility.
     * The datapacks use specific pack_format numbers but the commands inside work across
     * broader version ranges due to command syntax changes being independent of pack format.
     *
     * @param version Version identifier (e.g., '1_13', '1_14', '1_20_5', '1_21')
     * @return Human-readable version string
     */
    static String getVersionDescription(String version) {
        switch (version) {
            case '1_13':
                return 'Minecraft 1.13-1.14.3 (uses pack_format 4, functions/ directory)'
            case '1_14':
                return 'Minecraft 1.14.4-1.19.4 (uses pack_format 4, functions/ directory)'
            case '1_20':
                return 'Minecraft 1.20-1.20.4 (uses pack_format 15, functions/ directory)'
            case '1_20_5':
                return 'Minecraft 1.20.5-1.20.6 (uses pack_format 41, functions/ directory)'
            case '1_21':
                return 'Minecraft 1.21+ (uses pack_format 48, function/ directory)'
            default:
                return "Minecraft ${version}"
        }
    }

    /**
     * Get the supported Minecraft versions for datapack generation.
     *
     * @return List of version identifiers
     */
    static List<String> getSupportedVersions() {
        return ['1_13', '1_14', '1_20_5', '1_21']
    }

}
