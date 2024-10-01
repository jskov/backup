package dk.mada.unit.size;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.api.BackupOutputType;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;

/**
 * It should be possible to size limit the output files.
 */
class SizeLimitTest {
    /** Encryption file size limit. */
    private static final long MAX_ENCRYPT_FILE_SIZE = 8000L;
    /** Temporary output directory. */
    private @TempDir Path targetDir;
    /** The backup API - SUT */
    private BackupApi api;
    /** The prepared backup input tree. */
    private static Path srcDir;

    @BeforeAll
    static void prepSource() throws IOException, ArchiveException {
        srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
    }

    @BeforeEach
    void createBackupApi() {
        long megs4 = 4*1024*1024;
        Limits limits = new Limits(megs4, megs4, MAX_ENCRYPT_FILE_SIZE);
        api = new BackupApi(TestCertificateInfo.TEST_GPG_INFO, BackupOutputType.NUMBERED, limits);
    }

    /**
     * Information about a backup files.
     *
     * @param filename the file name
     * @param size the file size
     */
    private record CryptFile(String filename, long size) { }

    @Test
    void shouldSplitBackupOverSeveralFiles() throws IOException {
        api.makeBackup("test", srcDir, targetDir);

        List<CryptFile> crypted = getCryptFileInfos();

        assertThat(crypted)
            .map(CryptFile::filename)
            .containsExactlyInAnyOrder("test.sh", "test-01.crypt", "test-02.crypt", "test-03.crypt", "test-04.crypt");

        // the parts should split at the requested size
        assertThat(crypted)
            .filteredOn(cf -> cf.filename().endsWith(".crypt"))
            .map(CryptFile::size)
            .contains(MAX_ENCRYPT_FILE_SIZE);
    }

    private List<CryptFile> getCryptFileInfos() throws IOException {
        try (Stream<Path> files = Files.list(targetDir)) {
            return files
                    .map(this::toCryptInfo)
                    .toList();
        }
    }

    private CryptFile toCryptInfo(Path p) {
        try {
            return new CryptFile(p.getFileName().toString(), Files.size(p));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to capture data for " + p, e);
        }
    }
}
