package net.monacraft.mwd.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentStoreTest {
    @TempDir Path temp;

    @Test void arbitraryWorldNameIsAvailableImmediatelyAfterReload() throws IOException {
        Path worlds = temp.resolve("worlds.yml");
        Files.writeString(worlds, "worlds: {}\n");
        Files.createDirectories(temp.resolve("backups"));

        new AssignmentStore(temp).assign("test_nether", "incendium", "INCENDIUM");
        ConfigurationBundle reloaded = new ConfigManager(temp).load();

        assertThat(reloaded.worlds()).containsKey("test_nether");
        WorldAssignment assignment = reloaded.worlds().get("test_nether");
        assertThat(assignment.enabled()).isTrue();
        assertThat(assignment.environment()).isEqualTo("NETHER");
        assertThat(assignment.profile()).isEqualTo("INCENDIUM");
        assertThat(assignment.datapacks()).extracting(PackAssignment::id).containsExactly("incendium");
    }
}
