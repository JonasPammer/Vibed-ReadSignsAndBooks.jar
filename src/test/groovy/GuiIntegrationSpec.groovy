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
 * - GuiLogAppender message delivery (related to GitHub issue #12)
 * - GUI mode detection
 *
 * Note: The freeze prevention for GitHub issue #12 is handled by the rolling
 * buffer in GUI.groovy, not by batching in GuiLogAppender. These tests verify
 * that GuiLogAppender correctly delivers messages via Platform.runLater().
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
    // GuiLogAppender Message Delivery Tests (related to GitHub Issue #12)
    // =========================================================================

    def "GuiLogAppender should deliver all messages to handler"() {
        given: "A counter to track received messages"
        def receivedMessages = new StringBuilder()
        def latch = new CountDownLatch(10)

        and: "A log handler that collects messages"
        GuiLogAppender.setLogHandler { message ->
            receivedMessages.append(message)
            latch.countDown()
        }

        and: "A mock logging event creator"
        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        when: "We send 10 messages"
        def appender = new GuiLogAppender()
        appender.start()

        10.times { i ->
            appender.append(createEvent("Message ${i + 1}"))
        }

        and: "Wait for messages to be delivered via Platform.runLater"
        latch.await(5, TimeUnit.SECONDS)

        then: "All messages should be received"
        receivedMessages.toString().contains("Message 1")
        receivedMessages.toString().contains("Message 10")

        cleanup:
        appender.stop()
    }

    def "GuiLogAppender should handle many messages without losing them"() {
        given: "A counter to track received messages"
        def messageCount = new AtomicInteger(0)
        def targetMessages = 50
        def latch = new CountDownLatch(targetMessages)

        and: "A log handler that counts messages"
        GuiLogAppender.setLogHandler { message ->
            messageCount.incrementAndGet()
            latch.countDown()
        }

        and: "A mock logging event creator"
        def createEvent = { String msg ->
            [getFormattedMessage: { msg }] as ch.qos.logback.classic.spi.ILoggingEvent
        }

        when: "We send many messages quickly"
        def appender = new GuiLogAppender()
        appender.start()

        targetMessages.times { i ->
            appender.append(createEvent("Test message ${i + 1}"))
        }

        and: "Wait for all messages to be processed"
        def completed = latch.await(10, TimeUnit.SECONDS)

        then: "All messages should be delivered"
        completed
        messageCount.get() == targetMessages

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
