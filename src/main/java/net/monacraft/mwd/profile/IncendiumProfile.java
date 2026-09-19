package net.monacraft.mwd.profile;

import net.monacraft.mwd.config.WorldAssignment;
import net.monacraft.mwd.pack.DatapackAnalysis;
import net.monacraft.mwd.resource.*;
import java.util.*;

public final class IncendiumProfile implements DatapackProfile {
    @Override public String id() { return "INCENDIUM"; }
    @Override public boolean matches(DatapackAnalysis analysis) {
        return analysis.namespaces().contains("incendium") || analysis.id().toLowerCase(Locale.ROOT).contains("incendium");
    }
    @Override public ResourceLocation sourceDimension() { return new ResourceLocation("minecraft", "the_nether"); }
    @Override public List<String> validate(DatapackAnalysis analysis, WorldAssignment assignment) {
        List<String> errors = new ArrayList<>();
        if (!matches(analysis)) errors.add("Incendium profile selected, but no incendium namespace was detected");
        boolean netherOverride = analysis.resources().stream().anyMatch(n -> n.key().type() == ResourceType.NOISE_SETTINGS
                && n.key().location().equals(new ResourceLocation("minecraft", "nether")));
        boolean customNether = analysis.resources().stream().anyMatch(n -> n.key().type() == ResourceType.NOISE_SETTINGS
                && n.key().location().namespace().equals("incendium"));
        if (!netherOverride && !customNether) errors.add("No Incendium-like Nether noise_settings entry was found");
        if (!"NETHER".equalsIgnoreCase(assignment.environment())) errors.add("Incendium requires environment NETHER");
        return List.copyOf(errors);
    }
}

