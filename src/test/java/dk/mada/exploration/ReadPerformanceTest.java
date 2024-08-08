package dk.mada.exploration;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * New design idea:
 *  o keep all data flat (separating into smaller subfolders causes moving data over time)
 *  o skip splitting archive size (tar can handle 50GB+, so can disks and jotta)
 *  o group into archives based on 1st layer folder
 *  o read existing backup set metadata to allow:
 *  o computing file checksums
 *    if matching folder's backup metadata (file count + names + checksums)
 *     if existing crypted archive file matches checksum from metadata
 *       then: no need to update (or write any files)
 *    otherwise: create new backup for folder (re-reading all files again)
 *  
 * This test will determine performance of reading data twice (worst case)
 * compared to the current one-read where archive is created and checksums
 * computed in one go.
 *
 * Theory: linux disk cache should make the second read practically free.
 *  :: does look that way - damn, making checksums is slow
 *
 * This should make for a much better performant backup, and faster sync
 * of updated backup files to both external disks and jotta.
 *
 * Theory 2: will memory mapping of files for checksum be faster / cache better or worse?
 *  :: hm... mapping is marginally faster. but digesting is slower
 */
class ReadPerformanceTest {
    /** File scanning buffer size. */
    private static final int FILE_SCAN_BUFFER_SIZE = 2*8192;
    private static final int MAX_FILE_SIZE = 20*1024*1024;
    private final byte[] buffer = new byte[FILE_SCAN_BUFFER_SIZE];
    private MessageDigest digest;
    private final HexFormat formatter = HexFormat.of();
    private ByteBuffer byteBuf;

    @Test
    void shouldAvoidOverwritingFiles() throws IOException, NoSuchAlgorithmException {
        digest = MessageDigest.getInstance("SHA-256");
        byteBuf = ByteBuffer.allocate(MAX_FILE_SIZE);

        Path dir = Paths.get("/opt/music/0-A/ABBA/");
        
        Map<String, FileChecksummer> impls = 
                Map.of("stream", this::hexChecksum,
                        "channel", this::hexChecksumByChannel,
                        "mapping", this::hexChecksumByMapping);

        impls.forEach((name, fc) -> testDir(name, dir, fc));
    }
    
    private void testDir(String name, Path dir, FileChecksummer checksummer) {
        System.out.println("Using " + name);
        for (int i = 0; i < 3; i++) {
            long start = System.currentTimeMillis();
            scanDir(dir, checksummer);
            long time = System.currentTimeMillis() - start;
            System.out.println(" : " + time + " (" + totalDigest + " digesting)");
            totalDigest = 0;
        }
    }
    
    
    
    private List<FileInfo> scanDir(Path dir, FileChecksummer checksummer) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files
                .filter(Files::isRegularFile)
                .sorted()
                .map(p -> checksummer.process(dir,  p))
                .toList();
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Failed to scan dir " + dir, e);
        }
    }

    long totalDigest = 0;
    private FileInfo hexChecksum(Path rootDir, Path file) {
        digest.reset();
        try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
            int read;
            while ((read = bis.read(buffer)) > 0) {
                long s = System.currentTimeMillis();
                digest.update(buffer, 0, read);
                long total = System.currentTimeMillis() - s;
                totalDigest += total;
            }
            
            return new FileInfo(rootDir.relativize(file), formatter.formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute checksum for " + file, e);
        }
    }

    private FileInfo hexChecksumByMapping(Path rootDir, Path file) {
        digest.reset();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
                FileChannel channel = raf.getChannel()) {

            MappedByteBuffer mbb =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            long s = System.currentTimeMillis();
            digest.update(mbb);
            long total = System.currentTimeMillis() - s;
            totalDigest += total;
            
            return new FileInfo(rootDir.relativize(file), formatter.formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute checksum for " + file, e);
        }
    }

    private FileInfo hexChecksumByChannel(Path rootDir, Path file) {
        byteBuf.clear();
        digest.reset();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
                FileChannel channel = raf.getChannel()) {

            channel.read(byteBuf);
            byteBuf.flip();

            long s = System.currentTimeMillis();
            digest.update(byteBuf);
            long total = System.currentTimeMillis() - s;
            totalDigest += total;
            
            return new FileInfo(rootDir.relativize(file), formatter.formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute checksum for " + file, e);
        }
    }

    record FileInfo(Path file, String checksum) {
    }
    
    @FunctionalInterface
    interface FileChecksummer {
        FileInfo process(Path rootDir, Path file);
    }
}