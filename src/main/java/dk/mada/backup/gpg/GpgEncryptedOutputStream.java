package dk.mada.backup.gpg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupException;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.types.GpgId;

/**
 * OutputStream filter that GPG-encrypts the outgoing stream.
 *
 * Writes to this stream instance are passed on to the stdin of an external GPG process. The stdout from the GPG process
 * is passed to the super output stream of this instance.
 *
 * Stderr from the GPG process is captured separately.
 *
 * When this instance is closed it closes the GPG stdin stream and waits for the GPG process to complete.
 */
public final class GpgEncryptedOutputStream extends FilterOutputStream {
    private static final Logger logger = LoggerFactory.getLogger(GpgEncryptedOutputStream.class);
    /** The buffer size used when streaming. */
    private static final int BUFFER_SIZE = 8192;
    /** The max wait time in seconds for GPG output to be consumed after the process ends. */
    private static final int GPG_BACKGROUND_MAX_WAIT_SECONDS = 60;
    /** The max wait time in seconds for the GPG stderr output to be consumed after the process ends. */
    private static final int GPG_STDERR_MAX_WAIT_SECONDS = 5;

    /** GPG recipient key id. */
    private final GpgId recipientKeyId;
    /** Environment overrides. */
    private final Map<String, String> envOverrides;
    /** Latch signaling completion of the (stdout) GPG process. */
    private final CountDownLatch gpgStdoutDone = new CountDownLatch(1);
    /** Latch signaling completed capture of the stderr from the GPG process. */
    private final CountDownLatch gpgStderrDone = new CountDownLatch(1);
    /** Error message captured from GPG (or empty). */
    private final AtomicReference<String> stderrMessageRef = new AtomicReference<>();
    /** Exception captured when writing to the external GPG process (or empty). */
    private final AtomicReference<IOException> sinkException = new AtomicReference<>();
    /** The output stream (sink) connecting to the GPG process's stdin. */
    private OutputStream gpgSink;
    /** Exception captured in thread copying GPG stdout (the crypted data), or null. */
    @Nullable private Exception stdoutException;
    /** Exception captured in thread copying GPG stderr, or null. */
    @Nullable private Exception stderrException;

    /**
     * Creates new instance.
     *
     * @param out            the stream to write the encoded data to
     * @param recipientKeyId the recipient GPG key to use for encryption
     * @param envOverrides   the environment overrides to use
     *
     * @throws GpgEncrypterException if the GPG process fails
     */
    public GpgEncryptedOutputStream(OutputStream out, GpgId recipientKeyId, Map<String, String> envOverrides)
            throws GpgEncrypterException {
        super(out);
        this.recipientKeyId = recipientKeyId;
        this.envOverrides = envOverrides;

        gpgSink = startGpgBackgroundProcess();
    }

    /**
     * Creates new instance.
     *
     * @param out            the stream to write the encoded data to
     * @param recipientKeyId the recipient GPG key to use for encryption
     *
     * @throws GpgEncrypterException if the GPG process fails
     */
    public GpgEncryptedOutputStream(OutputStream out, GpgId recipientKeyId) throws GpgEncrypterException {
        this(out, recipientKeyId, Collections.emptyMap());
    }

