package io.github.jockerCN.common;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Reads Spring resource locations without assuming they are filesystem files. */
public final class SpringResourceUtils {

    private SpringResourceUtils() {
    }

    public static byte[] readBytes(String location) throws IOException {
        Resource resource = SpringProvider.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }

    public static String readString(String location, Charset charset) throws IOException {
        return new String(readBytes(location), charset);
    }

    public static String readUtf8(String location) throws IOException {
        return readString(location, StandardCharsets.UTF_8);
    }
}
