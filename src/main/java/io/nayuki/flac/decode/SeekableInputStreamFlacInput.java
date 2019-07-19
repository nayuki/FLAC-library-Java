package io.nayuki.flac.decode;

import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * Extends Flac low-level input to operate with streams.
 */
public final class SeekableInputStreamFlacInput extends AbstractFlacLowLevelInput {

    private BufferedInputStream stream;
    private final int length;

    public SeekableInputStreamFlacInput(BufferedInputStream io) throws IOException {
        super();
        stream = io;
        length = stream.available();
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public void seekTo(long pos) throws IOException {
        stream.skip(pos);
    }

    @Override
    protected int readUnderlying(byte[] buf, int off, int len) throws IOException {
        return stream.read(buf, off, len);
    }

    public void close() throws IOException {
        stream.close();
    }
}
