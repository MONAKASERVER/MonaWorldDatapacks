package net.monacraft.mwd.resource;

public record ResourceReference(ResourceKey source, ResourceLocation target, String jsonPath, boolean knownSchema) {}

