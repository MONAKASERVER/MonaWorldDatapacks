package net.monacraft.mwd.resource;

import java.util.*;

public final class ResourceGraph {
    private final Map<ResourceKey, ResourceNode> nodes;
    public ResourceGraph(Collection<ResourceNode> resources) {
        Map<ResourceKey, ResourceNode> collected = new LinkedHashMap<>();
        for (ResourceNode node : resources) {
            if (collected.putIfAbsent(node.key(), node) != null) throw new IllegalArgumentException("Duplicate resource: " + node.key());
        }
        nodes = Collections.unmodifiableMap(collected);
    }
    public Map<ResourceKey, ResourceNode> nodes() { return nodes; }

    public List<List<ResourceKey>> cycles() {
        List<List<ResourceKey>> cycles = new ArrayList<>();
        Set<ResourceKey> visited = new HashSet<>(), active = new HashSet<>();
        Deque<ResourceKey> stack = new ArrayDeque<>();
        for (ResourceKey key : nodes.keySet()) dfs(key, visited, active, stack, cycles);
        return cycles;
    }
    private void dfs(ResourceKey key, Set<ResourceKey> visited, Set<ResourceKey> active,
                     Deque<ResourceKey> stack, List<List<ResourceKey>> cycles) {
        if (active.contains(key)) {
            List<ResourceKey> path = new ArrayList<>();
            boolean record = false;
            for (ResourceKey item : stack) { if (item.equals(key)) record = true; if (record) path.add(item); }
            path.add(key); cycles.add(path); return;
        }
        if (!visited.add(key)) return;
        active.add(key); stack.addLast(key);
        ResourceNode node = nodes.get(key);
        if (node != null) for (ResourceReference ref : node.references()) {
            resolveExistingKey(ref.target()).ifPresent(next -> dfs(next, visited, active, stack, cycles));
        }
        stack.removeLast(); active.remove(key);
    }

    public Optional<ResourceKey> resolveExistingKey(ResourceLocation location) {
        return nodes.keySet().stream().filter(k -> k.location().equals(location)).findFirst();
    }

    public List<ResourceReference> missingPackReferences(Set<String> packNamespaces) {
        List<ResourceReference> missing = new ArrayList<>();
        for (ResourceNode node : nodes.values()) for (ResourceReference ref : node.references()) {
            // A missing minecraft:* reference is normally a built-in registry entry. Only actual
            // minecraft namespace overrides present in the pack are cloned by the compiler.
            if (!ref.target().namespace().equals("minecraft") && packNamespaces.contains(ref.target().namespace())
                    && resolveExistingKey(ref.target()).isEmpty()) missing.add(ref);
        }
        return missing;
    }
}
