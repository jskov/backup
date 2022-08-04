package dk.mada.unit.restorescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.BackupElement;
import dk.mada.backup.ShellEscaper;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;

class RestoreScriptGenerationTest {
    /** Temporary output directory. */
    private @TempDir Path dir;
    /** Temporary repository directory. */
    private @TempDir Path repositoryDir;

    /**
     * The restore script contains sections for each backup element type. These
     * should all be expanded during copy.
     */
    @Test
    void allThreeFileInfoBlocksAreFilled() throws IOException {
        RestoreScriptWriter sut = new RestoreScriptWriter();

        Map<VariableName, String> vars = Map.of(
                VariableName.VERSION, "1.2.7",
                VariableName.DATA_FORMAT_VERSION, "777");

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
                .contains("made with backup version 1.2.7",
                        "@version: 1.2.7",
                        "@data_format_version: 777");
    }

    @Test
    void specialCharsAreEscaped() throws IOException {
        RestoreScriptWriter sut = new RestoreScriptWriter();

        List<BackupElement> files = toBackupElements(
                "Annie Lennox/Medusa/01. Annie Lennox - No More \"I Love You's\".opus",
                "På slaget 12/Hjem til Århus/12 Li`e Midt I Mellen.ogg");

        Path script = dir.resolve("script.sh");
        sut.write(script, Map.of(), List.of(), List.of(), files);

        List<String> lines = Files.readAllLines(script);
        assertThat(lines)
                .contains("Annie Lennox/Medusa/01. Annie Lennox - No More \\\"I Love You's\\\".opus",
                          "På slaget 12/Hjem til Århus/12 Li\\`e Midt I Mellen.ogg");
    }

    @Test
    void restoreScriptIsWrittenToRepository() {
        RestoreScriptWriter sut = new RestoreScriptWriter();

        List<BackupElement> files = toBackupElements("not-relevant.txt");

        String backupTargetPath = "script.sh";
        Path script = dir.resolve(backupTargetPath);
        sut.write(script, Map.of(), List.of(), List.of(), files);
        
        assertThat(script)
            .exists();
        assertThat(repositoryDir.resolve(backupTargetPath))
            .exists();
    }
    
    List<BackupElement> toBackupElements(String... strings) {
        return Arrays.stream(strings)
                .map(TestBackupElement::new)
                .collect(Collectors.toList());
    }

    record TestBackupElement(String path) implements BackupElement {
        @Override
        public String toBackupSummary() {
            return ShellEscaper.toSafeShellString(path());
        }
    }
}
