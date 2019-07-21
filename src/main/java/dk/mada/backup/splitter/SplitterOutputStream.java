package dk.mada.backup.splitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import dk.mada.backup.api.BackupTargetExistsException;

/**
 * An output stream that splits the stream over several
 * files of a given size.
 */
public class SplitterOutputStream extends OutputStream {
	private final Path targetDir;
	private final String basename;
	private final String suffix;
	private final long openNextFileAtOffset;
	private int counter = 1;
	private long writtenToCurrentFile = 0;
	private OutputStream currentOutputStream = null;

	public SplitterOutputStream(Path targetDir, String basename, String suffix, long sizeLimit) {
		this.targetDir = Objects.requireNonNull(targetDir);
		this.basename = Objects.requireNonNull(basename);
		this.suffix = Objects.requireNonNull(suffix);
		this.openNextFileAtOffset = sizeLimit;
		
		if (sizeLimit < 1) {
			throw new IllegalArgumentException("Size limit must be >1");
		}
	}

	@Override
	public void write(int b) throws IOException {
		if (writtenToCurrentFile >= openNextFileAtOffset
				|| currentOutputStream == null) {
			openNextFile();
		}
		writtenToCurrentFile++;
		currentOutputStream.write(b);
	}
	
	private void openNextFile() throws IOException {
		close();
		String name = basename + "-" + String.format("%02d", counter++) + suffix;
		Path tarFile = targetDir.resolve(name);
		
		if (Files.exists(tarFile)) {
			throw new BackupTargetExistsException("Target file " + tarFile + " already exists");
		}
		
		currentOutputStream = Files.newOutputStream(tarFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		writtenToCurrentFile = 0;
	}

	@Override
	public void close() throws IOException {
		if (currentOutputStream != null) {
			currentOutputStream.close();
			currentOutputStream = null;
		}
	}
}
