package dk.mada.backup.splitter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupTargetExistsException;

/**
 * An output stream that splits the stream over several
 * files of a given size.
 */
public class SplitterOutputStream extends OutputStream {
	private static final Logger logger = LoggerFactory.getLogger(SplitterOutputStream.class);
	private final Path targetDir;
	private final String basename;
	private final String suffix;
	private final long openNextFileAtOffset;
	private final List<Path> outputFiles = new ArrayList<>();
	private int counter = 1;
	private long writtenToCurrentFile = 0;
	private OutputStream currentOutputStream = null;
	private CompletableFuture<List<Path>> outputFilesFuture = new CompletableFuture<>();
	
	public SplitterOutputStream(Path targetDir, String basename, String suffix, long sizeLimit) {
		this.targetDir = Objects.requireNonNull(targetDir);
		this.basename = Objects.requireNonNull(basename);
		this.suffix = Objects.requireNonNull(suffix);
		this.openNextFileAtOffset = sizeLimit;
		
		if (sizeLimit < 1) {
			throw new IllegalArgumentException("Size limit must be >1");
		}
	}

	public Future<List<Path>> getOutputFiles() {
		return outputFilesFuture;
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
		closeCurrentFile();
		String name = basename + "-" + String.format("%02d", counter++) + suffix;
		Path outputFile = targetDir.resolve(name);
		
		if (Files.exists(outputFile)) {
			throw new BackupTargetExistsException("Target file " + outputFile + " already exists");
		}

		outputFiles.add(outputFile);
		
		logger.debug("OPENING {}", outputFile);
		
		OutputStream fileOutput = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		currentOutputStream = new BufferedOutputStream(fileOutput);
		writtenToCurrentFile = 0;
	}

	public void closeCurrentFile() throws IOException {
		if (currentOutputStream != null) {
			currentOutputStream.close();
			currentOutputStream = null;
		}
	}

	@Override
	public void close() throws IOException {
		closeCurrentFile();
		outputFilesFuture.complete(Collections.unmodifiableList(outputFiles));
	}
}
