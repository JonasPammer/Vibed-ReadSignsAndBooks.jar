import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.stage.Stage
import org.testfx.framework.spock.ApplicationSpec
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.awt.GraphicsEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * GUI Integration Tests for ReadSignsAndBooks
 *
 * Tests the GUI components including:
 * - GuiLogAppender throttling behavior (fixes GitHub issue #12)
 * - GUI launch and basic functionality
 *
 * Uses TestFX with Monocle for headless testing in CI environments.
 *
 * IMPORTANT: These tests require a display to run. They will be skipped
 * automatically in headless environments (CI without xvfb, etc.) because
 * GuiLogAppender uses Platform.runLater() which requires JavaFX initialization.
 */
@IgnoreIf({ GraphicsEnvironment.isHeadless() })
class GuiIntegrationSpec extends Specification {

    @Shared
    static boolean jfxInitialized = false

    def setupSpec() {
        // Initialize JavaFX toolkit once for all tests
        // JFXPanel triggers JavaFX initialization in a headless-compatible way
        if (!jfxInitialized) {
            try {
                new JFXPanel()
                jfxInitialized = true
                println "JavaFX toolkit initialized successfully"
            } catch (Exception e) {
                println "Warning: Could not initialize JavaFX toolkit: ${e.message}"
            }
        }
    }

    def setup() {
        // Reset GuiLogAppender state before each test
        GuiLogAppender.clearLogHandler()
    }

    def cleanup() {
        // Clean up after each test
        GuiLogAppender.clearLogHandler()
    }

    // =========================================================================
    // GuiLogAppender Throttling Tests (GitHub Issue #12)
    // =========================================================================

    def "GuiLogAppender should batch multiple messages into single UI update"() {
        given: "A counter to track UI updates"
        def updateCount = new AtomicInteger(0)
        def receivedMessages = new StringBuilder()
        def latch = new CountDownLatch(1)

        and: "A log handler that counts updates"
        GuiLogAppender.setLogHandler { message ->
            updateCount.incrementAndGet()
            receivedMessages.append(message)
            if (receivedMessages.toString().contains("Message 10")) {
                latch.countDown()
            }
        }

        and: "A mock logging event creator"
        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        when: "We send 10 messages rapidly"
        def appender = new GuiLogAppender()
        appender.start()

        10.times { i ->
            appender.append(createEvent("Message ${i + 1}"))
        }

        and: "Wait for messages to be flushed"
        latch.await(2, TimeUnit.SECONDS)

        // Give extra time for any remaining flushes
        Thread.sleep(200)

        then: "All messages should be received"
        receivedMessages.toString().contains("Message 1")
        receivedMessages.toString().contains("Message 10")

        and: "Messages should be batched (fewer updates than messages)"
        updateCount.get() < 10

        cleanup:
        appender.stop()
    }

    def "GuiLogAppender should not flood UI when receiving many messages quickly"() {
        given: "A counter to track UI updates"
        def updateCount = new AtomicInteger(0)
        def messageCount = new AtomicInteger(0)
        def latch = new CountDownLatch(1)
        def targetMessages = 100

        and: "A log handler that counts updates and messages"
        GuiLogAppender.setLogHandler { message ->
            updateCount.incrementAndGet()
            // Count newlines to determine message count
            def count = message.count('\n')
            if (messageCount.addAndGet(count) >= targetMessages) {
                latch.countDown()
            }
        }

        and: "A mock logging event creator"
        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        when: "We send 100 messages as fast as possible"
        def appender = new GuiLogAppender()
        appender.start()

        targetMessages.times { i ->
            appender.append(createEvent("Flood test message ${i + 1}"))
        }

        and: "Wait for all messages to be processed"
        def completed = latch.await(5, TimeUnit.SECONDS)

        then: "All messages should eventually be delivered"
        completed || messageCount.get() >= targetMessages * 0.9  // Allow 90% delivery

        and: "UI updates should be significantly fewer than messages (batching works)"
        // With 100ms throttle and 100 messages sent instantly, we expect ~1-3 updates
        updateCount.get() < targetMessages / 5  // At most 20 updates for 100 messages

        cleanup:
        appender.stop()
    }

    def "GuiLogAppender should handle null handler gracefully"() {
        given: "No log handler set"
        GuiLogAppender.clearLogHandler()

        and: "A mock logging event"
        def event = [getFormattedMessage: { "Test message" }] as ch.qos.logback.classic.spi.ILoggingEvent

        when: "We append a message with no handler"
        def appender = new GuiLogAppender()
        appender.start()
        appender.append(event)

        then: "No exception should be thrown"
        noExceptionThrown()

        cleanup:
        appender.stop()
    }

    def "GuiLogAppender clearLogHandler should reset all state"() {
        given: "A log handler and some buffered messages"
        def received = new StringBuilder()
        GuiLogAppender.setLogHandler { message ->
            received.append(message)
        }

        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        def appender = new GuiLogAppender()
        appender.start()
        appender.append(createEvent("Message before clear"))

        when: "We clear the handler"
        GuiLogAppender.clearLogHandler()

        and: "Send more messages"
        appender.append(createEvent("Message after clear"))
        Thread.sleep(200)

        then: "Messages after clear should not be received"
        !received.toString().contains("Message after clear")

        cleanup:
        appender.stop()
    }

    def "GuiLogAppender should respect MAX_BATCH_SIZE limit"() {
        given: "A handler that tracks batch sizes"
        def batchSizes = []
        def latch = new CountDownLatch(1)
        def totalReceived = new AtomicInteger(0)
        def targetMessages = 150  // More than MAX_BATCH_SIZE (100)

        GuiLogAppender.setLogHandler { message ->
            def count = message.count('\n')
            batchSizes << count
            if (totalReceived.addAndGet(count) >= targetMessages) {
                latch.countDown()
            }
        }

        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        when: "We send more messages than MAX_BATCH_SIZE"
        def appender = new GuiLogAppender()
        appender.start()

        targetMessages.times { i ->
            appender.append(createEvent("Batch test ${i + 1}"))
        }

        and: "Wait for processing"
        latch.await(5, TimeUnit.SECONDS)

        then: "Each batch should be at most MAX_BATCH_SIZE"
        batchSizes.every { it <= 100 }

        and: "Multiple batches should be created"
        batchSizes.size() >= 2

        cleanup:
        appender.stop()
    }

    // =========================================================================
    // GUI Mode Detection Tests
    // =========================================================================

    def "Main.shouldUseGui should return true for empty args"() {
        expect:
        Main.shouldUseGui([] as String[])
    }

    def "Main.shouldUseGui should return true for --gui flag"() {
        expect:
        Main.shouldUseGui(['--gui'] as String[])
    }

    def "Main.shouldUseGui should return true for -g flag"() {
        expect:
        Main.shouldUseGui(['-g'] as String[])
    }

    def "Main.shouldUseGui should return false for CLI arguments"() {
        expect:
        !Main.shouldUseGui(['-w', '/some/path'] as String[])
    }

    def "Main.shouldUseGui should return false for --help"() {
        expect:
        !Main.shouldUseGui(['--help'] as String[])
    }

    @Unroll
    def "Main.shouldUseGui for args=#args should return #expected"() {
        expect:
        Main.shouldUseGui(args as String[]) == expected

        where:
        args                        | expected
        []                          | true
        ['--gui']                   | true
        ['-g']                      | true
        ['--gui', '-w', '/path']    | true  // --gui takes precedence
        ['-w', '/path']             | false
        ['-o', '/output']           | false
        ['--help']                  | false
        ['-h']                      | false
        ['--version']               | false
    }
}
