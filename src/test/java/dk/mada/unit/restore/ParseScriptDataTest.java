package dk.mada.unit.restore;

import static org.assertj.core.api.Assertions.assertThat;

import dk.mada.backup.restore.DataFormatVersion;
import dk.mada.backup.restore.java.Restore.BackupSet;
import dk.mada.backup.restore.java.Restore.BaseArgs;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParseScriptDataTest {
    @Test
    void canParseScriptData() {
        BackupSet actual = BaseArgs.parseData("""
                # @name: ebooks
                # @version: 1.3.5xxh
                # @data_format_version: 2
                # @gpg_key_id: 1198d09189a8ef47b6009f629f232c6fa98aac7d
                # @time: 2026.03.28-1454
                # @output_type: NAMED

                set -e

                crypts=(
                )
                """.lines().toList());

        assertThat(actual.backupMetadata()).satisfies(bi -> {
            assertThat(bi.name()).isEqualTo("ebooks");
            assertThat(bi.version()).isEqualTo("1.3.5xxh");
            assertThat(bi.dataFormatVersion()).isEqualTo(DataFormatVersion.VERSION_2);
            assertThat(bi.gpgKeyId()).isEqualTo("1198d09189a8ef47b6009f629f232c6fa98aac7d");
            assertThat(bi.time()).isEqualTo(LocalDateTime.of(2026, 3, 28, 14, 54, 0));
        });
    }
}
