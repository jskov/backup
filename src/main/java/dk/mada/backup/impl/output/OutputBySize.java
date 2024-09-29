package dk.mada.backup.impl.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.splitter.SplitterOutputStream;

/**
 * Write backup stream into numbered files, split by size.
 *
 * All input is streamed into one big tar archive. The tar stream is encrypted. The encrypted stream is then split into
 * files.
 */
public class OutputBySize implements BackupStreamWriter {
    /** The tar container builder. */
    private TarContainerBuilder tarBuilder;
    /** The active GPG output stream. */
    private GpgEncryptedOutputStream eos;
    /** The active splitter output stream. */
    private SplitterOutputStream sos;

    public OutputBySize(Path targetDir, String name, long cryptSplitSize, GpgStreamInfo gpgInfo) throws GpgEncrypterException {
        sos = new SplitterOutputStream(targetDir, name, ".crypt", cryptSplitSize);
        eos = new GpgEncryptedOutputStream(sos, gpgInfo);
        tarBuilder = new TarContainerBuilder(eos);
    }

    @Override
    public TarContainerBuilder processNextRootElement(String name) {
        // keeps sending output to the same, single tar
        return tarBuilder;
    }

    @Override
    public void close() throws IOException {
        try {
            if (tarBuilder != null) {
                tarBuilder.close();
            }
        } finally {
            try {
                if (eos != null) {
                    eos.close();
                }
            } finally {
                if (sos != null) {
                    sos.close();
                }
            }
        }
    }

    @Override
    public Future<List<Path>> getOutputFiles() {
        return sos.getOutputFiles();
    }
}
