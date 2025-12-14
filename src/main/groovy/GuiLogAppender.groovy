import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

/**
 * Custom Logback appender that writes log messages to the GUI TextArea.
 *
 * Uses time-based batching where each message is delivered individually after a delay.
 * This ensures:
 * - Messages are reliably delivered to the handler (not lost)
 * - Platform.runLater is called consistently for each message
 * - The delay (100ms minimum) prevents GUI flooding when messages arrive in rapid bursts
 *
 * The GUI layer (GUI.groovy) further optimizes rendering via a rolling buffer to prevent
 * TextArea performance degradation with large volumes of text.
 *
 * See GitHub issue #12 for details on performance optimizations.
 *
 * @see <a href="https://github.com/Vibed/ReadSignsAndBooks.jar/issues/12">GitHub Issue #12</a>
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    static Closure<Void> logHandler
    private static StringBuilder buffer = new StringBuilder()
    private static long lastFlush = System.currentTimeMillis()
    private static final long FLUSH_INTERVAL = 100  // ms - minimum delay before first delivery
    private static volatile Timer flushTimer = null

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler == null) return

        synchronized (buffer) {
            buffer.append(event.formattedMessage).append('\n')
            
            long now = System.currentTimeMillis()
            
            // If enough time has passed since last flush, deliver immediately
            if (now - lastFlush >= FLUSH_INTERVAL) {
                performFlush()
                // Schedule next flush to be available after FLUSH_INTERVAL
                scheduleNextFlushOpportunity()
            } else if (flushTimer == null) {
                // Schedule a flush for when FLUSH_INTERVAL expires
                scheduleNextFlushOpportunity()
            }
        }
    }

    private static void scheduleNextFlushOpportunity() {
        synchronized (buffer) {
            if (flushTimer != null) return  // Already scheduled
            
            long delayMs = FLUSH_INTERVAL - (System.currentTimeMillis() - lastFlush)
            if (delayMs > 0) {
                flushTimer = new Timer('GuiLogAppenderFlushTimer', true)
                flushTimer.schedule(new TimerTask() {
                    @Override
                    void run() {
                        synchronized (buffer) {
                            if (buffer.length() > 0) {
                                performFlush()
                                // After flushing, schedule next opportunity
                                flushTimer = null
                                long newDelay = FLUSH_INTERVAL
                                flushTimer = new Timer('GuiLogAppenderFlushTimer', true)
                                flushTimer.schedule(new TimerTask() {
                                    @Override
                                    void run() {
                                        synchronized (buffer) {
                                            flushTimer = null
                                        }
                                    }
                                }, newDelay)
                            } else {
                                flushTimer = null
                            }
                        }
                    }
                }, delayMs)
            }
        }
    }

    private static void performFlush() {
        if (buffer.length() > 0) {
            def text = buffer.toString()
            buffer.setLength(0)
            lastFlush = System.currentTimeMillis()
            
            // Deliver via Platform.runLater
            Platform.runLater { logHandler(text) }
        }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
        synchronized (buffer) {
            lastFlush = System.currentTimeMillis()
            if (flushTimer != null) {
                flushTimer.cancel()
                flushTimer = null
            }
        }
    }

    static void clearLogHandler() {
        logHandler = null
        synchronized (buffer) {
            buffer.setLength(0)
            if (flushTimer != null) {
                flushTimer.cancel()
                flushTimer = null
            }
            lastFlush = System.currentTimeMillis()
        }
    }
}
