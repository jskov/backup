package dk.mada.backup.restore.java;

import dk.mada.backup.restore.java.BackupSet.Archive;
import dk.mada.backup.restore.java.BackupSet.BackupMetadata;
import dk.mada.backup.restore.java.BackupSet.Crypt;
import dk.mada.backup.restore.java.BackupSet.DataFile;
import dk.mada.backup.restore.java.BackupSet.LocalBackupSet;
import dk.mada.logging.LoggerConfig;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * Backup restore command.
 */
@Command(
        name = "restore",
        mixinStandardHelpOptions = true,
        version = "@@VERSION@@",
        description = "Restore (or verify) mada backup set.",
        scope = ScopeType.INHERIT)
public final class Restore implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(Restore.class);

    @Override
    public Integer call() throws Exception {
        return 0;
    }

    /**
     * Command for printing information about the backup set.
     *
     * @param baseArgs the base arguments
     * @param full a flag for printing full information
     */
    @Command(name = "info", description = "Print information about backup set")
    void infoSet(@Mixin BaseArgs baseArgs, @Option(names = "--full") boolean full) {
        LocalBackupSet backup = baseArgs.readAndParseData();
        BackupSet backupSetData = backup.backupSetData();
        BackupMetadata metadata = backupSetData.backupMetadata();

        logger.info("Backup '" + metadata.name() + "'");
        logger.info(" made with backup version " + metadata.version());
        logger.info(" created on " + metadata.time());
        logger.info(" original size " + "TODO");
        logger.info(" encrypted with key id " + metadata.gpgKeyId());

        if (!full) {
            logger.info(backupSetData.crypts().size() + " crypted archive(s) contains "
                    + backupSetData.files().size() + " files in "
                    + backupSetData.archives().size() + " nested archives\n");
        } else {
            logger.info("Crypts (" + backupSetData.crypts().size() + ")");
            logger.info(" " + backupSetData.crypts().stream().map(Crypt::pretty).collect(Collectors.joining("\n ")));
            logger.info("Archives (" + backupSetData.archives().size() + ")");
            logger.info(
                    " " + backupSetData.archives().stream().map(Archive::pretty).collect(Collectors.joining("\n ")));
            logger.info("Files (" + backupSetData.files().size() + ")");
            logger.info(
                    " " + backupSetData.files().stream().map(DataFile::pretty).collect(Collectors.joining("\n ")));
        }
    }

    public static final class BaseArgs {
        /** The backup set location (restore script location). */
        @Option(
                names = {"-b", "--backup-set"},
                required = true,
                description = "Define the location of the backup set")
        private Path argBackupSet;

        /** The target directory for restore/verification. */
        @Option(
                names = {"-d", "--target-directory"},
                description = "Define the target directory for restore/verification")
        @Nullable private Path argDirectory;

        Path targetDir() {
            if (argDirectory != null) {
                return argDirectory;
            }

            return Objects.requireNonNull(argBackupSet.getParent());
        }

        /** {@return local backup set data read from the specified directory} */
        public LocalBackupSet readAndParseData() {
            return LocalBackupSet.newFromRestoreScript(argBackupSet);
        }
    }

    /**
     * Main CLI entry point.
     *
     * @param args the command line arguments
     */
    public static void main(String... args) {
        System.exit(mainReturn(args));
    }

    /**
     * Main entry point, also used for tests,
     *
     * @param args the command line arguments
     * @return the command result
     */
    public static int mainReturn(String... args) {
        LoggerConfig.loadConfig();
        return new CommandLine(new Restore()).execute(args);
    }
}
