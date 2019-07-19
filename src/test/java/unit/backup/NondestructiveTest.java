package unit.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fixture.DisplayNameCamelCase;
import fixture.TestDataPrepper;

/**
 * The backup program must not overwrite any files - it should
 * fail instead.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class NondestructiveTest {
	@TempDir Path dir;

	/**
	 * XXX
	 */
	@Disabled
	@Test
	void allThreeFileInfoBlocksAreFilled() throws IOException, ArchiveException {
		Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
		Path targetDir = Paths.get("build/backup-dest");
		
		org.assertj.core.util.Files.delete(targetDir.toFile());

		Path script = targetDir.resolve("test.sh");

		
		List<String> lines = Files.readAllLines(script);
		assertThat(lines)
			.containsSequence("crypts=(", "backup.tar", ")")
			.containsSequence("archives=(", "fun.tar", "sun.tar", ")")
			.containsSequence("files=(", "fun/photo1.jpg", "sun/photo2.jpg", ")")
			.doesNotContain("CRYPTS#", "ARCHIVES#", "FILES#");
	}
}
