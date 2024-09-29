package dk.mada.backup.impl.output;

import java.io.ByteArrayOutputStream;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;

import dk.mada.backup.types.Xxh3;

/**
 * In-memory buffer which can be streamed to.
 *
 * Based on BYAOS for now, which limits it to 2GB size. Rewrite at some time to allow for long-based storage. See
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/Arena.html
 */
public class InternalBufferStream extends ByteArrayOutputStream {
    /**
     * Create new instance.
     *
     * @param size the size of the buffer to allocate.
     */
    public InternalBufferStream(long size) {
        this(assertSize(size));
    }

    /**
     * Create new instance.
     *
     * This is the only valid constructor for now, as the implementation is based on BYAOS.
     *
     * @param size the size of the buffer to allocate.
     */
    private InternalBufferStream(int size) {
        super(size);
    }

    private static int assertSize(long size) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Current implementation can only handle buffer size " + Integer.MAX_VALUE);
        }
        return (int) size;
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        assertNoGrowth(len);
        super.write(b, off, len);
    }

    @Override
    public synchronized void write(int b) {
        assertNoGrowth(1);
        super.write(b);
    }

    // Call this between clients
    @Override
    public synchronized void reset() {
        super.reset();
    }

    /**
     * Compute XXH3 for data.
     *
     * TODO: Doing this while writing may perform better in the end.
     *
     * @return the computed hash
     */
    public Xxh3 xxh3() {
        HashStream64 hashStream = Hashing.xxh3_64().hashStream();
        hashStream.putBytes(buf, 0, count);
        return Xxh3.of(hashStream.getAsLong());
    }

    private void assertNoGrowth(int len) {
        if (count + len >= buf.length) {
            throw new IllegalStateException("Unable to handle backup inputs larger than " + buf.length);
        }
    }
}
