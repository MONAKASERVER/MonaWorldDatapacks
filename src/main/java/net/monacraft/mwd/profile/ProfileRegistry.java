package net.monacraft.mwd.profile;

import net.monacraft.mwd.pack.DatapackAnalysis;
import java.util.*;

public final class ProfileRegistry {
    private final List<DatapackProfile> profiles = List.of(new IncendiumProfile(), new StellarityProfile(), new GenericWorldgenProfile());
    public DatapackProfile named(String id) {
        return profiles.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown profile: " + id));
    }
    public DatapackProfile detect(DatapackAnalysis analysis) {
        return profiles.stream().filter(p -> p.matches(analysis)).findFirst().orElse(new GenericWorldgenProfile());
    }
    public List<String> ids() { return profiles.stream().map(DatapackProfile::id).toList(); }
}
