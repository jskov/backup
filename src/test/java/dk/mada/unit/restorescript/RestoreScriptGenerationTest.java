package dk.mada.unit.restorescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.BackupElement;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;
import dk.mada.fixture.DisplayNameCamelCase;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class RestoreScriptGenerationTest {
	@TempDir Path dir;

	/**
	 * The restore script contains sections for each backup
	 * element type. These should all be expanded during copy.
	 */
	@Test
	void allThreeFileInfoBlocksAreFilled() throws IOException {
		RestoreScriptWriter sut = new RestoreScriptWriter();
		
		Map<VariableName, String> vars = Map.of(
				VariableName.VERSION, "1.2.7"
				);

		List<BackupElement> crypts = toBackupElements("backup.tar");
		List<BackupElement> tars = toBackupElements("fun.tar", "sun.tar");
		List<BackupElement> files = toBackupElements("fun/photo1.jpg", "sun/photo2.jpg");
		
		Path script = dir.resolve("script.sh");
		sut.write(script, vars, crypts, tars, files);
		
		
		List<String> lines = Files.readAllLines(script);
		assertThat(lines)
			.containsSequence("crypts=(", "backup.tar", ")")
			.containsSequence("archives=(", "fun.tar", "sun.tar", ")")
			.containsSequence("files=(", "fun/photo1.jpg", "sun/photo2.jpg", ")")
			.doesNotContain("CRYPTS#", "ARCHIVES#", "FILES#");

		String fullText = String.join("\n", lines);
		assertThat(fullText)
			.contains("made with backup version 1.2.7");
	}

	List<BackupElement> toBackupElements(String... strings) {
		return Arrays.stream(strings)
			.map(s -> (BackupElement)() -> s)
			.collect(Collectors.toList());
	}
}
