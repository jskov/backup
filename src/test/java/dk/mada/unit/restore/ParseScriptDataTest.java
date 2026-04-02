package dk.mada.unit.restore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dk.mada.backup.restore.java.Restore.BackupSetData;
import dk.mada.backup.restore.java.Restore.BaseArgs;

class ParseScriptDataTest {
    @Test
    void canParseScriptData() {
        BackupSetData actual = BaseArgs.parseData("""
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
        
        assertThat(actual.backupInfo()).satisfies(bi -> {
            assertThat(bi.name()).isEqualTo("ebooks");
            assertThat(bi.version()).isEqualTo("1.3.5xxh");
        });
        
    }
}
