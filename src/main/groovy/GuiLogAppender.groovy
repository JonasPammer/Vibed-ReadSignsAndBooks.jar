import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

/**
 * Custom Logback appender that writes log messages to the GUI TextArea.
 *
 * Simply forwards formatted messages to the GUI handler via Platform.runLater.
 * The actual freeze prevention is handled by the rolling buffer in GUI.groovy
 * which limits TextArea content to 80KB.
 *
 * @see <a href="https://github.com/Vibed/ReadSignsAndBooks.jar/issues/12">GitHub Issue #12</a>
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    /** Handler closure that receives log messages for UI display */
    static Closure<Void> logHandler

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler == null) {
            return
        }

        def formattedMessage = "${event.formattedMessage}\n"
        Platform.runLater {
            logHandler(formattedMessage)
        }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
    }

    static void clearLogHandler() {
        logHandler = null
    }
}
