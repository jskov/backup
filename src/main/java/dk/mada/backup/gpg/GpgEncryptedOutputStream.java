package dk.mada.backup.gpg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupException;
import dk.mada.backup.api.BackupTargetExistsException;

/**
 * OutputStream  filter that GPG-encrypts the outgoing stream.
 */
public class GpgEncryptedOutputStream extends FilterOutputStream {
    private static final Logger logger = LoggerFactory.getLogger(GpgEncryptedOutputStream.class);
    private static final int GPG_BACKGROUND_MAX_WAIT_SECONDS = 60;
	private static final int GPG_STDERR_MAX_WAIT_SECONDS = 5;
	private String recipientKeyId;
	private Map<String, String> envOverrides;
	private OutputStream gpgSink;
	private CountDownLatch stdoutDone = new CountDownLatch(1);
	private CountDownLatch stderrDone = new CountDownLatch(1);
	private Exception stdoutException;
	private Exception stderrException;
	private AtomicReference<String> stderrMessageRef = new AtomicReference<>();
	private AtomicReference<IOException> sinkException = new AtomicReference<>();
	
	private GpgEncryptedOutputStream() {
		super(null);
	}

	private GpgEncryptedOutputStream(OutputStream out) {
		super(null);
	}

	public GpgEncryptedOutputStream(OutputStream out, String recipientKeyId, Map<String, String> envOverrides) throws GpgEncrypterException {
		super(out);
		this.recipientKeyId = recipientKeyId;
		this.envOverrides = envOverrides;

		gpgSink = startGpgBackgroundProcess();
	}

	public GpgEncryptedOutputStream(OutputStream out, String recipientKeyId) throws GpgEncrypterException {
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
    public void write(byte b[]) throws IOException {
    	try {
	        gpgSink.write(b, 0, b.length);
		} catch (IOException e) {
			sinkException.set(e);
			throw e;
		}
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
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
    	awaitLatch(stdoutDone, GPG_BACKGROUND_MAX_WAIT_SECONDS,
    	        "GPG background process");
    	awaitLatch(stderrDone, GPG_STDERR_MAX_WAIT_SECONDS,
    	        "GPG stderr output");

    	String stderrMessage = stderrMessageRef.get();
    	if (!stderrMessage.isEmpty()) {
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

    private void throwOnFailure(Exception e) throws GpgEncrypterException {
        if (e != null) {
            // Let specific exception through - but do not rethrow, as this causes problem with JDK
            if (e instanceof BackupTargetExistsException) {
                throw new BackupTargetExistsException(e.getMessage(), e);
            }
            throw new GpgEncrypterException("GPG IO failed", e);
        }
    }

	private OutputStream startGpgBackgroundProcess() throws GpgEncrypterException {
		try {
			List<String> cmd = new ArrayList<>(List.of(
					"/usr/bin/gpg",
					"-q",
					"--no-permission-warning",
					"--compress-algo",
					"none",
					"--with-colons",
					"--cipher-algo",
					"AES256",
					"--batch",
					"--no-tty",
					"--recipient",
					recipientKeyId,
					"--encrypt"));
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
			String msg = new String(bis.readAllBytes());
			stderrMessageRef.set(msg);
			if (!msg.isEmpty()) {
				logger.warn("GPG error:\n{}", msg);
			}
		} catch (BackupException e) {
			stderrException = e;
		} catch (IOException e) {
			stderrException = new IOException("Failed to read GPG error output", e);
		} finally {
			stderrDone.countDown();
		}
	}

	private void copyToUnderlyingStream(InputStream is) {
		byte[] buffer = new byte[8192];

		try (BufferedInputStream bis = new BufferedInputStream(is)) {
			int read;
			while ((read = bis.read(buffer)) >= 0) {
				logger.trace("Copying {} bytes from gpg to underlying stream", read);
		        for (int i = 0 ; i < read ; i++) {
		            super.write(buffer[i]); // Note, using single-byte method, or loops back to this.write(b)
		        }
			}
			logger.debug("Gpg backend copier ending");
		} catch (BackupException e) {
			stdoutException = e;
		} catch (Exception e) {
			stdoutException = new GpgEncrypterException("Failed to copy data from GPG to output stream", e);
		} finally {
			stdoutDone.countDown();
		}
	}
}
