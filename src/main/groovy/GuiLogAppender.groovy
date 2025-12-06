import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

/**
 * Custom Logback appender that writes log messages to the GUI TextArea.
 *
 * Uses simple time-based batching to prevent FX event queue flooding.
 * Messages accumulate in a buffer and flush every 100ms.
 * This reduces Platform.runLater calls from thousands/second to ~10/second.
 *
 * @see <a href="https://github.com/Vibed/ReadSignsAndBooks.jar/issues/12">GitHub Issue #12</a>
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    static Closure<Void> logHandler
    private static StringBuilder buffer = new StringBuilder()
    private static long lastFlush = 0
    private static final long FLUSH_INTERVAL = 100  // ms

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler == null) return

        synchronized (buffer) {
            buffer.append(event.formattedMessage).append('\n')

            long now = System.currentTimeMillis()
            if (now - lastFlush >= FLUSH_INTERVAL) {
                def text = buffer.toString()
                buffer.setLength(0)
                lastFlush = now
                Platform.runLater { logHandler(text) }
            }
        }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
    }

    static void clearLogHandler() {
        logHandler = null
        synchronized (buffer) { buffer.setLength(0) }
    }
}
