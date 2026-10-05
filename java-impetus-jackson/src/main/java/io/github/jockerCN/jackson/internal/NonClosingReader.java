package io.github.jockerCN.jackson.internal;

import java.io.FilterReader;
import java.io.Reader;

/** Leaves reader ownership with the caller. */
public final class NonClosingReader extends FilterReader {

    public NonClosingReader(Reader reader) {
        super(reader);
    }

    @Override
    public void close() {
    }
}
