import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import javafx.application.Platform

/**
 * Custom Logback appender that writes log messages to the GUI TextArea
 */
class GuiLogAppender extends AppenderBase<ILoggingEvent> {

    static Closure<Void> logHandler

    @Override
    protected void append(ILoggingEvent event) {
        if (logHandler) {
            def formattedMessage = "${event.formattedMessage}\n"

            // Run on JavaFX thread
            Platform.runLater {
                logHandler(formattedMessage)
            }
        }
    }

    static void setLogHandler(Closure<Void> handler) {
        logHandler = handler
    }

    static void clearLogHandler() {
        logHandler = null
    }
}
