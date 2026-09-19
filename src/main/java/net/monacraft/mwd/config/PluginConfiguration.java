package net.monacraft.mwd.config;

import net.monacraft.mwd.security.ZipLimits;

public record PluginConfiguration(boolean debug, boolean cache, String outputFormat,
                                  String namespacePrefix, ZipLimits zipLimits,
                                  boolean strictMode, boolean rejectGlobalRegistryOverrides,
                                  boolean rejectUnknownResources, boolean rejectExistingGeneratedWorld,
                                  boolean allowExperimentalRuntimeScope,
                                  boolean multiverseAutoImport, boolean multiverseValidate) {}

