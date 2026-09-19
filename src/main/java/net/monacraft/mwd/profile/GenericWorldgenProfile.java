package net.monacraft.mwd.profile;

import net.monacraft.mwd.config.WorldAssignment;
import net.monacraft.mwd.pack.DatapackAnalysis;
import net.monacraft.mwd.resource.*;
import java.util.*;

public final class GenericWorldgenProfile implements DatapackProfile {
    @Override public String id() { return "GENERIC"; }
    @Override public boolean matches(DatapackAnalysis analysis) { return analysis.worldgenCount() > 0; }
    @Override public ResourceLocation sourceDimension() { return new ResourceLocation("minecraft", "overworld"); }
    @Override public List<String> validate(DatapackAnalysis analysis, WorldAssignment assignment) {
        if (analysis.worldgenCount() == 0) return List.of("Pack contains no recognized worldgen resources");
        return List.of();
    }
}

