package org.ajtech.schema.io;

import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Parquet {@link InputFile} backed by a byte array, enabling Parquet reads
 * without requiring a filesystem or Hadoop {@code FileSystem}.
 *
 * <p>The input bytes are not copied; the caller must not mutate the provided
 * array for the lifetime of this {@code InMemoryInputFile}.
 *
 * <p>Thread-safe to open multiple {@link SeekableInputStream}s concurrently — each
 * stream maintains its own position.
 */
public final class InMemoryInputFile implements InputFile {

    private final byte[] data;
    private final int offset;
    private final int length;

    public InMemoryInputFile(byte[] data) {
        this(data, 0, Objects.requireNonNull(data, "data").length);
    }

    public InMemoryInputFile(byte[] data, int offset, int length) {
        Objects.requireNonNull(data, "data");
        Objects.checkFromIndexSize(offset, length, data.length);
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public SeekableInputStream newStream() {
        return new ByteArraySeekableInputStream(data, offset, length);
    }

    /**
     * {@link SeekableInputStream} reading from a byte array slice.
     *
     * <p>Each instance owns an independent position cursor, so multiple streams
     * over the same {@link InMemoryInputFile} do not interfere.
     */
    private static final class ByteArraySeekableInputStream extends SeekableInputStream {
        private final byte[] data;
        private final int offset;
        private final int length;
        private int pos;
        private boolean closed;

        ByteArraySeekableInputStream(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
            this.pos = 0;
        }

        @Override
        public long getPos() throws IOException {
            ensureOpen();
            return pos;
        }

        @Override
        public void seek(long newPos) throws IOException {
            ensureOpen();
            if (newPos < 0 || newPos > length) {
                throw new IOException("Seek position " + newPos + " out of bounds [0, " + length + "]");
            }
            this.pos = (int) newPos;
        }

        @Override
        public int read() throws IOException {
            ensureOpen();
            if (pos >= length) return -1;
            return data[offset + pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ensureOpen();
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) return 0;
            if (pos >= length) return -1;
            int toCopy = Math.min(len, length - pos);
            System.arraycopy(data, offset + pos, b, off, toCopy);
            pos += toCopy;
            return toCopy;
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            readFully(bytes, 0, bytes.length);
        }

        @Override
        public void readFully(byte[] bytes, int start, int len) throws IOException {
            ensureOpen();
            Objects.checkFromIndexSize(start, len, bytes.length);
            if (len > length - pos) {
                throw new EOFException("Reached end of stream with " + (len - (length - pos))
                        + " bytes still to read");
            }
            System.arraycopy(data, offset + pos, bytes, start, len);
            pos += len;
        }

        @Override
        public int read(ByteBuffer buf) throws IOException {
            ensureOpen();
            int remaining = buf.remaining();
            if (remaining == 0) return 0;
            if (pos >= length) return -1;
            int toCopy = Math.min(remaining, length - pos);
            buf.put(data, offset + pos, toCopy);
            pos += toCopy;
            return toCopy;
        }

        @Override
        public void readFully(ByteBuffer buf) throws IOException {
            ensureOpen();
            int remaining = buf.remaining();
            if (remaining > length - pos) {
                throw new EOFException("Reached end of stream with " + (remaining - (length - pos))
                        + " bytes still to read");
            }
            buf.put(data, offset + pos, remaining);
            pos += remaining;
        }

        @Override
        public long skip(long n) throws IOException {
            ensureOpen();
            if (n <= 0) return 0;
            long toSkip = Math.min(n, (long) length - pos);
            pos += (int) toSkip;
            return toSkip;
        }

        @Override
        public int available() throws IOException {
            ensureOpen();
            return length - pos;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
        }
    }
}
