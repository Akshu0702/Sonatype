package com.motivitylabs.mls.itsmapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Keeps builds on the local disk under one folder. The file name is built from the tenant and ticket ids only. */
public class LocalDiskBuildStore implements BuildStore {

    private final Path root;

    public LocalDiskBuildStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Stored put(UUID tenant, UUID ticket, InputStream data, long maxBytes) throws IOException {
        String key = tenant + "/" + ticket + "/build.zip";
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "upload-", ".part");
        try {
            MessageDigest digest = sha256();
            long size = 0;
            byte[] buffer = new byte[64 * 1024];
            try (OutputStream out = Files.newOutputStream(temp)) {
                int n;
                while ((n = data.read(buffer)) > 0) {
                    size += n;
                    if (size > maxBytes) {
                        throw new Errors.TooLarge(maxBytes);
                    }
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return new Stored(key, HexFormat.of().formatHex(digest.digest()), size);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // A leftover file is harmless: nothing refers to it.
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Key outside the store");
        }
        return p;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration
    static class Wiring {
        @Bean
        @ConditionalOnMissingBean(BuildStore.class)
        BuildStore buildStore(ItsmProperties properties) {
            return new LocalDiskBuildStore(Path.of(properties.storageDir()));
        }
    }
}
