package dk.mada.backup.restore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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
    /** Data line crypt name index start. */
    private static final int IX_CRYPT_NAME_START = 63;
    /** Data line (archive+file) name index start. */
    private static final int IX_NAME_START = 30;
    /** Data line XXH3 index end. */
    private static final int IX_XXH3_END = 29;
    /** Data line length index start. */
    private static final int IX_XXH3_START = 13;
    /** Data line length index end. */
    private static final int IX_LENGTH_END = 12;
    /** Data line MD5 index end. */
    private static final int IX_MD5_END = 62;
    /** Data line MD5 index start. */
    private static final int IX_MD5_START = 30;
    /** Marker prefix for backup set name. */
    private static final String BACKUP_NAME_PREFIX = "# @name: ";
    /** Marker prefix for backup script version. */
    private static final String BACKUP_VERSION_PREFIX = "# @version: ";
    /** Marker prefix for data format version. */
    private static final String DATA_FORMAT_VERSION_PREFIX = "# @data_format_version: ";
    /** Marker prefix for backup key Id used for encryption. */
    private static final String GPG_KEY_ID_PREFIX = "# @gpg_key_id: ";
    /** Marker prefix for backup time string. */
    private static final String TIME_ID_PREFIX = "# @time: ";
    /** Unknown backup version. */
    private static final String UNKNOWN_BACKUP_VERSION = "0";
    /** Unknown GPG key ID */
    private static final GpgId UNKNOWN_GPG_ID = new GpgId("0000000000000000000000000000000000000000");

    /** Creates new instance. */
    public RestoreScriptReader() {
        // empty
    }

    /**
     * Data extracted from an existing restore script.
     *
     * @param name              the name of the set (and thus the name of the restore file)
     * @param location          the location of the backup set
     * @param version           the backup application version
     * @param time              a string describing the backup creation time
     * @param dataFormatVersion the script data format version
     * @param gpgKeyId          the GPG key id used for encryption
     * @param rootFilesV2       a list of V2 root file entries
     * @param filesV2           a list of V2 file entries
     */
    public record RestoreScriptData(String name, Path location, String version, String time, DataFormatVersion dataFormatVersion,
            GpgId gpgKeyId,
            List<DataRootFile> rootFilesV2, List<DataFile> filesV2) {

        /** {@return an empty data instance} */
        public static RestoreScriptData empty() {
            // Provide a dummy non-existing dir as location in case
            // it is ever tried accessed/deleted.
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
            Path nonExistingSetDir = tmpDir.resolve("empty-backup-set");
            return new RestoreScriptData("empty-set", nonExistingSetDir, UNKNOWN_BACKUP_VERSION, "", DataFormatVersion.VERSION_INVALID,
                    UNKNOWN_GPG_ID, List.of(),
                    List.of());
        }

        /** {@return true if the read data appears to be valid} */
        public boolean isValid() {
            return DataFormatVersion.VERSION_INVALID != dataFormatVersion;
        }
    }

    /**
     * V2 information about root file entry.
     *
     * @param name        the file name
     * @param isDirectory true if the root file was a directory
     * @param crypt       information about the encrypted file
     * @param archive     information about the archive file
     */
    public record DataRootFile(String name, boolean isDirectory, DataCrypt crypt, DataArchive archive) {
    }

    /**
     * V2 information about an encrypted file.
     *
     * @param size the size of the file
     * @param xxh3 the XXH3 checksum of the file
     * @param md5  the MD5 checksum of the file
     * @param file the encrypted file
     */
    public record DataCrypt(long size, Xxh3 xxh3, Md5 md5, Path file) {
    }

    /**
     * V2 information about an archive file.
     *
     * @param size the size of the file
     * @param xxh3 the XXH3 checksum of the file
     */
    public record DataArchive(long size, Xxh3 xxh3) {
    }

    /**
     * V2 information about a data file.
     *
     * @param size the size of the file
     * @param xxh3 the XXH3 checksum of the file
     * @param name the file name
     */
    public record DataFile(long size, Xxh3 xxh3, String name) {
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
            Path backupSetDir = Objects.requireNonNull(scriptFile.getParent());
            String script = Files.readString(scriptFile);
            return parseScript(backupSetDir, script);
        } catch (Exception e) {
            logger.warn("Failed to read/parse restore script {}", scriptFile, e);
            return RestoreScriptData.empty();
        }
    }

    /**
     * Parses an existing restore script, extracting relevant data.
     *
     * @param backupSetDir the backup set directory
     * @param script       the restore script
     * @return the data from the script
     */
    public RestoreScriptData parseScript(Path backupSetDir, String script) {
        boolean collectingCrypts = false;
        boolean collectingArchives = false;
        boolean collectingFiles = false;
        List<String> cryptLines = new ArrayList<>();
        List<String> archiveLines = new ArrayList<>();
        List<String> fileLines = new ArrayList<>();
        String name = "unknown";
        String version = UNKNOWN_BACKUP_VERSION;
        GpgId gpgId = UNKNOWN_GPG_ID;
        String time = "";
        DataFormatVersion dataFormatVersion = DataFormatVersion.VERSION_INVALID;

        List<String> lines = script.lines().toList();
        for (String l : lines) {
            if (l.startsWith(BACKUP_NAME_PREFIX)) {
                name = l.substring(BACKUP_NAME_PREFIX.length()).trim();
            }
            if (l.startsWith(BACKUP_VERSION_PREFIX)) {
                version = l.substring(BACKUP_VERSION_PREFIX.length()).trim();
            }
            if (l.startsWith(TIME_ID_PREFIX)) {
                time = l.substring(TIME_ID_PREFIX.length()).trim();
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
            if (l.length() > IX_NAME_START) {
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

                // when data about files has been collected, bail - nothing more of interest
                if (!fileLines.isEmpty()) {
                    break;
                }
            }
        }
        List<DataRootFile> rootFiles = decodeRootFiles(backupSetDir, dataFormatVersion, cryptLines, archiveLines);
        List<DataFile> files = decodeFiles(dataFormatVersion, fileLines);
        return new RestoreScriptData(name, backupSetDir, version, time, dataFormatVersion, gpgId, rootFiles, files);
    }

    private List<DataRootFile> decodeRootFiles(Path backupSetDir, DataFormatVersion dataFormatVersion, List<String> cryptLines,
            List<String> archiveLines) {
        if (dataFormatVersion != DataFormatVersion.VERSION_2) {
            return List.of();
        }
        int rootElementCount = cryptLines.size();
        if (rootElementCount != archiveLines.size()) {
            throw new IllegalStateException("Expect same number of encrypted files and archives!");
        }

        return IntStream.range(0, rootElementCount)
                .mapToObj(i -> {
                    DataCrypt dc = deserializeCryptV2(backupSetDir, cryptLines.get(i));
                    return deserializeArchiveV2(dc, archiveLines.get(i));
                })
                .toList();
    }

    private List<DataFile> decodeFiles(DataFormatVersion dataFormatVersion, List<String> fileLines) {
        if (dataFormatVersion != DataFormatVersion.VERSION_2) {
            return List.of();
        }

        return fileLines.stream()
                .map(this::deserializeFileV2)
                .toList();
    }

    /**
     * Deserializes V2 crypt line which looks like this: "
     * 124221499,1eb326ca04a97a48,de275e40fe159cce2b5f198cad71b0d9,A-D.crypt"
     *
     * @param backupSetDir the backup set directory
     * @param l            the line
     * @return the decrypted data.
     */
    private DataCrypt deserializeCryptV2(Path backupSetDir, String l) {
        logger.trace("See '{}'", l);
        long length = Long.parseLong(l.substring(1, IX_LENGTH_END).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(IX_XXH3_START, IX_XXH3_END));
        Md5 md5 = Md5.ofHex(l.substring(IX_MD5_START, IX_MD5_END));
        String name = l.substring(IX_CRYPT_NAME_START, l.length() - 1);

        return new DataCrypt(length, xxh3, md5, backupSetDir.resolve(name));
    }

    /**
     * Deserializes V2 archive line and uses it to complete a root file description.
     *
     * The archive line looks like this: " 124164608,2957dcbcb03b43e7,./A-D.tar"
     *
     * @param dc the matching encrypted file data
     * @param l  the line
     * @return a root file data entry containing both encrypted and archive information
     */
    private DataRootFile deserializeArchiveV2(DataCrypt dc, String l) {
        long length = Long.parseLong(l.substring(1, IX_LENGTH_END).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(IX_XXH3_START, IX_XXH3_END));
        String name = l.substring(IX_NAME_START, l.length() - 1);
        boolean isDirectory = TarContainerBuilder.Entry.isWrappedFolderName(name);
        name = TarContainerBuilder.Entry.unwrapFolderName(name);

        DataArchive da = new DataArchive(length, xxh3);
        return new DataRootFile(name, isDirectory, dc, da);
    }

    /**
     * Deserializes V2 file line which looks like this: " 124164608,2957dcbcb03b43e7,A-D.tar"
     *
     * @param l the line
     * @return the decrypted data.
     */
    private DataFile deserializeFileV2(String l) {
        long length = Long.parseLong(l.substring(1, IX_LENGTH_END).trim());
        Xxh3 xxh3 = Xxh3.ofHex(l.substring(IX_XXH3_START, IX_XXH3_END));
        String name = l.substring(IX_NAME_START, l.length() - 1);
        return new DataFile(length, xxh3, name);
    }
}
