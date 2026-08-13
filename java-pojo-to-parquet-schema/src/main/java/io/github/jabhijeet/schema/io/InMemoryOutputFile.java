package io.github.jabhijeet.schema.io;

import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Parquet {@link OutputFile} backed by a {@link ByteArrayOutputStream}, enabling
 * Parquet writes without requiring a filesystem or Hadoop {@code FileSystem}.
 */
public final class InMemoryOutputFile implements OutputFile {

    private static final long DEFAULT_BLOCK_SIZE = 64L * 1024 * 1024;

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

    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

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
            closed = true;
        }

        private void ensureOpen() throws IOException {
            if (closed) throw new IOException("Stream is closed");
        }
    }
}
