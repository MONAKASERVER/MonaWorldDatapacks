package net.monacraft.mwd.profile;

import net.monacraft.mwd.config.WorldAssignment;
import net.monacraft.mwd.pack.DatapackAnalysis;
import net.monacraft.mwd.resource.*;
import java.util.*;

public final class StellarityProfile implements DatapackProfile {
    @Override public String id() { return "STELLARITY"; }
    @Override public boolean matches(DatapackAnalysis analysis) {
        return analysis.namespaces().contains("stellarity") || analysis.id().toLowerCase(Locale.ROOT).contains("stellarity");
    }
    @Override public ResourceLocation sourceDimension() { return new ResourceLocation("minecraft", "the_end"); }
    @Override public List<String> validate(DatapackAnalysis analysis, WorldAssignment assignment) {
        List<String> errors = new ArrayList<>();
        if (!matches(analysis)) errors.add("Stellarity profile selected, but no stellarity namespace was detected");
        boolean endWorldgen = analysis.resources().stream().anyMatch(n -> n.key().type().worldgen()
                && (n.key().location().namespace().equals("stellarity") || n.key().location().path().contains("end")));
        if (!endWorldgen) errors.add("No Stellarity-like End worldgen entry was found");
        if (!"THE_END".equalsIgnoreCase(assignment.environment())) errors.add("Stellarity requires environment THE_END");
        return List.copyOf(errors);
    }
}

