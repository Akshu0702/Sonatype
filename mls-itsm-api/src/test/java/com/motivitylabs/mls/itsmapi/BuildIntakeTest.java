package com.motivitylabs.mls.itsmapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildIntakeTest {

    @TempDir
    Path dir;

    static byte[] zip(int payloadBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("build/app.txt"));
            z.write(new byte[payloadBytes]);
            z.closeEntry();
        }
        return out.toByteArray();
    }

    private BuildIntake intake(long max) {
        return new BuildIntake(new LocalDiskBuildStore(dir), new ItsmProperties(List.of("c"), List.of(), dir.toString(), max, List.of()));
    }

    private BuildIntake.Accepted accept(BuildIntake intake, String name, String type, byte[] data) throws Exception {
        return intake.accept(UUID.randomUUID(), UUID.randomUUID(), name, type, new ByteArrayInputStream(data));
    }

    @Test
    void aZipIsStoredWithItsSha256AndSize() throws Exception {
        byte[] data = zip(1000);
        BuildIntake.Accepted a = accept(intake(1_000_000), "my build.zip", "application/zip", data);
        assertThat(a.displayName()).isEqualTo("my build.zip");
        assertThat(a.stored().size()).isEqualTo(data.length);
        assertThat(a.stored().sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        assertThat(Files.readAllBytes(dir.resolve(a.stored().key()))).isEqualTo(data);
    }

    @Test
    void theStoredKeyNeverContainsWhatTheUploaderTyped() throws Exception {
        BuildIntake.Accepted a = accept(intake(1_000_000), "../../etc/passwd.zip", "application/zip", zip(10));
        assertThat(a.displayName()).isEqualTo("passwd.zip");
        assertThat(a.stored().key()).endsWith("/build.zip").doesNotContain("passwd").doesNotContain("..");
    }

    @Test
    void aContentTypeWithParametersIsAccepted() throws Exception {
        assertThat(accept(intake(1_000_000), "a.ZIP", "application/x-zip-compressed; charset=binary", zip(10)).stored().size()).isPositive();
    }

    @Test
    void aFileThatIsNotNamedZipIsRefused() {
        assertThatThrownBy(() -> accept(intake(1_000_000), "build.tar.gz", "application/zip", zip(10))).isInstanceOf(Errors.BadRequest.class).hasMessageContaining(".zip");
    }

    @Test
    void aWrongContentTypeIsRefused() {
        assertThatThrownBy(() -> accept(intake(1_000_000), "a.zip", "text/html", zip(10))).isInstanceOf(Errors.BadRequest.class).hasMessageContaining("content type");
        assertThatThrownBy(() -> accept(intake(1_000_000), "a.zip", null, zip(10))).isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void aFileThatIsNamedAndTypedAsZipButIsNotOneIsRefused() {
        assertThatThrownBy(() -> accept(intake(1_000_000), "a.zip", "application/zip", "<html>not a zip</html>".getBytes())).isInstanceOf(Errors.BadRequest.class).hasMessageContaining("not a ZIP");
    }

    @Test
    void anEmptyFileIsRefused() {
        assertThatThrownBy(() -> accept(intake(1_000_000), "a.zip", "application/zip", new byte[0])).isInstanceOf(Errors.BadRequest.class).hasMessageContaining("empty");
    }

    @Test
    void aFileOverTheLimitIsRefusedAndNothingIsLeftOnDisk() throws Exception {
        byte[] data = zip(5000);
        BuildIntake small = intake(data.length - 1L);
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> small.accept(tenant, UUID.randomUUID(), "a.zip", "application/zip", new ByteArrayInputStream(data))).isInstanceOf(Errors.TooLarge.class);
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void aFileExactlyAtTheLimitIsAccepted() throws Exception {
        byte[] data = zip(5000);
        assertThat(accept(intake(data.length), "a.zip", "application/zip", data).stored().size()).isEqualTo(data.length);
    }
}
