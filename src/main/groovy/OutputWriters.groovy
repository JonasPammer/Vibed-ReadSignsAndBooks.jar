/**
 * Output file writing utilities for extracted Minecraft content.
 *
 * Provides utilities for:
 * - Writing books and signs to CSV format
 * - Generating summary statistics
 * - CSV field escaping
 *
 * This class is stateless - methods take all required data as parameters.
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
     * Write books data to CSV file.
     * CSV format: X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,Pages
     *
     * @param baseDirectory The base directory path
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param bookCsvData List of book data maps
     */
    static void writeBooksCSV(String baseDirectory, String outputFolder, List<Map<String, Object>> bookCsvData) {
        File outputBaseDir = new File(outputFolder)
        if (!outputBaseDir.absolute) {
            outputBaseDir = new File(baseDirectory, outputFolder)
        }
        File csvFile = new File(outputBaseDir, 'all_books.csv')
        LOGGER.info("Writing books CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,Pages')

            // Write data
            bookCsvData.each { Map<String, Object> book ->
                String x = book.x != null ? book.x.toString() : '0'
                String y = book.y != null ? book.y.toString() : '0'
                String z = book.z != null ? book.z.toString() : '0'
                String foundWhere = escapeCsvField(book.foundWhere?.toString() ?: 'undefined')
                String bookname = escapeCsvField(book.bookname?.toString() ?: 'undefined')
                String author = escapeCsvField(book.author?.toString() ?: 'undefined')
                String pageCount = book.pageCount != null ? book.pageCount.toString() : '0'
                String generationName = escapeCsvField(book.generationName?.toString() ?: 'Original')
                String pages = escapeCsvField(book.pages?.toString() ?: 'undefined')

                writer.writeLine("${x},${y},${z},${foundWhere},${bookname},${author},${pageCount},${generationName},${pages}")
            }
        }

        LOGGER.info("Books CSV written successfully with ${bookCsvData.size()} entries")
    }

    /**
     * Write signs data to CSV file.
     * CSV format: X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4
     *
     * @param baseDirectory The base directory path
     * @param outputFolder The output folder path (relative to baseDirectory)
     * @param signCsvData List of sign data maps
     */
    static void writeSignsCSV(String baseDirectory, String outputFolder, List<Map<String, Object>> signCsvData) {
        File outputBaseDir = new File(outputFolder)
        if (!outputBaseDir.absolute) {
            outputBaseDir = new File(baseDirectory, outputFolder)
        }
        File csvFile = new File(outputBaseDir, 'all_signs.csv')
        LOGGER.info("Writing signs CSV to: ${csvFile.absolutePath}")

        csvFile.withWriter('UTF-8') { BufferedWriter writer ->
            // Write header
            writer.writeLine('X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4')

            // Write data
            signCsvData.each { Map<String, Object> sign ->
                String x = sign.x?.toString() ?: '0'
                String y = sign.y?.toString() ?: '0'
                String z = sign.z?.toString() ?: '0'
                String foundWhere = escapeCsvField(sign.foundWhere?.toString() ?: 'unknown')
                String signText = escapeCsvField(sign.signText?.toString() ?: 'undefined')
                String line1 = escapeCsvField(sign.line1?.toString() ?: '')
                String line2 = escapeCsvField(sign.line2?.toString() ?: '')
                String line3 = escapeCsvField(sign.line3?.toString() ?: '')
                String line4 = escapeCsvField(sign.line4?.toString() ?: '')

                writer.writeLine("${x},${y},${z},${foundWhere},${signText},${line1},${line2},${line3},${line4}")
            }
        }

        LOGGER.info("Signs CSV written successfully with ${signCsvData.size()} entries")
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
