package dk.mada.backup.restore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.impl.output.TarContainerBuilder;
import dk.mada.backup.types.GpgId;
import dk.mada.backup.types.Md5;
import dk.mada.backup.types.Xxh3;

/**
 * Restore script reader.
 *
 * Extracts information from restore script in existing backup sets. Used for incrementally updating a backup.
 */
public class RestoreScriptReader {
    private static final Logger logger = LoggerFactory.getLogger(RestoreScriptReader.class);

    /** Marker prefix for backup script version. */
    private static final String BACKUP_VERSION_PREFIX = "# @version: ";
    /** Marker prefix for data format version. */
    private static final String DATA_FORMAT_VERSION_PREFIX = "# @data_format_version: ";
    /** Marker prefix for backup key Id used for encryption. */
    private static final String GPG_KEY_ID_PREFIX = "# @gpg_key_id: ";
    /** Unknown backup version. */
    private static final String UNKNOWN_BACKUP_VERSION = "0";
    /** Unknown GPG key ID */
    private static final GpgId UNKNOWN_GPG_ID = new GpgId("0000000000000000000000000000000000000000");

    /**
     * Data extracted from an existing restore script.
     *
     * @param version           the backup application version
     * @param dataFormatVersion the script data format version
     * @param gpgKeyId          the GPG key id used for encryption
     * @param cryptsV2          a list of V2 crypt entries
     * @param archivesV2        a list of V2 archive entries
     * @param filesV2           a list of V2 file entries
     */
    public record RestoreScriptData(String version, DataFormatVersion dataFormatVersion, GpgId gpgKeyId, List<DataCryptV2> cryptsV2,
            List<DataArchiveV2> archivesV2, List<DataFileV2> filesV2) {

        /** {@return an empty data instance} */
        public static RestoreScriptData empty() {
            return new RestoreScriptData(UNKNOWN_BACKUP_VERSION, DataFormatVersion.VERSION_INVALID, UNKNOWN_GPG_ID, List.of(), List.of(),
                    List.of());
        }

        /** {@return true if the read data appears to be valid} */
        public boolean isValid() {
            return DataFormatVersion.VERSION_INVALID != dataFormatVersion;
        }
    }

    /**
     * V2 information about an encrypted file.
     *
     * @param size the size of the file
     * @param xxh3 the XXH3 checksum of the file
     * @param md5  the MD5 checksum of the file
     * @param name the file name
     */
    public record DataCryptV2(long size, Xxh3 xxh3, Md5 md5, String name) {
    }

    /**
     * V2 information about an archive file.
     *
     * @param size        the size of the file
     * @param xxh3        the XXH3 checksum of the file
     * @param name        the file name
     * @param isDirectory true if the archive contains a directory, false if it contains a root file
     */
    public record DataArchiveV2(long size, Xxh3 xxh3, String name, boolean isDirectory) {
    }

    /**
     * V2 information about a data file.
     *
     * @param size the size of the file
     * @param xxh3 the XXH3 checksum of the file
     * @param name the file name
     */
    public record DataFileV2(long size, Xxh3 xxh3, String name) {
    }

    /**
     * Reads data from an existing restore script.
     *
     * NOTE: the script should self-validate before parsing it to ensure valid data.
     *
     * @param scriptFile the script to read
     * @return the resulting data
     */
    public RestoreScriptData readRestoreScriptData(Path scriptFile) {
        if (!Files.isRegularFile(scriptFile) || !Files.isReadable(scriptFile)) {
            return RestoreScriptData.empty();
        }

        try {
            String script = Files.readString(scriptFile);
            return parseScript(script);
        } catch (Exception e) {
            logger.warn("Failed to read/parse restore script {}", scriptFile, e);
            return RestoreScriptData.empty();
        }
    }

