package net.monacraft.mwd.util;

import net.monacraft.mwd.cache.PackHasher;
import net.monacraft.mwd.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class UtilitySecurityTest {
    @TempDir Path temp;

    @Test void sanitizesUnsafeWorldNamesDeterministically() {
        String first = WorldNameSanitizer.namespace("mwd", "イベント Nether 01");
        assertThat(first).matches("[a-z0-9._-]+").startsWith("mwd_");
        assertThat(WorldNameSanitizer.namespace("mwd", "イベント Nether 01")).isEqualTo(first);
        assertThat(WorldNameSanitizer.namespace("mwd", "resource_nether")).isEqualTo("mwd_resource_nether");
    }

    @Test void hashesContentWithSha256() throws IOException {
        Path file = temp.resolve("value.txt"); Files.writeString(file, "abc");
        assertThat(PackHasher.sha256(file)).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test void rejectsZipSlipAndZipBombLimits() throws IOException {
        Path slip = zip("slip.zip", "../server.properties", "bad");
        assertThatThrownBy(() -> new SafeZipReader(slip, ZipLimits.defaults())).isInstanceOf(UnsafeArchiveException.class).hasMessageContaining("ZIP Slip");
        Path large = zip("large.zip", "data/test.txt", "123456789");
        ZipLimits tiny = new ZipLimits(1024, 1024, 4, 10);
        assertThatThrownBy(() -> new SafeZipReader(large, tiny)).isInstanceOf(UnsafeArchiveException.class).hasMessageContaining("size limit");
    }
    private Path zip(String name, String entry, String value) throws IOException {
        Path target = temp.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            out.putNextEntry(new ZipEntry(entry)); out.write(value.getBytes()); out.closeEntry();
        }
        return target;
    }
}

