package net.monacraft.mwd.regen;

import net.monacraft.mwd.compiler.CompilationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class RegenerationRequestStoreTest {
    @TempDir Path temp;

    @Test void movesCustomWorldToRecoverableBackupOnNextBootstrap() throws Exception {
        Path data = dataDirectory();
        Files.writeString(temp.resolve("server.properties"), "level-name=world\n");
        Path world = temp.resolve("world_mwd_test_1_test_1");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "test");
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneId.of("UTC"));
        RegenerationRequestStore store = new RegenerationRequestStore(data, clock);
        store.request("test_1", "mwd_test_1:test_1");

        List<RegenerationRequestStore.Outcome> outcomes = store.process(Map.of("test_1", success()));

        assertThat(outcomes).singleElement().satisfies(outcome -> assertThat(outcome.success()).isTrue());
        assertThat(world).doesNotExist();
        assertThat(data.resolve("backups/regenerated/20260924-120000-000/world_mwd_test_1_test_1/level.dat")).exists();
        assertThat(data.resolve("pending-regeneration")).isEmptyDirectory();
    }

    @Test void missingFolderCompletesRequestSoFreshWorldCanLoad() throws Exception {
        Path data = dataDirectory();
        Files.writeString(temp.resolve("server.properties"), "level-name=world\n");
        RegenerationRequestStore store = new RegenerationRequestStore(data);
        store.request("test_1", "mwd_test_1:test_1");
        assertThat(store.process(Map.of("test_1", success()))).singleElement()
                .satisfies(outcome -> assertThat(outcome.detail()).contains("already absent"));
        assertThat(data.resolve("pending-regeneration")).isEmptyDirectory();
    }

    @Test void mismatchedCompilationKeepsMarkerForSafeRetry() throws Exception {
        Path data = dataDirectory();
        Files.writeString(temp.resolve("server.properties"), "level-name=world\n");
        RegenerationRequestStore store = new RegenerationRequestStore(data);
        store.request("test_1", "mwd_test_1:test_1");
        assertThat(store.process(Map.of())).singleElement().satisfies(outcome -> assertThat(outcome.success()).isFalse());
        assertThat(data.resolve("pending-regeneration")).isNotEmptyDirectory();
    }

    private Path dataDirectory() throws Exception {
        Path data = temp.resolve("plugins/MonaWorldDatapacks");
        Files.createDirectories(data);
        return data;
    }

    private CompilationResult success() {
        return new CompilationResult("test_1", true, false, temp.resolve("compiled.zip"),
                "mwd_test_1", "mwd_test_1:test_1", List.of(), List.of());
    }
}