    /**
     * Parses an existing restore script, extracting relevant data.
     *
     * @param script the restore script
     * @return the data from the script
     */
    public RestoreScriptData parseScript(String script) {
        boolean collectingCrypts = false;
        boolean collectingArchives = false;
        boolean collectingFiles = false;
        List<String> cryptLines = new ArrayList<>();
        List<String> archiveLines = new ArrayList<>();
        List<String> fileLines = new ArrayList<>();
        String version = UNKNOWN_BACKUP_VERSION;
        GpgId gpgId = UNKNOWN_GPG_ID;
        DataFormatVersion dataFormatVersion = DataFormatVersion.VERSION_INVALID;

        List<String> lines = script.lines().toList();
        for (String l : lines) {
            if (l.startsWith(BACKUP_VERSION_PREFIX)) {
                version = l.substring(BACKUP_VERSION_PREFIX.length()).trim();
            }
            if (l.startsWith(DATA_FORMAT_VERSION_PREFIX)) {
                dataFormatVersion = DataFormatVersion.parse(l.substring(DATA_FORMAT_VERSION_PREFIX.length()).trim());
            }
            if (l.startsWith(GPG_KEY_ID_PREFIX)) {
                gpgId = new GpgId(l.substring(GPG_KEY_ID_PREFIX.length()).trim());
            }
            if (l.startsWith("crypts=(")) {
                collectingCrypts = true;
            }
            if (l.startsWith("archives=(")) {
                collectingArchives = true;
            }
            if (l.startsWith("files=(")) {
                collectingFiles = true;
            }
            if (l.length() > 20) {
                if (collectingCrypts) {
                    cryptLines.add(l);
                }
                if (collectingArchives) {
                    archiveLines.add(l);
                }
                if (collectingFiles) {
                    fileLines.add(l);
                }
            }
            if (l.startsWith(")")) {
                collectingCrypts = false;
                collectingArchives = false;
                collectingFiles = false;

                // files are the last part of the script of interest
                if (!fileLines.isEmpty()) {
                    break;
                }
            }
        }

        List<DataCryptV2> cryptsV2 = List.of();
        List<DataArchiveV2> archivesV2 = List.of();
        List<DataFileV2> filesV2 = List.of();
        if (dataFormatVersion == DataFormatVersion.VERSION_2) {
            cryptsV2 = cryptLines.stream()
                    .map(this::deserializeCryptV2)
                    .toList();
            archivesV2 = archiveLines.stream()
                    .map(this::deserializeArchiveV2)
                    .toList();
            filesV2 = fileLines.stream()
                    .map(this::deserializeFileV2)
                    .toList();
        }

        return new RestoreScriptData(version, dataFormatVersion, gpgId, cryptsV2, archivesV2, filesV2);
    }

    /**
     * Deserializes V2 crypt line which looks like this: "
     * 124221499,1eb326ca04a97a48,de275e40fe159cce2b5f198cad71b0d9,A-D.crypt"
     *
     * @param l the line
     * @return the decrypted data.
     */
    private DataCryptV2 deserializeCryptV2(String l) {
        logger.trace("See '{}'", l);
        long length = Long.parseLong(l.substring(1, 12).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(13, 29));
        Md5 md5 = Md5.ofHex(l.substring(30, 62));
        String name = l.substring(63, l.length() - 1);
        return new DataCryptV2(length, xxh3, md5, name);
    }

    /**
     * Deserializes V2 archive line which looks like this: " 124164608,2957dcbcb03b43e7,./A-D.tar"
     *
     * @param l the line
     * @return the decrypted data.
     */
    private DataArchiveV2 deserializeArchiveV2(String l) {
        long length = Long.parseLong(l.substring(1, 12).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(13, 29));
        String name = l.substring(30, l.length() - 1);
        boolean isDirectory = TarContainerBuilder.Entry.isWrappedFolderName(name);
        name = TarContainerBuilder.Entry.unwrapFolderName(name);
        return new DataArchiveV2(length, xxh3, name, isDirectory);
    }

    /**
     * Deserializes V2 file line which looks like this: " 124164608,2957dcbcb03b43e7,A-D.tar"
     *
     * @param l the line
     * @return the decrypted data.
     */
    private DataFileV2 deserializeFileV2(String l) {
        long length = Long.parseLong(l.substring(1, 12).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(13, 29));
        String name = l.substring(30, l.length() - 1);
        return new DataFileV2(length, xxh3, name);
    }
}
