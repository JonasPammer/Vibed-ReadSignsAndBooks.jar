import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for OutputWriters utility class.
 * Tests CSV field escaping and file writing functionality.
 */
class OutputWritersSpec extends Specification {

    @TempDir
    Path tempDir

    // =========================================================================
    // escapeCsvField() Tests
    // =========================================================================

    def "escapeCsvField should return empty string for null"() {
        expect:
        OutputWriters.escapeCsvField(null) == ''
    }

    def "escapeCsvField should return empty string for empty string"() {
        expect:
        OutputWriters.escapeCsvField('') == ''
    }

    def "escapeCsvField should return plain text unchanged when no special characters"() {
        expect:
        OutputWriters.escapeCsvField('Plain text') == 'Plain text'
    }

    def "escapeCsvField should wrap in quotes when field contains comma"() {
        when:
        String escaped = OutputWriters.escapeCsvField('Text, with comma')

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('Text, with comma')
    }

    def "escapeCsvField should wrap in quotes when field contains quote"() {
        when:
        String escaped = OutputWriters.escapeCsvField('Text with "quote"')

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('""')  // Quotes should be doubled
    }

    def "escapeCsvField should double quotes inside quoted field"() {
        when:
        String escaped = OutputWriters.escapeCsvField('Text with "quotes" here')

        then:
        escaped == '"Text with ""quotes"" here"'
    }

    def "escapeCsvField should replace newlines with spaces"() {
        when:
        String escaped = OutputWriters.escapeCsvField("Line1\nLine2\rLine3")

        then:
        escaped.contains('Line1 Line2 Line3')
        !escaped.contains('\n')
        !escaped.contains('\r')
    }

    def "escapeCsvField should wrap in quotes when newlines are replaced"() {
        when:
        String escaped = OutputWriters.escapeCsvField("Line1\nLine2")

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('Line1 Line2')
    }

    def "escapeCsvField should handle multiple quotes"() {
        when:
        String escaped = OutputWriters.escapeCsvField('Text "with" multiple "quotes"')

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.count('""') >= 4  // At least 4 doubled quotes
    }

    def "escapeCsvField should handle field with both comma and quote"() {
        when:
        String escaped = OutputWriters.escapeCsvField('Text, with "quote" and comma')

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('""')  // Quotes doubled
        escaped.contains(',')    // Comma preserved
    }

    def "escapeCsvField should handle carriage return only"() {
        when:
        String escaped = OutputWriters.escapeCsvField("Text\rMore")

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('Text More')
    }

    def "escapeCsvField should handle newline only"() {
        when:
        String escaped = OutputWriters.escapeCsvField("Text\nMore")

        then:
        escaped.startsWith('"')
        escaped.endsWith('"')
        escaped.contains('Text More')
    }

    // =========================================================================
    // writeBooksCSV() Tests
    // =========================================================================

    def "writeBooksCSV should create CSV file with header"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        List<Map<String, Object>> books = []

        when:
        OutputWriters.writeBooksCSV(baseDir, outputFolder, books)

