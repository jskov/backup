package dk.mada.backup.restore.java;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import dk.mada.backup.BackupCreator;
import dk.mada.backup.restore.java.BackupSet.Archive;
import dk.mada.backup.restore.java.BackupSet.Crypt;
import dk.mada.backup.restore.java.BackupSet.DataFile;
import dk.mada.backup.restore.java.BackupSet.LocalBackupSet;
import dk.mada.backup.types.Xxh3;
import dk.mada.logging.LoggerConfig;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

@Command(
        name = "restore",
        mixinStandardHelpOptions = true,
        version = "@@VERSION@@",
        description = "Restore (or verify) mada backup set.",
        scope = ScopeType.INHERIT,
        subcommands = dk.mada.backup.restore.java.Restore.Verify.class)
public final class Restore implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(BackupCreator.class);
    /** File reading buffer size. */
    //    private static final int FILE_READ_BUFFER_SIZE = 8192; / 1m4s
    //  private static final int FILE_READ_BUFFER_SIZE = 2*8192; // 58s
    private static final int FILE_READ_BUFFER_SIZE = 4 * 8192; // 55s
    //    private static final int FILE_READ_BUFFER_SIZE = 16*8192; // 55s
    // parallelStreams: 27

    private static final String BACKUP_NAME = "# @name:";
    private static final String VERSION = "@@VERSION@@";
    private static final String BACKUP_KEY_ID = "@@BACKUP_KEY_ID@@";
    private static final String BACKUP_DATE_TIME = "@@BACKUP_DATE_TIME@@";

    @Deprecated
    @Nullable LocalBackupSet data;

    //    @Mixin BaseArgs baseArgs;

    //    @Option(
    //            names = {"-b", "--backup-set"},
    //            required = true,
    //            description = "Define the location of the backup set")
    //    private Path argBackupSet;

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        return 0;
    }

    @Command(name = "info", description = "Print information about backup set")
    void infoSet(@Mixin BaseArgs baseArgs, @Option(names = "--full") boolean full) {
        LocalBackupSet backup = baseArgs.readAndParseData();
        BackupSet backupSetData = backup.backupSetData();

        println("Backup " + BACKUP_NAME + "\n" + "made with backup version " + VERSION + "\n" + "created on "
                + BACKUP_DATE_TIME + "\n" + "original size @@BACKUP_INPUT_SIZE@@" + "\n" + "encrypted with key id "
                + BACKUP_KEY_ID + "\n");

        if (!full) {
            println(backupSetData.crypts().size() + " crypted archive(s) contains "
                    + backupSetData.files().size() + " files in "
                    + backupSetData.archives().size() + " nested archives\n");
        } else {
            println("Crypts (" + backupSetData.crypts().size() + ")");
            println(" " + backupSetData.crypts().stream().map(Crypt::pretty).collect(Collectors.joining("\n ")));
            println("Archives (" + backupSetData.archives().size() + ")");
            println(" " + backupSetData.archives().stream().map(Archive::pretty).collect(Collectors.joining("\n ")));
            println("Files (" + backupSetData.files().size() + ")");
            println(" " + backupSetData.files().stream().map(DataFile::pretty).collect(Collectors.joining("\n ")));
        }
    }

    public static class BaseArgs {
        @Option(
                names = {"-b", "--backup-set"},
                required = true,
                description = "Define the location of the backup set")
        private Path argBackupSet;

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

        public LocalBackupSet readAndParseData() {
            return LocalBackupSet.newFromRestoreScript(argBackupSet);
        }
    }

    @Command(name = "verify")
    public static class Verify implements Runnable {
        @Mixin
        BaseArgs a;

        public Verify() {}

        @Override
        public void run() {
            System.out.println("SEE mixin " + a);
        }
    }

    @Command(name = "archives", description = "Verification of backup set archives")
    int verifySet(@Mixin BaseArgs baseArgs) {
        Path target = baseArgs.targetDir();
        LocalBackupSet backup = baseArgs.readAndParseData();
        logger.info("Verify encryped files of backup set {} at {}", backup.restoreScript(), target);

        Instant start = Instant.now();
        AtomicBoolean failed = new AtomicBoolean(false);
        String output = backup.backupSetData().crypts().parallelStream()
                .map(c -> {
                    Xxh3 sum = xxhSum(target.resolve(c.name()));
                    boolean status = c.xxh().equals(sum);
                    failed.compareAndSet(false, !status);
                    return " - " + c.name() + "... "
                            + (status ? "ok" : ("BAD [expected " + c.xxh() + " was " + sum + "]"));
                })
                .sorted()
                .collect(Collectors.joining("\n"));
        System.out.println(output);
        logger.info("Completed in {}", Duration.between(start, Instant.now()));
        return failed.get() ? -1 : 0;
    }

    @SuppressWarnings("unused")
    void cmdVerifyJotta(BackupSet backupSet, String path) {
        info("Checking backup files at Jotta cloud path " + path);

        String out = runExternalCmd(List.of("jotta-cli", "ls", "-l", "-a", path));

        int nameIx = out.indexOf("Name");
        int nameEndIx = out.indexOf("Size");
        int md5sumIx = out.indexOf("Checksum");

        String ok = " \u2713";
        String bad = " \u274c";

        Map<String, String> jottaData = out.lines()
                .skip(2) // Skip header
                .collect(Collectors.toMap(
                        l -> l.substring(nameIx, nameEndIx).trim(), l -> l.substring(md5sumIx, md5sumIx + 32)));

        int foundBadChecksum = 0;
        for (Crypt c : backupSet.crypts()) {
            String name = c.name();
            String jottaMd5sum = jottaData.get(name);
            if (jottaMd5sum == null) {
                foundBadChecksum++;
                info(name + " [missing]" + bad);
                // FIXME below
            } else if (jottaMd5sum.equals(c.md5().hex())) {
                info(name + ok);
            } else {
                foundBadChecksum++;
                info(name + bad);
            }
        }

        boolean failed = foundBadChecksum != 0;
        exit(failed, failed ? ("Jotta backup has " + foundBadChecksum + " bad files") : "Jotta backup matches!");
    }

    @SuppressWarnings("unused")
    private void usage() {
        exit("""
                Usage:
                 restore [cmd]

                With cmd being one of:

                  info               information about backup
                  info parsed        data as parsed

                  unpack dir         unpacks all files to dir
                  unpack -a dir      unpacks (only) archives to dir

                  verify             verifies crypted backup files (locally)
                  verify -c dir      verifies crypted backup files in dir
                  verify -a dir      verifies decrypted archive files in dir
                  verify -f dir      verifies decrypted and unpacked files in dir
                  verify -s          decrypts and verifies files via streaming - prompts password
                  verify -j path     verifies MD5 checksum of backup files at Jotta path""");
    }

    private Xxh3 xxhSum(Path file) {
        byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is)) {
            HashStream64 hashStream = Hashing.xxh3_64().hashStream();
            int read;
            while ((read = bis.read(buffer)) > 0) {
                hashStream.putBytes(buffer, 0, read);
            }
            return Xxh3.of(hashStream.getAsLong());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compute xxh3 for " + file, e);
        }
    }

    @SuppressWarnings("unused")
    private String md5Sum(Path f) {
        String out = runExternalCmd(List.of("md5sum", f.toAbsolutePath().toString()));
        return out.substring(0, 31);
    }

    private String runExternalCmd(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return p.inputReader().lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed running cmd: " + cmd, e);
        }
    }

    public static void main(String... args) {
        System.exit(mainReturn(args));
    }

    public static int mainReturn(String... args) {
        LoggerConfig.loadConfig();
        return new CommandLine(new Restore()).execute(args);
    }

    private void exit(String msg) {
        exit(false, msg);
    }

    private void exit(boolean failed, String msg) {
        info(msg);
        System.exit(failed ? 1 : 0);
    }

    private static void info(String msg) {
        System.out.println(msg);
    }

    private static void println(String msg) {
        System.out.println(msg);
    }
}
