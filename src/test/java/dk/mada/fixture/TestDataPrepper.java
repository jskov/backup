package dk.mada.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.examples.Expander;

import dk.mada.backup.impl.output.DirectoryDeleter;

/**
 * Prepares test data.
 *
 * Test data is in an archive, because Git does not retain empty directories. Test data files are reset to a (arbitrary)
 * static date so that test results do not change over time.
 */
public final class TestDataPrepper {
    /** Time used for data time to ensure stability in tests. */
    private static final FileTime ARBITRARY_KNOWN_TIME = FileTime.fromMillis(1561574109070L);
    /** The destination directory for test backups. */
    public static final Path BACKUP_DEST_DIR = Paths.get("build/backup-dest");

    private TestDataPrepper() {
    }

    /**
     * Prepares a directory with known contents.
     *
     * @param name the test set name
     * @return a prepared build folder containing the extracted test set with file times set
     */
    public static Path prepareTestInputTree(String name) throws IOException, ArchiveException {
        Path srcDir = Paths.get("build/backup-src").toAbsolutePath();
        Files.createDirectories(srcDir);

        Path testSetDir = srcDir.resolve(name);
        DirectoryDeleter.delete(testSetDir);

        Path tar = Paths.get("src/test/data").resolve(name + ".tar");
        new Expander().expand(tar.toFile(), srcDir.toFile());
        setTimeOfTestFiles(srcDir);

        return testSetDir;
    }

    private static void setTimeOfTestFiles(Path srcDir) throws IOException {
        try (Stream<Path> files = Files.walk(srcDir)) {
            files.forEach(p -> {
                try {
                    Files.setLastModifiedTime(p, ARBITRARY_KNOWN_TIME);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
