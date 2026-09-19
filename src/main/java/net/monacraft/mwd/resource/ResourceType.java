package net.monacraft.mwd.resource;

import net.monacraft.mwd.compatibility.ScopeClass;
import java.util.*;

public enum ResourceType {
    DIMENSION("dimension", ScopeClass.WORLDGEN_SCOPABLE),
    DIMENSION_TYPE("dimension_type", ScopeClass.WORLDGEN_SCOPABLE),
    BIOME("worldgen/biome", ScopeClass.WORLDGEN_SCOPABLE),
    NOISE_SETTINGS("worldgen/noise_settings", ScopeClass.WORLDGEN_SCOPABLE),
    DENSITY_FUNCTION("worldgen/density_function", ScopeClass.WORLDGEN_SCOPABLE),
    NOISE("worldgen/noise", ScopeClass.WORLDGEN_SCOPABLE),
    CONFIGURED_FEATURE("worldgen/configured_feature", ScopeClass.WORLDGEN_SCOPABLE),
    PLACED_FEATURE("worldgen/placed_feature", ScopeClass.WORLDGEN_SCOPABLE),
    CONFIGURED_CARVER("worldgen/configured_carver", ScopeClass.WORLDGEN_SCOPABLE),
    STRUCTURE("worldgen/structure", ScopeClass.WORLDGEN_SCOPABLE),
    STRUCTURE_SET("worldgen/structure_set", ScopeClass.WORLDGEN_SCOPABLE),
    TEMPLATE_POOL("worldgen/template_pool", ScopeClass.WORLDGEN_SCOPABLE),
    PROCESSOR_LIST("worldgen/processor_list", ScopeClass.WORLDGEN_SCOPABLE),
    MULTI_NOISE_PARAMETER_LIST("worldgen/multi_noise_biome_source_parameter_list", ScopeClass.WORLDGEN_SCOPABLE),
    TIMELINE("timeline", ScopeClass.WORLDGEN_SCOPABLE),
    TAG("tags", ScopeClass.RUNTIME_SCOPABLE),
    FUNCTION("function", ScopeClass.SERVER_GLOBAL),
    PREDICATE("predicate", ScopeClass.RUNTIME_SCOPABLE),
    LOOT_TABLE("loot_table", ScopeClass.RUNTIME_SCOPABLE),
    ADVANCEMENT("advancement", ScopeClass.SERVER_GLOBAL),
    RECIPE("recipe", ScopeClass.SERVER_GLOBAL),
    ITEM_MODIFIER("item_modifier", ScopeClass.SERVER_GLOBAL),
    ENCHANTMENT("enchantment", ScopeClass.SERVER_GLOBAL),
    DAMAGE_TYPE("damage_type", ScopeClass.SERVER_GLOBAL),
    CHAT_TYPE("chat_type", ScopeClass.SERVER_GLOBAL),
    TRIM_PATTERN("trim_pattern", ScopeClass.SERVER_GLOBAL),
    TRIM_MATERIAL("trim_material", ScopeClass.SERVER_GLOBAL),
    GAME_RULE("game_rule", ScopeClass.SERVER_GLOBAL),
    ZOMBIE_NAUTILUS_VARIANT("zombie_nautilus_variant", ScopeClass.SERVER_GLOBAL),
    UNKNOWN("", ScopeClass.UNKNOWN);

    private static final Map<String, ResourceType> PATHS = new LinkedHashMap<>();
    static {
        for (ResourceType type : values()) if (!type.directory.isEmpty()) PATHS.put(type.directory, type);
        Map.ofEntries(
                Map.entry("tag", TAG),
                Map.entry("functions", FUNCTION), Map.entry("predicates", PREDICATE),
                Map.entry("loot_tables", LOOT_TABLE), Map.entry("advancements", ADVANCEMENT),
                Map.entry("recipes", RECIPE), Map.entry("item_modifiers", ITEM_MODIFIER),
                Map.entry("worldgen/configured_carvers", CONFIGURED_CARVER),
                Map.entry("worldgen/configured_features", CONFIGURED_FEATURE),
                Map.entry("worldgen/placed_features", PLACED_FEATURE),
                Map.entry("worldgen/structure_sets", STRUCTURE_SET),
                Map.entry("worldgen/template_pools", TEMPLATE_POOL),
                Map.entry("worldgen/processor_lists", PROCESSOR_LIST)
        ).forEach(PATHS::put);
    }

    private final String directory;
    private final ScopeClass scope;
    ResourceType(String directory, ScopeClass scope) { this.directory = directory; this.scope = scope; }
    public String directory() { return directory; }
    public ScopeClass scope() { return scope; }
    public boolean worldgen() { return scope == ScopeClass.WORLDGEN_SCOPABLE; }

    public static Match match(String relative) {
        return PATHS.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, ResourceType> e) -> e.getKey().length()).reversed())
                .filter(e -> relative.equals(e.getKey()) || relative.startsWith(e.getKey() + "/"))
                .map(e -> new Match(e.getValue(), e.getKey())).findFirst().orElse(new Match(UNKNOWN, ""));
    }
    public record Match(ResourceType type, String matchedDirectory) {}
}
