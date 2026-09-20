package net.monacraft.mwd.compiler;

import net.kyori.adventure.nbt.*;
import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.resource.ResourceType;

import java.io.*;
import java.util.*;

/** Rewrites registry references stored inside binary structure templates. */
public final class NbtStructureRewriter {
    private static final long MAX_NBT_BYTES = 64L << 20;

    public byte[] rewrite(byte[] input, NamespaceMapper mapper) throws IOException {
        boolean compressed = input.length >= 2 && (input[0] & 0xff) == 0x1f && (input[1] & 0xff) == 0x8b;
        BinaryTagIO.Compression compression = compressed ? BinaryTagIO.Compression.GZIP : BinaryTagIO.Compression.NONE;
        CompoundBinaryTag root;
        try (ByteArrayInputStream source = new ByteArrayInputStream(input)) {
            root = BinaryTagIO.reader(MAX_NBT_BYTES).read(source, compression);
        }

        CompoundBinaryTag rewritten = (CompoundBinaryTag) rewriteTag(root, null, mapper);
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        BinaryTagIO.writer().write(rewritten, output, compression);
        return output.toByteArray();
    }

    private BinaryTag rewriteTag(BinaryTag tag, String field, NamespaceMapper mapper) {
        if (tag instanceof CompoundBinaryTag compound) {
            Map<String, BinaryTag> values = new LinkedHashMap<>();
            for (Map.Entry<String, ? extends BinaryTag> entry : compound) {
                values.put(entry.getKey(), rewriteTag(entry.getValue(), entry.getKey(), mapper));
            }
            return CompoundBinaryTag.from(values);
        }
        if (tag instanceof ListBinaryTag list) {
            List<BinaryTag> values = new ArrayList<>(list.size());
            for (BinaryTag value : list) values.add(rewriteTag(value, field, mapper));
            return ListBinaryTag.from(values);
        }
        ResourceType expected = switch (field == null ? "" : field) {
            case "pool" -> ResourceType.TEMPLATE_POOL;
            case "LootTable", "DeathLootTable" -> ResourceType.LOOT_TABLE;
            default -> null;
        };
        if (tag instanceof StringBinaryTag string && expected != null) {
            Optional<ResourceLocation> parsed = ResourceLocation.tryParse(string.value());
            if (parsed.isPresent()) {
                ResourceLocation mapped = mapper.mapReference(expected, parsed.get());
                if (!mapped.equals(parsed.get())) return StringBinaryTag.stringBinaryTag(mapped.toString());
            }
        }
        return tag;
    }
}
