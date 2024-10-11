package dk.mada.unit.splitter;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.splitter.SplitterOutputStream;

/**
 * Splitter splits stream into several files.
 */
class SplitterTest {
    /** Temporary output directory. */
    private @TempDir Path targetDir;

    /**
     * Should fail if backup is going to overwrite an existing file.
     */
    @Test
    void shouldAvoidOverwritingFiles() throws IOException {
        Files.createFile(targetDir.resolve("basename-01.tar"));
        assertThatThrownBy(() -> writeSplitterOutput("test", 2)).isInstanceOf(BackupTargetExistsException.class);
    }

    @Test
    void shouldSplitStreamOverSeveralFiles() throws IOException {
        String text = "Test text to be split";
        int splitLength = 7; // NOSONAR - causes split of text into three parts

        writeSplitterOutput(text, splitLength);

        List<Path> files = getListOfGeneratedFiles();

        assertThat(files)
                .extracting(p -> p.getFileName().toString())
                .containsExactly("basename-01.tar", "basename-02.tar", "basename-03.tar");

        String reassembledText = reassembleText(files);
        assertThat(reassembledText)
                .isEqualTo(text);
    }

    private void writeSplitterOutput(String text, long sizeLimit) throws IOException {
        try (OutputStream os = new SplitterOutputStream(targetDir, "basename", ".tar", sizeLimit)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String reassembleText(List<Path> files) {
        return files.stream()
                .map(this::readFile)
                .collect(Collectors.joining());
    }

    private List<Path> getListOfGeneratedFiles() throws IOException {
        List<Path> files;
        try (Stream<Path> fileStream = Files.list(targetDir)) {
            files = fileStream
                    .sorted()
                    .toList();
        }
        return files;
    }

    private String readFile(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
