package net.monacraft.mwd.config;

import net.monacraft.mwd.resource.ResourceLocation;
import java.util.List;

public record WorldAssignment(String worldName, boolean enabled, String environment,
                              List<PackAssignment> datapacks, String profile,
                              ResourceLocation sourceDimension, String strategy,
                              ExistingWorldPolicy existingWorldPolicy) {}

