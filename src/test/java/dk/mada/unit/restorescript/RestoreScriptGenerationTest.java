package dk.mada.unit.restorescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
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
     * The restore script contains sections for each backup element type. These should all be expanded during copy.
     */
    @Test
    void allThreeFileInfoBlocksAreFilled() throws IOException {
        Map<VariableName, String> vars = Map.of(
                VariableName.BACKUP_NAME, "some-name",
                VariableName.VERSION, "1.2.7",
                VariableName.BACKUP_OUTPUT_TYPE, "named");

        List<BackupElement> crypts = toBackupElements("backup.tar");
        List<BackupElement> tars = toBackupElements("fun.tar", "sun.tar");
        List<BackupElement> files = toBackupElements("fun/photo1.jpg", "sun/photo2.jpg");

        RestoreScriptWriter sut = new RestoreScriptWriter(vars, crypts, tars, files);

        Path script = dir.resolve("script.sh");
        sut.write(script);

        List<String> lines = Files.readAllLines(script);
        assertThat(lines)
                .containsSequence("crypts=(", "backup.tar", ")")
                .containsSequence("archives=(", "fun.tar", "sun.tar", ")")
                .containsSequence("files=(", "fun/photo1.jpg", "sun/photo2.jpg", ")")
                .doesNotContain("CRYPTS#", "ARCHIVES#", "FILES#");

        String fullText = String.join("\n", lines);
        assertThat(fullText)
                .contains("made with backup version 1.2.7",
                        "@name: some-name",
                        "@version: 1.2.7",
                        "@data_format_version: 2", // this one matching the writer static
                        "@output_type: named");
    }

    @Test
    void specialCharsAreEscaped() throws IOException {
        List<BackupElement> files = toBackupElements(
                "Annie Lennox/Medusa/01. Annie Lennox - No More \"I Love You's\".opus",
                "På slaget 12/Hjem til Århus/12 Li`e Midt I Mellen.ogg");
        RestoreScriptWriter sut = new RestoreScriptWriter(Map.of(), List.of(), List.of(), files);

        Path script = dir.resolve("script.sh");
        sut.write(script);

        List<String> lines = Files.readAllLines(script);
        assertThat(lines)
                .contains("Annie Lennox/Medusa/01. Annie Lennox - No More \\\"I Love You's\\\".opus",
                        "På slaget 12/Hjem til Århus/12 Li\\`e Midt I Mellen.ogg");
    }

    @Disabled("FIXME: still not done")
    @Test
    void restoreScriptIsWrittenToRepository() {

        List<BackupElement> files = toBackupElements("not-relevant.txt");
        RestoreScriptWriter sut = new RestoreScriptWriter(Map.of(), List.of(), List.of(), files);

        String backupTargetPath = "script.sh";
        Path script = dir.resolve(backupTargetPath);
        sut.write(script);

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