    @Override
    public void write(int b) throws IOException {
        try {
            gpgSink.write(b);
        } catch (IOException e) {
            sinkException.set(e);
            throw e;
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            gpgSink.write(b, 0, b.length);
        } catch (IOException e) {
            sinkException.set(e);
            throw e;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            gpgSink.write(b, off, len);
        } catch (IOException e) {
            sinkException.set(e);
            throw e;
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            gpgSink.flush();
        } catch (IOException e) {
            sinkException.set(e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        // Do not close sink if it already failed
        if (sinkException.get() == null) {
            gpgSink.close();
        }

        logger.debug("Waiting for GPG background process to complete");
        awaitLatch(gpgStdoutDone, GPG_BACKGROUND_MAX_WAIT_SECONDS,
                "GPG background process");
        awaitLatch(gpgStderrDone, GPG_STDERR_MAX_WAIT_SECONDS,
                "GPG stderr output");

        @Nullable String stderrMessage = stderrMessageRef.get();
        if (stderrMessage != null && !stderrMessage.isEmpty()) {
            logger.warn("GPG error message: {}", stderrMessage);
        }

        throwOnFailure(stdoutException);
        throwOnFailure(stderrException);

        logger.debug("GPG background process completed");
    }

    private void awaitLatch(CountDownLatch latch, long timeout, String operationDescription) throws IOException {
        try {
            if (!latch.await(timeout, TimeUnit.SECONDS)) {
                throw new IllegalStateException(operationDescription + " failed to complete in " + timeout + " seconds!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Got timeout while waiting for " + operationDescription, e);
        }
    }

    private void throwOnFailure(@Nullable Exception e) throws GpgEncrypterException {
        if (e != null) {
            // Let specific exception through - but do not rethrow, as this causes problem with JDK
            // FIXME: Test on Java 20
            if (e instanceof BackupTargetExistsException) {
                throw new BackupTargetExistsException(e.getMessage(), e);
            }
            throw new GpgEncrypterException("GPG IO failed", e);
        }
    }

    /**
     * Starts an external GPG process and two threads to copy data from its stdout and stderr.
     *
     * @return output stream connected to GPG process's stdin
     *
     * @throws GpgEncrypterException if creation of process or threads failed
     */
    private OutputStream startGpgBackgroundProcess() throws GpgEncrypterException {
        try {
            List<String> cmd = List.of(
                    "/usr/bin/gpg",
                    "-q", "--no-permission-warning",
                    "--compress-algo", "none",
                    "--with-colons",
                    "--cipher-algo", "AES256",
                    "--batch", "--no-tty",
                    "--recipient", recipientKeyId.id(),
                    "--encrypt");
            ProcessBuilder pb = new ProcessBuilder()
                    .command(cmd)
                    .redirectErrorStream(false);

            pb.environment().putAll(envOverrides);

            logger.debug("Starting gpg background process: {}", cmd);
            logger.debug("Env: {}", envOverrides);

            Process p = pb.start();

            new Thread(() -> copyErrMessage(p.getErrorStream())).start();
            new Thread(() -> copyToUnderlyingStream(p.getInputStream())).start();

            return new BufferedOutputStream(p.getOutputStream());
        } catch (IOException e) {
            throw new GpgEncrypterException("Failed to create background gpg process", e);
        }
    }

    private void copyErrMessage(InputStream errorStream) {
        try (BufferedInputStream bis = new BufferedInputStream(errorStream)) {
            String msg = new String(bis.readAllBytes(), StandardCharsets.UTF_8);
            stderrMessageRef.set(msg);
            if (!msg.isEmpty()) {
                logger.warn("GPG error:\n{}", msg);
            }
        } catch (BackupException e) {
            stderrException = e;
        } catch (IOException e) {
            stderrException = new IOException("Failed to read GPG error output", e);
        } finally {
            gpgStderrDone.countDown();
        }
    }

    private void copyToUnderlyingStream(InputStream is) {
        byte[] buffer = new byte[BUFFER_SIZE];

        try (BufferedInputStream bis = new BufferedInputStream(is)) {
            int read;
            while ((read = bis.read(buffer)) >= 0) {
                logger.trace("Copying {} bytes from gpg to underlying stream", read);
                for (int i = 0; i < read; i++) {
                    super.write(buffer[i]); // Note, using single-byte method, or loops back to this.write(b)
                }
            }
            logger.debug("Gpg backend copier ending");
        } catch (BackupException e) {
            stdoutException = e;
        } catch (Exception e) {
            stdoutException = new GpgEncrypterException("Failed to copy data from GPG to output stream", e);
        } finally {
            gpgStdoutDone.countDown();
        }
    }
}
