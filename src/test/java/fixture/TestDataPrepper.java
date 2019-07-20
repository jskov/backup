package fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.examples.Expander;

/**
 * Prepares test data.
 * 
 * Test data is in an archive, because Git does not retain
 * empty directories.
 * Test data files are reset to a (arbitrary) static date so that
 * test results do not change over time.
 */
public class TestDataPrepper {
	private static final FileTime ARBITRARY_KNOWN_TIME = FileTime.fromMillis(1561574109070L);

	public static Path prepareTestInputTree(String name) throws IOException, ArchiveException {
		Path srcDir = Paths.get("build/backup-src");
		Files.createDirectories(srcDir);
		
		Path testSetDir = srcDir.resolve(name);
		org.assertj.core.util.Files.delete(testSetDir.toFile());

		Path tar = Paths.get("src/test/data").resolve(name+".tar");
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
