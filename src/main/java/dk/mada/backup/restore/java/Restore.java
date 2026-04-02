package dk.mada.backup.restore.java;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import dk.mada.backup.BackupCreator;
import dk.mada.backup.types.Md5;
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
import java.util.ArrayList;
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
    private static final int FILE_READ_BUFFER_SIZE = 4*8192; // 55s
//    private static final int FILE_READ_BUFFER_SIZE = 16*8192; // 55s
    // parallelStreams: 27
    
    private static final String BACKUP_NAME = "# @name:";
    private static final String VERSION = "@@VERSION@@";
    private static final String DATA_FORMAT_VERSION = "@@DATA_FORMAT_VERSION@@";
    private static final String BACKUP_KEY_ID = "@@BACKUP_KEY_ID@@";
    private static final String BACKUP_DATE_TIME = "@@BACKUP_DATE_TIME@@";
    private static final String BACKUP_OUTPUT_TYPE = "@@BACKUP_OUTPUT_TYPE@@";

    @Deprecated
    @Nullable Data data;

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
        Data backup = baseArgs.readAndParseData();
        BackupSetData backupSetData = backup.data();

        println("Backup " + BACKUP_NAME + "\n" + "made with backup version " + VERSION + "\n" + "created on "
                + BACKUP_DATE_TIME + "\n" + "original size @@BACKUP_INPUT_SIZE@@" + "\n" + "encrypted with key id "
                + BACKUP_KEY_ID + "\n");
        
        if (!full) {
            println(backupSetData.crypts().size() + " crypted archive(s) contains "
                + backupSetData.files().size() + " files in " + backupSetData.archives().size() + " nested archives\n");
        } else {
            println("Crypts (" + backupSetData.crypts().size() + ")");
            println(" " + backupSetData.crypts().stream().map(Crypt::pretty).collect(Collectors.joining("\n ")));
            println("Archives (" + backupSetData.archives().size() + ")");
            println(" " + backupSetData.archives().stream().map(Archive::pretty).collect(Collectors.joining("\n ")));
            println("Files (" + backupSetData.files().size() + ")");
            println(" " + backupSetData.files().stream().map(File::pretty).collect(Collectors.joining("\n ")));
        }
    }


    public record BackupInfo(String name, String version) {
    }
    
    
    public static class BaseArgs {
        private static final String BACKUP_NAME = "# @name:";
        private static final String BACKUP_VERSION = "# @version:";

        @Option(
                names = {"-b", "--backup-set"},
                required = true,
                description = "Define the location of the backup set")
        private Path argBackupSet;

        @Option(
                names = {"-d", "--target-directory"},
                description = "Define the target directory for restore/verification")
        private Path argDirectory;

        Path targetDir() {
            if (argDirectory != null) {
                return argDirectory;
            }
            
            return Objects.requireNonNull(argBackupSet.getParent());
        }
        
        /**
         * Parses data from shell restore script.
         *
         * @param datafile the restore script
         * @return the parsed data
         */
        private Data readAndParseData() {
            List<String> lines;
            try {
                lines = Files.readAllLines(argBackupSet);
                return new Data(Objects.requireNonNull(argBackupSet.getParent()), argBackupSet, parseData(lines));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed reading data " + argBackupSet, e);
            }
        }

        public static BackupSetData parseData(List<String> lines) {
            List<Crypt> crypts = new ArrayList<>();
            List<Archive> archives = new ArrayList<>();
            List<File> files = new ArrayList<>();
            int iCrypts = lines.indexOf("crypts=(");
            int iArchives = lines.indexOf("archives=(");
            int iFiles = lines.indexOf("files=(");
            
            String name = null;
            String version = null;
            int dataFormat = 0;
            String gpgKeyId = null;
            String time = null;
            String outputType = null;
                    
            for (int i = 0; i < iCrypts; i++) {
                String l = lines.get(i);
                System.out.println(l);
                if (l.startsWith(BACKUP_NAME)) {
                    name = l.substring(BACKUP_NAME.length()).trim();
                }
                if (l.startsWith(BACKUP_VERSION)) {
                    version = l.substring(BACKUP_VERSION.length()).trim();
                }
                
            }
            BackupInfo backupInfo = new BackupInfo(Objects.requireNonNull(name, "Did not find backup name"), Objects.requireNonNull(version, "Did not find backup version"));
            
            for (int i = iCrypts + 1; iCrypts > 0; i++) {
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
            for (int i = iArchives + 1; iArchives > 0 && i < iFiles; i++) {
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
            for (int i = iFiles + 1; iFiles > 0 && i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty()) {
                    continue;
                }
                if (")".equals(line)) {
                    break;
                }
                String l = line.substring(1, line.length() - 1);
                files.add(new File(
                        Long.valueOf(l.substring(0, 11).trim()), Xxh3.ofHex(l.substring(12, 28)), l.substring(29)));
            }

            return new BackupSetData(backupInfo, crypts, archives, files);
        }
    }
    
    @Command(name = "verify")
    public static class Verify implements Runnable {
        @Mixin BaseArgs a;
        
        public Verify() {}
        
        @Override
        public void run() {
            System.out.println("SEE mixin " + a);
        }
    }        
        @Command(name = "archives", description = "Verification of backup set archives")
        int verifySet(@Mixin BaseArgs baseArgs) {
            Path target = baseArgs.targetDir();
            Data backup = baseArgs.readAndParseData();
            logger.info("Verify encryped files of backup set {} at {}", backup.backupSetFile, target);
    
            Instant start = Instant.now();
            AtomicBoolean failed = new AtomicBoolean(false);
            String output = backup.data.crypts().parallelStream()
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

    void cmdVerifyJotta(String path) {
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
        for (Crypt c : data.data.crypts()) {
            String name = c.name();
            String jottaMd5sum = jottaData.get(name);
            if (jottaMd5sum == null) {
                foundBadChecksum++;
                info(name + " [missing]" + bad);
                // FIXME below
            } else if (jottaMd5sum.equals(c.md5())) {
                info(name + ok);
            } else {
                foundBadChecksum++;
                info(name + bad);
            }
        }

        boolean failed = foundBadChecksum != 0;
        exit(failed, failed ? ("Jotta backup has " + foundBadChecksum + " bad files") : "Jotta backup matches!");
    }

    private void usage() {
        exit(
                """
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

    
    record JottaFile(String name, Md5 md5sum) {}

    record Crypt(long size, Xxh3 xxh, Md5 md5, String name) {
        String pretty() {
            return xxh.hex() + " " + md5.hex() + String.format(" %10d %s", size, name);
        }
    }

    record Archive(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }

    record File(long size, Xxh3 xxh, String name) {
        String pretty() {
            return xxh.hex() + String.format(" %10d %s", size, name);
        }
    }

    record Data(Path backupSetDir, Path backupSetFile, BackupSetData data) {}

    public record BackupSetData(BackupInfo backupInfo, List<Crypt> crypts, List<Archive> archives, List<File> files) {}
}



// EOI - java parser stops after this line (due to byte 0x1a, end-of-input)
