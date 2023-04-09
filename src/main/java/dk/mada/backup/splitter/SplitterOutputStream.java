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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupTargetExistsException;

/**
 * An output stream that splits the stream over several files of a given size.
 */
public final class SplitterOutputStream extends OutputStream {
    private static final Logger logger = LoggerFactory.getLogger(SplitterOutputStream.class);

    /** Target directory for the files split from the stream. */
    private final Path targetDir;
    /** Base name of the output files. */
    private final String basename;
    /** Suffix to give the output files. */
    private final String suffix;
    /** Active file size limit. */
    private final long openNextFileAtOffset;
    /** Accruing list of files created from the stream. */
    private final List<Path> outputFiles = new ArrayList<>();
    /** The current file being written to. */
    @Nullable private OutputStream currentOutputStream = null;
    /** Bytes written to the current file. */
    private long writtenToCurrentFile = 0;
    /** Number of files written. */
    private int fileCounter = 0;
    /** Future for handing over the list of created files to the caller. */
    private CompletableFuture<List<Path>> outputFilesFuture = new CompletableFuture<>();

    /**
     * Split output stream over a number of files of a given size.
     *
     * @param targetDir the directory to store the files in
     * @param basename  the base name of the files
     * @param suffix    the suffix for the files
     * @param sizeLimit the size limit for the files
     */
    public SplitterOutputStream(Path targetDir, String basename, String suffix, long sizeLimit) {
        this.targetDir = Objects.requireNonNull(targetDir);
        this.basename = Objects.requireNonNull(basename);
        this.suffix = Objects.requireNonNull(suffix);
        this.openNextFileAtOffset = sizeLimit;

        if (sizeLimit < 1) {
            throw new IllegalArgumentException("Size limit must be >1");
        }
    }

    /** {@return the future containing the output files} */
    public Future<List<Path>> getOutputFiles() {
        return outputFilesFuture;
    }

    @Override
    public void write(int b) throws IOException {
        if (currentOutputStream == null
                || writtenToCurrentFile >= openNextFileAtOffset) {
            currentOutputStream = openNextFile();
        }
        writtenToCurrentFile++;
        currentOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (currentOutputStream == null) {
            currentOutputStream = openNextFile();
        }

        // do it piecemeal if limit is reached
        if (writtenToCurrentFile + len >= openNextFileAtOffset) {
            super.write(b, off, len);
        } else {
            // otherwise bulk write for performance
            writtenToCurrentFile += len;
            currentOutputStream.write(b, off, len);
        }
    }

    private OutputStream openNextFile() throws IOException {
        closeCurrentFile();
        String name = basename + "-" + String.format("%02d", ++fileCounter) + suffix;
        Path outputFile = targetDir.resolve(name);

        if (Files.exists(outputFile)) {
            // close before failing - or wrapping streams will fail when they try to flush
            // to this stream
            close();
            throw new BackupTargetExistsException("Target file " + outputFile + " already exists");
        }

        outputFiles.add(outputFile);

        logger.debug("OPENING {}", outputFile);

        OutputStream fileOutput = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        writtenToCurrentFile = 0;
        return new BufferedOutputStream(fileOutput);
    }

    private void closeCurrentFile() throws IOException {
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
