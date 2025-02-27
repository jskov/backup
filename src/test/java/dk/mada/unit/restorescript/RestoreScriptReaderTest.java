package dk.mada.unit.restorescript;

import static org.assertj.core.api.Assertions.assertThat;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.DataFormatVersion;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptReader.DataArchive;
import dk.mada.backup.restore.RestoreScriptReader.DataCrypt;
import dk.mada.backup.restore.RestoreScriptReader.DataFile;
import dk.mada.backup.restore.RestoreScriptReader.DataRootFile;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;
import dk.mada.backup.types.GpgId;
import dk.mada.backup.types.Md5;
import dk.mada.backup.types.Xxh3;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests reading data back from an existing restore script.
 */
class RestoreScriptReaderTest {
    /** Standard header to use in tests *not* interested in these values. */
    private static final String HEADER =
            """
            # @version: 1.0.0
            # @data_format_version: 2
            # @gpg_key_id: 0123456789012345678901234567890123456789
            # @time: 2024.12.31-17.01
            # @output_type: pip
            """;
    /** The subject under test. */
    private RestoreScriptReader sut = new RestoreScriptReader();

    @TempDir
    private Path backupDir;

    @Test
    void invalidInputGivesEmpty() {
        RestoreScriptData data =
                sut.parseScript(backupDir, """
                # not a valid script
                """);

        assertThat(data.isValid()).isFalse();
    }

    @Test
    void canReadHeader() {
        RestoreScriptData data = sut.parseScript(
                backupDir,
                """
                # @name: name-is-defined
                # @version: 1.2.3
                # @data_format_version: 1
                # @gpg_key_id: 7012345678901234567890123456789012345678
                # @time: 2024.10.31-09.02
                # @output_type: named
                """);

        assertThat(data.name()).isEqualTo("name-is-defined");
        assertThat(data.dataFormatVersion()).isEqualTo(DataFormatVersion.VERSION_1);
        assertThat(data.time()).isEqualTo("2024.10.31-09.02");
        assertThat(data.version()).isEqualTo("1.2.3");
        assertThat(data.gpgKeyId()).isEqualTo(new GpgId("7012345678901234567890123456789012345678"));
        assertThat(data.dataType()).isEqualTo(BackupOutputType.NAMED);
    }

    @Test
    void canReadV2Data() {
        RestoreScriptData data = sut.parseScript(
                backupDir,
                HEADER
                        + """
                        crypts=(
                        "  124221499,1eb326ca04a97a48,de275e40fe159cce2b5f198cad71b0d9,A-D.crypt"
                        "   69264274,663b0cb7a10aaa62,9d9576cec753d39605e22e9937816448,E-H.crypt"
                        "        140,223b0cb7a10aaa62,4d9576cec753d39605e22e9937816448,info.txt.crypt"
                        )
                        archives=(
                        "  124164608,2957dcbcb03b43e7,./A-D.tar"
                        "   69231616,9753f3e03054f863,./E-H.tar"
                        "        120,4453f3e03054f863,info.txt"
                        )
                        files=(
                        "    1021388,73ac231869538a9d,A-D/Abrahams, Tom/Descent - Tom Abrahams.epub"
                        "     812232,7a2ab3217b3baa58,A-D/Abrahams, Tom/Spaceman - Tom Abrahams.epub"
                        "    2155175,3a23e1befbac7319,A-D/Anderson, Kevin J./2113 - Stories Inspired by...epub"
                        "     425116,228e8f28402bbc54,A-D/Anderson, Kevin J./Climbing Olympus - Kevin J. Anderson.epub"
                        "        100,668e8f28402bbc54,info.txt"
                        )
                        """);

        assertThat(data.rootFilesV2())
                .containsExactly(
                        new DataRootFile(
                                "A-D",
                                true,
                                new DataCrypt(
                                        124221499L,
                                        Xxh3.ofHex("1eb326ca04a97a48"),
                                        Md5.ofHex("de275e40fe159cce2b5f198cad71b0d9"),
                                        backupDir.resolve("A-D.crypt")),
                                new DataArchive(124164608L, Xxh3.ofHex("2957dcbcb03b43e7"))),
                        new DataRootFile(
                                "E-H",
                                true,
                                new DataCrypt(
                                        69264274L,
                                        Xxh3.ofHex("663b0cb7a10aaa62"),
                                        Md5.ofHex("9d9576cec753d39605e22e9937816448"),
                                        backupDir.resolve("E-H.crypt")),
                                new DataArchive(69231616L, Xxh3.ofHex("9753f3e03054f863"))),
                        new DataRootFile(
                                "info.txt",
                                false,
                                new DataCrypt(
                                        140L,
                                        Xxh3.ofHex("223b0cb7a10aaa62"),
                                        Md5.ofHex("4d9576cec753d39605e22e9937816448"),
                                        backupDir.resolve("info.txt.crypt")),
                                new DataArchive(120L, Xxh3.ofHex("4453f3e03054f863"))));

        assertThat(data.filesV2())
                .contains(
                        new DataFile(
                                1021388L,
                                Xxh3.ofHex("73ac231869538a9d"),
                                "A-D/Abrahams, Tom/Descent - Tom Abrahams.epub"),
                        new DataFile(
                                2155175L,
                                Xxh3.ofHex("3a23e1befbac7319"),
                                "A-D/Anderson, Kevin J./2113 - Stories Inspired by...epub"),
                        new DataFile(100L, Xxh3.ofHex("668e8f28402bbc54"), "info.txt"));
    }
}
