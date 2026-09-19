package net.monacraft.mwd.profile;

import net.monacraft.mwd.config.WorldAssignment;
import net.monacraft.mwd.pack.DatapackAnalysis;
import net.monacraft.mwd.resource.ResourceLocation;
import java.util.List;

public interface DatapackProfile {
    String id();
    boolean matches(DatapackAnalysis analysis);
    ResourceLocation sourceDimension();
    List<String> validate(DatapackAnalysis analysis, WorldAssignment assignment);
}

