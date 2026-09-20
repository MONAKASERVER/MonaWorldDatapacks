package net.monacraft.mwd.resource;

import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.List;

public record ResourceNode(ResourceKey key, JsonElement json, Path archivePath,
                           List<ResourceReference> references, String provider, byte[] binary) {
    public ResourceNode(ResourceKey key, JsonElement json, Path archivePath,
                        List<ResourceReference> references, String provider) {
        this(key, json, archivePath, references, provider, null);
    }

    public boolean isBinary() { return binary != null; }
}
