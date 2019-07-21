package unit.splitter;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.splitter.SplitterOutputStream;
import fixture.DisplayNameCamelCase;

/**
 * Splitter splits stream into several files.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class SplitterTest {
	@TempDir Path targetDir;

	@Test
	void shouldAvoidOverwritingFiles() throws IOException {
		assertThatThrownBy(() -> {
			Files.createFile(targetDir.resolve("basename-01.tar"));
			try (OutputStream os = new SplitterOutputStream(targetDir, "basename", ".tar", 2)) {
				os.write("test".getBytes());
			}
		}).isInstanceOf(BackupTargetExistsException.class);
	}
	
	@Test
	void shouldSplitStreamOverSeveralFiles() throws IOException {
		String text = "Test text to be split";
		
		try (OutputStream os = new SplitterOutputStream(targetDir, "basename", ".tar", 7)) {
			os.write(text.getBytes());
		}

		List<Path> files = getListOfGeneratedFiles();

		assertThat(files)
			.extracting(p -> p.getFileName().toString())
			.containsExactly("basename-01.tar", "basename-02.tar", "basename-03.tar");
		
		String reassembledText = reassembleText(files);
		assertThat(reassembledText)
			.isEqualTo(text);
	}

	private String reassembleText(List<Path> files) {
		String assembledText = files.stream()
			.map(this::readFile)
			.collect(Collectors.joining());
		return assembledText;
	}

	private List<Path> getListOfGeneratedFiles() throws IOException {
		List<Path> files;
		try (Stream<Path> fileStream = Files.list(targetDir)) {
			files = fileStream
					.sorted()
					.collect(Collectors.toList());
		}
		return files;
	}
	
	private String readFile(Path f) {
		try {
			return new String(Files.readAllBytes(f));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
