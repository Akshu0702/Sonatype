package com.motivitylabs.mls.itsmapi;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Checks and stores an uploaded build. What is checked: the file name ends in .zip, the declared content type is a ZIP type, the first bytes
 * really are a ZIP header (the declared type alone can be anything), the file is not empty and not over the size limit. The ZIP is stored
 * as it came and is never unpacked here, so a ZIP bomb cannot hurt this service.
 */
@Component
public class BuildIntake {

    static final List<String> ZIP_TYPES = List.of("application/zip", "application/x-zip-compressed", "application/x-zip");
    private static final byte[] ZIP_HEADER = {'P', 'K', 3, 4};

    public record Accepted(String displayName, BuildStore.Stored stored) {
    }

    private final BuildStore store;
    private final long maxBytes;

    public BuildIntake(BuildStore store, ItsmProperties properties) {
        this.store = store;
        this.maxBytes = properties.uploadMaxBytes();
    }

    public Accepted accept(UUID tenant, UUID ticket, String fileName, String contentType, InputStream data) throws IOException {
        String name = displayName(fileName);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new Errors.BadRequest("The build must be a .zip file");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!ZIP_TYPES.contains(type)) {
            throw new Errors.BadRequest("The build must be sent as a ZIP file (content type application/zip)");
        }
        PushbackInputStream in = new PushbackInputStream(data, ZIP_HEADER.length);
        byte[] head = in.readNBytes(ZIP_HEADER.length);
        if (head.length == 0) {
            throw new Errors.BadRequest("The build file is empty");
        }
        if (!java.util.Arrays.equals(head, ZIP_HEADER)) {
            throw new Errors.BadRequest("The file is not a ZIP archive");
        }
        in.unread(head);
        return new Accepted(name, store.put(tenant, ticket, in, maxBytes));
    }

    /** The name shown to people: no folders, no control characters, at most 120 characters. */
    static String displayName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw new Errors.BadRequest("The build file needs a name");
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
