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
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

@Command(
        name = "restore",
        mixinStandardHelpOptions = true,
        version = "@@VERSION@@",
        description = "Restore (or verify) mada backup set.",
        scope = ScopeType.INHERIT)
public final class Restore implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(BackupCreator.class);
    /** File reading buffer size. */
    private static final int FILE_READ_BUFFER_SIZE = 8192;

    String BACKUP_NAME = "@@BACKUP_NAME@@";
    String VERSION = "@@VERSION@@";
    String DATA_FORMAT_VERSION = "@@DATA_FORMAT_VERSION@@";
    String BACKUP_KEY_ID = "@@BACKUP_KEY_ID@@";
    String BACKUP_DATE_TIME = "@@BACKUP_DATE_TIME@@";
    String BACKUP_OUTPUT_TYPE = "@@BACKUP_OUTPUT_TYPE@@";

    @Deprecated
    @Nullable Data data;

    @Option(
            names = {"-b", "--backup-set"},
            required = true,
            description = "Define the location of the backup set")
    private Path argBackupSet;

    @Option(
            names = {"-d", "--target-directory"},
            description = "Define the target directory for restore/verification")
    private Path argDirectory;

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        logger.info("See {}", argBackupSet);
        return 0;
    }

    Data parseBackupSet() {
        if (data == null) {
            data = parseData(argBackupSet);
        }
        logger.trace("Parsed {}", data);
        return data;
    }

    Path targetDir() {
        if (argDirectory != null) {
            return argDirectory;
        }
        return Objects.requireNonNull(argBackupSet.getParent());
    }

    
    @Command(name = "info", description = "Print information about backup set")
    void infoSet(@Option(names = "--full") boolean full) {
        Data backup = parseBackupSet();

        println("Backup " + BACKUP_NAME + "\n" + "made with backup version " + VERSION + "\n" + "created on "
                + BACKUP_DATE_TIME + "\n" + "original size @@BACKUP_INPUT_SIZE@@" + "\n" + "encrypted with key id "
                + BACKUP_KEY_ID + "\n");
        
        if (!full) {
            println(backup.crypts().size() + " crypted archive(s) contains "
                + backup.files().size() + " files in " + backup.archives().size() + " nested archives\n");
        } else {
            println("Crypts (" + backup.crypts().size() + ")");
            println(" " + backup.crypts().stream().map(Crypt::pretty).collect(Collectors.joining("\n ")));
            println("Archives (" + backup.archives().size() + ")");
            println(" " + backup.archives().stream().map(Archive::pretty).collect(Collectors.joining("\n ")));
            println("Files (" + backup.files().size() + ")");
            println(" " + backup.files().stream().map(File::pretty).collect(Collectors.joining("\n ")));
        }
    }
    
    @Command(name = "verify", description = "Verification of backup set")
    int verifySet() {
        Path target = targetDir();
        Data backup = parseBackupSet();
        logger.info("Verify encryped files of backup set {} at {}", argBackupSet, target);

        AtomicBoolean failed = new AtomicBoolean(false);
        String output = backup.crypts().stream()
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
        return failed.get() ? -1 : 0;
    }

    //
    //        switch(args.removeFirst()) {
    //        case "-c" -> cmdVerifyCrypts(Paths.get(args.removeFirst()).toRealPath());
    //        case "-j" -> cmdVerifyJotta(args.removeFirst());
    //        default -> usage();
    //        }
    //    }

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
        for (Crypt c : data.crypts()) {
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

    /**
     * Parses data from shell restore script.
     *
     * @param datafile the restore script
     * @return the parsed data
     */
    private Data parseData(Path datafile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(datafile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading data " + datafile, e);
        }

        List<Crypt> crypts = new ArrayList<>();
        List<Archive> archives = new ArrayList<>();
        List<File> files = new ArrayList<>();
        int iCrypts = lines.indexOf("crypts=(");
        int iArchives = lines.indexOf("archives=(");
        int iFiles = lines.indexOf("files=(");
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
            files.add(new File(
                    Long.valueOf(l.substring(0, 11).trim()), Xxh3.ofHex(l.substring(12, 28)), l.substring(29)));
        }

        return new Data(Objects.requireNonNull(datafile.getParent()), datafile, crypts, archives, files);
    }

    private Xxh3 xxhSum(Path file) {
        byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is)) {
            long size = Files.size(file);
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

    public static final void mainz(String[] args) {
        LoggerConfig.loadConfig();
        Instant start = Instant.now();
        //        String data = System.getenv("BACKUP_DATA");
        //        String data = "/var/home/jskov/git/_ebooks_backup_2026/ebooks.sh";
        String data = "/var/home/jskov/git/_music_backup_2026/music.sh";
        try {
            //            new Restore(Paths.get(data)).run(new ArrayList<>(List.of(args)));
            logger.info("Completed in {}", Duration.between(start, Instant.now()));
        } catch (Exception e) {
            logger.error("Failed processing {}", data, e);
            System.exit(1);
        }
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

    record Data(Path backupSetDir, Path backupData, List<Crypt> crypts, List<Archive> archives, List<File> files) {}
}

// EOI - java parser stops after this line (due to byte 0x1a, end-of-input)
