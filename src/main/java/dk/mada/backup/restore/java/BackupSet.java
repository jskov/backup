package dk.mada.backup.restore.java;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.DataFormatVersion;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.types.Md5;
import dk.mada.backup.types.Xxh3;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Information for restoring a backup set.
 *
 * @param backupMetadata the backup set's metadata
 * @param crypts         the encrypted files contained in the backup set
 * @param archives       the archives contained in the backup set (inside the
 *                       encrypted files)
 * @param files          the data files container in the backup set (inside the
 *                       encrypted archives)
 */
public record BackupSet(
        BackupMetadata backupMetadata, List<Crypt> crypts, List<Archive> archives, List<DataFile> files) {

    /**
     * Parses a restore script to extract the backup set information.
     *
     * @param lines the lines of the restore script
     * @return the parsed backup set
     */
    public static BackupSet parseRestoreScript(List<String> lines) {
        List<Crypt> crypts = new ArrayList<>();
        List<Archive> archives = new ArrayList<>();
        List<DataFile> files = new ArrayList<>();
        int iCrypts = lines.indexOf("crypts=(");
        int iArchives = lines.indexOf("archives=(");
        int iFiles = lines.indexOf("files=(");

        if (iCrypts == -1 || iCrypts >= iArchives) {
            throw new IllegalStateException("Failed to find range for encrypted files!");
        }
        if (iArchives == -1 || iArchives >= iFiles) {
            throw new IllegalStateException("Failed to find range for archive files!");
        }
        if (iFiles == -1) {
            throw new IllegalStateException("Failed to find range for files!");
        }

        BackupMetadata metadata = BackupMetadata.parseRestoreScriptHeader(lines.subList(0, iCrypts));

        for (int i = iCrypts + 1; ; i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            if (")".equals(line)) {
                break;
            }
            String l = line.substring(1, line.length() - 1);
            crypts.add(new Crypt(
                    Long.valueOf(l.substring(0, 11).trim()),
                    Xxh3.ofHex(l.substring(12, 28)),
                    Md5.ofHex(l.substring(29, 61)),
                    l.substring(62)));
        }
        for (int i = iArchives + 1; i < iFiles; i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            if (")".equals(line)) {
                break;
            }
            String l = line.substring(1, line.length() - 1);
            archives.add(new Archive(
                    Long.valueOf(l.substring(0, 11).trim()), Xxh3.ofHex(l.substring(12, 28)), l.substring(29)));
        }
        for (int i = iFiles + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            if (")".equals(line)) {
                break;
            }
            String l = line.substring(1, line.length() - 1);
            files.add(new DataFile(
                    Long.valueOf(l.substring(0, 11).trim()), Xxh3.ofHex(l.substring(12, 28)), l.substring(29)));
        }

        return new BackupSet(metadata, crypts, archives, files);
    }

    /**
     * Backup set metadata.
     *
     * @param name              the name of the backup set
     * @param version           the version of the backup application that created
     *                          the backup set
     * @param dataFormatVersion the format of the backup set
     * @param gpgKeyId          the id of the GPG key used for encrypting the backup
     *                          set
     * @param time              the time the backup was made
     * @param type              the output type of the backup set
     */
    public record BackupMetadata(
            String name,
            String version,
            DataFormatVersion dataFormatVersion,
            String gpgKeyId,
            LocalDateTime time,
            BackupOutputType type) {

        /** The name header. */
        private static final String BACKUP_NAME = "# @name:";
        /** The version header. */
        private static final String BACKUP_VERSION = "# @version:";
        /** The data format version header. */
        private static final String DATA_FORMAT_VERSION = "# @data_format_version:";
        /** The gpg id header. */
        private static final String GPG_KEY_ID = "# @gpg_key_id:";
        /** The time header. */
        private static final String TIME = "# @time:";
        /** The output type header. */
        private static final String OUTPUT_TYPE = "# @output_type:";

        /**
         * Parses the header lines of a restore script to extract backup set metadata.
         *
         * @param lines the header lines of the restore script
         * @return the parsed backup set metadata
         */
        public static BackupMetadata parseRestoreScriptHeader(List<String> lines) {
            String name = null;
            String version = null;
            DataFormatVersion dataFormat = null;
            String gpgKeyId = null;
            LocalDateTime time = null;
            BackupOutputType outputType = null;

            for (String l : lines) {
                if (l.startsWith(BACKUP_NAME)) {
                    name = l.substring(BACKUP_NAME.length()).trim();
                }
                if (l.startsWith(BACKUP_VERSION)) {
                    version = l.substring(BACKUP_VERSION.length()).trim();
                }
                if (l.startsWith(DATA_FORMAT_VERSION)) {
                    dataFormat = DataFormatVersion.parse(
                            l.substring(DATA_FORMAT_VERSION.length()).trim());
                }
                if (l.startsWith(GPG_KEY_ID)) {
                    gpgKeyId = l.substring(GPG_KEY_ID.length()).trim();
                }
                if (l.startsWith(GPG_KEY_ID)) {
                    gpgKeyId = l.substring(GPG_KEY_ID.length()).trim();
                }
                if (l.startsWith(TIME)) {
                    String timeStr = l.substring(TIME.length()).trim();
                    time = RestoreScriptWriter.RESTORE_SCRIPT_TIME_FORMAT.parse(timeStr, LocalDateTime::from);
                }
                if (l.startsWith(OUTPUT_TYPE)) {
                    outputType = BackupOutputType.from(
                            l.substring(OUTPUT_TYPE.length()).trim());
                }
            }
            return new BackupMetadata(
                    Objects.requireNonNull(name, "Did not find backup name"),
                    Objects.requireNonNull(version, "Did not find backup version"),
                    Objects.requireNonNull(dataFormat, "Did not fiond data format"),
                    Objects.requireNonNull(gpgKeyId, "Did not find gpg key"),
                    Objects.requireNonNull(time, "Did not find time"),
                    Objects.requireNonNull(outputType, "Did not find output type"));
        }
    }

    /**
     * Information about an encrypted file in a backup set.
     *
     * @param size the size of the file (as it should appear in the backup set)
     * @param xxh the XXH3 hash of the file
     * @param md5 the MD5 hash of the file (used for integrity checking on Jotta)
     * @param name the name of the file
     */
    public record Crypt(long size, Xxh3 xxh, Md5 md5, String name) {
        String pretty() {
            return xxh.hex() + " " + md5.hex() + String.format(" %10d %s", size, name);
        }
    }

    /**
     * Information about an archive file in a backup set.
     *
     * @param size the size of the file (as it should appear after decryption)
     * @param xxh the XXH3 hash of the file
     * @param name the name of the file
     */
    public record Archive(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }

    /**
     * Information about a data file (i.e. original input file) in a backup set.
     *
     * @param size the size of the file (as it should appear after a backup restore)
     * @param xxh the XXH3 hash of the file
     * @param name the name of the file
     */
    public record DataFile(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }

    /**
     * A local backup set.
     *
     * @param backupSetDir the directory holding the backup set files
     * @param restoreScript the restore script
     * @param backupSetData the backup set information
     */
    public record LocalBackupSet(Path backupSetDir, Path restoreScript, BackupSet backupSetData) {
        /**
         * Parses data from shell restore script.
         *
         * @param restoreScript the restore script
         * @return the parsed data
         */
        public static LocalBackupSet newFromRestoreScript(Path restoreScript) {
            List<String> lines;
            try {
                lines = Files.readAllLines(restoreScript);
                Path backupSetDir = Objects.requireNonNull(restoreScript.getParent());
                return new LocalBackupSet(backupSetDir, restoreScript, parseRestoreScript(lines));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed reading data " + restoreScript, e);
            }
        }
    }
}
