package org.ajtech.schema.io;

import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Parquet {@link OutputFile} backed by a {@link ByteArrayOutputStream}, enabling
 * Parquet writes without requiring a filesystem or Hadoop {@code FileSystem}.
 *
 * <p>The instance owns an internal buffer. Call {@link #toByteArray()} after the
 * Parquet writer has been closed to retrieve the written bytes; calling it before
 * close yields a snapshot that may not contain the footer.
 *
 * <p>Not thread-safe. A single Parquet writer instance is expected to use one
 * {@code InMemoryOutputFile}.
 */
public final class InMemoryOutputFile implements OutputFile {

    private static final long DEFAULT_BLOCK_SIZE = 64L * 1024 * 1024; // 64 MiB — parquet default

    private final ByteArrayOutputStream buffer;
    private final String path;
    private boolean streamCreated;

    public InMemoryOutputFile() {
        this("memory://parquet", 8192);
    }

    public InMemoryOutputFile(String path) {
        this(path, 8192);
    }

    public InMemoryOutputFile(String path, int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be >= 0, was " + initialCapacity);
        }
        this.path = Objects.requireNonNullElse(path, "memory://parquet");
        this.buffer = new ByteArrayOutputStream(initialCapacity);
    }

    @Override
    public PositionOutputStream create(long blockSizeHint) throws IOException {
        return openStream();
    }

    @Override
    public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
        // "Overwrite" semantics for an in-memory file mean "start fresh".
        buffer.reset();
        streamCreated = false;
        return openStream();
    }

    @Override
    public boolean supportsBlockSize() {
        return false;
    }

    @Override
    public long defaultBlockSize() {
        return DEFAULT_BLOCK_SIZE;
    }

    @Override
    public String getPath() {
        return path;
    }

    /**
     * Returns a copy of the bytes written so far. Safe to call after the
     * Parquet writer has been closed; calling it earlier yields whatever has
     * been flushed up to that point (typically not a valid Parquet file until
     * the footer is written on close).
     */
    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

    /**
     * Total bytes written so far.
     */
    public int size() {
        return buffer.size();
    }

    private PositionOutputStream openStream() {
        if (streamCreated) {
            throw new IllegalStateException(
                    "create() has already been called on this InMemoryOutputFile; "
                            + "create a new instance or use createOrOverwrite() to reset.");
        }
        streamCreated = true;
        return new BufferPositionOutputStream(buffer);
    }

    /**
     * A {@link PositionOutputStream} that writes into a {@link ByteArrayOutputStream}.
     *
     * <p>{@link #close()} flushes but does <em>not</em> release the buffer, so the
     * owning {@link InMemoryOutputFile} can still expose its bytes via
     * {@link InMemoryOutputFile#toByteArray()} after the Parquet writer closes.
     */
    private static final class BufferPositionOutputStream extends PositionOutputStream {
        private final ByteArrayOutputStream out;
        private boolean closed;

        BufferPositionOutputStream(ByteArrayOutputStream out) {
            this.out = out;
        }

        @Override
        public long getPos() {
            return out.size();
        }

        @Override
        public void write(int b) throws IOException {
            ensureOpen();
            out.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            ensureOpen();
            out.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ensureOpen();
            Objects.checkFromIndexSize(off, len, b.length);
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            ensureOpen();
            out.flush();
        }

        @Override
        public void close() {
            // Intentionally does not close `out`: the buffer outlives the stream
            // so InMemoryOutputFile#toByteArray() works post-close.
            closed = true;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
        }
    }
}
