package net.monacraft.mwd.config;
import java.util.Map;
public record ConfigurationBundle(PluginConfiguration plugin, Map<String, WorldAssignment> worlds) {}

