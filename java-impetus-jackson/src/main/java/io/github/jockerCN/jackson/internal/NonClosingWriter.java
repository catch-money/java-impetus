package io.github.jockerCN.jackson.internal;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/** Allows a JSON engine to close its writer without taking ownership of the caller's writer. */
public final class NonClosingWriter extends FilterWriter {

    public NonClosingWriter(Writer writer) {
        super(writer);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
