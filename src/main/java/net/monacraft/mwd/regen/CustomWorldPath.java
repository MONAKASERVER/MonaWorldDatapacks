package net.monacraft.mwd.regen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;

public final class CustomWorldPath {
    private CustomWorldPath() {}

    public static Path resolve(Path serverRoot, String dimensionKey) throws IOException {
        int separator = dimensionKey.indexOf(':');
        if (separator < 1 || separator == dimensionKey.length() - 1) throw new IOException("Invalid dimension key: " + dimensionKey);
        String namespace = dimensionKey.substring(0, separator);
        String path = dimensionKey.substring(separator + 1);
        if (!namespace.matches("[a-z0-9._-]+") || !path.matches("[a-z0-9/._-]+") || path.contains(".."))
            throw new IOException("Unsafe dimension key: " + dimensionKey);
        String levelName = primaryLevelName(serverRoot);
        String folderName = levelName + '_' + namespace + '_' + path.replace('/', '_');
        Path normalizedRoot = serverRoot.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(folderName).normalize();
        if (!normalizedRoot.equals(result.getParent())) throw new IOException("Unsafe custom world folder: " + result);
        return result;
    }

    public static Path serverRoot(Path dataDirectory) throws IOException {
        Path data = dataDirectory.toAbsolutePath().normalize();
        Path plugins = data.getParent();
        Path root = plugins == null ? null : plugins.getParent();
        if (root == null || !Files.isDirectory(root)) throw new IOException("Unable to resolve server root from " + dataDirectory);
        return root;
    }

    private static String primaryLevelName(Path serverRoot) throws IOException {
        Properties properties = new Properties();
        Path file = serverRoot.resolve("server.properties");
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        String name = properties.getProperty("level-name", "world").trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals(".."))
            throw new IOException("Unsafe level-name in server.properties: " + name);
        return name;
    }
}
