package com.motivitylabs.mls.itsmapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Where uploaded builds are kept. The local-disk store below is what runs now; an S3 store replaces it behind this interface (the key stays
 * {@code <tenant>/<ticket>/build.zip}, never anything the uploader typed).
 */
public interface BuildStore {

    record Stored(String key, String sha256, long size) {
    }

    /** Stores the stream, which must not be longer than {@code maxBytes}, and reports its SHA-256 and size. */
    Stored put(UUID tenant, UUID ticket, InputStream data, long maxBytes) throws IOException;

    void delete(String key);

    InputStream open(String key) throws IOException;
}
