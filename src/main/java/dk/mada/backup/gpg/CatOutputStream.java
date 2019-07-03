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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OutputStream that GPG encrypts the outgoing stream.
 */
public class CatOutputStream extends FilterOutputStream {
	private static final Logger logger = LoggerFactory.getLogger(CatOutputStream.class);
	private String recipientKeyId;
	private Map<String, String> envOverrides;
	private OutputStream gpgSink;
	private CountDownLatch gpgProcessEnding = new CountDownLatch(1);

	private CatOutputStream() {
		super(null);
	}

	public CatOutputStream(OutputStream out) throws GpgEncrypterException {
		super(out);

		logger.trace("Trace logging works");
		gpgSink = startGpgBackgroundProcess();
	}

    @Override
    public void write(int b) throws IOException {
    	gpgSink.write(b);
    }

    @Override
    public void write(byte b[]) throws IOException {
        gpgSink.write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
    	gpgSink.write(b, off, len);
    	logger.debug("Wrote {} bytes to gpgSink", len);
    	gpgSink.flush();
    }

    @Override
    public void flush() throws IOException {
    	logger.debug("gpgSink.flush()");
        gpgSink.flush();
    }

    @Override
    public void close() throws IOException {
    	flush();
    	
    	try {
			Thread.sleep(2_000);
		} catch (InterruptedException e1) {
			throw new IllegalStateException("Interrupt while waiting", e1);
		}
    	
    	logger.debug("gpgSink.close()");
    	gpgSink.close();
    	
    	logger.debug("Waiting for GPG background process to complete");
    	try {
			gpgProcessEnding.await(60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new IOException("Got timeout while waiting for GPG background process to complete", e);
		}
    	logger.debug("GPG background process completed");
    }
    
	private OutputStream startGpgBackgroundProcess() throws GpgEncrypterException {
		try {
			List<String> cmd = new ArrayList<>(List.of(
					"/bin/cat"));
			ProcessBuilder pb = new ProcessBuilder()
				.command(cmd)
				.redirectErrorStream(false);
			
			logger.debug("Starting gpg background process: {}", cmd);
			logger.debug("Env: {}", envOverrides);
			
			Process p = pb.start();

			new Thread(() -> copyToUnderlyingStream(p.getInputStream())).start();
			
			return new BufferedOutputStream(p.getOutputStream());
		} catch (IOException e) {
			throw new GpgEncrypterException("Failed to create background gpg process", e);
		}
	}

	private void copyToUnderlyingStream(InputStream is) {
		byte[] buffer = new byte[8192];

		logger.trace("Creating buffer for copying");
		try (BufferedInputStream bis = new BufferedInputStream(is)) {
			logger.trace("Waiting for first read to return");
			int read;
			while ((read = bis.read(buffer)) > 0) {
				logger.trace("Copying {} bytes from gpg to underlying stream", read);
		        for (int i = 0 ; i < read ; i++) {
		            super.write(buffer[i]); // Note, using single-byte method, or loops back to this.write(b)
		        }
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to copy data to crypted sink", e);
		}
		
		logger.debug("Gpg backend copier ending");
		gpgProcessEnding.countDown();
	}
}
