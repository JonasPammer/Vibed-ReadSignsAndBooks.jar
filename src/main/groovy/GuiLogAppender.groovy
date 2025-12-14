import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

/**
 * Custom Logback appender that writes log messages to the GUI TextArea.
 *
 * Delivers each log message to the handler via Platform.runLater.
 *
 * The GUI layer (GUI.groovy) handles optimization of TextArea updates via a rolling buffer
 * to prevent performance degradation during high-volume logging.
 *
 * See GitHub issue #12 for details on performance optimizations.
 *
 * @see <a href="https://github.com/Vibed/ReadSignsAndBooks.jar/issues/12">GitHub Issue #12</a>
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    static Closure<Void> logHandler

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler == null) return

        // Deliver each message individually via Platform.runLater
        // The handler processes the message; GUI.groovy manages rendering efficiency
        String message = event.formattedMessage + '\n'
        Platform.runLater { logHandler(message) }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
    }

    static void clearLogHandler() {
        logHandler = null
    }
}