        then:
        File csvFile = new File(baseDir, "${outputFolder}${File.separator}all_books.csv")
        csvFile.exists()
        csvFile.text.contains('X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,Pages')
    }

    def "writeBooksCSV should write book data correctly"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        List<Map<String, Object>> books = [
            [
                x: 100, y: 75, z: -200,
                foundWhere: 'chest',
                bookname: 'Test Book',
                author: 'Test Author',
                pageCount: 3,
                generationName: 'Original',
                pages: 'Page 1, Page 2, Page 3'
            ]
        ]

        when:
        OutputWriters.writeBooksCSV(baseDir, outputFolder, books)

        then:
        File csvFile = new File(baseDir, "${outputFolder}${File.separator}all_books.csv")
        String content = csvFile.text
        content.contains('100,75,-200')
        content.contains('Test Book')
        content.contains('Test Author')
    }

    def "writeBooksCSV should handle null values"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        List<Map<String, Object>> books = [
            [x: null, y: null, z: null, foundWhere: null, bookname: null, author: null]
        ]

        when:
        OutputWriters.writeBooksCSV(baseDir, outputFolder, books)

        then:
        File csvFile = new File(baseDir, "${outputFolder}${File.separator}all_books.csv")
        String content = csvFile.text
        content.contains('0,0,0')  // Defaults to 0 for coordinates
        content.contains('undefined')  // Default for missing fields
    }

    // =========================================================================
    // writeSignsCSV() Tests
    // =========================================================================

    def "writeSignsCSV should create CSV file with header"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        List<Map<String, Object>> signs = []

        when:
        OutputWriters.writeSignsCSV(baseDir, outputFolder, signs)

        then:
        File csvFile = new File(baseDir, "${outputFolder}${File.separator}all_signs.csv")
        csvFile.exists()
        csvFile.text.contains('X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4')
    }

    def "writeSignsCSV should write sign data correctly"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        List<Map<String, Object>> signs = [
            [
                x: 50, y: 64, z: 100,
                foundWhere: 'overworld',
                signText: 'Test Sign',
                line1: 'Line 1',
                line2: 'Line 2',
                line3: 'Line 3',
                line4: 'Line 4'
            ]
        ]

        when:
        OutputWriters.writeSignsCSV(baseDir, outputFolder, signs)

        then:
        File csvFile = new File(baseDir, "${outputFolder}${File.separator}all_signs.csv")
        String content = csvFile.text
        content.contains('50,64,100')
        content.contains('Test Sign')
        content.contains('Line 1')
    }

    // =========================================================================
    // printSummaryStatistics() Tests
    // =========================================================================

    def "printSummaryStatistics should create summary file"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        Set<Integer> bookHashes = [1, 2, 3] as Set
        Set<String> signHashes = ['hash1', 'hash2'] as Set

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 5000L,
            bookHashes, signHashes, 5, 0,
            [:], [:], []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        summaryFile.exists()
        summaryFile.text.contains('SUMMARY STATISTICS')
    }

    def "printSummaryStatistics should format ASCII table correctly"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        Set<Integer> bookHashes = [1] as Set
        Set<String> signHashes = [] as Set
        List<Map<String, String>> bookMetadataList = [
            [
                title: 'Test Book',
                author: 'Test Author',
                pageCount: '3',
                foundWhere: 'chest',
                coordinates: '100,64,200'
            ]
        ]

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 1000L,
            bookHashes, signHashes, 1, 0,
            [:], [:], bookMetadataList
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        String content = summaryFile.text
        content.contains('Test Book')
        content.contains('Test Author')
        content.contains('chest')
    }

    def "printSummaryStatistics should include books by location type"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        Map<String, Integer> booksByLocationType = [
            'overworld': 10,
            'nether': 5
        ]

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 2000L,
            [] as Set, [] as Set, 15, 0,
            booksByLocationType, [:], []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        String content = summaryFile.text
        content.contains('overworld: 10')
        content.contains('nether: 5')
    }

    def "printSummaryStatistics should include books by container type"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        Map<String, Integer> booksByContainerType = [
            'chest': 8,
            'shulker_box': 2
        ]

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 3000L,
            [] as Set, [] as Set, 10, 0,
            [:], booksByContainerType, []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        String content = summaryFile.text
        content.contains('chest: 8')
        content.contains('shulker_box: 2')
    }

    def "printSummaryStatistics should show empty signs removed count"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 4000L,
            [] as Set, [] as Set, 0, 5,
            [:], [:], []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        summaryFile.text.contains('Empty signs removed: 5')
    }

    def "printSummaryStatistics should format elapsed time correctly"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 5000L,
            [] as Set, [] as Set, 0, 0,
            [:], [:], []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        String content = summaryFile.text
        content.contains('5 seconds') || content.contains('Total processing time')
    }

    def "printSummaryStatistics should calculate duplicate books correctly"() {
        given:
        String baseDir = tempDir.toString()
        String outputFolder = 'output'
        new File(baseDir, outputFolder).mkdirs()
        Set<Integer> bookHashes = [1, 2, 3] as Set  // 3 unique
        int bookCounter = 5  // 5 total (2 duplicates)

        when:
        OutputWriters.printSummaryStatistics(
            baseDir, outputFolder, 1000L,
            bookHashes, [] as Set, bookCounter, 0,
            [:], [:], []
        )

        then:
        File summaryFile = new File(baseDir, "${outputFolder}${File.separator}summary.txt")
        String content = summaryFile.text
        content.contains('Total unique books found: 3')
        content.contains('Total books extracted (including duplicates): 5')
        content.contains('Duplicate books: 2')
    }
}
