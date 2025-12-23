/**
 * Output file writing utilities for extracted Minecraft content.
 *
 * Provides utilities for:
 * - Writing books and signs to CSV format (both clean and raw versions)
 * - Generating summary statistics
 * - CSV field escaping
 *
 * This class is stateless - methods take all required data as parameters.
 *
 * Output files generated:
 * - all_books.csv - Clean version (formatting codes stripped) for human reading
 * - all_books_raw.csv - Raw version (formatting codes preserved) for Minecraft recreation
 * - all_signs.csv - Clean version
 * - all_signs_raw.csv - Raw version
 */
import com.github.freva.asciitable.AsciiTable
import com.github.freva.asciitable.Column
import com.github.freva.asciitable.HorizontalAlign
import org.apache.commons.lang3.time.DurationFormatUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OutputWriters {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputWriters)

    /**
     * Write books data to CSV files (both clean and raw versions).
     * CSV format: X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,Pages
     *
     * Generates:
     * - all_books.csv - Clean version with formatting codes stripped
     * - all_books_raw.csv - Raw version with formatting codes preserved
     *
     * @param baseDirectory The base directory path
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param bookCsvData List of book data maps (contains raw text with formatting codes)
     */
    static void writeBooksCSV(String baseDirectory, String outputFolder, List<Map<String, Object>> bookCsvData) {
        File outputBaseDir = new File(outputFolder)
        if (!outputBaseDir.absolute) {
            outputBaseDir = new File(baseDirectory, outputFolder)
        }

        // Write clean version (formatting codes stripped)
        File cleanCsvFile = new File(outputBaseDir, 'all_books.csv')
        writeBooksCSVFile(cleanCsvFile, bookCsvData, true)
        LOGGER.info("Books CSV (clean) written to: ${cleanCsvFile.absolutePath}")

        // Write raw version (formatting codes preserved)
        File rawCsvFile = new File(outputBaseDir, 'all_books_raw.csv')
        writeBooksCSVFile(rawCsvFile, bookCsvData, false)
        LOGGER.info("Books CSV (raw) written to: ${rawCsvFile.absolutePath}")

        LOGGER.info("Books CSV files written successfully with ${bookCsvData.size()} entries each")
    }

    /**
     * Write books data to a single CSV file.
     *
     * @param csvFile The file to write to
     * @param bookCsvData List of book data maps
     * @param stripFormatting If true, strip Minecraft formatting codes from text
     */
    private static void writeBooksCSVFile(File csvFile, List<Map<String, Object>> bookCsvData, boolean stripFormatting) {
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,Pages')

            // Write data
            bookCsvData.each { Map<String, Object> book ->
                String x = book.x != null ? book.x.toString() : '0'
                String y = book.y != null ? book.y.toString() : '0'
                String z = book.z != null ? book.z.toString() : '0'
                String foundWhere = escapeCsvField(book.foundWhere?.toString() ?: 'undefined')
                String bookname = escapeCsvField(maybeStripFormatting(book.bookname?.toString() ?: 'undefined', stripFormatting))
                String author = escapeCsvField(maybeStripFormatting(book.author?.toString() ?: 'undefined', stripFormatting))
                String pageCount = book.pageCount != null ? book.pageCount.toString() : '0'
                String generationName = escapeCsvField(book.generationName?.toString() ?: 'Original')
                String pages = escapeCsvField(maybeStripFormatting(book.pages?.toString() ?: 'undefined', stripFormatting))

                writer.writeLine("${x},${y},${z},${foundWhere},${bookname},${author},${pageCount},${generationName},${pages}")
            }
        }
    }

    /**
     * Write signs data to CSV files (both clean and raw versions).
     * CSV format: X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4
     *
     * Generates:
     * - all_signs.csv - Clean version with formatting codes stripped
     * - all_signs_raw.csv - Raw version with formatting codes preserved
     *
     * @param baseDirectory The base directory path
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param signCsvData List of sign data maps (contains raw text with formatting codes)
     */
    static void writeSignsCSV(String baseDirectory, String outputFolder, List<Map<String, Object>> signCsvData) {
        File outputBaseDir = new File(outputFolder)
        if (!outputBaseDir.absolute) {
            outputBaseDir = new File(baseDirectory, outputFolder)
        }

        // Write clean version (formatting codes stripped)
        File cleanCsvFile = new File(outputBaseDir, 'all_signs.csv')
        writeSignsCSVFile(cleanCsvFile, signCsvData, true)
        LOGGER.info("Signs CSV (clean) written to: ${cleanCsvFile.absolutePath}")

        // Write raw version (formatting codes preserved)
        File rawCsvFile = new File(outputBaseDir, 'all_signs_raw.csv')
        writeSignsCSVFile(rawCsvFile, signCsvData, false)
        LOGGER.info("Signs CSV (raw) written to: ${rawCsvFile.absolutePath}")

        LOGGER.info("Signs CSV files written successfully with ${signCsvData.size()} entries each")
    }

    /**
     * Write signs data to a single CSV file.
     *
     * @param csvFile The file to write to
     * @param signCsvData List of sign data maps
     * @param stripFormatting If true, strip Minecraft formatting codes from text
     */
    private static void writeSignsCSVFile(File csvFile, List<Map<String, Object>> signCsvData, boolean stripFormatting) {
        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4')

            // Write data
            signCsvData.each { Map<String, Object> sign ->
                String x = sign.x?.toString() ?: '0'
                String y = sign.y?.toString() ?: '0'
                String z = sign.z?.toString() ?: '0'
                String foundWhere = escapeCsvField(sign.foundWhere?.toString() ?: 'unknown')
                String signText = escapeCsvField(maybeStripFormatting(sign.signText?.toString() ?: 'undefined', stripFormatting))
                String line1 = escapeCsvField(maybeStripFormatting(sign.line1?.toString() ?: '', stripFormatting))
                String line2 = escapeCsvField(maybeStripFormatting(sign.line2?.toString() ?: '', stripFormatting))
                String line3 = escapeCsvField(maybeStripFormatting(sign.line3?.toString() ?: '', stripFormatting))
                String line4 = escapeCsvField(maybeStripFormatting(sign.line4?.toString() ?: '', stripFormatting))

                writer.writeLine("${x},${y},${z},${foundWhere},${signText},${line1},${line2},${line3},${line4}")
            }
        }
    }

    /**
     * Strip Minecraft formatting codes if requested.
     *
     * @param text The text to process
     * @param strip If true, strip formatting codes; if false, return unchanged
     * @return Processed text
     */
    private static String maybeStripFormatting(String text, boolean strip) {
        if (!strip || !text) {
            return text ?: ''
        }
        return TextUtils.removeTextFormatting(text)
    }

    /**
     * Escape CSV field by wrapping in quotes if it contains comma, newline, or quote.
     *
     * @param field The field value to escape
     * @return The escaped field value
     */
    static String escapeCsvField(String field) {
        if (!field) {
            return ''
        }

        // Replace newlines with space for readability
        String escaped = field.replace('\n', ' ').replace('\r', ' ')

        // If field contains comma, quote, or was modified, wrap in quotes
        if (escaped.contains(',') || escaped.contains('"') || escaped != field) {
            // Escape quotes by doubling them
            escaped = escaped.replace('"', '""')
            return "\"${escaped}\""
        }

        return escaped
    }

    /**
     * Print and write summary statistics to file.
     *
     * @param baseDirectory The base directory path
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param elapsedMillis Total processing time in milliseconds
     * @param bookHashes Set of unique book hashes
     * @param signHashes Set of unique sign hashes
     * @param bookCounter Total books found (including duplicates)
     * @param emptySignsRemoved Count of empty signs filtered out
     * @param booksByLocationType Map of book counts by location type
     * @param booksByContainerType Map of book counts by container type
     * @param bookMetadataList List of book metadata for table display
     */
    @SuppressWarnings('ParameterCount')
    static void printSummaryStatistics(
            String baseDirectory,
            String outputFolder,
            long elapsedMillis,
            Set<Integer> bookHashes,
            Set<String> signHashes,
            int bookCounter,
            int emptySignsRemoved,
            Map<String, Integer> booksByLocationType,
            Map<String, Integer> booksByContainerType,
            List<Map<String, String>> bookMetadataList
    ) {
        File outputBaseDir = new File(outputFolder)
        if (!outputBaseDir.absolute) {
            outputBaseDir = new File(baseDirectory, outputFolder)
        }
        File summaryFile = new File(outputBaseDir, 'summary.txt')
        summaryFile.withWriter { BufferedWriter w ->
            w.writeLine('=' * 80)
            w.writeLine('SUMMARY STATISTICS')
            w.writeLine('=' * 80)

            w.writeLine('\nBooks:')
            w.writeLine("  Total unique books found: ${bookHashes.size()}")
            w.writeLine("  Total books extracted (including duplicates): ${bookCounter}")
            w.writeLine("  Duplicate books: ${bookCounter - bookHashes.size()}")

            if (booksByLocationType) {
                w.writeLine('\n  Books by location type:')
                booksByLocationType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                    w.writeLine("    ${k}: ${v}")
            }
        }

            if (booksByContainerType) {
                w.writeLine('\n  Books by container type:')
                booksByContainerType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                    w.writeLine("    ${k}: ${v}")
            }
    }

            w.writeLine('\nSigns:')
            w.writeLine("  Total signs found: ${signHashes.size()}")
            if (emptySignsRemoved > 0) {
                w.writeLine("  Empty signs removed: ${emptySignsRemoved}")
            }

            w.writeLine('\nPerformance:')
            w.writeLine("  Total processing time: ${DurationFormatUtils.formatDurationWords(elapsedMillis, true, true)} (${elapsedMillis / 1000} seconds)")

            // Generate ASCII table of books with detailed information
            if (bookMetadataList) {
                w.writeLine('\n  Books extracted:')
                List<Column> columns = [
                    new Column().header('Title').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.title?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('Author').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.author?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('PageCount').dataAlign(HorizontalAlign.RIGHT).with({ Map<String, Object> book -> book.pageCount?.toString() ?: '0' } as java.util.function.Function),
                    new Column().header('FoundWhere').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.foundWhere?.toString() ?: '' } as java.util.function.Function),
                    new Column().header('Coordinates').dataAlign(HorizontalAlign.LEFT).with({ Map<String, Object> book -> book.coordinates?.toString() ?: '' } as java.util.function.Function)
                ]
                String table = AsciiTable.getTable(bookMetadataList, columns)
                w.writeLine(table)
            }

            w.writeLine("\n${'=' * 80}")
            w.writeLine('Completed successfully!')
            w.writeLine('=' * 80)
}

        // Also log to console
        LOGGER.info("\n${'=' * 80}")
        LOGGER.info('SUMMARY STATISTICS')
        LOGGER.info('=' * 80)
        LOGGER.info('\nBooks:')
        LOGGER.info("  Total unique books found: ${bookHashes.size()}")
        LOGGER.info("  Total books extracted (including duplicates): ${bookCounter}")
        LOGGER.info("  Duplicate books: ${bookCounter - bookHashes.size()}")

        if (booksByLocationType) {
            LOGGER.info('\n  Books by location type:')
            booksByLocationType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                LOGGER.info("    ${k}: ${v}")
        }
        }

        if (booksByContainerType) {
            LOGGER.info('\n  Books by container type:')
            booksByContainerType.sort { Map.Entry<String, Integer> entry -> -entry.value }.each { String k, Integer v ->
                LOGGER.info("    ${k}: ${v}")
        }
        }

        LOGGER.info('\nSigns:')
        LOGGER.info("  Total signs found: ${signHashes.size()}")
        if (emptySignsRemoved > 0) {
            LOGGER.info("  Empty signs removed: ${emptySignsRemoved}")
        }

        LOGGER.info('\nPerformance:')
        LOGGER.info("  Total processing time: ${DurationFormatUtils.formatDurationWords(elapsedMillis, true, true)} (${elapsedMillis / 1000} seconds)")

        LOGGER.info("\n${'=' * 80}")
        LOGGER.info('Completed successfully!')
        LOGGER.info('=' * 80)
    }

}
