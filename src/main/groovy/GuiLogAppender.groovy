import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom Logback appender that writes log messages to the GUI TextArea.
 *
 * Implements message throttling to prevent flooding the JavaFX event queue.
 * Messages are buffered and flushed to the UI at most once per THROTTLE_MS milliseconds.
 * This prevents UI freeze when extraction generates thousands of log messages rapidly.
 *
 * @see <a href="https://github.com/Vibed/ReadSignsAndBooks.jar/issues/12">GitHub Issue #12</a>
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    /** Handler closure that receives batched log messages for UI display */
    static Closure<Void> logHandler

    /** Message buffer - thread-safe queue for collecting log messages */
    private static final ConcurrentLinkedQueue<String> messageBuffer = new ConcurrentLinkedQueue<>()

    /** Flag to prevent scheduling multiple flush tasks simultaneously */
    private static final AtomicBoolean flushScheduled = new AtomicBoolean(false)

    /** Minimum time between UI updates in milliseconds (100ms = max 10 updates/second) */
    private static final long THROTTLE_MS = 100

    /** Maximum messages to flush per batch (prevents massive single updates) */
    private static final int MAX_BATCH_SIZE = 100

    /** Last flush timestamp for throttling */
    private static volatile long lastFlushTime = 0

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler == null) {
            return
        }

        def formattedMessage = "${event.formattedMessage}\n"
        messageBuffer.offer(formattedMessage)

        // Schedule a flush if one isn't already pending
        scheduleFlush()
    }

    /**
     * Schedules a flush of buffered messages to the UI.
     * Uses atomic flag to ensure only one flush is scheduled at a time.
     */
    private static void scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            long now = System.currentTimeMillis()
            long elapsed = now - lastFlushTime
            long delay = Math.max(0, THROTTLE_MS - elapsed)

            if (delay == 0) {
                // Enough time has passed, flush immediately
                Platform.runLater { flushBuffer() }
            } else {
                // Schedule delayed flush
                Thread.start {
                    try {
                        Thread.sleep(delay)
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt()
                    }
                    Platform.runLater { flushBuffer() }
                }
            }
        }
    }

    /**
     * Flushes accumulated messages to the UI handler.
     * Called on JavaFX Application Thread.
     */
    private static void flushBuffer() {
        if (logHandler == null) {
            messageBuffer.clear()
            flushScheduled.set(false)
            return
        }

        // Build batch of messages
        StringBuilder batch = new StringBuilder()
        int count = 0
        String message

        while (count < MAX_BATCH_SIZE && (message = messageBuffer.poll()) != null) {
            batch.append(message)
            count++
        }

        // Update UI with batched messages
        if (batch.length() > 0) {
            logHandler(batch.toString())
        }

        lastFlushTime = System.currentTimeMillis()
        flushScheduled.set(false)

        // If there are more messages, schedule another flush
        if (!messageBuffer.isEmpty()) {
            scheduleFlush()
        }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
    }

    static void clearLogHandler() {
        logHandler = null
        messageBuffer.clear()
        flushScheduled.set(false)
    }
}
