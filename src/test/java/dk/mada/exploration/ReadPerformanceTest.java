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

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hasher64;
import com.dynatrace.hash4j.hashing.Hashing;

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
 *  
 *
 *  Using channel
 *   : 1581 (1361 digesting)
 *   : 1493 (1306 digesting)
 *   : 1479 (1288 digesting)
 *  Using mapping
 *   : 1240 (1215 digesting)
 *   : 1213 (1203 digesting)
 *   : 1209 (1199 digesting)
 *  Using stream
 *   : 1246 (1112 digesting)
 *   : 1223 (1108 digesting)
 *   : 1233 (1109 digesting)
 *
 * Mapping is clearly fastest.
 * 
 * Testing digest implementations:
 *  
 * JDK SHA-256 digest implementation:
 * 
 *  Using mapping
 *   : 10043 (9937 digesting)
 *   : 9854 (9804 digesting)
 *   : 9866 (9824 digesting)
 *
 * Try https://github.com/corretto/amazon-corretto-crypto-provider
 * (see CORRETTO comment in code):
 *
 * Using mapping
 *  : 9624 (9529 digesting)
 *  : 9532 (9489 digesting)
 *  : 9586 (9548 digesting)
 *  
 * Corretto (using openssl?) is ~3.5% faster on my Intel(R) Core(TM) i5-8250U CPU @ 1.60GHz
 * 
 * 
 * Vs Native:
 *  $ time find /opt/music/0-A/ -type f -exec sha256sum -b '{}' \; > /dev/null
 *  real    0m10,223s
 *  user    0m8,583s
 *  sys     0m1,689s
 * 
 * 
 * Try alternative digest algorithms:
 * 
 * SHA512, around 40% faster (java: 6700ish, sha2512sum: 0m7,3s)
 * Buy cryptographicly secury which is not needed. Just looking to catch file corruption.
 *
 * https://github.com/Cyan4973/xxHash (specifically XXH3):
 *
 *  Using mapping
 *   : 10082 (9964 digesting)
 *   : 9888 (9844 digesting)
 *   : 9959 (9897 digesting)
 *  Using xxh3Mapping
 *   : 1330 (1187 digesting)
 *   : 993 (963 digesting)
 *   : 1029 (994 digesting)
 *  Using xxh3Streaming
 *   : 1498 (554 digesting)
 *   : 1406 (543 digesting)
 *   : 1447 (569 digesting)
 *
 * So 10 times faster. Even on my old CPU. I think it is a winner!
 *
 * Even compared to native implementation, it is OK in java:
 *
 *  $ time find /opt/music/0-A/ -type f -exec xxh64sum '{}' \; >/dev/null
 *                                                                        
 *  real    0m1,863s
 *  user    0m0,574s
 *  sys     0m1,251s
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
        // CORRETTO
//        com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider.install();

        Hasher64 x = Hashing.xxh3_64();
        
        digest = MessageDigest.getInstance("SHA-256");
        byteBuf = ByteBuffer.allocate(MAX_FILE_SIZE);

        Path dir = Paths.get("/opt/music/0-A/ABBA/");
        
        Map<String, FileChecksummer> impls = 
                Map.of(
                        // mapping is clearly fastest
//                        "stream", this::hexChecksum,
//                        "channel", this::hexChecksumByChannel,
                        "xxh3Streaming", this::xxxChecksumByStream,
                        "xxh3Mapping", this::xxxChecksumByMapping,
                        "mapping", this::hexChecksumByMapping
                        );

        impls.entrySet().stream()
            .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
            .forEach(e -> testDir(e.getKey(), dir, e.getValue()));
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

    private FileInfo xxxChecksumByMapping(Path rootDir, Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
                FileChannel channel = raf.getChannel()) {

            MappedByteBuffer mbb =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

            HashStream64 hashStream = Hashing.xxh3_64().hashStream();
            
            long s = System.currentTimeMillis();
            while (mbb.hasRemaining()) {
                int copy = mbb.remaining();
                if (copy > buffer.length) {
                    copy = buffer.length;
                }
                mbb.get(buffer, 0, copy);
                hashStream.putBytes(buffer, 0, copy);
            }
            long total = System.currentTimeMillis() - s;
            totalDigest += total;
            
            return new FileInfo(rootDir.relativize(file), formatter.toHexDigits(hashStream.getAsLong()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute checksum for " + file, e);
        }
    }

    private FileInfo xxxChecksumByStream(Path rootDir, Path file) {
        try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
            int read;
            HashStream64 hashStream = Hashing.xxh3_64().hashStream();
            while ((read = bis.read(buffer)) > 0) {
                long s = System.currentTimeMillis();
                hashStream.putBytes(buffer, 0, read);
                long total = System.currentTimeMillis() - s;
                totalDigest += total;
            }
            
            return new FileInfo(rootDir.relativize(file), formatter.toHexDigits(hashStream.getAsLong()));
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
