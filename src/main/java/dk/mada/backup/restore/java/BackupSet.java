package dk.mada.backup.restore.java;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.DataFormatVersion;
import dk.mada.backup.types.Md5;
import dk.mada.backup.types.Xxh3;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public record BackupSet(
        BackupMetadata backupMetadata, List<Crypt> crypts, List<Archive> archives, List<DataFile> files) {
    public record LocalBackupSet(Path backupSetDir, Path backupSetFile, BackupSet data) {}

    public record BackupMetadata(
            String name,
            String version,
            DataFormatVersion dataFormatVersion,
            String gpgKeyId,
            LocalDateTime time,
            BackupOutputType type) {}

    public record Crypt(long size, Xxh3 xxh, Md5 md5, String name) {
        String pretty() {
            return xxh.hex() + " " + md5.hex() + String.format(" %10d %s", size, name);
        }
    }

    public record Archive(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }

    public record DataFile(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }
}
