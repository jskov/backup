package dk.mada.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.jspecify.annotations.Nullable;

/**
 * Logger output capture for testing.
 */
public final class LoggerCapture extends StreamHandler {
    /** The last created instance. Normally just one instance is created. */
    @Nullable private static LoggerCapture instance;
    /** The consumed output. */
    private final ByteArrayOutputStream bos;

    /**
     * Creates instance.
     */
    public LoggerCapture() {
        this(new ByteArrayOutputStream());
    }

    /**
     * Creates instance.
     *
     * @param bos the capturing output stream
     */
    private LoggerCapture(ByteArrayOutputStream bos) {
        super(bos, new SimpleFormatter());

        this.bos = bos;
        LoggerCapture.instance = this;

        System.err.println("WARNING: Created LoggerCapture instance " + this.hashCode());
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        // ensures that all published records reach this instance immediately
        super.flush();
    }

    /**
     * Clears the captured log output.
     */
    public static void clear() {
        instance().bos.reset();
    }

    /** {@return the captured log output from the last created instance} */
    public static String getCaptured() {
        return instance().bos.toString(StandardCharsets.UTF_8);
    }

    /** {@return the last created instance} */
    private static LoggerCapture instance() {
        return Objects.requireNonNull(instance, "No instance created!");
    }
}
