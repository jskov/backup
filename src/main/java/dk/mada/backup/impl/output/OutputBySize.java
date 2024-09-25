package dk.mada.backup.impl.output;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.splitter.SplitterOutputStream;
import dk.mada.backup.gpg.GpgEncrypterException;

/**
 * Write backup stream into numbered files, split by size.
 *
 * All input is streamed into one big tar archive. The tar stream is encrypted. The encrypted stream is then split into
 * files.
 */
public class OutputBySize implements BackupStreamWriter {
    /** The active tar output stream. */
    private TarArchiveOutputStream tarOs;
    /** The active GPG output stream. */
    private GpgEncryptedOutputStream eos;
    /** The active splitter output stream. */
    private SplitterOutputStream sos;

    public OutputBySize(Path targetDir, String name, long maxCryptFileSize, GpgStreamInfo gpgInfo) throws GpgEncrypterException {
        sos = new SplitterOutputStream(targetDir, name, ".crypt", maxCryptFileSize);
        eos = new GpgEncryptedOutputStream(sos, gpgInfo);
        tarOs = makeTarOutputStream(eos);
    }

    private static TarArchiveOutputStream makeTarOutputStream(OutputStream sink) {
        TarArchiveOutputStream taos = new TarArchiveOutputStream(sink);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        return taos;
    }

    @Override
    public TarArchiveOutputStream processNextElement(String name) {
        // keeps sending output to the same, single tar
        return tarOs;
    }

    @Override
    public void close() throws IOException {
        try {
            if (tarOs != null) {
                tarOs.close();
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
