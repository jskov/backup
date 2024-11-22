package dk.mada.backup.impl.output;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;

import dk.mada.backup.types.Xxh3;

/**
 * In-memory buffer which can be streamed to, backed by a MemorySegement.
 */
public final class MemorySegmentOutputStream extends OutputStream {
    /** The memory used for streaming. */
    private final MemorySegment memory;
    /** The number of valid bytes in the buffer. */
    private long count = 0;

    /**
     * Create new instance.
     *
     * @param size the size of the buffer to allocate.
     */
    public MemorySegmentOutputStream(long size) {
        memory = Arena.global().allocate(size);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
//        MemorySegment ms = MemorySegment.ofArray(b);
//        MemorySegment.copy(ms, off, memory, count, len);
        MemorySegment.copy(b, off, memory, JAVA_BYTE, count, len);
        count = count + len;
    }

    @Override
    public synchronized void write(int b) {
        memory.set(JAVA_BYTE, count, (byte) b);
        count = count + 1;
    }

    /**
     * Resets the buffer location, preparing for the next buffering operation.
     */
    public synchronized void reset() {
        count = 0;
    }

    /**
     * Writes the complete contents of this {@code MemorySegmentOutputStream} to the specified output stream argument, as if
     * by calling the output stream's write method using {@code out.write(buf, 0, count)}.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
     */
    public synchronized void writeTo(OutputStream out) throws IOException {
        consumeSegments((buffer, len) -> out.write(buffer, 0, len));
    }

    /**
     * Accept the buffer contents in segmented byte arrays.
     *
     * This allows the (potential) long sized buffer to be consumed by callers that can only handle the traditional
     * int-sized arrays.
     */
    @FunctionalInterface
    public interface ByteArraySegmentConsumer {
        /**
         * Accepts the next segment of the buffer.
         *
         * @param segment a byte array with the next segment of data
         * @param len     the number of valid bytes in the array (starting from offset 0)
         * @throws IOException if an I/O error occurs.
         */
        void accept(byte[] segment, int len) throws IOException;
    }

    private void consumeSegments(ByteArraySegmentConsumer consumer) throws IOException {
        consumeSegments(new byte[16 * 1024], consumer);
    }

    private synchronized void consumeSegments(byte[] buffer, ByteArraySegmentConsumer consumer) throws IOException {
        int copyBufferSize = buffer.length;
        long offset = 0;
        while (offset < count) {
            long remaining = count - offset;
            int len;
            if (remaining < copyBufferSize) {
                len = (int) remaining;
            } else {
                len = copyBufferSize;
            }
            MemorySegment.copy(memory, JAVA_BYTE, offset, buffer, 0, len);
            offset += len;

            consumer.accept(buffer, len);
        }
    }

    /**
     * Compute XXH3 for data.
     *
     * TODO: Doing this while writing may perform better in the end. FIXME: This should live outside this class
     *
     * @return the computed hash
     */
    public Xxh3 xxh3() {
        try {
            HashStream64 hashStream = Hashing.xxh3_64().hashStream();
            consumeSegments((buffer, len) -> hashStream.putBytes(buffer, 0, len));
            return Xxh3.of(hashStream.getAsLong());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute XXH3", e);
        }
    }

    /** {@return the current buffer count} */
    public long count() {
        return count;
    }
}
